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

package com.openexchange.folderstorage.filestorage.impl;

import java.sql.Connection;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFileStorageFolder;
import com.openexchange.file.storage.DefaultFileStoragePermission;
import com.openexchange.file.storage.FileStorageAccounts;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStoragePermission;
import com.openexchange.file.storage.FileStorageService;
import com.openexchange.file.storage.SharingFileStorageService;
import com.openexchange.file.storage.WarningsAware;
import com.openexchange.file.storage.composition.FolderID;
import com.openexchange.file.storage.composition.IDBasedFolderAccess;
import com.openexchange.file.storage.composition.IDBasedFolderAccessFactory;
import com.openexchange.file.storage.registry.FileStorageServiceRegistry;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.Folder;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.FolderType;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.SetterAwareFolder;
import com.openexchange.folderstorage.SortableId;
import com.openexchange.folderstorage.StorageParameters;
import com.openexchange.folderstorage.StorageParametersUtility;
import com.openexchange.folderstorage.StoragePriority;
import com.openexchange.folderstorage.StorageType;
import com.openexchange.folderstorage.SubfolderListingFolderStorage;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.UpdateOperation;
import com.openexchange.folderstorage.filestorage.FileStorageId;
import com.openexchange.folderstorage.filestorage.FileStorageUtils;
import com.openexchange.folderstorage.filestorage.contentType.FileStorageContentType;
import com.openexchange.folderstorage.tx.TransactionManager;
import com.openexchange.folderstorage.type.FileStorageType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.java.Collators;
import com.openexchange.java.Reference;
import com.openexchange.messaging.MessagingPermission;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;

/**
 * {@link FileStorageFolderStorage} - The file storage folder storage.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class FileStorageFolderStorage implements SubfolderListingFolderStorage {

    private static final String ALT_NAMES = "altNames";

    /**
     * <code>"infostore"</code>
     */
    private static final String INFOSTORE_ACCOUNT_ID = com.openexchange.file.storage.composition.FileID.INFOSTORE_ACCOUNT_ID;

    private static final String PARAM = FileStorageParameterConstants.PARAM_ID_BASED_FOLDER_ACCESS;

    /**
     * <code>"1"</code>
     */
    private static final String PRIVATE_FOLDER_ID = String.valueOf(FolderObject.SYSTEM_PRIVATE_FOLDER_ID);

    /**
     * <code>"9"</code>
     */
    private static final String INFOSTORE = Integer.toString(FolderObject.SYSTEM_INFOSTORE_FOLDER_ID);

    // --------------------------------------------------------------------------------------------------------------------------- //

    private final ServiceLookup services;

    /**
     * Initializes a new {@link FileStorageFolderStorage}.
     *
     * @param services A service lookup reference
     */
    public FileStorageFolderStorage(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public boolean startTransaction(StorageParameters parameters, boolean modify) throws OXException {
        /*
         * initialize ID based file access if necessary
         */
        if (null == parameters.getParameter(getFolderType(), PARAM)) {
            /*
             * ensure the session is present
             */
            if (null == parameters.getSession()) {
                throw FolderExceptionErrorMessage.MISSING_SESSION.create();
            }
            /*
             * create access via factory
             */
            IDBasedFolderAccessFactory factory = services.getService(IDBasedFolderAccessFactory.class);
            if (null == factory) {
                throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(IDBasedFolderAccessFactory.class.getName());
            }
            IDBasedFolderAccess folderAccess = factory.createAccess(parameters.getSession());
            if (parameters.putParameterIfAbsent(getFolderType(), PARAM, folderAccess)) {
                /*
                 * enqueue in managed transaction if possible, otherwise signal that we started the transaction ourselves
                 */
                if (!TransactionManager.isManagedTransaction(parameters)) {
                    return true;
                }
                TransactionManager transactionManager = TransactionManager.getTransactionManager(parameters);
                Session session = parameters.getSession();
                session.setParameter(Connection.class.getName() + '@' + Thread.currentThread().getId(), transactionManager.getConnection());
                transactionManager.transactionStarted(this);
            }
        }
        return false;
    }

    @Override
    public void rollback(StorageParameters storageParameters) {
        IDBasedFolderAccess folderAccess = storageParameters.getParameter(getFolderType(), PARAM);
        if (null != folderAccess) {
            try {
                folderAccess.rollback();
            } catch (Exception e) {
                // Ignore
                org.slf4j.LoggerFactory.getLogger(FileStorageFolderStorage.class).warn("Unexpected error during rollback: {}", e.getMessage(), e);
            } finally {
                if (null != storageParameters.putParameter(getFolderType(), PARAM, null)) {
                    Session session = storageParameters.getSession();
                    if (null != session && session.containsParameter(Connection.class.getName() + '@' + Thread.currentThread().getId())) {
                        session.setParameter(Connection.class.getName() + '@' + Thread.currentThread().getId(), null);
                    }
                }
            }
            addWarnings(storageParameters, folderAccess);
        }
    }

    @Override
    public void commitTransaction(StorageParameters storageParameters) throws OXException {
        IDBasedFolderAccess folderAccess = storageParameters.getParameter(getFolderType(), PARAM);
        if (null != folderAccess) {
            try {
                folderAccess.commit();
            } finally {
                if (null != storageParameters.putParameter(getFolderType(), PARAM, null)) {
                    Session session = storageParameters.getSession();
                    if (null != session && session.containsParameter(Connection.class.getName() + '@' + Thread.currentThread().getId())) {
                        session.setParameter(Connection.class.getName() + '@' + Thread.currentThread().getId(), null);
                    }
                }
            }
            addWarnings(storageParameters, folderAccess);
        }
    }

    @Override
    public void clearCache(final int userId, final int contextId) {
        /*
         * Nothing to do...
         */
    }

    @Override
    public void restore(final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        // TODO:
    }

    @Override
    public Folder prepareFolder(final String treeId, final Folder folder, final StorageParameters storageParameters) throws OXException {
        // TODO
        return folder;
    }

    @Override
    public void checkConsistency(final String treeId, final StorageParameters storageParameters) throws OXException {
        // Nothing to do
    }

    @Override
    public SortableId[] getVisibleFolders(final String treeId, final ContentType contentType, final Type type, final StorageParameters storageParameters) throws OXException {
        throw new UnsupportedOperationException("FileStorageFolderStorage.getVisibleSubfolders()");
    }

    @Override
    public SortableId[] getVisibleFolders(String rootFolderId, String treeId, ContentType contentType, Type type, StorageParameters storageParameters) throws OXException {
        throw new UnsupportedOperationException("FileStorageFolderStorage.getVisibleSubfolders()");
    }

    @Override
    public SortableId[] getUserSharedFolders(String treeId, ContentType contentType, StorageParameters storageParameters) throws OXException {
        if (!FileStorageContentType.class.isInstance(contentType)) {
            throw FolderExceptionErrorMessage.UNKNOWN_CONTENT_TYPE.create(contentType.toString());
        }
        IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);
        FileStorageFolder[] sharedFolders = folderAccess.getUserSharedFolders();
        if (null == sharedFolders) {
            return null;
        }
        SortableId[] sortableIds = new SortableId[sharedFolders.length];
        for (int i = 0; i < sharedFolders.length; i++) {
            sortableIds[i] = new FileStorageId(sharedFolders[i].getId(), i, sharedFolders[i].getName());
        }
        return sortableIds;
    }

    @Override
    public ContentType[] getSupportedContentTypes() {
        return new ContentType[] { FileStorageContentType.getInstance() };
    }

    @Override
    public ContentType getDefaultContentType() {
        return FileStorageContentType.getInstance();
    }

    @Override
    public void createFolder(final Folder folder, final StorageParameters storageParameters) throws OXException {
        final IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);
        final DefaultFileStorageFolder fsFolder = new DefaultFileStorageFolder();
        fsFolder.setExists(false);
        fsFolder.setParentId(folder.getParentID());
        // Other
        fsFolder.setName(folder.getName());
        if (false == SetterAwareFolder.class.isInstance(folder) || ((SetterAwareFolder) folder).containsSubscribed()) {
            fsFolder.setSubscribed(folder.isSubscribed());
        }
        // Permissions
        final Permission[] permissions = folder.getPermissions();
        if (null != permissions && permissions.length > 0) {
            fsFolder.setPermissions(FileStorageUtils.getFileStoragePermissions(permissions));
        } else if (FileStorageFolder.ROOT_FULLNAME.equals(folder.getParentID())) {
            FileStoragePermission fsPerm = DefaultFileStoragePermission.newInstance();
            fsPerm.setEntity(storageParameters.getUserId());
            fsPerm.setAllPermissions(
                MessagingPermission.MAX_PERMISSION,
                MessagingPermission.MAX_PERMISSION,
                MessagingPermission.MAX_PERMISSION,
                MessagingPermission.MAX_PERMISSION);
            fsPerm.setAdmin(true);
            fsPerm.setGroup(false);
            fsFolder.setPermissions(Collections.singletonList(fsPerm));
        }
        try {
            String fullName = folderAccess.createFolder(fsFolder);
            folder.setID(fullName);
        } catch (OXException e) {
            if (FileStorageExceptionCodes.DUPLICATE_FOLDER.equals(e)) {
                Throwable cause = e.getCause();
                Object[] args = e.getLogArgs();
                throw FolderExceptionErrorMessage.EQUAL_NAME.create(cause, args[0], args[1], folder.getTreeID());
            }
            throw e;
        }
    }

    @Override
    public void clearFolder(final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        boolean hardDelete = StorageParametersUtility.getBoolParameter("hardDelete", storageParameters);
        getFolderAccess(storageParameters).clearFolder(folderId, hardDelete);
    }

    @Override
    public void deleteFolder(String treeId, String folderId, StorageParameters storageParameters) throws OXException {
        boolean hardDelete = StorageParametersUtility.getBoolParameter("hardDelete", storageParameters);
        getFolderAccess(storageParameters).deleteFolder(folderId, hardDelete);
    }

    @Override
    public String getDefaultFolderID(final User user, final String treeId, final ContentType contentType, final Type type, final StorageParameters storageParameters) throws OXException {
        if (!(contentType instanceof FileStorageContentType)) {
            throw FolderExceptionErrorMessage.UNKNOWN_CONTENT_TYPE.create(contentType.toString());
        }
        // TODO: Return primary InfoStore's default folder
        return INFOSTORE;
    }

    @Override
    public Type getTypeByParent(final User user, final String treeId, final String parentId, final StorageParameters storageParameters) throws OXException {
        return FileStorageType.getInstance();
    }

    @Override
    public boolean containsForeignObjects(final User user, final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        final IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);

        if (!folderAccess.exists(folderId)) {
            FolderID folderID = new FolderID(folderId);
            throw FileStorageExceptionCodes.FOLDER_NOT_FOUND.create(
                folderID.getFolderId(),
                Integer.valueOf(folderID.getAccountId()),
                folderID.getService(),
                Integer.valueOf(storageParameters.getUserId()),
                Integer.valueOf(storageParameters.getContextId()));
        }

        return false;
    }

    @Override
    public boolean isEmpty(String treeId, String folderId, StorageParameters storageParameters) throws OXException {
        return 1 > getFolderAccess(storageParameters).getFolder(folderId).getFileCount();
    }

    @Override
    public void updateLastModified(final long lastModified, final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        // Nothing to do
    }

    @Override
    public List<Folder> getFolders(final String treeId, final List<String> folderIds, final StorageParameters storageParameters) throws OXException {
        return getFolders(treeId, folderIds, StorageType.WORKING, storageParameters);
    }

    @Override
    public List<Folder> getFolders(final String treeId, final List<String> folderIds, final StorageType storageType, final StorageParameters storageParameters) throws OXException {
        if (StorageType.BACKUP.equals(storageType)) {
            throw FolderExceptionErrorMessage.UNSUPPORTED_STORAGE_TYPE.create(storageType);
        }

        IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);
        boolean altNames = StorageParametersUtility.getBoolParameter(ALT_NAMES, storageParameters);
        Session session = storageParameters.getSession();

        List<Folder> ret = new ArrayList<>(folderIds.size());
        for (String folderId : folderIds) {
            FolderID fid = new FolderID(folderId);
            FileStorageFolder fsFolder = folderAccess.getFolder(fid);
            String accountID = FileStorageAccounts.getQualifiedID(fid.getService(), fid.getAccountId());
            FileStorageFolderImpl folder;
            if (session == null) {
                folder = new FileStorageFolderImpl(fsFolder, accountID, storageParameters.getUserId(), storageParameters.getContextId(), altNames, folderAccess);
            } else {
                folder = new FileStorageFolderImpl(fsFolder, accountID, session, altNames, folderAccess);
            }
            folder.setTreeID(treeId);
            ret.add(folder);
        }
        return ret;
    }

    @Override
    public Folder getFolder(final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        return getFolder(treeId, folderId, StorageType.WORKING, storageParameters);
    }

    @Override
    public Folder getFolder(final String treeId, final String folderId, final StorageType storageType, final StorageParameters storageParameters) throws OXException {
        if (StorageType.BACKUP.equals(storageType)) {
            throw FolderExceptionErrorMessage.UNSUPPORTED_STORAGE_TYPE.create(storageType);
        }
        FolderID fid = new FolderID(folderId);
        IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);
        FileStorageFolder fsFolder = folderAccess.getFolder(fid);
        boolean altNames = StorageParametersUtility.getBoolParameter(ALT_NAMES, storageParameters);
        String accountID = FileStorageAccounts.getQualifiedID(fid.getService(), fid.getAccountId());
        Session session = storageParameters.getSession();
        FileStorageFolderImpl retval;
        if (session == null) {
            retval = new FileStorageFolderImpl(fsFolder, accountID, storageParameters.getUserId(), storageParameters.getContextId(), altNames, folderAccess);
        } else {
            retval = new FileStorageFolderImpl(fsFolder, accountID, session, altNames, folderAccess);
        }
        retval.setTreeID(treeId);
        return retval;
    }

    @Override
    public FolderType getFolderType() {
        return FileStorageFolderType.getInstance();
    }

    /**
     * Checks if a given folder is related to a {@link SharingFileStorageService}
     *
     * @param session The session
     * @param folderId The folder
     * @return True, if the given folder is related to a {@link SharingFileStorageService}, false otherwise.
     * @throws OXException
     */
    private boolean belongToSharingFileService(Session session, FolderID folderId) throws OXException {
        FileStorageServiceRegistry registry = ServerServiceRegistry.getInstance().getService(FileStorageServiceRegistry.class);
        FileStorageService fileStorageService = registry.getFileStorageService(folderId.getService());
        if (fileStorageService != null) {
            return fileStorageService instanceof SharingFileStorageService;
        }
        return false;
    }

    @Override
    public SortableId[] getSubfolders(final String treeId, final String parentId, final StorageParameters storageParameters) throws OXException {
        final IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);

        final boolean isRealTree = REAL_TREE_ID.equals(treeId);
        if (isRealTree ? PRIVATE_FOLDER_ID.equals(parentId) : INFOSTORE.equals(parentId)) {
            /*-
             * TODO:
             * 1. Check for file storage permission; e.g. session.getUserPermissionBits().isMultipleMailAccounts()
             *    Add primary only if not enabled
             * 2. Strip Unified-FileStorage account from obtained list
             */
            Locale userLocale = null;
            if (storageParameters.getSession() != null) {
                User user = ServerSessionAdapter.valueOf(storageParameters.getSession()).getUser();
                if (user != null) {
                    userLocale = user.getLocale();
                }
            }
            if (null == userLocale) {
                userLocale = storageParameters.getUser().getLocale();
            }

            FileStorageFolder[] rootFolders = folderAccess.getRootFolders(userLocale);
            int size = rootFolders.length;
            if (size <= 0) {
                return new SortableId[0];
            }

            List<SortableId> list = new ArrayList<>(size);
            if (isRealTree) {
                int index = 0;
                for (int j = 0; j < size; j++) {
                    String id = rootFolders[j].getId();

                    if(belongToSharingFileService(storageParameters.getSession(), new FolderID(id))) {
                        continue;
                    }

                    if ((id.length() != 0) && !INFOSTORE_ACCOUNT_ID.equals(new FolderID(id).getAccountId())) {
                        list.add(new FileStorageId(id, index++, null));
                    }
                }
            } else {
                for (int j = 0; j < size; j++) {
                    list.add(new FileStorageId(rootFolders[j].getId(), j, null));
                }
            }
            return list.toArray(new SortableId[list.size()]);
        }

        // A file storage folder denoted by full name
        List<FileStorageFolder> children = Arrays.asList(folderAccess.getSubfolders(parentId, true));
        /*
         * Sort
         */
        Collections.sort(children, new SimpleFileStorageFolderComparator(storageParameters.getUser().getLocale()));
        final List<SortableId> list = new ArrayList<>(children.size());
        final int size = children.size();
        for (int j = 0; j < size; j++) {
            final FileStorageFolder cur = children.get(j);
            list.add(new FileStorageId(cur.getId(), j, cur.getName()));
        }
        return list.toArray(new SortableId[list.size()]);
    }

    @Override
    public Folder[] getSubfolderObjects(final String treeId, final String parentId, final StorageParameters storageParameters) throws OXException {
        IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);

        Session session = storageParameters.getSession();
        boolean isRealTree = REAL_TREE_ID.equals(treeId);
        if (isRealTree ? PRIVATE_FOLDER_ID.equals(parentId) : INFOSTORE.equals(parentId)) {
            /*-
             * TODO:
             * 1. Check for file storage permission; e.g. session.getUserPermissionBits().isMultipleMailAccounts()
             *    Add primary only if not enabled
             * 2. Strip Unified-FileStorage account from obtained list
             */
            Locale userLocale = null;
            if (session != null) {
                User user = ServerSessionAdapter.valueOf(session).getUser();
                if (user != null) {
                    userLocale = user.getLocale();
                }
            }
            if (null == userLocale) {
                userLocale = storageParameters.getUser().getLocale();
            }

            FileStorageFolder[] rootFolders = folderAccess.getRootFolders(userLocale);
            int size = rootFolders.length;
            if (size <= 0) {
                return new Folder[0];
            }

            boolean altNames = StorageParametersUtility.getBoolParameter(ALT_NAMES, storageParameters);
            List<Folder> list = new ArrayList<>(size);
            if (isRealTree) {
                for (int j = 0; j < size; j++) {
                    FileStorageFolder fsFolder = rootFolders[j];
                    String id = fsFolder.getId();
                    if (id.length() != 0) {
                        FolderID fid = new FolderID(id);
                        if (!INFOSTORE_ACCOUNT_ID.equals(fid.getAccountId())) {
                            String accountID = FileStorageAccounts.getQualifiedID(fid.getService(), fid.getAccountId());
                            FileStorageFolderImpl folder;
                            if (session == null) {
                                folder = new FileStorageFolderImpl(fsFolder, accountID, storageParameters.getUserId(), storageParameters.getContextId(), altNames, folderAccess);
                            } else {
                                folder = new FileStorageFolderImpl(fsFolder, accountID, session, altNames, folderAccess);
                            }
                            folder.setTreeID(treeId);
                            list.add(folder);
                        }
                    }
                }
            } else {
                for (int j = 0; j < size; j++) {
                    FileStorageFolder fsFolder = rootFolders[j];
                    FolderID fid = new FolderID(fsFolder.getId());
                    String accountID = FileStorageAccounts.getQualifiedID(fid.getService(), fid.getAccountId());
                    FileStorageFolderImpl folder;
                    if (session == null) {
                        folder = new FileStorageFolderImpl(fsFolder, accountID, storageParameters.getUserId(), storageParameters.getContextId(), altNames, folderAccess);
                    } else {
                        folder = new FileStorageFolderImpl(fsFolder, accountID, session, altNames, folderAccess);
                    }
                    folder.setTreeID(treeId);
                    list.add(folder);
                }
            }
            return list.toArray(new Folder[list.size()]);
        }

        // Get subfolders
        List<FileStorageFolder> children = Arrays.asList(folderAccess.getSubfolders(parentId, true));

        // Sort
        Collections.sort(children, new SimpleFileStorageFolderComparator(storageParameters.getUser().getLocale()));

        // Return listing
        boolean altNames = StorageParametersUtility.getBoolParameter(ALT_NAMES, storageParameters);
        List<Folder> list = new ArrayList<>(children.size());
        int size = children.size();
        for (int j = 0; j < size; j++) {
            FileStorageFolder fsFolder = children.get(j);
            FolderID fid = new FolderID(fsFolder.getId());
            String accountID = FileStorageAccounts.getQualifiedID(fid.getService(), fid.getAccountId());
            FileStorageFolderImpl folder;
            if (session == null) {
                folder = new FileStorageFolderImpl(fsFolder, accountID, storageParameters.getUserId(), storageParameters.getContextId(), altNames, folderAccess);
            } else {
                folder = new FileStorageFolderImpl(fsFolder, accountID, session, altNames, folderAccess);
            }
            folder.setTreeID(treeId);
            list.add(folder);
        }
        return list.toArray(new Folder[list.size()]);
    }

    @Override
    public StoragePriority getStoragePriority() {
        return StoragePriority.NORMAL;
    }

    @Override
    public boolean containsFolder(final String treeId, final String folderId, final StorageParameters storageParameters) throws OXException {
        return containsFolder(treeId, folderId, StorageType.WORKING, storageParameters);
    }

    @Override
    public boolean containsFolder(final String treeId, final String folderId, final StorageType storageType, final StorageParameters storageParameters) throws OXException {
        if (StorageType.BACKUP.equals(storageType)) {
            return false;
        }
        final IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);

        return folderAccess.exists(folderId);
    }

    @Override
    public String[] getDeletedFolderIDs(final String treeId, final Date timeStamp, final StorageParameters storageParameters) throws OXException {
        return new String[0];
    }

    @Override
    public String[] getModifiedFolderIDs(final String treeId, final Date timeStamp, final ContentType[] includeContentTypes, final StorageParameters storageParameters) throws OXException {
        if (null == includeContentTypes || includeContentTypes.length == 0) {
            return new String[0];
        }
        final List<String> ret = new ArrayList<>();
        final Set<ContentType> supported = new HashSet<>(Arrays.asList(getSupportedContentTypes()));
        for (final ContentType includeContentType : includeContentTypes) {
            if (supported.contains(includeContentType)) {
                final SortableId[] subfolders = getSubfolders(FolderStorage.REAL_TREE_ID, PRIVATE_FOLDER_ID, storageParameters);
                for (final SortableId sortableId : subfolders) {
                    ret.add(sortableId.getId());
                }
            }
        }
        return ret.toArray(new String[ret.size()]);
    }

    private FileStorageFolder getFolder(Reference<FileStorageFolder> folderReference, String folderId, IDBasedFolderAccess folderAccess) throws OXException {
        FileStorageFolder folder = folderReference.getValue();
        if (null == folder) {
            folder = folderAccess.getFolder(folderId);
            folderReference.setValue(folder);
        }
        return folder;
    }

    @Override
    public void updateFolder(Folder folder, StorageParameters storageParameters) throws OXException {
        try {
            /*
             * convert supplied folder & determine required updates in storage
             */
            IDBasedFolderAccess folderAccess = getFolderAccess(storageParameters);
            DefaultFileStorageFolder folderToUpdate = getFileStorageFolder(folder);
            boolean move;
            boolean rename;
            boolean permissions;
            boolean subscribed;
            {
                Reference<FileStorageFolder> originalFolder = new Reference<>(null);
                UpdateOperation updateOperation = UpdateOperation.optUpdateOperation(storageParameters);
                if (null != updateOperation) {
                    move = UpdateOperation.MOVE == updateOperation;
                    rename = UpdateOperation.RENAME == updateOperation;
                } else {
                    move = null != folderToUpdate.getParentId() && false == getFolder(originalFolder, folder.getID(), folderAccess).getParentId().equals(folderToUpdate.getParentId());
                    rename = null != folderToUpdate.getName() && false == getFolder(originalFolder, folder.getID(), folderAccess).getName().equals(folderToUpdate.getName());
                }
                permissions = null != folderToUpdate.getPermissions() && !folderToUpdate.getPermissions().isEmpty() && false == folderToUpdate.getPermissions().equals(getFolder(originalFolder, folder.getID(), folderAccess).getPermissions());
                subscribed = SetterAwareFolder.class.isInstance(folder) && ((SetterAwareFolder) folder).containsSubscribed() &&
                    folder.isSubscribed() != getFolder(originalFolder, folder.getID(), folderAccess).isSubscribed();
            }
            /*
             * perform move and/or rename operation
             */
            if (move) {
                boolean ignoreWarnings = StorageParametersUtility.getBoolParameter("ignoreWarnings", storageParameters);
                String newName = rename ? folderToUpdate.getName() : null;
                String newID = folderAccess.moveFolder(folderToUpdate.getId(), folderToUpdate.getParentId(), newName, ignoreWarnings);
                folderToUpdate.setId(newID);
            } else if (rename) {
                String newID = folderAccess.renameFolder(folderToUpdate.getId(), folderToUpdate.getName());
                folderToUpdate.setId(newID);
            }
            /*
             * update permissions / subscribed separately if needed
             */
            if (permissions || subscribed) {
                boolean ignoreWarnings = StorageParametersUtility.getBoolParameter("ignoreWarnings", storageParameters);
                String newID = folderAccess.updateFolder(folderToUpdate.getId(), folderToUpdate, false, ignoreWarnings);
                folderToUpdate.setId(newID);
                if (StorageParametersUtility.isHandDownPermissions(storageParameters)) {
                    handDown(folderToUpdate.getId(), folderToUpdate.getPermissions(), folderAccess);
                }
            }
            /*
             * take over updated identifiers in passed folder reference
             */
            folder.setID(folderToUpdate.getId());
            folder.setParentID(folderToUpdate.getParentId());
            folder.setLastModified(folderToUpdate.getLastModifiedDate());
        } catch (OXException e) {
            if (FileStorageExceptionCodes.DUPLICATE_FOLDER.equals(e)) {
                Throwable cause = e.getCause();
                Object[] args = e.getLogArgs();
                throw FolderExceptionErrorMessage.EQUAL_NAME.create(cause, args[0], args[1], folder.getTreeID());
            }
            throw e;
        }
    }

    private static void handDown(final String parentId, final List<FileStoragePermission> fsPermissions, final IDBasedFolderAccess folderAccess) throws OXException {
        final FileStorageFolder[] subfolders = folderAccess.getSubfolders(parentId, true);
        for (final FileStorageFolder subfolder : subfolders) {
            final DefaultFileStorageFolder fsFolder = new DefaultFileStorageFolder();
            fsFolder.setExists(true);
            // Full name
            final String id = subfolder.getId();
            fsFolder.setId(id);
            fsFolder.setPermissions(fsPermissions);
            folderAccess.updateFolder(id, fsFolder);
            // Recursive
            handDown(id, fsPermissions, folderAccess);
        }
    }

    /**
     * Gets the ID based folder access reference from the supplied storage parameters, throwing an appropriate exception in case it is
     * absent.
     *
     * @param storageParameters The storage parameters to get the folder access from
     * @return The folder access
     */
    private static IDBasedFolderAccess getFolderAccess(StorageParameters storageParameters) throws OXException {
        IDBasedFolderAccess folderAccess = storageParameters.getParameter(FileStorageFolderType.getInstance(), PARAM);
        if (null == folderAccess) {
            throw FolderExceptionErrorMessage.MISSING_PARAMETER.create(PARAM);
        }
        return folderAccess;
    }

    private static final class SimpleFileStorageFolderComparator implements Comparator<FileStorageFolder> {

        private final Collator collator;

        SimpleFileStorageFolderComparator(final Locale locale) {
            super();
            collator = Collators.getSecondaryInstance(locale);
        }

        @Override
        public int compare(final FileStorageFolder o1, final FileStorageFolder o2) {
            return collator.compare(o1.getName(), o2.getName());
        }
    } // End of SimpleFileStorageFolderComparator

    /**
     * Create a file storage folder equivalent to the supplied folder.
     *
     * @param folder the folder to create the file storage folder for
     * @return The file storage folder
     */
    private static DefaultFileStorageFolder getFileStorageFolder(Folder folder) {
        DefaultFileStorageFolder fileStorageFolder = new DefaultFileStorageFolder();
        fileStorageFolder.setExists(true);
        fileStorageFolder.setId(folder.getID());
        fileStorageFolder.setParentId(folder.getParentID());
        fileStorageFolder.setName(folder.getName());
        if (false == SetterAwareFolder.class.isInstance(folder) || ((SetterAwareFolder) folder).containsSubscribed()) {
            fileStorageFolder.setSubscribed(folder.isSubscribed());
        }
        fileStorageFolder.setPermissions(FileStorageUtils.getFileStoragePermissions(folder.getPermissions()));
        fileStorageFolder.setCreatedBy(folder.getCreatedBy());
        fileStorageFolder.setModifiedBy(folder.getModifiedBy());
        return fileStorageFolder;
    }

    /**
     * Adds any present warnings from the supplied {@link WarningsAware} reference to the storage parameters warnings list.
     *
     * @param storageParameters The storage parameters to add the warnings to
     * @param warningsAware The warnings aware to get and flush the warnings from
     */
    private static void addWarnings(StorageParameters storageParameters, WarningsAware warningsAware) {
        List<OXException> list = warningsAware.getAndFlushWarnings();
        if (null != list && !list.isEmpty()) {
            for (OXException warning : list) {
                storageParameters.addWarning(warning);
            }
        }
    }

}
