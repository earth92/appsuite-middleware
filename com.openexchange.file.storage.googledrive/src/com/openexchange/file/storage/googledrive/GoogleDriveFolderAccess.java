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

package com.openexchange.file.storage.googledrive;

import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.QUERY_STRING_FILES_ONLY;
import static com.openexchange.java.Autoboxing.l;
import static com.openexchange.java.Strings.isEmpty;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.openexchange.annotation.NonNull;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAutoRenameFoldersAccess;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.NameBuilder;
import com.openexchange.file.storage.Quota;
import com.openexchange.file.storage.Quota.Type;
import com.openexchange.file.storage.UserCreatedFileStorageFolderAccess;
import com.openexchange.file.storage.googledrive.access.GoogleDriveOAuthAccess;
import com.openexchange.file.storage.googledrive.osgi.Services;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;

/**
 * {@link GoogleDriveFolderAccess}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class GoogleDriveFolderAccess extends AbstractGoogleDriveAccess implements FileStorageFolderAccess, FileStorageAutoRenameFoldersAccess, UserCreatedFileStorageFolderAccess {

    private final int     userId;
    private final String  accountDisplayName;
    private final boolean useOptimisticSubfolderDetection;

    /**
     * Initializes a new {@link GoogleDriveFolderAccess}.
     */
    public GoogleDriveFolderAccess(final @NonNull GoogleDriveOAuthAccess googleDriveAccess, final @NonNull FileStorageAccount account, final @NonNull Session session) throws OXException {
        super(googleDriveAccess, account, session);
        userId = session.getUserId();
        accountDisplayName = account.getDisplayName();
        ConfigViewFactory viewFactory = Services.optService(ConfigViewFactory.class);
        if (null == viewFactory) {
            throw ServiceExceptionCode.absentService(ConfigViewFactory.class);
        }
        ConfigView view = viewFactory.getView(session.getUserId(), session.getContextId());
        useOptimisticSubfolderDetection = ConfigViews.getDefinedBoolPropertyFrom("com.openexchange.file.storage.googledrive.useOptimisticSubfolderDetection", true, view);

    }

    @Override
    public boolean exists(String folderId) throws OXException {
        return new BackOffPerformer<Boolean>(googleDriveAccess, account, session) {

            @Override
            Boolean perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                com.google.api.services.drive.model.File file = drive.files().get(toGoogleDriveFolderId(folderId)).execute();
                Boolean explicitlyTrashed = file.getExplicitlyTrashed();
                return Boolean.valueOf(isDir(file) && (null == explicitlyTrashed || !explicitlyTrashed.booleanValue()));
            }
        }.perform(null).booleanValue();
    }

    @Override
    public FileStorageFolder getFolder(String folderId) throws OXException {
        return new BackOffPerformer<FileStorageFolder>(googleDriveAccess, account, session) {

            @Override
            FileStorageFolder perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                com.google.api.services.drive.model.File dir = drive.files().get(toGoogleDriveFolderId(folderId)).setFields(GoogleDriveConstants.FIELDS_DEFAULT).execute();
                checkDirValidity(dir);
                return parseGoogleDriveFolder(dir, drive);
            }
        }.perform(folderId);
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
    public FileStorageFolder[] getUserSharedFolders() throws OXException {
        return new FileStorageFolder[0];
    }

    @Override
    public FileStorageFolder[] getSubfolders(final String parentIdentifier, final boolean all) throws OXException {
        return new BackOffPerformer<FileStorageFolder[]>(googleDriveAccess, account, session) {

            @Override
            FileStorageFolder[] perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).searchForChildren(toGoogleDriveFolderId(parentIdentifier)).build());
                FileList childList = list.execute();

                if (childList.getFiles().isEmpty()) {
                    return new FileStorageFolder[0];
                }

                List<FileStorageFolder> folders = new LinkedList<FileStorageFolder>();
                for (File childReference : childList.getFiles()) {
                    folders.add(parseGoogleDriveFolder(drive.files().get(childReference.getId()).setFields(GoogleDriveConstants.FIELDS_DEFAULT).execute(), drive));
                }

                String nextPageToken = childList.getNextPageToken();
                while (!isEmpty(nextPageToken)) {
                    list.setPageToken(nextPageToken);
                    childList = list.execute();
                    if (!childList.getFiles().isEmpty()) {
                        for (File childReference : childList.getFiles()) {
                            folders.add(parseGoogleDriveFolder(drive.files().get(childReference.getId()).execute(), drive));
                        }
                    }

                    nextPageToken = childList.getNextPageToken();
                }

                return folders.toArray(new FileStorageFolder[0]);
            }
        }.perform(parentIdentifier);
    }

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        GoogleDriveFolder rootFolder = new GoogleDriveFolder(userId);
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
        return new BackOffPerformer<String>(googleDriveAccess, account, session) {

            @Override
            String perform() throws OXException, IOException, RuntimeException {
                String parentId = toGoogleDriveFolderId(toCreate.getParentId());
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                String baseName = toCreate.getName();

                NameBuilder name = new NameBuilder(baseName);
                if (autoRename) {
                    // Duplicate name needs to be explicitly checked since Google Drive allows multiple folders with the same name next to each other
                    {
                        com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                        list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).searchForChildren(parentId).build());
                        List<File> existingFolders = list.execute().getFiles();
                        if (!existingFolders.isEmpty()) {
                            // Check if there is already such a folder
                            boolean alreadySuchAFolder;
                            do {
                                alreadySuchAFolder = false;
                                String nameToCheck = name.toString();
                                for (File childReference : existingFolders) {
                                    if (drive.files().get(childReference.getId()).execute().getName().equals(nameToCheck)) {
                                        name.advance();
                                        alreadySuchAFolder = true;
                                        break;
                                    }
                                }
                            } while (alreadySuchAFolder);
                        }
                    }
                } else {
                    String nameToCheck = name.toString();
                    com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                    list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).equalsName(nameToCheck).searchForChildren(parentId).build());
                    List<File> existingFolders = list.execute().getFiles();
                    if (!existingFolders.isEmpty()) {
                        // Check if there is already such a folder
                        for (File childReference : existingFolders) {
                            if (drive.files().get(childReference.getId()).execute().getName().equals(nameToCheck)) {
                                throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(nameToCheck, drive.files().get(parentId).execute().getName());
                            }
                        }
                    }
                }

                File driveDir = new File();
                GoogleDriveUtil.setParentFolder(driveDir, (parentId));
                driveDir.setName(name.toString());
                driveDir.setMimeType(GoogleDriveConstants.MIME_TYPE_DIRECTORY);

                File newDir = drive.files().create(driveDir).execute();
                return toFileStorageFolderId(newDir.getId());
            }
        }.perform(toCreate.getParentId());
    }

    @Override
    public String updateFolder(String identifier, FileStorageFolder toUpdate) throws OXException {
        // Neither support for subscription nor permissions
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
        return new BackOffPerformer<String>(googleDriveAccess, account, session) {

            @Override
            String perform() throws OXException, IOException, RuntimeException {
                String fid = toGoogleDriveFolderId(folderId);
                String nfid = toGoogleDriveFolderId(newParentId);
                Drive drive = googleDriveAccess.<Drive> getClient().client;

                File fileToMove = drive.files().get(fid).setFields(GoogleDriveConstants.FIELDS_DEFAULT).execute();
                String baseName = null == newName ? fileToMove.getName() : newName;

                // Duplicate name needs to be explicitly checked since Google Drive allows multiple folders with the same name next to each other
                NameBuilder name = new NameBuilder(baseName);
                if (autoRename) {
                    com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                    list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).searchForChildren(nfid).build());
                    List<File> existingFolders = list.execute().getFiles();
                    if (!existingFolders.isEmpty()) {
                        // Check if there is already such a folder
                        boolean alreadySuchAFolder;
                        do {
                            alreadySuchAFolder = false;
                            String nameToCheck = name.toString();
                            for (File childReference : existingFolders) {
                                if (drive.files().get(childReference.getId()).execute().getName().equals(nameToCheck)) {
                                    name.advance();
                                    alreadySuchAFolder = true;
                                    break;
                                }
                            }
                        } while (alreadySuchAFolder);
                    }
                } else {
                    String nameToCheck = name.toString();
                    com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                    list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).equalsName(nameToCheck).searchForChildren(nfid).build());
                    List<File> existingFolders = list.execute().getFiles();
                    if (!existingFolders.isEmpty()) {
                        // Check if there is already such a folder
                        for (File childReference : existingFolders) {
                            if (drive.files().get(childReference.getId()).execute().getName().equals(nameToCheck)) {
                                throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(nameToCheck, drive.files().get(nfid).execute().getName());
                            }
                        }
                    }
                }

                File movedDir = drive.files().update(fid, null).setAddParents(nfid).setRemoveParents(GoogleDriveUtil.getParentFolders(fileToMove)).setFields("id, parents").execute();
                return toFileStorageFolderId(movedDir.getId());
            }
        }.perform(folderId);
    }

    @Override
    public String renameFolder(String folderId, String newName) throws OXException {
        return new BackOffPerformer<String>(googleDriveAccess, account, session) {

            @Override
            String perform() throws OXException, IOException, RuntimeException {
                String fid = toGoogleDriveFolderId(folderId);
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                /*
                 * get folder to rename
                 */
                File folder = drive.files().get(fid).setFields("parents,id,name").execute();
                if (newName.equals(folder.getName())) {
                    return folderId;
                }
                /*
                 * check for name conflict below parent folder
                 */
                List<String> parentReferences = folder.getParents();
                for (String parentReference : parentReferences) {
                    com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                    list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_DIRECTORIES_ONLY_EXCLUDING_TRASH).equalsName(newName).searchForChildren(parentReference).build());
                    List<File> existingFolders = list.execute().getFiles();
                    if (!existingFolders.isEmpty()) {
                        // Check if there is already such a folder
                        for (File childReference : existingFolders) {
                            if (drive.files().get(childReference.getId()).execute().getName().equals(newName)) {
                                throw FileStorageExceptionCodes.DUPLICATE_FOLDER.create(newName, drive.files().get(parentReference).execute().getName());
                            }
                        }
                    }
                }
                /*
                 * perform rename
                 */
                File driveDir = new File().setName(newName);
                File renamedDir = drive.files().update(fid, driveDir).execute();
                return toFileStorageFolderId(renamedDir.getId());
            }
        }.perform(folderId);
    }

    @Override
    public String deleteFolder(String folderId) throws OXException {
        return deleteFolder(folderId, false);
    }

    @Override
    public String deleteFolder(String folderId, boolean hardDelete) throws OXException {
        return new BackOffPerformer<String>(googleDriveAccess, account, session) {

            @Override
            String perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;

                String fid = toGoogleDriveFolderId(folderId);
                if (hardDelete || isTrashed(fid, drive)) {
                    drive.files().delete(fid).execute();
                } else {
                    trashFile(drive, fid);
                }

                return folderId;
            }
        }.perform(folderId);
    }

    @Override
    public void clearFolder(String folderId) throws OXException {
        clearFolder(folderId, false);
    }

    @Override
    public void clearFolder(String folderId, boolean hardDelete) throws OXException {
        new BackOffPerformer<Void>(googleDriveAccess, account, session) {

            @Override
            Void perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                /*
                 * build request to list all files in a folder
                 */
                String fid = toGoogleDriveFolderId(folderId);
                boolean deletePermanently = hardDelete || isTrashed(fid, drive);
                com.google.api.services.drive.Drive.Files.List listRequest = drive.files().list().setQ(new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY).searchForChildren(fid).build()).setFields("nextPageToken,files(id)");

                /*
                 * execute as often as needed & delete files
                 */
                FileList childList;
                do {
                    childList = listRequest.execute();
                    for (File child : childList.getFiles()) {
                        if (deletePermanently) {
                            drive.files().delete(child.getId()).execute();
                        } else {
                            trashFile(drive, child.getId());
                        }
                    }
                    listRequest.setPageToken(childList.getNextPageToken());
                } while (null != childList.getNextPageToken());
                return null;
            }
        }.perform(folderId);
    }

    @Override
    public FileStorageFolder[] getPath2DefaultFolder(String folderId) throws OXException {
        return new BackOffPerformer<FileStorageFolder[]>(googleDriveAccess, account, session) {

            @Override
            FileStorageFolder[] perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;

                List<FileStorageFolder> list = new LinkedList<FileStorageFolder>();

                String fid = toGoogleDriveFolderId(folderId);
                File dir = drive.files().get(fid).setFields(GoogleDriveConstants.FIELDS_DEFAULT).execute();
                FileStorageFolder f = parseGoogleDriveFolder(dir, drive);
                list.add(f);

                String rootFolderId = getRootFolderId();
                while (!rootFolderId.equals(fid)) {
                    fid = dir.getParents().get(0);
                    dir = drive.files().get(fid).setFields(GoogleDriveConstants.FIELDS_DEFAULT).execute();
                    f = parseGoogleDriveFolder(dir, drive);
                    list.add(f);
                }

                return list.toArray(new FileStorageFolder[list.size()]);
            }
        }.perform(folderId);
    }

    @Override
    public Quota getStorageQuota(String folderId) throws OXException {
        return new BackOffPerformer<Quota>(googleDriveAccess, account, session) {

            @Override
            Quota perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                About about = drive.about().get().setFields("kind,user,storageQuota").execute();
                if (null == about.getStorageQuota().getLimit()) {
                    return Type.STORAGE.getUnlimited();
                }
                return new Quota(l(about.getStorageQuota().getLimit()), l(about.getStorageQuota().getUsage()), Type.STORAGE);
            }
        }.perform(folderId);
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

    void checkDirValidity(com.google.api.services.drive.model.File file) throws OXException {
        if (!isDir(file)) {
            throw FileStorageExceptionCodes.NOT_A_FOLDER.create(GoogleDriveConstants.ID, file.getId());
        }
        checkIfTrashed(file);
    }

    GoogleDriveFolder parseGoogleDriveFolder(com.google.api.services.drive.model.File dir, Drive drive) throws OXException, IOException {
        return new GoogleDriveFolder(userId).parseDirEntry(dir, getRootFolderId(), accountDisplayName, useOptimisticSubfolderDetection, drive);
    }

}
