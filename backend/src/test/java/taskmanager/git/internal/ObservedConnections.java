package taskmanager.git.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.sql.DataSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * TASK-120, F3 and H1: observes the connection service and the database of a test context without any hook in the product
 * code. Every call of a provider that ended with its own check of the connection is reported with the result it hands on,
 * so a test knows that one entry of an answer is fully read before it changes the connection. The entry of the final check
 * is reported with the results and their generations exactly as the check receives them, together with every SQL statement
 * the final check sends through the JDBC wrapper of the data source. Without a test that waits for them the service and the
 * database work unchanged.
 */
public class ObservedConnections implements BeanPostProcessor {
    /** One call of a provider of the instance {@code providerId} that returned {@code result} after its own check. */
    public record Finished(UUID providerId, Object result) {
    }

    /** One final check: its results and their generations at its entry, and the statements it sent. */
    public record FinalCheck(List<Object> results, List<String> generations, List<String> statements) {
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

    private static final BlockingQueue<Finished> FINISHED = new LinkedBlockingQueue<>();
    private static final BlockingQueue<FinalCheck> CHECKS = new LinkedBlockingQueue<>();
    private static final ThreadLocal<List<String>> RECORDING = new ThreadLocal<>();

    /** Forgets everything reported so far; a test calls it right before the request it observes. */
    public static void reset() {
        FINISHED.clear();
        CHECKS.clear();
    }

    /** The next call of a provider that ended with its own check; fails after 10 seconds. */
    public static Finished awaitFinished() throws InterruptedException {
        Finished finished = FINISHED.poll(10, TimeUnit.SECONDS);
        if (finished == null) {
            throw new AssertionError("no call of a provider ended within 10 seconds");
        }
        return finished;
    }

    /** No call of a provider ended since the last {@link #awaitFinished()} or {@link #reset()}. */
    public static boolean noneFinished() {
        return FINISHED.isEmpty();
    }

    /** The next final check; fails after 20 seconds. */
    public static FinalCheck awaitFinalCheck() throws InterruptedException {
        FinalCheck check = CHECKS.poll(20, TimeUnit.SECONDS);
        if (check == null) {
            throw new AssertionError("no final check within 20 seconds");
        }
        return check;
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
            Object result = delegate(real, invocation);
            FINISHED.add(new Finished(((Provider) invocation.getArgument(2)).id(), result));
            return result;
        }
        if ("keepCurrent".equals(name)) {
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
                CHECKS.add(new FinalCheck(results, Collections.unmodifiableList(generations), List.copyOf(statements)));
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

    /** A connection that writes down the SQL of every statement prepared or run while the final check records. */
    private static Connection recording(Connection real) {
        return wrap(Connection.class, real, (method, args) -> {
            if (args != null && args.length > 0 && args[0] instanceof String sql
                && (method.getName().startsWith("prepare") || method.getName().equals("nativeSQL"))) {
                record(sql);
            }
        }, result -> result instanceof Statement statement && !(result instanceof java.sql.PreparedStatement)
            ? wrap(Statement.class, statement, (method, args) -> {
                if (args != null && args.length > 0 && args[0] instanceof String sql && method.getName().startsWith("execute")
                    || method.getName().equals("addBatch") && args != null && args.length > 0 && args[0] instanceof String) {
                    record((String) args[0]);
                }
            }, unchanged -> unchanged)
            : result);
    }

    private static void record(String sql) {
        List<String> statements = RECORDING.get();
        if (statements != null) {
            statements.add(sql);
        }
    }

    private interface Before {
        void accept(Method method, Object[] args);
    }

    private static <T> T wrap(Class<T> type, T real, Before before, Function<Object, Object> after) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("unwrap") && args != null && args.length == 1 && args[0] == type) {
                return proxy;
            }
            before.accept(method, args);
            try {
                return after.apply(method.invoke(real, args));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
