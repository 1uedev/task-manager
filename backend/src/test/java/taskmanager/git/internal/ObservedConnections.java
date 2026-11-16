package taskmanager.git.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * TASK-120, F3 and H1, TASK-121, F1 and F2, TASK-122, F1, and TASK-123: observes the connection service and the database
 * of a test context without any hook in the product code. Every call of a provider is reported when it ends, with the result it
 * hands on or the failure it ended with, so a test knows that one entry of an answer is fully read before it changes the
 * connection, and that a held entry ended only after the test released it. The entry of the final check is reported with
 * the results and their generations exactly as the check receives them, together with every SQL statement the final check
 * executes through the JDBC wrapper of the data source. Every report carries a number of one sequence that the test shares
 * through {@link #mark()}, so the order of action, release, end of a call and final check can be checked. Without a test
 * that waits for them the service and the database work unchanged.
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
            return Mockito.mock(bean.getClass(), invocation -> dataSource(real, invocation));
        }
        return bean;
    }

    /**
     * The observed data source (TASK-123, F1): {@code unwrap} and {@code isWrapperFor} to a target the observed data source
     * fulfils answer with the observed data source itself and never reach the pool, which would hand out itself; another
     * target fails while the final check records and reaches the pool otherwise. Every connection it hands out is observed.
     */
    private static Object dataSource(DataSource real, InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        Object[] args = invocation.getArguments();
        if ((name.equals("unwrap") || name.equals("isWrapperFor")) && args.length == 1 && args[0] instanceof Class<?> target) {
            if (target.isInstance(invocation.getMock())) {
                return name.equals("unwrap") ? invocation.getMock() : true;
            }
            if (RECORDING.get() != null) {
                if (name.equals("isWrapperFor")) {
                    return false;
                }
                throw new SQLException("the final check may not leave the observed data source through unwrap to " + target);
            }
        }
        Object result = delegate(real, invocation);
        return result instanceof Connection connection ? recording(connection) : result;
    }

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
     * argument, for example {@code setArray}, reaches it as the original.
     */
    static Connection recording(Connection real) {
        try {
            return (Connection) new Observed().observe(real, Connection.class);
        } catch (SQLException e) {
            throw new IllegalStateException("a connection is always observed", e);
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

    /** The wrappers of one borrowed connection, by the identity of the object they wrap and back. */
    private static final class Observed {
        private final Map<Object, Object> wrappers = new IdentityHashMap<>();
        private final Map<Object, Object> originals = new IdentityHashMap<>();
        /** The SQL a prepared statement was prepared with, by its original. */
        private final Map<Object, String> prepared = new IdentityHashMap<>();

        /**
         * {@code real} as the caller may see it: unchanged when it is no JDBC object, else its wrapper. Fails while the final
         * check records when the wrapper would not be a {@code returned}.
         */
        synchronized Object observe(Object real, Class<?> returned) throws SQLException {
            if (real == null || originals.containsKey(real)) {
                return real;
            }
            if (real instanceof Object[] values && !values.getClass().getComponentType().isPrimitive()) {
                return observeAll(values);
            }
            Object known = wrappers.get(real);
            if (known == null) {
                Set<Class<?>> types = jdbcInterfaces(real.getClass());
                if (types.isEmpty()) {
                    return real;
                }
                List<String> batch = Collections.synchronizedList(new ArrayList<>());
                InvocationHandler handler = (proxy, method, args) -> invoke(proxy, real, batch, method, args);
                known = Proxy.newProxyInstance(ObservedConnections.class.getClassLoader(), types.toArray(Class<?>[]::new),
                    handler);
                wrappers.put(real, known);
                originals.put(known, real);
            }
            if (returned.isInstance(known)) {
                return known;
            }
            return refused(real, "a " + returned.getName() + " the wrapper cannot hand out observed: " + real.getClass());
        }

        /** An array of values whose JDBC objects are all observed, in a copy of the same type. */
        private Object observeAll(Object[] values) throws SQLException {
            Object[] copy = values.clone();
            for (int i = 0; i < copy.length; i++) {
                Object value = copy[i];
                if (value != null && !jdbcInterfaces(value.getClass()).isEmpty()) {
                    Object wrapper = observe(value, Object.class);
                    if (!values.getClass().getComponentType().isInstance(wrapper)) {
                        return refused(values, "an array of " + values.getClass().getComponentType().getName());
                    }
                    copy[i] = wrapper;
                }
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

        /** {@code value} as the driver must see it: the original of an observed object, also inside an array of objects. */
        private synchronized Object original(Object value) {
            Object real = originals.get(value);
            if (real != null) {
                return real;
            }
            if (value instanceof Object[] values) {
                Object[] copy = values.clone();
                for (int i = 0; i < copy.length; i++) {
                    Object inner = originals.get(copy[i]);
                    if (inner != null) {
                        copy[i] = inner;
                    }
                }
                return copy;
            }
            return value;
        }

        private Object invoke(Object proxy, Object real, List<String> batch, Method method, Object[] given) throws Throwable {
            String name = method.getName();
            Object[] args = given == null ? null : given.clone();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    args[i] = original(args[i]);
                }
            }
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
            return observe(result, method.getReturnType());
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
