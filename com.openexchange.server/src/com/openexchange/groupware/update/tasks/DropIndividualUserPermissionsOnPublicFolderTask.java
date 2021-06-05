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

package com.openexchange.groupware.update.tasks;

import static com.openexchange.database.Databases.closeSQLStuff;
import static com.openexchange.database.Databases.startTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.impl.RdbContextStorage;
import com.openexchange.groupware.update.Attributes;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.ProgressState;
import com.openexchange.groupware.update.TaskAttributes;
import com.openexchange.groupware.update.UpdateConcurrency;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskAdapter;
import com.openexchange.groupware.update.WorkingLevel;
import com.openexchange.server.impl.OCLPermission;

/**
 * Restores the initial permissions on the public root folder.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public class DropIndividualUserPermissionsOnPublicFolderTask extends UpdateTaskAdapter {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DropIndividualUserPermissionsOnPublicFolderTask.class);

    public DropIndividualUserPermissionsOnPublicFolderTask() {
        super();
    }

    @Override
    public String[] getDependencies() {
        return new String[] { "com.openexchange.groupware.update.tasks.GlobalAddressBookPermissionsResolverTask" };
    }

    @Override
    public TaskAttributes getAttributes() {
        return new Attributes(UpdateConcurrency.BACKGROUND, WorkingLevel.SCHEMA);
    }

    @Override
    public void perform(final PerformParameters params) throws OXException {
        final ProgressState progress = params.getProgressState();
        Connection con = params.getConnection();
        int rollback = 0;
        try {
            startTransaction(con);
            rollback = 1;

            Exception re = null;
            final int[] contextIds = params.getContextsInSameSchema();
            progress.setTotal(contextIds.length);
            int pos = 0;
            for (final int contextId : contextIds) {
                progress.setState(pos++);
                try {
                    final List<OCLPermission> permissions = getPermissions(con, contextId);
                    final int contextAdminId = RdbContextStorage.getAdmin(con, contextId);
                    correctGroupZero(con, contextId, permissions);
                    correctContextAdmin(con, contextId, permissions, contextAdminId);
                    dropAllOtherPermissions(con, contextId, isContained(permissions, contextAdminId), contextAdminId);
                } catch (SQLException e) {
                    LOG.error("", e);
                    if (null == re) {
                        re = e;
                    }
                }
            }

            con.commit();
            rollback = 2;
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (rollback > 0) {
                if (rollback == 1) {
                    Databases.rollback(con);
                }
                Databases.autocommit(con);
            }
        }
    }

    private void correctGroupZero(final Connection con, final int ctxId, final List<OCLPermission> permissions) throws SQLException {
        if (isGroupZeroCorrect(permissions)) {
            return;
        }
        correctPermission(con, ctxId, permissions, OCLPermission.ALL_GROUPS_AND_USERS, false, true);
    }

    private boolean isGroupZeroCorrect(final List<OCLPermission> permissions) {
        for (final OCLPermission permission : permissions) {
            if (OCLPermission.ALL_GROUPS_AND_USERS == permission.getEntity()) {
                return !permission.isFolderAdmin() && OCLPermission.CREATE_SUB_FOLDERS == permission.getFolderPermission() && OCLPermission.NO_PERMISSIONS == permission.getReadPermission() && OCLPermission.NO_PERMISSIONS == permission.getWritePermission() && OCLPermission.NO_PERMISSIONS == permission.getDeletePermission();
            }
        }
        return false;
    }

    private void correctContextAdmin(final Connection con, final int ctxId, final List<OCLPermission> permissions, final int contextAdminId) throws SQLException {
        if (!isContextAdminWrong(permissions, contextAdminId)) {
            return;
        }
        correctPermission(con, ctxId, permissions, contextAdminId, true, false);
    }

    private void correctPermission(final Connection con, final int ctxId, final List<OCLPermission> permissions, final int permId, final boolean admin, final boolean group) throws SQLException {
        PreparedStatement stmt = null;
        try {
            final String sql;
            final boolean update = isContained(permissions, permId);
            if (update) {
                sql = "UPDATE oxfolder_permissions SET fp=?,orp=?,owp=?,odp=?,admin_flag=?,system=? WHERE cid=? AND fuid=? AND permission_id=?";
            } else {
                sql = "INSERT INTO oxfolder_permissions (fp,orp,owp,odp,admin_flag,system,cid,fuid,permission_id,group_flag) VALUE (?,?,?,?,?,?,?,?,?,?)";
            }
            stmt = con.prepareStatement(sql);
            int pos = 1;
            stmt.setInt(pos++, OCLPermission.CREATE_SUB_FOLDERS);
            stmt.setInt(pos++, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(pos++, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(pos++, OCLPermission.NO_PERMISSIONS);
            stmt.setBoolean(pos++, admin); // admin_flag
            stmt.setBoolean(pos++, false);
            stmt.setInt(pos++, ctxId);
            stmt.setInt(pos++, FolderObject.SYSTEM_PUBLIC_FOLDER_ID);
            stmt.setInt(pos++, permId);
            if (!update) {
                stmt.setBoolean(pos++, group);
            }
            stmt.executeUpdate();
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private boolean isContained(final List<OCLPermission> permissions, final int adminId) {
        for (final OCLPermission permission : permissions) {
            if (adminId == permission.getEntity()) {
                return true;
            }
        }
        return false;
    }

    private boolean isContextAdminWrong(final List<OCLPermission> permissions, final int adminId) {
        for (final OCLPermission permission : permissions) {
            if (adminId == permission.getEntity()) {
                return !permission.isFolderAdmin();
            }
            if (adminId != permission.getEntity() && !permission.isGroupPermission() && permission.isFolderAdmin()) {
                return true;
            }
        }
        return false;
    }

    private void dropAllOtherPermissions(final Connection con, final int ctxId, final boolean addedAdmin, final int adminId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String sql = "DELETE FROM oxfolder_permissions WHERE cid=? AND fuid=? AND permission_id!=?";
            if (addedAdmin) {
                sql += " AND permission_id!=?";
            }
            stmt = con.prepareStatement(sql);
            int pos = 1;
            stmt.setInt(pos++, ctxId);
            stmt.setInt(pos++, FolderObject.SYSTEM_PUBLIC_FOLDER_ID);
            stmt.setInt(pos++, OCLPermission.ALL_GROUPS_AND_USERS);
            if (addedAdmin) {
                stmt.setInt(pos++, adminId);
            }
            stmt.executeUpdate();
        } finally {
            closeSQLStuff(stmt);
        }
    }

    private List<OCLPermission> getPermissions(final Connection con, final int ctxId) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet result = null;
        final List<OCLPermission> retval = new ArrayList<OCLPermission>();
        try {
            stmt = con.prepareStatement("SELECT permission_id,fp,orp,owp,odp,admin_flag,group_flag,system,type,sharedParentFolder FROM oxfolder_permissions WHERE cid=? AND fuid=?");
            stmt.setInt(1, ctxId);
            stmt.setInt(2, FolderObject.SYSTEM_PUBLIC_FOLDER_ID);
            result = stmt.executeQuery();
            while (result.next()) {
                final OCLPermission p = new OCLPermission();
                p.setEntity(result.getInt(1));
                p.setAllPermission(result.getInt(2), result.getInt(3), result.getInt(4), result.getInt(5));
                p.setFolderAdmin(result.getInt(6) > 0 ? true : false);
                p.setGroupPermission(result.getInt(7) > 0 ? true : false);
                p.setSystem(result.getInt(8));
                p.setType(FolderPermissionType.getType(result.getInt(9)));
                int legator = result.getInt(10);
                p.setPermissionLegator(legator == 0 ? null : String.valueOf(legator));
                retval.add(p);
            }
        } finally {
            closeSQLStuff(result, stmt);
        }
        return retval;
    }
}
