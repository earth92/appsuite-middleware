/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.database;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.database.internal.JdbcPropertiesImpl;
import com.openexchange.exception.ExceptionUtils;
import com.openexchange.exception.OXException;
import com.openexchange.java.ConcurrentList;
import com.openexchange.java.Strings;

/**
 * Utilities for database resource handling.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class Databases {

    /**
     * {@link ConnectionStatus} - Defines the connection status.
     */
    public enum ConnectionStatus {
        INITIALISED,
        FAILED,
        SUCCEEDED;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Databases.class);
    private static final Cache<String, String> CHARSETS_BY_SCHEMA = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    /** The default limit for SQL-IN expressions: <code>1000</code */
    public static final int IN_LIMIT = 1000;

    private Databases() {
        super();
    }

    /**
     * Decides on how to return the write-able connection
     *
     * @param databaseService The database service
     * @param writeConnection The write-able connection to return to pool
     * @param contextId The context identifier
     * @param connectionStatus The connection status
     */
    public static void backWriteable(DatabaseService databaseService, Connection writeConnection, int contextId, ConnectionStatus connectionStatus) {
        if (null == writeConnection) {
            return;
        }
        switch (connectionStatus) {
            case INITIALISED:
                databaseService.backWritableAfterReading(contextId, writeConnection);
                return;
            case FAILED:
                rollback(writeConnection);
                autocommit(writeConnection);
                databaseService.backWritableAfterReading(contextId, writeConnection);
                return;
            case SUCCEEDED:
                autocommit(writeConnection);
                databaseService.backWritable(contextId, writeConnection);
        }
    }

    /**
     * Closes the given instances.
     *
     * @param closeables The instances to close.
     */
    public static void closeSQLStuff(AutoCloseable... closeables) {
        if (closeables == null) {
            return;
        }
        for (AutoCloseable closeable : closeables) {
            closeSQLStuff(closeable);
        }
    }

    /**
     * Closes the instance.
     *
     * @param closeable <code>null</code> or a {@link AutoCloseable} to close.
     */
    public static void closeSQLStuff(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.error("Failed to close {}", closeable.getClass().getName(), e);
        }
    }

    /**
     * Closes the {@link ResultSet} instances.
     *
     * @param results The instances to close.
     */
    public static void closeSQLStuff(ResultSet... results) {
        if (results == null) {
            return;
        }
        for (ResultSet result : results) {
            closeSQLStuff(result);
        }
    }

    /**
     * Closes the {@link ResultSet} instance.
     *
     * @param result <code>null</code> or a {@link ResultSet} to close.
     */
    public static void closeSQLStuff(ResultSet result) {
        if (result == null) {
            return;
        }
        try {
            result.close();
        } catch (SQLException e) {
            LOG.error("Failed to close result-set", e);
        }
    }

    /**
     * Closes the {@link Statement} instances.
     *
     * @param stmts The statements to close.
     */
    public static void closeSQLStuff(Statement... stmts) {
        if (null == stmts) {
            return;
        }
        for (Statement stmt : stmts) {
            closeSQLStuff(stmt);
        }
    }

    /**
     * Closes the {@link Statement}.
     *
     * @param stmt <code>null</code> or a {@link Statement} to close.
     */
    public static void closeSQLStuff(Statement stmt) {
        if (null == stmt) {
            return;
        }
        try {
            stmt.close();
        } catch (SQLException e) {
            LOG.error("Failed to close statement", e);
        }
    }

    /**
     * Closes the ResultSet and the Statement.
     *
     * @param result <code>null</code> or a ResultSet to close.
     * @param stmt <code>null</code> or a Statement to close.
     */
    public static void closeSQLStuff(ResultSet result, Statement stmt) {
        closeSQLStuff(result);
        closeSQLStuff(stmt);
    }

    /**
     * Gets the <code>toString()</code> representation for given <code>Statement</code> instance.
     *
     * @param stmt The statement
     * @return The <code>toString()</code> representation or an empty string if <code>null</code>
     */
    public static String getStatement(Statement stmt) {
        return stmt == null ? "" : stmt.toString();
    }

    /**
     * Gets the SQL statement from given <code>PreparedStatement</code> instance.
     *
     * @param stmt The <code>PreparedStatement</code> instance
     * @param query The optional query to return
     * @return The SQL statement
     */
    public static String getStatement(PreparedStatement stmt, String query) {
        if (stmt == null) {
            return query;
        }
        try {
            return stmt.toString();
        } catch (Exception x) {
            return query;
        }
    }

    /**
     * Gets the SQL statement from given <code>PreparedStatement</code> instance.
     *
     * @param stmt The <code>PreparedStatement</code> instance
     * @param query The optional query associated with given <code>PreparedStatement</code> instance
     * @return The SQL statement
     */
    public static String getSqlStatement(Statement stmt, String query) {
        if (stmt == null || isClosedSafe(stmt)) {
            return query;
        }
        try {
            String sql = stmt.toString();
            int pos = sql.indexOf(": ");
            return pos < 0 ? sql : sql.substring(pos + 2);
        } catch (Exception x) {
            return query;
        }
    }

    private static boolean isClosedSafe(Statement stmt) {
        try {
            return stmt.isClosed();
        } catch (Exception e) {
            // Assume as closed
            return true;
        }
    }

    /**
     * Starts a transaction on the given connection. This implementation sets autocommit to false and even executes a START TRANSACTION
     * statement to ensure isolation levels for the current connection.
     *
     * @param con connection to start the transaction on.
     * @throws SQLException if starting the transaction fails.
     */
    public static void startTransaction(Connection con) throws SQLException {
        if (null == con) {
            return;
        }
        Statement stmt = null;
        try {
            con.setAutoCommit(false);
            stmt = con.createStatement();
            stmt.execute("START TRANSACTION");
        } finally {
            closeSQLStuff(stmt);
        }
    }

    /**
     * Rolls a transaction of a connection back.
     *
     * @param con connection to roll back.
     */
    public static void rollback(Connection con) {
        if (null == con) {
            return;
        }
        try {
            con.rollback();
        } catch (SQLException e) {
            LOG.error("Failed to perform a roll-back.", e);
        }
    }

    /**
     * Rolls a transaction of a connection back to specified save-point,
     * which undoes all changes made after the the save-point was set.
     *
     * @param savepoint The save-point
     * @param con connection to roll back.
     */
    public static void rollback(Savepoint savepoint, Connection con) {
        rollback(savepoint, false, con);
    }

    /**
     * Rolls a transaction of a connection back to specified save-point,
     * which undoes all changes made after the the save-point was set.
     *
     * @param savepoint The save-point
     * @param releaseSavepoint Whether to release the save-point from current transaction afterwards
     * @param con connection to roll back.
     */
    public static void rollback(Savepoint savepoint, boolean releaseSavepoint, Connection con) {
        if (null == con || savepoint == null) {
            return;
        }

        if (releaseSavepoint) {
            try {
                con.rollback(savepoint);
            } catch (SQLException e) {
                LOG.error("Failed to perform a roll-back to save-point.", e);
            } finally {
                releaseSavepoint(savepoint, con);
            }
        } else {
            try {
                con.rollback(savepoint);
            } catch (SQLException e) {
                LOG.error("Failed to perform a roll-back to save-point.", e);
            }
        }
    }

    /**
     * Removes given save-point from current transaction.
     *
     * @param savepoint The save-point to remove
     * @param con The connection
     */
    public static void releaseSavepoint(Savepoint savepoint, Connection con) {
        if (null == con || savepoint == null) {
            return;
        }

        try {
            con.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            LOG.error("Failed to remove save-point from current transaction.", e);
        }
    }

    /**
     * Convenience method to set the auto-commit of a connection to <code>true</code>.
     *
     * @param con connection that should go into auto-commit mode.
     */
    public static void autocommit(Connection con) {
        if (null == con) {
            return;
        }
        try {
            con.setAutoCommit(true);
        } catch (SQLException e) {
            LOG.error("Failed to set auto-commit mode", e);
        }
    }

    private static final Pattern PAT_TRUNCATED_IDS = Pattern.compile("[^']*'(\\S+)'[^']*");

    /**
     * This method tries to parse the truncated fields out of the DataTruncation exception. This method has been implemented because MySQL
     * doesn't return the column identifier of the truncated field through the getIndex() method of the DataTruncation exception. This
     * method uses the fact that the exception sent by the MySQL server encapsulates the truncated fields into single quotes.
     *
     * @param e DataTruncation exception to parse.
     * @return a string array containing all truncated field from the exception.
     */
    public static String[] parseTruncatedFields(DataTruncation trunc) {
        Matcher matcher = PAT_TRUNCATED_IDS.matcher(trunc.getMessage());
        if (!matcher.find()) {
            return new String[0];
        }

        List<String> retval = new ArrayList<>();
        do {
            retval.add(matcher.group(1));
        } while (matcher.find());
        return retval.toArray(new String[retval.size()]);
    }

    /**
     * Extends an SQL statement with enough <code>'?'</code> characters in the last <code>IN</code> argument.
     *
     * @param sql The SQL statement ending with <code>"IN ("</code>
     * @param length The number of entries.
     * @return The ready to use SQL statement.
     * @throws IllegalArgumentException If <code>sql</code> is <code>null</code> <i>OR</i> <code>length</code> is less than or equal to <code>0</code> (zero)
     */
    public static String getIN(String sql, int length) {
        if (null == sql) {
            throw new IllegalArgumentException("SQL statement must not be null");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder retval = new StringBuilder(sql);
        for (int i = length; i-- > 0;) {
            retval.append("?,");
        }
        retval.setCharAt(retval.length() - 1, ')');
        return retval.toString();
    }

    /**
     * Appends an SQL statement with enough <code>'?'</code> characters in the last <code>IN</code> argument and appends the closing <code>")"</code>.
     * <p>
     * <b>Note</b>: SQL statement is expected to end with <code>"IN ("</code>
     *
     * @param length The number of entries.
     * @return The ready to use SQL statement.
     * @throws IllegalArgumentException If <code>sql</code> is <code>null</code> <i>OR</i> <code>length</code> is less than or equal to <code>0</code> (zero)
     */
    public static String appendIN(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder retval = new StringBuilder(length << 1);
        retval.append('?');
        for (int i = length - 1; i-- > 0;) {
            retval.append(",?");
        }
        retval.append(')');
        return retval.toString();
    }

    /**
     * Gets an SQL clause for the given number of place holders, i.e. either <code>=?</code> if <code>count</code> is <code>1</code>, or
     * an <code>IN</code> clause like <code>IN (?,?,?,?)</code> in case <code>count</code> is greater than <code>1</code>.
     *
     * @param count The number of place holders to append
     * @return The placeholder string
     * @throws IllegalArgumentException if count is <code>0</code> or negative
     */
    public static String getPlaceholders(int count) {
        if (0 >= count) {
            throw new IllegalArgumentException("count");
        }
        if (1 == count) {
            return "=?";
        }
        StringBuilder stringBuilder = new StringBuilder(6 + 2 * count);
        stringBuilder.append(" IN (?");
        for (int i = 1; i < count; i++) {
            stringBuilder.append(",?");
        }
        stringBuilder.append(')');
        return stringBuilder.toString();
    }

    /**
     * This method determines the size of a database column. For strings it gives the maximum allowed characters and for number it returns
     * the precision.
     *
     * @param con read only database connection.
     * @param table name of the table.
     * @param column name of the column.
     * @return the size or <code>-1</code> if the column is not found.
     * @throws SQLException if some exception occurs reading from database.
     */
    public static int getColumnSize(Connection con, String table, String column) throws SQLException {
        DatabaseMetaData metas = con.getMetaData();
        int retval = -1;
        try (ResultSet result = metas.getColumns(null, null, table, column)) {
            if (result.next()) {
                retval = result.getInt("COLUMN_SIZE");
            }
        }
        return retval;
    }

    /**
     * Filters a given list of tablenames. Returns only those that also exist
     *
     * @param con The connection to the database in which to check for the tables
     * @param tablesToCheck The list of table names to check for.
     * @return A set with all the tables that exist of those to be checked for
     * @throws SQLException If something goes wrong
     */
    public static Set<String> existingTables(Connection con, String... tablesToCheck) throws SQLException {
        Set<String> tables = new HashSet<>();
        for (String table : tablesToCheck) {
            if (tableExists(con, table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    /**
     * Finds out whether all tables listed exist in the given database
     *
     * @param con The connection to the database in which to check for the tables
     * @param tablesToCheck The list of table names to check for.
     * @return A set with all the tables that exist of those to be checked for
     * @throws SQLException If something goes wrong
     */
    public static boolean tablesExist(Connection con, String... tablesToCheck) throws SQLException {
        for (String table : tablesToCheck) {
            if (!tableExists(con, table)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds out whether a table listed exist in the given database
     *
     * @param con The connection to the database in which to check for the tables
     * @param table The table name to check for.
     * @return <code>true</code> if the table exists and the found table name matches
     *         <code>false</code> otherwise
     * @throws SQLException If something goes wrong
     */
    public static boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        ResultSet rs = null;
        try {
            rs = metaData.getTables(null, null, table, new String[] { "TABLE" });
            if (false == rs.next()) {
                return false;
            }
            String foundTable = rs.getString("TABLE_NAME");
            closeSQLStuff(rs);
            rs = null;
            if (table.equals(foundTable)) {
                return true;
            }
            if (table.equalsIgnoreCase(foundTable)) {
                /*
                 * assume table exists based on 'lower_case_table_names' configuration
                 */
                try (PreparedStatement stmt = con.prepareStatement("SHOW variables LIKE 'lower_case_table_names';"); ResultSet result = stmt.executeQuery()) {
                    if (result.next()) {
                        switch (result.getInt("Value")) {
                            case 1: // table names are stored in lowercase, name comparisons are case-insensitive ("windows")
                            case 2: // table names are stored in specified lettercase, but lowercased on lookup, name comparisons are case-insensitive ("mac os")
                                return true;
                            case 0: // table names are stored in specified lettercase, name comparisons are case-sensitive ("unix")
                            default:
                                return false;
                        }
                    }
                }
            }
            return false;
        } finally {
            closeSQLStuff(rs);
        }
    }

    private static final Pattern DUPLICATE_KEY = Pattern.compile("Duplicate entry '([^']+)' for key '([^']+)'");
    private static final Pattern DUPLICATE_KEY_MYSQL_PRE51 = Pattern.compile("Duplicate entry '([^']+)' for key 1");

    /**
     * Checks if given {@link SQLException} instance denotes an integrity constraint violation due to a PRIMARY KEY conflict.
     *
     * @param e The <code>SQLException</code> instance to check
     * @return <code>true</code> if given {@link SQLException} instance denotes a PRIMARY KEY conflict; otherwise <code>false</code>
     */
    public static boolean isPrimaryKeyConflictInMySQL(SQLException e) {
        return isKeyConflictInMySQL(e, "PRIMARY") || isPrimaryKeyConflictInMySQL50(e);
    }

    private static boolean isPrimaryKeyConflictInMySQL50(SQLException e) {
        if (null == e) {
            return false;
        }
        /*
         * SQLState 23000: Integrity Constraint Violation
         * Error: 1586 SQLSTATE: 23000 (ER_DUP_ENTRY_WITH_KEY_NAME)
         * Error: 1062 SQLSTATE: 23000 (ER_DUP_ENTRY)
         * com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException: Duplicate entry 'some-data' for key 1
         * Message: Duplicate entry '%s' for key 1
         */
        if ("23000".equals(e.getSQLState())) {
            int errorCode = e.getErrorCode();
            if (1062 == errorCode || 1586 == errorCode) {
                Matcher matcher = DUPLICATE_KEY_MYSQL_PRE51.matcher(e.getMessage());
                return matcher.matches();
            }
        }
        return false;
    }

    /**
     * Checks if given {@link SQLException} instance denotes an integrity constraint violation due to a conflict caused by the specified key.
     *
     * @param e The <code>SQLException</code> instance to check
     * @param keyName The name of the key causing the integrity constraint violation
     * @return <code>true</code> if given {@link SQLException} instance denotes a conflict caused by the specified key; otherwise <code>false</code>
     */
    public static boolean isKeyConflictInMySQL(SQLException e, String keyName) {
        if (null == e || null == keyName) {
            return false;
        }

        /*
         * SQLState 23000: Integrity Constraint Violation
         * Error: 1586 SQLSTATE: 23000 (ER_DUP_ENTRY_WITH_KEY_NAME)
         * Error: 1062 SQLSTATE: 23000 (ER_DUP_ENTRY)
         * com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException: Duplicate entry 'some-data' for key 'key-name'
         * Message: Duplicate entry '%s' for key '%s'
         */
        if ("23000".equals(e.getSQLState())) {
            int errorCode = e.getErrorCode();
            if (1062 == errorCode || 1586 == errorCode) {
                Matcher matcher = DUPLICATE_KEY.matcher(e.getMessage());
                if (matcher.matches() && keyName.equals(matcher.group(2))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if given SQL exception represents a duplicate key conflict in MySQL.
     *
     * @param e The SQL exception to check
     * @return <code>true</code> for duplicate key conflict; otherwise <code>false</code>
     */
    public static boolean isDuplicateKeyConflictInMySQL(SQLException e) {
        if ("23000".equals(e.getSQLState())) {
            int errorCode = e.getErrorCode();
            return (1062 == errorCode || 1586 == errorCode);
        }
        return false;
    }

    /**
     * Checks if given {@link SQLException} instance is caused by a (recoverable) socket read timeout.
     *
     * @param e The <code>SQLException</code> instance to check
     * @return <code>true</code> if given {@link SQLException} instance is a read timeout; otherwise <code>false</code>
     */
    public static boolean isReadTimeout(SQLException e) {
        return (e instanceof java.sql.SQLRecoverableException) && ExceptionUtils.isEitherOf(e.getCause(), java.net.SocketTimeoutException.class);
    }

    /**
     * Code is correct and will not leave a connection in CLOSED_WAIT state. See CloseWaitTest.java.
     */
    public static void close(Connection con) {
        if (null == con) {
            return;
        }
        try {
            if (!con.isClosed()) {
                con.close();
            }
        } catch (SQLException e) {
            LOG.error("Failed to close database connection", e);
        }
    }

    /**
     * Rolls specified connection back to given save-point.
     *
     * @param con The connection to roll-back
     * @param savePoint The save-point to restore to
     */
    public static void rollback(Connection con, Savepoint savePoint) {
        if (null == con || null == savePoint) {
            return;
        }
        try {
            if (!con.isClosed()) {
                con.rollback(savePoint);
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Checks if specified column exists.
     *
     * @param con The connection
     * @param table The table name
     * @param column The column name
     * @return <code>true</code> if specified column exists; otherwise <code>false</code>
     * @throws SQLException If an SQL error occurs
     */
    public static boolean columnExists(final Connection con, final String table, final String column) throws SQLException {
        final DatabaseMetaData metaData = con.getMetaData();
        ResultSet rs = null;
        boolean retval = false;
        try {
            rs = metaData.getColumns(null, null, table, column);
            while (rs.next()) {
                retval = rs.getString(4).equalsIgnoreCase(column);
            }
        } finally {
            closeSQLStuff(rs);
        }
        return retval;
    }

    /**
     * Checks if specified columns exist.
     *
     * @param con The connection
     * @param table The table name
     * @param columns Array of column names
     * @return <code>true</code> if specified columns exist; otherwise <code>false</code>
     * @throws SQLException If an SQL error occurs
     */
    public static boolean columnsExist(final Connection con, final String table, final String... columns) throws SQLException {
        final DatabaseMetaData metaData = con.getMetaData();
        ResultSet rs = null;
        ConcurrentList<String> expectedColumns = new ConcurrentList<>(Arrays.asList(columns));
        try {
            rs = metaData.getColumns(null, null, table, null);
            while (rs.next()) {
                String string = rs.getString(4);
                expectedColumns.removeIf(x -> x.contains(string));
                if (expectedColumns.isEmpty()) {
                    return true;
                }
            }
        } finally {
            closeSQLStuff(rs);
        }
        return expectedColumns.isEmpty() ? true : false;
    }

    /**
     * Gets the underlying character set of the supplied database connection, based on the <code>character_set_connection</code> variable.
     *
     * @param connection The connection to determine the character set for
     * @return The connection's character set
     * @throws SQLException In case the character set cannot be determined
     */
    public static String getCharacterSet(Connection connection) throws SQLException {
        String schemaName = connection.getCatalog();
        if (null == schemaName) {
            LOG.warn("Unable to derive schema name for connection {}, evaluating character set dynamically.", connection);
            return readCharacterSet(connection);
        }
        try {
            return CHARSETS_BY_SCHEMA.get(schemaName, new Callable<String>() {

                @Override
                public String call() throws Exception {
                    return readCharacterSet(connection);
                }
            });
        } catch (ExecutionException e) {
            throw SQLException.class.isInstance(e.getCause()) ? (SQLException) e.getCause() : new SQLException(e.getCause());
        }
    }

    /**
     * Reads out the underlying character set of the supplied database connection, based on the <code>character_set_connection</code>
     * variable.
     *
     * @param connection The connection to determine the character set for
     * @return The connection's character set
     * @throws SQLException In case the character set cannot be determined
     */
    private static String readCharacterSet(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SHOW VARIABLES LIKE 'character_set_connection';"); ResultSet resultSet = stmt.executeQuery()) {
            if (false == resultSet.next()) {
                throw new SQLException("Unable to determine 'character_set_connection'");
            }
            String value = resultSet.getString("Value");
            LOG.debug("'character_set_connection' evaluated to \"{}\" on database schema \"{}\".", value, connection.getCatalog());
            return value;
        }
    }

    /**
     * Checks whether the specified {@link Connection} is in a transaction mode.
     *
     * @param connection The {@link Connection}
     * @return <code>true</code> if the {@link Connection} is in a transaction mode; <code>false</code> otherwise
     * @throws SQLException In case any SQL error occurs
     * @throws IllegalArgumentException if the {@link Connection} is <code>null</code>
     * @throws IllegalStateException if the {@link Connection} is already closed
     */
    public static boolean isInTransaction(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("The connection can not be 'null'.");
        }
        if (connection.isClosed()) {
            throw new IllegalStateException("The connection is already closed.");
        }
        return false == connection.getAutoCommit();
    }

    /**
     * Removes possible parameters appended to specified JDBC URL and returns it.
     *
     * @param url The URL to remove possible parameters from
     * @return The parameter-less JDBC URL
     */
    public static String removeParametersFromJdbcUrl(String url) {
        return JdbcPropertiesImpl.doRemoveParametersFromJdbcUrl(url);
    }

    /**
     * Checks if passed <tt>SQLException</tt> (or any of chained <tt>SQLException</tt>s) indicates a failed transaction roll-back.
     *
     * <pre>
     * Deadlock found when trying to get lock; try restarting transaction
     * </pre>
     *
     * @param sqlException The SQL exception to check
     * @return <code>true</code> if a failed transaction roll-back is indicated; otherwise <code>false</code>
     */
    public static boolean isTransactionRollbackException(final SQLException sqlException) {
        if (null == sqlException) {
            return false;
        }
        if (suggestsRestartingTransaction(sqlException) || sqlException.getClass().getName().endsWith("TransactionRollbackException")) {
            return true;
        }
        if (isTransactionRollbackException(sqlException.getNextException())) {
            return true;
        }
        final Throwable cause = sqlException.getCause();
        if (null == cause || !(cause instanceof Exception)) {
            return false;
        }
        return isTransactionRollbackException((Exception) cause);
    }

    /**
     * Checks if passed <tt>SQLException</tt> (or any of chained <tt>SQLException</tt>s) indicates a failed transaction roll-back.
     *
     * <pre>
     * Deadlock found when trying to get lock; try restarting transaction
     * </pre>
     *
     * @param exception The exception to check
     * @return <code>true</code> if a failed transaction roll-back is indicated; otherwise <code>false</code>
     */
    public static boolean isTransactionRollbackException(final Exception exception) {
        if (null == exception) {
            return false;
        }
        if (exception instanceof SQLException) {
            return isTransactionRollbackException((SQLException) exception);
        }
        final Throwable cause = exception.getCause();
        if (null == cause || !(cause instanceof Exception)) {
            return false;
        }
        return isTransactionRollbackException((Exception) cause);
    }

    /**
     * Checks if specified SQL exception's detail message contains a suggestion to restart the transaction;<br>
     * e.g. <code>"Lock wait timeout exceeded; try restarting transaction"</code>
     *
     * @param sqlException The SQL exception to check
     * @return <code>true</code> if SQL exception suggests restarting transaction; otherwise <code>false</code>
     */
    public static boolean suggestsRestartingTransaction(SQLException sqlException) {
        String message = null == sqlException ? null : sqlException.getMessage();
        return null != message && Strings.asciiLowerCase(message).indexOf("try restarting transaction") >= 0;
    }

    /**
     * Extracts possibly nested <tt>SQLException</tt> reference.
     *
     * @param exception The parental exception to extract from
     * @return The <tt>SQLException</tt> reference or <code>null</code>
     */
    public static SQLException extractSqlException(final Exception exception) {
        if (null == exception) {
            return null;
        }
        if (exception instanceof SQLException) {
            return (SQLException) exception;
        }
        final Throwable cause = exception.getCause();
        if (null == cause || !(cause instanceof Exception)) {
            return null;
        }
        return extractSqlException((Exception) cause);
    }

    /**
     * Gets the value for 'max_allowed_packet' setting.
     *
     * @param con The connection to use
     * @return The max. allowed packet size in bytes or <code>-1</code> if unknown
     * @throws SQLException If an SQL error occurs
     */
    public static long getMaxAllowedPacketSize(Connection con) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SHOW variables LIKE 'max_allowed_packet'");
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt("Value");
            }
        } finally {
            closeSQLStuff(result, stmt);
        }
        return -1;
    }

    /**
     * Checks if given SQL exception represents a "package too big" SQL error, which is thrown when a packet is created that is too big for
     * the database server.
     *
     * @param e The SQL exception to examine
     * @return <code>true</code> if SQL exception represents a "package too big" SQL error; otherwise <code>false</code>
     */
    public static boolean isPacketTooBigException(SQLException e) {
        return e instanceof com.mysql.jdbc.PacketTooBigException;
    }

    // ------------------------------------------------- Callback-accepting methods --------------------------------------------------------

    /**
     * Executes an SQL query.
     *
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param connection The optional connection. If <code>null</code>, a new connection for config database is fetched from the service
     * @param statement The statement to execute
     * @param rc The consumer of the result to use transform into a concrete java object
     * @param valueSetters The valueSetters to fill the statement with variables
     * @throws SQLException In case of an SQL error
     * @throws OXException In all other error cases
     */
    public static void executeAndConsumeQuery(DatabaseService databaseService, Connection connection, ResultConsumer rc, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        if (null == connection) {
            executeAndConsumeQuery(-1, databaseService, rc, statement, valueSetters);
        } else {
            executeAndConsumeQuery(connection, rc, statement, valueSetters);
        }
    }

    /**
     * Executes an SQL query.
     *
     * @param <T> The class of the response object
     * @param databaseService The database service
     * @param connection The connection to use or <code>null</code> to obtain a new connection for config database
     * @param producer The producer for the result object
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return See {@link PreparedStatement#executeUpdate()}
     * @throws SQLException In case of an SQL error
     * @throws OXException In case result can't be produced
     */
    public static <T> T executeQuery(DatabaseService databaseService, Connection connection, ResultProduccer<T> producer, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        if (null == connection) {
            return executeQuery(-1, databaseService, producer, statement, valueSetters);
        }
        return executeQuery(connection, producer, statement, valueSetters);
    }

    /**
     * Executes an SQL query against config database.
     *
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param statement The statement to execute
     * @param rc The consumer of the result to use transform into a concrete java object
     * @param valueSetters The valueSetters to fill the statement with variables
     * @throws SQLException In case of an SQL error
     * @throws OXException In all other error cases
     */
    public static void executeAndConsumeQuery(DatabaseService databaseService, ResultConsumer rc, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        executeAndConsumeQuery(-1, databaseService, rc, statement, valueSetters);
    }

    /**
     * Executes an SQL query against either context-associated database or config database.
     *
     * @param <T> The class of the result object
     * @param contextId The context identifier to use when obtaining the connection. <code>-1</code> to fetch connection to the config database
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param producer The producer to produce the result object
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return The result instance of given type or <code>null</code>
     * @throws SQLException In case of an SQL error
     * @throws OXException In all other error cases
     */
    public static <T> T executeQuery(int contextId, DatabaseService databaseService, ResultProduccer<T> producer, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        Connection connection = contextId <= 0 ? databaseService.getReadOnly() : databaseService.getReadOnly(contextId);
        try {
            return executeQuery(connection, producer, statement, valueSetters);
        } finally {
            if (contextId <= 0) {
                databaseService.backReadOnly(connection);
            } else {
                databaseService.backReadOnly(contextId, connection);
            }
        }
    }

    /**
     * Executes an SQL query against either context-associated database or config database.
     *
     * @param contextId The context identifier to use when obtaining the connection. <code>-1</code> to fetch connection to the config database
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param statement The statement to execute
     * @param rc The consumer of the result to use transform into a concrete java object
     * @param valueSetters The valueSetters to fill the statement with variables
     * @throws SQLException In case of an SQL error
     * @throws OXException In all other error cases
     */
    public static void executeAndConsumeQuery(int contextId, DatabaseService databaseService, ResultConsumer rc, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        Connection connection = contextId <= 0 ? databaseService.getReadOnly() : databaseService.getReadOnly(contextId);
        try {
            executeAndConsumeQuery(connection, rc, statement, valueSetters);
        } finally {
            if (contextId <= 0) {
                databaseService.backReadOnly(connection);
            } else {
                databaseService.backReadOnly(contextId, connection);
            }
        }
    }

    /**
     * Executes an SQL update on the config database.
     *
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return See {@link PreparedStatement#executeUpdate()} or <code>-1</code> if no connection could be obtained
     * @throws SQLException In case of an SQL error
     * @throws OXException If no connection can be obtained
     */
    public static int executeUpdate(DatabaseService databaseService, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        return executeUpdate(-1, databaseService, statement, valueSetters);
    }

    /**
     * Executes an SQL update on either context-associated database or config database.
     *
     * @param contextId The context identifier to use when obtaining the connection. <code>-1</code> to fetch connection to the config database
     * @param databaseService The {@link DatabaseService} to obtain the connection from
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return See {@link PreparedStatement#executeUpdate()}
     * @throws SQLException In case of an SQL error
     * @throws OXException If no connection can be obtained
     */
    public static int executeUpdate(int contextId, DatabaseService databaseService, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        int rollback = 0;
        boolean modified = false;
        Connection connection = contextId <= 0 ? databaseService.getWritable() : databaseService.getWritable(contextId);
        try {
            // Start transaction
            connection.setAutoCommit(false);
            rollback = 1;

            // Perform update
            int result = executeUpdate(connection, statement, valueSetters);
            modified = result > 0;

            // Commit changes (if any) & return result
            connection.commit();
            rollback = 2;
            return result;
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    rollback(connection);
                }
                autocommit(connection);
            }
            if (contextId <= 0) {
                if (modified) {
                    databaseService.backWritable(connection);
                } else {
                    databaseService.backWritableAfterReading(connection);
                }
            } else {
                if (modified) {
                    databaseService.backWritable(contextId, connection);
                } else {
                    databaseService.backWritableAfterReading(contextId, connection);
                }
            }
        }
    }

    /**
     * Executes an SQL query and passes the data produced by the query to given result consumer.
     *
     * @param connection The connection to use
     * @param rc The consumer of the result to use transform into a concrete java object
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @throws SQLException In case of an SQL error
     * @throws OXException In all other error cases
     */
    public static void executeAndConsumeQuery(Connection connection, ResultConsumer rc, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(statement);
            rs = executeQuery(stmt, valueSetters);
            while (rs.next()) {
                rc.consume(rs);
            }
        } finally {
            closeSQLStuff(stmt, rs);
        }
    }

    /**
     * Executes an SQL query and returns
     * <ul>
     * <li>either producer's result</li>
     * <li>or <code>null</code> if given statement yields no resulting rows</li>
     * </ul>
     *
     * @param <T> The class of the response object
     * @param connection The connection to use
     * @param producer The producer for the result object
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return Either the producer's result of given type or <code>null</code>
     * @throws SQLException If an SQL error occurs
     * @throws OXException If result cannot be produced when calling {@link ResultProduccer#produce(ResultSet)}
     */
    public static <T> T executeQuery(Connection connection, ResultProduccer<T> producer, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException, OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = connection.prepareStatement(statement);
            rs = executeQuery(stmt, valueSetters);
            return rs.next() ? producer.produce(rs) : null;
        } finally {
            closeSQLStuff(stmt, rs);
        }
    }

    /**
     * Executes an SQL update and returns
     * <ul>
     * <li>either (1) the row count for SQL Data Manipulation Language (DML) statements</li>
     * <li>or (2) <code>0</code> for SQL statements that return nothing</li>
     * </ul>
     *
     * @param connection The connection to use
     * @param statement The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return Either (1) the row count for SQL Data Manipulation Language (DML) statements
     *         or (2) <code>0</code> for SQL statements that return nothing
     * @throws SQLException In case of an SQL error
     */
    public static int executeUpdate(Connection connection, String statement, PreparedStatementValueSetter... valueSetters) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(statement);
            return executeUpdate(stmt, valueSetters);
        } finally {
            closeSQLStuff(stmt);
        }
    }

    /**
     * Executes an SQL query and returns a <code>ResultSet</code> object that contains the data produced by the query.
     *
     * @param stmt The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return A <code>ResultSet</code> object that contains the data produced by the query; never <code>null</code>
     * @throws SQLException In case of error
     */
    public static ResultSet executeQuery(PreparedStatement stmt, PreparedStatementValueSetter... valueSetters) throws SQLException {
        return execute(stmt, PreparedStatement::executeQuery, valueSetters);
    }

    /**
     * Executes an SQL update and returns
     * <ul>
     * <li>either (1) the row count for SQL Data Manipulation Language (DML) statements</li>
     * <li>or (2) <code>0</code> for SQL statements that return nothing</li>
     * </ul>
     *
     * @param stmt The statement to execute
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return Either (1) the row count for SQL Data Manipulation Language (DML) statements
     *         or (2) <code>0</code> for SQL statements that return nothing
     * @throws SQLException In case of error
     */
    public static int executeUpdate(PreparedStatement stmt, PreparedStatementValueSetter... valueSetters) throws SQLException {
        return i(execute(stmt, s -> I(s.executeUpdate()), valueSetters));
    }

    /**
     * Executes the given statement with the given execute function.
     *
     * @param <T> The result to return
     * @param stmt The statement to execute
     * @param executionFunction The executing function
     * @param valueSetters The valueSetters to fill the statement with variables
     * @return The value returned by {@link SQLExecutorFunction#execute(PreparedStatement)}
     * @throws SQLException In case of error
     */
    public static <T> T execute(PreparedStatement stmt, SQLExecutorFunction<T> executionFunction, PreparedStatementValueSetter... valueSetters) throws SQLException {
        for (PreparedStatementValueSetter setter : valueSetters) {
            setter.setValue(stmt);
        }
        return executionFunction.execute(stmt);
    }

    /**
     * {@link SQLExecutorFunction}
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.4
     * @param <T> The class of the result value
     */
    @FunctionalInterface
    public interface SQLExecutorFunction<T> {

        /**
         * Executes the given statement and returns a specific value.
         *
         * @param stmt The statement to execute
         * @return The value to return
         * @throws SQLException In case of SQL error
         */
        T execute(PreparedStatement stmt) throws SQLException;
    }

    /**
     *
     * {@link PreparedStatementValueSetter}
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.4
     */
    @FunctionalInterface
    public interface PreparedStatementValueSetter {

        /**
         * Sets a value to the supplied {@link PreparedStatement}.
         *
         * @param stmt The {@link PreparedStatement} to fill with values.
         * @throws SQLException If setting values fails
         */
        void setValue(PreparedStatement stmt) throws SQLException;
    }

    /**
     *
     * {@link ResultConsumer} - Accepts an instance of {@link ResultSet} and consumes its currently selected row.
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @since v7.10.4
     */
    @FunctionalInterface
    public interface ResultConsumer {

        /**
         * Consumes a {@link ResultSet} that has already a moved cursor, frankly speaking the {@link ResultSet#next()} was already called.
         * The method will be called until {@link ResultSet#next()} signals <code>false</code>.
         * <p>
         * It is possible that the method won't be called at all. This means that there has been no result to consume.
         *
         * @param rs The result set with moved cursor
         * @throws SQLException In case of an SQL related error
         * @throws OXException In case of other errors
         */
        void consume(ResultSet rs) throws SQLException, OXException;
    }

    /**
     * {@link ResultProduccer} - Accepts an instance of {@link ResultSet} and parses the currently selected row to an instance of given
     * result type.
     *
     * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
     * @param <T> The class of the result object
     * @since v7.10.4
     */
    @FunctionalInterface
    public interface ResultProduccer<T> {

        /**
         * Consumes a {@link ResultSet} that has already a moved cursor, frankly speaking the {@link ResultSet#next()} was already called.
         * The method will be called at most once.
         * <p>
         * It is possible that the method won't be called at all. This means that there has been no result to consume.
         *
         * @param rs The result set with moved cursor
         * @return The object to create
         * @throws SQLException In case of an SQL related error
         * @throws OXException In case of other errors
         */
        T produce(ResultSet rs) throws SQLException, OXException;
    }

}
