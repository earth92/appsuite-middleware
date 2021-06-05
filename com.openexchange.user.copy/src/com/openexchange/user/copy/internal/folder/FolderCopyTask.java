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

package com.openexchange.user.copy.internal.folder;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.i;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.impl.IDGenerator;
import com.openexchange.user.copy.CopyUserTaskService;
import com.openexchange.user.copy.ObjectMapping;
import com.openexchange.user.copy.UserCopyExceptionCodes;
import com.openexchange.user.copy.internal.CopyTools;
import com.openexchange.user.copy.internal.connection.ConnectionFetcherTask;
import com.openexchange.user.copy.internal.context.ContextLoadTask;
import com.openexchange.user.copy.internal.folder.util.FolderEqualsWrapper;
import com.openexchange.user.copy.internal.folder.util.FolderPermission;
import com.openexchange.user.copy.internal.folder.util.Tree;
import com.openexchange.user.copy.internal.user.UserCopyTask;

/**
 * {@link FolderCopyTask} - Copies all private folders, the users private infostore folder and, if necessary, the mail attachment folder.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 */
public class FolderCopyTask implements CopyUserTaskService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FolderCopyTask.class);

    private static final String SELECT_FOLDERS =
        "SELECT "+
            "fuid, parent, fname, module, type, creating_date, " +
            "changing_date, changed_from, permission_flag, " +
            "subfolder_flag, default_flag " +
        "FROM " +
            "oxfolder_tree " +
        "WHERE " +
            "cid = ? AND created_from = ? AND (module = 8 OR type = 1)";

    private static final String INSERT_FOLDERS =
        "INSERT INTO " +
            "oxfolder_tree " +
            "(fuid, cid, parent, fname, module, type, creating_date, " +
            "created_from, changing_date, changed_from, permission_flag, " +
            "subfolder_flag, default_flag) " +
        "VALUES " +
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_VIRTUAL_FOLDERS =
        "SELECT " +
            "tree, folderId, parentId, " +
            "name, lastModified, modifiedBy, shadow, sortNum " +
        "FROM " +
            "virtualTree " +
        "WHERE " +
            "cid = ? " +
        "AND " +
            "user = ?";

    private static final String INSERT_VIRTUAL_FOLDERS =
        "INSERT INTO " +
            "virtualTree " +
            "(cid, tree, user, folderId, parentId, name, lastModified, modifiedBy, shadow, sortNum) " +
        "VALUES " +
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_PERMISSIONS =
        "INSERT INTO " +
            "oxfolder_permissions " +
            "(cid, fuid, permission_id, fp, orp, owp, odp, admin_flag, group_flag, system) " +
        "VALUES " +
            "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#getAlreadyCopied()
     */
    @Override
    public String[] getAlreadyCopied() {
        return new String[] {
            UserCopyTask.class.getName(),
            ContextLoadTask.class.getName(),
            ConnectionFetcherTask.class.getName()
        };
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#getObjectName()
     */
    @Override
    public String getObjectName() {
        return FolderObject.class.getName();
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#copyUser(java.util.Map)
     */
    @Override
    public ObjectMapping<FolderObject> copyUser(final Map<String, ObjectMapping<?>> copied) throws OXException {
        final CopyTools tools = new CopyTools(copied);
        final Integer srcCtxId = tools.getSourceContextId();
        final Integer dstCtxId = tools.getDestinationContextId();
        final Integer srcUsrId = tools.getSourceUserId();
        final Integer dstUsrId = tools.getDestinationUserId();
        final Connection srcCon = tools.getSourceConnection();
        final Connection dstCon = tools.getDestinationConnection();

        /*
         * Load all private folders from oxfolder_tree and modify object and parent ids.
         */
        final SortedMap<Integer, FolderEqualsWrapper> loadedFolders = loadFoldersFromDB(srcCon, i(srcCtxId), i(srcUsrId));
        final Tree<FolderEqualsWrapper> folderTree = buildFolderTree(loadedFolders);
        final SortedMap<Integer, FolderEqualsWrapper> originFolders = new TreeMap<Integer, FolderEqualsWrapper>();
        for (final FolderEqualsWrapper folder : folderTree.getAllNodesAsSet()) {
            try {
                final int id = folder.getObjectID();
                if (!ignoreFolder(id)) {
                    originFolders.put(I(id), folder.clone());
                }
            } catch (CloneNotSupportedException e) {
                throw UserCopyExceptionCodes.UNKNOWN_PROBLEM.create(e);
            }
        }

        final Map<Integer, Integer> idMapping = new HashMap<Integer, Integer>();
        exchangeIds(folderTree, folderTree.getRoot(), i(dstCtxId), i(dstUsrId), dstCon, -1, idMapping);

        /*
         * Write folders and permissions.
         */
        writeFoldersToDB(dstCon, folderTree, i(dstCtxId));
        writePermissionsToDB(idMapping.values(), dstCon, i(dstCtxId), i(dstUsrId));

        /*
         * Load and write virtual folders.
         */
        final List<VirtualFolder> virtualFolders = loadVirtualFoldersFromDB(srcCon, i(srcCtxId), i(srcUsrId));
        writeVirtualFoldersToDB(virtualFolders, idMapping, dstCon, i(dstCtxId), i(dstUsrId));

        /*
         * Create mapping between origin and target folders.
         */
        final SortedMap<Integer, FolderEqualsWrapper> movedFolders = loadFoldersFromDB(dstCon, i(dstCtxId), i(dstUsrId));
        final FolderMapping folderMapping = new FolderMapping();

        for (final Map.Entry<Integer, FolderEqualsWrapper> entry : originFolders.entrySet()) {
            Integer fuid = entry.getKey();
            FolderEqualsWrapper originWrapper = entry.getValue();
            final Integer targetId = idMapping.get(fuid);
            if (targetId == null) {
                throw UserCopyExceptionCodes.UNKNOWN_PROBLEM.create();
            }

            final FolderEqualsWrapper targetWrapper = movedFolders.get(targetId);
            if (targetWrapper == null) {
                throw UserCopyExceptionCodes.UNKNOWN_PROBLEM.create();
            }
            folderMapping.addMapping(fuid, originWrapper.getFolder(), targetId, targetWrapper.getFolder());
        }

        return folderMapping;
    }

    private boolean ignoreFolder(final int id) {
        return id == 0 || id == 1 || id == 9 || id == 10 || id == 15;
    }

    SortedMap<Integer, FolderEqualsWrapper> loadFoldersFromDB(final Connection con, final int cid, final int uid) throws OXException {
        final SortedMap<Integer, FolderEqualsWrapper> folderMap = new TreeMap<Integer, FolderEqualsWrapper>();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(SELECT_FOLDERS);
            stmt.setInt(1, cid);
            stmt.setInt(2, uid);
            rs = stmt.executeQuery();

            while (rs.next()) {
                final FolderObject folder = buildFolderFromResultSet(rs);
                folderMap.put(I(folder.getObjectID()), new FolderEqualsWrapper(folder, "orig"));
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        return folderMap;
    }

    private FolderObject buildFolderFromResultSet(final ResultSet rs) throws SQLException {
        int i = 1;
        final FolderObject folder = new FolderObject(rs.getInt(i++));
        folder.setParentFolderID(rs.getInt(i++));
        folder.setFolderName(rs.getString(i++));
        folder.setModule(rs.getInt(i++));
        folder.setType(rs.getInt(i++));
        folder.setCreationDate(new Date(rs.getLong(i++)));
        folder.setLastModified(new Date(rs.getLong(i++)));
        folder.setModifiedBy(rs.getInt(i++));
        folder.setPermissionFlag(rs.getInt(i++));
        folder.setSubfolderFlag(rs.getBoolean(i++));
        folder.setDefaultFolder(rs.getBoolean(i++));

        return folder;
    }

    private Tree<FolderEqualsWrapper> buildFolderTree(final SortedMap<Integer, FolderEqualsWrapper> folderMap) throws OXException {
        final FolderEqualsWrapper rootFolder = new FolderEqualsWrapper(new FolderObject(FolderObject.SYSTEM_ROOT_FOLDER_ID), "orig");
        rootFolder.setParentFolderID(-1);
        final FolderEqualsWrapper privateFolder = new FolderEqualsWrapper(new FolderObject(FolderObject.SYSTEM_PRIVATE_FOLDER_ID), "orig");
        privateFolder.setParentFolderID(FolderObject.SYSTEM_ROOT_FOLDER_ID);
        final FolderEqualsWrapper systemInfostoreFolder = new FolderEqualsWrapper(new FolderObject(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID), "orig");
        systemInfostoreFolder.setParentFolderID(FolderObject.SYSTEM_ROOT_FOLDER_ID);
        final FolderEqualsWrapper userInfostoreFolder = new FolderEqualsWrapper(new FolderObject(FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID), "orig");
        userInfostoreFolder.setParentFolderID(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID);
        final FolderEqualsWrapper publicInfostoreFolder = new FolderEqualsWrapper(new FolderObject(FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID), "orig");
        publicInfostoreFolder.setParentFolderID(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID);

        final SortedMap<Integer, FolderEqualsWrapper> extendedMap = new TreeMap<Integer, FolderEqualsWrapper>();
        extendedMap.putAll(folderMap);
        extendedMap.put(I(privateFolder.getObjectID()), privateFolder);
        extendedMap.put(I(systemInfostoreFolder.getObjectID()), systemInfostoreFolder);
        extendedMap.put(I(userInfostoreFolder.getObjectID()), userInfostoreFolder);
        extendedMap.put(I(publicInfostoreFolder.getObjectID()), publicInfostoreFolder);

        /*
         * A recursion is used here to be sure that the folder tree always contains a folders parent before the folder is added.
         * If the tree does not contain the parent already, the parent will be added first.
         */
        final Tree<FolderEqualsWrapper> folderTree = new Tree<FolderEqualsWrapper>(rootFolder);
        for (FolderEqualsWrapper folder : extendedMap.values()) {
            addFoldersRecursive(extendedMap, folderTree, folder);
        }

        folderTree.removeChild(publicInfostoreFolder);
        return folderTree;
    }

    private void addFoldersRecursive(final SortedMap<Integer, FolderEqualsWrapper> folderMap, final Tree<FolderEqualsWrapper> folderTree, final FolderEqualsWrapper folder) throws OXException {
        final int folderId = folder.getObjectID();
        final int parentFolderId = folder.getParentFolderID();
        final FolderEqualsWrapper parent = folderMap.get(I(parentFolderId));
        if (parentFolderId != 0 && parent == null) {
            LOG.warn(String.format("A private folder (%1$s) without existing parent (%2$s) was found. The folder will be ignored!", I(folderId), I(folder.getParentFolderID())));
            return;
        }

        if (parentFolderId == 0) {
            /*
             * Folder is a subfolder of root and can be added immediately.
             */
            folderTree.addChild(folder, folderTree.getRoot());
        } else {
            /*
             * If the folders parent is already part of the tree the folder will be added.
             * If not the parent will be added first. We also have to check if the folder already exists in the tree.
             */
            if (folderTree.containsChild(parent)) {
                if (!folderTree.containsChild(folder)) {
                    folderTree.addChild(folder, parent);
                }
            } else {
                addFoldersRecursive(folderMap, folderTree, parent);
                folderTree.addChild(folder, parent);
            }
        }
    }

    private void writeFoldersToDB(final Connection con, final Tree<FolderEqualsWrapper> folderTree, final int cid) throws OXException {
        final Set<FolderEqualsWrapper> allFolders = folderTree.getAllNodesAsSet();
        final List<FolderObject> foldersToWrite = new ArrayList<FolderObject>();
        for (final FolderEqualsWrapper folderWrapper : allFolders) {
            final FolderObject folder = folderWrapper.getFolder();
            if (!ignoreFolder(folder.getObjectID())) {
                foldersToWrite.add(folder);
            }
        }

        writeFoldersToDB(con, foldersToWrite, cid);
    }

    private void writeFoldersToDB(final Connection con, final List<FolderObject> folders, final int cid) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(INSERT_FOLDERS);
            for (final FolderObject folder : folders) {
                int i = 1;
                stmt.setInt(i++, folder.getObjectID());
                stmt.setInt(i++, cid);
                stmt.setInt(i++, folder.getParentFolderID());
                stmt.setString(i++, folder.getFolderName());
                stmt.setInt(i++, folder.getModule());
                stmt.setInt(i++, folder.getType());
                stmt.setLong(i++, folder.getCreationDate().getTime());
                stmt.setInt(i++, folder.getCreator());
                stmt.setLong(i++, folder.getLastModified().getTime());
                stmt.setInt(i++, folder.getModifiedBy());
                stmt.setInt(i++, folder.getPermissionFlag());
                stmt.setInt(i++, folder.hasSubfolders() ? 1 : 0);
                stmt.setInt(i++, folder.isDefaultFolder() ? 1 : 0);

                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private void exchangeIds(final Tree<FolderEqualsWrapper> folderTree, final FolderEqualsWrapper root, final int cid, final int uid, final Connection con, final int newParent, final Map<Integer, Integer> idMapping) throws OXException {
        try {
            final int origId = root.getObjectID();
            int newId = origId;
            if (!ignoreFolder(origId)) {
                newId = IDGenerator.getId(cid, com.openexchange.groupware.Types.FOLDER, con);
                idMapping.put(I(origId), I(newId));
            }

            final FolderEqualsWrapper rootClone = root.clone();
            rootClone.setObjectID(newId);
            rootClone.setCreator(uid);
            rootClone.setModifiedBy(uid);
            rootClone.setParentFolderID(newParent);
            rootClone.setKey("clone");
            if (folderTree.exchangeNodes(root, rootClone) && !folderTree.isLeaf(rootClone)) {
                final Set<FolderEqualsWrapper> children = folderTree.getChildren(rootClone);
                for (final FolderEqualsWrapper folder : children) {
                    exchangeIds(folderTree, folder, cid, uid, con, newId, idMapping);
                }
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } catch (CloneNotSupportedException e) {
            throw UserCopyExceptionCodes.UNKNOWN_PROBLEM.create(e);
        }
    }

    private void writePermissionsToDB(final Collection<Integer> folderIds, final Connection con, final int cid, final int uid) throws OXException {
        final List<FolderPermission> permissions = new ArrayList<FolderPermission>();
        for (final int folderId : folderIds) {
            final FolderPermission permission = new FolderPermission();
            permission.setUserId(uid);
            permission.setFolderId(folderId);
            permission.setFp(128);
            permission.setOrp(128);
            permission.setOwp(128);
            permission.setOdp(128);
            permission.setAdminFlag(true);
            permission.setGroupFlag(false);
            permission.setSystem(false);

            permissions.add(permission);
        }

        writePermissionsToDB(permissions, con, cid);
    }

    private void writePermissionsToDB(final List<FolderPermission> permissions, final Connection con, final int cid) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(INSERT_PERMISSIONS);
            for (final FolderPermission permission : permissions) {
                int i = 1;
                stmt.setInt(i++, cid);
                stmt.setInt(i++, permission.getFolderId());
                stmt.setInt(i++, permission.getUserId());
                stmt.setInt(i++, permission.getFp());
                stmt.setInt(i++, permission.getOrp());
                stmt.setInt(i++, permission.getOwp());
                stmt.setInt(i++, permission.getOdp());
                stmt.setInt(i++, permission.hasAdminFlag() ? 1 : 0);
                stmt.setInt(i++, permission.hasGroupFlag() ? 1 : 0);
                stmt.setInt(i++, permission.hasSystem() ? 1 : 0);
                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    List<VirtualFolder> loadVirtualFoldersFromDB(final Connection con, final int cid, final int uid) throws OXException {
        final List<VirtualFolder> folderList = new ArrayList<VirtualFolder>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(SELECT_VIRTUAL_FOLDERS);
            stmt.setInt(1, cid);
            stmt.setInt(2, uid);
            rs = stmt.executeQuery();

            while (rs.next()) {
                final VirtualFolder folder = new VirtualFolder();
                int i = 1;
                folder.setTree(rs.getInt(i++));
                folder.setFolderId(rs.getString(i++));
                folder.setParentId(rs.getString(i++));
                folder.setName(rs.getString(i++));
                folder.setLastModified(rs.getLong(i++));
                folder.setModifiedBy(rs.getInt(i++));
                folder.setShadow(rs.getString(i++));
                folder.setSortNum(i++);
                if (rs.wasNull()) {
                    folder.setSortNum(-1);
                }

                folderList.add(folder);
            }
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(rs, stmt);
        }

        return folderList;
    }

    private void writeVirtualFoldersToDB(final List<VirtualFolder> folderList, final Map<Integer, Integer> idMapping, final Connection con, final int cid, final int uid) throws OXException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(INSERT_VIRTUAL_FOLDERS);
            for (final VirtualFolder folder : folderList) {
                /*
                 * Correct folderId and parentId if necessary.
                 */
                final String folderIdStr = folder.getFolderId();
                final String parentIdStr = folder.getParentId();
                Integer folderId = null;
                Integer parentId = null;
                try {
                    Integer tmp = Integer.valueOf(folderIdStr);
                    final Integer newFolderId = idMapping.get(tmp);
                    if (newFolderId != null) {
                        folderId = newFolderId;
                    }

                    tmp = Integer.valueOf(parentIdStr);
                    final Integer newParentId = idMapping.get(tmp);
                    if (newParentId != null) {
                        parentId = newParentId;
                    }
                } catch (NumberFormatException e) {
                    // do nothing
                }

                int i = 1;
                stmt.setInt(i++, cid);
                stmt.setInt(i++, folder.getTree());
                stmt.setInt(i++, uid);
                if (folderId == null) {
                    stmt.setString(i++, folderIdStr);
                } else {
                    stmt.setString(i++, String.valueOf(i(folderId)));
                }
                if (parentId == null) {
                    stmt.setString(i++, parentIdStr);
                } else {
                    stmt.setString(i++, String.valueOf(i(parentId)));
                }
                stmt.setString(i++, folder.getName());
                if (folder.getLastModified() == 0) {
                    stmt.setNull(i++, java.sql.Types.BIGINT);
                } else {
                    stmt.setLong(i++, folder.getLastModified());
                }
                if (folder.getModifiedBy() == 0) {
                    stmt.setNull(i++, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(i++, folder.getModifiedBy());
                }
                stmt.setString(i++, folder.getShadow());
                if (folder.getSortNum() == -1) {
                    stmt.setNull(i++, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(i++, folder.getSortNum());
                }

                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw UserCopyExceptionCodes.SQL_PROBLEM.create(e);
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    /**
     * @see com.openexchange.user.copy.CopyUserTaskService#done(java.util.Map, boolean)
     */
    @Override
    public void done(final Map<String, ObjectMapping<?>> copied, final boolean failed) {
    }

}
