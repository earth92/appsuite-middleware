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

import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.FIELDS_DEFAULT;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.FIELDS_MINIMAL;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.QUERY_STRING_FILES_ONLY;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.QUERY_STRING_FILES_ONLY_EXCLUDING_TRASH;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.GoogleFileFields.NAME;
import static com.openexchange.file.storage.googledrive.GoogleDriveConstants.GoogleFileFields.THUMBNAIL;
import static com.openexchange.java.Strings.isEmpty;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Update;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.FileList;
import com.openexchange.annotation.NonNull;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileDelta;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageAutoRenameFoldersAccess;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStoragePersistentIDs;
import com.openexchange.file.storage.FileStorageSequenceNumberProvider;
import com.openexchange.file.storage.FileTimedResult;
import com.openexchange.file.storage.NameBuilder;
import com.openexchange.file.storage.ThumbnailAware;
import com.openexchange.file.storage.googledrive.access.GoogleDriveOAuthAccess;
import com.openexchange.groupware.results.Delta;
import com.openexchange.groupware.results.TimedResult;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorAdapter;

/**
 * {@link GoogleDriveFileAccess}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class GoogleDriveFileAccess extends AbstractGoogleDriveAccess implements ThumbnailAware, FileStorageSequenceNumberProvider, FileStoragePersistentIDs, FileStorageAutoRenameFoldersAccess {

    private final GoogleDriveAccountAccess accountAccess;

    private final GoogleDriveFolderAccess folderAccess;

    private final int userId;

    /**
     * Initializes a new {@link GoogleDriveFileAccess}.
     *
     * @param googleDriveAccess The underlying Google Drive access
     * @param account The used file storage account
     * @param session The session
     * @param accountAccess A Google Drive account access reference
     * @param folderAccess A Google Drive folder access reference
     */
    public GoogleDriveFileAccess(@NonNull GoogleDriveOAuthAccess googleDriveAccess, @NonNull FileStorageAccount account, @NonNull Session session, GoogleDriveAccountAccess accountAccess, GoogleDriveFolderAccess folderAccess) {
        super(googleDriveAccess, account, session);
        this.accountAccess = accountAccess;
        this.folderAccess = folderAccess;
        this.userId = session.getUserId();
    }

    @Override
    public FileStorageAccountAccess getAccountAccess() {
        return accountAccess;
    }

    @Override
    public String createFolder(FileStorageFolder toCreate, boolean autoRename) throws OXException {
        return folderAccess.createFolder(toCreate, autoRename);
    }

    @Override
    public String moveFolder(String folderId, String newParentId, String newName, boolean autoRename) throws OXException {
        return folderAccess.moveFolder(folderId, newParentId, newName, autoRename);
    }

    @Override
    public void startTransaction() throws OXException {
        // Nope
    }

    @Override
    public void commit() throws OXException {
        // Nope
    }

    @Override
    public void rollback() throws OXException {
        // Nope
    }

    @Override
    public void finish() throws OXException {
        // Nope
    }

    @Override
    public void setTransactional(final boolean transactional) {
        // Nope
    }

    @Override
    public void setRequestTransactional(boolean transactional) {
        // Nope
    }

    @Override
    public void setCommitsTransaction(boolean commits) {
        // Nope
    }

    @Override
    public boolean exists(String folderId, String id, String version) throws OXException {
        try {
            return null != getMetadata(folderId, id, version, Collections.singletonList(Field.ID));
        } catch (OXException e) {
            if (FileStorageExceptionCodes.FILE_NOT_FOUND.equals(e) || FileStorageExceptionCodes.FOLDER_NOT_FOUND.equals(e) || FileStorageExceptionCodes.FILE_VERSION_NOT_FOUND.equals(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public File getFileMetadata(String folderId, String id, String version) throws OXException {
        return getMetadata(folderId, id, version, ALL_FIELDS);
    }

    @Override
    public IDTuple saveFileMetadata(File file, long sequenceNumber) throws OXException {
        return saveFileMetadata(file, sequenceNumber, ALL_FIELDS);
    }

    @Override
    public IDTuple saveFileMetadata(File file, long sequenceNumber, List<Field> modifiedFields) throws OXException {
        if (null != modifiedFields && false == (modifiedFields.contains(Field.FILENAME) || modifiedFields.contains(Field.VERSION))) {
            /*
             * File was changed, but neither the name nor the version changed. Nothing to update in the meta data
             */
            return new IDTuple(file.getFolderId(), file.getId());
        }

        return new BackOffPerformer<IDTuple>(googleDriveAccess, account, session) {

            @Override
            IDTuple perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                com.google.api.services.drive.model.File savedFile;
                if (FileStorageFileAccess.NEW != file.getId()) {
                    if (isFileNameChanged(drive, file)) {
                        /*
                         * The file was renamed. First check if there is already such a file.
                         */
                        {
                            String fileName = file.getFileName();
                            if (containsFileName(searchByFileNamePattern(fileName, file.getFolderId(), false, Arrays.asList(Field.ID, Field.FILENAME), null, null), fileName)) {
                                throw FileStorageExceptionCodes.FILE_ALREADY_EXISTS.create();
                            }
                        }
                        savedFile = drive.files().update(file.getId(), new com.google.api.services.drive.model.File().setName(file.getFileName())).execute();
                    } else {
                        return new IDTuple(file.getFolderId(), file.getId());
                    }
                } else {
                    /*
                     * New file. First check if there is already such a file
                     */
                    String fileNameToUse = null;
                    {
                        NameBuilder fileName = NameBuilder.nameBuilderFor(file.getFileName());
                        while (null == fileNameToUse) {
                            String fileNameToTest = fileName.toString();
                            if (containsFileName(searchByFileNamePattern(fileNameToTest, file.getFolderId(), false, Arrays.asList(Field.ID, Field.FILENAME), null, null), fileNameToTest)) {
                                fileName.advance();
                            } else {
                                fileNameToUse = fileNameToTest;
                            }
                        }
                    }
                    savedFile = drive.files().create(new com.google.api.services.drive.model.File().setName(fileNameToUse)).execute();
                }
                return new IDTuple(file.getFolderId(), savedFile.getId());
            }
        }.perform(file.getId());
    }

    @Override
    public IDTuple copy(IDTuple source, String version, String destFolder, File update, InputStream newFil, List<Field> modifiedFields) throws OXException {
        return new BackOffPerformer<IDTuple>(googleDriveAccess, account, session) {

            @Override
            IDTuple perform() throws OXException, IOException, RuntimeException {
                if (version != CURRENT_VERSION) {
                    // can only copy the current revision
                    throw FileStorageExceptionCodes.VERSIONING_NOT_SUPPORTED.create(GoogleDriveConstants.ID);
                }
                String id = source.getId();
                Drive drive = googleDriveAccess.<Drive> getClient().client;

                com.google.api.services.drive.model.File srcFile = drive.files().get(id).setFields(FIELDS_MINIMAL).execute();
                checkFileValidity(srcFile);

                // Determine destination identifier
                String destId = toGoogleDriveFolderId(destFolder);

                // Check destination folder
                String name = srcFile.getName();
                name = getFileName(drive, destFolder, name);

                // Create a file at destination directory
                com.google.api.services.drive.model.File copy = new com.google.api.services.drive.model.File();
                copy.setName(name);
                GoogleDriveUtil.setParentFolder(copy, destId);
                handleNameChange(update, modifiedFields, srcFile, copy);

                // Copy file
                com.google.api.services.drive.model.File copiedFile = drive.files().copy(id, copy).execute();

                return new IDTuple(destFolder, copiedFile.getId());
            }
        }.perform(source.getId());
    }

    @Override
    public IDTuple move(IDTuple source, String destFolder, long sequenceNumber, File update, List<File.Field> modifiedFields) throws OXException {
        String id = source.getId();
        return new BackOffPerformer<IDTuple>(googleDriveAccess, account, session) {

            @Override
            IDTuple perform() throws OXException, IOException, RuntimeException {

                Drive drive = googleDriveAccess.<Drive> getClient().client;

                // Get source file
                com.google.api.services.drive.model.File srcFile = drive.files().get(id).setFields(FIELDS_DEFAULT).execute();
                checkFileValidity(srcFile);

                // Determine destination identifier
                String destId = toGoogleDriveFolderId(destFolder);

                // Check destination folder
                for (String parentReference : srcFile.getParents()) {
                    if (destFolder.equals(parentReference)) {
                        return source;
                    }
                }

                String name = srcFile.getName();

                // Create patch file
                com.google.api.services.drive.model.File patch = new com.google.api.services.drive.model.File();

                if (null != update) {
                    if (isFileNameChanged(update, modifiedFields, srcFile)) {
                        name = update.getTitle();
                        patch.setName(name);
                    }
                }

                String fileName = getFileName(drive, destId, name);
                if (false == name.equals(fileName)) {
                    patch.setName(fileName);
                }

                // Patch the file
                Update update = drive.files().update(id, patch);
                update.setRemoveParents(GoogleDriveUtil.getParentFolders(srcFile));
                update.setAddParents(destId);
                com.google.api.services.drive.model.File patchedFile = update.execute();

                return new IDTuple(destFolder, patchedFile.getId());

            }
        }.perform(id);
    }

    @Override
    public InputStream getDocument(String folderId, String id, String version) throws OXException {
        return new BackOffPerformer<InputStream>(googleDriveAccess, account, session) {

            @Override
            InputStream perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                /*
                 * get file or revision
                 */
                com.google.api.services.drive.model.File file = drive.files().get(id).setFields(FIELDS_MINIMAL).execute();
                checkFileValidity(file);
                if (CURRENT_VERSION == version) {
                    return drive.files().get(id).executeMediaAsInputStream();
                }

                return drive.revisions().get(id, version).executeMediaAsInputStream();
            }
        }.perform(id);
    }

    @Override
    public InputStream getThumbnailStream(String folderId, String id, String version) throws OXException {
        return new BackOffPerformer<InputStream>(googleDriveAccess, account, session) {

            @Override
            InputStream perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                /*
                 * get thumbnail link from file
                 */
                com.google.api.services.drive.model.File file = drive.files().get(id).setFields(FIELDS_MINIMAL + "," + THUMBNAIL.getField()).execute();
                checkFileValidity(file);
                String thumbnailLink = file.getThumbnailLink();
                if (Strings.isEmpty(thumbnailLink)) {
                    // The file doesn't have a thumbnail
                    return null;
                }
                /*
                 * get thumbnail stream
                 */
                HttpResponse resp = drive.getRequestFactory().buildGetRequest(new GenericUrl(thumbnailLink)).execute();
                return resp.getContent();
            }
        }.perform(id);
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber) throws OXException {
        return saveDocument(file, data, sequenceNumber, null);
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber, List<Field> modifiedFields) throws OXException {
        return new BackOffPerformer<IDTuple>(googleDriveAccess, account, session) {

            @Override
            IDTuple perform() throws OXException, IOException, RuntimeException {
                /*
                 * prepare Google Drive file
                 */
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                if (FileStorageFileAccess.NEW == file.getId()) {
                    GoogleDriveUtil.setParentFolder(fileMetadata, toGoogleDriveFolderId(file.getFolderId()));
                    try {
                        /*
                         * first check if there is already such a file
                         */
                        String fileNameToUse = null;
                        {
                            List<Field> fields = Arrays.asList(Field.ID, Field.FILENAME);
                            NameBuilder fileName = NameBuilder.nameBuilderFor(file.getFileName());
                            while (null == fileNameToUse) {
                                String fileNameToTest = fileName.toString();
                                if (containsFileName(searchByFileNamePattern(fileNameToTest, file.getFolderId(), false, fields, null, null), fileNameToTest)) {
                                    fileName.advance();
                                } else {
                                    fileNameToUse = fileNameToTest;
                                }
                            }
                        }

                        fileMetadata.setName(fileNameToUse);
                        Drive.Files.Create create = drive.files().create(fileMetadata, new InputStreamContent(file.getFileMIMEType(), data));
                        create.getMediaHttpUploader().setDirectUploadEnabled(true);
                        com.google.api.services.drive.model.File gDriveFile = create.execute();
                        String newId = gDriveFile.getId();
                        file.setId(newId);
                        return new IDTuple(file.getFolderId(), newId);
                    } catch (IOException e) {
                        if (GoogleDriveConstants.SC_CONFLICT != GoogleDriveUtil.getStatusCode(e)) {
                            throw e;
                        }
                        throw FileStorageExceptionCodes.FILE_ALREADY_EXISTS.create();
                    } finally {
                        Streams.close(data);
                    }
                }

                /*
                 * Update an existing file...
                 *
                 * Upload new version of existing file, adjusting metadata as requested
                 */
                if ((null == modifiedFields || modifiedFields.contains(Field.FILENAME)) && isFileNameChanged(drive, file)) {
                    /*
                     * first check if there is already such a file
                     */
                    {
                        String fileName = file.getFileName();
                        if (containsFileName(searchByFileNamePattern(fileName, file.getFolderId(), false, Arrays.asList(Field.ID, Field.FILENAME), null, null), fileName)) {
                            throw FileStorageExceptionCodes.FILE_ALREADY_EXISTS.create();
                        }
                    }

                    fileMetadata.setName(file.getFileName());
                }
                Drive.Files.Update update = drive.files().update(file.getId(), fileMetadata, new InputStreamContent(file.getFileMIMEType(), data));
                update.getMediaHttpUploader().setDirectUploadEnabled(true);
                fileMetadata = update.execute();
                return new IDTuple(file.getFolderId(), fileMetadata.getId());
            }
        }.perform(file.getId());
    }

    @Override
    public void removeDocument(String folderId, long sequenceNumber) throws OXException {
        new BackOffPerformer<Void>(googleDriveAccess, account, session) {

            @Override
            Void perform() throws OXException, IOException, RuntimeException {

                Drive drive = googleDriveAccess.<Drive> getClient().client;

                // Determine folder identifier
                String fid = toGoogleDriveFolderId(folderId);

                // Query all files
                com.google.api.services.drive.Drive.Files.List list = drive.files().list();
                list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY_EXCLUDING_TRASH).searchForChildren(fid).build());
                list.setFields("kind,nextPageToken,files(id)");

                boolean hardDelete = isTrashed(fid, drive);

                FileList childList = list.execute();
                if (childList.getFiles().isEmpty()) {
                    return null;
                }

                deleteFiles(drive, hardDelete, childList);

                String nextPageToken = childList.getNextPageToken();
                while (false == isEmpty(nextPageToken)) {
                    list.setPageToken(nextPageToken);
                    childList = list.execute();
                    if (false == childList.getFiles().isEmpty()) {
                        deleteFiles(drive, hardDelete, childList);
                    }
                    nextPageToken = childList.getNextPageToken();
                }
                return null;
            }
        }.perform(folderId);
    }

    @Override
    public List<IDTuple> removeDocument(List<IDTuple> ids, long sequenceNumber) throws OXException {
        return removeDocument(ids, sequenceNumber, false);
    }

    @Override
    public List<IDTuple> removeDocument(List<IDTuple> ids, long sequenceNumber, boolean hardDelete) throws OXException {
        return new BackOffPerformer<List<IDTuple>>(googleDriveAccess, account, session) {

            @Override
            List<IDTuple> perform() throws OXException, IOException, RuntimeException {

                Drive drive = googleDriveAccess.<Drive> getClient().client;
                Map<String, Boolean> knownTrashFolders = new HashMap<>();
                List<IDTuple> ret = new ArrayList<>(ids.size());
                for (IDTuple id : ids) {
                    try {
                        if (hardDelete) {
                            drive.files().delete(id.getId()).execute();
                            continue;
                        }
                        Boolean isTrashed = knownTrashFolders.get(id.getFolder());
                        if (null == isTrashed) {
                            isTrashed = Boolean.valueOf(isTrashed(toGoogleDriveFolderId(id.getFolder()), drive));
                            knownTrashFolders.put(id.getFolder(), isTrashed);
                        }
                        if (isTrashed.booleanValue()) {
                            drive.files().delete(id.getId()).execute();
                        } else {
                            trashFile(drive, id.getId());
                        }
                    } catch (IOException e) {
                        if (GoogleDriveConstants.SC_NOT_FOUND == GoogleDriveUtil.getStatusCode(e)) {
                            throw e;
                        }
                        ret.add(id);
                    }
                }
                return ret;
            }
        }.perform(null);
    }

    @Override
    public void touch(String folderId, String id) throws OXException {
        exists(folderId, id, CURRENT_VERSION);
    }

    @Override
    public TimedResult<File> getDocuments(String folderId) throws OXException {
        return getDocuments(folderId, ALL_FIELDS);
    }

    @Override
    public TimedResult<File> getDocuments(final String folderId, final List<Field> fields) throws OXException {
        return getDocuments(folderId, fields, null, SortDirection.DEFAULT);
    }

    @Override
    public TimedResult<File> getDocuments(String folderId, List<Field> fields, Field sort, SortDirection order) throws OXException {
        return new BackOffPerformer<TimedResult<File>>(googleDriveAccess, account, session) {

            @Override
            TimedResult<File> perform() throws OXException, IOException, RuntimeException {

                Drive drive = googleDriveAccess.<Drive> getClient().client;
                List<File> files = new LinkedList<>();
                /*
                 * build request to list all files in a folder
                 */
                com.google.api.services.drive.Drive.Files.List listRequest = drive.files().list().setQ(new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY_EXCLUDING_TRASH).searchForChildren(toGoogleDriveFolderId(folderId)).build()).setFields("kind,nextPageToken,files(" + getFields(fields) + ')');
                /*
                 * execute as often as needed & parse files
                 */
                FileList fileList;
                do {
                    fileList = listRequest.execute();
                    for (com.google.api.services.drive.model.File file : fileList.getFiles()) {
                        GoogleDriveFile metadata = createFile(folderId, file.getId(), file, fields);
                        files.add(metadata);
                    }
                    listRequest.setPageToken(fileList.getNextPageToken());
                } while (null != fileList.getNextPageToken());
                /*
                 * return sorted timed result
                 */
                sort(files, sort, order);
                return new FileTimedResult(files);
            }
        }.perform(folderId);
    }

    @Override
    public TimedResult<File> getDocuments(List<IDTuple> ids, List<Field> fields) throws OXException {
        List<File> files = new LinkedList<>();
        for (IDTuple idTuple : ids) {
            files.add(getMetadata(idTuple.getFolder(), idTuple.getId(), CURRENT_VERSION, fields));
        }
        return new FileTimedResult(files);
    }

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, boolean ignoreDeleted) throws OXException {
        return getDelta(folderId, updateSince, fields, null, SortDirection.DEFAULT, ignoreDeleted);
    }

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, Field sort, SortDirection order, boolean ignoreDeleted) throws OXException {
        return new BackOffPerformer<Delta<File>>(googleDriveAccess, account, session) {

            @Override
            Delta<File> perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                List<File> updatedFiles = new LinkedList<>();
                List<File> deletedFiles = new LinkedList<>();
                List<File> newFiles = new LinkedList<>();
                long sequenceNumber = updateSince;
                /*
                 * build request to list all files in a folder, changed since the supplied timestamp
                 */
                GoogleFileQueryBuilder builder = new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY);
                builder.searchForChildren(folderId);

                if (Long.MIN_VALUE != updateSince) {
                    builder.modificationDateGreaterThan(updateSince);
                }
                com.google.api.services.drive.Drive.Files.List listRequest = drive.files().list().setQ(builder.build()).setFields("kind,nextPageToken,files(" + getFields(fields, Field.CREATED) + ')');
                /*
                 * execute as often as needed & parse files
                 */
                FileList fileList;
                do {
                    fileList = listRequest.execute();
                    for (com.google.api.services.drive.model.File file : fileList.getFiles()) {
                        GoogleDriveFile metadata = createFile(folderId, file.getId(), file, fields);
                        /*
                         * determine maximum sequence number & add file to appropriate delta collection
                         */
                        sequenceNumber = Math.max(sequenceNumber, metadata.getSequenceNumber());
                        if (Boolean.TRUE.equals(file.getTrashed())) {
                            deletedFiles.add(metadata);
                        } else {
                            if (Long.MIN_VALUE == updateSince || null != metadata.getCreated() && metadata.getCreated().getTime() > updateSince) {
                                newFiles.add(metadata);
                            } else {
                                updatedFiles.add(metadata);
                            }
                        }
                    }
                    listRequest.setPageToken(fileList.getNextPageToken());
                } while (null != fileList.getNextPageToken());
                /*
                 * return sorted timed result
                 */
                sort(updatedFiles, sort, order);
                sort(deletedFiles, sort, order);
                sort(newFiles, sort, order);
                return new FileDelta(newFiles, updatedFiles, deletedFiles, sequenceNumber);
            }
        }.perform(folderId);
    }

    @Override
    public SearchIterator<File> search(String pattern, List<Field> fields, String folderId, Field sort, SortDirection order, int start, int end) throws OXException {
        return search(pattern, fields, folderId, false, sort, order, start, end);
    }

    @Override
    public SearchIterator<File> search(String pattern, List<Field> fields, String folderId, boolean includeSubfolders, Field sort, SortDirection order, int start, int end) throws OXException {
        try {
            List<Field> fieldsToUse = Field.addDateFieldsIfNeeded(fields, sort);
            // Search by pattern
            List<File> files = searchByFileNamePattern(pattern, folderId, includeSubfolders, fieldsToUse, sort, order);

            // Start, end...
            if ((start != NOT_SET) && (end != NOT_SET)) {
                final int size = files.size();
                if ((start) > size) {
                    /*
                     * Return empty iterator if start is out of range
                     */
                    return SearchIteratorAdapter.emptyIterator();
                }
                /*
                 * Reset end index if out of range
                 */
                int toIndex = end;
                if (toIndex >= size) {
                    toIndex = size;
                }
                files = files.subList(start, toIndex);
            }

            return new SearchIteratorAdapter<>(files.iterator(), files.size());
        } catch (RuntimeException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public Map<String, Long> getSequenceNumbers(List<String> folderIds) throws OXException {
        return new BackOffPerformer<Map<String, Long>>(googleDriveAccess, account, session) {

            @Override
            Map<String, Long> perform() throws OXException, IOException, RuntimeException {

                Map<String, Long> sequenceNumbers = new HashMap<>(folderIds.size());
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                List<Change> changes = drive.changes().list(drive.changes().getStartPageToken().execute().getStartPageToken()).execute().getChanges();
                for (Change change : changes) {
                    com.google.api.services.drive.model.File file = change.getFile();
                    if (isDir(file) && folderIds.contains(file.getId())) {
                        sequenceNumbers.put(file.getId(), file.getVersion());
                    }
                }
                return sequenceNumbers;
            }
        }.perform(null);
    }

    /**
     * Searches for files whose filename matches the supplied pattern.
     *
     * @param pattern The pattern to search for
     * @param folderId The parent folder identifier to restrict the search to, or <code>null</code> to search all folders
     * @param includeSubfolders <code>true</code> to include subfolderes, <code>false</code>, otherwise
     * @param fields The fields to retrieve
     * @param sort The field to use to sort the results
     * @param order The sort order to apply
     * @return The found files
     */
    List<File> searchByFileNamePattern(String pattern, String folderId, boolean includeSubfolders, List<Field> fields, Field sort, SortDirection order) throws OXException {
        return new BackOffPerformer<List<File>>(googleDriveAccess, account, session) {

            @Override
            List<File> perform() throws OXException, IOException, RuntimeException {

                Drive drive = googleDriveAccess.<Drive> getClient().client;
                List<File> files = new ArrayList<>();
                /*
                 * build search query
                 */
                GoogleFileQueryBuilder query = new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY_EXCLUDING_TRASH);
                Map<String, Boolean> allowedFolders;
                if (null != folderId) {
                    allowedFolders = new HashMap<>();
                    allowedFolders.put(folderId, Boolean.TRUE);
                    if (false == includeSubfolders) {
                        query.searchForChildren(toGoogleDriveFolderId(folderId));
                    }
                } else {
                    allowedFolders = null;
                }
                if (null != pattern) {
                    query.containsName(escape(pattern));
                }
                /*
                 * build request based on query
                 */
                com.google.api.services.drive.Drive.Files.List listRequest = drive.files().list().setQ(query.build()).setFields("kind,nextPageToken,files(" + getFields(fields, sort) + ')');
                /*
                 * execute as often as needed & parse files
                 */
                FileList fileList;
                do {
                    fileList = listRequest.execute();
                    for (com.google.api.services.drive.model.File file : fileList.getFiles()) {
                        GoogleDriveFile metadata = createFile(null, file.getId(), file, fields);
                        if (null != allowedFolders) {
                            Boolean allowed = allowedFolders.get(metadata.getFolderId());
                            if (null == allowed) {
                                allowed = Boolean.valueOf(includeSubfolders && isSubfolderOf(drive, metadata.getFolderId(), folderId));
                                allowedFolders.put(metadata.getFolderId(), allowed);
                            }
                            if (false == allowed.booleanValue()) {
                                continue; // skip this file
                            }
                        }
                        files.add(metadata);
                    }
                    listRequest.setPageToken(fileList.getNextPageToken());
                } while (null != fileList.getNextPageToken());
                /*
                 * return sorted timed result
                 */
                sort(files, sort, order);
                return files;
            }
        }.perform(folderId);
    }

    /**
     * Gets a value indicating whether a folder is a subfolder (at any level) of a parent folder.
     *
     * @param drive A reference to the drive service
     * @param folderId The identifier of the folder to check
     * @param parentFolderId The identifier of the parent folder, or <code>null</code> to fall back to the root folder
     * @return <code>true</code> if the folder is a subfolder (at any level) of the parent folder, <code>false</code>, otherwise
     */
    boolean isSubfolderOf(Drive drive, String folderId, String parentFolderId) throws OXException, IOException {
        String driveId = toGoogleDriveFolderId(folderId);
        String rootDriveId = getRootFolderId();
        String parentDriveId = null != parentFolderId ? toGoogleDriveFolderId(parentFolderId) : rootDriveId;
        if (parentDriveId.equals(rootDriveId)) {
            return true;
        }
        if (driveId.equals(rootDriveId) || driveId.equals(parentDriveId)) {
            return false;
        }
        do {
            com.google.api.services.drive.model.File dir = drive.files().get(driveId).setFields(FIELDS_DEFAULT).execute();
            driveId = dir.getParents().get(0);
        } while (false == driveId.equals(parentDriveId) && false == driveId.equals(rootDriveId));
        return driveId.equals(parentDriveId);
    }

    /**
     * Gets metadata of a single file version in a folder.
     *
     * @param folderId The identifier of the parent folder
     * @param id The file identifier
     * @param version The version to get, or {@link FileStorageFileAccess#CURRENT_VERSION} to get the current one
     * @param fields The fields to include
     * @return The file
     */
    GoogleDriveFile getMetadata(String folderId, String id, String version, List<Field> fields) throws OXException {
        return new BackOffPerformer<GoogleDriveFile>(googleDriveAccess, account, session) {

            @Override
            GoogleDriveFile perform() throws OXException, IOException, RuntimeException {
                Drive drive = googleDriveAccess.<Drive> getClient().client;
                /*
                 * get single file
                 */
                com.google.api.services.drive.model.File file = drive.files().get(id).setFields(getFields(fields)).execute();
                checkFileValidity(file);
                String parentID = file.getParents().get(0);
                GoogleDriveFile metadata = createFile(parentID, id, file, fields);
                if (null != folderId && false == folderId.equals(metadata.getFolderId())) {
                    throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(id, folderId);
                }

                return metadata;
            }
        }.perform(id);
    }

    /**
     * Creates a {@link GoogleDriveFile} based on a {@link com.google.api.services.drive.model.File}.
     *
     * @param folderId The folder identifier to apply, or <code>null</code> to get the parent folder from the file
     * @param fileId The file identifier to apply
     * @param file The file
     * @param fields The fields to assign, or <code>null</code> to set all fields
     * @return The file
     */
    GoogleDriveFile createFile(String folderId, String fileId, com.google.api.services.drive.model.File file, List<Field> fields) throws OXException {
        if (null == folderId && null != file && null != file.getParents() && 0 < file.getParents().size()) {
            folderId = file.getParents().get(0);
        }
        List<Field> fieldsToUse = Field.addDateFieldsIfNeeded(fields, null);
        return new GoogleDriveFile(folderId, fileId, userId, getRootFolderId()).parseGoogleDriveFile(file, fieldsToUse);
    }

    void checkFileValidity(com.google.api.services.drive.model.File file) throws OXException {
        if (isDir(file)) {
            throw FileStorageExceptionCodes.NOT_A_FILE.create(GoogleDriveConstants.ID, file.getId());
        }
        checkIfTrashed(file);
    }

    /**
     * Sorts the supplied list of files if needed.
     *
     * @param files The files to sort
     * @param sort The sort order, or <code>null</code> if not specified
     * @param order The sort direction
     */
    static void sort(List<File> files, Field sort, SortDirection order) {
        if (null != sort && 1 < files.size()) {
            Collections.sort(files, order.comparatorBy(sort));
        }
    }

    /**
     * Gets the Google Drive fields to query from the service corresponding to the supplied {@link Field} collection. The mandatory {@link GoogleDriveConstants#FIELDS_DEFAULT} are always included.
     * <p>
     * Check <a href="https://developers.google.com/drive/api/v2/search-parameters">https://developers.google.com/drive/api/v2/search-parameters</a>
     *
     * @param requestedFields The fields as requested by the client, or {@link FileStorageFileAccess#ALL_FIELDS} to query all known fields
     * @param additionalFields Additional fields to include
     * @return The Google Drive fields as comma-separated string
     */
    static String getFields(List<Field> requestedFields, Field... additionalFields) {
        StringBuilder stringBuilder = new StringBuilder(FIELDS_DEFAULT);
        for (Field field : getUniqueFields(requestedFields, additionalFields)) {
            switch (field) {
                case CREATED:
                    stringBuilder.append(",createdTime");
                    break;
                case TITLE:
                    /* fall-through */
                case FILENAME:
                    stringBuilder.append(",name");
                    break;
                case FILE_SIZE:
                    stringBuilder.append(",size");
                    break;
                case URL:
                    stringBuilder.append(",webContentLink");
                    break;
                case DESCRIPTION:
                    stringBuilder.append(",description");
                    break;
                case FILE_MD5SUM:
                    stringBuilder.append(",md5Checksum");
                    break;
                default:
                    break;
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Gets a unique collection containing the fields based on the ones that were requested by the client, as well as additionally needed
     * fields.
     *
     * @param requestedFields The fields as requested by the client, or {@link FileStorageFileAccess#ALL_FIELDS} to query all known fields
     * @param additionalFields Additional fields to include
     * @return The unique fields
     */
    static Collection<Field> getUniqueFields(List<Field> requestedFields, Field... additionalFields) {
        if (ALL_FIELDS == requestedFields) {
            return Arrays.asList(Field.values());
        }

        Set<Field> fieldSet = new HashSet<>(requestedFields);
        if (null != additionalFields && 0 < additionalFields.length) {
            for (Field additionalField : additionalFields) {
                if (null != additionalField) {
                    fieldSet.add(additionalField);
                }
            }
        }
        return fieldSet;
    }

    /**
     * Escapes a pattern string to be used in Google Drive queries.
     *
     * @param pattern The pattern to escape
     * @return The escaped pattern
     */
    String escape(String pattern) {
        if (null == pattern) {
            return pattern;
        }

        StringBuilder opt = null;
        int length = pattern.length();
        for (int i = 0; i < length; i++) {
            char c = pattern.charAt(i);
            if ('\'' == c) {
                if (null == opt) {
                    opt = new StringBuilder(length);
                    if (i > 0) {
                        opt.append(pattern, 0, i);
                    }
                }
                opt.append("\\'");
            } else {
                if (null != opt) {
                    opt.append(c);
                }
            }
        }
        return null == opt ? pattern : opt.toString();
    }

    /**
     * Get the name of the file. If a file with the same name already exists the file name
     * will be incremented until it is unique.
     *
     * @param drive The {@link Drive}
     * @param destFolder The folder the file is in
     * @param name The name the file
     * @return The name of the file, eventually auto-incremented like <code>fileName(1)</code>
     * @throws IOException If listing files fails
     */
    String getFileName(Drive drive, String destFolder, String name) throws IOException {
        String fileName = name;
        String baseName;
        String ext;
        {
            int dotPos = name.lastIndexOf('.');
            if (dotPos > 0) {
                baseName = name.substring(0, dotPos);
                ext = name.substring(dotPos);
            } else {
                baseName = name;
                ext = "";
            }
        }
        int count = 1;
        boolean keepOn = true;
        while (keepOn) {
            com.google.api.services.drive.Drive.Files.List list = drive.files().list();
            list.setQ(new GoogleFileQueryBuilder(QUERY_STRING_FILES_ONLY_EXCLUDING_TRASH).equalsName(fileName).searchForChildren(destFolder).build());

            FileList childList = list.execute();
            if (childList.getFiles().isEmpty()) {
                keepOn = false;
            } else {
                fileName = new StringBuilder(baseName).append(" (").append(count++).append(')').append(ext).toString();
            }
        }
        return fileName;
    }

    boolean isFileNameChanged(Drive drive, File file) throws IOException {
        return Strings.isNotEmpty(file.getFileName()) && false == drive.files().get(file.getId()).setFields(NAME.getField()).execute().getName().equals(file.getFileName());
    }

    /**
     * Deletes the specified Files from the user's Drive.
     *
     * @param drive The drive API
     * @param hardDelete <code>true</code> to delete permanently, <code>false</code> to put them in trash
     * @param childList The files to delete
     * @throws IOException if an I/O error is occurred
     */
    void deleteFiles(Drive drive, boolean hardDelete, FileList childList) throws IOException {
        for (com.google.api.services.drive.model.File child : childList.getFiles()) {
            if (hardDelete) {
                drive.files().delete(child.getId()).execute();
            } else {
                trashFile(drive, child.getId());
            }
        }
    }

    /**
     * Handles the file name change
     *
     * @param update The updated metadata of the file
     * @param modifiedFields The modified fields
     * @param srcFile The source file
     * @param toUpdate The update payload
     */
    void handleNameChange(File update, List<Field> modifiedFields, com.google.api.services.drive.model.File srcFile, com.google.api.services.drive.model.File toUpdate) {
        if (update == null) {
            return;
        }

        if (false == isFileNameChanged(update, modifiedFields, srcFile)) {
            return;
        }
        toUpdate.setName(update.getTitle());
    }

    /**
     * Checks whether the file name was modified
     *
     * @param update The updated metadata of the file
     * @param modifiedFields The modified fields
     * @param srcFile The source file
     * @return <code>true</code> if the file name was modified, <code>false</code> otherwise
     */
    boolean isFileNameChanged(File update, List<Field> modifiedFields, com.google.api.services.drive.model.File srcFile) {
        return Strings.isNotEmpty(update.getTitle()) && (null == modifiedFields || modifiedFields.contains(File.Field.FILENAME)) && false == update.getTitle().equals(srcFile.getName());
    }

    /**
     * Checks in the specified list with files
     *
     * @param hits The list with the files
     * @param filename The filename to search for
     * @return <code>true</code> if the file is contained in the hits list, <code>false</code> otherwise
     */
    boolean containsFileName(List<File> hits, String filename) {
        for (File file : hits) {
            if (file.getFileName().equals(filename)) {
                return true;
            }
        }
        return false;
    }
}
