package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TASK-121, F2: the JDBC wrapper of {@link ObservedConnections} counts executions, not preparations. A statement prepared once
 * and executed twice with new parameters is two queries for PostgreSQL and is written down twice; preparing alone and
 * {@code nativeSQL} execute nothing. The connection is a test double that answers every call with an empty value.
 */
class ObservedConnectionsTest {
    private static final String JOINED = "select * from git_provider p join git_connection c on c.provider_id = p.id where p.id = ?";

    @Test
    void aStatementPreparedOnceAndExecutedTwiceCountsTwice() {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try (PreparedStatement statement = connection.prepareStatement(JOINED)) {
                statement.setObject(1, "first");
                statement.executeQuery();
                statement.setObject(1, "second");
                statement.executeQuery();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED), executed);
    }

    @Test
    void preparingAndTranslatingExecuteNothing() {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                connection.nativeSQL(JOINED);
                connection.prepareStatement(JOINED).close();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(), executed);
    }

    @Test
    void everyKindOfExecutionCounts() {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                PreparedStatement prepared = connection.prepareStatement("update a set b = ?");
                prepared.execute();
                prepared.executeUpdate();
                prepared.addBatch();
                prepared.addBatch();
                prepared.executeBatch();
                Statement plain = connection.createStatement();
                plain.execute("select 1");
                plain.addBatch("select 2");
                plain.executeBatch();
                CallableStatement call = connection.prepareCall("call c()");
                call.execute();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of("update a set b = ?", "update a set b = ?", "update a set b = ?", "update a set b = ?", "select 1",
            "select 2", "call c()"), executed);
    }

    @Test
    void nothingIsWrittenDownOutsideTheRecording() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        connection.prepareStatement(JOINED).executeQuery();
        assertEquals(List.of(), ObservedConnections.executedWhile(() -> { }));
    }

    /** A JDBC object of {@code type} whose methods return a further double, {@code false}, zero or {@code null}. */
    @SuppressWarnings("unchecked")
    private static <T> T nothing(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> returned = method.getReturnType();
            if (returned == boolean.class) {
                return false;
            }
            if (returned == int.class || returned == long.class) {
                return returned == int.class ? (Object) 0 : (Object) 0L;
            }
            if (returned == int[].class) {
                return new int[0];
            }
            if (returned == long[].class) {
                return new long[0];
            }
            if (returned.isInterface() && returned.getName().startsWith("java.sql.") && Statement.class.isAssignableFrom(returned)) {
                return nothing(returned);
            }
            return null;
        });
    }
}
