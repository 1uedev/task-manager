package taskmanager.git.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
 * TASK-120, F3 and H1, TASK-121, F1 and F2, and TASK-122, F1: observes the connection service and the database of a test
 * context without any hook in the product code. Every call of a provider is reported when it ends, with the result it
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
            return Mockito.mock(bean.getClass(), invocation -> {
                if ("unwrap".equals(invocation.getMethod().getName()) && RECORDING.get() != null
                    && !((Class<?>) invocation.getArgument(0)).isInstance(invocation.getMock())) {
                    throw new SQLException("the final check may not leave the observed data source through unwrap");
                }
                Object result = delegate(real, invocation);
                return result instanceof Connection connection ? recording(connection) : result;
            });
        }
        return bean;
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
     * <p>TASK-122, F1: every JDBC object that leaves the wrapper is observed as well. A statement, result set or database
     * description the wrapper hands out is wrapped, and a way back to an object it already wrapped, such as
     * {@code ResultSet.getStatement()}, {@code Statement.getConnection()} or {@code DatabaseMetaData.getConnection()}, returns
     * the same wrapper. {@code unwrap} to an interface the wrapper fulfils itself returns the wrapper; {@code unwrap} to any
     * other target fails while the final check records, so no SQL can run past the count.
     */
    static Connection recording(Connection real) {
        return new Observed().wrap(Connection.class, real, null);
    }

    /** The JDBC interfaces the wrapper observes, the most specific first. */
    private static final List<Class<?>> OBSERVED = List.of(CallableStatement.class, PreparedStatement.class, Statement.class,
        Connection.class, ResultSet.class, DatabaseMetaData.class);

    /** The wrappers of one borrowed connection by the identity of the object they wrap. */
    private static final class Observed {
        private final Map<Object, Object> wrappers = Collections.synchronizedMap(new IdentityHashMap<>());

        /** The wrapper of {@code real} as {@code type}, the same one for the same object; {@code prepared} is its SQL, if any. */
        @SuppressWarnings("unchecked")
        <T> T wrap(Class<T> type, T real, String prepared) {
            synchronized (wrappers) {
                Object known = wrappers.get(real);
                if (known != null && type.isInstance(known)) {
                    return (T) known;
                }
                List<String> batch = Collections.synchronizedList(new ArrayList<>());
                InvocationHandler handler = (proxy, method, args) -> invoke(proxy, real, prepared, batch, method, args);
                T proxy = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
                wrappers.put(real, proxy);
                return proxy;
            }
        }

        private Object invoke(Object proxy, Object real, String prepared, List<String> batch, Method method, Object[] args)
            throws Throwable {
            String name = method.getName();
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
                String sql = args != null && args.length > 0 && args[0] instanceof String given ? given : prepared;
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
            return observed(method, args, result);
        }

        /** {@code result} wrapped when it is a JDBC object the wrapper observes, else unchanged. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object observed(Method method, Object[] args, Object result) {
            if (result == null) {
                return null;
            }
            for (Class<?> type : OBSERVED) {
                if (type.isInstance(result) && method.getReturnType().isAssignableFrom(type)) {
                    String sql = result instanceof PreparedStatement && method.getName().startsWith("prepare") && args != null
                        && args.length > 0 && args[0] instanceof String given ? given : null;
                    return wrap((Class) type, result, sql);
                }
            }
            return result;
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
