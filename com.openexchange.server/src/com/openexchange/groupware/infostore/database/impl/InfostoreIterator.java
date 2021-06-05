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

import static com.openexchange.tools.sql.DBUtils.getStatement;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.database.Databases;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.MediaStatus;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.InfostoreFolderPath;
import com.openexchange.groupware.infostore.database.impl.InfostoreQueryCatalog.FieldChooser;
import com.openexchange.groupware.infostore.database.impl.InfostoreQueryCatalog.Table;
import com.openexchange.groupware.infostore.utils.Metadata;
import com.openexchange.groupware.infostore.utils.SetSwitch;
import com.openexchange.java.AsciiReader;
import com.openexchange.java.Charsets;
import com.openexchange.java.GeoLocation;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.user.User;

public class InfostoreIterator implements SearchIterator<DocumentMetadata> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(InfostoreIterator.class);
    private static final InfostoreQueryCatalog QUERIES = InfostoreQueryCatalog.getInstance();

    public static InfostoreIterator loadDocumentIterator(final int id, final int version, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getSelectDocument(id, version, ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, Metadata.VALUES_ARRAY, QUERIES.getChooserForVersion(version));
    }

    public static InfostoreIterator list(final int[] id, final Metadata[] metadata, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getListQuery(id,metadata,new InfostoreQueryCatalog.DocumentWins(),ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator documents(final long folderId, final Metadata[] metadata,final Metadata sort, final int order, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getDocumentsQuery(folderId, metadata, sort, order, -1, -1, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator documents(final long folderId, final Metadata[] metadata,final Metadata sort, final int order, int start, int end, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getDocumentsQuery(folderId, metadata, sort, order, start, end, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator documentsByCreator(final long folderId,final int userId, final Metadata[] metadata,final Metadata sort, final int order, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getDocumentsQuery(folderId,userId, metadata, sort, order, -1, -1, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator documentsByCreator(final long folderId,final int userId, final Metadata[] metadata,final Metadata sort, final int order, int start, int end, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getDocumentsQuery(folderId,userId, metadata, sort, order, start, end, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator versions(final int id, final Metadata[] metadata, final Metadata sort, final int order, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getVersionsQuery(id, metadata, sort, order, new InfostoreQueryCatalog.VersionWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.VersionWins());
    }

    public static InfostoreIterator modifiedDocuments(final long folderId, final Metadata[] metadata, final Metadata sort, final int order, final long since, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getModifiedDocumentsQuery(folderId,since, metadata, sort, order, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator deletedDocuments(final long folderId, final Metadata sort, final int order, final long since, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getDeletedDocumentsQuery(folderId,since, sort, order, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, new Metadata[]{Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL}, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator modifiedDocumentsByCreator(final long folderId,final int userId, final Metadata[] metadata, final Metadata sort, final int order, final long since, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getModifiedDocumentsQuery(folderId,userId, since, metadata, sort, order, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator deletedDocumentsByCreator(final long folderId,final int userId, final Metadata sort, final int order, final long since, final DBProvider provider, final Context ctx) {
        final String query = QUERIES.getDeletedDocumentsQuery(folderId,userId, since, sort, order, new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, new Metadata[]{Metadata.ID_LITERAL, Metadata.FOLDER_ID_LITERAL}, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator allDocumentsWhere(final String where, final Metadata[] metadata, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getAllDocumentsQuery(where,metadata,new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator allVersionsWhere(final String where, final Metadata[] metadata, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getAllVersionsQuery(where,metadata,new InfostoreQueryCatalog.VersionWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.VersionWins());
    }

    public static InfostoreIterator documentsByFilename(final long folderId, final String filename, final Metadata[] metadata, final DBProvider provider, final Context ctx){
        final String query = QUERIES.getCurrentFilenameQuery(folderId,metadata,new InfostoreQueryCatalog.DocumentWins(), ctx.getContextId());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins(), filename);
    }

    public static InfostoreIterator sharedDocumentsForUser(final Context ctx, final User user, final int leastPermission, final Metadata[] metadata, Metadata sort, int order, int start, int end, final DBProvider provider) {
        final String query = QUERIES.getSharedDocumentsForUserQuery(ctx.getContextId(), user.getId(), user.getGroups(), leastPermission, metadata, sort, order, start, end, new InfostoreQueryCatalog.DocumentWins());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator sharedDocumentsByUser(Context ctx, User user, Metadata[] metadata, Metadata sort, int order, int start, int end, DBProvider provider) {
        String query = QUERIES.getSharedDocumentsByUserQuery(ctx.getContextId(), user.getId(), metadata, sort, order, start, end, new InfostoreQueryCatalog.DocumentWins());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator modifiedSharedDocumentsForUser(final Context ctx, final User user, final Metadata[] metadata, final Metadata sort, final int order, final long since, final DBProvider provider) {
        final String query = QUERIES.getModifiedSharedDocumentsSince(ctx.getContextId(), user.getId(), user.getGroups(), since, metadata, sort, order, new InfostoreQueryCatalog.DocumentWins());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    public static InfostoreIterator deletedSharedDocumentsForUser(final Context ctx, final User user, final Metadata[] metadata, final Metadata sort, final int order, final long since, final DBProvider provider) {
        final String query = QUERIES.getDeletedSharedDocumentsSince(ctx.getContextId(), user.getId(), user.getGroups(), since, metadata, sort, order, new InfostoreQueryCatalog.DocumentWins());
        return new InfostoreIterator(query, provider, ctx, metadata, new InfostoreQueryCatalog.DocumentWins());
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private final Object[] args;
    private final DBProvider provider;
    private final String query;
    private boolean queried;
    private boolean initNext;
    private PreparedStatement stmt;
    private Connection con;
    private ResultSet rs;
    private boolean next;
    private OXException exception;
    private final List<OXException> warnings;
    private DocumentCustomizer cutomizer;
    private final Context ctx;
    private final Metadata[] fields;
    private final FieldChooser chooser;

    protected InfostoreIterator(final String query,final DBProvider provider, final Context ctx, final Metadata[] fields, final FieldChooser chooser, final Object...args){
        this.warnings =  new ArrayList<OXException>(2);
        this.query = query;
        this.provider = provider;
        this.args = args;
        this.ctx = ctx;
        this.fields = fields;
        this.chooser = chooser;
    }

    @Override
    public void close() {
        if (rs == null) {
            return;
        }

        try {
            Databases.closeSQLStuff(rs, stmt);
            provider.releaseReadConnection(ctx, con);
        } finally {
            con = null;
            stmt = null;
            rs = null;
        }
    }

    @Override
    public boolean hasNext() throws OXException {
        if (false == queried) {
            query();
        }
        if (exception != null) {
            return true;
        }
        if (initNext) {
            Statement stmt = null;
            try {
                stmt = rs.getStatement();
                next = rs.next();
                if (!next) {
                    close();
                }
            } catch (SQLException e) {
                this.exception = InfostoreExceptionCodes.SQL_PROBLEM.create(e, getStatement(stmt));
            }
        }
        initNext = false;
        return next;
    }

    @Override
    public void addWarning(final OXException warning) {
        warnings.add(warning);
    }

    @Override
    public OXException[] getWarnings() {
        return warnings.isEmpty() ? null : warnings.toArray(new OXException[warnings.size()]);
    }

    @Override
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void setCustomizer(DocumentCustomizer customizer) {
        this.cutomizer = customizer;
    }

    private void query() {
        queried = true;
        initNext = true;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        boolean close = true;
        try {
            con = provider.getReadConnection(ctx);
            stmt = con.prepareStatement(query);
            int i = 1;
            for (Object arg : args) {
                stmt.setObject(i++, arg);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("{}", stmt.toString());
            }
            rs = stmt.executeQuery();
            this.stmt = stmt;
            this.rs = rs;
            this.con = con;
            close = false;
        } catch (OXException e) {
            this.exception = e;
        } catch (SQLException x) {
            this.exception = InfostoreExceptionCodes.SQL_PROBLEM.create(x, Databases.getSqlStatement(stmt, query));
        } catch (RuntimeException x) {
            this.exception = OXException.general(x.getMessage(), x);
        } finally {
            if (close) {
                Databases.closeSQLStuff(rs, stmt);
                provider.releaseReadConnection(ctx, con);
            }
        }
    }

    @Override
    public DocumentMetadata next() throws OXException {
        hasNext();
        if (exception != null) {
            throw exception;
        }
        initNext = true;

        return getDocument();
    }

    private DocumentMetadata getDocument() throws OXException {
        DocumentMetadata dm = new DocumentMetadataImpl();
        SetSwitch set = new SetSwitch(dm);

        StringBuilder sb = new StringBuilder(64);
        NextMetadata: for (final Metadata m : fields) {
            if (m == Metadata.CURRENT_VERSION_LITERAL) {
                try {
                    dm.setIsCurrentVersion(rs.getBoolean("current_version"));
                } catch (SQLException e) {
                    throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, "Failed to query \"current_version\" from result set.");
                }
            } else {
                // Determine column name for current field
                Table t = chooser.choose(m);
                String colName = (String) m.doSwitch(t.getFieldSwitcher());
                if (colName == null) {
                    continue NextMetadata;
                }

                String column = sb.append(t.getTablename()).append('.').append(colName).toString();
                sb.setLength(0);
                try {
                    switch (m.getId()) {
                        case Metadata.META:
                            //$FALL-THROUGH$
                        case Metadata.MEDIA_META:
                            // If the value is SQL NULL, the result is null
                            set.setValue(readMetaFrom(rs.getBinaryStream(column), m, dm));
                            break;
                        case Metadata.ORIGIN:
                            String sFolderPath = rs.getString(column);
                            if (rs.wasNull()) {
                                set.setValue(null);
                            } else {
                                set.setValue(InfostoreFolderPath.parseFrom(sFolderPath));
                            }
                            break;
                        case Metadata.GEOLOCATION:
                            // If the value is SQL NULL, the result is null
                            String point = rs.getString(colName);
                            if (null != point) {
                                // POINT(28.093833333333333 -16.735833333333336)
                                set.setValue(GeoLocation.parseSqlPoint(point));
                            } else {
                                set.setValue(null);
                            }
                            break;
                        case Metadata.MEDIA_STATUS:
                            String status = rs.getString(column);
                            if (!rs.wasNull() && null != status) {
                                MediaStatus mediaStatus = MediaStatus.valueFor(status);
                                set.setValue(null == mediaStatus ? MediaStatus.none() : mediaStatus);
                            } else {
                                set.setValue(null);
                            }
                            break;
                        case Metadata.CAPTURE_DATE:
                            long date = rs.getLong(column);
                            if (!rs.wasNull()) {
                                set.setValue(new Date(date));
                            } else {
                                set.setValue(null);
                            }
                            break;
                        case Metadata.CAMERA_APERTURE:
                            //$FALL-THROUGH$
                        case Metadata.CAMERA_EXPOSURE_TIME:
                            //$FALL-THROUGH$
                        case Metadata.CAMERA_FOCAL_LENGTH:
                            double dbl = rs.getDouble(column);
                            if (!rs.wasNull()) {
                                // A negative value signals infinity
                                set.setValue(Double.valueOf(dbl < 0 ? Double.POSITIVE_INFINITY : dbl));
                            } else {
                                set.setValue(null);
                            }
                            break;
                        default:
                            set.setValue(process(m, rs.getObject(column)));
                            break;
                    }
                } catch (SQLException e) {
                    throw InfostoreExceptionCodes.SQL_PROBLEM.create(e, sb.append("Failed to query \"").append(column).append("\" from result set.").toString());
                }
                m.doSwitch(set);
            }
        }

        return cutomizer != null ? cutomizer.handle(dm) : dm;
    }

    private Object process(final Metadata m, final Object object) {
        switch (m.getId()) {
        default : return object;
        case Metadata.LAST_MODIFIED : case Metadata.CREATION_DATE : case Metadata.LAST_MODIFIED_UTC: return new Date(((Long)object).longValue());
        case Metadata.MODIFIED_BY : case Metadata.CREATED_BY : case Metadata.VERSION : case Metadata.ID:case  Metadata.COLOR_LABEL:
            return Integer.valueOf(((Long)object).intValue());
        }
    }

    @Override
    public int size() {
        return -1;
    }

    /**
     * Gets the list view for the remaining elements of this iterator and closes all resources.
     *
     * @return A listing of remaining elements
     * @throws OXException If list view cannot be returned
     * @deprecated Prefer using {@link SearchIterators#asList(SearchIterator)} instead
     */
    @Deprecated
    public List<DocumentMetadata> asList() throws OXException {
        try {
            if (!hasNext()) {
                return Collections.emptyList();
            }

            List<DocumentMetadata> result = new ArrayList<DocumentMetadata>();
            do {
                result.add(next());
            } while (hasNext());
            return result;
        } finally {
            close();
        }
    }

    /**
     * Reads metadata from specified {@link java.sql.Types#BLOB SQL <code>BLOB</code>} input stream providing JSON data.
     *
     * @param jsonBlobStream The SQL <code>BLOB</code> input stream providing JSON data
     * @param m The metadata field
     * @param dm The document metadata, which is currently filled
     * @return The resulting metadata as a map
     */
    public static Map<String, Object> readMetaFrom(InputStream jsonBlobStream, Metadata m, DocumentMetadata dm) {
        if (null == jsonBlobStream) {
            return null;
        }

        String jsonString = null;
        try {
            if (jsonBlobStream instanceof ByteArrayInputStream) {
                jsonString = Charsets.toAsciiString((ByteArrayInputStream) jsonBlobStream);
                if (Strings.isEmpty(jsonString) || "null".equalsIgnoreCase(jsonString)) {
                    return null;
                }
                return new JSONObject(jsonString).asMap();
            }

            jsonBlobStream = getNonEmptyMeta(jsonBlobStream, m, dm);
            if (null == jsonBlobStream) {
                return null;
            }
            return new JSONObject(new AsciiReader(jsonBlobStream)).asMap();
        } catch (JSONException e) {
            if (null != jsonString && !LOG.isDebugEnabled()) {
                jsonString = null;
            }
            logFailedMetaRead(jsonString, m, dm, e);
            return null;
        } finally {
            Streams.close(jsonBlobStream);
        }
    }

    /**
     * Safely checks if specified stream is empty.
     * <p>
     * If <code>null</code> is returned, the given stream is ensured to be closed.
     */
    private static InputStream getNonEmptyMeta(InputStream is, Metadata m, DocumentMetadata dm) {
        InputStream toCheck = is;
        try {
            InputStream nonEmpty = Streams.getNonEmpty(toCheck);
            toCheck = null;
            return nonEmpty;
        } catch (IOException e) {
            logFailedMetaRead(null, m, dm, e);
            return null;
        } finally {
            Streams.close(toCheck);
        }
    }

    private static void logFailedMetaRead(String optCorruptJson, Metadata m, DocumentMetadata dm, Exception e) {
        if (m == Metadata.MEDIA_META_LITERAL) {
            if (null == optCorruptJson) {
                LOG.warn("Failed to read media metadata from document {} in folder {}", dm.getId(), dm.getFolderId(), e);
            } else {
                LOG.warn("Failed to read media metadata from document {} in folder {}:{}{}", dm.getId(), dm.getFolderId(), Strings.getLineSeparator(), optCorruptJson, e);
            }
        } else {
            if (null == optCorruptJson) {
                LOG.warn("Failed to read metadata from document {} in folder {}", dm.getId(), dm.getFolderId(), e);
            } else {
                LOG.warn("Failed to read metadata from document {} in folder {}:{}{}", dm.getId(), dm.getFolderId(), Strings.getLineSeparator(), optCorruptJson, e);
            }
        }
    }

}
