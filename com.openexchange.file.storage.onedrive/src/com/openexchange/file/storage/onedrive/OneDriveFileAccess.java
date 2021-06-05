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

package com.openexchange.file.storage.onedrive;

import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.DefaultFile;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileDelta;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageAutoRenameFoldersAccess;
import com.openexchange.file.storage.FileStorageCaseInsensitiveAccess;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStoragePersistentIDs;
import com.openexchange.file.storage.FileStorageSequenceNumberProvider;
import com.openexchange.file.storage.FileTimedResult;
import com.openexchange.file.storage.ThumbnailAware;
import com.openexchange.file.storage.onedrive.access.OneDriveOAuthAccess;
import com.openexchange.groupware.results.Delta;
import com.openexchange.groupware.results.TimedResult;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Streams;
import com.openexchange.microsoft.graph.onedrive.MicrosoftGraphDriveService;
import com.openexchange.session.Session;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorAdapter;

/**
 * {@link OneDriveFileAccess} - Just a light-weighted proxy that bridges the Infostore and the real
 * the real service that handles the actual requests, {@link MicrosoftGraphDriveService}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class OneDriveFileAccess extends AbstractOneDriveResourceAccess implements ThumbnailAware, FileStorageSequenceNumberProvider, FileStorageCaseInsensitiveAccess, FileStorageAutoRenameFoldersAccess, FileStoragePersistentIDs {

    private final OneDriveAccountAccess accountAccess;
    private final OneDriveFolderAccess folderAccess;
    final int userId;

    /**
     * Initializes a new {@link OneDriveFileAccess}.
     *
     * @param oneDriveAccess The underlying One Drive access
     * @param account The underlying account
     * @param session The session The account access
     * @param accountAccess The account access
     * @throws OXException if the {@link MicrosoftGraphDriveService} is absent
     */
    public OneDriveFileAccess(OneDriveOAuthAccess oneDriveAccess, FileStorageAccount account, Session session, OneDriveAccountAccess accountAccess, OneDriveFolderAccess folderAccess) throws OXException {
        super(oneDriveAccess, session);
        this.accountAccess = accountAccess;
        this.folderAccess = folderAccess;
        this.userId = session.getUserId();
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
    public boolean exists(final String folderId, final String id, final String version) throws OXException {
        return perform(new OneDriveClosure<Boolean>() {

            @Override
            protected Boolean doPerform() throws OXException {
                try {
                    return Autoboxing.valueOf(null != getFileMetadata(folderId, id, version));
                } catch (OXException e) {
                    if (FileStorageExceptionCodes.FILE_NOT_FOUND.equals(e)) {
                        return Boolean.FALSE;
                    }
                    throw e;
                }
            }
        }).booleanValue();
    }

    @Override
    public File getFileMetadata(final String folderId, final String id, final String version) throws OXException {
        if (CURRENT_VERSION != version) {
            throw FileStorageExceptionCodes.VERSIONING_NOT_SUPPORTED.create(OneDriveConstants.ID);
        }
        return perform(new OneDriveClosure<File>() {

            @Override
            protected File doPerform() throws OXException {
                return driveService.getFile(userId, getAccessToken(), id);
            }
        });
    }

    @Override
    public IDTuple saveFileMetadata(File file, long sequenceNumber) throws OXException {
        return saveFileMetadata(file, sequenceNumber, null);
    }

    @Override
    public IDTuple saveFileMetadata(final File file, final long sequenceNumber, final List<Field> modifiedFields) throws OXException {
        if (FileStorageFileAccess.NEW == file.getId()) {
            // create new, empty file ("touch")
            return saveDocument(file, Streams.EMPTY_INPUT_STREAM, sequenceNumber, modifiedFields);
        }
        return perform(new OneDriveClosure<IDTuple>() {

            @Override
            protected IDTuple doPerform() throws OXException {
                // rename / description change
                return new IDTuple(file.getFolderId(), driveService.updateFile(getAccessToken(), file, modifiedFields, null));
            }
        });
    }

    @Override
    public IDTuple copy(final IDTuple source, String version, final String destFolder, final File update, final InputStream newFile, final List<Field> modifiedFields) throws OXException {
        if (version != CURRENT_VERSION) {
            // can only copy the current revision
            throw FileStorageExceptionCodes.VERSIONING_NOT_SUPPORTED.create(OneDriveConstants.ID);
        }
        return perform(new OneDriveClosure<IDTuple>() {

            @Override
            protected IDTuple doPerform() throws OXException {
                if (update == null) {
                    return new IDTuple(destFolder, driveService.copyFile(getAccessToken(), source.getId(), toOneDriveFolderId(destFolder)));
                }
                return new IDTuple(destFolder, driveService.copyFile(getAccessToken(), source.getId(), update, modifiedFields, destFolder));
            }
        });
    }

    @Override
    public IDTuple move(final IDTuple source, final String destFolder, long sequenceNumber, final File update, final List<File.Field> modifiedFields) throws OXException {
        return perform(new OneDriveClosure<IDTuple>() {

            @Override
            protected IDTuple doPerform() throws OXException {
                // perform move operation
                if (update == null) {
                    return new IDTuple(destFolder, driveService.moveFile(getAccessToken(), source.getId(), toOneDriveFolderId(destFolder)));
                }
                // move and update additional metadata as needed
                return new IDTuple(destFolder, driveService.updateFile(getAccessToken(), new DefaultFile(update), modifiedFields, destFolder));
            }
        });
    }

    @Override
    public InputStream getDocument(final String folderId, final String id, final String version) throws OXException {
        return perform(new OneDriveClosure<InputStream>() {

            @Override
            protected InputStream doPerform() throws OXException {
                return driveService.getFile(getAccessToken(), id);
            }
        });
    }

    @Override
    public InputStream getThumbnailStream(String folderId, final String id, String version) throws OXException {
        return perform(new OneDriveClosure<InputStream>() {

            @Override
            protected InputStream doPerform() throws OXException {
                return driveService.getThumbnail(getAccessToken(), id);
            }
        });
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber) throws OXException {
        return saveDocument(file, data, sequenceNumber, null);
    }

    @Override
    public IDTuple saveDocument(final File file, final InputStream data, final long sequenceNumber, final List<Field> modifiedFields) throws OXException {
        return perform(new OneDriveClosure<IDTuple>() {

            @Override
            protected IDTuple doPerform() throws OXException {
                return new IDTuple(file.getFolderId(), driveService.upload(getAccessToken(), file, data));
            }
        });
    }

    @Override
    public void removeDocument(final String folderId, long sequenceNumber) throws OXException {
        perform(new OneDriveClosure<Void>() {

            @Override
            protected Void doPerform() throws OXException {
                driveService.clearFolder(getAccessToken(), toOneDriveFolderId(folderId));
                return null;
            }
        });
    }

    @Override
    public List<IDTuple> removeDocument(List<IDTuple> ids, long sequenceNumber) throws OXException {
        return removeDocument(ids, sequenceNumber, false);
    }

    @Override
    public List<IDTuple> removeDocument(final List<IDTuple> ids, long sequenceNumber, final boolean hardDelete) throws OXException {
        return perform(new OneDriveClosure<List<IDTuple>>() {

            @Override
            protected List<IDTuple> doPerform() throws OXException {
                for (IDTuple id : ids) {
                    driveService.deleteFile(getAccessToken(), id.getId());
                }
                return Collections.emptyList();
            }
        });
    }

    @Override
    public void touch(String folderId, String id) throws OXException {
        exists(folderId, id, CURRENT_VERSION);
    }

    @Override
    public TimedResult<File> getDocuments(final String folderId) throws OXException {
        return perform(new OneDriveClosure<TimedResult<File>>() {

            @Override
            protected TimedResult<File> doPerform() throws OXException {
                return new FileTimedResult(new LinkedList<>(driveService.getFiles(userId, getAccessToken(), toOneDriveFolderId(folderId))));
            }
        });
    }

    @Override
    public TimedResult<File> getDocuments(final String folderId, final List<Field> fields) throws OXException {
        return getDocuments(folderId);
    }

    @Override
    public TimedResult<File> getDocuments(final String folderId, List<Field> fields, final Field sort, final SortDirection order) throws OXException {
        return perform(new OneDriveClosure<TimedResult<File>>() {

            @Override
            protected TimedResult<File> doPerform() throws OXException {
                return new FileTimedResult(new LinkedList<>(driveService.getFiles(userId, getAccessToken(), toOneDriveFolderId(folderId))));
            }
        });
    }

    @Override
    public TimedResult<File> getDocuments(final List<IDTuple> ids, List<Field> fields) throws OXException {
        return perform(new OneDriveClosure<TimedResult<File>>() {

            @Override
            protected TimedResult<File> doPerform() throws OXException {
                List<String> itemIds = ids.stream().map(IDTuple::getId).collect(Collectors.<String> toList());
                return new FileTimedResult(new LinkedList<>(driveService.getFiles(userId, getAccessToken(), itemIds)));
            }
        });
    }

    private static final SearchIterator<File> EMPTY_ITER = SearchIteratorAdapter.emptyIterator();

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, boolean ignoreDeleted) throws OXException {
        return new FileDelta(EMPTY_ITER, EMPTY_ITER, EMPTY_ITER, 0L);
    }

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, Field sort, SortDirection order, boolean ignoreDeleted) throws OXException {
        return new FileDelta(EMPTY_ITER, EMPTY_ITER, EMPTY_ITER, 0L);
    }

    @Override
    public SearchIterator<File> search(final String pattern, List<Field> fields, final String folderId, final Field sort, final SortDirection order, final int start, final int end) throws OXException {
        return search(pattern, fields, folderId, false, sort, order, start, end);
    }

    @Override
    public SearchIterator<File> search(final String pattern, List<Field> fields, final String folderId, final boolean includeSubfolders, final Field sort, final SortDirection order, final int start, final int end) throws OXException {
        return perform(new OneDriveClosure<SearchIterator<File>>() {

            @Override
            protected SearchIterator<File> doPerform() throws OXException {
                List<File> files = new LinkedList<>(driveService.searchFiles(end, getAccessToken(), pattern, folderId, includeSubfolders));
                // Sort collection
                sort(files, sort, order);
                if ((start != NOT_SET) && (end != NOT_SET)) {
                    final int size = files.size();
                    if ((start) > size) {
                        // Return empty iterator if start is out of range
                        return SearchIteratorAdapter.emptyIterator();
                    }
                    // Reset end index if out of range
                    int toIndex = end;
                    if (toIndex >= size) {
                        toIndex = size;
                    }
                    files = files.subList(start, toIndex);
                }

                return new SearchIteratorAdapter<>(files.iterator(), files.size());
            }
        });
    }

    @Override
    public FileStorageAccountAccess getAccountAccess() {
        return accountAccess;
    }

    @Override
    public Map<String, Long> getSequenceNumbers(List<String> folderIds) throws OXException {
        if (null == folderIds || 0 == folderIds.size()) {
            return Collections.emptyMap();
        }
        FileStorageFolderAccess folderAccess = getAccountAccess().getFolderAccess();
        Map<String, Long> sequenceNumbers = new HashMap<>(folderIds.size());
        for (String folderId : folderIds) {
            Date lastModifiedDate = folderAccess.getFolder(folderId).getLastModifiedDate();
            sequenceNumbers.put(folderId, null != lastModifiedDate ? Long.valueOf(lastModifiedDate.getTime()) : null);
        }
        return sequenceNumbers;
    }

    ////////////////////// NO-OPS ////////////////////////

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
    public void setRequestTransactional(final boolean transactional) {
        // Nope
    }

    @Override
    public void setCommitsTransaction(final boolean commits) {
        // Nope
    }

    //////////////////////////// HELPERS ///////////////////////////

    /**
     * Sorts the supplied list of files if needed.
     *
     * @param files The files to sort
     * @param sort The sort order, or <code>null</code> if not specified
     * @param order The sort direction
     */
    protected static void sort(List<File> files, Field sort, SortDirection order) {
        if (null != sort && 1 < files.size()) {
            Collections.sort(files, order.comparatorBy(sort));
        }
    }
}
