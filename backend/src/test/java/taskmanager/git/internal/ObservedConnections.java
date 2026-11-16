package taskmanager.git.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * TASK-120, F3 and H1, and TASK-121, F1 and F2: observes the connection service and the database of a test context without any
 * hook in the product code. Every call of a provider is reported when it ends, with the result it hands on or the failure it
 * ended with, so a test knows that one entry of an answer is fully read before it changes the connection, and that a held
 * entry ended only after the test released it. The entry of the final check is reported with the results and their
 * generations exactly as the check receives them, together with every SQL statement the final check executes through the
 * JDBC wrapper of the data source. Every report carries a number of one sequence that the test shares through {@link #mark()},
 * so the order of action, release, end of a call and final check can be checked. Without a test that waits for them the
 * service and the database work unchanged.
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
     */
    static Connection recording(Connection real) {
        return wrap(Connection.class, real, (proxy, method, args) -> {
            Object result = method.invoke(real, args);
            if (result instanceof PreparedStatement prepared && args != null && args.length > 0 && args[0] instanceof String sql) {
                return executing(method.getReturnType(), prepared, sql);
            }
            if (result instanceof Statement statement && !(result instanceof PreparedStatement)) {
                return executing(Statement.class, statement, null);
            }
            return result;
        });
    }

    /** A statement that writes down {@code prepared} (or the SQL handed to it) at every execution. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object executing(Class type, Statement real, String prepared) {
        List<String> batch = Collections.synchronizedList(new ArrayList<>());
        return wrap(type, real, (proxy, method, args) -> {
            String name = method.getName();
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
            return method.invoke(real, args);
        });
    }

    private static void record(String sql) {
        List<String> statements = RECORDING.get();
        if (statements != null) {
            statements.add(sql);
        }
    }

    private interface Call {
        Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
    }

    private static <T> T wrap(Class<T> type, T real, Call call) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("unwrap") && args != null && args.length == 1 && args[0] == type) {
                return proxy;
            }
            try {
                return call.invoke(proxy, method, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
