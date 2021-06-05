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

package com.openexchange.groupware.container;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.modules.Module;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.java.Strings;
import com.openexchange.java.util.Pair;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;


/**
 * {@link EffectiveObjectPermissions} provides static helper functions to load
 * {@link EffectiveObjectPermission} instances.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public class EffectiveObjectPermissions {

    private EffectiveObjectPermissions() {
        super();
    }

    /**
     * Finds the permission with the highest bits for the given user in the given
     * list of permissions.
     *
     * @param user The user
     * @param permissions The list of permissions
     * @return The highest permission within the list, or <code>null</code> if no permission
     * for the user was contained.
     */
    public static ObjectPermission find(final User user, Collection<ObjectPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }

        int userId = user.getId();
        Set<Integer> groups = new HashSet<Integer>();
        int[] tmp = user.getGroups();
        if (tmp != null) {
            for (int group : tmp) {
                groups.add(I(group));
            }
        }

        ObjectPermission found = null;
        for (ObjectPermission permission : permissions) {
            boolean checkAndSet = false;
            int entity = permission.getEntity();
            if (permission.isGroup()) {
                if (groups.contains(I(entity))) {
                    checkAndSet = true;
                }
            } else if (entity == userId) {
                checkAndSet = true;
            }

            if (checkAndSet) {
                if (found == null || found.getPermissions() < permission.getPermissions()) {
                    found = permission;
                }
            }
        }

        return found;
    }

    /**
     * Converts an {@link ObjectPermission} into an {@link EffectiveObjectPermission}.
     *
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderId The folder
     * @param id The object id
     * @param permission The permission
     * @param permissionBits The users permission bits
     * @return A new {@link EffectiveObjectPermission} instance
     */
    public static EffectiveObjectPermission convert(int module, int folderId, int id, ObjectPermission permission, UserPermissionBits permissionBits) {
        return new EffectiveObjectPermission(module, folderId, id, permission, permissionBits);
    }

    /**
     * Loads the {@link EffectiveObjectPermission} for a given user and item. If the
     * user has multiple permissions (e.g. due to being member of several groups), the
     * one with the highest bits is returned. The permission is fetched from the database.
     * If an active database connection is available, you should use the method with the
     * connection parameter.
     *
     * @param session The users session
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderId The folder id
     * @param id The item id
     * @return The users permission for the given item or <code>null</code> if no permission is defined.
     * @throws OXException
     */
    public static EffectiveObjectPermission load(ServerSession session, int module, int folderId, int id) throws OXException {
        return load(session.getContext(), session.getUser(), session.getUserPermissionBits(), module, folderId, id);
    }

    /**
     * Loads the {@link EffectiveObjectPermission} for a given user and item. If the
     * user has multiple permissions (e.g. due to being member of several groups), the
     * one with the highest bits is returned. The permission is fetched from the database.
     *
     * @param session The users session
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderId The folder id
     * @param id The item id
     * @param con The active database connection
     * @return The users permission for the given item or <code>null</code> if no permission is defined.
     * @throws OXException
     */
    public static EffectiveObjectPermission load(ServerSession session, int module, int folderId, int id, Connection con) throws OXException {
        return load(session.getContext(), session.getUser(), session.getUserPermissionBits(), module, folderId, id, con);
    }

    /**
     * Loads the {@link EffectiveObjectPermission} for a given user and item. If the
     * user has multiple permissions (e.g. due to being member of several groups), the
     * one with the highest bits is returned. The permission is fetched from the database.
     * If an active database connection is available, you should use the method with the
     * connection parameter.
     *
     * @param ctx The context
     * @param user The user
     * @param permissionBits The users permission bits
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderId The folder id
     * @param id The item id
     * @return The users permission for the given item or <code>null</code> if no permission is defined.
     * @throws OXException
     */
    public static EffectiveObjectPermission load(final Context ctx, final User user, final UserPermissionBits permissionBits, final int module, final int folderId, final int id) throws OXException {
        DatabaseService service = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
        if (null == service) {
            throw ServiceExceptionCode.absentService(DatabaseService.class);
        }

        Connection con = null;
        try {
            con = service.getReadOnly(ctx);
            return load(ctx, user, permissionBits, module, folderId, id, con);
        } finally {
            if (con != null) {
                service.backReadOnly(ctx, con);
            }
        }
    }

    /**
     * Loads the {@link EffectiveObjectPermission} for a given user and item. If the
     * user has multiple permissions (e.g. due to being member of several groups), the
     * one with the highest bits is returned. The permission is fetched from the database.
     *
     * @param ctx The context
     * @param user The user
     * @param permissionBits The users permission bits
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderId The folder id
     * @param id The item id
     * @param con The active database connection
     * @return The users permission for the given item or <code>null</code> if no permission is defined.
     * @throws OXException
     */
    public static EffectiveObjectPermission load(Context ctx, User user, UserPermissionBits permissionBits, int module, int folderId, int id, Connection con) throws OXException {
        if (null == con) {
            return load(ctx, user, permissionBits, module, folderId, id);
        }

        StringBuilder sb = new StringBuilder(128).append("SELECT bits, permission_id, group_flag FROM object_permission WHERE cid = ").append(ctx.getContextId()).append(" AND module = ").append(module);
        sb.append(" AND folder_id = ").append(folderId).append(" AND object_id = ").append(id);
        appendEntityConstraint(sb, user.getId(), user.getGroups());

        Statement stmt = null;
        ResultSet rs = null;
        ObjectPermission permission = null;
        try {
            stmt = con.createStatement();
            rs = stmt.executeQuery(sb.toString());
            while (rs.next()) {
                int bits = rs.getInt(1);
                if (permission == null || bits > permission.getPermissions()) {
                    permission = new ObjectPermission(rs.getInt(2), rs.getBoolean(3), bits);
                }
            }
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        if (permission == null) {
            return null;
        }

        return new EffectiveObjectPermission(module, folderId, id, permission, permissionBits);
    }

    /**
     * Loads all {@link EffectiveObjectPermissions} for a user and a list of &lt;folder-id&gt;-&lt;object-id&gt;-pairs
     * in a given module.
     *
     * @param ctx The context
     * @param user The user
     * @param permissionBits The users permission bits
     * @param module The module (see {@link Module#getFolderConstant()}).
     * @param folderAndObjectIds The folder and object ids to load permissions for. For every desired
     * permission, a {@link Pair} has to be defined, with the folder id as first parameter and object
     * id as second parameter.
     * @param con The active database connection
     * @return A mapping &lt;folder-id&gt; =&gt; &lt;object-id&gt; =&gt; &lt;permission&gt;
     * @throws OXException
     */
    public static Map<Integer, Map<Integer, EffectiveObjectPermission>> load(Context ctx, User user, UserPermissionBits permissionBits, int module, List<Pair<Integer, Integer>> folderAndObjectIds, Connection con) throws OXException {
        if (folderAndObjectIds.isEmpty()) {
            return new HashMap<Integer, Map<Integer, EffectiveObjectPermission>>();
        }

        Map<Integer, List<Integer>> objectIdsByFolder = new HashMap<Integer, List<Integer>>(folderAndObjectIds.size());
        for (Pair<Integer, Integer> folderAndObjectId : folderAndObjectIds) {
            Integer folderId = folderAndObjectId.getFirst();
            Integer objectId = folderAndObjectId.getSecond();
            List<Integer> objectIds = objectIdsByFolder.get(folderId);
            if (objectIds == null) {
                objectIds = new ArrayList<Integer>();
                objectIdsByFolder.put(folderId, objectIds);
            }

            objectIds.add(objectId);
        }

        StringBuilder sb = new StringBuilder(128).append("SELECT folder_id, object_id, bits, permission_id, group_flag FROM object_permission WHERE cid = ").append(ctx.getContextId()).append(" AND module = ").append(module);
        sb.append(" AND (");
        for (Entry<Integer, List<Integer>> entry : objectIdsByFolder.entrySet()) {
            sb.append("(folder_id = ").append(entry.getKey()).append(" AND object_id IN (");
            Strings.join(entry.getValue(), ", ", sb);
            sb.append("))");
            sb.append(" OR ");
        }
        sb.setLength(sb.length() - " OR ".length());
        sb.append(")");
        appendEntityConstraint(sb, user.getId(), user.getGroups());

        Map<Integer, Map<Integer, EffectiveObjectPermission>> gatheredPermissions = new HashMap<Integer, Map<Integer, EffectiveObjectPermission>>(objectIdsByFolder.size());
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.createStatement();
            rs = stmt.executeQuery(sb.toString());
            while (rs.next()) {
                int folderId = rs.getInt(1);
                int objectId = rs.getInt(2);
                int bits = rs.getInt(3);
                Map<Integer, EffectiveObjectPermission> permissionsInFolder = gatheredPermissions.get(I(folderId));
                if (permissionsInFolder == null) {
                    permissionsInFolder = new HashMap<Integer, EffectiveObjectPermission>();
                    gatheredPermissions.put(I(folderId), permissionsInFolder);
                }

                EffectiveObjectPermission permission = permissionsInFolder.get(I(objectId));
                if (permission == null || bits > permission.getPermission().getPermissions()) {
                    permissionsInFolder.put(I(objectId), new EffectiveObjectPermission(module, folderId, objectId, new ObjectPermission(rs.getInt(4), rs.getBoolean(5), bits), permissionBits));
                }
            }
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e.getMessage());
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        return gatheredPermissions;
    }

    /**
     * Flattens a permission mapping like the one returned in
     * {@link EffectiveObjectPermissions#load(Context, User, UserPermissionBits, int, List, Connection)}.
     *
     * @param permissions The input map
     * @return A plain list of alle permissions contained in the input map
     */
    public static List<EffectiveObjectPermission> flatten(Map<Integer, Map<Integer, EffectiveObjectPermission>> permissions) {
        List<EffectiveObjectPermission> permissionList = new ArrayList<EffectiveObjectPermission>();
        for (Map<Integer, EffectiveObjectPermission> value : permissions.values()) {
            for (EffectiveObjectPermission inner : value.values()) {
                permissionList.add(inner);
            }
        }

        return permissionList;
    }

    private static void appendEntityConstraint(StringBuilder sb, int userId, int[] groups) {
        if (groups != null && groups.length > 0) {
            sb.append(" AND ((").append("group_flag <> 1 AND permission_id = ").append(userId).append(") OR (group_flag = 1 AND permission_id IN (");
            Strings.join(groups, ", ", sb);
            sb.append(")))");
        } else {
            sb.append(" AND (group_flag <> 1 AND permission_id = ").append(userId).append(")");
        }
    }

}
