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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import com.openexchange.database.Assignment;
import com.openexchange.database.DBPoolingExceptionCodes;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.database.SchemaInfo;
import com.openexchange.database.internal.wrapping.JDBC4ConnectionReturner;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.log.LogProperties;
import com.openexchange.pooling.PoolingException;

/**
 * Interface class for accessing the database system.
 * TODO test threads.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class DatabaseServiceImpl implements DatabaseService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private final Pools pools;
    private final ConfigDatabaseServiceImpl configDatabaseService;
    private final GlobalDatabaseServiceImpl globalDatabaseService;
    private final ReplicationMonitor monitor;

    public DatabaseServiceImpl(Pools pools, ConfigDatabaseServiceImpl configDatabaseService, GlobalDatabaseServiceImpl globalDatabaseService, ReplicationMonitor monitor) {
        super();
        this.pools = pools;
        this.configDatabaseService = configDatabaseService;
        this.globalDatabaseService = globalDatabaseService;
        this.monitor = monitor;
    }

    private Connection get(final int contextId, final boolean write, final boolean noTimeout) throws OXException {
        final AssignmentImpl assign = configDatabaseService.getAssignment(contextId);
        setSchemaLogProperty(assign.getSchema());
        Connection connection = get(assign, write, noTimeout);
        return connection;
    }

    private Connection get(final AssignmentImpl assign, final boolean write, final boolean noTimeout) throws OXException {
        setSchemaLogProperty(assign.getSchema());
        Connection connection = monitor.checkActualAndFallback(pools, assign, noTimeout, write);
        return connection;
    }

    private static void back(final Connection con) {
        if (null == con) {
            final OXException e = DBPoolingExceptionCodes.NULL_CONNECTION.create();
            LOG.error("", e);
            return;
        }
        try {
            con.close();
        } catch (SQLException e) {
            final OXException e1 = DBPoolingExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            LOG.error("", e1);
        }
    }

    private static void backFromReading(Connection con) {
        if (null == con) {
            final OXException e = DBPoolingExceptionCodes.NULL_CONNECTION.create();
            LOG.error("", e);
            return;
        }
        try {
            if (con instanceof JDBC4ConnectionReturner) {
                // Not the nice way to tell the replication monitor not to increment the counter.
                ((JDBC4ConnectionReturner) con).setUsedAsRead(true);
            }
            con.close();
        } catch (SQLException e) {
            final OXException e1 = DBPoolingExceptionCodes.SQL_ERROR.create(e, e.getMessage());
            LOG.error("", e1);
        }
    }

    // Delegate config database service methods.

    /**
     * Gets the assignment for specified context identifier
     *
     * @param contextId The context identifier
     * @return The associated assignment
     * @throws OXException If such an assignment cannot be returned
     */
    public AssignmentImpl getAssignment(int contextId) throws OXException {
        return configDatabaseService.getAssignment(contextId);
    }

    @Override
    public Connection getReadOnly() throws OXException {
        Connection connection = configDatabaseService.getReadOnly();
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public Connection getWritable() throws OXException {
        Connection connection = configDatabaseService.getWritable();
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public Connection getForUpdateTask() throws OXException {
        Connection connection = configDatabaseService.getForUpdateTask();
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public void backReadOnly(final Connection con) {
        configDatabaseService.backReadOnly(con);
    }

    @Override
    public void backWritable(final Connection con) {
        configDatabaseService.backWritable(con);
    }

    @Override
    public void backWritableAfterReading(Connection con) {
        configDatabaseService.backWritableAfterReading(con);
    }

    @Override
    public void backForUpdateTask(Connection con) {
        configDatabaseService.backForUpdateTask(con);
    }

    @Override
    public void backForUpdateTaskAfterReading(Connection con) {
        configDatabaseService.backForUpdateTaskAfterReading(con);
    }

    @Override
    public int[] listContexts(int poolId, int offset, int length) throws OXException {
        return configDatabaseService.listContexts(poolId, offset, length);
    }

    @Override
    public int getServerId() throws OXException {
        return configDatabaseService.getServerId();
    }

    @Override
    public String getServerName() throws OXException {
        return configDatabaseService.getServerName();
    }

    @Override
    public int getWritablePool(int contextId) throws OXException {
        return configDatabaseService.getWritablePool(contextId);
    }

    @Override
    public String getSchemaName(int contextId) throws OXException {
        return configDatabaseService.getSchemaName(contextId);
    }

    @Override
    public SchemaInfo getSchemaInfo(int contextId) throws OXException {
        return configDatabaseService.getSchemaInfo(contextId);
    }

    @Override
    public int[] getContextsInSameSchema(int contextId) throws OXException {
        return configDatabaseService.getContextsInSameSchema(contextId);
    }

    @Override
    public int[] getContextsInSameSchema(Connection con, int contextId) throws OXException {
        return configDatabaseService.getContextsInSameSchema(con, contextId);
    }

    @Override
    public int[] getContextsInSchema(Connection con, int poolId, String schema) throws OXException {
        return configDatabaseService.getContextsInSchema(con, poolId, schema);
    }

    @Override
    public String[] getUnfilledSchemas(Connection con, int poolId, int maxContexts) throws OXException {
        return configDatabaseService.getUnfilledSchemas(con, poolId, maxContexts);
    }

    @Override
    public Map<String, Integer> getContextCountPerSchema(Connection con, int poolId, int maxContexts) throws OXException {
        return configDatabaseService.getContextCountPerSchema(con, poolId, maxContexts);
    }

    @Override
    public void invalidate(int... contextIds) {
        configDatabaseService.invalidate(contextIds);
    }

    @Override
    public void writeAssignment(Connection con, Assignment assignment) throws OXException {
        configDatabaseService.writeAssignment(con, assignment);
    }

    @Override
    public void deleteAssignment(Connection con, int contextId) throws OXException {
        configDatabaseService.deleteAssignment(con, contextId);
    }

    @Override
    public void lock(Connection con, int writePoolId) throws OXException {
        configDatabaseService.lock(con, writePoolId);
    }

    @Override
    public Map<String, Integer> getAllSchemata(Connection con) throws OXException {
        return configDatabaseService.getAllSchemata(con);
    }

    // Delegate global database service methods.

    @Override
    public boolean isGlobalDatabaseAvailable() {
        return globalDatabaseService.isGlobalDatabaseAvailable();
    }

    @Override
    public boolean isGlobalDatabaseAvailable(String group) throws OXException {
        return globalDatabaseService.isGlobalDatabaseAvailable(group);
    }

    @Override
    public boolean isGlobalDatabaseAvailable(int contextId) throws OXException {
        return globalDatabaseService.isGlobalDatabaseAvailable(contextId);
    }

    @Override
    public Set<String> getDistinctGroupsPerSchema() {
        return globalDatabaseService.getDistinctGroupsPerSchema();
    }

    @Override
    public Connection getReadOnlyForGlobal(String group) throws OXException {
        Connection connection = globalDatabaseService.getReadOnlyForGlobal(group);
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public Connection getReadOnlyForGlobal(int contextId) throws OXException {
        Connection connection = globalDatabaseService.getReadOnlyForGlobal(contextId);
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public void backReadOnlyForGlobal(String group, Connection connection) {
        globalDatabaseService.backReadOnlyForGlobal(group, connection);
    }

    @Override
    public void backReadOnlyForGlobal(int contextId, Connection connection) {
        globalDatabaseService.backReadOnlyForGlobal(contextId, connection);
    }

    @Override
    public Connection getWritableForGlobal(String group) throws OXException {
        Connection connection = globalDatabaseService.getWritableForGlobal(group);
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public Connection getWritableForGlobal(int contextId) throws OXException {
        Connection connection = globalDatabaseService.getWritableForGlobal(contextId);
        setSchemaLogProperty(connection);
        return connection;
    }

    @Override
    public void backWritableForGlobal(String group, Connection connection) {
        globalDatabaseService.backWritableForGlobal(group, connection);
    }

    @Override
    public void backWritableForGlobal(int contextId, Connection connection) {
        globalDatabaseService.backWritableForGlobal(contextId, connection);
    }

    @Override
    public void backWritableForGlobalAfterReading(int contextId, Connection connection) {
        globalDatabaseService.backWritableForGlobalAfterReading(contextId, connection);
    }

    @Override
    public void backWritableForGlobalAfterReading(String group, Connection connection) {
        globalDatabaseService.backWritableForGlobalAfterReading(group, connection);
    }

    // Implemented database service methods.

    @Override
    public Connection getReadOnly(final Context ctx) throws OXException {
        return get(ctx.getContextId(), false, false);
    }

    @Override
    public Connection getReadOnly(final int contextId) throws OXException {
        return get(contextId, false, false);
    }

    @Override
    public Connection getWritable(final Context ctx) throws OXException {
        return get(ctx.getContextId(), true, false);
    }

    @Override
    public Connection getWritable(final int contextId) throws OXException {
        return get(contextId, true, false);
    }

    @Override
    public Connection getForUpdateTask(final int contextId) throws OXException {
        return get(contextId, true, true);
    }

    @Override
    public Connection get(final int poolId, final String schema) throws OXException {
        setSchemaLogProperty(schema);
        Connection con;
        try {
            con = pools.getPool(poolId).get();
        } catch (PoolingException e) {
            throw DBPoolingExceptionCodes.NO_CONNECTION.create(e, I(poolId));
        }
        try {
            if (null != schema && !con.getCatalog().equals(schema)) {
                con.setCatalog(schema);
            }
        } catch (SQLException e) {
            try {
                pools.getPool(poolId).back(con);
                con = null;
            } catch (PoolingException e1) {
                LOG.error(e1.getMessage(), e1);
            } finally {
                // Something went wrong while trying to put back into pool if con is not null
                close(con);
            }
            throw DBPoolingExceptionCodes.SCHEMA_FAILED.create(e);
        }
        return con;
    }

    @Override
    public Connection getNoTimeout(final int poolId, final String schema) throws OXException {
        setSchemaLogProperty(schema);
        Connection con;
        try {
            con = pools.getPool(poolId).getWithoutTimeout();
        } catch (PoolingException e) {
            throw DBPoolingExceptionCodes.NO_CONNECTION.create(e, I(poolId));
        }
        try {
            if (null != schema && !con.getCatalog().equals(schema)) {
                con.setCatalog(schema);
            }
        } catch (SQLException e) {
            try {
                pools.getPool(poolId).back(con);
                con = null;
            } catch (PoolingException e1) {
                LOG.error(e1.getMessage(), e1);
            } finally {
                // Something went wrong while trying to put back into pool if con is not null
                close(con);
            }
            throw DBPoolingExceptionCodes.SCHEMA_FAILED.create(e);
        }
        return con;
    }

    @Override
    public Connection getReadOnlyMonitored(int readPoolId, int writePoolId, String schema, int partitionId) throws OXException {
        return getMonitoredConnection(readPoolId, writePoolId, schema, partitionId, false, false);
    }

    @Override
    public Connection getWritableMonitored(int readPoolId, int writePoolId, String schema, int partitionId) throws OXException {
        return getMonitoredConnection(readPoolId, writePoolId, schema, partitionId, true, false);
    }

    @Override
    public Connection getWritableMonitoredForUpdateTask(int readPoolId, int writePoolId, String schema, int partitionId) throws OXException {
        return getMonitoredConnection(readPoolId, writePoolId, schema, partitionId, true, true);
    }

    public Connection getMonitoredConnection(int readPoolId, int writePoolId, String schema, int partitionId, boolean write, boolean noTimeout) throws OXException {
        AssignmentImpl assignment = new AssignmentImpl(partitionId, Server.getServerId(), readPoolId, writePoolId, schema);
        return get(assignment, write, noTimeout);
    }

    @Override
    public void backReadOnly(final Context ctx, final Connection con) {
        back(con);
    }

    @Override
    public void backReadOnly(final int contextId, final Connection con) {
        back(con);
    }

    @Override
    public void backWritable(final Context ctx, final Connection con) {
        back(con);
    }

    @Override
    public void backWritable(final int contextId, final Connection con) {
        back(con);
    }

    @Override
    public void backWritableAfterReading(Context ctx, Connection con) {
        backFromReading(con);
    }

    @Override
    public void backWritableAfterReading(int contextId, Connection con) {
        backFromReading(con);
    }

    @Override
    public void backForUpdateTask(final int contextId, final Connection con) {
        back(con);
    }

    @Override
    public void backForUpdateTaskAfterReading(final int contextId, final Connection con) {
        backFromReading(con);
    }

    @Override
    public void back(final int poolId, final Connection connection) {
        Connection con = connection;
        try {
            pools.getPool(poolId).back(con);
            con = null;
        } catch (PoolingException e) {
            final OXException e2 = DBPoolingExceptionCodes.RETURN_FAILED.create(e, con.toString());
            LOG.error("", e2);
        } catch (OXException e) {
            LOG.error("", e);
        } finally {
            // Something went wrong while trying to put back into pool if con is not null
            close(con);
        }
    }

    @Override
    public void backNoTimeoout(final int poolId, final Connection connection) {
        Connection con = connection;
        try {
            pools.getPool(poolId).backWithoutTimeout(con);
            con = null;
        } catch (OXException e) {
            LOG.error("", e);
        } finally {
            // Something went wrong while trying to put back into pool if con is not null
            close(con);
        }
    }

    @Override
    public void backReadOnlyMonitored(int readPoolId, int writePoolId, String schema, int partitionId, Connection con) {
        back(con);
    }

    @Override
    public void backWritableMonitored(int readPoolId, int writePoolId, String schema, int partitionId, Connection con) {
        back(con);
    }

    @Override
    public void backWritableMonitoredForUpdateTask(int readPoolId, int writePoolId, String schema, int partitionId, Connection con) {
        back(con);
    }

    @Override
    public void initMonitoringTables(int writePoolId, String schema) throws OXException {
        Connection con = get(writePoolId, schema);
        int rollback = 0;
        try {
            con.setAutoCommit(false);
            rollback = 1;
            CreateReplicationTable createReplicationTable = new CreateReplicationTable();
            createReplicationTable.perform(con);
            con.commit();
            rollback = 2;
        } catch (SQLException x) {
            throw DBPoolingExceptionCodes.SQL_ERROR.create(x, x.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback==1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
            if (con != null) {
                back(writePoolId, con);
            }
        }
    }

    @Override
    public void initPartitions(int writePoolId, String schema, int... partitions) throws OXException {
        if (null == partitions || partitions.length <= 0) {
            return;
        }
        Connection con = get(writePoolId, schema);
        PreparedStatement stmt = null;
        int rollback = 0;
        try {
            con.setAutoCommit(false);
            rollback = 1;
            stmt = con.prepareStatement("INSERT INTO replicationMonitor (cid, transaction) VALUES (?, ?)");
            stmt.setInt(2, 0);
            for (int partition : partitions) {
                stmt.setInt(1, partition);
                stmt.addBatch();
            }
            stmt.executeBatch();
            con.commit();
            rollback = 2;
        } catch (SQLException x) {
            throw DBPoolingExceptionCodes.SQL_ERROR.create(x, x.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
            if (rollback > 0) {
                if (rollback==1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
            if (con != null) {
                back(writePoolId, con);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getReadOnly(Assignment assignment, boolean noTimeout) throws OXException {
        AssignmentImpl assignmentImpl = new AssignmentImpl(assignment);
        return get(assignmentImpl, false, noTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getWritable(Assignment assignment, boolean noTimeout) throws OXException {
        AssignmentImpl assignmentImpl = new AssignmentImpl(assignment);
        return get(assignmentImpl, true, noTimeout);
    }

    private void setSchemaLogProperty(String schemaName) {
        if (null != schemaName) {
            LogProperties.put(LogProperties.Name.DATABASE_SCHEMA, schemaName);
        }
    }

    private void setSchemaLogProperty(Connection connection) {
        if (null != connection) {
            try {
                String schemaName = connection.getCatalog();
                if (null != schemaName) {
                    LogProperties.put(LogProperties.Name.DATABASE_SCHEMA, schemaName);
                }
            } catch (Exception e) {
                // Ignore...
                LOG.debug("Failed to obtain schema name from connection", e);
            }
        }
    }

    private static void close(Connection con) {
        if (null != con) {
            try {
                con.close();
            } catch (Exception e) {
                LOG.error("Failed to close connection.", e);
            }
        }
    }

}
