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

package com.openexchange.database.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.database.DBPoolingExceptionCodes;
import com.openexchange.database.Databases;
import com.openexchange.database.internal.reloadable.GenericReloadable;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.pooling.PoolableLifecycle;
import com.openexchange.pooling.PooledData;

/**
 * Life cycle for database connections.
 */
class ConnectionLifecycle implements PoolableLifecycle<Connection> {

    /**
     * SQL command for checking the connection.
     */
    private static final String TEST_SELECT = "SELECT 1 AS test";

    private static final AtomicReference<Field> openStatementsField = new AtomicReference<Field>(null);

    private static Field getOpenStatementsField() {
        Field openStatementsField = ConnectionLifecycle.openStatementsField.get();
        if (null == openStatementsField) {
            synchronized (ConnectionLifecycle.class) {
                openStatementsField = ConnectionLifecycle.openStatementsField.get();
                if (null == openStatementsField) {
                    try {
                        openStatementsField = com.mysql.jdbc.ConnectionImpl.class.getDeclaredField("openStatements");
                        openStatementsField.setAccessible(true);
                        ConnectionLifecycle.openStatementsField.set(openStatementsField);
                    } catch (NoSuchFieldException e) {
                        // Unable to retrieve openStatements content.
                        return null;
                    } catch (SecurityException e) {
                        // Unable to retrieve openStatements content.
                        return null;
                    }
                }
            }
        }
        return openStatementsField;
    }

    private static class UrlAndConnectionArgs {

        final String url;
        final Properties connectionArguments;

        UrlAndConnectionArgs(String url, Properties connectionArguments) {
            super();
            this.url = url;
            this.connectionArguments = connectionArguments;
        }
    }

    static volatile Integer usageThreshold;

    private static int usageThreshold() {
        Integer tmp = usageThreshold;
        if (null == tmp) {
            synchronized (ConnectionLifecycle.class) {
                tmp = usageThreshold;
                if (null == tmp) {
                    final int defaultValue = 2000;
                    final ConfigurationService confService = Initialization.getConfigurationService();
                    if (null == confService) {
                        return defaultValue;
                    }

                    tmp = Integer.valueOf(confService.getIntProperty("com.openexchange.database.usageThreshold", defaultValue));
                    usageThreshold = tmp;
                }
            }
        }
        return tmp.intValue();
    }

    static {
        GenericReloadable.getInstance().addReloadable(new Reloadable() {

            @Override
            public void reloadConfiguration(ConfigurationService configService) {
                usageThreshold = null;
            }

            @Override
            public Interests getInterests() {
                return DefaultInterests.builder().propertiesOfInterest("com.openexchange.database.usageThreshold").build();
            }
        });
    }

    // ----------------------------------------------------------------------------------------------

    /**
     * Time between checks if a connection still works.
     */
    private static final long DEFAULT_CHECK_TIME = 120000;

    private final AtomicReference<UrlAndConnectionArgs> urlAndConnectionReference;

    /**
     * Initializes a new {@link ConnectionLifecycle}.
     *
     * @param url A database URL of the form <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param connectionArguments A list of arbitrary string tag/value pairs as connection arguments; normally at least a "user" and "password" property should be included
     */
    public ConnectionLifecycle(final String url, final Properties connectionArguments) {
        super();
        urlAndConnectionReference = new AtomicReference<>(new UrlAndConnectionArgs(url, connectionArguments));
    }

    /**
     * Sets the JDBC URL and connection arguments to use.
     *
     * @param url A database URL of the form <code> jdbc:<em>subprotocol</em>:<em>subname</em></code>
     * @param connectionArguments A list of arbitrary string tag/value pairs as connection arguments; normally at least a "user" and "password" property should be included
     */
    public void setUrlAndConnectionArgs(String url, Properties connectionArguments) {
        urlAndConnectionReference.set(new UrlAndConnectionArgs(url, connectionArguments));
    }

    @Override
    public boolean activate(final PooledData<Connection> data, boolean forceValidityCheck) {
        final Connection con = data.getPooled();

        boolean retval;
        Statement stmt = null;
        ResultSet result = null;
        try {
            retval = MysqlUtils.ClosedState.OPEN == MysqlUtils.isClosed(con, true);
            if (retval && (forceValidityCheck || data.getLastPacketDiffFallbackToTimeDiff() > DEFAULT_CHECK_TIME)) {
                stmt = con.createStatement();
                result = stmt.executeQuery(TEST_SELECT);
                retval = result.next() ? result.getInt(1) == 1 : false;
            }
        } catch (SQLException e) {
            long connectionId = MysqlUtils.getConnectionId(con);
            ConnectionPool.LOG.debug("Test SELECT statement failed ({})", L(connectionId), e);
            retval = false;
        } finally {
            Databases.closeSQLStuff(result, stmt);
        }
        return retval;
    }

    @Override
    public boolean deactivate(final PooledData<Connection> data) {
        boolean retval;
        try {
            retval = MysqlUtils.ClosedState.OPEN == MysqlUtils.isClosed(data.getPooled(), true);
        } catch (SQLException e) {
            retval = false;
        }
        return retval;
    }

    @Override
    public boolean validate(final PooledData<Connection> data, boolean onActivate) {
        final Connection con = data.getPooled();
        boolean retval = true;
        try {
            MysqlUtils.ClosedState closedState = MysqlUtils.isClosed(con, true);
            if (MysqlUtils.ClosedState.OPEN != closedState) {
                /*-
                 * Check whether connection is validated on fetching from pool or on putting it back into the pool.
                 *
                 * The latter case should not be logged since a connection may break during usage ("Communications link failure").
                 * Simply return 'false' as result for the validation to indicate that connection is no more poolable,
                 * in which case the connection gets closed and is discarded.
                 */
                if (onActivate) {
                    long connectionId = MysqlUtils.getConnectionId(con);
                    if (MysqlUtils.ClosedState.EXPLICITLY_CLOSED == closedState) {
                        ConnectionPool.LOG.error("Found closed connection ({}).", L(connectionId), new Throwable("tracked closed connection"));
                    } else {
                        ConnectionPool.LOG.error("Found internally closed connection ({}).", L(connectionId), new Throwable("tracked closed connection"));
                    }
                }
                retval = false;
            } else if (!con.getAutoCommit()) {
                final OXException dbe = DBPoolingExceptionCodes.NO_AUTOCOMMIT.create();
                addTrace(dbe, data);
                ConnectionPool.LOG.error("", dbe);
                con.rollback();
                con.setAutoCommit(true);
            }
            // Getting number of open statements.
            try {
                int active = getNumberOfActiveStatements(con);
                if (active > 0) {
                    final OXException dbe = DBPoolingExceptionCodes.ACTIVE_STATEMENTS.create(I(active));
                    addTrace(dbe, data);
                    String openStatement = "";
                    if (con instanceof com.mysql.jdbc.ConnectionImpl) {
                        Field openStatementsField = getOpenStatementsField();
                        if (null == openStatementsField) {
                            // Unable to retrieve openStatements content. Just log that there is an open statement...
                        } else {
                            CopyOnWriteArrayList<Statement> open = (CopyOnWriteArrayList<Statement>) openStatementsField.get(con);
                            for (Statement statement : open) {
                                openStatement = statement.toString();
                            }
                        }
                    }
                    ConnectionPool.LOG.error(openStatement, dbe);
                    retval = false;
                }
            } catch (Exception e) {
                ConnectionPool.LOG.error("", e);
            }
            // Write warning if using this connection was longer than 2 seconds.
            if (data.getTimeDiff() > usageThreshold()) {
                final OXException dbe = DBPoolingExceptionCodes.TOO_LONG.create(L(data.getTimeDiff()));
                addTrace(dbe, data);
                ConnectionPool.LOG.warn("", dbe);
            }
        } catch (SQLException e) {
            retval = false;
        }
        return retval;
    }

    private int getNumberOfActiveStatements(final Connection con) {
        int active = 0;
        if (con instanceof com.mysql.jdbc.Connection) {
            active = ((com.mysql.jdbc.Connection) con).getActiveStatementCount();
        }
        return active;
    }

    @Override
    public Connection create() throws SQLException {
        UrlAndConnectionArgs urlAndConnectionArgs = urlAndConnectionReference.get();
        return DriverManager.getConnection(urlAndConnectionArgs.url, urlAndConnectionArgs.connectionArguments);
    }

    public Connection createWithoutTimeout() throws SQLException {
        UrlAndConnectionArgs urlAndConnectionArgs = urlAndConnectionReference.get();
        Properties withoutTimeout = new Properties();
        withoutTimeout.putAll(urlAndConnectionArgs.connectionArguments);
        for (Iterator<Object> iter = withoutTimeout.keySet().iterator(); iter.hasNext();) {
            final Object test = iter.next();
            if (String.class.isAssignableFrom(test.getClass()) && Strings.asciiLowerCase(((String) test)).endsWith("timeout")) {
                iter.remove();
            }
        }
        return DriverManager.getConnection(urlAndConnectionArgs.url, withoutTimeout);
    }

    @Override
    public void destroy(final Connection con) {
        Databases.close(con);
        if (ConnectionPool.LOG.isDebugEnabled()) { // Guard with if-statement to avoid unnecessary creation of Throwable instance
            long connectionId = MysqlUtils.getConnectionId(con);
            ConnectionPool.LOG.debug("Connection ({}) closed", L(connectionId), new Throwable("tracked destroyed connection"));
        }
    }

    private static void addTrace(final OXException dbe, final PooledData<Connection> data) {
        if (null != data.getTrace()) {
            dbe.setStackTrace(data.getTrace());
        }
    }

    @Override
    public String getObjectName() {
        return "Database connection";
    }
}
