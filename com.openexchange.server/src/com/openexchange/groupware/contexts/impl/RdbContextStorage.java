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

package com.openexchange.groupware.contexts.impl;

import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import com.openexchange.context.PoolAndSchema;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.databaseold.Database;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.UpdateBehavior;
import com.openexchange.groupware.ldap.LdapExceptionCode;
import com.openexchange.java.Sets;
import com.openexchange.java.Strings;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.update.Tools;
import com.openexchange.user.UserExceptionCode;

/**
 * This class implements a storage for contexts in a relational database.
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.org">Sebastian Kauss</a>
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class RdbContextStorage extends ContextStorage {

    /**
     * SQL select statement for loading a context.
     */
    private static final String SELECT_CONTEXT = "SELECT name,enabled,filestore_id,filestore_name,filestore_login,filestore_passwd,quota_max,server_id FROM context JOIN context_server2db_pool ON context.cid=context_server2db_pool.cid WHERE context.cid=?";

    /**
     * SQL select statement for resolving the login info to the context
     * identifier.
     */
    private static final String RESOLVE_CONTEXT = "SELECT cid FROM login2context WHERE login_info=?";

    /**
     * SQL select statement for resolving the login info to the context.
     */
    private static final String RESOLVE_FULL_CONTEXT = "SELECT context.cid,name,enabled,filestore_id,filestore_name,filestore_login,filestore_passwd,quota_max,server_id FROM context JOIN login2context ON context.cid=login2context.cid JOIN context_server2db_pool ON context.cid=context_server2db_pool.cid WHERE login_info=?";

    /**
     * SQL select statement for resolving the identifier of the contexts
     * mailadmin.
     */
    private static final String GET_MAILADMIN = "SELECT user FROM user_setting_admin WHERE cid=?";

    /**
     * SQL select statement for reading the login information of a context.
     */
    private static final String GET_LOGININFOS = "SELECT login_info FROM login2context WHERE cid=?";

    /**
     * Default constructor.
     */
    public RdbContextStorage() {
        super();
    }

    @Override
    public int getContextId(final String loginInfo) throws OXException {
        if (Strings.containsSurrogatePairs(loginInfo)) {
            return NOT_FOUND;
        }
        final Connection con;
        try {
            con = DBPool.pickup();
        } catch (OXException e) {
            throw ContextExceptionCodes.NO_CONNECTION.create(e);
        }
        int contextId = NOT_FOUND;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement(RESOLVE_CONTEXT);
            stmt.setString(1, loginInfo);
            result = stmt.executeQuery();
            if (result.next()) {
                contextId = result.getInt(1);
            }
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            DBPool.closeReaderSilent(con);
        }
        return contextId;
    }

    /**
     * Instantiates an implementation of the context interface and fill its attributes according to the needs to be able to separate
     * contexts.
     *
     * @param loginContextInfo the login info for the context.
     * @return the context or <code>null</code> if no matching context exists.
     * @throws OXException if an error occurs.
     */
    public ContextExtended getContext(String loginInfo) throws OXException {
        if (Strings.containsSurrogatePairs(loginInfo)) {
            return null;
        }


        DatabaseService databaseService = Database.getDatabaseService();
        if (null == databaseService) {
            throw ServiceExceptionCode.absentService(DatabaseService.class);
        }

        Connection con;
        try {
            con = databaseService.getReadOnly();
        } catch (OXException e) {
            throw ContextExceptionCodes.NO_CONNECTION.create(e);
        }

        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement(RESOLVE_FULL_CONTEXT);
            stmt.setString(1, loginInfo);
            result = stmt.executeQuery();
            if (!result.next()) {
                return null;
            }

            ContextImpl context = loadContextDataFromResultSet(result, -1);
            context.setLoginInfo(getLoginInfos(con, context));
            // Load context data from UserDB
            loadContextDataFromUserDb(context, databaseService);
            return context;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            databaseService.backReadOnly(con);
        }
    }


    /**
     * Gets the identifier of the context administrator
     *
     * @param con The connection to use
     * @param ctxId The context identifier
     * @return The identifier of the context administrator
     * @throws OXException If no context administrator could be found
     */
    public static final int getAdmin(final Connection con, final int ctxId) throws OXException {
        int identifier = -1;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement(GET_MAILADMIN);
            stmt.setInt(1, ctxId);
            result = stmt.executeQuery();
            if (!result.next()) {
                throw ContextExceptionCodes.NO_MAILADMIN.create(Integer.valueOf(ctxId));
            }

            identifier = result.getInt(1);
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
        }
        return identifier;
    }

    private String[] getLoginInfos(final Connection con, final Context ctx) throws OXException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement(GET_LOGININFOS);
            stmt.setInt(1, ctx.getContextId());
            result = stmt.executeQuery();
            if (false == result.next()) {
                return new String[0];
            }

            List<String> loginInfo = new LinkedList<String>();
            do {
                loginInfo.add(result.getString(1));
            } while (result.next());
            return loginInfo.toArray(new String[loginInfo.size()]);
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
        }
    }

    @Override
    public boolean exists(int contextId) throws OXException {
        DatabaseService databaseService = Database.getDatabaseService();
        if (null == databaseService) {
            throw ServiceExceptionCode.absentService(DatabaseService.class);
        }

        Connection con = databaseService.getReadOnly();
        try {
            return exists(contextId, con);
        } finally {
            databaseService.backReadOnly(con);
        }
    }

    private boolean exists(int contextId, Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SELECT 1 FROM context WHERE cid=?");
            stmt.setInt(1, contextId);
            result = stmt.executeQuery();
            return result.next();
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
        }
    }

    @Override
    public ContextExtended loadContext(int contextId, UpdateBehavior updateBehavior) throws OXException {
        DatabaseService databaseService = Database.getDatabaseService();
        if (null == databaseService) {
            throw ServiceExceptionCode.absentService(DatabaseService.class);
        }

        // Load context data from ConfigDB
        ContextImpl context;
        try {
            Connection con = databaseService.getReadOnly();
            try {
                context = loadContext(con, contextId);
            } finally {
                databaseService.backReadOnly(con);
            }
        } catch (OXException e) {
            if (false == ContextExceptionCodes.NOT_FOUND.equals(e)) {
                throw e;
            }

            // Context not found. Retry with read-write connection
            Connection con = databaseService.getWritable();
            try {
                context = loadContext(con, contextId);
            } finally {
                databaseService.backWritableAfterReading(con);
            }
        }

        // Load context data from UserDB
        loadContextDataFromUserDb(context, databaseService);
        return context;
    }

    private void loadContextDataFromUserDb(ContextImpl context, DatabaseService databaseService) throws OXException {
        int contextId = context.getContextId();
        try {
            Connection con = databaseService.getReadOnly(contextId);
            try {
                setMailAdminAndAttributes(con, context);
            } finally {
                databaseService.backReadOnly(contextId, con);
            }
        } catch (OXException e) {
            if (false == ContextExceptionCodes.NO_MAILADMIN.equals(e)) {
                throw e;
            }

            Connection con = databaseService.getWritable(contextId);
            try {
                setMailAdminAndAttributes(con, context);
            } finally {
                databaseService.backWritableAfterReading(contextId, con);
            }
        }
    }

    private ContextImpl loadContext(Connection con, int contextId) throws OXException {
        ContextImpl context = loadContextData(con, contextId);
        context.setLoginInfo(getLoginInfos(con, context));
        return context;
    }

    private void setMailAdminAndAttributes(Connection con, ContextImpl context) throws OXException {
        context.setMailadmin(getAdmin(con, context.getContextId()));
        loadAndSetAttributes(con, context);
    }

    private void loadAndSetAttributes(Connection con, ContextImpl ctx) throws OXException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SELECT name, value FROM contextAttribute WHERE cid = ?");
            stmt.setInt(1, ctx.getContextId());
            result = stmt.executeQuery();
            while (result.next()) {
                final String name = result.getString(1);
                final String value = result.getString(2);
                ctx.addAttribute(name, value);
            }
        } catch (SQLException e) {
            try {
                if (!Tools.tableExists(con, "contextAttribute")) {
                    // This would be an explanation for the exception. Will
                    // happen once for every schema.
                    return;
                }
            } catch (SQLException e1) {
                // IGNORE
            }
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
        }
    }

    public ContextImpl loadContextData(final int contextId) throws OXException {
        final Connection con = DBPool.pickup();
        try {
            return loadContextData(con, contextId);
        } finally {
            DBPool.closeReaderSilent(con);
        }
    }

    public ContextImpl loadContextData(final Connection con, final int contextId) throws OXException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement(SELECT_CONTEXT);
            stmt.setInt(1, contextId);
            result = stmt.executeQuery();

            // Check if such a context exists
            if (false == result.next()) {
                throw ContextExceptionCodes.NOT_FOUND.create(I(contextId));
            }

            return loadContextDataFromResultSet(result, contextId);
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
        }
    }

    /**
     * Loads the context data from the result set and puts them into a context object
     *
     * @param result The result set
     * @param optContextId The context identifier. <code>-1</code> to indicate that the context ID was
     * loaded by the query and is contained in the result set with the column label <code>cid</code>
     * @return The loaded context
     * @throws OXException Wrapped {@link SQLException} or {@link ContextExceptionCodes#LOCATED_IN_ANOTHER_SERVER}
     */
    private ContextImpl loadContextDataFromResultSet(ResultSet result, int optContextId) throws OXException {
        try {
            boolean byContextId = optContextId > -1;
            int contextId = byContextId ? optContextId : result.getInt("cid");

            // Determine context-associated server
            int serverId = result.getInt("server_id");

            // Initialize & fill ContextImpl instance from result set
            ContextImpl context = new ContextImpl(contextId);
            int pos = byContextId ? 1 : 2;
            context.setName(result.getString(pos++));
            boolean enabled = result.getBoolean(pos++);
            context.setEnabled(enabled);
            context.setFilestoreId(result.getInt(pos++));
            context.setFilestoreName(result.getString(pos++));
            final String[] auth = new String[2];
            auth[0] = result.getString(pos++);
            auth[1] = result.getString(pos++);
            context.setFilestoreAuth(auth);
            context.setFileStorageQuota(result.getLong(pos++));

            /*-
             * If context is disabled, return ContextImpl instance to let outer logic throw an appropriate exception. Otherwise, the user
             * might be redirected to another server although context is disabled. See redirect in 'c.o.login.internal.LoginPerformer.doLogin()'
             *
             * Otherwise check if context-associated server matches this node's one
             */
            if (enabled && serverId != DBPool.getServerId()) {
                throw ContextExceptionCodes.LOCATED_IN_ANOTHER_SERVER.create(I(contextId), I(serverId));
            }

            return context;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Integer> getAllContextIds() throws OXException {
        Connection con = DBPool.pickup();
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SELECT cid FROM context");
            result = stmt.executeQuery();
            if (!result.next()) {
                return Collections.emptyList();
            }

            List<Integer> retval = new ArrayList<Integer>();
            do {
                retval.add(Integer.valueOf(result.getInt(1)));
            } while (result.next());
            return retval;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            DBPool.closeReaderSilent(con);
        }
    }

    @Override
    public List<Integer> getDistinctContextsPerSchema() throws OXException {
        Connection con = DBPool.pickup();
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            // GROUP BY CLAUSE: ensure ONLY_FULL_GROUP_BY compatibility
            stmt = con.prepareStatement("SELECT MIN(cid) FROM context_server2db_pool GROUP BY db_schema");
            result = stmt.executeQuery();
            if (false == result.next()) {
                return Collections.emptyList();
            }

            List<Integer> retval = new LinkedList<>();
            do {
                retval.add(Integer.valueOf(result.getInt(1)));
            } while (result.next());
            return retval;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            DBPool.closeReaderSilent(con);
        }
    }

    @Override
    public Map<PoolAndSchema, List<Integer>> getSchemaAssociations() throws OXException {
        Connection con = DBPool.pickup();
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SELECT DISTINCT write_db_pool_id, db_schema FROM context_server2db_pool");
            result = stmt.executeQuery();
            if (!result.next()) {
                return Collections.emptyMap();
            }

            List<PoolAndSchema> schemas = new LinkedList<>();
            do {
                schemas.add(new PoolAndSchema(result.getInt(1)/* write_db_pool_id */, result.getString(2)/* db_schema */));
            } while (result.next());
            closeSQLStuff(result, stmt);
            result = null;
            stmt = null;

            // Use a map to group by database schema association
            Map<PoolAndSchema, List<Integer>> map = new LinkedHashMap<>(256, 0.9F);
            for (PoolAndSchema schema : schemas) {
                stmt = con.prepareStatement("SELECT cid FROM context_server2db_pool WHERE db_schema=? AND write_db_pool_id=?");
                stmt.setString(1, schema.getSchema());
                stmt.setInt(2, schema.getPoolId());
                result = stmt.executeQuery();
                if (result.next()) {
                    List<Integer> contextIds = new LinkedList<>();
                    do {
                        contextIds.add(Integer.valueOf(result.getInt(1)));
                    } while (result.next());
                    map.put(schema, contextIds);
                }
                closeSQLStuff(result, stmt);
                result = null;
                stmt = null;
            }
            return map;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            if (null != con) {
                DBPool.closeReaderSilent(con);
            }
        }
    }

    @Override
    public Map<PoolAndSchema, List<Integer>> getSchemaAssociationsFor(List<Integer> contextIds) throws OXException {
        if (null == contextIds) {
            return Collections.emptyMap();
        }

        int size = contextIds.size();
        if (size <= 0) {
            return Collections.emptyMap();
        }

        Connection con = DBPool.pickup();
        try {
            if (size == 1) {
                // Only one context identifier given
                Integer contextId = contextIds.get(0);
                PreparedStatement stmt = null;
                ResultSet result = null;
                try {
                    stmt = con.prepareStatement("SELECT write_db_pool_id, db_schema FROM context_server2db_pool WHERE cid=?");
                    stmt.setInt(1, contextId.intValue());
                    result = stmt.executeQuery();
                    if (false == result.next()) {
                        return Collections.emptyMap();
                    }

                    PoolAndSchema pas = new PoolAndSchema(result.getInt(1)/*write_db_pool_id*/, result.getString(2)/*db_schema*/);
                    return Collections.singletonMap(pas, Collections.singletonList(contextId));
                } finally {
                    closeSQLStuff(result, stmt);
                }
            }

            // Use a map to group by database schema association
            Map<PoolAndSchema, List<Integer>> map = new LinkedHashMap<>(contextIds.size() >> 1, 0.9F);
            for (Set<Integer> chunk : Sets.partition(new LinkedHashSet<>(contextIds), Databases.IN_LIMIT)) {
                PreparedStatement stmt = null;
                ResultSet result = null;
                try {
                    stmt = con.prepareStatement(Databases.getIN("SELECT cid, write_db_pool_id, db_schema FROM context_server2db_pool WHERE cid IN (", chunk.size()));
                    int pos = 1;
                    for (Integer contextId : chunk) {
                        stmt.setInt(pos++, contextId.intValue());
                    }
                    result = stmt.executeQuery();
                    if (result.next()) {
                        do {
                            PoolAndSchema pas = new PoolAndSchema(result.getInt(2)/*write_db_pool_id*/, result.getString(3)/*db_schema*/);
                            List<Integer> cids = map.get(pas);
                            if (null == cids) {
                                cids = new LinkedList<>();
                                map.put(pas, cids);
                            }
                            cids.add(Integer.valueOf(result.getInt(1)/*cid*/));
                        } while (result.next());
                    }
                } finally {
                    closeSQLStuff(result, stmt);
                }
            }
            return map;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            if (null != con) {
                DBPool.closeReaderSilent(con);
            }
        }
    }

    @Override
    public List<Integer> getAllContextIdsForFilestore(int filestoreId) throws OXException {
        Connection con = DBPool.pickup();
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = con.prepareStatement("SELECT cid FROM context WHERE filestore_id = ?");
            stmt.setInt(1, filestoreId);
            result = stmt.executeQuery();
            if (!result.next()) {
                return Collections.emptyList();
            }

            List<Integer> retval = new ArrayList<Integer>();
            do {
                retval.add(Integer.valueOf(result.getInt(1)));
            } while (result.next());
            return retval;
        } catch (SQLException e) {
            throw ContextExceptionCodes.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeSQLStuff(result, stmt);
            DBPool.closeReaderSilent(con);
        }
    }

    @Override
    protected void shutDown() {
        // Nothing to do.
    }

    @Override
    protected void startUp() {
        // Nothing to do.
    }

    /**
     * Stores a internal user attribute. Internal user attributes must not be exposed to clients through the HTTP/JSON API.
     * <p>
     * This method might throw a {@link ContextExceptionCodes#CONCURRENT_ATTRIBUTES_UPDATE_DISPLAY} error in case a concurrent modification occurred. The
     * caller can decide to treat as an error or to simply ignore it.
     *
     * @param name Name of the attribute.
     * @param value Value of the attribute. If the value is <code>null</code>, the attribute is removed.
     * @param contextId Identifier of the context that attribute should be set.
     * @throws OXException if writing the attribute fails.
     * @see ContextExceptionCodes#CONCURRENT_ATTRIBUTES_UPDATE
     */
    @Override
    public void setAttribute(String name, String value, int contextId) throws OXException {
        if (null == name) {
            throw LdapExceptionCode.UNEXPECTED_ERROR.create("Attribute name is null.").setPrefix("CTX");
        }
        DatabaseService dbService = ServerServiceRegistry.getInstance().getService(DatabaseService.class);

        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbService.getWritable(contextId);
            if (value == null) {
                stmt = con.prepareStatement("DELETE FROM contextAttribute WHERE cid = ? AND name = ?");
                stmt.setInt(1, contextId);
                stmt.setString(2, name);
                stmt.executeUpdate();
            } else {
                insertOrUpdateAttribute(name, value, contextId, con);
            }
        } catch (SQLException e) {
            throw LdapExceptionCode.SQL_ERROR.create(e, e.getMessage()).setPrefix("CTX");
        } finally {
            Databases.closeSQLStuff(stmt);
            if (con != null) {
                dbService.backWritable(contextId, con);
            }
        }
    }

    private static void insertOrUpdateAttribute(String name, String newValue, int contextId, Connection con) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int rollback = 0;
        try {
            Databases.startTransaction(con);
            rollback = 1;
            stmt = con.prepareStatement("SELECT value FROM contextAttribute WHERE cid=? AND name=?");
            stmt.setInt(1, contextId);
            stmt.setString(2, name);
            rs = stmt.executeQuery();
            List<String> toUpdate = new LinkedList<String>();
            while (rs.next()) {
                toUpdate.add(rs.getString(1));
            }
            Databases.closeSQLStuff(rs, stmt);
            rs = null;
            if (toUpdate.isEmpty()) {
                stmt = con.prepareStatement("INSERT INTO contextAttribute (cid,name,value) VALUES (?,?,?)");
                stmt.setInt(1, contextId);
                stmt.setString(2, name);
                stmt.setString(3, newValue);
                stmt.executeUpdate();
            } else {
                stmt = con.prepareStatement("UPDATE contextAttribute SET value=? WHERE cid=? AND name=? AND value=?");
                for (String oldValue : toUpdate) {
                    stmt.setString(1, newValue);
                    stmt.setInt(2, contextId);
                    stmt.setString(3, name);
                    stmt.setString(4, oldValue);
                    stmt.addBatch();
                }
                int[] updateCounts = stmt.executeBatch();
                for (int updateCount : updateCounts) {
                    // Concurrent modification of at least one attribute. We lost the race...
                    if (updateCount != 1) {
                        Logger logger = org.slf4j.LoggerFactory.getLogger(RdbContextStorage.class);
                        logger.error("Concurrent modification of attribute '{}' for context {}. New value '{}' could not be set.", name, I(contextId), newValue);
                        throw ContextExceptionCodes.CONCURRENT_ATTRIBUTES_UPDATE.create(I(contextId));
                    }
                }
            }
            con.commit();
            rollback = 2;
        } catch (SQLException e) {
            throw UserExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UserExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            Databases.closeSQLStuff(stmt);
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
        }
    }
}
