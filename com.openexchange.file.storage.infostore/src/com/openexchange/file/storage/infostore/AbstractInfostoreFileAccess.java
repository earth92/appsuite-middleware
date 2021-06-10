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

package com.openexchange.file.storage.infostore;

import static com.openexchange.file.storage.FileStorageUtility.checkUrl;
import static com.openexchange.file.storage.infostore.internal.FieldMapping.getMatching;
import static com.openexchange.file.storage.infostore.internal.FieldMapping.getSortDirection;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.Document;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.File.Field;
import com.openexchange.file.storage.FileStorageAdvancedSearchFileAccess;
import com.openexchange.file.storage.FileStorageCaseInsensitiveAccess;
import com.openexchange.file.storage.FileStorageCountableFolderFileAccess;
import com.openexchange.file.storage.FileStorageEfficientRetrieval;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageExtendedMetadata;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStorageLockedFileAccess;
import com.openexchange.file.storage.FileStorageMultiMove;
import com.openexchange.file.storage.FileStoragePersistentIDs;
import com.openexchange.file.storage.FileStorageRandomFileAccess;
import com.openexchange.file.storage.FileStorageRangeFileAccess;
import com.openexchange.file.storage.FileStorageRestoringFileAccess;
import com.openexchange.file.storage.FileStorageSequenceNumberProvider;
import com.openexchange.file.storage.FileStorageVersionedFileAccess;
import com.openexchange.file.storage.FileStorageZippableFolderFileAccess;
import com.openexchange.file.storage.ObjectPermissionAware;
import com.openexchange.file.storage.Range;
import com.openexchange.file.storage.infostore.internal.FieldMapping;
import com.openexchange.file.storage.infostore.internal.Utils;
import com.openexchange.file.storage.search.SearchTerm;
import com.openexchange.groupware.infostore.DocumentAndMetadata;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreFacade;
import com.openexchange.groupware.infostore.InfostoreSearchEngine;
import com.openexchange.groupware.infostore.utils.Metadata;
import com.openexchange.groupware.results.Delta;
import com.openexchange.groupware.results.TimedResult;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.session.ServerSession;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * {@link AbstractInfostoreFileAccess}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since 7.10.5
 */
public abstract class AbstractInfostoreFileAccess extends InfostoreAccess implements FileStorageRandomFileAccess, FileStorageSequenceNumberProvider, FileStorageAdvancedSearchFileAccess, FileStoragePersistentIDs, FileStorageVersionedFileAccess, FileStorageLockedFileAccess, FileStorageEfficientRetrieval, ObjectPermissionAware, FileStorageRangeFileAccess, FileStorageExtendedMetadata, FileStorageMultiMove, FileStorageZippableFolderFileAccess, FileStorageCountableFolderFileAccess, FileStorageCaseInsensitiveAccess, FileStorageRestoringFileAccess {

    protected final InfostoreSearchEngine search;
    protected final ServerSession session;

    /**
     * Initializes a new {@link AbstractInfostoreFileAccess}.
     *
     * @param session The session
     * @param infostore The underlying infostore facade
     * @param search The underlying infostore search engine
     */
    protected AbstractInfostoreFileAccess(ServerSession session, InfostoreFacade infostore, InfostoreSearchEngine search) {
        super(infostore);
        this.session = session;
        this.search = search;
    }

    /**
     * Gets the utility to convert {@link File} arguments to their {@link DocumentMetadata} equivalents and vice-versa.
     * 
     * @return The file converter
     */
    protected FileConverter getConverter() {
        return new FileConverter();
    }

    @Override
    public boolean exists(String folderId, String id, String version) throws OXException {
        try {
            return getInfostore(folderId).exists(ID(id), null == version ? -1 : Utils.parseUnsignedInt(version), session);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public InputStream getDocument(String folderId, String id, String version) throws OXException {
        try {
            return getInfostore(folderId).getDocument(ID(id), null == version ? -1 : Utils.parseUnsignedInt(version), session);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public InputStream getDocument(String folderId, String id, String version, long offset, long length) throws OXException {
        try {
            return getInfostore(folderId).getDocument(ID(id), null == version ? -1 : Utils.parseUnsignedInt(version), offset, length, session);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public File getFileMetadata(String folderId, String id, String version) throws OXException {
        try {
            DocumentMetadata metadata;
            if (null == folderId) {
                metadata = getInfostore(folderId).getDocumentMetadata(-1, ID(id), null == version ? -1 : Utils.parseUnsignedInt(version), session);
            } else {
                metadata = getInfostore(folderId).getDocumentMetadata(FOLDERID(folderId), ID(id), null == version ? -1 : Utils.parseUnsignedInt(version), session);
                if (0 < metadata.getFolderId() && false == folderId.equals(Long.toString(metadata.getFolderId()))) {
                    throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(id, folderId);
                }
            }
            return getConverter().getFile(metadata);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public Document getDocumentAndMetadata(String folderId, String id, String version) throws OXException {
        return getDocumentAndMetadata(folderId, id, version, null);
    }

    @Override
    public Document getDocumentAndMetadata(String folderId, String id, String version, String clientETag) throws OXException {
        try {
            DocumentAndMetadata document;
            if (null == folderId) {
                document = getInfostore(folderId).getDocumentAndMetadata(-1, ID(id), null == version ? -1 : ID(version), clientETag, session);
            } else {
                document = getInfostore(folderId).getDocumentAndMetadata(FOLDERID(folderId), ID(id), null == version ? -1 : ID(version), clientETag, session);
                long documentFolderId = null != document.getMetadata() ? document.getMetadata().getFolderId() : 0;
                if (0 < documentFolderId && false == folderId.equals(String.valueOf(documentFolderId))) {
                    throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(id, folderId);
                }
            }
            return getConverter().getFileDocument(document);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public void lock(String folderId, String id, long diff) throws OXException {
        try {
            getInfostore(folderId).lock(ID(id), diff, session);
        } catch (NumberFormatException e) {
            throw FileStorageExceptionCodes.FILE_NOT_FOUND.create(e, id, folderId);
        }
    }

    @Override
    public void removeDocument(String folderId, long sequenceNumber) throws OXException {
        getInfostore(folderId).removeDocument(FOLDERID(folderId), sequenceNumber, session);
    }

    @Override
    public List<IDTuple> removeDocument(List<IDTuple> ids, long sequenceNumber) throws OXException {
        return removeDocument(ids, sequenceNumber, false);
    }

    @Override
    public List<IDTuple> removeDocument(List<IDTuple> ids, long sequenceNumber, boolean hardDelete) throws OXException {
        InfostoreFacade infostore = getInfostore(null);
        return infostore.removeDocument(ids, sequenceNumber, session, hardDelete);
    }

    @Override
    public String[] removeVersion(String folderId, String id, String[] versions) throws OXException {
        return toStrings(getInfostore(folderId).removeVersion(ID(id), parseInts(versions), session));
    }

    private static int[] parseInts(String[] sa) {
        if (null == sa) {
            return null;
        }
        int[] ret = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            String version = sa[i];
            ret[i] = null == version ? -1 : Utils.parseUnsignedInt(version);
        }
        return ret;
    }

    private static String[] toStrings(int[] ia) {
        if (null == ia) {
            return null;
        }
        String[] ret = new String[ia.length];
        for (int i = 0; i < ia.length; i++) {
            int iVersion = ia[i];
            ret[i] = iVersion < 0 ? null : Integer.toString(iVersion);
        }
        return ret;
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber) throws OXException {
        checkUrl(file);
        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocument(metadata, data, sequenceNumber, session);
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber, List<Field> modifiedFields) throws OXException {
        if (modifiedFields.contains(Field.URL)) {
            checkUrl(file);
        }

        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocument(metadata, data, sequenceNumber, FieldMapping.getMatching(modifiedFields), session);
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber, List<Field> modifiedFields, long offset) throws OXException {
        if (modifiedFields.contains(Field.URL)) {
            checkUrl(file);
        }

        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocument(metadata, data, sequenceNumber, FieldMapping.getMatching(modifiedFields), offset, session);
    }

    @Override
    public IDTuple saveDocument(File file, InputStream data, long sequenceNumber, List<Field> modifiedFields, boolean ignoreVersion) throws OXException {
        if (modifiedFields.contains(Field.URL)) {
            checkUrl(file);
        }

        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocument(metadata, data, sequenceNumber, FieldMapping.getMatching(modifiedFields), ignoreVersion, session);
    }

    @Override
    public IDTuple saveDocumentTryAddVersion(File file, InputStream data, long sequenceNumber, List<Field> modifiedFields) throws OXException {
        if (modifiedFields.contains(Field.URL)) {
            checkUrl(file);
        }

        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocumentTryAddVersion(metadata, data, sequenceNumber, FieldMapping.getMatching(modifiedFields), session);
    }

    @Override
    public IDTuple saveFileMetadata(File file, long sequenceNumber) throws OXException {
        checkUrl(file);
        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocumentMetadata(metadata, sequenceNumber, session);
    }

    @Override
    public IDTuple saveFileMetadata(File file, long sequenceNumber, List<Field> modifiedFields) throws OXException {
        if (modifiedFields.contains(Field.URL)) {
            checkUrl(file);
        }

        DocumentMetadata metadata = getConverter().getMetadata(file);
        return getInfostore(file.getFolderId()).saveDocumentMetadata(metadata, sequenceNumber, FieldMapping.getMatching(modifiedFields), session);
    }

    @Override
    public void touch(String folderId, String id) throws OXException {
        getInfostore(folderId).touch(ID(id), session);
    }

    @Override
    public void unlock(String folderId, String id) throws OXException {
        getInfostore(folderId).unlock(ID(id), session);
    }

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, boolean ignoreDeleted) throws OXException {
        Delta<DocumentMetadata> delta = getInfostore(folderId).getDelta(FOLDERID(folderId), updateSince, FieldMapping.getMatching(fields), ignoreDeleted, session);
        return getConverter().getFileDelta(delta);
    }

    @Override
    public Delta<File> getDelta(String folderId, long updateSince, List<Field> fields, Field sort, SortDirection order, boolean ignoreDeleted) throws OXException {
        Delta<DocumentMetadata> delta = getInfostore(folderId).getDelta(FOLDERID(folderId), updateSince, FieldMapping.getMatching(fields), FieldMapping.getMatching(sort), FieldMapping.getSortDirection(order), ignoreDeleted, session);
        return getConverter().getFileDelta(delta);
    }

    @Override
    public Map<String, Long> getSequenceNumbers(List<String> folderIds) throws OXException {
        /*
         * filter virtual folders
         */
        Map<String, Long> sequenceNumbers = new HashMap<>(folderIds.size());
        List<Long> foldersToQuery = new ArrayList<>(folderIds.size());
        for (String folderId : folderIds) {
            Long id = Long.valueOf(Utils.parseUnsignedLong(folderId));
            if (VIRTUAL_FOLDERS.contains(id)) {
                sequenceNumbers.put(folderId, Long.valueOf(0L));
            } else {
                foldersToQuery.add(id);
            }
        }
        /*
         * query infostore for non-virtual ones
         */
        if (0 < foldersToQuery.size()) {
            Map<Long, Long> infostoreNumbers = infostore.getSequenceNumbers(foldersToQuery, true, session);
            for (Map.Entry<Long, Long> entry : infostoreNumbers.entrySet()) {
                sequenceNumbers.put(String.valueOf(entry.getKey().longValue()), entry.getValue());
            }
        }
        return sequenceNumbers;
    }

    @Override
    public TimedResult<File> getDocuments(String folderId) throws OXException {
        TimedResult<DocumentMetadata> documents = getInfostore(folderId).getDocuments(FOLDERID(folderId), session);
        return getConverter().getFileTimedResult(documents);
    }

    @Override
    public TimedResult<File> getDocuments(String folderId, List<Field> fields) throws OXException {
        TimedResult<DocumentMetadata> documents = getInfostore(folderId).getDocuments(FOLDERID(folderId), FieldMapping.getMatching(fields), session);
        return getConverter().getFileTimedResult(documents);
    }

    @Override
    public TimedResult<File> getDocuments(String folderId, List<Field> fields, Field sort, SortDirection order) throws OXException {
        TimedResult<DocumentMetadata> documents = getInfostore(folderId).getDocuments(FOLDERID(folderId), FieldMapping.getMatching(fields), FieldMapping.getMatching(sort), FieldMapping.getSortDirection(order), session);
        return getConverter().getFileTimedResult(documents);
    }

    @Override
    public TimedResult<File> getDocuments(String folderId, List<Field> fields, Field sort, SortDirection order, Range range) throws OXException {
        if (null == range) {
            return getDocuments(folderId, fields, sort, order);
        }

        TimedResult<DocumentMetadata> documents = getInfostore(folderId).getDocuments(FOLDERID(folderId), FieldMapping.getMatching(fields), FieldMapping.getMatching(sort), FieldMapping.getSortDirection(order), range.from, range.to, session);
        return getConverter().getFileTimedResult(documents);
    }

    @Override
    public TimedResult<File> getDocuments(List<IDTuple> ids, List<Field> fields) throws OXException {
        try {
            TimedResult<DocumentMetadata> documents = getInfostore(null).getDocuments(ids, FieldMapping.getMatching(fields), session);
            return getConverter().getFileTimedResult(documents);
        } catch (IllegalAccessException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public SearchIterator<File> getUserSharedDocuments(List<Field> fields, Field sort, SortDirection order) throws OXException {
        TimedResult<DocumentMetadata> documents = getInfostore(null).getUserSharedDocuments(getMatching(fields), getMatching(sort), getSortDirection(order), -1, -1, session);
        return getConverter().getFileSearchIterator(documents.results());
    }

    @Override
    public TimedResult<File> getVersions(String folderId, String id) throws OXException {
        TimedResult<DocumentMetadata> versions = getInfostore(folderId).getVersions(ID(id), session);
        return getConverter().getFileTimedResult(versions);
    }

    @Override
    public TimedResult<File> getVersions(String folderId, String id, List<Field> fields) throws OXException {
        TimedResult<DocumentMetadata> versions = getInfostore(folderId).getVersions(FOLDERID(folderId), ID(id), FieldMapping.getMatching(fields), session);
        return getConverter().getFileTimedResult(versions);
    }

    @Override
    public TimedResult<File> getVersions(String folderId, String id, List<Field> fields, Field sort, SortDirection order) throws OXException {
        TimedResult<DocumentMetadata> versions = getInfostore(folderId).getVersions(FOLDERID(folderId), ID(id), FieldMapping.getMatching(fields), FieldMapping.getMatching(sort), FieldMapping.getSortDirection(order), session);
        return getConverter().getFileTimedResult(versions);
    }

    @Override
    public SearchIterator<File> search(String pattern, List<Field> fields, String folderId, Field sort, SortDirection order, int start, int end) throws OXException {
        return search(pattern, fields, folderId, false, sort, order, start, end);
    }

    @Override
    public SearchIterator<File> search(String pattern, List<Field> fields, String folderId, boolean includeSubfolders, Field sort, SortDirection order, int start, int end) throws OXException {
        int folder = (folderId == null) ? InfostoreSearchEngine.NO_FOLDER : Utils.parseUnsignedInt(folderId);
        return new InfostoreSearchIterator(search.search(session, pattern, folder, includeSubfolders, getMatching(fields), getMatching(sort), getSortDirection(order), start, end));
    }

    @Override
    public SearchIterator<File> search(List<String> folderIds, SearchTerm<?> searchTerm, List<Field> fields, Field sort, SortDirection order, int start, int end) throws OXException {
        TIntList fids = new TIntArrayList(null == folderIds ? 0 : folderIds.size());
        if (null != folderIds) {
            for (String folderId : folderIds) {
                try {
                    fids.add(Utils.parseUnsignedInt(folderId));
                } catch (NumberFormatException e) {
                    throw FileStorageExceptionCodes.INVALID_FOLDER_IDENTIFIER.create(e, folderId);
                }
            }
        }

        ToInfostoreTermVisitor visitor = new ToInfostoreTermVisitor();
        searchTerm.visit(visitor);
        return getConverter().getFileSearchIterator(search.search(session, visitor.getInfostoreTerm(), fids.toArray(), getMatching(fields), getMatching(sort), getSortDirection(order), start, end));
    }

    @Override
    public SearchIterator<File> search(String folderId, boolean includeSubfolders, SearchTerm<?> searchTerm, List<Field> fields, Field sort, SortDirection order, int start, int end) throws OXException {
        int folder = null == folderId ? InfostoreSearchEngine.NO_FOLDER : Utils.parseUnsignedInt(folderId);
        ToInfostoreTermVisitor visitor = new ToInfostoreTermVisitor();
        searchTerm.visit(visitor);
        return getConverter().getFileSearchIterator(search.search(session, visitor.getInfostoreTerm(), folder, includeSubfolders, getMatching(fields), getMatching(sort), getSortDirection(order), start, end));
    }

    @Override
    public void commit() throws OXException {
        infostore.commit();
    }

    @Override
    public void finish() throws OXException {
        infostore.finish();
    }

    @Override
    public void rollback() throws OXException {
        infostore.rollback();
    }

    @Override
    public void setCommitsTransaction(boolean commits) {
        infostore.setCommitsTransaction(commits);
    }

    @Override
    public void setRequestTransactional(boolean transactional) {
        infostore.setRequestTransactional(transactional);
    }

    @Override
    public void setTransactional(boolean transactional) {
        infostore.setTransactional(transactional);
    }

    @Override
    public void startTransaction() throws OXException {
        infostore.startTransaction();
    }

    @Override
    public IDTuple copy(IDTuple source, String version, String destFolder, File update, InputStream newFile, List<File.Field> modifiedFields) throws OXException {
        InfostoreFacade infostoreFacade = getInfostore(destFolder);
        Metadata[] modifiedColumns = FieldMapping.getMatching(modifiedFields);

        DocumentMetadata updateDocument = null != update ? getConverter().getMetadata(update) : null;
        return infostoreFacade.copyDocument(session, source, null == version ? -1 : Utils.parseUnsignedInt(version), updateDocument, modifiedColumns, newFile, UNDEFINED_SEQUENCE_NUMBER, destFolder);
    }

    @Override
    public IDTuple move(IDTuple source, String destFolder, long sequenceNumber, File update, List<Field> modifiedFields) throws OXException {
        /*
         * use saveFileMetadata method with adjusted folder; the file ID is sufficient to identify the source
         */
        update.setFolderId(destFolder);
        update.setId(source.getId());

        if (modifiedFields.contains(Field.URL)) {
            checkUrl(update);
        }

        DocumentMetadata document = getConverter().getMetadata(update);
        Metadata[] modifiedColumns = FieldMapping.getMatching(modifiedFields);

        getInfostore(update.getFolderId()).saveDocumentMetadata(document, sequenceNumber, modifiedColumns, session);
        return new IDTuple(update.getFolderId(), update.getId());
    }

    @Override
    public List<IDTuple> move(List<IDTuple> sources, String destFolder, long sequenceNumber, boolean adjustFilenamesAsNeeded) throws OXException {
        int size;
        if (null == sources || (size = sources.size()) <= 0) {
            return Collections.emptyList();
        }

        // Check if all denoted files are located in the same folder
        boolean sameFolder = true;
        for (int i = size; sameFolder && i-- > 1;) {
            sameFolder = sources.get(i).getFolder().equals(sources.get(i - 1).getFolder());
        }

        // All in the same folder...
        if (sameFolder) {
            // ... yes
            return doMove(sources.get(0).getFolder(), sources, destFolder, sequenceNumber, adjustFilenamesAsNeeded);
        }

        // ... no, different folders. Split by folder identifiers,
        Map<String, List<IDTuple>> folder2ids = new LinkedHashMap<>(size);
        for (IDTuple idTuple : sources) {
            String folder = idTuple.getFolder();
            List<IDTuple> ids = folder2ids.get(folder);
            if (null == ids) {
                ids = new ArrayList<>();
                folder2ids.put(folder, ids);
            }
            ids.add(idTuple);
        }

        List<IDTuple> retval = new ArrayList<>(size);
        for (Map.Entry<String, List<IDTuple>> filesInFolder : folder2ids.entrySet()) {
            retval.addAll(doMove(filesInFolder.getKey(), filesInFolder.getValue(), destFolder, sequenceNumber, adjustFilenamesAsNeeded));
        }
        return retval;
    }

    private List<IDTuple> doMove(String folderId, List<IDTuple> filesInFolder, String destFolder, long sequenceNumber, boolean adjustFilenamesAsNeeded) throws OXException {
        return getInfostore(folderId).moveDocuments(session, filesInFolder, sequenceNumber, destFolder, adjustFilenamesAsNeeded);
    }

    @Override
    public Map<IDTuple, FileStorageFolder[]> restore(List<IDTuple> tuples, String destFolderId) throws OXException {
        FileStorageFolderAccess folderAccess = getAccountAccess().getFolderAccess();
        Map<IDTuple, String> restored = getInfostore(destFolderId).restore(tuples, FOLDERID(destFolderId), session);
        Map<IDTuple, FileStorageFolder[]> result = new LinkedHashMap<>(restored.size());
        for (Map.Entry<IDTuple, String> r : restored.entrySet()) {
            FileStorageFolder[] restoredPath = r.getValue() != null ? folderAccess.getPath2DefaultFolder(r.getValue()) : null;
            result.put(IDTuple.copy(r.getKey()), restoredPath);
        }
        return result;
    }


    @Override
    public List<Field> getSupportedFields() {
        // all supported
        return Arrays.asList(File.Field.values());
    }
}
