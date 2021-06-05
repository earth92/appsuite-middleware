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

package com.openexchange.filestore.impl;

import static com.openexchange.filestore.FileStorages.indicatesConnectionClosed;
import static com.openexchange.filestore.impl.groupware.unified.UnifiedQuotaUtils.isUnifiedQuotaEnabledFor;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadBase.FileUploadIOException;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.io.FileUtils;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.Databases;
import com.openexchange.exception.OXException;
import com.openexchange.filestore.FileStorage;
import com.openexchange.filestore.FileStorageCodes;
import com.openexchange.filestore.Info;
import com.openexchange.filestore.OwnerInfo;
import com.openexchange.filestore.Purpose;
import com.openexchange.filestore.QuotaFileStorage;
import com.openexchange.filestore.QuotaFileStorageExceptionCodes;
import com.openexchange.filestore.QuotaFileStorageListener;
import com.openexchange.filestore.Spool;
import com.openexchange.filestore.SpoolingCapableQuotaFileStorage;
import com.openexchange.filestore.event.FileStorageListener;
import com.openexchange.filestore.impl.osgi.Services;
import com.openexchange.filestore.unified.KnownContributor;
import com.openexchange.filestore.unified.UnifiedQuotaService;
import com.openexchange.filestore.unified.UsageResult;
import com.openexchange.filestore.utils.TempFileHelper;
import com.openexchange.groupware.upload.impl.UploadFileSizeExceededException;
import com.openexchange.groupware.upload.impl.UploadSizeExceededException;
import com.openexchange.groupware.userconfiguration.UserConfigurationCodes;
import com.openexchange.java.Streams;
import com.openexchange.log.LogProperties;
import com.openexchange.osgi.ServiceListing;
import com.openexchange.osgi.ServiceListings;

/**
 * {@link DBQuotaFileStorage} - Delegates file storage operations to associated {@link FileStorage} instance while accounting quota in
 * <code>'filestore_usage'</code> database table.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public class DBQuotaFileStorage implements SpoolingCapableQuotaFileStorage, Serializable /* For cache service */{

    private static final long serialVersionUID = -4048657112670657310L;

    private static final String SERVICE_ID_APPSUITE_DRIVE = KnownContributor.APPSUITE_DRIVE.getId();

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(QuotaFileStorage.class);

    // -------------------------------------------------------------------------------------------------------------

    private final int contextId;
    private final transient FileStorage fileStorage;
    private final long quota;
    private final OwnerInfo ownerInfo;
    private final URI uri;
    private final ServiceListing<FileStorageListener> storageListeners;
    private final ServiceListing<QuotaFileStorageListener> quotaListeners;
    private final ServiceListing<UnifiedQuotaService> unifiedQuotaServices;
    private final Purpose purpose;
    private final int effectiveUserId;

    /**
     * Initializes a new {@link DBQuotaFileStorage} for an owner.
     *
     * @param contextId The context identifier
     * @param userId The identifier of the user for which the file storage instance was requested; pass <code>0</code> if not requested for a certain user
     * @param ownerInfo The file storage owner information
     * @param quota The assigned quota
     * @param fs The file storage associated with the owner
     * @param uri The URI that fully qualifies this file storage
     * @param nature The storage's nature
     * @param storageListeners The file storage listeners
     * @param quotaListeners The quota listeners
     * @param unifiedQuotaServices The tracked Unified Quota services
     * @throws OXException If initialization fails
     */
    public DBQuotaFileStorage(int contextId, Info info, OwnerInfo ownerInfo, long quota, FileStorage fs, URI uri, ServiceListing<FileStorageListener> storageListeners, ServiceListing<QuotaFileStorageListener> quotaListeners, ServiceListing<UnifiedQuotaService> unifiedQuotaServices) throws OXException {
        super();
        if (fs == null) {
            throw QuotaFileStorageExceptionCodes.INSTANTIATIONERROR.create();
        }

        this.storageListeners = null == storageListeners ? ServiceListings.<FileStorageListener> emptyList() : storageListeners;
        this.unifiedQuotaServices = null == unifiedQuotaServices ? ServiceListings.<UnifiedQuotaService> emptyList() : unifiedQuotaServices;
        this.quotaListeners = null == quotaListeners ? ServiceListings.<QuotaFileStorageListener> emptyList() : quotaListeners;

        this.purpose = null == info ? Purpose.ADMINISTRATIVE : info.getPurpose();

        this.uri = uri;
        this.contextId = contextId;
        this.ownerInfo = ownerInfo;
        this.quota = quota;
        fileStorage = fs;

        // Having purpose and owner information available it is possible to check whether call-backs to usage/limit service is supposed to happen
        effectiveUserId = considerService();
    }

    /**
     * Checks whether separate usage/limit service is supposed to be invoked.
     * <p>
     * In case such a service should be called, a valid user identifier is returned; otherwise <code>0</code> (zero).
     *
     * @return A valid user identifier in case usage/limit service should be called; otherwise <code>0</code> (zero)
     */
    private int considerService() {
        if (purpose == Purpose.ADMINISTRATIVE) {
            LOGGER.debug("Not considering another quota backend service for file storage '{}' since used for administrative purpose.", uri);
            return 0;
        }

        if (false == ownerInfo.isMaster()) {
            LOGGER.debug("Not considering another quota backend service for file storage '{}' since owned by another user.", uri);
            return 0;
        }

        int ownerId = ownerInfo.getOwnerId();
        if (ownerId <= 0) {
            LOGGER.debug("Not considering another quota backend service for file storage '{}' since not user-associated, but context-associated ({}).", uri, Integer.valueOf(contextId));
            return 0;
        }

        LOGGER.debug("Considering another quota backend service for file storage '{}' of user {} in context {}.", uri, Integer.valueOf(ownerId), Integer.valueOf(contextId));
        return ownerId;
    }

    private DatabaseService getDatabaseService() throws OXException {
        return Services.requireService(DatabaseService.class);
    }

    private UnifiedQuotaService getHighestRankedBackendService(int userId, int contextId) throws OXException  {
        if (userId <= 0) {
            return null;
        }

        Iterator<UnifiedQuotaService> iter = unifiedQuotaServices.iterator();
        if (false == iter.hasNext()) {
            // No one available...
            LOGGER.debug("No Unified Quota service available for file storage '{}' of user {} in context {}.", uri, Integer.valueOf(userId), Integer.valueOf(contextId));
            return null;
        }

        if (false == checkIfUnifiedQuotaIsEnabledFor(userId, contextId)) {
            LOGGER.debug("Unified Quota is not enabled for user {} in context {}.", Integer.valueOf(userId), Integer.valueOf(contextId));
            return null;
        }

        do {
            UnifiedQuotaService unifiedQuotaService = iter.next();
            if (unifiedQuotaService.isApplicableFor(userId, contextId)) {
                LOGGER.debug("Using Unified Quota service '{}' for file storage '{}' of user {} in context {}.", unifiedQuotaService.getMode(), uri, Integer.valueOf(userId), Integer.valueOf(contextId));
                return unifiedQuotaService;
            }
            LOGGER.debug("Unified Quota service '{}' is not applicable for file storage '{}' of user {} in context {}.", unifiedQuotaService.getMode(), uri, Integer.valueOf(userId), Integer.valueOf(contextId));
        } while (iter.hasNext());

        LOGGER.debug("No Unified Quota service applicable for file storage '{}' of user {} in context {}.", uri, Integer.valueOf(userId), Integer.valueOf(contextId));
        return null;
    }

    private boolean checkIfUnifiedQuotaIsEnabledFor(int userId, int contextId) throws OXException {
        try {
            return isUnifiedQuotaEnabledFor(userId, contextId);
        } catch (OXException e) {
            if (UserConfigurationCodes.NOT_FOUND.equals(e)) {
                // Such a user does not (yet) exist. Thus Unified Quota cannot be enabled.
                return false;
            }
            throw e;
        }
    }

    @Override
    public String getMode() throws OXException {
        UnifiedQuotaService unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
        return null == unifiedQuotaService ? DEFAULT_MODE : unifiedQuotaService.getMode();
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public long getQuota() throws OXException {
        UnifiedQuotaService unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
        return null == unifiedQuotaService ? quota : unifiedQuotaService.getLimit(effectiveUserId, contextId);
    }

    @Override
    public boolean deleteFile(String identifier) throws OXException {
        long fileSize;
        try {
            fileSize = fileStorage.getFileSize(identifier);
        } catch (OXException e) {
            if (false == FileStorageCodes.FILE_NOT_FOUND.equals(e)) {
                throw e;
            }

            // Obviously the file has been deleted before
            return true;
        }

        boolean deleted = fileStorage.deleteFile(identifier);
        if (!deleted) {
            return false;
        }
        decUsage(identifier, fileSize);
        // Notify storage listeners
        for (FileStorageListener storageListener : storageListeners) {
            try {
                storageListener.onFileDeleted(identifier, fileStorage);
            } catch (Exception e) {
                LOGGER.warn("", e);
            }
        }
        return true;
    }

    /**
     * Increases the quota usage.
     *
     * @param id The identifier of the associated file
     * @param required The value by which the quota is supposed to be increased
     * @return <code>true</code> if quota is exceeded; otherwise <code>false</code>
     * @throws OXException If a database error occurs
     */
    protected boolean incUsage(String id, long required) throws OXException {
        DatabaseService db = getDatabaseService();
        Connection con = db.getWritable(contextId);

        Long toRestore = null;
        UnifiedQuotaService unifiedQuotaService = null;

        PreparedStatement sstmt = null;
        PreparedStatement ustmt = null;
        ResultSet rs = null;
        int rollback = 0;
        try {
            con.setAutoCommit(false);
            rollback = 1;

            // Grab the current usage from database
            int ownerId = ownerInfo.getOwnerId();
            sstmt = con.prepareStatement("SELECT used FROM filestore_usage WHERE cid=? AND user=? FOR UPDATE");
            sstmt.setInt(1, contextId);
            sstmt.setInt(2, ownerId);
            rs = sstmt.executeQuery();
            if (!rs.next()) {
                if (ownerId > 0) {
                    throw QuotaFileStorageExceptionCodes.NO_USAGE_USER.create(I(ownerId), I(contextId));
                }
                throw QuotaFileStorageExceptionCodes.NO_USAGE.create(I(contextId));
            }
            long oldUsageFromDb = rs.getLong(1);
            long newUsageForDb = oldUsageFromDb + required;

            // Check if usage is exceeded regarding effective new usage
            if (quota < 0) {
                // Unlimited quota...
            } else {
                unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
                if (unifiedQuotaService != null) {
                    long quota = unifiedQuotaService.getLimit(effectiveUserId, contextId);
                    boolean keepOn = true;
                    while (keepOn) {
                        long otherUsages = unifiedQuotaService.getUsageExceptFor(effectiveUserId, contextId, SERVICE_ID_APPSUITE_DRIVE).getTotal();
                        long oldTotalUsage = otherUsages + oldUsageFromDb;
                        long newTotalUsage = otherUsages + newUsageForDb;
                        long effectiveLimit = quota;
                        if (checkExceededQuota(id, effectiveLimit, required, newTotalUsage, oldTotalUsage)) {
                            return true;
                        }

                        // Advertise usage increment to listeners
                        for (QuotaFileStorageListener quotaListener : quotaListeners) {
                            quotaListener.onUsageIncrement(id, required, oldTotalUsage, effectiveLimit, ownerId, contextId);
                        }

                        // Increment usage
                        keepOn = !unifiedQuotaService.setUsage(newUsageForDb, SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                    }
                    toRestore = Long.valueOf(oldUsageFromDb);
                } else {
                    long quota = this.quota;
                    if (checkExceededQuota(id, quota, required, newUsageForDb, oldUsageFromDb)) {
                        return true;
                    }

                    // Advertise usage increment to listeners
                    for (QuotaFileStorageListener quotaListener : quotaListeners) {
                        quotaListener.onUsageIncrement(id, required, oldUsageFromDb, quota, ownerId, contextId);
                    }
                }
            }

            ustmt = con.prepareStatement("UPDATE filestore_usage SET used=? WHERE cid=? AND user=?");
            ustmt.setLong(1, newUsageForDb);
            ustmt.setInt(2, contextId);
            ustmt.setInt(3, ownerId);
            final int rows = ustmt.executeUpdate();
            if (rows == 0) {
                if (ownerId > 0) {
                    throw QuotaFileStorageExceptionCodes.UPDATE_FAILED_USER.create(I(ownerId), I(contextId));
                }
                throw QuotaFileStorageExceptionCodes.UPDATE_FAILED.create(I(contextId));
            }
            con.commit();
            rollback = 2;
        } catch (SQLException s) {
            throw QuotaFileStorageExceptionCodes.SQLSTATEMENTERROR.create(s);
        } finally {
            Databases.closeSQLStuff(rs);
            Databases.closeSQLStuff(sstmt);
            Databases.closeSQLStuff(ustmt);
            if (rollback > 0) {
                if (rollback==1) {
                    Databases.rollback(con);

                    if (null != unifiedQuotaService && null != toRestore) {
                        unifiedQuotaService.setUsage(toRestore.longValue(), SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                    }
                }
                Databases.autocommit(con);
            }
            db.backWritable(contextId, con);
        }
        return false;
    }

    private void checkAvailable(String id, long required, long quota) throws OXException {
        if (0 < required) {
            long oldUsage = getUsage();
            long usage = oldUsage + required;
            if (checkExceededQuota(id, quota, required, usage, oldUsage)) {
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
            }
        }
    }

    private boolean checkExceededQuota(String id, long quota, long required, long newUsage, long oldUsage) {
        if ((quota == 0) || (quota > 0 && newUsage > quota)) {
            // Advertise exceeded quota to listeners
            notifyOnQuotaExceeded(id, quota, required, newUsage, oldUsage);
            return true;
        }
        return false;
    }

    private boolean checkNoQuota(String id, long quota) {
        if (quota == 0) {
            // Advertise no quota to listeners
            int ownerId = ownerInfo.getOwnerId();
            for (QuotaFileStorageListener quotaListener : quotaListeners) {
                try {
                    quotaListener.onNoQuotaAvailable(id, ownerId, contextId);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Decreases the QuotaUsage.
     *
     * @param id The identifier of the associated file
     * @param usage by that the Quota has to be decreased
     * @throws OXException
     */
    protected void decUsage(String id, long usage) throws OXException {
        decUsage(Collections.singletonList(id), usage);
    }

    /**
     * Decreases the QuotaUsage.
     *
     * @param ids The identifiers of the associated files
     * @param released by that the Quota has to be decreased
     * @throws OXException
     */
    protected void decUsage(List<String> ids, long released) throws OXException {
        DatabaseService db = getDatabaseService();
        Connection con = db.getWritable(contextId);

        Long toRestore = null;
        UnifiedQuotaService unifiedQuotaService = null;

        PreparedStatement sstmt = null;
        PreparedStatement ustmt = null;
        ResultSet rs = null;
        int rollback = 0;
        try {
            con.setAutoCommit(false);
            rollback = 1;

            long toReleaseBy = released;

            // Grab current usage from database
            int ownerId = ownerInfo.getOwnerId();
            sstmt = con.prepareStatement("SELECT used FROM filestore_usage WHERE cid=? AND user=? FOR UPDATE");
            sstmt.setInt(1, contextId);
            sstmt.setInt(2, ownerId);
            rs = sstmt.executeQuery();
            if (!rs.next()) {
                if (ownerId > 0) {
                    throw QuotaFileStorageExceptionCodes.NO_USAGE_USER.create(I(ownerId), I(contextId));
                }
                throw QuotaFileStorageExceptionCodes.NO_USAGE.create(I(contextId));
            }
            long oldUsageFromDb = rs.getLong(1);
            long newUsageForDb = oldUsageFromDb - toReleaseBy;
            if (newUsageForDb < 0) {
                newUsageForDb = 0;
                toReleaseBy = oldUsageFromDb;
                final OXException e = QuotaFileStorageExceptionCodes.QUOTA_UNDERRUN.create(I(ownerId), I(contextId));
                LOGGER.error("", e);
            }

            // Check whether to mirror new usage to possible QuotaBackendService
            unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
            if (unifiedQuotaService != null) {
                boolean keepOn = true;
                while (keepOn) {
                    keepOn = !unifiedQuotaService.setUsage(newUsageForDb, SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                }
                toRestore = Long.valueOf(oldUsageFromDb);
            }

            // Advertise usage increment to listeners
            long quota = getQuota();
            for (QuotaFileStorageListener quotaListener : quotaListeners) {
                try {
                    quotaListener.onUsageDecrement(ids, toReleaseBy, oldUsageFromDb, quota, ownerId, contextId);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }

            ustmt = con.prepareStatement("UPDATE filestore_usage SET used=? WHERE cid=? AND user=?");
            ustmt.setLong(1, newUsageForDb);
            ustmt.setInt(2, contextId);
            ustmt.setInt(3, ownerId);

            int rows = ustmt.executeUpdate();
            if (1 != rows) {
                if (ownerId > 0) {
                    throw QuotaFileStorageExceptionCodes.UPDATE_FAILED_USER.create(I(ownerId), I(contextId));
                }
                throw QuotaFileStorageExceptionCodes.UPDATE_FAILED.create(I(contextId));
            }

            con.commit();
            rollback = 2;
        } catch (SQLException s) {
            throw QuotaFileStorageExceptionCodes.SQLSTATEMENTERROR.create(s);
        } finally {
            Databases.closeSQLStuff(rs);
            Databases.closeSQLStuff(sstmt);
            Databases.closeSQLStuff(ustmt);
            if (rollback > 0) {
                if (rollback==1) {
                    Databases.rollback(con);

                    if (null != unifiedQuotaService && null != toRestore) {
                        unifiedQuotaService.setUsage(toRestore.longValue(), SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                    }
                }
                Databases.autocommit(con);
            }
            db.backWritable(contextId, con);
        }
    }

    /**
     * Checks if stream is supposed to be spooled.
     *
     * @param spoolToFile The flag to examine
     * @param source The source to examine
     * @return <code>true</code> to spool; otherwise <code>false</code>
     */
    private boolean spool(boolean spoolToFile, InputStream source) {
        return (spoolToFile || (source.getClass().getAnnotation(Spool.class) != null) || "true".equals(LogProperties.get(LogProperties.Name.FILESTORE_SPOOL)));
    }

    @Override
    public Set<String> deleteFiles(String[] identifiers) throws OXException {
        try {
            if (null == identifiers || identifiers.length == 0) {
                return Collections.emptySet();
            }

            Map<String, Long> fileSizes = new HashMap<String, Long>();
            SortedSet<String> notDeletedIds = new TreeSet<String>();
            for (String identifier : newLinkedHashSetFor(identifiers)) {
                boolean deleted;
                try {
                    // Get size before attempting delete. File is not found afterwards
                    Long size = L(getFileSize(identifier));
                    deleted = fileStorage.deleteFile(identifier);
                    fileSizes.put(identifier, size);
                } catch (OXException e) {
                    if (!FileStorageCodes.FILE_NOT_FOUND.equals(e)) {
                        throw e;
                    }
                    deleted = false;
                }
                if (!deleted) {
                    notDeletedIds.add(identifier);
                }
            }
            fileSizes.keySet().removeAll(notDeletedIds);
            long sum = 0L;
            for (Long fileSize : fileSizes.values()) {
                sum += fileSize.longValue();
            }
            decUsage(Arrays.asList(identifiers), sum);

            // Notify storage listeners
            String[] deletedIds = fileSizes.keySet().toArray(new String[fileSizes.size()]);
            for (FileStorageListener storageListener : storageListeners) {
                try {
                    storageListener.onFilesDeleted(deletedIds, fileStorage);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }

            return notDeletedIds;
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    private static LinkedHashSet<String> newLinkedHashSetFor(String[] identifiers) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String identifier : identifiers) {
            if (null != identifier) {
                set.add(identifier);
            }
        }
        return set;
    }

    @Override
    public long getUsage() throws OXException {
        try {
            UnifiedQuotaService unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
            if (unifiedQuotaService != null) {
                UsageResult usage = unifiedQuotaService.getUsage(effectiveUserId, contextId);
                if (usage.hasUsageFor(SERVICE_ID_APPSUITE_DRIVE)) {
                    return usage.getTotal();
                }

                // Set & return
                long fsUsage = getUsage0();
                unifiedQuotaService.setUsage(fsUsage, SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                return usage.getTotal() + fsUsage;
            }

            // Otherwise grab the usage from database
            return getUsage0();
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    private long getUsage0() throws OXException {
        DatabaseService db = getDatabaseService();
        Connection con = db.getReadOnly(contextId);

        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            int ownerId = ownerInfo.getOwnerId();
            stmt = con.prepareStatement("SELECT used FROM filestore_usage WHERE cid=? AND user=?");
            stmt.setInt(1, contextId);
            stmt.setInt(2, ownerId);
            result = stmt.executeQuery();
            if (!result.next()) {
                if (ownerId > 0) {
                    throw QuotaFileStorageExceptionCodes.NO_USAGE_USER.create(I(ownerId), I(contextId));
                }
                throw QuotaFileStorageExceptionCodes.NO_USAGE.create(I(contextId));
            }

            return result.getLong(1);
        } catch (SQLException e) {
            throw QuotaFileStorageExceptionCodes.SQLSTATEMENTERROR.create(e);
        } finally {
            Databases.closeSQLStuff(result, stmt);
            db.backReadOnly(contextId, con);
        }
    }

    @Override
    public String saveNewFile(InputStream is) throws OXException {
        return saveNewFile(is, -1);
    }

    @Override
    public String saveNewFile(InputStream is, long sizeHint) throws OXException {
        return saveNewFile(is, sizeHint, false);
    }

    @Override
    public String saveNewFile(InputStream source, long sizeHint, boolean spoolToFile) throws OXException {
        InputStream is = source;
        String file = null;
        File tmpFile = null;
        try {
            // Check for no quota at all
            long quota = getQuota();
            if (checkNoQuota(null, quota)) {
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
            }

            // Check in case size hint is given
            boolean checked = false;
            if (0 < sizeHint) {
                checkAvailable(null, sizeHint, quota);
                checked = true;
            }

            // Create limited stream based on free space in destination storage
            if (!checked && 0 <= quota) {
                long usage = getUsage();
                long free = quota - usage;
                is = new LimitedInputStream(is, free);
            }

            // Spool to temporary file to not exhaust/block file storage resources (e.g. HTTP connection pool)
            if (spool(spoolToFile, source)) {
                Optional<File> optionalTempFile = TempFileHelper.getInstance().newTempFile();
                if (optionalTempFile.isPresent()) {
                    tmpFile = optionalTempFile.get();
                    is = Streams.transferToFileAndCreateStream(is, tmpFile);
                }
            }

            // Store new file
            file = fileStorage.saveNewFile(is);

            // Check against quota limitation
            boolean full = incUsage(file, fileStorage.getFileSize(file));
            if (full) {
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
            }

            // Notify storage listeners
            for (FileStorageListener storageListener : storageListeners) {
                try {
                    storageListener.onFileCreated(file, fileStorage);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }

            // Null'ify reference (to avoid preliminary deletion) & return new file identifier
            String retval = file;
            file = null;
            return retval;
        } catch (FileUploadIOException e) {
            // Might wrap a size-limit-exceeded error
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            Throwable cause = e.getCause();
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException exc = (FileSizeLimitExceededException) cause;
                throw UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            if (cause instanceof SizeLimitExceededException) {
                SizeLimitExceededException exc = (SizeLimitExceededException) cause;
                throw UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            throw FileStorageCodes.IOERROR.create(e, e.getMessage());
        } catch (IOException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            if (indicatesConnectionClosed(e)) {
                // End of stream has been reached unexpectedly during reading input
                throw FileStorageCodes.CONNECTION_CLOSED.create(e.getCause(), new Object[0]);
            }
            if (e instanceof StorageFullIOException) {
                long required = ((StorageFullIOException) e).getActualSize();
                long usage = getUsage();
                notifyOnQuotaExceeded(null, quota, required, usage + required, usage);
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create(e, new Object[0]);
            }
            throw FileStorageCodes.IOERROR.create(e, e.getMessage());
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            Throwable cause = e.getCause();
            if (cause instanceof StorageFullIOException) {
                long required = ((StorageFullIOException) cause).getActualSize();
                long usage = getUsage();
                notifyOnQuotaExceeded(null, quota, required, usage + required, usage);
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create(e, new Object[0]);
            }
            if (cause instanceof FileUploadIOException) {
                // Might wrap a size-limit-exceeded error
                Throwable thr = cause.getCause();
                if (thr instanceof FileSizeLimitExceededException) {
                    FileSizeLimitExceededException exc = (FileSizeLimitExceededException) thr;
                    throw UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
                }
                if (thr instanceof SizeLimitExceededException) {
                    SizeLimitExceededException exc = (SizeLimitExceededException) thr;
                    throw UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
                }
            }
            if (cause instanceof IOException) {
                if (indicatesConnectionClosed(cause)) {
                    // End of stream has been reached unexpectedly during reading input
                    throw FileStorageCodes.CONNECTION_CLOSED.create(e.getCause(), new Object[0]);
                }
            }
            throw e;
        } finally {
            Streams.close(is);
            FileUtils.deleteQuietly(tmpFile);
            if (file != null) {
                try {
                    fileStorage.deleteFile(file);
                } catch (OXException e) {
                    LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
                    LOGGER.warn("Error rolling back 'save' operation for file {}", file, e);
                }
            }
        }
    }

    @Override
    public long appendToFile(InputStream is, String name, long offset) throws OXException {
        return appendToFile(is, name, offset, -1);
    }

    @Override
    public long appendToFile(InputStream is, String name, long offset, long sizeHint) throws OXException {
        return appendToFile(is, name, offset, sizeHint, false);
    }

    @Override
    public long appendToFile(InputStream source, String name, long offset, long sizeHint, boolean spoolToFile) throws OXException {
        InputStream is = source;
        long newSize = -1;
        boolean notFoundError = false;
        File tmpFile = null;
        try {
            // Check for no quota at all
            long quota = getQuota();
            if (checkNoQuota(name, quota)) {
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
            }

            // Check in case size hint is given
            boolean checked = false;
            if (0 < sizeHint) {
                checkAvailable(name, sizeHint, quota);
                checked = true;
            }

            // Create limited stream based on free space in destination storage
            if (!checked && 0 <= quota) {
                long usage = getUsage();
                long free = quota - usage;
                is = new LimitedInputStream(is, free);
            }

            // Spool to temporary file to not exhaust/block file storage resources (e.g. HTTP connection pool)
            if (spool(spoolToFile, source)) {
                Optional<File> optionalTempFile = TempFileHelper.getInstance().newTempFile();
                if (optionalTempFile.isPresent()) {
                    tmpFile = optionalTempFile.get();
                    is = Streams.transferToFileAndCreateStream(is, tmpFile);
                }
            }

            // Append to new file
            newSize = fileStorage.appendToFile(is, name, offset);

            // Check against quota limitation
            boolean full = incUsage(name, newSize - offset);
            if (full) {
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
            }

            // Notify storage listeners
            for (FileStorageListener storageListener : storageListeners) {
                try {
                    storageListener.onFileModified(name, fileStorage);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }
        } catch (FileUploadIOException e) {
            // Might wrap a size-limit-exceeded error
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            Throwable cause = e.getCause();
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException exc = (FileSizeLimitExceededException) cause;
                throw UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            if (cause instanceof SizeLimitExceededException) {
                SizeLimitExceededException exc = (SizeLimitExceededException) cause;
                throw UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            throw FileStorageCodes.IOERROR.create(e, e.getMessage());
        } catch (IOException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            if (indicatesConnectionClosed(e)) {
                // End of stream has been reached unexpectedly during reading input
                throw FileStorageCodes.CONNECTION_CLOSED.create(e.getCause(), new Object[0]);
            }
            throw FileStorageCodes.IOERROR.create(e, e.getMessage());
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            Throwable cause = e.getCause();
            if (cause instanceof StorageFullIOException) {
                long required = ((StorageFullIOException) cause).getActualSize();
                long usage = getUsage();
                notifyOnQuotaExceeded(name, quota, required, usage + required, usage);
                throw QuotaFileStorageExceptionCodes.STORE_FULL.create(e, new Object[0]);
            }
            if (cause instanceof FileUploadIOException) {
                // Might wrap a size-limit-exceeded error
                Throwable thr = cause.getCause();
                if (thr instanceof FileSizeLimitExceededException) {
                    FileSizeLimitExceededException exc = (FileSizeLimitExceededException) thr;
                    throw UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
                }
                if (thr instanceof SizeLimitExceededException) {
                    SizeLimitExceededException exc = (SizeLimitExceededException) thr;
                    throw UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
                }
            }
            if (cause instanceof IOException) {
                if (indicatesConnectionClosed(cause)) {
                    // End of stream has been reached unexpectedly during reading input
                    throw FileStorageCodes.CONNECTION_CLOSED.create(e.getCause(), new Object[0]);
                }
            }
            if (FileStorageCodes.FILE_NOT_FOUND.equals(e)) {
                notFoundError = true;
            }
            throw e;
        } finally {
            Streams.close(is);
            FileUtils.deleteQuietly(tmpFile);
            if (false == notFoundError && -1 == newSize) {
                try {
                    fileStorage.setFileLength(offset, name);
                } catch (OXException e) {
                    LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
                    LOGGER.warn("Error rolling back 'append' operation for file {}", name, e);
                }
            }
        }
        return newSize;
    }

    /**
     * Recalculates the Usage if it's inconsistent based on all physically existing files and writes it into quota_usage.
     */
    @Override
    public void recalculateUsage() throws OXException {
        Set<String> filesToIgnore = Collections.emptySet();
        recalculateUsage(filesToIgnore);
    }

    @Override
    public void recalculateUsage(Set<String> filesToIgnore) throws OXException {
        try {
            int ownerId = ownerInfo.getOwnerId();
            if (ownerId > 0) {
                LOGGER.info("Recalculating usage for owner {} in context {}", I(ownerId), I(contextId));
            } else {
                LOGGER.info("Recalculating usage for context {}", I(contextId));
            }

            SortedSet<String> filenames = fileStorage.getFileList();
            long entireFileSize = 0;
            for (String filename : filenames) {
                if (null == filesToIgnore || !filesToIgnore.contains(filename)) {
                    try {
                        entireFileSize += fileStorage.getFileSize(filename);
                    } catch (OXException e) {
                        if (!FileStorageCodes.FILE_NOT_FOUND.equals(e)) {
                            throw e;
                        }
                    }
                }
            }

            UnifiedQuotaService unifiedQuotaService = null;
            Long toRestore = null;

            DatabaseService db = getDatabaseService();
            Connection con = db.getWritable(contextId);
            PreparedStatement stmt = null;
            int rollback = 0;
            try {
                con.setAutoCommit(false);
                rollback = 1;

                unifiedQuotaService = getHighestRankedBackendService(effectiveUserId, contextId);
                if (unifiedQuotaService != null) {
                    long effectiveOldUsage = unifiedQuotaService.getUsage(effectiveUserId, contextId).getTotal();
                    unifiedQuotaService.setUsage(entireFileSize, SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                    toRestore = Long.valueOf(effectiveOldUsage);
                }

                stmt = con.prepareStatement("UPDATE filestore_usage SET used=? WHERE cid=? AND user=?");
                stmt.setLong(1, entireFileSize);
                stmt.setInt(2, contextId);
                stmt.setInt(3, ownerId);
                final int rows = stmt.executeUpdate();
                if (1 != rows) {
                    if (ownerId > 0) {
                        throw QuotaFileStorageExceptionCodes.UPDATE_FAILED_USER.create(I(ownerId), I(contextId));
                    }
                    throw QuotaFileStorageExceptionCodes.UPDATE_FAILED.create(I(contextId));
                }

                con.commit();
                rollback = 2;
            } catch (SQLException s) {
                throw QuotaFileStorageExceptionCodes.SQLSTATEMENTERROR.create(s);
            } catch (RuntimeException s) {
                throw QuotaFileStorageExceptionCodes.SQLSTATEMENTERROR.create(s);
            } finally {
                Databases.closeSQLStuff(stmt);
                if (rollback > 0) {
                    if (rollback==1) {
                        Databases.rollback(con);

                        if (null != unifiedQuotaService && null != toRestore) {
                            unifiedQuotaService.setUsage(toRestore.longValue(), SERVICE_ID_APPSUITE_DRIVE, effectiveUserId, contextId);
                        }
                    }
                    Databases.autocommit(con);
                }
                db.backWritable(contextId, con);
            }
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public SortedSet<String> getFileList() throws OXException {
        try {
            return fileStorage.getFileList();
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public InputStream getFile(String file) throws OXException {
        try {
            return fileStorage.getFile(file);
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public long getFileSize(String name) throws OXException {
        try {
            return fileStorage.getFileSize(name);
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public String getMimeType(String name) throws OXException {
        try {
            return fileStorage.getMimeType(name);
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public void remove() throws OXException {
        try {
            fileStorage.remove();
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public void recreateStateFile() throws OXException {
        try {
            fileStorage.recreateStateFile();
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public boolean stateFileIsCorrect() throws OXException {
        try {
            return fileStorage.stateFileIsCorrect();
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public void setFileLength(long length, String name) throws OXException {
        try {
            // First, remember old length
            long oldLength = fileStorage.getFileSize(name);
            if (length == oldLength) {
                // Nothing to do
                return;
            }

            // Adjust file length
            fileStorage.setFileLength(length, name);

            // Adjust usage accordingly
            if (length > oldLength) {
                // Increment usage
                if (incUsage(name, length - oldLength)) {
                    throw QuotaFileStorageExceptionCodes.STORE_FULL.create();
                }
            } else {
                // Decrement usage
                decUsage(name, oldLength - length);
            }

            // Notify storage listeners
            for (FileStorageListener storageListener : storageListeners) {
                try {
                    storageListener.onFileModified(name, fileStorage);
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    @Override
    public InputStream getFile(String name, long offset, long length) throws OXException {
        try {
            return fileStorage.getFile(name, offset, length);
        } catch (OXException e) {
            LogProperties.put(LogProperties.Name.FILESTORE_URI, uri);
            throw e;
        }
    }

    /**
     * Sends notifications when the quota is exceeded
     *
     * @param id The item's id that caused the quota to exceed
     * @param quota current quota
     * @param required The required quota
     * @param newUsage The new quota after the operation
     * @param oldUsage The old quota before the operation
     */
    private void notifyOnQuotaExceeded(String id, long quota, long required, long newUsage, long oldUsage) {
        int ownerId = ownerInfo.getOwnerId();
        for (QuotaFileStorageListener quotaListener : quotaListeners) {
            try {
                quotaListener.onQuotaExceeded(id, required, oldUsage, quota, ownerId, contextId);
            } catch (Exception e) {
                LOGGER.warn("", e);
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + contextId;
        result = prime * result + ownerInfo.getOwnerId();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof DBQuotaFileStorage)) {
            return false;
        }
        DBQuotaFileStorage other = (DBQuotaFileStorage) obj;
        if (contextId != other.contextId) {
            return false;
        }
        if (ownerInfo.getOwnerId() != other.ownerInfo.getOwnerId()) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DBQuotaFileStorage [contextId=" + contextId + ", quota=" + quota + ", ownerId=" + ownerInfo.getOwnerId() + ", uri=" + uri + "]";
    }

}
