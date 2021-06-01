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

import static com.openexchange.database.Databases.autocommit;
import static com.openexchange.database.Databases.rollback;
import static com.openexchange.groupware.update.WorkingLevel.SCHEMA;
import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.update.Attributes;
import com.openexchange.groupware.update.PerformParameters;
import com.openexchange.groupware.update.ProgressState;
import com.openexchange.groupware.update.TaskAttributes;
import com.openexchange.groupware.update.UpdateConcurrency;
import com.openexchange.groupware.update.UpdateExceptionCodes;
import com.openexchange.groupware.update.UpdateTaskAdapter;
import com.openexchange.server.impl.OCLPermission;

/**
 * {@link FolderPermissionAddGuestGroup}
 *
 * Adds permissions to system- and root-folders for the virtual guest group.
 *
 * @author <a href="mailto:tobias.Friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public final class FolderPermissionAddGuestGroup extends UpdateTaskAdapter {

    private static int[] systemFolderIDs = new int[] {
        FolderObject.SYSTEM_PRIVATE_FOLDER_ID, FolderObject.SYSTEM_SHARED_FOLDER_ID,
        FolderObject.SYSTEM_INFOSTORE_FOLDER_ID, FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID,
        FolderObject.SYSTEM_PUBLIC_FOLDER_ID, FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID
    };

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(FolderPermissionAddGuestGroup.class);

    /**
     * Default constructor.
     */
    public FolderPermissionAddGuestGroup() {
        super();
    }

    @Override
    public String[] getDependencies() {
        return new String[0];
    }

    @Override
    public TaskAttributes getAttributes() {
        return new Attributes(UpdateConcurrency.BLOCKING, SCHEMA);
    }

    @Override
    public void perform(PerformParameters params) throws OXException {
        log.info("Performing update task {}", FolderPermissionAddGuestGroup.class.getSimpleName());
        ProgressState progress = params.getProgressState();
        Connection connection = params.getConnection();
        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            int[] contextIDs = params.getContextsInSameSchema();
            progress.setTotal(contextIDs.length);
            for (int i = 0; i < contextIDs.length; i++) {
                progress.setState(i);
                insertGroupPermission(connection, contextIDs[i]);
            }
            connection.commit();
            committed = true;
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw UpdateExceptionCodes.OTHER_PROBLEM.create(e, e.getMessage());
        } finally {
            if (false == committed) {
                rollback(connection);
            }
            autocommit(connection);
        }
        log.info("{} successfully performed.", FolderPermissionAddGuestGroup.class.getSimpleName());
    }

    /**
     * Inserts the required guest group permissions on system folders for a specific context.
     *
     * @param connection A writable database connection
     * @param contextID The context ID
     * @return The batch update count
     * @throws SQLException
     */
    private static int[] insertGroupPermission(Connection connection, int contextID) throws SQLException, OXException {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(
                "INSERT INTO oxfolder_permissions (cid,fuid,permission_id,fp,orp,owp,odp,admin_flag,group_flag,system) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE fp=?,orp=?,owp=?,odp=?,admin_flag=?,group_flag=?,system=?;"
            );
            stmt.setInt(1, contextID);
            stmt.setInt(3, OCLPermission.ALL_GUESTS);
            stmt.setInt(4, OCLPermission.READ_FOLDER);
            stmt.setInt(5, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(6, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(7, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(8, 0);
            stmt.setInt(9, 1);
            stmt.setInt(10, 0);
            stmt.setInt(11, OCLPermission.READ_FOLDER);
            stmt.setInt(12, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(13, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(14, OCLPermission.NO_PERMISSIONS);
            stmt.setInt(15, 0);
            stmt.setInt(16, 1);
            stmt.setInt(17, 0);
            /*
             * add permissions to system folders with "read_folder" folder access
             */
            for (int folderID : systemFolderIDs) {
                if (systemFolderExist(connection, contextID, folderID)) {
                    stmt.setInt(2, folderID);
                    stmt.addBatch();
                } else {
                    log.warn("System folder {} not found in context {}, skipping.", I(folderID), I(contextID));
                }
            }
            /*
             * execute batch for context
             */
            return stmt.executeBatch();
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static boolean systemFolderExist(Connection con, int contextId, int fuid) throws OXException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement("SELECT 1 FROM oxfolder_tree WHERE cid = ? AND fuid = ?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, fuid);
            rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw UpdateExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }
    }

}
