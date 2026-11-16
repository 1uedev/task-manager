package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * TASK-121, F2: the JDBC wrapper of {@link ObservedConnections} counts executions, not preparations. A statement prepared once
 * and executed twice with new parameters is two queries for PostgreSQL and is written down twice; preparing alone and
 * {@code nativeSQL} execute nothing. TASK-122, F1: every way back through {@code ResultSet.getStatement()},
 * {@code unwrap} and {@code getConnection()} leads to an observed object, so a second execution there counts as well, and
 * {@code unwrap} to any other target fails while recording. TASK-123: every JDBC object, also an array, a large object and
 * the data source itself through {@code unwrap}, is observed or refused while recording. The connection is a test double
 * that answers every call with an empty value or a further double.
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
                PreparedStatement again = (PreparedStatement) statement.unwrap(Statement.class);
                again.setObject(1, "second");
                again.executeQuery();
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

    @Test
    void theDataSourceUnwrapsToItselfAndNeverToThePool() throws SQLException {
        try (HikariDataSource pool = new HikariDataSource()) {
            DataSource observed = (DataSource) new ObservedConnections().postProcessAfterInitialization(pool, "dataSource");
            assertSame(pool, pool.unwrap(DataSource.class), "the real pool hands out itself");
            List<String> executed = ObservedConnections.executedWhile(() -> {
                try {
                    assertSame(observed, observed.unwrap(DataSource.class));
                    assertSame(observed, observed.unwrap(HikariDataSource.class));
                    assertTrue(observed.isWrapperFor(DataSource.class));
                    assertFalse(observed.isWrapperFor(java.sql.Driver.class));
                    assertThrows(SQLException.class, () -> observed.unwrap(java.sql.Driver.class));
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertEquals(List.of(), executed);
            assertSame(observed, observed.unwrap(DataSource.class), "also outside the recording");
        }
    }

    @Test
    void aSecondExecutionThroughTheResultOfAnArrayCounts() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                statement.executeQuery();
                Array array = connection.createArrayOf("uuid", new Object[0]);
                assertTrue(observed(array), "the array is observed");
                ResultSet result = array.getResultSet();
                Statement made = result.getStatement();
                assertTrue(observed(made), "the statement the array made is observed");
                assertSame(made, result.getStatement(), "the same statement, the same wrapper");
                assertSame(connection, made.getConnection());
                made.executeQuery(JOINED);
                made.getConnection().prepareStatement(JOINED).executeQuery();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED, JOINED), executed);
    }

    @Test
    void largeObjectsAndOtherJdbcTypesAreObservedAndReachTheDriverAsOriginals() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                Blob blob = connection.createBlob();
                Clob clob = connection.createClob();
                SQLXML xml = connection.createSQLXML();
                Array array = connection.createArrayOf("text", new Object[0]);
                Savepoint savepoint = connection.setSavepoint();
                for (Object made : List.of(blob, clob, xml, array, savepoint, connection.getMetaData(),
                    statement.getParameterMetaData(), array.getResultSet().getMetaData())) {
                    assertTrue(observed(made), "observed: " + made.getClass());
                }
                assertThrows(SQLException.class, () -> statement.getParameterMetaData().unwrap(java.sql.Driver.class));
                connection.releaseSavepoint(savepoint);
                statement.setArray(1, array);
                statement.setBlob(2, blob);
                statement.setObject(3, clob);
                statement.executeQuery();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED), executed);
    }

    /** Whether {@code value} is a wrapper of {@link ObservedConnections}. */
    private static boolean observed(Object value) {
        return value instanceof Proxy
            && Proxy.getInvocationHandler(value).getClass().getName().startsWith(ObservedConnections.class.getName() + "$");
    }

    /**
     * A JDBC object of {@code type} whose methods return {@code false}, zero, {@code null} or a further double. Like a driver
     * it answers the way back with the same object: a statement names the connection that made it, a result set the
     * statement that made it, and a description of the database its connection. An array makes its result over a new
     * statement of the same connection, as PgArray does. A wrapper of the test handed to it as an argument fails, as a
     * driver would not know it.
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
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Proxy && !Proxy.getInvocationHandler(arg).getClass().getName()
                        .startsWith(ObservedConnectionsTest.class.getName())) {
                        throw new IllegalArgumentException("a wrapper reached the driver in " + name);
                    }
                }
            }
            if (returned == ResultSet.class) {
                // Like PgArray, an array makes its result over a statement of its own on the same connection.
                Object maker = proxy instanceof Statement ? proxy : nothing(Statement.class, owner, null);
                return nothing(ResultSet.class, owner, maker);
            }
            if (returned.isInterface() && returned.getName().startsWith("java.sql.")) {
                return nothing(returned, owner, null);
            }
            return null;
        });
    }
}
