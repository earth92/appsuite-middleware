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

package com.openexchange.database.internal.wrapping;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.openexchange.database.DatabaseConnectionListener;
import com.openexchange.database.DatabaseConnectionListenerAnnotatable;
import com.openexchange.database.Databases;
import com.openexchange.database.GeneralDatabaseConnectionListener;
import com.openexchange.database.Heartbeat;
import com.openexchange.database.internal.AssignmentImpl;
import com.openexchange.database.internal.ConnectionState;
import com.openexchange.database.internal.Initialization;
import com.openexchange.database.internal.MysqlUtils;
import com.openexchange.database.internal.Pools;
import com.openexchange.database.internal.ReplicationMonitor;
import com.openexchange.database.internal.StateAware;
import com.openexchange.osgi.ServiceListing;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;

/**
 * {@link JDBC4ConnectionReturner}
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public abstract class JDBC4ConnectionReturner implements Connection, StateAware, Heartbeat, DatabaseConnectionListenerAnnotatable {

    /** The logger constant */
    static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JDBC4ConnectionReturner.class);

    private static final class HeartBeatHelper {

        private final long timeoutMillis;
        private final Lock readLock;
        final Lock writeLock;
        final AtomicLong lastAccessed;
        final Connection con;
        private volatile ScheduledTimerTask timerTask;

        HeartBeatHelper(Connection con) {
            super();
            this.con = con;
            this.timeoutMillis = getSocketTimeout(con);
            lastAccessed = new AtomicLong(System.currentTimeMillis());
            ReadWriteLock readWriteLock = new ReentrantReadWriteLock(false);
            readLock = readWriteLock.readLock();
            writeLock = readWriteLock.writeLock();
        }

        /**
         * Touches this connection; updates its last-accessed time stanp
         */
        public void touch() {
            readLock.lock();
            try {
                lastAccessed.set(System.currentTimeMillis());
            } finally {
                readLock.unlock();
            }
        }

        /**
         * Starts the hear-beat using given timer service
         *
         * @param timer The timer service to use
         */
        public void startHeartbeat(TimerService timer) {
            long timeoutMillis = this.timeoutMillis > 0 ? this.timeoutMillis : 15000;
            final long maxIdleTime = timeoutMillis - 3000L;
            Runnable keepAliveTask = new Runnable() {

                @Override
                public void run() {
                    long idleTime = System.currentTimeMillis() - lastAccessed.get();
                    if (idleTime > maxIdleTime) {
                        writeLock.lock();
                        try {
                            Statement statement = null;
                            ResultSet rs = null;
                            try {
                                statement = con.createStatement();
                                rs = statement.executeQuery("SELECT 1 AS keep_alive_test");
                                LOGGER.debug("Performed \"SELECT 1 AS keep_alive_test\" to keep connection alive");
                                while (rs.next()) {
                                    // Discard
                                }
                                lastAccessed.set(System.currentTimeMillis()); // Obviously...
                            } finally {
                                Databases.closeSQLStuff(rs, statement);
                            }
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            writeLock.unlock();
                        }
                    }
                }
            };
            timerTask = timer.scheduleWithFixedDelay(keepAliveTask, timeoutMillis - 1000L, 2500L, TimeUnit.MILLISECONDS);
        }

        /**
         * Stops the heart-beat
         */
        public void stopHeartbeat() {
            ScheduledTimerTask timerTask = this.timerTask;
            if (null != timerTask) {
                this.timerTask = null;
                timerTask.cancel(true);
            }
        }
    }

    static long getSocketTimeout(Connection con) {
        if (!(con instanceof com.mysql.jdbc.ConnectionProperties)) {
            return 0L;
        }

        com.mysql.jdbc.ConnectionProperties connectionProperties = (com.mysql.jdbc.ConnectionProperties) con;
        int socketTimeout = connectionProperties.getSocketTimeout();
        return socketTimeout <= 0 ? 0L : socketTimeout;
    }

    // -------------------------------------------------------------------------------------------------------------------

    private final Pools pools;
    private final ReplicationMonitor monitor;
    private final AssignmentImpl assign;
    private final boolean noTimeout;
    private final boolean write;
    private final AtomicBoolean closed;

    protected final ConnectionState state;
    protected final Connection delegate;

    private final AtomicReference<HeartBeatHelper> heartBeatReference;

    private final ServiceListing<GeneralDatabaseConnectionListener> generalConnectionListeners;
    private final Queue<DatabaseConnectionListener> connectionListeners;

    public JDBC4ConnectionReturner(Pools pools, ReplicationMonitor monitor, AssignmentImpl assign, Connection delegate, boolean noTimeout, boolean write, boolean usedAsRead) {
        super();
        this.pools = pools;
        this.monitor = monitor;
        generalConnectionListeners = monitor.getConnectionListeners();
        connectionListeners = new ConcurrentLinkedQueue<DatabaseConnectionListener>();
        this.assign = assign;
        this.delegate = delegate;
        this.noTimeout = noTimeout;
        this.write = write;
        closed = new AtomicBoolean(false);
        state = new ConnectionState(usedAsRead);
        heartBeatReference = new AtomicReference<>(null);
    }

    @Override
    public boolean startHeartbeat() {
        if (noTimeout) {
            // No need for heart beat
            return false;
        }

        if (closed.get()) {
            return false;
        }

        TimerService timer = Initialization.getInstance().getTimer().getTimer();
        if (null == timer) {
            return false;
        }

        HeartBeatHelper heartBeat = new HeartBeatHelper(delegate);
        if (false == heartBeatReference.compareAndSet(null, heartBeat)) {
            return false;
        }

        heartBeat.startHeartbeat(timer);
        state.setHeartbeatEnabled(true);
        return true;
    }

    @Override
    public void stopHeartbeat() {
        HeartBeatHelper heartBeat = heartBeatReference.getAndSet(null);
        if (null != heartBeat) {
            heartBeat.stopHeartbeat();
        }
    }

    @Override
    public void addListener(DatabaseConnectionListener listener) {
        connectionListeners.offer(listener);
    }

    @Override
    public void removeListener(DatabaseConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    @Override
    public void commit() throws SQLException {
        checkForAlreadyClosed();
        if (write && state.isUsedForUpdate()) {
            if (!delegate.getAutoCommit()) {
                // For performance reasons we increase the replication counter within a possibly active transaction.
                monitor.increaseInCurrentTransaction(assign, delegate, state);
            }
        }

        List<GeneralDatabaseConnectionListener> generalListeners = generalConnectionListeners.getServiceList();
        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onBeforeCommitPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeCommitPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onBeforeCommitPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeCommitPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }

        delegate.commit();

        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onAfterCommitPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterCommitPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onAfterCommitPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterCommitPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        if (false == closed.compareAndSet(false, true)) {
            throw new SQLException("Connection is already closed.");
        }

        stopHeartbeat();
        connectionListeners.clear();
        monitor.backAndIncrementTransaction(pools, assign, delegate, noTimeout, write, state);
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkForAlreadyClosed();
        delegate.clearWarnings();
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41StatementWrapper(delegate.createStatement(), this);
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41StatementWrapper(delegate.createStatement(resultSetType, resultSetConcurrency), this);
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41StatementWrapper(delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), this);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getAutoCommit();
    }

    @Override
    public String getCatalog() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getCatalog();
    }

    @Override
    public int getHoldability() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getHoldability();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getMetaData();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getTransactionIsolation();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getTypeMap();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getWarnings();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return delegate == null || delegate.isClosed();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkForAlreadyClosed();
        return delegate.isReadOnly();
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
        checkForAlreadyClosed();
        return delegate.nativeSQL(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkForAlreadyClosed();
        return delegate.prepareCall(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkForAlreadyClosed();
        return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        checkForAlreadyClosed();
        return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql), this);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql, autoGeneratedKeys), this);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency), this);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), this);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql, columnIndexes), this);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        checkForAlreadyClosed();
        return new JDBC41PreparedStatementWrapper(delegate.prepareStatement(sql, columnNames), this);
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
        checkForAlreadyClosed();
        delegate.releaseSavepoint(savepoint);
    }

    @Override
    public void rollback() throws SQLException {
        checkForAlreadyClosed();

        List<GeneralDatabaseConnectionListener> generalListeners = generalConnectionListeners.getServiceList();
        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onBeforeRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onBeforeRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }

        delegate.rollback();

        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onAfterRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onAfterRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
        checkForAlreadyClosed();

        List<GeneralDatabaseConnectionListener> generalListeners = generalConnectionListeners.getServiceList();
        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onBeforeRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onBeforeRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onBeforeRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }

        delegate.rollback(savepoint);

        for (GeneralDatabaseConnectionListener connectionListener : generalListeners) {
            try {
                connectionListener.onAfterRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onAfterRollbackPerformed(this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAfterRollbackPerformed() for {}", connectionListener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkForAlreadyClosed();
        delegate.setAutoCommit(autoCommit);

        for (GeneralDatabaseConnectionListener connectionListener : generalConnectionListeners) {
            try {
                connectionListener.onAutoCommitChanged(autoCommit, this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAutoCommitChanged() for {}", connectionListener.getClass().getName(), e);
            }
        }
        for (DatabaseConnectionListener connectionListener : connectionListeners) {
            try {
                connectionListener.onAutoCommitChanged(autoCommit, this);
            } catch (Exception e) {
                LOGGER.warn("Failed to invoke onAutoCommitChanged() for {}", connectionListener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkForAlreadyClosed();
        delegate.setCatalog(catalog);
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        checkForAlreadyClosed();
        delegate.setHoldability(holdability);
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        checkForAlreadyClosed();
        delegate.setReadOnly(readOnly);
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkForAlreadyClosed();
        return delegate.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
        checkForAlreadyClosed();
        return delegate.setSavepoint(name);
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        checkForAlreadyClosed();
        delegate.setTransactionIsolation(level);
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        checkForAlreadyClosed();
        delegate.setTypeMap(map);
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkForAlreadyClosed();
        return delegate.createArrayOf(typeName, elements);
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkForAlreadyClosed();
        return delegate.createBlob();
    }

    @Override
    public Clob createClob() throws SQLException {
        checkForAlreadyClosed();
        return delegate.createClob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkForAlreadyClosed();
        return delegate.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkForAlreadyClosed();
        return delegate.createSQLXML();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkForAlreadyClosed();
        return delegate.createStruct(typeName, attributes);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkForAlreadyClosed();
        return delegate.getClientInfo();
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkForAlreadyClosed();
        return delegate.getClientInfo(name);
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        checkForAlreadyClosed();
        return delegate.isValid(timeout);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        delegate.setClientInfo(properties);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        delegate.setClientInfo(name, value);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkForAlreadyClosed();
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        checkForAlreadyClosed();
        if (iface.isInstance(this)) {
            try {
                return iface.cast(this);
            } catch (ClassCastException e) {
                LOGGER.debug("", e);
                // fall through
            }
        }
        return delegate.unwrap(iface);
    }

    public void updatePerformed() {
        state.setUsedForUpdate(true);
        touch();
    }

    public void setUsedAsRead(boolean usedAsRead) {
        state.setUsedAsRead(usedAsRead);
        touch();
    }

    /**
     * Checks if this connection has already been {@link #close() closed}.
     *
     * @throws SQLException If connection has already been closed
     */
    protected void checkForAlreadyClosed() throws SQLException {
        if (closed.get()) {
            throw new SQLNonTransientConnectionException("Connection was already closed.");
        }

        MysqlUtils.ClosedState closedState = MysqlUtils.isClosed(delegate, false);
        if (MysqlUtils.ClosedState.OPEN != closedState) {
            if (MysqlUtils.ClosedState.INTERNALLY_CLOSED == closedState) {
                // Connection explicitly closed or lost its internal network resources, thus unusable
                throw new SQLNonTransientConnectionException("Connection is internally closed.");
            }
            throw new SQLNonTransientConnectionException("Connection is closed.");
        }

        touch();
    }

    /**
     * Touches this connection; updates its last-accessed time stamp
     */
    public void touch() {
        HeartBeatHelper heartBeat = heartBeatReference.get();
        if (null != heartBeat) {
            heartBeat.touch();
        }
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
