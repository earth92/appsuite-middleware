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

package com.openexchange.groupware.infostore.database.impl;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import com.openexchange.database.DatabaseExceptionCodes;
import com.openexchange.database.Databases;
import com.openexchange.database.EmptyResultSet;
import com.openexchange.database.IncorrectStringSQLException;
import com.openexchange.database.StringLiteralSQLException;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.NameBuilder;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.database.FilenameReservation;
import com.openexchange.groupware.infostore.database.FilenameReserver;
import com.openexchange.java.util.UUIDs;

/**
 * {@link FilenameReserverImpl}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class FilenameReserverImpl implements FilenameReserver {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FilenameReserverImpl.class);
    private static final FilenameReservation PLACEHOLDER_RESERVATION = new FilenameReservationImpl(UUIDs.toByteArray(UUID.randomUUID()), null, false, false);

    private final Context context;
    private final DBProvider provider;
    private final List<FilenameReservationImpl> performedReservations;

    /**
     * Initializes a new {@link FilenameReserverImpl}.
     *
     * @param context The context to work on
     * @param provider the provider to use
     */
    public FilenameReserverImpl(Context context, DBProvider provider) {
        super();
        this.context = context;
        this.provider = provider;
        performedReservations = new ArrayList<FilenameReservationImpl>();
    }

    /**
     * Silently cleans up any previous reservation held by this filename reserver.
     */
    @Override
    public void cleanUp() {
        if (0 < performedReservations.size()) {
            destroySilently(performedReservations);
        }
    }

    @Override
    public FilenameReservation reserve(DocumentMetadata document, boolean adjustAsNeeded) throws OXException {
        return reserve(Collections.singletonList(document), adjustAsNeeded).get(document);
    }

    /**
     * Reserves the filenames of the supplied documents in their target folders.
     *
     * @param documents The documents to reserve the filenames for
     * @param adjustAsNeeded <code>true</code> to automatically adjust the filenames in case of conflicts in the target folder,
     *                       <code>false</code>, otherwise
     * @return The reservations, each one mapped to its corresponding document
     * @throws OXException
     */
    @Override
    public Map<DocumentMetadata, FilenameReservation> reserve(List<DocumentMetadata> documents, boolean adjustAsNeeded) throws OXException {
        if (null == documents || 0 == documents.size()) {
            return Collections.emptyMap();
        }
        Map<DocumentMetadata, FilenameReservation> reservations = new HashMap<DocumentMetadata, FilenameReservation>(documents.size());
        Map<Long, List<DocumentMetadata>> documentsPerFolder = getDocumentsPerFolder(documents);
        boolean committed = false;
        boolean startedTransaction = false;
        Connection con = null;
        try {
            con = provider.getWriteConnection(context);
            if (con.getAutoCommit()) {
                Databases.startTransaction(con);
                startedTransaction = true;
            }

            for (Map.Entry<Long, List<DocumentMetadata>> entry : documentsPerFolder.entrySet()) {
                long folderID = entry.getKey().longValue();
                List<DocumentMetadata> documentsInFolder = entry.getValue();
                List<FilenameReservationImpl> reservationsInFolder = new ArrayList<FilenameReservationImpl>(documentsInFolder.size());
                /*
                 * lock target folder
                 */
                lockFolder(con, context.getContextId(), folderID);
                /*
                 * get conflicting filenames in target folder
                 */
                Map<String, DocumentMetadata> usedNames = getConflictingFilenames(con, context.getContextId(), folderID, getFileNames(documentsInFolder));
                /*
                 * prepare required reservations, adjusting target filenames as needed
                 */
                for (DocumentMetadata document : documentsInFolder) {
                    String fileName = document.getFileName();
                    if (null == fileName) {
                        reservations.put(document, PLACEHOLDER_RESERVATION);
                        continue;
                    }
                    boolean adjusted = false;
                    if (usedNames.keySet().contains(fileName)) {
                        if (false == adjustAsNeeded) {
                            DocumentMetadata documentMetadata = usedNames.get(fileName);
                            throw InfostoreExceptionCodes.FILENAME_NOT_UNIQUE.create(fileName, L(documentMetadata.getFolderId()), I(documentMetadata.getId()));
                        }
                        adjusted = true;
                        NameBuilder nb = NameBuilder.nameBuilderFor(fileName);
                        do {
                            fileName = nb.advance().toString();
                        } while (usedNames.keySet().contains(fileName));
                    }
                    boolean sameTitle = null != document.getTitle() && document.getTitle().equals(document.getFileName());
                    FilenameReservationImpl reservation = new FilenameReservationImpl(
                        UUIDs.toByteArray(UUID.randomUUID()), fileName, adjusted, sameTitle);
                    reservations.put(document, reservation);
                    reservationsInFolder.add(reservation);
                    usedNames.put(fileName, new DocumentMetadataImpl());
                }
                /*
                 * perform & remember reservations
                 */
                if (0 < reservationsInFolder.size()) {
                    insertReservations(con, context.getContextId(), folderID, reservationsInFolder);
                    performedReservations.addAll(reservationsInFolder);
                }
            }

            if (startedTransaction) {
                con.commit();
                committed = true;
            }
        } catch (IncorrectStringSQLException e) {
            throw AbstractInfostoreAction.handleIncorrectStringError(e, null);
        } catch (StringLiteralSQLException e) {
            throw DatabaseExceptionCodes.STRING_LITERAL_ERROR.create(e, e.getMessage());
        } catch (SQLException e) {
            throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        } finally {
            if (startedTransaction) {
                if (false == committed) {
                    Databases.rollback(con);
                }

                Databases.autocommit(con);
            }
            provider.releaseWriteConnection(context, con);
        }
        /*
         * return successful reservations
         */
        return reservations;
    }

    private int destroySilently(Collection<FilenameReservationImpl> reservations) {
        int updated = 0;
        try {
            updated = destroy(reservations);
        } catch (OXException e) {
            LOG.warn("", e);
        } catch (SQLException e) {
            LOG.warn("", e);
        }
        return updated;
    }

    private int destroy(Collection<FilenameReservationImpl> reservations) throws OXException, SQLException {
        if (null == reservations || 0 == reservations.size()) {
            return 0;
        }
        List<byte[]> reservationIDs = new ArrayList<byte[]>(reservations.size());
        for (FilenameReservationImpl reservation : reservations) {
            reservationIDs.add(reservation.getReservationID());
        }
        int updated = 0;
        boolean committed = false;
        Connection con = null;
        try {
            con = provider.getWriteConnection(context);
            Databases.startTransaction(con);
            updated = deleteReservations(con, context.getContextId(), reservationIDs);
            con.commit();
            committed = true;
        } finally {
            if (false == committed) {
                Databases.rollback(con);
            }
            Databases.autocommit(con);
            provider.releaseWriteConnection(context, con);
        }
        return updated;
    }

    private static int deleteReservations(Connection connection, int contextID, List<byte[]> reservationIDs) throws SQLException {
        if (null == reservationIDs || 0 == reservationIDs.size()) {
            return 0;
        }
        StringBuilder StringBuilder = new StringBuilder();
        StringBuilder.append("DELETE FROM infostoreReservedPaths WHERE cid=? AND uuid");
        if (1 == reservationIDs.size()) {
            StringBuilder.append("=?;");
        } else {
            StringBuilder.append(" IN (?");
            for (int i = 1; i < reservationIDs.size(); i++) {
                StringBuilder.append(",?");
            }
            StringBuilder.append(");");
        }
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(StringBuilder.toString());
            int parameterIndex = 0;
            stmt.setInt(++parameterIndex, contextID);
            for (byte[] reservationID : reservationIDs) {
                stmt.setBytes(++parameterIndex, reservationID);
            }
            return stmt.executeUpdate();
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static int insertReservations(Connection connection, int contextID, long targetFolderID, List<FilenameReservationImpl> reservations) throws SQLException {
        if (null == reservations || 0 == reservations.size()) {
            return 0;
        }
        StringBuilder StringBuilder = new StringBuilder();
        StringBuilder.append("INSERT INTO infostoreReservedPaths (uuid,cid,folder,name) VALUES (?,?,?,?)");
        for (int i = 1; i < reservations.size(); i++) {
            StringBuilder.append(",(?,?,?,?)");
        }
        StringBuilder.append(';');
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement(StringBuilder.toString());
            int parameterIndex = 0;
            for (FilenameReservationImpl reservation : reservations) {
                stmt.setBytes(++parameterIndex, reservation.getReservationID());
                stmt.setInt(++parameterIndex, contextID);
                stmt.setLong(++parameterIndex, targetFolderID);
                stmt.setString(++parameterIndex, reservation.getFilename());
            }
            return stmt.executeUpdate();
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static Map<String, DocumentMetadata> getConflictingFilenames(Connection connection, int contextID, long targetFolderID, Set<String> fileNames) throws SQLException {
        if (null == fileNames || 0 == fileNames.size()) {
            return Collections.emptyMap();
        }
        Set<String> possibleWildcards = Tools.getEnhancedWildcards(fileNames);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT DISTINCT infostore_document.filename AS name, infostore.id AS id FROM infostore JOIN infostore_document ")
            .append("ON infostore.cid=infostore_document.cid AND infostore.version=infostore_document.version_number ")
            .append("AND infostore.id=infostore_document.infostore_id WHERE infostore.cid=? AND infostore.folder_id=? ")
            .append("AND (infostore_document.filename")
        ;
        if (1 == fileNames.size()) {
            stringBuilder.append("=?");
        } else {
            stringBuilder.append(" IN (?");
            for (int i = 1; i < fileNames.size(); i++) {
                stringBuilder.append(",?");
            }
            stringBuilder.append(")");
        }
        for (int i = 0; i < possibleWildcards.size(); i++) {
            stringBuilder.append(" OR infostore_document.filename LIKE ?");
        }
        stringBuilder.append(") UNION SELECT DISTINCT name, -1 FROM infostoreReservedPaths WHERE cid=? AND folder=? AND (name");
        if (1 == fileNames.size()) {
            stringBuilder.append("=?");
        } else {
            stringBuilder.append(" IN (?");
            for (int i = 1; i < fileNames.size(); i++) {
                stringBuilder.append(",?");
            }
            stringBuilder.append(")");
        }
        for (int i = 0; i < possibleWildcards.size(); i++) {
            stringBuilder.append(" OR name LIKE ?");
        }
        stringBuilder.append(");");
        Map<String, DocumentMetadata> conflictingFilenames = new TreeMap<String, DocumentMetadata>(String.CASE_INSENSITIVE_ORDER);
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            stmt = connection.prepareStatement(stringBuilder.toString());
            int parameterIndex = 0;
            stmt.setInt(++parameterIndex, contextID);
            stmt.setLong(++parameterIndex, targetFolderID);
            for (String filename : fileNames) {
                stmt.setString(++parameterIndex, filename);
            }
            for (String possibleWildcard : possibleWildcards) {
                stmt.setString(++parameterIndex, possibleWildcard);
            }
            stmt.setInt(++parameterIndex, contextID);
            stmt.setLong(++parameterIndex, targetFolderID);
            for (String filename : fileNames) {
                stmt.setString(++parameterIndex, filename);
            }
            for (String possibleWildcard : possibleWildcards) {
                stmt.setString(++parameterIndex, possibleWildcard);
            }
            try {
                result = stmt.executeQuery();
            } catch (StringLiteralSQLException e) {
                // No result possible
                result = EmptyResultSet.getInstance();
            }
            while (result.next()) {
                DocumentMetadata document = new DocumentMetadataImpl();
                String name = result.getString(1);
                int id = result.getInt(2);
                document.setId(id);
                document.setFolderId(targetFolderID);
                document.setFileName(name);
                conflictingFilenames.put(name, document);
            }
        } finally {
            Databases.closeSQLStuff(result, stmt);
        }
        return conflictingFilenames;
    }

    private static boolean lockFolder(Connection connection, int contextID, long targetFolderID) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = connection.prepareStatement("SELECT fuid FROM oxfolder_tree WHERE cid=? AND fuid=? FOR UPDATE;");
            stmt.setInt(1, contextID);
            stmt.setLong(2, targetFolderID);
            return stmt.execute();
        } finally {
            Databases.closeSQLStuff(stmt);
        }
    }

    private static Set<String> getFileNames(List<DocumentMetadata> documents) {
        Set<String> fileNames = new HashSet<String>(documents.size());
        for (DocumentMetadata document : documents) {
            String fileName = document.getFileName();
            if (null != fileName) {
                fileNames.add(fileName);
            }
        }
        return fileNames;
    }

    private static Map<Long, List<DocumentMetadata>> getDocumentsPerFolder(List<DocumentMetadata> documents) {
        Map<Long, List<DocumentMetadata>> documentsPerFolder = new HashMap<Long, List<DocumentMetadata>>();
        for (DocumentMetadata document : documents) {
            Long folderID = Long.valueOf(document.getFolderId());
            List<DocumentMetadata> documentsInFolder = documentsPerFolder.get(folderID);
            if (null == documentsInFolder) {
                documentsInFolder = new ArrayList<DocumentMetadata>();
                documentsPerFolder.put(folderID, documentsInFolder);
            }
            documentsInFolder.add(document);
        }
        return documentsPerFolder;
    }

}
