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

package com.openexchange.file.storage.dropbox.access;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.files.CreateFolderError;
import com.dropbox.core.v2.files.CreateFolderErrorException;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderContinueErrorException;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.LookupError;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.RelocationError;
import com.dropbox.core.v2.files.RelocationErrorException;
import com.dropbox.core.v2.files.WriteError;
import com.dropbox.core.v2.users.IndividualSpaceAllocation;
import com.dropbox.core.v2.users.SpaceAllocation;
import com.dropbox.core.v2.users.SpaceUsage;
import com.dropbox.core.v2.users.TeamSpaceAllocation;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAutoRenameFoldersAccess;
import com.openexchange.file.storage.FileStorageCaseInsensitiveAccess;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.NameBuilder;
import com.openexchange.file.storage.PathKnowingFileStorageFolderAccess;
import com.openexchange.file.storage.Quota;
import com.openexchange.file.storage.Quota.Type;
import com.openexchange.file.storage.UserCreatedFileStorageFolderAccess;
import com.openexchange.file.storage.dropbox.DropboxServices;
import com.openexchange.oauth.access.AbstractOAuthAccess;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;

/**
 * {@link DropboxFolderAccess}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class DropboxFolderAccess extends AbstractDropboxAccess implements FileStorageFolderAccess, FileStorageCaseInsensitiveAccess, FileStorageAutoRenameFoldersAccess, PathKnowingFileStorageFolderAccess, UserCreatedFileStorageFolderAccess {

    private static final Logger LOG = LoggerFactory.getLogger(DropboxFolderAccess.class);

    private final int userId;
    private final String accountDisplayName;
    private final boolean useOptimisticSubfolderDetection;

    /**
     * Initialises a new {@link DropboxFolderAccess}.
     *
     * @throws OXException
     */
    public DropboxFolderAccess(AbstractOAuthAccess dropboxOAuthAccess, FileStorageAccount account, Session session) throws OXException {
        super(dropboxOAuthAccess, account, session);
        userId = session.getUserId();
        accountDisplayName = account.getDisplayName();
        ConfigViewFactory viewFactory = DropboxServices.getOptionalService(ConfigViewFactory.class);
        if (viewFactory == null) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }
        ConfigView view = viewFactory.getView(session.getUserId(), session.getContextId());
        useOptimisticSubfolderDetection = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.file.storage.dropbox.useOptimisticSubfolderDetection", true, view);
    }

    @Override
    public boolean exists(String folderId) throws OXException {
        try {
            // The Dropbox V2 API does not allow to fetch metadata for the root folder,
            // thus we assume that it always exists
            if (isRoot(folderId)) {
                return true;
            }
            Metadata metadata = getMetadata(folderId);
            return metadata instanceof FolderMetadata;
        } catch (GetMetadataErrorException e) {
            if (LookupError.NOT_FOUND.equals(e.errorValue.getPathValue())) {
                return false;
            }
            throw DropboxExceptionHandler.handleGetMetadataErrorException(e, folderId, "");
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public FileStorageFolder getFolder(String folderId) throws OXException {
        try {
            if (isRoot(folderId)) {
                return getRootFolder();
            }

            FolderMetadata metadata = getFolderMetadata(folderId);
            // The '/get_metadata' Dropbox V2 API call does not return a hint to indicate if a folder has sub-folders,
            // thus we have to initiate an extra 'listFolder' call and check for sub folders.
            // More information: https://www.dropbox.com/developers/documentation/http/documentation#files-get_metadata
            boolean hasSubfolders = hasSubFolders(folderId);
            // Parse metadata
            return new DropboxFolder(metadata, userId, accountDisplayName, hasSubfolders);
        } catch (ListFolderErrorException e) {
            throw DropboxExceptionHandler.handleListFolderErrorException(e, folderId);
        } catch (GetMetadataErrorException e) {
            throw DropboxExceptionHandler.handleGetMetadataErrorException(e, folderId, "");
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public FileStorageFolder getPersonalFolder() throws OXException {
        throw FileStorageExceptionCodes.NO_SUCH_FOLDER.create();
    }

    @Override
    public FileStorageFolder getTrashFolder() throws OXException {
        throw FileStorageExceptionCodes.NO_SUCH_FOLDER.create();
    }

    @Override
    public FileStorageFolder[] getPublicFolders() throws OXException {
        return new FileStorageFolder[0];
    }

    @Override
    public FileStorageFolder[] getSubfolders(String parentIdentifier, boolean all) throws OXException {
        try {
            List<Metadata> entries = listFolder(parentIdentifier);

            List<FileStorageFolder> folders = new ArrayList<>(entries.size());
            for (Metadata entry : entries) {
                if (entry instanceof FolderMetadata) {
                    FolderMetadata folderMetadata = (FolderMetadata) entry;
                    folders.add(new DropboxFolder(folderMetadata, userId, accountDisplayName, hasSubFolders(folderMetadata.getPathDisplay())));
                }
            }

            return folders.toArray(new FileStorageFolder[folders.size()]);
        } catch (ListFolderContinueErrorException e) {
            throw DropboxExceptionHandler.handleListFolderContinueErrorException(e, parentIdentifier);
        } catch (ListFolderErrorException e) {
            throw DropboxExceptionHandler.handleListFolderErrorException(e, parentIdentifier);
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public FileStorageFolder[] getUserSharedFolders() throws OXException {
        return new FileStorageFolder[0];
    }

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        DropboxFolder rootFolder = new DropboxFolder(userId);
        rootFolder.setRootFolder(true);
        rootFolder.setId(FileStorageFolder.ROOT_FULLNAME);
        rootFolder.setParentId(null);
        rootFolder.setName(accountDisplayName);
        rootFolder.setSubfolders(true);
        rootFolder.setSubscribedSubfolders(true);
        return rootFolder;
    }

    @Override
    public String createFolder(FileStorageFolder toCreate) throws OXException {
        return createFolder(toCreate, true);
    }

    @Override
    public String createFolder(FileStorageFolder toCreate, boolean autoRename) throws OXException {
        String parentId = toCreate.getParentId();

        if (false == autoRename) {
            String fullpath = constructPath(parentId, toCreate.getName());
            try {
                FolderMetadata folderMetadata = client.files().createFolderV2(fullpath).getMetadata();
                return folderMetadata.getPathDisplay();
            } catch (CreateFolderErrorException e) {
                CreateFolderError error = e.errorValue;
                if (CreateFolderError.Tag.PATH == error.tag()) {
                    if (WriteError.Tag.CONFLICT == error.getPathValue().tag()) {
                        String parentName = "/".equals(parentId) ? accountDisplayName : parentId.substring(parentId.lastIndexOf('/') + 1);
                        throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(e, toCreate.getName(), parentName);
                    }
                }

                throw DropboxExceptionHandler.handleCreateFolderErrorException(e, fullpath, accountDisplayName);
            } catch (DbxException e) {
                throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
            }
        }

        String baseName = toCreate.getName();
        String fullpath = constructPath(parentId, baseName);

        NameBuilder name = null;
        while (true) {
            try {
                FolderMetadata folderMetadata = client.files().createFolderV2(fullpath).getMetadata();
                return folderMetadata.getPathDisplay();
            } catch (CreateFolderErrorException e) {
                CreateFolderError error = e.errorValue;
                if (CreateFolderError.Tag.PATH != error.tag()) {
                    throw DropboxExceptionHandler.handleCreateFolderErrorException(e, fullpath, accountDisplayName);
                }

                if (WriteError.Tag.CONFLICT != error.getPathValue().tag()) {
                    throw DropboxExceptionHandler.handleCreateFolderErrorException(e, fullpath, accountDisplayName);
                }

                // Compile a new name and retry...
                if (null == name) {
                    name = new NameBuilder(baseName);
                }
                fullpath = constructPath(parentId, name.advance().toString());
            } catch (DbxException e) {
                throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
            }
        }
    }

    @Override
    public String updateFolder(String identifier, FileStorageFolder toUpdate) throws OXException {
        return identifier;
    }

    @Override
    public String moveFolder(String folderId, String newParentId) throws OXException {
        return moveFolder(folderId, newParentId, null);
    }

    @Override
    public String moveFolder(String folderId, String newParentId, String newName) throws OXException {
        return moveFolder(folderId, newParentId, newName, true);
    }

    @Override
    public String moveFolder(String folderId, String newParentId, String newName, boolean autoRename) throws OXException {
        if (newName == null) {
            int lastIndex = folderId.lastIndexOf('/');
            newName = folderId.substring(lastIndex);
        }

        if (false == autoRename) {
            try {
                Metadata metadata = client.files().moveV2Builder(folderId, newParentId).start().getMetadata();
                return metadata.getPathDisplay();
            } catch (RelocationErrorException e) {
                RelocationError relocationError = e.errorValue;
                if (RelocationError.Tag.TO == relocationError.tag()) {
                    WriteError error = relocationError.getToValue();
                    if (WriteError.Tag.CONFLICT == error.tag()) {
                        String parentName = "/".equals(newParentId) ? accountDisplayName : newParentId.substring(newParentId.lastIndexOf('/') + 1);
                        throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(e, newName, parentName);
                    }
                }

                throw DropboxExceptionHandler.handleRelocationErrorException(e, folderId, "", accountDisplayName);
            } catch (DbxException e) {
                throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
            }
        }


        String baseName = newName;
        NameBuilder name = new NameBuilder(baseName);
        while (true) {
            try {
                String toPath = newParentId + name.toString();
                Metadata metadata = client.files().moveV2Builder(folderId, toPath).start().getMetadata();
                return metadata.getPathDisplay();
            } catch (RelocationErrorException e) {
                RelocationError relocationError = e.errorValue;
                if (RelocationError.Tag.TO != relocationError.tag()) {
                    throw DropboxExceptionHandler.handleRelocationErrorException(e, folderId, "", accountDisplayName);
                }

                WriteError error = relocationError.getToValue();
                if (WriteError.Tag.CONFLICT != error.tag()) {
                    throw DropboxExceptionHandler.handleRelocationErrorException(e, folderId, "", accountDisplayName);
                }

                // Compile a new name and retry...
                name.advance();
            } catch (DbxException e) {
                throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
            }
        }
    }

    @Override
    public String renameFolder(String folderId, String newName) throws OXException {
        int lastIndex = folderId.lastIndexOf('/');
        String parentId = folderId.substring(0, lastIndex + 1);
        try {
            String newPath = parentId + newName;
            Metadata metadata = client.files().moveV2Builder(folderId, newPath).start().getMetadata();
            return metadata.getPathDisplay();
        } catch (RelocationErrorException e) {
            RelocationError relocationError = e.errorValue;
            if (RelocationError.Tag.TO == relocationError.tag()) {
                WriteError error = relocationError.getToValue();
                if (WriteError.Tag.CONFLICT == error.tag()) {
                    String parentName = "/".equals(parentId) ? accountDisplayName : parentId.substring(parentId.lastIndexOf('/') + 1);
                    throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(e, newName, parentName);
                }
            }

            throw DropboxExceptionHandler.handleRelocationErrorException(e, folderId, "", accountDisplayName);
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public String deleteFolder(String folderId) throws OXException {
        try {
            Metadata metadata = client.files().deleteV2(folderId).getMetadata();
            return metadata.getName();
        } catch (DeleteErrorException e) {
            throw DropboxExceptionHandler.handleDeleteErrorException(e, folderId, "", accountDisplayName);
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public String deleteFolder(String folderId, boolean hardDelete) throws OXException {
        return deleteFolder(folderId);
    }

    @Override
    public void clearFolder(String folderId) throws OXException {
        try {
            List<Metadata> entries = listFolder(folderId);
            for (Metadata entry : entries) {
                if (entry instanceof FolderMetadata) {
                    try {
                        client.files().deleteV2(entry.getPathDisplay());
                    } catch (DeleteErrorException e) {
                        LOG.debug("The folder '{}' could not be deleted. Skipping.", entry.getPathDisplay(), e);
                    }
                }
            }
        } catch (ListFolderErrorException e) {
            throw DropboxExceptionHandler.handleListFolderErrorException(e, folderId);
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }

    }

    @Override
    public void clearFolder(String folderId, boolean hardDelete) throws OXException {
        clearFolder(folderId);
    }

    @Override
    public FileStorageFolder[] getPath2DefaultFolder(String folderId) throws OXException {
        String parentId = folderId;
        FileStorageFolder folder;
        List<FileStorageFolder> folders = new ArrayList<>();
        do {
            folder = getFolder(parentId);
            folders.add(folder);
        } while ((parentId = folder.getParentId()) != null);

        return folders.toArray(new FileStorageFolder[folders.size()]);
    }

    @Override
    public String[] getPathIds2DefaultFolder(String folderId) throws OXException {
        List<String> path = new LinkedList<>();
        String parentId = folderId;
        path.add(parentId);
        for (int index; (index = parentId.lastIndexOf('/')) >= 0;) {
            parentId = parentId.substring(0, index);
            path.add(parentId);
        }
        return path.toArray(new String[path.size()]);
    }

    @Override
    public Quota getStorageQuota(String folderId) throws OXException {
        try {
            SpaceUsage spaceUsage = client.users().getSpaceUsage();
            SpaceAllocation allocation = spaceUsage.getAllocation();

            IndividualSpaceAllocation individualValue = allocation.getIndividualValue();

            if (allocation.isTeam()) {
                TeamSpaceAllocation teamValue = allocation.getTeamValue();
                return new Quota(individualValue.getAllocated() + teamValue.getAllocated(), spaceUsage.getUsed(), Type.STORAGE);
            }

            return new Quota(individualValue.getAllocated(), spaceUsage.getUsed(), Type.STORAGE);
        } catch (DbxException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        } catch (IllegalStateException e) {
            throw DropboxExceptionHandler.handle(e, session, dropboxOAuthAccess.getOAuthAccount());
        }
    }

    @Override
    public Quota getFileQuota(String folderId) throws OXException {
        return Type.FILE.getUnlimited();
    }

    @Override
    public Quota[] getQuotas(String folder, Type[] types) throws OXException {
        if (null == types) {
            return null;
        }
        Quota[] quotas = new Quota[types.length];
        for (int i = 0; i < types.length; i++) {
            switch (types[i]) {
                case FILE:
                    quotas[i] = getFileQuota(folder);
                    break;
                case STORAGE:
                    quotas[i] = getStorageQuota(folder);
                    break;
                default:
                    throw FileStorageExceptionCodes.OPERATION_NOT_SUPPORTED.create("Quota " + types[i]);
            }
        }
        return quotas;
    }

    ///////////////////////////////////////////// HELPERS //////////////////////////////////////////////

    /**
     * Check for sub folders
     *
     * @param folderId The folder to check for sub folders
     * @return <code>true</code> if at least one entry of the specified folder is of type {@link FolderMetadata}; <code>false</code> otherwise
     * @throws ListFolderErrorException If a list folder error is occurred
     * @throws DbxException if a generic Dropbox error is occurred
     */
    private boolean hasSubFolders(String folderId) throws ListFolderErrorException, DbxException {
        if (useOptimisticSubfolderDetection) {
            return true;
        }

        ListFolderResult listResult = client.files().listFolder(folderId);
        boolean hasMore;
        do {
            hasMore = listResult.getHasMore();

            List<Metadata> entries = listResult.getEntries();
            for (Metadata metadata : entries) {
                if (metadata instanceof FolderMetadata) {
                    return true;
                }
            }

            if (hasMore) {
                String cursor = listResult.getCursor();
                listResult = client.files().listFolderContinue(cursor);
            }
        } while (hasMore);
        return false;
    }

    /**
     * Construct a full path from the specified parent and folder name.
     * It simply concatenates both strings by using the '/' path separator.
     *
     * @param parent The parent folder
     * @param folder The folder name
     * @return The full path
     */
    private String constructPath(String parent, String folder) {
        if (isRoot(parent)) {
            parent = "/";
        }

        //Strip leading '/'
        if (folder.startsWith("/")) {
            folder = folder.substring(1);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(parent);
        if (!parent.endsWith("/")) {
            builder.append("/");
        }
        builder.append(folder);
        return builder.toString();
    }
}
