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

import static com.openexchange.tools.sql.DBUtils.closeResources;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.json.JSONException;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.FolderPathObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.impl.OCLPermission;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * {@link OXFolderLoader}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class OXFolderLoader {

    private static final String TABLE_OT = "oxfolder_tree";

    private static final String TABLE_OP = "oxfolder_permissions";

    /**
     * Initializes a new {@link OXFolderLoader}.
     */
    private OXFolderLoader() {
        super();
    }

    public static FolderObject loadFolderObjectFromDB(final int folderId, final Context ctx) throws OXException {
        return loadFolderObjectFromDB(folderId, ctx, null, true, false);
    }

    public static FolderObject loadFolderObjectFromDB(final int folderId, final Context ctx, final Connection readCon) throws OXException {
        return loadFolderObjectFromDB(folderId, ctx, readCon, true, false);
    }

    /**
     * Loads specified folder from database.
     *
     * @param folderId The folder identifier
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @param loadPermissions <code>true</code> to load folder's permissions, otherwise <code>false</code>
     * @param loadSubfolderList <code>true</code> to load sub-folders, otherwise <code>false</code>
     * @return The loaded folder object from database
     * @throws OXException If folder cannot be loaded
     */
    public static FolderObject loadFolderObjectFromDB(final int folderId, final Context ctx, final Connection readConArg, final boolean loadPermissions, final boolean loadSubfolderList) throws OXException {
        return loadFolderObjectFromDB(folderId, ctx, readConArg, loadPermissions, loadSubfolderList, TABLE_OT, TABLE_OP);
    }

    private static final String SQL_LOAD_F =
        "SELECT parent, fname, module, type, creating_date, created_from, changing_date, changed_from, permission_flag, subfolder_flag, default_flag, meta, origin FROM #TABLE# WHERE cid = ? AND fuid = ?";

    /**
     * Loads specified folder from database.
     *
     * @param folderId The folder identifier
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @param loadPermissions <code>true</code> to load folder's permissions, otherwise <code>false</code>
     * @param loadSubfolderList <code>true</code> to load sub-folders, otherwise <code>false</code>
     * @param table The folder's working or backup table name
     * @param permTable The folder permissions' working or backup table name
     * @return The loaded folder object from database
     * @throws OXException If folder cannot be loaded
     */
    public static FolderObject loadFolderObjectFromDB(final int folderId, final Context ctx, final Connection readConArg, final boolean loadPermissions, final boolean loadSubfolderList, final String table, final String permTable) throws OXException {
        try {
            Connection readCon = readConArg;
            boolean closeCon = false;
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                if (readCon == null) {
                    readCon = DBPool.pickup(ctx);
                    closeCon = true;
                }
                stmt = readCon.prepareStatement(Strings.replaceSequenceWith(SQL_LOAD_F, "#TABLE#", table));
                stmt.setInt(1, ctx.getContextId());
                stmt.setInt(2, folderId);
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    throw OXFolderExceptionCode.NOT_EXISTS.create(Integer.valueOf(folderId), Integer.valueOf(ctx.getContextId()));
                }
                final FolderObject folderObj = new FolderObject(rs.getString(2), folderId, rs.getInt(3), rs.getInt(4), rs.getInt(6));
                folderObj.setParentFolderID(rs.getInt(1));
                folderObj.setCreatedBy(parseStringValue(rs.getString(6), ctx));
                folderObj.setCreationDate(new Date(rs.getLong(5)));
                folderObj.setSubfolderFlag(rs.getInt(10) > 0 ? true : false);
                folderObj.setLastModified(new Date(rs.getLong(7)));
                folderObj.setModifiedBy(parseStringValue(rs.getString(8), ctx));
                folderObj.setPermissionFlag(rs.getInt(9));
                String sFolderPath = rs.getString(13);
                if (rs.wasNull()) {
                    folderObj.setOriginPath(null);
                } else {
                    folderObj.setOriginPath(FolderPathObject.parseFrom(sFolderPath));
                }
                final int defaultFolder = rs.getInt(11);
                if (rs.wasNull()) {
                    folderObj.setDefaultFolder(false);
                } else {
                    folderObj.setDefaultFolder(defaultFolder > 0);
                }
                {
                    final InputStream jsonBlobStream = rs.getBinaryStream(12);
                    if (!rs.wasNull() && null != jsonBlobStream) {
                        try {
                            folderObj.setMeta(OXFolderUtility.deserializeMeta(jsonBlobStream));
                        } finally {
                            Streams.close(jsonBlobStream);
                        }
                    }
                }
                if (loadSubfolderList) {
                    List<Integer> subfolderList = getSubfolderIds(folderId, ctx, readCon, table);
                    folderObj.setSubfolderIds(subfolderList);
                }

                if (loadPermissions) {
                    folderObj.setPermissionsAsArray(getFolderPermissions(folderId, ctx, readCon, permTable));
                }
                return folderObj;
            } finally {
                closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
            }
        } catch (SQLException e) {
            throw OXFolderExceptionCode.FOLDER_COULD_NOT_BE_LOADED.create(e, Integer.toString(folderId), Integer.toString(ctx.getContextId()));
        } catch (JSONException e) {
            throw OXFolderExceptionCode.FOLDER_COULD_NOT_BE_LOADED.create(e, Integer.toString(folderId), Integer.toString(ctx.getContextId()));
        }
        //catch (OXException e) {
        //    throw OXFolderExceptionCode.FOLDER_COULD_NOT_BE_LOADED.create(e, String.valueOf(folderId), String.valueOf(ctx.getContextId()));
        //}
    }

    /**
     * Loads folder permissions from database. Creates a new connection if <code>null</code> is given.
     *
     * @param folderId The folder identifier
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @return The folder's permissions
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static OCLPermission[] getFolderPermissions(final int folderId, final Context ctx, final Connection readConArg) throws SQLException, OXException {
        return getFolderPermissions(folderId, ctx, readConArg, TABLE_OP);
    }

    private static final String SQL_LOAD_P =
        "SELECT permission_id, fp, orp, owp, odp, admin_flag, group_flag, `system`, type, sharedParentFolder FROM #TABLE# WHERE cid = ? AND fuid = ?";

    /**
     * Loads folder permissions from database. Creates a new connection if <code>null</code> is given.
     *
     * @param folderId The folder identifier
     * @param ctx The context
     * @param readCon A connection with read capability; may be <code>null</code> to fetch from pool
     * @param table Either folder permissions working or backup table name
     * @return The folder's permissions
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static OCLPermission[] getFolderPermissions(final int folderId, final Context ctx, final Connection readConArg, final String table) throws SQLException, OXException {
        Connection readCon = readConArg;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (readCon == null) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(Strings.replaceSequenceWith(SQL_LOAD_P, "#TABLE#", table));
            stmt.setInt(1, ctx.getContextId());
            stmt.setInt(2, folderId);
            rs = stmt.executeQuery();
            final ArrayList<OCLPermission> permList = new ArrayList<OCLPermission>();
            while (rs.next()) {
                final OCLPermission p = new OCLPermission();
                p.setEntity(rs.getInt(1)); // Entity
                p.setAllPermission(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)); // fp, orp, owp, and odp
                p.setFolderAdmin(rs.getInt(6) > 0 ? true : false); // admin_flag
                p.setGroupPermission(rs.getInt(7) > 0 ? true : false); // group_flag
                p.setSystem(rs.getInt(8)); // system
                p.setType(FolderPermissionType.getType(rs.getInt(9))); // type
                int legator = rs.getInt(10);
                p.setPermissionLegator(legator==0 ? null : String.valueOf(legator)); // permission legator
                permList.add(p);
            }
            return permList.toArray(new OCLPermission[permList.size()]);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            if (closeCon) {
                DBPool.closeReaderSilent(ctx, readCon);
            }
        }
    }

    /**
     * Gets the sub-folder identifiers and names of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folders identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static List<IdAndName> getSubfolderIdAndNames(final int folderId, final Context ctx, final Connection readConArg) throws SQLException, OXException {
        return getSubfolderIdAndNames(folderId, ctx, readConArg, TABLE_OT);
    }

    private static final String SQL_SEL2 = "SELECT fuid, fname FROM #TABLE# WHERE cid = ? AND parent = ? ORDER BY default_flag DESC, fname";

    /**
     * Gets the sub-folder identifiers and names of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folders identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @param table The folder's working or backup table name
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static List<IdAndName> getSubfolderIdAndNames(final int folderId, final Context ctx, final Connection readConArg, final String table) throws SQLException, OXException {
        Connection readCon = readConArg;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (readCon == null) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(Strings.replaceSequenceWith(SQL_SEL2, "#TABLE#", table));
            stmt.setInt(1, ctx.getContextId());
            stmt.setInt(2, folderId);
            rs = stmt.executeQuery();
            final List<IdAndName> retval = new ArrayList<IdAndName>();
            while (rs.next()) {
                retval.add(new IdAndName(rs.getInt(1), rs.getString(2)));
            }
            return retval;
        } finally {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
        }
    }

    /**
     * Gets the sub-folder identifiers of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folders identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static List<Integer> getSubfolderIds(final int folderId, final Context ctx, final Connection readConArg) throws SQLException, OXException {
        return getSubfolderIds(folderId, ctx, readConArg, TABLE_OT);
    }

    private static final String SQL_SEL = "SELECT fuid FROM #TABLE# WHERE cid = ? AND parent = ? ORDER BY default_flag DESC, fname";

    /**
     * Gets the sub-folder identifiers of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folders identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @param table The folder's working or backup table name
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static List<Integer> getSubfolderIds(final int folderId, final Context ctx, final Connection readConArg, final String table) throws SQLException, OXException {
        Connection readCon = readConArg;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (readCon == null) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(Strings.replaceSequenceWith(SQL_SEL, "#TABLE#", table));
            stmt.setInt(1, ctx.getContextId());
            stmt.setInt(2, folderId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return Collections.emptyList();
            }

            List<Integer> retval = new ArrayList<Integer>();
            do {
                retval.add(Integer.valueOf(rs.getInt(1)));
            } while (rs.next());
            return retval;
        } finally {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
        }
    }

    /**
     * Gets the sub-folder identifiers of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folder identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static TIntList getSubfolderInts(final int folderId, final Context ctx, final Connection readConArg) throws SQLException, OXException {
        return getSubfolderInts(folderId, ctx, readConArg, TABLE_OT);
    }

    /**
     * Gets the sub-folder identifiers of specified folder.
     *
     * @param folderId The identifier of the folder whose sub-folders identifiers shall be returned
     * @param ctx The context
     * @param readConArg A connection with read capability; may be <code>null</code> to fetch from pool
     * @param table The folder's working or backup table name
     * @return The sub-folder identifiers of specified folder
     * @throws SQLException If an SQL error occurs
     * @throws OXException If a pooling error occurs
     */
    public static TIntList getSubfolderInts(final int folderId, final Context ctx, final Connection readConArg, final String table) throws SQLException, OXException {
        Connection readCon = readConArg;
        boolean closeCon = false;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            if (readCon == null) {
                readCon = DBPool.pickup(ctx);
                closeCon = true;
            }
            stmt = readCon.prepareStatement(Strings.replaceSequenceWith(SQL_SEL, "#TABLE#", table));
            stmt.setInt(1, ctx.getContextId());
            stmt.setInt(2, folderId);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return new TIntArrayList(0);
            }

            TIntList retval = new TIntArrayList();
            do {
                retval.add(rs.getInt(1));
            } while (rs.next());
            return retval;
        } finally {
            closeResources(rs, stmt, closeCon ? readCon : null, true, ctx);
        }
    }

    private static final int parseStringValue(final String str, final Context ctx) {
        if (null == str) {
            return -1;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            if (str.equalsIgnoreCase("system")) {
                return ctx.getMailadmin();
            }
        }
        return -1;
    }

    public static final class IdAndName {

        private final int fuid;

        private final String fname;

        private final int hash;

        IdAndName(final int fuid, final String fname) {
            super();
            this.fuid = fuid;
            this.fname = fname;
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fname == null) ? 0 : fname.hashCode());
            result = prime * result + fuid;
            this.hash = result;
        }

        /**
         * Gets the folder identifier
         *
         * @return The folder identifier
         */
        public int getFolderId() {
            return fuid;
        }

        /**
         * Gets the folder name
         *
         * @return The folder name
         */
        public String getName() {
            return fname;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof IdAndName)) {
                return false;
            }
            final IdAndName other = (IdAndName) obj;
            if (fname == null) {
                if (other.fname != null) {
                    return false;
                }
            } else if (!fname.equals(other.fname)) {
                return false;
            }
            if (fuid != other.fuid) {
                return false;
            }
            return true;
        }

    }

}
