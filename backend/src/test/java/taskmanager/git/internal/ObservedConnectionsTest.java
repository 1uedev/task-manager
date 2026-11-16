package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TASK-121, F2: the JDBC wrapper of {@link ObservedConnections} counts executions, not preparations. A statement prepared once
 * and executed twice with new parameters is two queries for PostgreSQL and is written down twice; preparing alone and
 * {@code nativeSQL} execute nothing. TASK-122, F1: every way back through {@code ResultSet.getStatement()},
 * {@code unwrap} and {@code getConnection()} leads to an observed object, so a second execution there counts as well, and
 * {@code unwrap} to any other target fails while recording. The connection is a test double that answers every call with an
 * empty value or a further double.
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

    @Test
    void aSecondExecutionThroughTheStatementOfItsResultCounts() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                ResultSet result = statement.executeQuery();
                assertSame(statement, result.getStatement());
                ((PreparedStatement) result.getStatement()).executeQuery();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED), executed);
    }

    @Test
    void aSecondExecutionThroughUnwrapCounts() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                statement.executeQuery();
                assertTrue(statement.isWrapperFor(Statement.class));
                assertSame(statement, statement.unwrap(Statement.class));
                assertSame(statement, statement.unwrap(PreparedStatement.class));
                statement.unwrap(Statement.class).executeQuery(JOINED);
                assertSame(connection, connection.unwrap(Connection.class));
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED), executed);
    }

    @Test
    void aSecondExecutionThroughTheConnectionOfAStatementCounts() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                statement.executeQuery();
                assertSame(connection, statement.getConnection());
                assertSame(connection, statement.executeQuery().getStatement().getConnection());
                assertSame(connection, connection.getMetaData().getConnection());
                statement.getConnection().prepareStatement(JOINED).executeQuery();
                connection.getMetaData().getConnection().createStatement().executeQuery(JOINED);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED, JOINED, JOINED), executed);
    }

    @Test
    void unwrapToAnyOtherTargetFailsWhileRecording() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            assertThrows(SQLException.class, () -> statement.unwrap(CallableStatement.class));
            assertThrows(SQLException.class, () -> connection.unwrap(java.sql.Driver.class));
            try {
                assertFalse(statement.isWrapperFor(CallableStatement.class));
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(), executed);
    }

    /**
     * A JDBC object of {@code type} whose methods return {@code false}, zero, {@code null} or a further double. Like a driver
     * it answers the way back with the same object: a statement names the connection that made it, a result set the
     * statement that made it, and a description of the database its connection.
     */
    private static <T> T nothing(Class<T> type) {
        return nothing(type, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T nothing(Class<T> type, Object connection, Object statement) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> returned = method.getReturnType();
            String name = method.getName();
            Object owner = connection == null ? proxy : connection;
            if (name.equals("getConnection")) {
                return owner;
            }
            if (name.equals("getStatement")) {
                return statement;
            }
            if (name.equals("unwrap")) {
                return proxy;
            }
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
            if (returned == ResultSet.class) {
                return nothing(ResultSet.class, owner, proxy);
            }
            if (returned == DatabaseMetaData.class || (returned.isInterface() && Statement.class.isAssignableFrom(returned))) {
                return nothing(returned, owner, null);
            }
            return null;
        });
    }
}
