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

package com.openexchange.admin.storage.sqlStorage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import com.openexchange.admin.rmi.exceptions.PoolException;
import com.openexchange.database.Assignment;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.log.LogProperties;
import com.openexchange.log.LogProperties.Name;

public class OXAdminPoolDBPool implements OXAdminPoolInterface {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OXAdminPoolDBPool.class);
    }

    /** The special log property to disable honoring server association */
    protected static final Name DATABASE_IGNORE_SERVER_ASSOCIATION = LogProperties.Name.DATABASE_IGNORE_SERVER_ASSOCIATION;

    /** Simple callable that sets and unsets the <code>"com.openexchange.database.ignoreServerAssociation"</code> log property */
    public static abstract class DatabaseServiceCallable<T> {

        /**
         * Initializes a new {@link DatabaseServiceCallable}.
         */
        protected DatabaseServiceCallable() {
            super();
        }

        /**
         * Executes this callable using given database service
         *
         * @param databaseService The database service to use
         * @return The result
         * @throws PoolException If operation fails
         */
        public T perform(DatabaseService databaseService) throws PoolException {
            LogProperties.put(DATABASE_IGNORE_SERVER_ASSOCIATION, "true");
            try {
                return doPerform(databaseService);
            } catch (SQLException e) {
                LoggerHolder.LOG.error("", e);
                throw new PoolException(e.getMessage(), e);
            } catch (OXException e) {
                LoggerHolder.LOG.error("", e);
                throw new PoolException(e.getMessage(), e);
            } finally {
                LogProperties.remove(DATABASE_IGNORE_SERVER_ASSOCIATION);
            }
        }

        /**
         * Actually performs the callable's operation.
         *
         * @param databaseService The database service to use
         * @return The result
         * @throws SQLException If an SQL error occurs
         * @throws OXException If operation fails
         */
        protected abstract T doPerform(DatabaseService databaseService) throws SQLException, OXException;

    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    private final AtomicReference<DatabaseService> service;

    public OXAdminPoolDBPool() {
        super();
        service = new AtomicReference<DatabaseService>(null);
    }

    @Override
    public void setService(DatabaseService service) {
        this.service.set(service);
    }

    @Override
    public void removeService() {
        setService(null);
    }

    public DatabaseService getService() throws PoolException {
        DatabaseService service = this.service.get();
        if (null == service) {
            throw new PoolException("DatabaseService is missing.");
        }
        return service;
    }

    @Override
    public Connection getConnectionForConfigDB() throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getWritable();
            }
        }.perform(getService());
    }

    @Override
    public Connection getWriteConnectionForConfigDB() throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getWritable();
            }
        }.perform(getService());
    }

    @Override
    public Connection getReadConnectionForConfigDB() throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getReadOnly();
            }
        }.perform(getService());
    }

    @Override
    public Connection getWriteConnectionForConfigDBNoTimeout() throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getForUpdateTask();
            }
        }.perform(getService());
    }

    @Override
    public Connection getConnectionForContext(final int contextId) throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getWritable(contextId);
            }
        }.perform(getService());
    }

    @Override
    public Connection getConnection(final int poolId, final String schema) throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.get(poolId, schema);
            }
        }.perform(getService());
    }

    @Override
    public Connection getConnectionForContextNoTimeout(final int contextId) throws PoolException {
        return new DatabaseServiceCallable<Connection>() {

            @Override
            protected Connection doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getForUpdateTask(contextId);
            }
        }.perform(getService());
    }

    @Override
    public boolean pushConnectionForConfigDB(final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.backWritable(con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushReadConnectionForConfigDB(final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.backReadOnly(con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushWriteConnectionForConfigDB(final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.backWritable(con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushWriteConnectionForConfigDBNoTimeout(final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.backForUpdateTask(con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushConnectionForContext(final int contextId, final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.backWritable(contextId, con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushConnectionForContextAfterReading(final int contextId, final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws SQLException, OXException {
                Databases.autocommit(con);
                databaseService.backWritableAfterReading(contextId, con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushConnectionForContextNoTimeout(final int contextId, final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws SQLException, OXException {
                Databases.autocommit(con);
                databaseService.backForUpdateTask(contextId, con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public boolean pushConnection(final int poolId, final Connection con) throws PoolException {
        if (null == con) {
            return false;
        }

        return new DatabaseServiceCallable<Boolean>() {

            @Override
            protected Boolean doPerform(DatabaseService databaseService) throws OXException {
                Databases.autocommit(con);
                databaseService.back(poolId, con);
                return Boolean.TRUE;
            }
        }.perform(getService()).booleanValue();
    }

    @Override
    public int getServerId() throws PoolException {
        return new DatabaseServiceCallable<Integer>() {

            @Override
            protected Integer doPerform(DatabaseService databaseService) throws OXException {
                return Integer.valueOf(databaseService.getServerId());
            }
        }.perform(getService()).intValue();
    }

    @Override
    public void writeAssignment(final Connection con, final Assignment assign) throws PoolException {
        new DatabaseServiceCallable<Void>() {

            @Override
            protected Void doPerform(DatabaseService databaseService) throws OXException {
                databaseService.writeAssignment(con, assign);
                return null;
            }
        }.perform(getService());
    }

    @Override
    public void deleteAssignment(final Connection con, final int contextId) throws PoolException {
        new DatabaseServiceCallable<Void>() {

            @Override
            protected Void doPerform(DatabaseService databaseService) throws OXException {
                databaseService.deleteAssignment(con, contextId);
                return null;
            }
        }.perform(getService());
    }

    @Override
    public int[] getContextInSameSchema(final Connection con, final int contextId) throws PoolException {
        return new DatabaseServiceCallable<int[]>() {

            @Override
            protected int[] doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getContextsInSameSchema(con, contextId);
            }
        }.perform(getService());
    }

    @Override
    public int[] getContextInSchema(final Connection con, final int poolId, final String schema) throws PoolException {
        return new DatabaseServiceCallable<int[]>() {

            @Override
            protected int[] doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getContextsInSchema(con, poolId, schema);
            }
        }.perform(getService());
    }

    @Override
    public int[] listContexts(final int poolId, final int offset, final int length) throws PoolException {
        return new DatabaseServiceCallable<int[]>() {

            @Override
            protected int[] doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.listContexts(poolId, offset, length);
            }
        }.perform(getService());
    }

    @Override
    public String[] getUnfilledSchemas(final Connection con, final int poolId, final int maxContexts) throws PoolException {
        return new DatabaseServiceCallable<String[]>() {

            @Override
            protected String[] doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getUnfilledSchemas(con, poolId, maxContexts);
            }
        }.perform(getService());
    }

    @Override
    public Map<String, Integer> getContextCountPerSchema(final Connection con, final int poolId, final int maxContexts) throws PoolException {
        return new DatabaseServiceCallable<Map<String, Integer>>() {

            @Override
            protected Map<String, Integer> doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getContextCountPerSchema(con, poolId, maxContexts);
            }
        }.perform(getService());
    }

    @Override
    public int getWritePool(final int contextId) throws PoolException {
        return new DatabaseServiceCallable<Integer>() {

            @Override
            protected Integer doPerform(DatabaseService databaseService) throws OXException {
                return Integer.valueOf(databaseService.getWritablePool(contextId));
            }
        }.perform(getService()).intValue();
    }

    @Override
    public String getSchemaName(final int contextId) throws PoolException {
        return new DatabaseServiceCallable<String>() {

            @Override
            protected String doPerform(DatabaseService databaseService) throws OXException {
                return databaseService.getSchemaName(contextId);
            }
        }.perform(getService());
    }

    @Override
    public void lock(final Connection con, final int writePoolId) throws PoolException {
        new DatabaseServiceCallable<Void>() {

            @Override
            protected Void doPerform(DatabaseService databaseService) throws OXException {
                databaseService.lock(con, writePoolId);
                return null;
            }
        }.perform(getService());
    }
}
