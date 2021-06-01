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

package com.openexchange.admin.storage.mysqlStorage;

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.database.Databases.startTransaction;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.i;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import com.openexchange.admin.daemons.ClientAdminThread;
import com.openexchange.admin.properties.AdminProperties;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Database;
import com.openexchange.admin.rmi.dataobjects.MaintenanceReason;
import com.openexchange.admin.rmi.exceptions.ContextExistsException;
import com.openexchange.admin.rmi.exceptions.PoolException;
import com.openexchange.admin.rmi.exceptions.StorageException;
import com.openexchange.admin.services.AdminServiceRegistry;
import com.openexchange.admin.storage.interfaces.OXToolStorageInterface;
import com.openexchange.admin.storage.sqlStorage.OXAdminPoolInterface;
import com.openexchange.admin.tools.AdminCache;
import com.openexchange.admin.tools.PropertyHandler;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.AssignmentInsertData;
import com.openexchange.database.DBPoolingExceptionCodes;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.tools.pipesnfilters.DataSource;
import com.openexchange.tools.pipesnfilters.Filter;
import com.openexchange.tools.pipesnfilters.PipesAndFiltersException;
import com.openexchange.tools.pipesnfilters.PipesAndFiltersService;

public class OXContextMySQLStorageCommon {

    public static final String LOG_ERROR_CLOSING_STATEMENT = "Error closing statement";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OXContextMySQLStorageCommon.class);

    private final Map<String, StartNumberProvider> startValues;
    private final AdminCache cache;
    final PropertyHandler prop;

    private static interface StartNumberProvider {

        int getStartValue();
    }

    public OXContextMySQLStorageCommon() {
        super();
        cache = ClientAdminThread.cache;
        prop = cache.getProperties();

        Map<String, StartNumberProvider> startValues = new HashMap<String, StartNumberProvider>(4);
        startValues.put("sequence_folder", new StartNumberProvider() {

            @Override
            public int getStartValue() {
                // below id 20 is reserved
                return 20;
            }
        });
        startValues.put("sequence_uid_number", new StartNumberProvider() {

            @Override
            public int getStartValue() {
                int startnum = Integer.parseInt(prop.getUserProp(AdminProperties.User.UID_NUMBER_START, "-1"));
                if (startnum > 0) {
                    // we use the uid number feature
                    // set the start number in the sequence for uid_numbers
                    return startnum;
                }
                return 0;
            }
        });
        startValues.put("sequence_gid_number", new StartNumberProvider() {

            @Override
            public int getStartValue() {
                int startnum = Integer.parseInt(prop.getGroupProp(AdminProperties.Group.GID_NUMBER_START, "-1"));
                if (startnum > 0) {
                    // we use the gid number feature
                    // set the start number in the sequence for gid_numbers
                    return startnum;
                }
                return 0;
            }
        });
        this.startValues = startValues;
    }

    // TODO: The average size parameter can be removed if we have an new property handler which can
    // deal right with plugin properties
    public Context getData(final Context ctx, final Connection configdb_con, final long average_size) throws SQLException, PoolException, StorageException {
        Connection oxdb_read = null;
        PreparedStatement prep = null;
        final int context_id = ctx.getId().intValue();

        try {

            prep = configdb_con.prepareStatement("SELECT context.name, context.enabled, context.reason_id, context.filestore_id, context.filestore_name, context.quota_max, context_server2db_pool.write_db_pool_id, context_server2db_pool.read_db_pool_id, context_server2db_pool.db_schema, login2context.login_info FROM context LEFT JOIN ( login2context, context_server2db_pool, server ) ON ( context.cid = context_server2db_pool.cid AND context_server2db_pool.server_id = server.server_id AND context.cid = login2context.cid ) WHERE context.cid = ? AND server.name = ?");
            prep.setInt(1, context_id);
            final String serverName = AdminServiceRegistry.getInstance().getService(ConfigurationService.class).getProperty(AdminProperties.Prop.SERVER_NAME, "local");
            prep.setString(2, serverName);
            ResultSet rs = prep.executeQuery();

            final Context cs = new Context();

            // DATABASE HANDLE
            if (rs.next()) {
                // filestore_id | filestore_name | filestore_login |
                // filestore_passwd | quota_max
                final String name = rs.getString(1); // name
                // name of the context, currently same with contextid
                if (name != null) {
                    cs.setName(name);
                }

                cs.setEnabled(Boolean.valueOf(rs.getBoolean(2))); // enabled
                final int reason_id = rs.getInt(3); //reason
                // CONTEXT STATE INFOS #
                if (-1 != reason_id) {
                    cs.setMaintenanceReason(new MaintenanceReason(I(reason_id)));
                }
                cs.setFilestoreId(I(rs.getInt(4))); // filestore_id
                cs.setFilestore_name(rs.getString(5)); //filestorename
                long quota_max = rs.getLong(6); //quota max
                if (quota_max != -1) {
                    quota_max = quota_max >> 20;
                    // set quota max also in context setup object
                    cs.setMaxQuota(L(quota_max));
                }
                final int write_pool = rs.getInt(7); // write_pool_id
                final int read_pool = rs.getInt(8); //read_pool_id
                final String db_schema = rs.getString(9); // db_schema
                if (null != db_schema) {
                    cs.setReadDatabase(new Database(read_pool, db_schema));
                    cs.setWriteDatabase(new Database(write_pool, db_schema));
                }
                //DO NOT RETURN THE CONTEXT ID AS A MAPPING!!
                // THIS CAN CAUSE ERRORS IF CHANGING LOGINMAPPINGS AFTERWARDS!
                // SEE #11094 FOR DETAILS!
                final String login_mapping = rs.getString(10);
                if (!ctx.getIdAsString().equals(login_mapping)) {
                    cs.addLoginMapping(login_mapping);
                }
            }
            // All other lines contain the same content except the mapping so we concentrate on the mapping here
            while (rs.next()) {
                final String login_mapping = rs.getString(10);
                // DO NOT RETURN THE CONTEXT ID AS A MAPPING!!
                // THIS CAN CAUSE ERRORS IF CHANGING LOGINMAPPINGS AFTERWARDS!
                // SEE #11094 FOR DETAILS!
                if (!ctx.getIdAsString().equals(login_mapping)) {
                    cs.addLoginMapping(login_mapping);
                }
            }

            // ######################

            rs.close();
            prep.close();

            oxdb_read = cache.getConnectionForContext(context_id);

            prep = oxdb_read.prepareStatement("SELECT filestore_usage.used FROM filestore_usage WHERE filestore_usage.cid = ? AND filestore_usage.user = 0");
            prep.setInt(1, context_id);
            rs = prep.executeQuery();

            long quota_used = 0;
            while (rs.next()) {
                quota_used = rs.getLong(1);
            }
            rs.close();
            prep.close();
            quota_used = quota_used >> 20;
            // set used quota in context setup
            cs.setUsedQuota(L(quota_used));

            cs.setAverage_size(L(average_size));

            // context id
            cs.setId(I(context_id));
            loadDynamicAttributes(oxdb_read, cs);
            return cs;
        } finally {
            Databases.closeSQLStuff(prep);
            if (oxdb_read != null) {
                try {
                    cache.pushConnectionForContextAfterReading(context_id, oxdb_read);
                } catch (PoolException exp) {
                    log.error("Pool Error pushing ox read connection to pool!", exp);
                }
            }
        }
    }

    /**
     * Parses a dynamic attribute from the contextAttribute table
     * Returns a String[] with retval[0] being the namespace and retval[1] being the name
     *
     * @throws StorageException
     */
    public static String[] parseDynamicAttribute(final String name) throws StorageException {
        final int pos = name.indexOf('/');
        if (pos == -1) {
            throw new StorageException("Could not parse dynamic attribute name: " + name);
        }
        final String[] parsed = new String[2];
        parsed[0] = name.substring(0, pos);
        parsed[1] = name.substring(pos + 1);
        return parsed;
    }

    public static boolean isDynamicAttribute(final String name) {
        return name.indexOf('/') >= 0;
    }

    private void loadDynamicAttributes(final Connection oxCon, final Context ctx) throws SQLException, StorageException {
        ResultSet rs = null;
        PreparedStatement stmtuserattributes = null;
        final int contextId = ctx.getId().intValue();
        try {
            stmtuserattributes = oxCon.prepareStatement("SELECT name, value FROM contextAttribute WHERE cid = ?");
            stmtuserattributes.setInt(1, contextId);
            rs = stmtuserattributes.executeQuery();
            while (rs.next()) {
                final String name = rs.getString("name");
                final String value = rs.getString("value");
                if (isDynamicAttribute(name)) {
                    final String[] namespaced = parseDynamicAttribute(name);
                    ctx.setUserAttribute(namespaced[0], namespaced[1], value);
                }
            }
        } finally {
            closeSQLStuff(rs, stmtuserattributes);
        }
    }

    public Context[] loadContexts(final Collection<Integer> cids, final long averageSize, final List<Filter<Context, Context>> filters, final boolean failOnMissing) throws StorageException {
        PipesAndFiltersService pnfService;
        try {
            pnfService = AdminServiceRegistry.getInstance().getService(PipesAndFiltersService.class, true);
        } catch (OXException e) {
            throw StorageException.wrapForRMI(e);
        }
        DataSource<Context> output = pnfService.create(cids).addFilter(new ContextLoader(cache, failOnMissing));
        if (null != filters && !filters.isEmpty()) {
            for (final Filter<Context, Context> f : filters) {
                output = output.addFilter(f);
            }
        }
        output = output.addFilter(new LoginInfoLoader(cache)).addFilter(new FilestoreUsageLoader(cache, averageSize, failOnMissing)).addFilter(new DynamicAttributesLoader(cache));
        final SortedMap<Integer, Context> retval = new TreeMap<Integer, Context>();
        try {
            while (output.hasData()) {
                final List<Context> tmp = new ArrayList<Context>();
                output.getData(tmp);
                for (final Context context : tmp) {
                    retval.put(context.getId(), context);
                }
            }
        } catch (PipesAndFiltersException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof StorageException) {
                throw (StorageException) cause;
            }
            throw new StorageException(cause.getMessage(), cause);
        }
        return retval.values().toArray(new Context[retval.size()]);
    }

    public final void createStandardGroupForContext(final int context_id, final Connection ox_write_con, final String display_name, final int group_id, final int gid_number) throws SQLException {
        // TODO: this must be defined somewhere else
        final int NOGROUP = 65534;
        final PreparedStatement group_stmt = ox_write_con.prepareStatement("INSERT INTO groups (cid, id, identifier, displayname,lastModified,gidNumber) VALUES (?,?,'users',?,?,?);");
        try {
            group_stmt.setInt(1, context_id);
            group_stmt.setInt(2, group_id);
            group_stmt.setString(3, display_name);
            group_stmt.setLong(4, System.currentTimeMillis());
            if (Integer.parseInt(prop.getGroupProp(AdminProperties.Group.GID_NUMBER_START, "-1")) > 0) {
                group_stmt.setInt(5, gid_number);
            } else {
                group_stmt.setInt(5, NOGROUP);
            }
            group_stmt.executeUpdate();
        } finally {
            closeSQLStuff(group_stmt);
        }

    }

    /**
     * Checks if there are any context referencing the given schema on the given database. If this is not the case, then the database will
     * be deleted.
     *
     * @param poolId should be the pool identifier of the master database server of a database cluster.
     * @param dbSchema the name of the database schema that should be checked for deletion.
     * @throws StorageException if somehow the check and delete process fails.
     */
    public static void deleteEmptySchema(int poolId, String dbSchema) throws StorageException {
        AdminCache cache = ClientAdminThread.cache;
        final Connection con;
        try {
            con = cache.getWriteConnectionForConfigDB();
        } catch (PoolException e) {
            throw new StorageException(e.getMessage(), e);
        }
        try {
            startTransaction(con);
            deleteSchemaIfEmpty(con, poolId, dbSchema, cache);
            con.commit();
        } catch (SQLException e) {
            rollback(con);
            log.error(e.getMessage(), e);
            throw new StorageException(e.getMessage(), e);
        } catch (PoolException e) {
            rollback(con);
            log.error(e.getMessage(), e);
            throw new StorageException(e.getMessage(), e);
        } finally {
            autocommit(con);
            try {
                cache.pushWriteConnectionForConfigDB(con);
            } catch (PoolException e) {
                log.error("Error pushing configdb connection to pool!", e);
            }
        }
    }

    /**
     * If this method is used the surrounding code needs to take care, that according locks on the database tables are created. If they are
     * no such locks this method may delete schemas where another request currently writes to.
     */
    public static void deleteSchemaIfEmpty(Connection con, int poolId, String dbSchema, AdminCache cache) throws StorageException, PoolException {
        final int[] otherContexts;
        try {
            otherContexts = cache.getPool().getContextInSchema(con, poolId, dbSchema);
        } catch (PoolException e) {
            log.error(e.getMessage(), e);
            throw new StorageException(e.getMessage(), e);
        }
        if (otherContexts.length == 0) {
            Database db = OXToolStorageInterface.getInstance().loadDatabaseById(poolId);
            db.setScheme(dbSchema);
            cache.getPool().lock(con, poolId);
            OXUtilMySQLStorageCommon.deleteDatabase(db, con);
        }
    }

    public final void deleteContextFromConfigDB(Connection con, int contextId) throws StorageException {
        OXAdminPoolInterface pool = cache.getPool();
        PreparedStatement stmt = null;
        try {
            // Decrement filestore counter
            int filestoreId = getFilestoreIdFor(con, contextId);
            if (0 != filestoreId) {
                stmt = con.prepareStatement("UPDATE contexts_per_filestore SET count=count-1 WHERE filestore_id=? AND count>0");
                stmt.setInt(1, filestoreId);
                stmt.executeUpdate();
                closeSQLStuff(stmt);
                stmt = null;
            }

            // Delete association from "context_server2db_pool" table (and thus implicitly decrement counters for database/schema)
            pool.deleteAssignment(con, contextId);

            log.debug("Deleting login2context entries for context {}", I(contextId));
            stmt = con.prepareStatement("DELETE FROM login2context WHERE cid=?");
            stmt.setInt(1, contextId);
            stmt.executeUpdate();
            stmt.close();

            log.debug("Deleting context entry for context {}", I(contextId));
            stmt = con.prepareStatement("DELETE FROM context WHERE cid=?");
            stmt.setInt(1, contextId);
            stmt.executeUpdate();
            stmt.close();
        } catch (PoolException e) {
            log.error(e.getMessage(), e);
            throw new StorageException(e.getMessage(), e);
        } catch (DataTruncation e) {
            OXException oxe = DBPoolingExceptionCodes.COUNTS_INCONSISTENT.create(e, new Object[0]);
            throw new StorageException(oxe.getMessage(), oxe);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new StorageException(e.getMessage(), e);
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private int getFilestoreIdFor(Connection con, int contextId) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT filestore_id FROM context WHERE cid=?");
            stmt.setInt(1, contextId);
            rs = stmt.executeQuery();
            int filestoreId = 0;
            if (rs.next()) {
                filestoreId = rs.getInt(1);
            }
            return filestoreId;
        } finally {
            closeSQLStuff(rs, stmt);
        }
    }

    private String[] determineSequenceTables(final Connection con) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        final List<String> tmp = new ArrayList<String>();
        try {
            stmt = con.prepareStatement("SHOW TABLES LIKE ?");
            stmt.setString(1, "%sequence\\_%");
            result = stmt.executeQuery();
            while (result.next()) {
                tmp.add(result.getString(1));
            }
        } finally {
            closeSQLStuff(result, stmt);
        }
        return tmp.toArray(new String[tmp.size()]);
    }

    /**
     * Inserts context data to appropriate tables.
     *
     * @param ctx The context to add
     * @param con The connection to the configdb
     * @param db The database associated with the context
     * @throws StorageException If a general storage error occurs
     * @throws ContextExistsException If there is already a context with the same context identifier
     */
    public void fillContextAndServer2DBPool(final Context ctx, final Connection con, final Database db) throws StorageException, ContextExistsException {
        // dbid is the id in db_pool of database engine to use for next context

        // if read id -1 (not set by client ) or 0 (there is no read db for this
        // cluster) then read id must be same as write id
        // else the db pool cannot resolve the database
        if (null == db.getRead_id() || 0 == db.getRead_id().intValue()) {
            db.setRead_id(db.getId());
        }
        fillContextTable(ctx, con);

        try {
            final int serverId = cache.getServerId();
            cache.getPool().writeAssignment(con, new AssignmentInsertData() {

                @Override
                public boolean updateDatabaseCounters() {
                    return false;
                }

                @Override
                public int getContextId() {
                    return i(ctx.getId());
                }

                @Override
                public int getServerId() {
                    return serverId;
                }

                @Override
                public int getReadPoolId() {
                    Integer readId = db.getRead_id();
                    if (null == readId) {
                        // Hints to a pool w/o a slave; return write-pool identifier instead
                        return getWritePoolId();
                    }
                    return i(readId);
                }

                @Override
                public int getWritePoolId() {
                    return i(db.getId());
                }

                @Override
                public String getSchema() {
                    return db.getScheme();
                }
            });
        } catch (PoolException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    public final void handleCreateContextRollback(Connection configCon, int contextId) throws StorageException {
        // remove all entries from configuration database because everything to configuration database has been committed.
        try {
            if (configCon != null) {
                deleteContextFromConfigDB(configCon, contextId);
                configCon.commit();
            }
        } catch (SQLException e) {
            log.error("SQL Error removing/rollback entries from configdb for context {}", I(contextId), e);
        }
    }

    public final void handleContextDeleteRollback(final Connection write_ox_con, final Connection con_write) {
        try {
            if (con_write != null && !con_write.getAutoCommit()) {
                con_write.rollback();
                log.debug("Rollback of configdb write connection ok");
            }
        } catch (SQLException rexp) {
            log.error("SQL Error", rexp);
        }
        try {
            if (write_ox_con != null && !write_ox_con.getAutoCommit()) {
                write_ox_con.rollback();
                log.debug("Rollback of ox db write connection ok");
            }
        } catch (SQLException rexp) {
            log.error("Error processing rollback of ox write connection!", rexp);
        }
    }

    public final void initSequenceTables(final int contextId, final Connection con) throws SQLException {
        PreparedStatement stmt = null;
        try {
            for (final String tableName : determineSequenceTables(con)) {
                final int startValue = modifyStartValue(tableName);
                stmt = con.prepareStatement("INSERT INTO `" + tableName + "` VALUES (?,?)");
                stmt.setInt(1, contextId);
                stmt.setInt(2, startValue);
                stmt.execute();
                stmt.close();
            }
        } finally {
            closeSQLStuff(stmt);
        }
    }

    public final void initReplicationMonitor(final Connection con, final int contextId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("INSERT INTO replicationMonitor (cid,transaction) VALUES (?,0)");
            stmt.setInt(1, contextId);
            stmt.execute();
        } finally {
            closeSQLStuff(stmt);
        }
    }

    public final void initFilestoreUsage(final Connection con, final int contextId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("INSERT INTO filestore_usage (cid,used) VALUES (?,0)");
            stmt.setInt(1, contextId);
            stmt.execute();
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private int modifyStartValue(final String tableName) {
        StartNumberProvider startNumberProvider = startValues.get(tableName);
        return null == startNumberProvider ? 0 : startNumberProvider.getStartValue();
    }

    /**
     * <code>INSERT</code>s the data row into the "context" table.
     *
     * @param ctx The context to insert
     * @param configdbCon A connection to the configdb
     * @throws StorageException If a general SQL error occurs
     * @throws ContextExistsException If there is already a context with the same context identifier
     */
    private final void fillContextTable(final Context ctx, final Connection configdbCon) throws StorageException, ContextExistsException {
        String name;
        if (ctx.getName() != null && ctx.getName().trim().length() > 0) {
            name = ctx.getName();
        } else {
            name = ctx.getIdAsString();
        }

        PreparedStatement stmt = null;
        try {
            stmt = configdbCon.prepareStatement("INSERT INTO context (cid,name,enabled,filestore_id,filestore_name,quota_max) VALUES (?,?,?,?,?,?)");
            stmt.setInt(1, ctx.getId().intValue());
            stmt.setString(2, name);
            stmt.setBoolean(3, true);
            stmt.setInt(4, ctx.getFilestoreId().intValue());
            stmt.setString(5, ctx.getFilestore_name());
            // quota is in MB, but we store in Byte
            long quota_max_temp = ctx.getMaxQuota().longValue();
            if (quota_max_temp != -1) {
                quota_max_temp = quota_max_temp << 20;
            }
            stmt.setLong(6, quota_max_temp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (Databases.isPrimaryKeyConflictInMySQL(e)) {
                throw new ContextExistsException("Context " + ctx.getId().intValue() + " already exists!");
            }
            if (Databases.isKeyConflictInMySQL(e, "context_name_unique")) {
                throw new ContextExistsException("Context " + name + " already exists!");
            }
            throw new StorageException(e.getMessage(), e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_filestore" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param ctx The context newly using a filestore
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerFilestoreCount(boolean increment, Context ctx) throws StorageException {
        Connection configdbCon;
        try {
            configdbCon = cache.getWriteConnectionForConfigDB();
        } catch (PoolException e) {
            throw new StorageException(e);
        }
        try {
            updateContextsPerFilestoreCount(increment, ctx, configdbCon);
        } finally {
            if (null != configdbCon) {
                try {
                    cache.pushWriteConnectionForConfigDB(configdbCon);
                } catch (PoolException e) {
                    log.error("Failed to push back read-write connection for ConfigDB", e);
                }
            }
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_filestore" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param ctx The context newly using a filestore
     * @param configdbCon A connection to the configdb
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerFilestoreCount(boolean increment, Context ctx, Connection configdbCon) throws StorageException {
        if (null == configdbCon) {
            updateContextsPerFilestoreCount(increment, ctx);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = configdbCon.prepareStatement("UPDATE contexts_per_filestore SET count=count" + (increment ? '+' : '-') + "1 WHERE filestore_id=?");
            stmt.setInt(1, ctx.getFilestoreId().intValue());
            stmt.executeUpdate();
        } catch (DataTruncation e) {
            OXException oxe = DBPoolingExceptionCodes.COUNTS_INCONSISTENT.create(e, new Object[0]);
            throw new StorageException(oxe.getMessage(), oxe);
        } catch (SQLException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_dbpool" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param db The used database
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerDBPoolCount(boolean increment, Database db) throws StorageException {
        Connection configdbCon;
        try {
            configdbCon = cache.getWriteConnectionForConfigDB();
        } catch (PoolException e) {
            throw new StorageException(e);
        }
        try {
            updateContextsPerDBPoolCount(increment, db, configdbCon);
        } finally {
            if (null != configdbCon) {
                try {
                    cache.pushWriteConnectionForConfigDB(configdbCon);
                } catch (PoolException e) {
                    log.error("Failed to push back read-write connection for ConfigDB", e);
                }
            }
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_dbpool" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param db The used database
     * @param configdbCon A connection to the configdb
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerDBPoolCount(boolean increment, Database db, Connection configdbCon) throws StorageException {
        if (null == configdbCon) {
            updateContextsPerDBPoolCount(increment, db);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = configdbCon.prepareStatement("UPDATE contexts_per_dbpool SET count=count" + (increment ? '+' : '-') + "1 WHERE db_pool_id=?");
            stmt.setInt(1, db.getId().intValue());
            stmt.executeUpdate();
        } catch (DataTruncation e) {
            if (!increment) {
                OXException oxe = DBPoolingExceptionCodes.COUNTS_INCONSISTENT.create(e, new Object[0]);
                throw new StorageException(oxe.getMessage(), oxe);
            }
            throw new StorageException(e.getMessage(), e);
        } catch (SQLException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_dbpool" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param schema The schema name
     * @param db The used database
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerDBSchemaCount(boolean increment, String schema, Database db) throws StorageException {
        Connection configdbCon;
        try {
            configdbCon = cache.getWriteConnectionForConfigDB();
        } catch (PoolException e) {
            throw new StorageException(e);
        }
        try {
            updateContextsPerDBSchemaCount(increment, schema, db, configdbCon);
        } finally {
            if (null != configdbCon) {
                try {
                    cache.pushWriteConnectionForConfigDB(configdbCon);
                } catch (PoolException e) {
                    log.error("Failed to push back read-write connection for ConfigDB", e);
                }
            }
        }
    }

    /**
     * <code>UPDATE</code>s the data row in the "contexts_per_dbpool" table.
     *
     * @param increment <code>true</code> to increment; otherwise <code>false</code> for decrement
     * @param schema The schema name
     * @param db The used database
     * @param configdbCon A connection to the configdb
     * @throws StorageException If a general SQL error occurs
     */
    public final void updateContextsPerDBSchemaCount(boolean increment, String schema, Database db, Connection configdbCon) throws StorageException {
        if (null == configdbCon) {
            updateContextsPerDBSchemaCount(increment, schema, db);
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = configdbCon.prepareStatement("UPDATE contexts_per_dbschema SET count=count" + (increment ? '+' : '-') + "1 WHERE db_pool_id=? AND schemaname=?");
            stmt.setInt(1, db.getId().intValue());
            stmt.setString(2, schema);
            stmt.executeUpdate();
        } catch (DataTruncation e) {
            if (!increment) {
                OXException oxe = DBPoolingExceptionCodes.COUNTS_INCONSISTENT.create(e, new Object[0]);
                throw new StorageException(oxe.getMessage(), oxe);
            }
            throw new StorageException(e.getMessage(), e);
        } catch (SQLException e) {
            throw new StorageException(e.getMessage(), e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    public void fillLogin2ContextTable(Context ctx, Connection configdb_write_con) throws SQLException, StorageException {
        int contextId = ctx.getId().intValue();
        for (String mapping : ctx.getLoginMappings()) {
            if (null != mapping) {
                insertLogin2ContextMapping(mapping, contextId, configdb_write_con);
            }
        }
    }

    private void insertLogin2ContextMapping(String mapping, int contextId, Connection configdb_write_con) throws SQLException, StorageException {
        PreparedStatement stmt = null;
        try {
            stmt = configdb_write_con.prepareStatement("INSERT INTO login2context (cid,login_info) VALUES (?,?)");
            stmt.setInt(1, contextId);
            stmt.setString(2, mapping);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (Databases.isPrimaryKeyConflictInMySQL(e)) {
                throw new StorageException("Cannot map '" + mapping + "' to the newly created context. This mapping is already in use.", e);
            }
            log.error("SQL Error", e);
            throw e;
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

}
