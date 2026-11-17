package taskmanager.git.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.sql.DataSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * TASK-120, F3 and H1, TASK-121, F1 and F2, TASK-122, F1, TASK-123 and TASK-124: observes the connection service and the
 * database of a test context without any hook in the product code. Every call of a provider is reported when it ends, with
 * the result it hands on or the failure it ended with, so a test knows that one entry of an answer is fully read before it
 * changes the connection, and that a held entry ended only after the test released it. The entry of the final check is
 * reported with the results and their generations exactly as the check receives them, together with every SQL statement
 * the final check executes through the JDBC wrapper of the data source. Every report carries a number of one sequence that
 * the test shares through {@link #mark()}, so the order of action, release, end of a call and final check can be checked.
 * Without a test that waits for them the service and the database work unchanged.
 */
public class ObservedConnections implements BeanPostProcessor {
    /**
     * One call of a provider of the instance {@code providerId} that ended at {@code sequence}: with {@code result} after its
     * own check, or with {@code failure}, for example a timeout of the HTTP client or a changed connection.
     */
    public record Finished(UUID providerId, Object result, Throwable failure, long sequence) {
        /** Whether the call failed at the provider: a timeout, a refused connection or another failure of the HTTP client. */
        public boolean failedAtProvider() {
            return failure instanceof GitHttp.Failure;
        }
    }

    /** One final check that began at {@code sequence}: its results and their generations at its entry, and the statements it executed. */
    public record FinalCheck(List<Object> results, List<String> generations, List<String> statements, long sequence) {
        /** The generation the final check received for exactly {@code result}, or fails when it did not receive it. */
        public String generationOf(Object result) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i) == result) {
                    return generations.get(i);
                }
            }
            throw new AssertionError("the final check did not receive the entry " + result);
        }
    }

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final BlockingQueue<Finished> FINISHED = new LinkedBlockingQueue<>();
    private static final BlockingQueue<FinalCheck> CHECKS = new LinkedBlockingQueue<>();
    private static final ThreadLocal<List<String>> RECORDING = new ThreadLocal<>();

    /** Forgets everything reported so far; a test calls it right before the request it observes. */
    public static void reset() {
        FINISHED.clear();
        CHECKS.clear();
    }

    /** The next number of the sequence, for a step of the test such as the end of its action or the release of a held read. */
    public static long mark() {
        return SEQUENCE.incrementAndGet();
    }

    /** The next call of a provider that ended; fails after 10 seconds. */
    public static Finished awaitFinished() throws InterruptedException {
        Finished finished = FINISHED.poll(10, TimeUnit.SECONDS);
        if (finished == null) {
            throw new AssertionError("no call of a provider ended within 10 seconds");
        }
        return finished;
    }

    /** Every call of a provider that ended since the last {@link #awaitFinished()}, {@link #drainFinished()} or {@link #reset()}. */
    public static List<Finished> drainFinished() {
        List<Finished> drained = new ArrayList<>();
        FINISHED.drainTo(drained);
        return drained;
    }

    /** The next final check; fails after 20 seconds. */
    public static FinalCheck awaitFinalCheck() throws InterruptedException {
        FinalCheck check = CHECKS.poll(20, TimeUnit.SECONDS);
        if (check == null) {
            throw new AssertionError("no final check within 20 seconds");
        }
        return check;
    }

    /** Runs {@code work} on this thread and returns every statement it executed through {@link #recording(Connection)}. */
    static List<String> executedWhile(Runnable work) {
        List<String> statements = Collections.synchronizedList(new ArrayList<>());
        RECORDING.set(statements);
        try {
            work.run();
        } finally {
            RECORDING.remove();
        }
        return List.copyOf(statements);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ConnectionService real) {
            return Mockito.mock(ConnectionService.class, invocation -> connections(real, invocation));
        }
        if (bean instanceof DataSource real) {
            Observed scope = new Observed(true);
            Object observed = Mockito.mock(bean.getClass(), invocation -> dataSource(scope, real, invocation));
            scope.know(real, observed);
            return observed;
        }
        return bean;
    }

    /**
     * The observed data source (TASK-123, F1, and TASK-124, F1). It answers every call by the rule of {@link Observed}, the
     * same rule as every other wrapper: {@code unwrap} and {@code isWrapperFor} to a target the observed data source fulfils
     * answer with the observed data source itself and never reach the pool, which would hand out itself; another target
     * fails while the final check records and reaches the pool otherwise. Every JDBC object it returns is observed or
     * refused while the final check records, not only a connection: the data source set at the pool that
     * {@code getDataSource()} returns, a connection builder and the connection it builds. Every connection counts its
     * executions, whichever of these ways it came.
     */
    private static Object dataSource(Observed scope, DataSource real, InvocationOnMock invocation) throws Throwable {
        Method method = invocation.getMethod();
        method.setAccessible(true);
        return scope.invoke(invocation.getMock(), real, NO_BATCH, method, invocation.getRawArguments());
    }

    /** The batch of an object that is no statement and never collects one. */
    private static final List<String> NO_BATCH = List.of();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object connections(ConnectionService real, InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        if ("callWith".equals(name)) {
            UUID provider = ((Provider) invocation.getArgument(2)).id();
            Object result;
            try {
                result = delegate(real, invocation);
            } catch (Throwable failure) {
                FINISHED.add(new Finished(provider, null, failure, mark()));
                throw failure;
            }
            FINISHED.add(new Finished(provider, result, null, mark()));
            return result;
        }
        if ("keepCurrent".equals(name)) {
            long sequence = mark();
            List<Object> results = new ArrayList<>((List<Object>) invocation.getArgument(2));
            Function generationOf = invocation.getArgument(4);
            List<String> generations = new ArrayList<>();
            for (Object result : results) {
                generations.add((String) generationOf.apply(result));
            }
            List<String> statements = Collections.synchronizedList(new ArrayList<>());
            RECORDING.set(statements);
            try {
                return delegate(real, invocation);
            } finally {
                RECORDING.remove();
                CHECKS.add(new FinalCheck(results, Collections.unmodifiableList(generations), List.copyOf(statements), sequence));
            }
        }
        return delegate(real, invocation);
    }

    private static Object delegate(Object real, InvocationOnMock invocation) throws Throwable {
        Method method = invocation.getMethod();
        method.setAccessible(true);
        try {
            return method.invoke(real, invocation.getArguments());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * A connection whose statements write down their SQL at every execution while the final check records (TASK-121, F2).
     * Preparing a statement or translating SQL with {@code nativeSQL} executes nothing and is not written down; a prepared
     * statement executed twice, also with new parameters, is written down twice, and a batch once per statement in it.
     *
     * <p>TASK-122, F1, and TASK-123: one general rule for every object of {@code java.sql} and {@code javax.sql}. Every such
     * object the wrapper hands out, as a result, inside a returned array of objects or through a way back such as
     * {@code ResultSet.getStatement()}, is itself observed: connections, statements, results, descriptions, arrays, large
     * objects, save points and every other JDBC type. The wrapper implements every JDBC interface of the object it wraps,
     * and the same object always gets the same wrapper, so a way back to an object already handed out returns its wrapper.
     * A result set an array creates over a statement of its own, for example, hands out that statement observed, and its
     * connection is the observed connection again. {@code unwrap} and {@code isWrapperFor} to an interface the wrapper
     * fulfils answer with the wrapper; any other target fails while the final check records. A JDBC object the wrapper
     * cannot hand out observed fails while the final check records. An observed object handed back to the driver as an
     * argument, for example {@code setArray}, reaches it as the original, whichever connection or data source handed it out.
     *
     * <p>TASK-124. F2: arrays of objects are followed to every depth in both directions. A returned array is copied with its
     * type, every inner array as well, and every JDBC object in it is observed; an array handed to the driver is copied the
     * same way with every observed object replaced by its original. An array that occurs twice or contains itself is copied
     * once. F3: a call that names the class of its result, such as {@code getObject(1, Type.class)}, gets a value of that
     * class. When the wrapper is not of that class, the call fails while the final check records and returns the original
     * otherwise. An object of a class of the application, which JDBC marks with {@code SQLData}, is never wrapped, because
     * no wrapper is of its class and its methods are not those of the driver: it fails while the final check records and
     * is returned unchanged otherwise, also from a read without a named class.
     *
     * <p>Limits. The wrapper observes what the application receives as a result of a call on an observed object. It does not
     * observe a connection that never came from the observed data source, for example one of {@code DriverManager}; an
     * original taken outside the recording, through {@code unwrap} to another target or a read with a named class, and
     * used inside it; a JDBC object inside a result that is neither a JDBC object nor an array of objects, such as a
     * collection; and what the driver passes to an object of the application that it calls back, such as a listener of a
     * pooled connection or the {@code readSQL} of an {@code SQLData}. The final check uses none of these.
     */
    static Connection recording(Connection real) {
        try {
            return (Connection) new Observed(false).observe(real, Connection.class);
        } catch (SQLException e) {
            throw new IllegalStateException("a connection is always observed", e);
        }
    }

    /**
     * The class a call asks for: the one it names for the type variable it returns, as {@code getObject(1, Type.class)}
     * does, else its return type.
     */
    private static Class<?> requested(Method method, Object[] args) {
        if (args != null && method.getGenericReturnType() instanceof TypeVariable<?> variable) {
            Type[] parameters = method.getGenericParameterTypes();
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                if (parameters[i] instanceof ParameterizedType named && named.getRawType() == Class.class
                    && named.getActualTypeArguments()[0].equals(variable) && args[i] instanceof Class<?> type) {
                    return type;
                }
            }
        }
        return method.getReturnType();
    }

    /** What a wrapper of any connection or data source wraps, or {@code null} when {@code value} is no wrapper. */
    private static Object originalOf(Object value) {
        if (value == null || !Proxy.isProxyClass(value.getClass())) {
            return null;
        }
        return Proxy.getInvocationHandler(value) instanceof Handler handler ? handler.real : null;
    }

    /**
     * {@code value} as the driver must see it: the original of an observed object, also inside arrays of objects of every
     * depth. {@code arrays} holds the copy of every array met so far.
     */
    private static Object original(Object value, Map<Object, Object> arrays) throws SQLException {
        Object real = originalOf(value);
        if (real != null) {
            return real;
        }
        if (!(value instanceof Object[] values)) {
            return value;
        }
        Object known = arrays.get(values);
        if (known != null) {
            return known;
        }
        Object[] copy = values.clone();
        arrays.put(values, copy);
        Class<?> component = values.getClass().getComponentType();
        for (int i = 0; i < copy.length; i++) {
            Object inner = original(copy[i], arrays);
            if (inner != copy[i] && !component.isInstance(inner)) {
                throw new SQLException("an array of " + component.getName() + " cannot carry the original of an observed object");
            }
            copy[i] = inner;
        }
        return copy;
    }

    /** The handler of one wrapper. It names the original, so every wrapper is recognized and leads to what it wraps. */
    private static final class Handler implements InvocationHandler {
        private final Observed scope;
        private final Object real;
        private final List<String> batch = Collections.synchronizedList(new ArrayList<>());

        Handler(Observed scope, Object real) {
            this.scope = scope;
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return scope.invoke(proxy, real, batch, method, args);
        }
    }

    /** Whether {@code type} is an interface of JDBC. */
    private static boolean jdbc(Class<?> type) {
        String name = type.getName();
        return type.isInterface() && (name.startsWith("java.sql.") || name.startsWith("javax.sql."));
    }

    /** Every JDBC interface a class implements, also through its super classes and super interfaces. */
    private static final ClassValue<Set<Class<?>>> JDBC_INTERFACES = new ClassValue<>() {
        @Override
        protected Set<Class<?>> computeValue(Class<?> type) {
            return Set.copyOf(collectJdbcInterfaces(type));
        }
    };

    private static Set<Class<?>> jdbcInterfaces(Class<?> type) {
        return JDBC_INTERFACES.get(type);
    }

    private static Set<Class<?>> collectJdbcInterfaces(Class<?> type) {
        Set<Class<?>> found = new LinkedHashSet<>();
        Deque<Class<?>> open = new ArrayDeque<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            open.addAll(List.of(current.getInterfaces()));
        }
        while (!open.isEmpty()) {
            Class<?> next = open.pop();
            if (jdbc(next)) {
                found.add(next);
            }
            open.addAll(List.of(next.getInterfaces()));
        }
        return found;
    }

    /**
     * The wrappers of one borrowed connection or of one data source, by the identity of the object they wrap. A data source
     * starts a new one for every connection it hands out, so the wrappers of a connection end with it.
     */
    private static final class Observed {
        /** Whether this belongs to a data source. */
        private final boolean source;
        private final Map<Object, Object> wrappers = new IdentityHashMap<>();
        /** The SQL a prepared statement was prepared with, by its original. */
        private final Map<Object, String> prepared = new IdentityHashMap<>();

        Observed(boolean source) {
            this.source = source;
        }

        /** Takes {@code wrapper} as the wrapper of {@code real}: the observed data source for the pool it stands for. */
        synchronized void know(Object real, Object wrapper) {
            wrappers.put(real, wrapper);
        }

        /**
         * {@code real} as the caller may see it: unchanged when it is no JDBC object, an array with every JDBC object in it
         * observed, else its wrapper. When the wrapper is not a {@code requested}, or {@code real} cannot be observed at all,
         * the call fails while the final check records and returns {@code real} otherwise.
         */
        Object observe(Object real, Class<?> requested) throws SQLException {
            return observe(real, requested, new IdentityHashMap<>());
        }

        private synchronized Object observe(Object real, Class<?> requested, Map<Object, Object> arrays) throws SQLException {
            if (real == null || originalOf(real) != null) {
                return real;
            }
            if (real instanceof Object[] values) {
                return observeAll(values, arrays);
            }
            Set<Class<?>> types = jdbcInterfaces(real.getClass());
            if (types.isEmpty()) {
                return real;
            }
            if (real instanceof SQLData) {
                return refused(real, "an object of a class of the application: " + real.getClass().getName());
            }
            Observed scope = source && real instanceof Connection ? new Observed(false) : this;
            Object wrapper = scope.wrapper(real, types);
            if (requested.isInstance(wrapper)) {
                return wrapper;
            }
            return refused(real, "a " + requested.getName() + " the wrapper cannot hand out observed: " + real.getClass());
        }

        /** The one wrapper of {@code real}, with every JDBC interface of it. */
        private synchronized Object wrapper(Object real, Set<Class<?>> types) {
            Object known = wrappers.get(real);
            if (known == null) {
                known = Proxy.newProxyInstance(ObservedConnections.class.getClassLoader(), types.toArray(Class<?>[]::new),
                    new Handler(this, real));
                wrappers.put(real, known);
            }
            return known;
        }

        /**
         * A copy of an array of objects, of the same type, in which every JDBC object of every depth is observed.
         * {@code arrays} holds the copy of every array met so far, so an array that occurs twice or contains itself is
         * copied once.
         */
        private Object observeAll(Object[] values, Map<Object, Object> arrays) throws SQLException {
            Object known = arrays.get(values);
            if (known != null) {
                return known;
            }
            Object[] copy = values.clone();
            arrays.put(values, copy);
            Class<?> component = values.getClass().getComponentType();
            for (int i = 0; i < copy.length; i++) {
                copy[i] = observe(copy[i], component, arrays);
            }
            return copy;
        }

        /** {@code real} outside the final check; while it records, a failure. */
        private static Object refused(Object real, String what) throws SQLException {
            if (RECORDING.get() != null) {
                throw new SQLException("the final check may not receive " + what);
            }
            return real;
        }

        private Object invoke(Object proxy, Object real, List<String> batch, Method method, Object[] given) throws Throwable {
            String name = method.getName();
            Object[] args = given == null ? null : (Object[]) original(given, new IdentityHashMap<>());
            if ((name.equals("unwrap") || name.equals("isWrapperFor")) && args != null && args.length == 1
                && args[0] instanceof Class<?> target) {
                if (target.isInstance(proxy)) {
                    return name.equals("unwrap") ? proxy : true;
                }
                if (RECORDING.get() != null) {
                    if (name.equals("isWrapperFor")) {
                        return false;
                    }
                    throw new SQLException("the final check may not leave the observed JDBC objects through unwrap to " + target);
                }
                return call(real, method, args);
            }
            if (real instanceof Statement) {
                String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : preparedOf(real);
                if (name.equals("addBatch")) {
                    batch.add(sql);
                } else if (name.equals("clearBatch")) {
                    batch.clear();
                } else if (name.startsWith("execute") && name.contains("Batch")) {
                    synchronized (batch) {
                        batch.forEach(ObservedConnections::record);
                        batch.clear();
                    }
                } else if (name.startsWith("execute")) {
                    record(sql);
                }
            }
            Object result = call(real, method, args);
            if (result instanceof PreparedStatement && name.startsWith("prepare") && args != null && args.length > 0
                && args[0] instanceof String text) {
                synchronized (this) {
                    prepared.put(result, text);
                }
            }
            return observe(result, requested(method, args));
        }

        private synchronized String preparedOf(Object statement) {
            return prepared.get(statement);
        }

        private static Object call(Object real, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static void record(String sql) {
        List<String> statements = RECORDING.get();
        if (statements != null) {
            statements.add(sql);
        }
    }
}
