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

package com.openexchange.file.storage.composition.internal;

import static com.openexchange.file.storage.composition.internal.FileStorageTools.getAccountName;
import static com.openexchange.file.storage.composition.internal.FileStorageTools.getPathString;
import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.DefaultFileStorageFolder;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageAutoRenameFoldersAccess;
import com.openexchange.file.storage.FileStorageCapability;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFileAccess.IDTuple;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStorageVersionedFileAccess;
import com.openexchange.file.storage.composition.FileID;
import com.openexchange.file.storage.composition.FolderID;
import com.openexchange.groupware.results.TimedResult;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tx.ConnectionHolder;

/**
 * {@link StorageTransfer}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class StorageTransfer {

    private final AbstractCompositingIDBasedFolderAccess compositingAccess;
    private final FolderID sourceFolderID;
    private final FolderID targetParentFolderID;
    private final String newName;
    private final FileStorageFolderAccess sourceFolderAccess;
    private final FileStorageFolderAccess targetFolderAccess;
    private final FileStorageFileAccess sourceFileAccess;
    private final FileStorageFileAccess targetFileAccess;

    /**
     * Initializes a new {@link StorageTransfer}.
     *
     * @param compositingAccess The underlying ID-based folder access
     * @param sourceFolderID The identifier of the source folder
     * @param targetParentFolderID The identifier of the target parent folder
     * @param newName A new name to take over for the target folder, or <code>null</code> to keep the source folder name
     */
    public StorageTransfer(AbstractCompositingIDBasedFolderAccess compositingAccess, FolderID sourceFolderID, FolderID targetParentFolderID, String newName) throws OXException {
        super();
        this.compositingAccess = compositingAccess;
        this.sourceFolderID = sourceFolderID;
        this.targetParentFolderID = targetParentFolderID;
        this.newName = newName;
        this.sourceFolderAccess = compositingAccess.getFolderAccess(sourceFolderID);
        this.targetFolderAccess = compositingAccess.getFolderAccess(targetParentFolderID);
        this.sourceFileAccess = compositingAccess.getFileAccess(sourceFolderID);
        this.targetFileAccess = compositingAccess.getFileAccess(targetParentFolderID);
    }

    /**
     * Performs the storage transfer, i.e. copies the source folder and all descendant files and subfolders recursively to the target parent folder.
     *
     * @param dryRun <code>true</code> to only simulate the transfer and gather any potential warnings, <code>false</code> to actually perform the operation
     * @return The transfer result
     * @throws OXException
     */
    public TransferResult run(boolean dryRun) throws OXException {
        /*
         * supply an active connection to connection holder
         */
        Object connection = compositingAccess.getSession().getParameter(Connection.class.getName());
        if (null != connection) {
            ConnectionHolder.CONNECTION.set((Connection) connection);
        }
        try {
            targetFileAccess.startTransaction();
            sourceFileAccess.startTransaction();
            try {
                /*
                 * copy folder recursively
                 */
                FileStorageFolder sourceFolder = sourceFolderAccess.getFolder(sourceFolderID.getFolderId());
                FileStorageFolder[] sourcePath = sourceFolderAccess.getPath2DefaultFolder(sourceFolderID.getFolderId());
                FileStorageFolder[] targetParentPath = targetFolderAccess.getPath2DefaultFolder(targetParentFolderID.getFolderId());
                TransferResult result = copyFolder(sourceFolder, sourcePath, targetParentFolderID, targetParentPath, newName, dryRun);
                sourceFileAccess.commit();
                targetFileAccess.commit();
                return result;
            } catch (OXException e) {
                sourceFileAccess.rollback();
                targetFileAccess.rollback();
                throw e;
            } finally {
                sourceFileAccess.finish();
                targetFileAccess.finish();
            }
        } finally {
            if (null != connection) {
                ConnectionHolder.CONNECTION.set(null);
            }
        }
    }

    /**
     * Copies a folder and all descendant files and subfolders recursively.
     *
     * @param sourceFolder The source folder to copy
     * @param sourceFolderID The full identifier of the source folder
     * @param sourcePath The path to the source folder
     * @param targetParentFolderID The identifier of the target parent folder
     * @param targetParentPath The path to the target parent folder
     * @param dryRun <code>true</code> to perform a simulated run and check for possible warnings only, <code>false</code> to really perform the actual operation
     * @param newName A new name to apply for the target folder, or <code>null</code> to use the source folder's name
     * @return The recursive transfer result
     */
    private TransferResult copyFolder(FileStorageFolder sourceFolder, FileStorageFolder[] sourcePath, FolderID targetParentFolderID, FileStorageFolder[] targetParentPath, String newName, boolean dryRun) throws OXException {
        /*
         * prepare target folder
         */
        DefaultFileStorageFolder targetFolder = new DefaultFileStorageFolder();
        targetFolder.setName(null != newName ? newName : sourceFolder.getName());
        targetFolder.setParentId(targetParentFolderID.getFolderId());
        targetFolder.setSubscribed(sourceFolder.isSubscribed());
        /*
         * collect warnings beforehand, create folder when not in dry-run mode
         */
        List<OXException> warnings = collectWarnings(sourceFolder, sourcePath, targetParentFolderID);
        String newID = dryRun ? "virtual" : (targetFolderAccess instanceof FileStorageAutoRenameFoldersAccess) ? ((FileStorageAutoRenameFoldersAccess) targetFolderAccess).createFolder(targetFolder, false) : targetFolderAccess.createFolder(targetFolder);
        /*
         * prepare transfer result based on target folder information
         */
        targetFolder.setId(newID);
        FolderID targetFolderID = new FolderID(targetParentFolderID.getService(), targetParentFolderID.getAccountId(), newID);
        FileStorageFolder[] targetPath = prepend(targetFolder, targetParentPath);
        TransferResult transferResult = new TransferResult(getFolderID(sourceFileAccess, sourceFolder), sourcePath, targetFolderID, targetPath);
        /*
         * copy contained files
         */
        Map<File, IDTuple> copiedFiles = copyContents(warnings, getFolderID(sourceFileAccess, sourceFolder), sourcePath, targetFolderID, dryRun);
        transferResult.addTransferredFiles(copiedFiles);
        transferResult.addWarnings(warnings);
        if (sourceFolder.hasSubfolders()) {
            /*
             * copy contained folders recursively
             */
            FileStorageFolder[] subfolders = sourceFolderAccess.getSubfolders(sourceFolder.getId(), true);
            if (null != subfolders && 0 < subfolders.length) {
                for (FileStorageFolder nextSourceFolder : subfolders) {
                    FileStorageFolder[] nextSourcePath = prepend(nextSourceFolder, sourcePath);
                    transferResult.addNestedResult(copyFolder(nextSourceFolder, nextSourcePath, targetFolderID, targetPath, null, dryRun));
                }
            }
        }
        return transferResult;
    }

    private Map<File, IDTuple> copyContents(List<OXException> warnings, FolderID sourceFolderID, FileStorageFolder[] sourcePath, FolderID targetFolderID, boolean dryRun) throws OXException {
        Map<File, IDTuple> copiedFiles = new HashMap<File, IDTuple>();
        TimedResult<File> sourceDocuments = sourceFileAccess.getDocuments(sourceFolderID.getFolderId());
        SearchIterator<File> searchIterator = null;
        try {
            searchIterator = sourceDocuments.results();
            while (searchIterator.hasNext()) {
                File file = searchIterator.next();
                copiedFiles.put(file, copyFile(warnings, file, sourcePath, targetFolderID, dryRun));
            }
        } finally {
            SearchIterators.close(searchIterator);
        }
        return copiedFiles;
    }

    private IDTuple copyFile(List<OXException> warnings, File file, FileStorageFolder[] sourcePath, FolderID targetFolderID, boolean dryRun) throws OXException {
        /*
         * collect warnings, cancel operation when in dry-run mode
         */
        warnings.addAll(collectWarnings(file, sourcePath, targetFolderID));
        if (dryRun) {
            return new IDTuple(targetFolderID.getFolderId(), "virtual");
        }
        if (1 < file.getNumberOfVersions() &&
            FileStorageTools.supports(sourceFileAccess, FileStorageCapability.FILE_VERSIONS) &&
            FileStorageTools.supports(targetFileAccess, FileStorageCapability.FILE_VERSIONS)) {
            /*
             * copy all versions
             */
            return copyFileVersions(sourceFileAccess, file, targetFileAccess, targetFolderID);
        }
        /*
         * copy current version only
         */
        return copyCurrentVersion(sourceFileAccess, file, targetFileAccess, targetFolderID);
    }

    private List<OXException> collectWarnings(File sourceFile, FileStorageFolder[] sourcePath, FolderID targetFolderID) throws OXException {
        List<OXException> warnings = new ArrayList<OXException>();
        if (Strings.isNotEmpty(sourceFile.getDescription()) && false == FileStorageTools.supports(targetFileAccess, FileStorageCapability.EXTENDED_METADATA)) {
            warnings.add(FileStorageExceptionCodes.LOSS_OF_NOTES.create(sourceFile.getFileName(), getPathString(sourcePath),
                getAccountName(compositingAccess, targetFolderID), getFileID(sourceFileAccess, sourceFile).toUniqueID(), targetFolderID.toUniqueID()));
        }
        if (Strings.isNotEmpty(sourceFile.getCategories()) && false == FileStorageTools.supports(targetFileAccess, FileStorageCapability.EXTENDED_METADATA)) {
            warnings.add(FileStorageExceptionCodes.LOSS_OF_CATEGORIES.create(sourceFile.getFileName(), getPathString(sourcePath),
                getAccountName(compositingAccess, targetFolderID), getFileID(sourceFileAccess, sourceFile).toUniqueID(), targetFolderID.toUniqueID()));
        }
        if (1 < sourceFile.getNumberOfVersions() && false == FileStorageTools.supports(targetFileAccess, FileStorageCapability.FILE_VERSIONS)) {
            warnings.add(FileStorageExceptionCodes.LOSS_OF_VERSIONS.create(sourceFile.getFileName(), getPathString(sourcePath),
                getAccountName(compositingAccess, targetFolderID), getFileID(sourceFileAccess, sourceFile).toUniqueID(), targetFolderID.toUniqueID()));
        }
        if (null != sourceFile.getObjectPermissions() && 0 < sourceFile.getObjectPermissions().size()) {
            warnings.add(FileStorageExceptionCodes.LOSS_OF_FILE_SHARES.create(sourceFile.getFileName(), getPathString(sourcePath),
                getAccountName(compositingAccess, targetFolderID), getFileID(sourceFileAccess, sourceFile).toUniqueID(), targetFolderID.toUniqueID()));
        }
        return warnings;
    }

    private List<OXException> collectWarnings(FileStorageFolder sourceFolder, FileStorageFolder[] sourcePath, FolderID targetParentFolderID) throws OXException {
        ArrayList<OXException> warnings = new ArrayList<OXException>();
        if (FileStorageTools.containsForeignPermissions(compositingAccess.getSession().getUserId(), sourceFolder)) {
            warnings.add(FileStorageExceptionCodes.LOSS_OF_FOLDER_SHARES.create(getPathString(sourcePath),
                getAccountName(compositingAccess, targetParentFolderID), getFolderID(sourceFileAccess, sourceFolder).toUniqueID(), targetParentFolderID.toUniqueID()));
        }
        return warnings;
    }

    private static FileID getFileID(FileStorageFileAccess fileAccess, File file) {
        return new FileID(fileAccess.getAccountAccess().getService().getId(), fileAccess.getAccountAccess().getAccountId(), file.getFolderId(), file.getId());
    }

    private static FolderID getFolderID(FileStorageFileAccess fileAccess, FileStorageFolder folder) {
        return new FolderID(fileAccess.getAccountAccess().getService().getId(), fileAccess.getAccountAccess().getAccountId(), folder.getId());
    }

    private static IDTuple copyCurrentVersion(FileStorageFileAccess sourceFileAccess, File file, FileStorageFileAccess targetFileAccess, FolderID targetFolderID) throws OXException {
        DefaultFile toCreate = new DefaultFile(file);
        toCreate.setId(null);
        toCreate.setUniqueId(null);
        toCreate.setFolderId(targetFolderID.getFolderId());
        toCreate.setVersion(null);
        toCreate.setObjectPermissions(null);
        InputStream contents = null;
        try {
            contents = sourceFileAccess.getDocument(file.getFolderId(), file.getId(), FileStorageFileAccess.CURRENT_VERSION);
            return targetFileAccess.saveDocument(toCreate, contents, FileStorageFileAccess.UNDEFINED_SEQUENCE_NUMBER);
        } finally {
            Streams.close(contents);
        }
    }

    private static IDTuple copyFileVersions(FileStorageFileAccess sourceFileAccess, File file, FileStorageFileAccess targetFileAccess, FolderID targetFolderID) throws OXException {
        TimedResult<File> versions = ((FileStorageVersionedFileAccess) sourceFileAccess).getVersions(file.getFolderId(), file.getId());
        SearchIterator<File> searchIterator = null;
        IDTuple id = null;
        try {
            searchIterator = versions.results();
            while (searchIterator.hasNext()) {
                File version = searchIterator.next();
                DefaultFile toCreate = new DefaultFile(version);
                toCreate.setId(null == id ? FileStorageFileAccess.NEW : id.getId());
                toCreate.setUniqueId(null);
                toCreate.setFolderId(targetFolderID.getFolderId());
                toCreate.setObjectPermissions(null);
                InputStream contents = null;
                try {
                    contents = sourceFileAccess.getDocument(version.getFolderId(), version.getId(), version.getVersion());
                    id = targetFileAccess.saveDocument(toCreate, contents, FileStorageFileAccess.UNDEFINED_SEQUENCE_NUMBER);
                } finally {
                    Streams.close(contents);
                }
            }
        } finally {
            SearchIterators.close(searchIterator);
        }
        return id;
    }

    private static FileStorageFolder[] prepend(FileStorageFolder folder, FileStorageFolder[] folders) {
        FileStorageFolder[] newFolders = new FileStorageFolder[folders.length + 1];
        newFolders[0] = folder;
        System.arraycopy(folders, 0, newFolders, 1, folders.length);
        return newFolders;
    }

}
