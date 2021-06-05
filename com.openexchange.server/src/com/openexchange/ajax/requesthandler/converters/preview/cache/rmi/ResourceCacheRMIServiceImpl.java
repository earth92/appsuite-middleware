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

package com.openexchange.ajax.requesthandler.converters.preview.cache.rmi;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import com.openexchange.ajax.requesthandler.cache.ResourceCache;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link ResourceCacheRMIServiceImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.1
 */
public class ResourceCacheRMIServiceImpl implements ResourceCacheRMIService {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ResourceCacheRMIServiceImpl.class);
    }

    /** The cache reference */
    public static AtomicReference<ResourceCache> CACHE_REF = new AtomicReference<ResourceCache>();

    /**
     * Initialises a new {@link ResourceCacheRMIServiceImpl}.
     */
    public ResourceCacheRMIServiceImpl() {
        super();
    }

    @Override
    public void clear() throws RemoteException {
        ResourceCache resourceCache = CACHE_REF.get();
        if (null == resourceCache) {
            return;
        }

        List<Integer> contextIds = null;
        try {
            contextIds = getContextIds();
        } catch (OXException e) {
            LoggerHolder.LOG.error("", e);
            String message = e.getMessage();
            throw new RemoteException(message, new Exception(message));
        }

        for (Integer contextId : contextIds) {
            try {
                resourceCache.clearFor(contextId.intValue());
            } catch (OXException e) {
                LoggerHolder.LOG.error("", e);
                String message = e.getMessage();
                throw new RemoteException(message, new Exception(message));
            } catch (RuntimeException e) {
                LoggerHolder.LOG.error("", e);
                String message = e.getMessage();
                throw new RemoteException(message, new Exception(message));
            }
        }
    }

    @Override
    public void clearFor(int contextId) throws RemoteException {
        ResourceCache resourceCache = CACHE_REF.get();
        if (null == resourceCache) {
            return;
        }
        try {
            resourceCache.clearFor(contextId);
        } catch (Exception e) {
            LoggerHolder.LOG.error("", e);
            String message = e.getMessage();
            throw new RemoteException(message, new Exception(message));
        }
    }

    @Override
    public String sanitizeMimeTypesInDatabaseFor(int contextId, String invalids) throws RemoteException {
        try {
            DatabaseService databaseService = ServerServiceRegistry.getInstance().getService(DatabaseService.class);
            if (null == databaseService) {
                String message = "Missing service: " + DatabaseService.class.getName();
                throw new RemoteException(message, new Exception(message));
            }

            Set<String> invalidsSet = new HashSet<String>(Arrays.asList("application/force-download", "application/x-download", "application/$suffix"));
            if (Strings.isNotEmpty(invalids)) {
                for (String invalid : invalids.split(" *, *")) {
                    invalidsSet.add(Strings.toLowerCase(invalid.trim()));
                }
            }

            if (contextId >= 0) {
                return processContext(contextId, invalidsSet, databaseService);
            }

            // Process all available contexts
            TIntSet contextIds;
            {
                Connection configDbCon = null;
                PreparedStatement stmt = null;
                ResultSet rs = null;
                try {
                    configDbCon = databaseService.getReadOnly();
                    stmt = configDbCon.prepareStatement("SELECT cid FROM context");
                    rs = stmt.executeQuery();

                    contextIds = new TIntHashSet();

                    while (rs.next()) {
                        contextIds.add(rs.getInt(1));
                    }
                } finally {
                    Databases.closeSQLStuff(rs, stmt);
                    rs = null;
                    stmt = null;
                    if (null != configDbCon) {
                        databaseService.backReadOnly(configDbCon);
                        configDbCon = null;
                    }
                }
            }

            if (contextIds.isEmpty()) {
                return "No contexts found";
            }

            String sep = Strings.getLineSeparator();
            StringBuilder responseBuilder = new StringBuilder(65536);
            AtomicReference<Exception> errorRef = new AtomicReference<Exception>();
            contextIds.forEach(cid -> {
                if (responseBuilder.length() > 0) {
                    responseBuilder.append(sep);
                }
                try {
                    responseBuilder.append(processContext(cid, invalidsSet, databaseService));
                } catch (Exception e) {
                    LoggerHolder.LOG.error("Context {} could not be processed", Integer.valueOf(cid), e);
                    responseBuilder.append("Context ").append(cid).append(" could not be processed: >>>").append(e.getMessage()).append("<<<");
                }
                return true;
            });

            Exception exc = errorRef.get();
            if (null != exc) {
                throw exc;
            }

            return responseBuilder.toString();
        } catch (Exception e) {
            LoggerHolder.LOG.error("", e);
            String message = e.getMessage();
            throw new RemoteException(message, new Exception(message));
        }
    }

    ////////////////////////////// HELPERS ///////////////////////////////

    /**
     * Gets available context identifier.
     *
     * @param optService The optional database service
     * @return The context identifiers
     * @throws OXException If identifiers cannot be loaded from configDB
     */
    private List<Integer> getContextIds() throws OXException {
        ContextService contextService = ServerServiceRegistry.getInstance().getService(ContextService.class);
        if (null == contextService) {
            throw ServiceExceptionCode.absentService(ContextService.class);
        }
        return contextService.getAllContextIds();
    }

    /**
     * Processes the specified context
     * 
     * @param contextId The context identifier
     * @param invalidsSet A {@link Set} with the invalid content types
     * @param databaseService The {@link DatabaseService}
     * @return The outcome of the operation
     * @throws OXException if an error is occurred
     * @throws SQLException if an SQL error is occurred
     */
    private String processContext(int contextId, Set<String> invalidsSet, DatabaseService databaseService) throws OXException, SQLException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        boolean afterReading = true;

        try {
            con = databaseService.getForUpdateTask(contextId);
            stmt = con.prepareStatement("SELECT infostore_id, version_number, file_mimetype, filename FROM infostore_document WHERE cid=?");
            stmt.setInt(1, contextId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                // No documents in specified context;
                return "Context " + contextId + " does not hold any documents.";
            }

            String defaultMimeType = MimeTypes.MIME_APPL_OCTET;
            List<Tuple> tuples = new LinkedList<Tuple>();
            do {
                String fileName = rs.getString(4);
                if (Strings.isNotEmpty(fileName)) {
                    String mimeType = rs.getString(3);
                    if (Strings.isNotEmpty(mimeType)) {
                        String contentTypeByFileName = MimeType2ExtMap.getContentType(fileName);
                        if (invalidsSet.contains(Strings.toLowerCase(mimeType)) || (!defaultMimeType.equals(contentTypeByFileName) && !equalPrimaryTypes(mimeType, contentTypeByFileName))) {
                            tuples.add(new Tuple(contentTypeByFileName, rs));
                        }
                    }
                }
            } while (rs.next());
            Databases.closeSQLStuff(rs, stmt);
            rs = null;
            stmt = null;

            if (tuples.isEmpty()) {
                return "No document with a broken/corrupt MIME type found in context " + contextId;
            }

            stmt = con.prepareStatement("UPDATE infostore_document SET file_mimetype=? WHERE cid=? AND infostore_id=? AND version_number=?");
            stmt.setInt(2, contextId);

            for (Tuple tuple : tuples) {
                stmt.setString(1, tuple.getMimeType());
                stmt.setInt(3, tuple.getId());
                stmt.setInt(4, tuple.getVersion());
                stmt.addBatch();
            }

            int[] result = stmt.executeBatch();
            afterReading = false;

            return "Fixed " + Integer.toString(result.length) + (result.length == 1 ? " document" : " documents") + " with a broken/corrupt MIME type in context " + contextId;
        } finally {
            Databases.closeSQLStuff(rs, stmt);
            if (null != con) {
                if (afterReading) {
                    databaseService.backForUpdateTaskAfterReading(contextId, con);
                } else {
                    databaseService.backForUpdateTask(contextId, con);
                }
            }
        }
    }

    /**
     * Returns the primary type of the specified content-type
     * 
     * @param contentType The content-type from which to return the primary type
     * @return The primary type of the specified content-type, or an empty
     *         string or <code>null</code> if the content-type is empty or <code>null</code> respectively
     */
    private String getPrimaryType(String contentType) {
        if (Strings.isEmpty(contentType)) {
            return contentType;
        }
        int pos = contentType.indexOf('/');
        return pos > 0 ? contentType.substring(0, pos) : contentType;
    }

    /**
     * Checks whether the specified content types are equal, by checking their primary types
     * 
     * @param contentType1 The first content type
     * @param contentType2 The second content type
     * @return <code>true</code> if the primary types of the specified content types are equal; <code>false</code>
     *         otherwise
     */
    private boolean equalPrimaryTypes(String contentType1, String contentType2) {
        if (null == contentType1 || null == contentType2) {
            return false;
        }
        return Strings.toLowerCase(getPrimaryType(contentType1)).startsWith(Strings.toLowerCase(getPrimaryType(contentType2)));
    }

    ///////////////////////////////////////// NESTED ////////////////////////////////////////////////

    private static class Tuple {

        private final int id;
        private final int version;
        private final String mimeType;

        /**
         * Initialises a new {@link Tuple}.
         * 
         * @param mimeType The mime type
         * @param rs The {@link ResultSet} from which to extract the id and the version
         * @throws SQLException if an SQL error is occurred
         */
        protected Tuple(String mimeType, ResultSet rs) throws SQLException {
            super();
            this.id = rs.getInt(1);
            this.version = rs.getInt(2);
            this.mimeType = mimeType;
        }

        /**
         * Gets the id
         *
         * @return The id
         */
        public int getId() {
            return id;
        }

        /**
         * Gets the version
         *
         * @return The version
         */
        public int getVersion() {
            return version;
        }

        /**
         * Gets the mimeType
         *
         * @return The mimeType
         */
        public String getMimeType() {
            return mimeType;
        }
    }
}
