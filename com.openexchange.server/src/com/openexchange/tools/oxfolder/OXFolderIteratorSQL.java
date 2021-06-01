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

package com.openexchange.tools.oxfolder;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.tools.oxfolder.OXFolderUtility.folderModule2String;
import static com.openexchange.tools.sql.DBUtils.closeResources;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.google.common.collect.ImmutableList;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.group.GroupStorage;
import com.openexchange.groupware.EnumComponent;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.i18n.Groups;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.tools.iterator.FolderObjectIterator;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.StringCollection;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorAdapter;
import com.openexchange.tools.iterator.SearchIteratorException;
import com.openexchange.tools.iterator.SearchIteratorExceptionCodes;
import com.openexchange.tools.oxfolder.memory.Condition;
import com.openexchange.tools.oxfolder.memory.ConditionTreeMap;
import com.openexchange.tools.oxfolder.memory.ConditionTreeMapManagement;
import gnu.trove.EmptyTIntSet;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * This class provides SQL related methods to fill instances of <code>com.openexchange.tools.iterator.FolderObjectIterator</code>
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class OXFolderIteratorSQL {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OXFolderIteratorSQL.class);

    private static final String OXFOLDER_PERMISSIONS = "oxfolder_permissions";

    private static final String OXFOLDER_TREE = "oxfolder_tree";

    private static final String STR_EMPTY = "";

    private static final String STR_SELECT = "SELECT ";

    private static final int TASK = FolderObject.TASK;

    private static final int CONTACT = FolderObject.CONTACT;

    private static final int CALENDAR = FolderObject.CALENDAR;

    private static final int INFOSTORE = FolderObject.INFOSTORE;

    private static final int SYSTEM_MODULE = FolderObject.SYSTEM_MODULE;

    private static final int PRIVATE = FolderObject.PRIVATE;

    private static final int PUBLIC = FolderObject.PUBLIC;

    private static final int TRASH = FolderObject.TRASH;

    private static final int SHARED = FolderObject.SHARED;

    private static final int SYSTEM_TYPE = FolderObject.SYSTEM_TYPE;

    private static final int SYSTEM_PUBLIC_FOLDER_ID = FolderObject.SYSTEM_PUBLIC_FOLDER_ID;

    private static final int SYSTEM_PRIVATE_FOLDER_ID = FolderObject.SYSTEM_PRIVATE_FOLDER_ID;

    private static final int SYSTEM_INFOSTORE_FOLDER_ID = FolderObject.SYSTEM_INFOSTORE_FOLDER_ID;

    private static final int SYSTEM_SHARED_FOLDER_ID = FolderObject.SYSTEM_SHARED_FOLDER_ID;

    private static final int SYSTEM_LDAP_FOLDER_ID = FolderObject.SYSTEM_LDAP_FOLDER_ID;

    private static final int SYSTEM_ROOT_FOLDER_ID = FolderObject.SYSTEM_ROOT_FOLDER_ID;

    private static final int VIRTUAL_LIST_INFOSTORE_FOLDER_ID = FolderObject.VIRTUAL_LIST_INFOSTORE_FOLDER_ID;

    private static final int VIRTUAL_LIST_CONTACT_FOLDER_ID = FolderObject.VIRTUAL_LIST_CONTACT_FOLDER_ID;

    private static final int VIRTUAL_LIST_CALENDAR_FOLDER_ID = FolderObject.VIRTUAL_LIST_CALENDAR_FOLDER_ID;

    private static final int VIRTUAL_LIST_TASK_FOLDER_ID = FolderObject.VIRTUAL_LIST_TASK_FOLDER_ID;

    private static final int PRIVATE_PERMISSION = FolderObject.PRIVATE_PERMISSION;

    /*private static final int SYSTEM_GLOBAL_FOLDER_ID = FolderObject.SYSTEM_GLOBAL_FOLDER_ID;*/ // finally dropped

    private static final String SHARED_PREFIX = FolderObject.SHARED_PREFIX;

    private static final int MIN_FOLDER_ID = FolderObject.MIN_FOLDER_ID;

    private static final String SQL_IN_STR_STANDARD_MODULES_ALL = FolderObject.SQL_IN_STR_STANDARD_MODULES_ALL;

    private static final String SQL_IN_STR_STANDARD_MODULES = FolderObject.SQL_IN_STR_STANDARD_MODULES;

    private OXFolderIteratorSQL() {
        super();
    }

    /**
     * Generates the core SQL statement to query user-visible folders.
     * <p>
     * Returned {@link String} is supposed to be used within a {@link PreparedStatement}.<br>
     * The following fields have to be set via {@link PreparedStatement#setInt(int, int)} method:
     * <ol>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <ol>
     *
     * @param fields The fields to select
     * @param permissionIds The user's permission identifiers
     * @param accessibleModules The user's accessible modules
     * @param additionalCondition The optional additional condition; pass <code>null</code> to ignore
     * @param queryAppendix The optional <code>ORDER BY</code> and/or <code>LIMIT</code> clause; pass <code>null</code> to ignore
     * @return The core SQL statement to query user-visible folders
     */
    private static String getSQLUserVisibleFolders(final String fields, final String permissionIds, final String accessibleModules, final String additionalCondition, final QueryAppendix queryAppendix) {
        return getSQLUserVisibleFolders(OXFOLDER_TREE, OXFOLDER_PERMISSIONS, fields, permissionIds, accessibleModules, additionalCondition, queryAppendix, false);
    }

    /**
     * Generates the core SQL statement to query user-visible folders.
     * <p>
     * Returned {@link String} is supposed to be used within a {@link PreparedStatement}.<br>
     * The following fields have to be set via {@link PreparedStatement#setInt(int, int)} method:
     * <ol>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <ol>
     *
     * @param folderTable The folder table name
     * @param permissionTable The permission table name
     * @param fields The fields to select
     * @param permissionIds The user's permission identifiers
     * @param accessibleModules The user's accessible modules
     * @param additionalCondition The optional additional condition; pass <code>null</code> to ignore
     * @param queryAppendix The optional <code>ORDER BY</code> and/or <code>LIMIT</code> clause; pass <code>null</code> to ignore
     * @param queryOptUserPrivate <code>true</code> to also query user private folders (optional); otherwise <code>false</code> for pure permission-wise query
     * @return The core SQL statement to query user-visible folders
     */
    private static String getSQLUserVisibleFolders(final String folderTable, final String permissionTable, final String fields, final String permissionIds, final String accessibleModules, final String additionalCondition, final QueryAppendix queryAppendix, final boolean queryOptUserPrivate) {
        return getSQLUserVisibleFolders(folderTable, permissionTable, fields, permissionIds, accessibleModules, additionalCondition, queryAppendix, queryOptUserPrivate, new String[0]);
    }

    /**
     * Generates the core SQL statement to query user-visible folders.
     * <p>
     * Returned {@link String} is supposed to be used within a {@link PreparedStatement}.<br>
     * The following fields have to be set via {@link PreparedStatement#setInt(int, int)} method:
     * <ol>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <ol>
     *
     * @param folderTable The folder table name
     * @param permissionTable The permission table name
     * @param fields The fields to select
     * @param permissionIds The user's permission identifiers
     * @param accessibleModules The user's accessible modules
     * @param additionalCondition The optional additional condition; pass <code>null</code> to ignore
     * @param queryAppendix The optional <code>ORDER BY</code> and/or <code>LIMIT</code> clause; pass <code>null</code> to ignore
     * @param queryOptUserPrivate <code>true</code> to also query user private folders (optional); otherwise <code>false</code> for pure permission-wise query
     * @param indexNames The optional indexes to use (<code>"...FORCE INDEX..."</code>)
     * @return The core SQL statement to query user-visible folders
     */
    private static String getSQLUserVisibleFolders(String folderTable, String permissionTable, String fields, String permissionIds, String accessibleModules, String additionalCondition, QueryAppendix queryAppendix, boolean queryOptUserPrivate, String... indexNames) {
        return getSQLUserVisibleFolders(false, folderTable, permissionTable, fields, permissionIds, accessibleModules, additionalCondition, queryAppendix, queryOptUserPrivate, indexNames);
    }

    /**
     * Generates the core SQL statement to query user-visible folders.
     * <p>
     * Returned {@link String} is supposed to be used within a {@link PreparedStatement}.<br>
     * The following fields have to be set via {@link PreparedStatement#setInt(int, int)} method:
     * <ol>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <li>User ID</li>
     * <li>Context ID</li>
     * <li>Context ID</li>
     * <ol>
     *
     * @param useUnionAll Whether to use <code>"UNION ALL"</code> if duplicates are allowed
     * @param folderTable The folder table name
     * @param permissionTable The permission table name
     * @param fields The fields to select
     * @param permissionIds The user's permission identifiers
     * @param accessibleModules The user's accessible modules
     * @param additionalCondition The optional additional condition; pass <code>null</code> to ignore
     * @param queryAppendix The optional <code>ORDER BY</code> and/or <code>LIMIT</code> clause; pass <code>null</code> to ignore
     * @param queryOptUserPrivate <code>true</code> to also query user private folders (optional); otherwise <code>false</code> for pure permission-wise query
     * @param indexNames The optional indexes to use (<code>"...FORCE INDEX..."</code>)
     * @return The core SQL statement to query user-visible folders
     */
    private static String getSQLUserVisibleFolders(boolean useUnionAll, String folderTable, String permissionTable, String fields, String permissionIds, String accessibleModules, String additionalCondition, QueryAppendix queryAppendix, boolean queryOptUserPrivate, String... indexNames) {
        /*
         * Compose SELECT string prepended to each UNION statement
         */
        final StringBuilder sb = new StringBuilder(256);
        sb.append(STR_SELECT).append(fields);
        final String preparedQueryAppendix;
        if (null == queryAppendix) {
            preparedQueryAppendix = null;
        } else {
            /*
             * Ensure each field contained in ORDER BY clause is contained in selected fields
             */
            OrderBy orderBy = queryAppendix.orderBy;
            if (null != orderBy) {
                StringBuilder tmp = null;
                List<ColumnAndDirection> columnsToOrderBy = orderBy.columns;
                for (ColumnAndDirection columnToOrderBy : columnsToOrderBy) {
                    if (null == tmp) {
                        tmp = new StringBuilder(16);
                    } else {
                        tmp.setLength(0);
                    }
                    String orderField = tmp.append(columnToOrderBy.tableAlias).append('.').append(columnToOrderBy.columnName).toString();
                    if (-1 == fields.indexOf(orderField)) {
                        sb.append(", ").append(orderField);
                    }
                }
            }
            /*
             * Prepare the ORDER BY clause by removing table alias
             */
            preparedQueryAppendix = queryAppendix.toString();
        }
        sb.append(" FROM ").append(folderTable).append(" AS ot");
        /*
         * Add index name
         */
        if (null != indexNames && indexNames.length > 0) {
            // " FORCE INDEX (lastModifiedIndex)"
            sb.append(" FORCE INDEX (");
            sb.append(indexNames[0]);
            for (int i = 1; i < indexNames.length; i++) {
                sb.append(',').append(indexNames[i]);
            }
            sb.append(')');
        }
        final String select = sb.toString();
        sb.setLength(0);
        /*
         * Compose WHERE clauses
         */
        final List<String> whereClauses = new ArrayList<String>(3);
        /*-
         * Optional:
         *
         * WHERE ot.cid = ? AND (ot.permission_flag = 1 AND ot.created_from = ?)
         *
         * 1. cid
         * 2. user
         */
        if (queryOptUserPrivate) {
            sb.append(" WHERE ot.cid = ? AND (ot.permission_flag = ").append(PRIVATE_PERMISSION).append(" AND ot.created_from = ?)");
            appendix(sb, accessibleModules, additionalCondition);
            whereClauses.add(sb.toString());
            sb.setLength(0);
        }
        /*-
         * JOIN oxfolder_permissions AS op ON ot.fuid = op.fuid AND ot.cid = ? AND op.cid = ?
         * WHERE (op.admin_flag = 1 AND op.permission_id = ?)
         *
         * 3. cid
         * 4. cid
         * 5. user
         */
        sb.append(" JOIN ").append(permissionTable).append(" AS op ON ot.fuid = op.fuid AND ot.cid = ? AND op.cid = ? WHERE (op.admin_flag = 1 AND op.permission_id = ?)");
        appendix(sb, accessibleModules, additionalCondition);
        whereClauses.add(sb.toString());
        sb.setLength(0);
        /*-
         * JOIN oxfolder_permissions AS op ON ot.fuid = op.fuid AND ot.cid = ? AND op.cid = ?
         * WHERE (op.fp > 0 AND op.permission_id IN (17,0,1))
         *
         * 6. cid
         * 7. cid
         */
        sb.append(" JOIN ").append(permissionTable).append(" AS op ON ot.fuid = op.fuid AND ot.cid = ? AND op.cid = ? WHERE (op.fp > ").append(OCLPermission.NO_PERMISSIONS).append(" AND op.permission_id IN ").append(permissionIds).append(')');
        appendix(sb, accessibleModules, additionalCondition);
        whereClauses.add(sb.toString());
        sb.setLength(0);
        /*
         * Finally, compose UNION statement
         */
        Iterator<String> it = whereClauses.iterator();
        {
            sb.append(select);
            sb.append(it.next());
        }
        for (int i = whereClauses.size() - 1; i-- > 0;) {
            sb.append(" UNION ");
            if (useUnionAll) {
                sb.append("ALL ");
            }
            sb.append(select);
            sb.append(it.next());
        }
        if (null != preparedQueryAppendix) {
            sb.append(' ').append(preparedQueryAppendix);
        }
        return sb.toString();
    }

    private static final void appendix(final StringBuilder sb, final String accessibleModules, final String additionalCondition) {
        /*-
         *
        if (OXFolderProperties.isIgnoreSharedAddressbook()) {
            sb.append(" AND (ot.fuid != ").append(SYSTEM_GLOBAL_FOLDER_ID).append(')');
        }
        */
        if (accessibleModules != null) {
            sb.append(" AND (ot.module IN ").append(accessibleModules).append(')');
        }
        if (additionalCondition != null) {
            sb.append(' ').append(additionalCondition);
        }
    }

    private final static String STR_OT = "ot";

    private final static String STR_FUID = "fuid";

    private static QueryAppendix getRootOrderBy(final String tableAlias, final int limit) {
        return new QueryAppendix(new OrderBy(new ColumnAndDirection(tableAlias, "fuid", Order.ASC)), limit);
    }

    private static QueryAppendix getSubfolderOrderBy(final String tableAlias, final int limit) {
        return new QueryAppendix(new OrderBy(new ColumnAndDirection(tableAlias, "default_flag", Order.DESC), new ColumnAndDirection(tableAlias, "fname", Order.ASC)), limit);
    }

    private final static String STR_GROUP_BY = "GROUP BY ";

    private static String getGroupBy(final String tableAlias) {
        final String alias;
        if (tableAlias == null) {
            alias = STR_EMPTY;
        } else {
            alias = new StringBuilder(tableAlias.length() + 1).append(tableAlias).append('.').toString();
        }
        return new StringBuilder(STR_GROUP_BY).append(alias).append(STR_FUID).toString();
    }

    /**
     * Gets the user-visible root folders.
     *
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param permissionBits The user's permission bits
     * @param ctx The context
     * @return The user-visible root folders
     * @throws OXException If a folder error occurs
     * @throws SearchIteratorException If a search iterator error occurs
     */
    public static SearchIterator<FolderObject> getUserRootFoldersIterator(final int userId, final int[] memberInGroups, final UserPermissionBits permissionBits, final Context ctx) throws OXException {
        StringBuilder condBuilder = new StringBuilder(32).append("AND (ot.type = ").append(SYSTEM_TYPE).append(") AND (ot.parent = 0)");
        /*
         * Check whether to display shared folder
         */
        if (!permissionBits.hasFullSharedFolderAccess()) {
            condBuilder.append(" AND (ot.fuid != ").append(SYSTEM_SHARED_FOLDER_ID).append(')');
        }
        /*
         * Check whether to display infostore folder
         */
        if (!permissionBits.hasInfostore()) {
            condBuilder.append(" AND (ot.fuid != ").append(SYSTEM_INFOSTORE_FOLDER_ID).append(')');
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(permissionBits.getAccessibleModules()), condBuilder.toString(), getRootOrderBy(STR_OT, 0));
        condBuilder = null;
        Connection readCon = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        boolean closeResources = true;
        try {
            readCon = DBPool.pickup(ctx);
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            closeResources = false;
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        } finally {
            if (closeResources) {
                closeResources(rs, stmt, readCon, true, ctx);
            }
        }
        try {
            return new FolderObjectIterator(rs, stmt, true, ctx, readCon, true);
        } catch (OXException e) {
            closeResources(rs, stmt, readCon, true, ctx);
            throw e;
        }
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible sub folders of a
     * certain parent folder.
     */
    public static SearchIterator<FolderObject> getVisibleSubfoldersIterator(final int parentFolderId, final int userId, final int[] groups, final Context ctx, final UserPermissionBits permissionBits, final Timestamp since) throws OXException {
        return getVisibleSubfoldersIterator(parentFolderId, userId, groups, ctx, permissionBits, since, null);
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible sub folders of a
     * certain parent folder.
     */
    public static SearchIterator<FolderObject> getVisibleSubfoldersIterator(final int parentFolderId, final int userId, final int[] groups, final Context ctx, final UserPermissionBits permissionBits, final Timestamp since, final Connection con) throws OXException {
        if (parentFolderId == SYSTEM_ROOT_FOLDER_ID) {
            return getUserRootFoldersIterator(userId, groups, permissionBits, ctx);
        } else if (parentFolderId == SYSTEM_PRIVATE_FOLDER_ID) {
            return getVisiblePrivateFolders(userId, groups, permissionBits.getAccessibleModules(), ctx, since, con);
        } else if (parentFolderId == SYSTEM_PUBLIC_FOLDER_ID) {
            return getVisiblePublicFolders(userId, groups, permissionBits.getAccessibleModules(), ctx, since, con);
        } else if (parentFolderId == SYSTEM_SHARED_FOLDER_ID) {
            return getVisibleSharedFolders(userId, groups, permissionBits.getAccessibleModules(), ctx, since, con);
        } else {
            /*
             * Check user's effective permission on subfolder's parent
             */
            final FolderObject parentFolder = new OXFolderAccess(con, ctx).getFolderObject(parentFolderId);
            final OCLPermission effectivePerm = parentFolder.getEffectiveUserPermission(userId, permissionBits);
            if (effectivePerm.getFolderPermission() < OCLPermission.READ_FOLDER) {
                return FolderObjectIterator.EMPTY_FOLDER_ITERATOR;
            }
            return getVisibleSubfoldersIterator(parentFolder, userId, groups, permissionBits.getAccessibleModules(), ctx, since, con);
        }
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances which are located beneath system's private folder.
     */
    private static SearchIterator<FolderObject> getVisiblePrivateFolders(final int userId, final int[] groups, final int[] accessibleModules, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                final List<Condition> conditions = new ArrayList<Condition>(3);
                conditions.add(new ConditionTreeMap.PrivateCondition(userId));
                conditions.add(new ConditionTreeMap.ParentCondition(SYSTEM_PRIVATE_FOLDER_ID));
                if (since != null) {
                    conditions.add(new ConditionTreeMap.LastModifiedCondition(since.getTime()));
                }
                final TIntSet set = treeMap.getVisibleForUser(userId, groups, accessibleModules, conditions);
                final List<FolderObject> list = ConditionTreeMap.asList(set, ctx, con);
                return new FolderObjectIterator(list, false);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final StringBuilder condBuilder = new StringBuilder(32).append("AND (ot.type = ").append(PRIVATE).append(" AND ot.created_from = ").append(userId).append(") AND (ot.parent = ").append(SYSTEM_PRIVATE_FOLDER_ID).append(')');
        if (since != null) {
            condBuilder.append(" AND (changing_date > ").append(since.getTime()).append(')');
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, groups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            /*
             * Ensure ordering of private default folder follows: calendar, contacts, tasks
             */
            @SuppressWarnings("resource") final List<FolderObject> list = new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeCon).asList();
            if (list.size() >= 3 && (list.get(0).getModule() != CALENDAR || list.get(1).getModule() != CONTACT || list.get(2).getModule() != TASK)) {
                final FolderObject[] defaultFolders = new FolderObject[] { list.remove(0), list.remove(0), list.remove(0) };
                /*
                 * Restore order
                 */
                switchElements(defaultFolders);
                for (int i = 0; i < defaultFolders.length; i++) {
                    list.add(i, defaultFolders[i]);
                }
            }
            return new FolderObjectIterator(list, false);
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        // return new FolderObjectIterator(rs, stmt, false, ctx, readCon, true);
    }

    private static final int[] DEF_MODULES = { CALENDAR, CONTACT, TASK };

    private static void switchElements(final FolderObject[] folders) {
        for (int i = 0; i < folders.length; i++) {
            boolean switched = false;
            for (int j = 0; j < DEF_MODULES.length && !switched; j++) {
                if (folders[i].getModule() == DEF_MODULES[j] && i != j) {
                    /*
                     * Switch elements
                     */
                    final FolderObject tmp = folders[j];
                    folders[j] = folders[i];
                    folders[i] = tmp;
                    switched = true;
                }
            }
        }
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances which are located beneath system's public folder.
     */
    private static SearchIterator<FolderObject> getVisiblePublicFolders(final int userId, final int[] groups, final int[] accessibleModules, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final SQLStuff sqlStuff = getVisiblePublicFolders0(userId, groups, accessibleModules, ctx, since, con);
        try {
            return new FolderObjectIterator(sqlStuff.rs, sqlStuff.stmt, false, ctx, sqlStuff.readCon, sqlStuff.closeCon);
        } catch (OXException e) {
            closeResources(sqlStuff.rs, sqlStuff.stmt, sqlStuff.closeCon ? sqlStuff.readCon : null, true, ctx);
            throw e;
        }
    }

    /**
     * Returns the <code>SQLStuff</code> for subfolders which are located beneath system's public folder.
     */
    private static SQLStuff getVisiblePublicFolders0(final int userId, final int[] groups, final int[] accessibleModules, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final StringBuilder condBuilder = new StringBuilder(32).append("AND (ot.type = ").append(PUBLIC).append(") AND (ot.parent = ").append(SYSTEM_PUBLIC_FOLDER_ID).append(')');
        if (null != since) {
            condBuilder.append(" AND (changing_date > ").append(since.getTime()).append(')');
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, groups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            return new SQLStuff(stmt, rs, readCon, closeCon);
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances which offer a share right for given user and therefore
     * should appear right beneath system's shared folder in displayed folder tree.
     */
    private static SearchIterator<FolderObject> getVisibleSharedFolders(final int userId, final int[] groups, final int[] accessibleModules, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        return getVisibleSharedFolders(userId, groups, accessibleModules, -1, ctx, since, con);
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances which are located beneath given parent folder.
     */
    private static SearchIterator<FolderObject> getVisibleSubfoldersIterator(final FolderObject parentFolder, final int userId, final int[] memberInGroups, final int[] accessibleModules, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final boolean shared = parentFolder.isShared(userId);
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                final List<Condition> conditions = new ArrayList<Condition>(3);
                if (shared) {
                    conditions.add(new ConditionTreeMap.TypeCondition(SHARED, userId));
                }
                conditions.add(new ConditionTreeMap.ParentCondition(parentFolder.getObjectID()));
                if (since != null) {
                    conditions.add(new ConditionTreeMap.LastModifiedCondition(since.getTime()));
                }
                final TIntSet set = treeMap.getVisibleForUser(userId, memberInGroups, accessibleModules, conditions);
                final List<FolderObject> list = ConditionTreeMap.asList(set, ctx, con);
                return new FolderObjectIterator(list, false);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final StringBuilder condBuilder = new StringBuilder(32);
        if (shared) {
            condBuilder.append("AND (ot.type = ").append(PRIVATE).append(" AND ot.created_from != ").append(userId).append(") ");
        }
        condBuilder.append("AND (ot.parent = ").append(parentFolder.getObjectID()).append(')');
        if (null != since) {
            condBuilder.append(" AND (changing_date > ").append(since.getTime()).append(')');
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // tmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeCon);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        }
    }

    /**
     * Checks if specified folder is visible.
     *
     * @param folderId The folder identifier
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param accessibleModules The user's accessible modules
     * @param ctx The context
     * @param con An optional connection; set to <code>null</code> to obtain from connection pool
     * @return <code>true</code> if folder is visible; otherwise <code>false</code>
     * @throws OXException If an error occurs
     */
    public static boolean isVisibleFolder(final int folderId, final int userId, final int[] memberInGroups, final int[] accessibleModules, final Context ctx, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                return treeMap.isVisibleFolder(userId, memberInGroups, accessibleModules, folderId);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final StringBuilder condBuilder = new StringBuilder(32);
        final String fields = condBuilder.append(STR_OT).append(".fuid").toString();
        condBuilder.setLength(0);
        condBuilder.append("AND (ot.fuid = ").append(folderId).append(')');
        final String sqlSelectStr = getSQLUserVisibleFolders(fields, permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            return rs.next();
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        } finally {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
        }
    }

    /**
     * Gets visible subfolders' identifiers from specified parent.
     *
     * @param parent The parent identifier
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param accessibleModules The user's accessible modules
     * @param ctx The context
     * @param con An optional connection; set to <code>null</code> to obtain from connection pool
     * @return The subfolders' identifiers
     * @throws OXException If an error occurs
     */
    public static TIntList getVisibleSubfolders(final int parent, final int userId, final int[] memberInGroups, final int[] accessibleModules, final Context ctx, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                final List<Condition> conditions = Collections.<Condition> singletonList(new ConditionTreeMap.ParentCondition(parent));
                return new TIntArrayList(treeMap.getVisibleForUser(userId, memberInGroups, accessibleModules, conditions));
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final StringBuilder condBuilder = new StringBuilder(32);
        final String fields = condBuilder.append(STR_OT).append(".fuid").toString();
        condBuilder.setLength(0);
        condBuilder.append("AND (ot.parent = ").append(parent).append(')');
        final String sqlSelectStr = getSQLUserVisibleFolders(fields, permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            if (!rs.next()) {
                return new TIntArrayList(0);
            }
            final TIntList retval = new TIntArrayList(16);
            do {
                retval.add(rs.getInt(1));
            } while (rs.next());
            return retval;
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        } finally {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
        }
    }

    /**
     * Checks existence of user-visible shared folders.
     */
    public static boolean hasVisibleSharedFolders(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int owner, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final SQLStuff stuff = getVisibleSharedFolders0(userId, memberInGroups, accessibleModules, owner, ctx, since, con);
        try {
            return stuff.rs.next();
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (Exception e) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(e, EnumComponent.FOLDER, e.getMessage());
        } finally {
            closeResources(stuff.rs, stuff.stmt, stuff.closeCon ? stuff.readCon : null, true, ctx);
        }
    }

    /**
     * Returns an <code>SearchIterator</code> of <code>FolderObject</code> instances of user-visible shared folders.
     */
    public static SearchIterator<FolderObject> getVisibleSharedFolders(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int owner, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                final List<Condition> conditions = new ArrayList<Condition>(3);
                conditions.add(new ConditionTreeMap.TypeCondition(FolderObject.SHARED, userId));
                if (owner > -1) {
                    conditions.add(new ConditionTreeMap.CreatorCondition(owner));
                }
                if (since != null) {
                    conditions.add(new ConditionTreeMap.LastModifiedCondition(since.getTime()));
                }
                final TIntSet set = treeMap.getVisibleForUser(userId, memberInGroups, accessibleModules, conditions);
                final List<FolderObject> list = ConditionTreeMap.asList(set, ctx, con);
                return new FolderObjectIterator(list, false);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final SQLStuff stuff = getVisibleSharedFolders0(userId, memberInGroups, accessibleModules, owner, ctx, since, con);
        try {
            return new FolderObjectIterator(stuff.rs, stuff.stmt, false, ctx, stuff.readCon, stuff.closeCon);
        } catch (OXException e) {
            closeResources(stuff.rs, stuff.stmt, stuff.closeCon ? stuff.readCon : null, true, ctx);
            throw e;
        } catch (Exception e) {
            closeResources(stuff.rs, stuff.stmt, stuff.closeCon ? stuff.readCon : null, true, ctx);
            throw SearchIteratorExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage()).setPrefix("FLD");
        }
    }

    private static SQLStuff getVisibleSharedFolders0(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int owner, final Context ctx, final Timestamp since, final Connection con) throws OXException {
        final StringBuilder condBuilder = new StringBuilder(32).append("AND (ot.type = ").append(PRIVATE).append(" AND ot.created_from != ").append(userId).append(')');
        if (owner > -1) {
            condBuilder.append(" AND (ot.created_from = ").append(owner).append(')');
        }
        if (since != null) {
            condBuilder.append(" AND (changing_date > ").append(since.getTime()).append(')');
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        return new SQLStuff(stmt, rs, readCon, closeCon);
    }

    /**
     * Gets all visible public folders that are not visible in hierarchic tree-view (because any ancestor folder is not visible)
     *
     * @param userId The user ID
     * @param groups The user's group IDs
     * @param permissionBits The user permission bits
     * @param ctx The context
     * @return An iterator for all visible public folders that are not visible in hierarchic tree-view (because any ancestor folder is not
     *         visible)
     * @throws OXException If all visible public folders that are not visible in hierarchic tree-view cannot be determined
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersNotSeenInTreeView(final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx) throws OXException {
        return getVisibleFoldersNotSeenInTreeViewNew(null, userId, groups, permissionBits, ctx, null);
    }

    /**
     * Gets all visible public folders that are not visible in hierarchic tree-view (because any ancestor folder is not visible)
     *
     * @param userId The user ID
     * @param groups The user's group IDs
     * @param permissionBits The user permission bits
     * @param ctx The context
     * @param readCon An readable connection (optional: may be <code>null</code>)
     * @return An iterator for all visible public folders that are not visible in hierarchic tree-view (because any ancestor folder is not
     *         visible)
     * @throws OXException If all visible public folders that are not visible in hierarchic tree-view cannot be determined
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersNotSeenInTreeView(final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx, final Connection readCon) throws OXException {
        return getVisibleFoldersNotSeenInTreeViewNew(null, userId, groups, permissionBits, ctx, readCon);
    }

    /**
     * Gets specified module's visible public folders that are not visible in hierarchic tree-view (because any ancestor folder is not
     * visible)
     *
     * @param module The module whose non-hierarchic-visible folders should be determined
     * @param userId The user ID
     * @param groups The user's group IDs
     * @param permissionBits The user permission bits
     * @param ctx The context
     * @param readCon An readable connection (optional: may be <code>null</code>)
     * @return An iterator for specified module's visible public folders that are not visible in hierarchic tree-view
     * @throws OXException If module's visible public folders that are not visible in hierarchic tree-view cannot be determined
     */
    public static SearchIterator<FolderObject> getVisibleFoldersNotSeenInTreeView(final int module, final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx, final Connection readCon) throws OXException {
        return getVisibleFoldersNotSeenInTreeViewNew(Integer.valueOf(module), userId, groups, permissionBits, ctx, readCon);
    }

    /**
     * Checks for non-tree-visible folder of specified module.
     *
     * @param module The module
     * @param userId The user ID
     * @param groups The user's group IDs
     * @param permissionBits The user permission bits
     * @param ctx The context
     * @param readCon An readable connection (optional: may be <code>null</code>)
     * @return <code>true</code> if non-tree-visible folder of specified module exist; otherwise <code>false</code>
     * @throws OXException If module's visible public folders that are not visible in hierarchic tree-view cannot be determined
     */
    public static boolean hasVisibleFoldersNotSeenInTreeView(final int module, final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx, final Connection readCon) throws OXException {
        return hasVisibleFoldersNotSeenInTreeView(new int[] {module}, userId, groups, permissionBits, ctx, readCon).contains(module);
    }

    /**
     * Checks for non-tree-visible folder of specified modules.
     *
     * @param modules The modules
     * @param userId The user ID
     * @param groups The user's group IDs
     * @param permissionBits The user permission bits
     * @param ctx The context
     * @param readCon An readable connection (optional: may be <code>null</code>)
     * @return The set of contained modules
     * @throws OXException If module's visible public folders that are not visible in hierarchic tree-view cannot be determined
     */
    public static TIntSet hasVisibleFoldersNotSeenInTreeView(final int[] modules, final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx, final Connection readCon) throws OXException {
        if (null == modules || modules.length == 0) {
            return EmptyTIntSet.getInstance();
        }

        Connection rc = readCon;
        boolean closeReadCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (readCon == null) {
                rc = DBPool.pickup(ctx);
                closeReadCon = true;
            }
            /*
             * Statement to select all user-visible public folders
             */
            TIntSet modulesToCheck = new TIntHashSet(modules);
            String condition = (modulesToCheck.contains(FolderObject.INFOSTORE) ? new StringBuilder("AND (ot.type IN (").append(PUBLIC).append(',').append(TRASH).append("))") : new StringBuilder("AND (ot.type = ").append(PUBLIC).append(')')).toString();
            stmt = rc.prepareStatement(getSQLUserVisibleFolders(true, OXFOLDER_TREE, OXFOLDER_PERMISSIONS, "ot.fuid, ot.parent, ot.module", permissionIds(userId, groups, ctx), StringCollection.getSqlInString(permissionBits.getAccessibleModules()), condition, null, false, new String[0]));
            int pos = 1;
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            rs = executeQuery(stmt);
            if (!rs.next()) {
                closeResources(rs, stmt, closeReadCon ? rc : null, true, ctx);
                return EmptyTIntSet.getInstance();
            }

            TIntSet retval = new TIntHashSet(modules.length);
            TIntIntMap fuid2parent = new TIntIntHashMap(128);
            TIntIntMap fuid2module = new TIntIntHashMap(128);
            TIntSet fuids = new TIntHashSet(128);
            do {
                int fuid = rs.getInt(1);
                fuid2parent.put(fuid, rs.getInt(2));
                fuid2module.put(fuid, rs.getInt(3));
                fuids.add(fuid);
            } while (rs.next());
            closeResources(rs, stmt, closeReadCon ? rc : null, true, ctx);
            /*
             * Check for a parent not contained in keys
             */
            for (final TIntIntIterator iterator = fuid2parent.iterator(); iterator.hasNext();) {
                iterator.advance();
                final int fuid = iterator.key();
                final int parent = iterator.value();
                if (parent >= MIN_FOLDER_ID && !fuids.contains(parent)) {
                    int module = fuid2module.get(fuid);
                    if (modulesToCheck.remove(module)) {
                        retval.add(module);
                        if (modulesToCheck.isEmpty()) {
                            return retval;
                        }
                    }
                }
            }
            return retval;
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        } finally {
            closeResources(rs, stmt, closeReadCon ? rc : null, true, ctx);
        }
    }

    private static SearchIterator<FolderObject> getVisibleFoldersNotSeenInTreeViewNew(final Integer module, final int userId, final int[] groups, final UserPermissionBits permissionBits, final Context ctx, final Connection readCon) throws OXException {
        Connection rc = readCon;
        boolean closeReadCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        boolean closeResources = true;
        try {
            if (readCon == null) {
                rc = DBPool.pickup(ctx);
                closeReadCon = true;
            }
            /*
             * Statement to select all user-visible public folders
             */
            {
                final StringBuilder condBuilder = new StringBuilder(32).append("AND (ot.type = ").append(PUBLIC);
                if (null != module) {
                    condBuilder.append(") AND (ot.module = ").append(module.intValue());
                }
                condBuilder.append(')');
                stmt = rc.prepareStatement(getSQLUserVisibleFolders("ot.fuid, ot.parent", // fuid, parent, ...
                permissionIds(userId, groups, ctx), StringCollection.getSqlInString(permissionBits.getAccessibleModules()), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0)));
            }
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
            if (!rs.next()) {
                return FolderObjectIterator.EMPTY_FOLDER_ITERATOR;
            }
            final TIntIntMap fuid2parent = new TIntIntHashMap(128);
            final TIntSet fuids = new TIntHashSet(128);
            do {
                final int fuid = rs.getInt(1);
                fuid2parent.put(fuid, rs.getInt(2));
                fuids.add(fuid);
            } while (rs.next());
            Databases.closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;
            /*
             * Remove those fuids with a parent contained as a key
             */
            for (final TIntIntIterator iterator = fuid2parent.iterator(); iterator.hasNext();) {
                iterator.advance();
                final int parent = iterator.value();
                if (parent < MIN_FOLDER_ID || fuids.contains(parent)) {
                    iterator.remove();
                }
            }
            /*
             * Remaining entries have a non-visible parent
             */
            if (fuid2parent.isEmpty()) {
                return FolderObjectIterator.EMPTY_FOLDER_ITERATOR;
            }
            stmt = rc.prepareStatement("SELECT " + FolderObjectIterator.getFieldsForSQL(STR_OT) + " FROM oxfolder_tree AS ot WHERE ot.cid = ? AND ot.fuid IN " + StringCollection.getSqlInString(fuid2parent.keys()) + ' ' + getSubfolderOrderBy(STR_OT, 0));
            stmt.setInt(1, contextId);
            rs = executeQuery(stmt);
            closeResources = false;
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        } finally {
            if (closeResources) {
                closeResources(rs, stmt, closeReadCon ? rc : null, true, ctx);
            }
        }
        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeReadCon);
        } catch (OXException e) {
            closeResources(rs, stmt, closeReadCon ? rc : null, true, ctx);
            throw e;
        }
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> which represent all visible folders lying on path from given
     * folder to root folder.
     */
    public static SearchIterator<FolderObject> getFoldersOnPathToRoot(final int folderId, final int userId, final UserPermissionBits permissionBits, final Locale locale, final Context ctx) throws OXException {
        final List<FolderObject> folderList = new ArrayList<FolderObject>();
        fillAncestor(folderList, folderId, userId, permissionBits, locale, null, ctx);
        return new FolderObjectIterator(folderList, false);
    }

    private static void fillAncestor(final List<FolderObject> folderList, final int folderId, final int userId, final UserPermissionBits permissionBits, final Locale locale, final UserStorage userStoreArg, final Context ctx) throws OXException {
        final OXFolderAccess access = new OXFolderAccess(ctx);
        if (checkForSpecialFolder(folderList, folderId, locale, access)) {
            return;
        }
        UserStorage userStore = userStoreArg;
        FolderObject fo = access.getFolderObject(folderId);
        final int contextId = ctx.getContextId();
        try {
            if (!fo.getEffectiveUserPermission(userId, permissionBits).isFolderVisible()) {
                if (folderList.isEmpty()) {
                    /*
                     * Starting folder is not visible to user
                     */
                    throw OXFolderExceptionCode.NOT_VISIBLE.create(Integer.valueOf(folderId), I(userId), Integer.valueOf(contextId));
                }
                return;
            }
            if (fo.isShared(userId)) {
                folderList.add(fo);
                /*
                 * Shared: Create virtual folder named to folder owner
                 */
                if (userStore == null) {
                    userStore = UserStorage.getInstance();
                }
                String creatorDisplayName;
                try {
                    creatorDisplayName = userStore.getUser(fo.getCreatedBy(), ctx).getDisplayName();
                } catch (OXException e) {
                    if (fo.getCreatedBy() != OCLPermission.ALL_GROUPS_AND_USERS) {
                        throw e;
                    }
                    final StringHelper strHelper = StringHelper.valueOf(locale);
                    creatorDisplayName = strHelper.getString(Groups.ALL_USERS);
                }
                final FolderObject virtualOwnerFolder = FolderObject.createVirtualFolderObject(SHARED_PREFIX + fo.getCreatedBy(), creatorDisplayName, SYSTEM_MODULE, true, SYSTEM_TYPE);
                folderList.add(virtualOwnerFolder);
                /*
                 * Set folder to system shared folder
                 */
                fo = access.getFolderObject(SYSTEM_SHARED_FOLDER_ID);
                fo.setFolderName(FolderObject.getFolderString(SYSTEM_SHARED_FOLDER_ID, locale));
                folderList.add(fo);
                return;
            } else if (fo.getType() == PUBLIC && hasNonVisibleParent(fo, userId, permissionBits, access)) {
                /*
                 * Insert current folder
                 */
                folderList.add(fo);
                final int virtualParent;
                switch (fo.getModule()) {
                    case TASK:
                        virtualParent = VIRTUAL_LIST_TASK_FOLDER_ID;
                        break;
                    case CALENDAR:
                        virtualParent = VIRTUAL_LIST_CALENDAR_FOLDER_ID;
                        break;
                    case CONTACT:
                        virtualParent = VIRTUAL_LIST_CONTACT_FOLDER_ID;
                        break;
                    case INFOSTORE:
                        virtualParent = VIRTUAL_LIST_INFOSTORE_FOLDER_ID;
                        break;
                    default:
                        throw OXFolderExceptionCode.UNKNOWN_MODULE.create(STR_EMPTY, folderModule2String(fo.getModule()), Integer.valueOf(contextId));
                }
                checkForSpecialFolder(folderList, virtualParent, locale, access);
                return;
            }
            /*
             * Add folder to path
             */
            folderList.add(fo);
            /*
             * Follow ancestors to root
             */
            if (fo.getParentFolderID() != SYSTEM_ROOT_FOLDER_ID) {
                fillAncestor(folderList, fo.getParentFolderID(), userId, permissionBits, locale, userStore, ctx);
            }
        } catch (RuntimeException t) {
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
    }

    private static boolean checkForSpecialFolder(final List<FolderObject> folderList, final int folderId, final Locale locale, final OXFolderAccess access) throws OXException {
        final boolean publicParent;
        final FolderObject specialFolder;
        switch (folderId) {
            case SYSTEM_LDAP_FOLDER_ID:
                specialFolder = access.getFolderObject(folderId);
                specialFolder.setFolderName(FolderObject.getFolderString(SYSTEM_LDAP_FOLDER_ID, locale));
                publicParent = true;
                break;
            case VIRTUAL_LIST_TASK_FOLDER_ID:
                specialFolder = FolderObject.createVirtualFolderObject(VIRTUAL_LIST_TASK_FOLDER_ID, FolderObject.getFolderString(VIRTUAL_LIST_TASK_FOLDER_ID, locale), SYSTEM_MODULE, true, SYSTEM_TYPE);
                publicParent = true;
                break;
            case VIRTUAL_LIST_CALENDAR_FOLDER_ID:
                specialFolder = FolderObject.createVirtualFolderObject(VIRTUAL_LIST_CALENDAR_FOLDER_ID, FolderObject.getFolderString(VIRTUAL_LIST_CALENDAR_FOLDER_ID, locale), SYSTEM_MODULE, true, SYSTEM_TYPE);
                publicParent = true;
                break;
            case VIRTUAL_LIST_CONTACT_FOLDER_ID:
                specialFolder = FolderObject.createVirtualFolderObject(VIRTUAL_LIST_CONTACT_FOLDER_ID, FolderObject.getFolderString(VIRTUAL_LIST_CONTACT_FOLDER_ID, locale), SYSTEM_MODULE, true, SYSTEM_TYPE);
                publicParent = true;
                break;
            case VIRTUAL_LIST_INFOSTORE_FOLDER_ID:
                specialFolder = FolderObject.createVirtualFolderObject(VIRTUAL_LIST_INFOSTORE_FOLDER_ID, FolderObject.getFolderString(VIRTUAL_LIST_INFOSTORE_FOLDER_ID, locale), SYSTEM_MODULE, true, SYSTEM_TYPE);
                publicParent = false;
                break;
            default:
                return false;
        }
        folderList.add(specialFolder);
        final int parentId = publicParent ? SYSTEM_PUBLIC_FOLDER_ID : SYSTEM_INFOSTORE_FOLDER_ID;
        /*
         * Parent
         */
        final FolderObject parent = access.getFolderObject(parentId);
        parent.setFolderName(FolderObject.getFolderString(parentId, locale));
        folderList.add(parent);
        return true;
    }

    private static boolean hasNonVisibleParent(final FolderObject fo, final int userId, final UserPermissionBits permissionBits, final OXFolderAccess access) throws OXException {
        if (fo.getParentFolderID() == SYSTEM_ROOT_FOLDER_ID) {
            return false;
        }
        return !access.getFolderObject(fo.getParentFolderID()).getEffectiveUserPermission(userId, permissionBits).isFolderVisible();
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of a certain
     * type regardless of their parent folder.
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int type, final int[] modules, final Context ctx) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, type, modules, null, ctx, null);
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of certain
     * types regardless of their parent folder.
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int[] types, final int[] modules, final Context ctx) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, types, modules, null, false, ctx, null);
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of a certain
     * type regardless of their parent folder.
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int type, final int[] modules, final Context ctx, final Connection con) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, type, modules, null, ctx, con);
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of a certain
     * type and a certain parent folder.
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int type, final int[] modules, final int parent, final Context ctx) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, type, modules, Integer.valueOf(parent), ctx, null);
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of certain
     * types and a certain parent folder.
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int[] types, final int[] modules, final int parent, final Context ctx) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, types, modules, Integer.valueOf(parent), true, ctx, null);
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances, which represent all user-visible folders of certain
     * types and a certain parent folder.
     */
    private static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int type, final int[] modules, final Integer parent, final Context ctx, final Connection con) throws OXException {
        return getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, new int[] { type }, modules, parent, false, ctx, con);
    }

    /**
     * Collect all user-visible subfolders below a list of parent folders
     *
     * @param folders The folder list to check
     * @param userId The user identifier
     * @param memberInGroups The group identifiers where user is member
     * @param accessibleModules The accessible modules
     * @param types Array of allowed folder types
     * @param modules Array of allowed modules
     * @param gatherVisibleSubfolders Check found subfolders recursively for own subfolders
     * @param ctx The context
     * @param con The connection
     * @return A list of {@link FolderObject} representing the collected folders
     * @throws OXException On error
     */
    private static List<FolderObject> gatherVisibleSubfolders(List<FolderObject> folders, int userId, int[] memberInGroups, int[] accessibleModules, int[] types, int[] modules, boolean gatherVisibleSubfolders, Context ctx, Connection con) throws OXException {
        List<FolderObject> result = new ArrayList<FolderObject>();
        for (FolderObject folder : folders) {
            if (folder.hasSubfolders()) {
                result.addAll(((FolderObjectIterator) getAllVisibleFoldersIteratorOfType(userId, memberInGroups, accessibleModules, types, modules, I(folder.getObjectID()), gatherVisibleSubfolders, ctx, con)).asList());
            }
        }
        return result;
    }

    /**
     * Gather recursively all user-visible subfolders for specified parent folders from database
     *
     * @param parents The parent folders to check
     * @param userId The user identifier
     * @param memberInGroups The group identifier where user is member
     * @param modules The folder modules to check
     * @param types The folder types to check
     * @param ctx The context
     * @param con The conntection
     * @return An array of folder identifier visible for specified user
     * @throws OXException On error
     */
    private static int[] gatherVisibleSubfoldersFromDB(int[] parents, int userId, int[] memberInGroups, int[] modules, int[] types, Context ctx, Connection con) throws OXException {
        if (null == parents || parents.length == 0) {
            return new int[0];
        }
        StringBuilder sb = new StringBuilder("SELECT ot.fuid FROM oxfolder_tree AS ot JOIN oxfolder_permissions AS op ON ot.fuid = op.fuid AND ot.cid = op.cid WHERE ot.cid = ? AND ot.parent IN (");
        for (int i = 0; i < parents.length; i++) {
            sb.append("?,");
        }
        sb.deleteCharAt(sb.length() - 1).append(") AND ");
        if (null != modules && modules.length > 0) {
            sb.append("ot.module IN (");
            for (int i = 0; i < modules.length; i++) {
                sb.append("?,");
            }
            sb.deleteCharAt(sb.length() - 1).append(") AND ");
        }
        if (null != types && types.length > 0) {
            sb.append("ot.type IN (");
            for (int i = 0; i < types.length; i++) {
                sb.append("?,");
            }
            sb.deleteCharAt(sb.length() - 1).append(") ");
        }
        sb.append("AND (op.admin_flag = 1 AND op.permission_id = ?) ");
        sb.append("UNION SELECT ot.fuid FROM oxfolder_tree AS ot JOIN oxfolder_permissions AS op ON ot.fuid = op.fuid AND ot.cid = op.cid WHERE ot.cid = ? AND ot.parent IN (");
        for (int i = 0; i < parents.length; i++) {
            sb.append("?,");
        }
        sb.deleteCharAt(sb.length() - 1).append(") AND (op.fp > 0 AND op.permission_id IN (?,");
        if (null != memberInGroups && memberInGroups.length > 0) {
            for (int i = 0; i < memberInGroups.length; i++) {
                sb.append("?,");
            }
        }
        sb.deleteCharAt(sb.length() - 1).append(")) AND ");
        if (null != modules && modules.length > 0) {
            sb.append("ot.module IN (");
            for (int i = 0; i < modules.length; i++) {
                sb.append("?,");
            }
            sb.deleteCharAt(sb.length() - 1).append(") AND ");
        }
        if (null != types && types.length > 0) {
            sb.append("ot.type IN (");
            for (int i = 0; i < types.length; i++) {
                sb.append("?,");
            }
            sb.deleteCharAt(sb.length() - 1).append(") ");
        }
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(sb.toString());
            int pos = 1;
            stmt.setInt(pos++, ctx.getContextId());
            for (int parent : parents) {
                stmt.setInt(pos++, parent);
            }
            if (null != modules) {
                for (int module : modules) {
                    stmt.setInt(pos++, module);
                }
            }
            if (null != types) {
                for (int type : types) {
                    stmt.setInt(pos++, type);
                }
            }
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, ctx.getContextId());
            for (int parent : parents) {
                stmt.setInt(pos++, parent);
            }
            stmt.setInt(pos++, userId);
            if (null != memberInGroups) {
                for (int group : memberInGroups) {
                    stmt.setInt(pos++, group);
                }
            }
            if (null != modules) {
                for (int module : modules) {
                    stmt.setInt(pos++, module);
                }
            }
            if (null != types) {
                for (int type : types) {
                    stmt.setInt(pos++, type);
                }
            }
            rs = stmt.executeQuery();
            TIntSet folders = new TIntHashSet();
            while (rs.next()) {
                folders.add(rs.getInt(1));
            }
            folders.addAll(gatherVisibleSubfoldersFromDB(folders.toArray(), userId, memberInGroups, modules, types, ctx, con));
            return folders.toArray();
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeResources(rs, stmt, null, true, ctx);
        }
    }

    /**
     * Returns a <code>List</code> of <code>FolderObject</code> instances, which represent all user-visible folders of a certain
     * type and a certain parent folder.
     */
    private static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfType(final int userId, final int[] memberInGroups, final int[] accessibleModules, int[] types, final int[] modules, final Integer parent, boolean gatherVisibleSubfolders, final Context ctx, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null == types || types.length == 0) {
            return FolderObjectIterator.EMPTY_FOLDER_ITERATOR;
        }
        if (null != treeMap) {
            try {
                final List<Condition> conditions = new ArrayList<Condition>(3);
                {
                    final TIntSet set = new TIntHashSet(modules);
                    set.retainAll(accessibleModules);
                    if (set.isEmpty()) {
                        return FolderObjectIterator.EMPTY_FOLDER_ITERATOR;
                    }
                    conditions.add(new ConditionTreeMap.ModulesCondition(set));
                }
                if (types.length == 1) {
                    conditions.add(new ConditionTreeMap.TypeCondition(types[0], userId));
                } else {
                    conditions.add(new ConditionTreeMap.TypesCondition(types));
                }
                if (parent != null) {
                    conditions.add(new ConditionTreeMap.ParentCondition(parent.intValue()));
                }
                final TIntSet set = treeMap.getVisibleForUser(userId, memberInGroups, accessibleModules, conditions);
                final List<FolderObject> list = ConditionTreeMap.asList(set, ctx, con);
                List<FolderObject> result = new ArrayList<FolderObject>(list);
                if (gatherVisibleSubfolders) {
                    result.addAll(gatherVisibleSubfolders(list, userId, memberInGroups, accessibleModules, types, modules, gatherVisibleSubfolders, ctx, con));
                }
                return new FolderObjectIterator(result, false);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final StringBuilder condBuilder = new StringBuilder(32);
        if (null != modules && modules.length > 0) {
            if (1 == modules.length) {
                condBuilder.append("AND (ot.module = ").append(modules[0]).append(')');
            } else {
                condBuilder.append("AND (ot.module IN (");
                condBuilder.append(modules[0]);
                for (int i = 1; i < modules.length; i++) {
                    condBuilder.append(", ").append(modules[i]);
                }
                condBuilder.append("))");
            }
        }
        if (types.length == 1) {
            if (types[0] == SHARED) {
                condBuilder.append(" AND (ot.type = ").append(PRIVATE);
                condBuilder.append(" AND ot.created_from != ").append(userId).append(')');
            } else {
                condBuilder.append(" AND (ot.type = ").append(types[0]).append(')');
            }
        } else {
            condBuilder.append(" AND ot.type IN (");
            for (int type : types) {
                condBuilder.append(type).append(",");
            }
            condBuilder.deleteCharAt(condBuilder.length() - 1);
            condBuilder.append(")");
        }
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            if (false == gatherVisibleSubfolders) {
                if (parent != null) {
                    condBuilder.append(" AND (ot.parent = ").append(parent.intValue()).append(')');
                }
            } else {
                if (parent != null) {
                    int[] visibleSubfolderIds = gatherVisibleSubfoldersFromDB(new int[] {parent.intValue()}, userId, memberInGroups, modules, types, ctx, readCon);
                    condBuilder.append(" AND ot.parent IN (").append(parent.intValue()).append(",");
                    for (int id : visibleSubfolderIds) {
                        condBuilder.append(id).append(",");
                    }
                    condBuilder.deleteCharAt(condBuilder.length() - 1).append(")");
                }
            }
            stmt = readCon.prepareStatement(getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0)));
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeCon);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        }
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances of a certain module
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfModule(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int module, final Context ctx) throws OXException {
        return getAllVisibleFoldersIteratorOfModule(userId, memberInGroups, accessibleModules, module, ctx, null);
    }

    /**
     * Gets a value indicating whether a specific user has visible folders in a certain module.
     *
     * @param userId The identifier of the user to check
     * @param memberInGroups An array supplying the identifier of those groups the user is member of
     * @param accessibleModules The identifiers of those modules accessible by the user
     * @param module The module identifier to check
     * @param ctx The context
     * @param ignoreSystem <code>true</code> to ignore folders of type {@link FolderObject#SYSTEM_TYPE} or with an identifier smaller
     *        than {@link FolderObject#MIN_FOLDER_ID}, <code>false</code>, otherwise
     * @param con The database connection to use, or <code>null</code> to acquire one dynamically from the pool
     * @return <code>true</code> if there's at least one visible folder in the module, <code>false</code>, otherwise
     * @throws OXException
     */
    public static boolean hasVisibleFoldersOfModule(int userId, int[] memberInGroups, int[] accessibleModules, int module, Context ctx, boolean ignoreSystem, Connection con) throws OXException {
        /*
         * prepare query
         */
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("AND (ot.module=").append(module);
        if (ignoreSystem) {
            stringBuilder.append(" AND ot.type<>").append(FolderObject.SYSTEM_TYPE).append(" AND ot.fuid>=").append(FolderObject.MIN_FOLDER_ID);
        }
        stringBuilder.append(')');
        String additionalCondition = stringBuilder.toString();
        String sql = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx),
            StringCollection.getSqlInString(accessibleModules), additionalCondition, new QueryAppendix(null, 1));
        Connection readCon;
        boolean closeReadCon = false;
        if (con == null) {
            readCon = DBPool.pickup(ctx);
            closeReadCon = true;
        } else {
            readCon = con;
        }
        int contextId = ctx.getContextId();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = readCon.prepareStatement(sql);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            rs = executeQuery(stmt);
            return rs.next();
        } catch (SQLException e) {
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } finally {
            closeResources(rs, stmt, closeReadCon ? readCon : null, true, ctx);
        }
    }

    /**
     * Returns a <code>SearchIterator</code> of <code>FolderObject</code> instances of a certain module
     */
    public static SearchIterator<FolderObject> getAllVisibleFoldersIteratorOfModule(final int userId, final int[] memberInGroups, final int[] accessibleModules, final int module, final Context ctx, final Connection con) throws OXException {
        final ConditionTreeMap treeMap = ConditionTreeMapManagement.getInstance().optMapFor(ctx.getContextId(), isNullOrAutocommit(con));
        if (null != treeMap) {
            try {
                final TIntSet set = treeMap.getVisibleModuleForUser(Optional.ofNullable(con), userId, memberInGroups, accessibleModules, module);
                final List<FolderObject> list = ConditionTreeMap.asList(set, ctx, con);
                return new FolderObjectIterator(list, false);
            } catch (OXException e) {
                handleConditionTreeMapException(e, ctx);
            }
        }
        /*
         * Query database
         */
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), new StringBuilder("AND (ot.module = ").append(module).append(')').toString(), getSubfolderOrderBy(STR_OT, 0));
        final Connection readCon;
        boolean closeReadCon = false;
        final int contextId = ctx.getContextId();
        {
            if (con == null) {
                readCon = DBPool.pickup(ctx);
                closeReadCon = true;
            } else {
                readCon = con;
            }
        }
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (SQLException e) {
            closeResources(rs, stmt, closeReadCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeReadCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }

        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeReadCon);
        } catch (OXException e) {
            closeResources(rs, stmt, closeReadCon ? readCon : null, true, ctx);
            throw e;
        }
    }

    /**
     * Gets formerly user-visible folders which were deleted since specified time stamp.
     *
     * @param since The time stamp
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param accessibleModules The user's accessible modules
     * @param ctx The context
     * @return The formerly user-visible folders which were deleted since specified time stamp
     * @throws OXException If a folder error occurs
     * @throws SearchIteratorException If a search iterator error occurs
     */
    public static SearchIterator<FolderObject> getDeletedFoldersSince(final Date since, final int userId, final int[] memberInGroups, final int[] accessibleModules, final Context ctx) throws OXException {
        return getDeletedFoldersSince(since, userId, memberInGroups, accessibleModules, ctx, null);
    }

    /**
     * Gets formerly user-visible folders which were deleted since specified time stamp.
     *
     * @param since The time stamp
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param accessibleModules The user's accessible modules
     * @param ctx The context
     * @param con The connection to use
     * @return The formerly user-visible folders which were deleted since specified time stamp
     * @throws OXException If a folder error occurs
     * @throws SearchIteratorException If a search iterator error occurs
     */
    public static SearchIterator<FolderObject> getDeletedFoldersSince(final Date since, final int userId, final int[] memberInGroups, final int[] accessibleModules, final Context ctx, final Connection con) throws OXException {
        final String fields = FolderObjectIterator.getFieldsForSQL(STR_OT);
        final String condition = since == null ? null : new StringBuilder(" AND (ot.changing_date > ").append(since.getTime()).append(')').toString();
        final String sqlSelectStr = getSQLUserVisibleFolders("del_oxfolder_tree", "del_oxfolder_permissions", fields, permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condition, new QueryAppendix(new OrderBy(new ColumnAndDirection("ot", "fuid", Order.ASC)), 0), false);
        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            //stmt.setInt(pos++, contextId);
            //stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        try {
            FolderObjectIterator result = new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeCon);
            result.releaseCache();
            return result;
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        }
    }

    /**
     * Gets user-visible folders which were modified since specified time stamp.
     *
     * @param since The time stamp
     * @param userId The user identifier
     * @param memberInGroups The user's group identifiers
     * @param accessibleModules The user's accessible modules
     * @param userFoldersOnly <code>true</code> to consider only folder which were created by given user; otherwise <code>false</code>
     * @param ctx The context
     * @return The user-visible folders which were modified since specified time stamp
     * @throws OXException If a folder error occurs
     * @throws SearchIteratorException If a search iterator error occurs
     */
    public static SearchIterator<FolderObject> getModifiedFoldersSince(final Date since, final int userId, final int[] memberInGroups, final int[] accessibleModules, final boolean userFoldersOnly, final Context ctx) throws OXException {
        final StringBuilder condBuilder = new StringBuilder(32).append("AND (changing_date > ").append(since.getTime()).append(") AND (module IN ").append(SQL_IN_STR_STANDARD_MODULES).append(')');
        if (userFoldersOnly) {
            condBuilder.append(" AND (ot.created_from = ").append(userId).append(") ");
        }
        final String sqlSelectStr = getSQLUserVisibleFolders(FolderObjectIterator.getFieldsForSQL(STR_OT), permissionIds(userId, memberInGroups, ctx), StringCollection.getSqlInString(accessibleModules), condBuilder.toString(), getSubfolderOrderBy(STR_OT, 0));
        Connection readCon = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final int contextId = ctx.getContextId();
        try {
            readCon = DBPool.pickup(ctx);
            stmt = readCon.prepareStatement(sqlSelectStr);
            int pos = 1;
            // stmt.setInt(pos++, contextId);
            // stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, userId);
            stmt.setInt(pos++, contextId);
            stmt.setInt(pos++, contextId);

            rs = executeQuery(stmt);
        } catch (SQLException e) {
            closeResources(rs, stmt, readCon, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, readCon, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(contextId));
        }
        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, true);
        } catch (OXException e) {
            closeResources(rs, stmt, readCon, true, ctx);
            throw e;
        }
    }

    private static final boolean useLastModifiedIndex = false;

    /**
     * Gets <b>all</b> modified folders since given time stamp.
     * <p>
     * Quote from <a href= "http://www.open-xchange.com/wiki/index.php?title=HTTP_API#Updates">HTTP API Updates</a>: <code>
     * ...
     * When requesting updates to a previously retrieved set of objects,
     * the client sends the last timestamp which belongs to that set of objects.
     * The response contains all updates with timestamps greater than the one
     * specified by the client. The field timestamp of the response contains the
     * new maximum timestamp value.
     * ...
     * </code>
     *
     * @param since The time stamp
     * @param ctx The context
     * @return <b>All</b> modified folders since given time stamp
     * @throws OXException If a folder error occurs
     */
    public static SearchIterator<FolderObject> getAllModifiedFoldersSince(final Date since, final Context ctx) throws OXException {
        return getAllModifiedFoldersSince(since, ctx, null);
    }

    /**
     * Gets <b>all</b> modified folders since given time stamp.
     * <p>
     * Quote from <a href= "http://www.open-xchange.com/wiki/index.php?title=HTTP_API#Updates">HTTP API Updates</a>: <code>
     * ...
     * When requesting updates to a previously retrieved set of objects,
     * the client sends the last timestamp which belongs to that set of objects.
     * The response contains all updates with timestamps greater than the one
     * specified by the client. The field timestamp of the response contains the
     * new maximum timestamp value.
     * ...
     * </code>
     *
     * @param since The time stamp
     * @param ctx The context
     * @param con The connection to use
     * @return <b>All</b> modified folders since given time stamp
     * @throws OXException If a folder error occurs
     */
    public static SearchIterator<FolderObject> getAllModifiedFoldersSince(final Date since, final Context ctx, final Connection con) throws OXException {
        return getModifiedFoldersSince(since, null, ctx, con);
    }

    /**
     * Gets <b>all</b> modified folders from specific module(s) since given time stamp.
     * <p>
     * Quote from <a href= "http://www.open-xchange.com/wiki/index.php?title=HTTP_API#Updates">HTTP API Updates</a>: <code>
     * ...
     * When requesting updates to a previously retrieved set of objects,
     * the client sends the last timestamp which belongs to that set of objects.
     * The response contains all updates with timestamps greater than the one
     * specified by the client. The field timestamp of the response contains the
     * new maximum timestamp value.
     * ...
     * </code>
     *
     * @param since The time stamp
     * @param modules The modules to include, or <code>null</code> to include all modules
     * @param ctx The context
     * @param con The connection to use
     * @return <b>All</b> modified folders since given time stamp
     * @throws OXException If a folder error occurs
     */
    public static SearchIterator<FolderObject> getModifiedFoldersSince(final Date since, final int[] modules, final Context ctx, final Connection con) throws OXException {
        final StringBuilder sb = new StringBuilder(256).append(STR_SELECT);
        sb.append(FolderObjectIterator.getFieldsForSQL(STR_OT)).append(" FROM oxfolder_tree AS ot");
        final long time = since.getTime();
        /*
         * Check time stamp
         */
        if (time > 0) {
            if (useLastModifiedIndex) {
                sb.append(" FORCE INDEX (lastModifiedIndex)");
            }
            sb.append(" WHERE (cid = ").append(ctx.getContextId()).append(')');
            sb.append(" AND (changing_date > ").append(time).append(')');
        } else {
            sb.append(" WHERE (cid = ").append(ctx.getContextId()).append(')');
        }
        if (null == modules) {
            sb.append(" AND (module IN ").append(SQL_IN_STR_STANDARD_MODULES_ALL).append(") ");
        } else if (0 == modules.length) {
            return SearchIteratorAdapter.emptyIterator();
        } else if (1 == modules.length) {
            sb.append(" AND (module=").append(modules[0]).append(") ");
        } else {
            sb.append(" AND (module IN (").append(modules[0]);
            for (int i = 1; i < modules.length; i++) {
                sb.append(',').append(modules[i]);
            }
            sb.append(")) ");
        }
        sb.append(" ORDER by ot.fuid").toString();

        Connection readCon = con;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (null == readCon) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(sb.toString());
            rs = executeQuery(stmt);
        } catch (SQLException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.SQL_ERROR.create(e, e.getMessage());
        } catch (RuntimeException t) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw OXFolderExceptionCode.RUNTIME_ERROR.create(t, Integer.valueOf(ctx.getContextId()));
        }
        try {
            return new FolderObjectIterator(rs, stmt, false, ctx, readCon, closeCon);
        } catch (OXException e) {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            throw e;
        }
    }

    private static final class SQLStuff {

        final ResultSet rs;

        final Statement stmt;

        final Connection readCon;

        final boolean closeCon;

        SQLStuff(final Statement stmt, final ResultSet rs, final Connection readCon, final boolean closeCon) {
            super();
            this.stmt = stmt;
            this.rs = rs;
            this.closeCon = closeCon;
            this.readCon = readCon;
        }
    }

    private static ResultSet executeQuery(final PreparedStatement stmt) throws SQLException {
        try {
            return stmt.executeQuery();
        } catch (SQLException e) {
            if ("MySQLSyntaxErrorException".equals(e.getClass().getSimpleName())) {
                final String sql = stmt.toString();
                LOG.error("\nFollowing SQL query contains syntax errors:\n{}", sql.substring(sql.indexOf(": ") + 2));
            }
            throw e;
        }
    }

    private static String permissionIds(final int userId, final int[] memberInGroups, final Context ctx) throws OXException {
        int[] groups = memberInGroups;
        if (null == groups || 0 == groups.length) {
            groups = UserStorage.getInstance().getUser(userId, ctx).getGroups();
            if (null == groups || 0 == groups.length) {
                groups = new int[] { GroupStorage.GROUP_ZERO_IDENTIFIER };
            }
        }
        return StringCollection.getSqlInString(userId, groups);
    }

    private static boolean isNullOrAutocommit(Connection con) {
        try {
            return con == null || con.getAutoCommit();
        } catch (SQLException e) {
            return false;
        }
    }

    private static void handleConditionTreeMapException(OXException e, final Context ctx) {
        LOG.debug("", e);
        ConditionTreeMapManagement.dropFor(ctx.getContextId());
        final ThreadPoolService threadPool = ThreadPools.getThreadPool();
        final Runnable task = new Runnable() {

            @Override
            public void run() {
                try {
                    ConditionTreeMapManagement.getInstance().getMapFor(ctx.getContextId());
                } catch (Exception e) {
                    // Ignore
                }
            }
        };
        if (null == threadPool) {
            task.run();
        } else {
            threadPool.submit(ThreadPools.trackableTask(task));
        }
        // Retry from storage...
    }

    private static class QueryAppendix {

        final OrderBy orderBy;
        final int limit;

        QueryAppendix(OrderBy orderBy, int limit) {
            super();
            this.orderBy = orderBy;
            this.limit = limit;
        }

        @Override
        public String toString() {
            StringBuilder sb = null;
            if (null != orderBy) {
                sb = new StringBuilder(32).append("ORDER BY ");
                List<ColumnAndDirection> columns = orderBy.columns;
                boolean first = true;
                for (ColumnAndDirection columnAndDirection : columns) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    sb.append(columnAndDirection.columnName);
                    Order order = columnAndDirection.order;
                    if (order == Order.DESC) {
                        sb.append(" ").append(order.id);
                    }
                }
            }
            if (limit > 0) {
                if (null == sb) {
                    sb = new StringBuilder(16);
                } else {
                    sb.append(' ');
                }
                sb.append("LIMIT ").append(limit);
            }
            return null == sb ? null : sb.toString();
        }
    }

    private static class OrderBy {

        final List<ColumnAndDirection> columns;

        OrderBy(ColumnAndDirection column, ColumnAndDirection... others) {
            super();
            if (null == others || others.length == 0) {
                columns = ImmutableList.of(column);
            } else {
                ImmutableList.Builder<ColumnAndDirection> list = ImmutableList.builderWithExpectedSize(others.length + 1);
                list.add(column);
                for (ColumnAndDirection c : others) {
                    if (null != c) {
                        list.add(c);
                    }
                }
                columns = list.build();
            }
        }
    }

    private static class ColumnAndDirection {

        final String tableAlias;
        final String columnName;
        final Order order;

        ColumnAndDirection(String tableAlias, String columnName, Order order) {
            super();
            this.tableAlias = tableAlias;
            this.columnName = columnName;
            this.order = order;
        }
    }

    private static enum Order {
        ASC("ASC"),
        DESC("DESC"),
        ;

        final String id;

        private Order(String id) {
            this.id = id;
        }
    }


}
