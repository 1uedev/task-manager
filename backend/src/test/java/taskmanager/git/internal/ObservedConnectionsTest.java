package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.sql.ConnectionBuilder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * TASK-121, F2: the JDBC wrapper of {@link ObservedConnections} counts executions, not preparations. A statement prepared once
 * and executed twice with new parameters is two queries for PostgreSQL and is written down twice; preparing alone and
 * {@code nativeSQL} execute nothing. TASK-122, F1: every way back through {@code ResultSet.getStatement()},
 * {@code unwrap} and {@code getConnection()} leads to an observed object, so a second execution there counts as well, and
 * {@code unwrap} to any other target fails while recording. TASK-123: every JDBC object, also an array, a large object and
 * the data source itself through {@code unwrap}, is observed or refused while recording. TASK-124: the same holds for
 * every JDBC object the data source returns, such as the data source set at the pool and a connection builder, and for
 * JDBC objects in arrays of every depth in both directions; a read that names the class of its result returns that
 * class or fails while recording. The connection is a test double that answers every call with an empty value, a further
 * double or the answer a test set for it. Every statement binds its parameters before it is executed, and a statement
 * without parameters has none in its text, so every test would also run against a driver.
 */
class ObservedConnectionsTest {
    private static final String JOINED = "select * from git_provider p join git_connection c on c.provider_id = p.id where p.id = ?";
    private static final String PLAIN = "select count(*) from git_provider";
    private static final String THREE = "select ?::text[], ?::bytea, ?::text";

    /** The answers a test sets for the doubles, by interface and method as in {@code ResultSet.getObject}. */
    private final Map<String, Object> answers = new HashMap<>();
    /** The arguments the doubles received last, by interface and method as in {@code Connection.createStruct}. */
    private final Map<String, Object[]> received = new HashMap<>();

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
                prepared.setObject(1, "value");
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
        query(connection.prepareStatement(JOINED), "first");
        assertEquals(List.of(), ObservedConnections.executedWhile(() -> { }));
    }

    @Test
    void aSecondExecutionThroughTheStatementOfItsResultCounts() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                ResultSet result = query(statement, "first");
                assertSame(statement, result.getStatement());
                query((PreparedStatement) result.getStatement(), "second");
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
                statement.setObject(1, "first");
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
                query(statement, "first");
                assertSame(connection, statement.getConnection());
                assertSame(connection, query(statement, "second").getStatement().getConnection());
                assertSame(connection, connection.getMetaData().getConnection());
                query(statement.getConnection().prepareStatement(JOINED), "third");
                connection.getMetaData().getConnection().createStatement().executeQuery(PLAIN);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, JOINED, JOINED, PLAIN), executed);
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
                query(statement, "first");
                Array array = connection.createArrayOf("uuid", new Object[0]);
                assertTrue(observed(array), "the array is observed");
                ResultSet result = array.getResultSet();
                Statement made = result.getStatement();
                assertTrue(observed(made), "the statement the array made is observed");
                assertSame(made, result.getStatement(), "the same statement, the same wrapper");
                assertSame(connection, made.getConnection());
                made.executeQuery(PLAIN);
                query(made.getConnection().prepareStatement(JOINED), "second");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, PLAIN, JOINED), executed);
    }

    @Test
    void largeObjectsAndOtherJdbcTypesAreObservedAndReachTheDriverAsOriginals() throws SQLException {
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        PreparedStatement statement = connection.prepareStatement(THREE);
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
        assertEquals(List.of(THREE), executed);
    }

    @Test
    void aSecondExecutionThroughTheDataSourceSetAtThePoolCounts() throws SQLException {
        DataSource inner = nothing(DataSource.class);
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSource(inner);
            HikariDataSource observed = (HikariDataSource) new ObservedConnections()
                .postProcessAfterInitialization(pool, "dataSource");
            assertSame(inner, pool.getDataSource(), "the real pool hands out the data source it was given");
            List<String> executed = ObservedConnections.executedWhile(() -> {
                try {
                    DataSource handed = observed.getDataSource();
                    assertTrue(observed(handed), "the data source set at the pool is observed");
                    assertSame(handed, observed.getDataSource(), "the same data source, the same wrapper");
                    assertSame(handed, handed.unwrap(DataSource.class));
                    assertThrows(SQLException.class, () -> handed.unwrap(java.sql.Driver.class));
                    Connection plain = handed.getConnection();
                    assertTrue(observed(plain), "its connection is observed");
                    query(plain.prepareStatement(JOINED), "first");
                    query(handed.getConnection("reader", "none").prepareStatement(JOINED), "second");
                    ConnectionBuilder builder = handed.createConnectionBuilder();
                    assertTrue(observed(builder), "its connection builder is observed");
                    assertSame(builder, builder.user("reader"), "the same builder, the same wrapper");
                    Connection built = builder.user("reader").build();
                    assertTrue(observed(built), "the connection it builds is observed");
                    query(built.prepareStatement(JOINED), "third");
                    built.createStatement().executeQuery(PLAIN);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            assertEquals(List.of(JOINED, JOINED, JOINED, PLAIN), executed);
        }
    }

    @Test
    void aSecondExecutionThroughAStatementInANestedArrayCounts() throws SQLException {
        Connection driver = nothing(Connection.class);
        Statement hidden = driver.createStatement();
        Object[] loop = {null, hidden};
        loop[0] = loop;
        answers.put("ResultSet.getObject", new Object[] {new Statement[] {hidden}, new Object[][] {{hidden, "text"}}, loop});
        Connection connection = ObservedConnections.recording(driver);
        PreparedStatement statement = connection.prepareStatement(JOINED);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                Object[] read = (Object[]) query(statement, "first").getObject(1);
                Statement[] typed = (Statement[]) read[0];
                Object[][] deep = (Object[][]) read[1];
                Object[] circle = (Object[]) read[2];
                assertTrue(observed(typed[0]), "a statement in an inner array is observed");
                assertSame(typed[0], deep[0][0], "the same statement, the same wrapper");
                assertEquals("text", deep[0][1]);
                assertSame(circle, circle[0], "an array that contains itself is copied once");
                assertSame(typed[0], circle[1]);
                assertSame(connection, typed[0].getConnection());
                typed[0].executeQuery(PLAIN);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(JOINED, PLAIN), executed);
        assertSame(hidden, loop[1], "the array of the driver is unchanged");
    }

    @Test
    void anObservedObjectInANestedArrayReachesTheDriverAsTheOriginal() throws SQLException {
        Connection driver = nothing(Connection.class);
        Array original = nothing(Array.class, driver, null);
        answers.put("Connection.createArrayOf", original);
        Connection connection = ObservedConnections.recording(driver);
        List<String> executed = ObservedConnections.executedWhile(() -> {
            try {
                Array array = connection.createArrayOf("text", new Object[0]);
                assertTrue(observed(array), "the array is observed");
                Object[] inner = {array, "text"};
                Object[] loop = {null, array};
                loop[0] = loop;
                connection.createStruct("pair", new Object[] {inner, new Array[][] {{array}}, inner, loop});
                Object[] reached = (Object[]) received.get("Connection.createStruct")[1];
                assertSame(original, ((Object[]) reached[0])[0], "the driver receives the original in an inner array");
                assertEquals("text", ((Object[]) reached[0])[1]);
                assertSame(original, ((Array[][]) reached[1])[0][0], "also two levels down, in an array of the same type");
                assertSame(reached[0], reached[2], "an array that occurs twice is copied once");
                assertSame(reached[3], ((Object[]) reached[3])[0], "an array that contains itself is copied once");
                assertSame(original, ((Object[]) reached[3])[1]);
                assertSame(array, inner[0], "the array of the caller is unchanged");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(), executed);
    }

    @Test
    void aReadThatNamesAClassReturnsThatClassOrFailsWhileRecording() throws SQLException {
        Key key = new Key();
        answers.put("ResultSet.getObject", key);
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        ResultSet result = query(connection.prepareStatement(JOINED), "first");
        Key outside = result.getObject(1, Key.class);
        assertSame(key, outside, "outside the recording the caller receives the value of the class it named");
        assertSame(key, result.getObject("id", Key.class));
        List<String> executed = ObservedConnections.executedWhile(() -> {
            assertThrows(SQLException.class, () -> result.getObject(1, Key.class));
            assertThrows(SQLException.class, () -> result.getObject("id", Key.class));
            try {
                RowId read = result.getObject(1, RowId.class);
                assertTrue(observed(read), "a JDBC interface the wrapper fulfils is observed");
                assertSame(read, result.getObject(1), "the same object, the same wrapper");
                assertArrayEquals(key.getBytes(), read.getBytes());
                answers.put("ResultSet.getObject", 5);
                assertEquals(5, result.getObject(1, Integer.class));
                assertEquals(5, result.getObject(1, int.class), "a value that is no JDBC object is never refused");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(), executed);
    }

    @Test
    void anObjectOfAClassOfTheApplicationIsNeverWrapped() throws SQLException {
        Money money = new Money();
        answers.put("ResultSet.getObject", money);
        Connection connection = ObservedConnections.recording(nothing(Connection.class));
        ResultSet result = query(connection.prepareStatement(JOINED), "first");
        Money read = (Money) result.getObject(1);
        assertSame(money, read, "outside the recording the caller receives the object itself");
        assertSame(money, result.getObject(1, Money.class));
        assertSame(money, result.getObject(1, Map.of("money", Money.class)));
        List<String> executed = ObservedConnections.executedWhile(() -> {
            assertThrows(SQLException.class, () -> result.getObject(1));
            assertThrows(SQLException.class, () -> result.getObject(1, Money.class));
            assertThrows(SQLException.class, () -> result.getObject(1, SQLData.class));
            answers.put("ResultSet.getObject", new Object[] {new Object[] {money}});
            assertThrows(SQLException.class, () -> result.getObject(1), "also in an inner array");
        });
        assertEquals(List.of(), executed);
        assertSame(money, ((Object[]) ((Object[]) result.getObject(1))[0])[0]);
    }

    /** Binds {@code value} to the one parameter of {@code statement} and executes it. */
    private static ResultSet query(PreparedStatement statement, Object value) throws SQLException {
        statement.setObject(1, value);
        return statement.executeQuery();
    }

    /** A value of a class of a driver that implements a JDBC interface. */
    private static final class Key implements RowId {
        @Override
        public byte[] getBytes() {
            return new byte[] {1, 2, 3};
        }
    }

    /** A value of a class of the application, as a driver creates it for a type the application mapped. */
    private static final class Money implements SQLData {
        @Override
        public String getSQLTypeName() {
            return "money";
        }

        @Override
        public void readSQL(SQLInput stream, String typeName) {
        }

        @Override
        public void writeSQL(SQLOutput stream) {
        }
    }

    /** Whether {@code value} is a wrapper of {@link ObservedConnections}. */
    private static boolean observed(Object value) {
        return value instanceof Proxy
            && Proxy.getInvocationHandler(value).getClass().getName().startsWith(ObservedConnections.class.getName() + "$");
    }

    /** Whether {@code value} is a proxy that is no double of this test, or an array of objects that holds one at any depth. */
    private static boolean foreign(Object value, Set<Object> arrays) {
        if (value instanceof Object[] values) {
            if (!arrays.add(values)) {
                return false;
            }
            for (Object inner : values) {
                if (foreign(inner, arrays)) {
                    return true;
                }
            }
            return false;
        }
        return value instanceof Proxy
            && !Proxy.getInvocationHandler(value).getClass().getName().startsWith(ObservedConnectionsTest.class.getName());
    }

    /**
     * A JDBC object of {@code type} whose methods return {@code false}, zero, {@code null}, a further double or the answer
     * the test set in {@link #answers}. Like a driver it answers the way back with the same object: a statement names the
     * connection that made it, a result set the statement that made it, and a description of the database its connection.
     * An array makes its result over a new statement of the same connection, as PgArray does. A data source and a
     * connection builder make a new connection at every call, and a connection builder returns itself from its setters.
     * A wrapper of the test handed to it as an argument, also inside an array, fails, as a driver would not know it; the
     * arguments it received are kept in {@link #received}.
     */
    private <T> T nothing(Class<T> type) {
        return nothing(type, null, null);
    }

    @SuppressWarnings("unchecked")
    private <T> T nothing(Class<T> type, Object connection, Object statement) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> returned = method.getReturnType();
            String name = method.getName();
            String key = method.getDeclaringClass().getSimpleName() + "." + name;
            Object owner = connection == null || proxy instanceof Connection ? proxy : connection;
            if (args != null) {
                if (foreign(args, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                    throw new IllegalArgumentException("a wrapper reached the driver in " + name);
                }
                received.put(key, args);
            }
            if (answers.containsKey(key)) {
                return answers.get(key);
            }
            if (name.equals("getConnection") && !(proxy instanceof DataSource)) {
                return owner;
            }
            if (name.equals("getStatement")) {
                return statement;
            }
            if (name.equals("unwrap") || returned == ConnectionBuilder.class && proxy instanceof ConnectionBuilder) {
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
