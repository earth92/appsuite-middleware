/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.drive.impl.internal;

import static com.openexchange.java.Autoboxing.I;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.capabilities.CapabilitySet;
import com.openexchange.drive.DriveExceptionCodes;
import com.openexchange.drive.DriveFileField;
import com.openexchange.drive.DriveSession;
import com.openexchange.drive.checksum.rdb.RdbChecksumStore;
import com.openexchange.drive.impl.DriveUtils;
import com.openexchange.drive.impl.checksum.ChecksumProvider;
import com.openexchange.drive.impl.checksum.ChecksumStore;
import com.openexchange.drive.impl.checksum.ChecksumSupplier;
import com.openexchange.drive.impl.checksum.DirectoryChecksum;
import com.openexchange.drive.impl.checksum.FileChecksum;
import com.openexchange.drive.impl.comparison.LazyServerDirectoryVersion;
import com.openexchange.drive.impl.comparison.ServerDirectoryVersion;
import com.openexchange.drive.impl.comparison.ServerFileVersion;
import com.openexchange.drive.impl.management.DriveConfig;
import com.openexchange.drive.impl.storage.DriveStorage;
import com.openexchange.drive.impl.storage.DriveTemp;
import com.openexchange.drive.impl.storage.StorageOperation;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.File;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.composition.FilenameValidationUtils;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.share.core.tools.PermissionResolver;
import com.openexchange.tools.session.ServerSession;
import jonelo.jacksum.algorithm.MD;

/**
 * {@link SyncSession}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class SyncSession {

    private final DriveSession session;
    private final Tracer tracer;
    private final DriveConfig config;
    private final long maxProcessingTime;
    private ChecksumStore checksumStore;
    private DriveStorage storage;
    private DirectLinkGenerator linkGenerator;
    private CapabilitySet capabilities;
    private DriveTemp temp;
    private PermissionResolver permissionResolver;

    /**
     * Initializes a new {@link SyncSession}.
     *
     * @param session The underlying drive session
     */
    public SyncSession(DriveSession session) {
        this(session, -1L);
    }

    /**
     * Initializes a new {@link SyncSession}.
     *
     * @param session The underlying drive session
     * @param maxProcessingTime The maximum processing time (in milliseconds) to check against regularly, or <code>-1</code> if unrestricted
     */
    public SyncSession(DriveSession session, long maxProcessingTime) {
        super();
        this.session = session;
        this.config = new DriveConfig(session.getServerSession().getContextId(), session.getServerSession().getUserId());
        this.tracer = new Tracer(session.isDiagnostics());
        this.maxProcessingTime = 0 < maxProcessingTime ? (System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxProcessingTime)) : Long.MAX_VALUE;
        if (isTraceEnabled()) {
            trace("Initializing sync session for user " + session.getServerSession().getLoginName() + " (" +
                session.getServerSession().getUserId() + ") in context " + session.getServerSession().getContextId() +
                ", root folder ID is " + session.getRootFolderID() +
                " via client " + session.getClientType() + " v" + session.getClientVersion());
        }
    }

    /**
     * Checks whether a configured maximum processing time is elapsed since this synch session was initialized. If this is the case, a
     * corresponding exception is thrown to prematurely abort the current synchronization request.
     *
     * @throws OXException In case the maximum sync processing time is elapsed
     */
    private void checkMaxSyncProcessingTime() throws OXException {
        if (System.nanoTime() > maxProcessingTime) {
            throw DriveExceptionCodes.SERVER_BUSY.create(new Exception("Maximum processing time for current sync operation is exceeded."));
        }
    }

    /**
     * Gets the underlying server session
     *
     * @return The server session
     */
    public ServerSession getServerSession() {
        return session.getServerSession();
    }

    /**
     * Gets the file metadata fields relevant for the client.
     *
     * @return The file metadata fields, or <code>null</code> if not specified
     */
    public List<DriveFileField> getFields() {
        return session.getFields();
    }

    /**
     * Gets the drive storage
     *
     * @return The drive storage
     */
    public DriveStorage getStorage() throws OXException {
        checkMaxSyncProcessingTime();
        if (null == storage) {
            storage = new DriveStorage(this);
        }
        return storage;
    }

    /**
     * Gets the drive configuration.
     *
     * @return The drive config
     */
    public DriveConfig getConfig() {
        return config;
    }

    /**
     * Gets the permission resolver.
     *
     * @return The permission resolver
     */
    public PermissionResolver getPermissionResolver() {
        if (null == permissionResolver) {
            permissionResolver = new PermissionResolver(DriveServiceLookup.get(), getServerSession());
        }
        return permissionResolver;
    }

    /**
     * Get the identifier of the referenced root folder on the server.
     *
     * @return The root folder ID.
     */
    public String getRootFolderID() {
        return session.getRootFolderID();
    }

    /**
     * Gets the checksum store.
     *
     * @return The checksum store
     */
    public ChecksumStore getChecksumStore() throws OXException {
        checkMaxSyncProcessingTime();
        if (null == checksumStore) {
            checksumStore = new RdbChecksumStore(getServerSession().getContextId());
        }
        return checksumStore;
    }

    /**
     * Creates a new MD5 instance.
     *
     * @return A new MD5 instance.
     * @throws OXException
     */
    public MD newMD5() throws OXException {
        try {
            return new MD("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw DriveExceptionCodes.IO_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets the device name as supplied by the client.
     *
     * @return The device name, or <code>null</code> if not set
     */
    public String getDeviceName() {
        return session.getDeviceName();
    }

    /**
     * Gets the underlying drive session.
     *
     * @return The drive session.
     */
    public DriveSession getDriveSession() {
        return session;
    }

    /**
     * Gets the host data of the session.
     *
     * @return The host data
     */
    public HostData getHostData() {
        return session.getHostData();
    }

    /**
     * Gets the direct link generator.
     *
     * @return The direct link generator
     */
    public DirectLinkGenerator getLinkGenerator() {
        if (null == this.linkGenerator) {
            linkGenerator = new DirectLinkGenerator(this);
        }
        return linkGenerator;
    }

    /**
     * Appends a new line for the supplied message into the trace log.
     *
     * @param message The message to trace
     */
    public void trace(Object message) {
        tracer.trace(message);
    }

    /**
     * Gets the recorded trace log.
     *
     * @return The trace log
     */
    public String getDiagnosticsLog() {
        return tracer.getTraceLog();
    }

    /**
     * Gets a value indicating whether tracing is enabled either in the named logger instance or the drive-internal diagnostics log
     * generator.
     *
     * @return <code>true</code> if tracing is enabled, <code>false</code>, otherwise
     */
    public boolean isTraceEnabled() {
        return tracer.isTraceEnabled();
    }

    /**
     * Gets a helper to access the special ".drive" folder for temporary uploads.
     *
     * @return The drive temp helper
     */
    public DriveTemp getTemp() {
        if (null == temp) {
            temp = new DriveTemp(this);
        }
        return temp;
    }

    /**
     * Gets a list of all file versions in a directory available at the server. Only synchronized files are taken into account, i.e.
     * invalid and ignored files are excluded from the result. Missing file checksums will be calculated on demand.
     *
     * @return The server directory versions
     */
    public List<ServerFileVersion> getServerFiles(String path) throws OXException {
        return getServerFiles(path, -1);
    }

    /**
     * Gets a list of all file versions in a directory available at the server. Only synchronized files are taken into account, i.e.
     * invalid and ignored files are excluded from the result. Missing file checksums will be calculated on demand.
     *
     * @param limit The maximum number of files to add before throwing an exception, or <code>-1</code> for no limitations
     * @return The server directory versions
     */
    public List<ServerFileVersion> getServerFiles(String path, int limit) throws OXException {
        FileStorageFolder folder = getStorage().getFolder(path);
        List<File> files = getStorage().getFilesInFolder(folder.getId());
        int maxFilesPerDirectory = getConfig().getMaxFilesPerDirectory();
        if (-1 != maxFilesPerDirectory && files.size() > maxFilesPerDirectory) {
            throw DriveExceptionCodes.TOO_MANY_FILES.create(I(maxFilesPerDirectory), path);
        }
        StringBuilder stringBuilder = isTraceEnabled() ? new StringBuilder("Server files in path \"").append(path).append("\":\n") : null;
        List<FileChecksum> checksums = ChecksumProvider.getChecksums(this, folder.getId(), files);
        List<ServerFileVersion> serverFiles = new ArrayList<ServerFileVersion>(files.size());
        for (int i = 0; i < files.size(); i++) {
            ServerFileVersion fileVersion = new ServerFileVersion(files.get(i), checksums.get(i));
            serverFiles.add(fileVersion);
            if (stringBuilder != null) {
                stringBuilder.append(" [").append(fileVersion.getFileChecksum().getFileID()).append("] ")
                    .append(fileVersion.getName()).append(" | ").append(fileVersion.getChecksum())
                    .append(" (").append(fileVersion.getFileChecksum().getSequenceNumber()).append(")\n");
            }
        }
        if (stringBuilder != null) {
            trace(stringBuilder);
        }
        return serverFiles;
    }

    /**
     * Gets a list of all directory versions available at the server. Only synchronized folders are taken into account, i.e. invalid and
     * ignored directories are excluded from the result. Missing directory checksums will be calculated on demand.
     *
     * @param limit The maximum number of directories to add before throwing an exception, or <code>-1</code> for no limitations
     * @return The server directory versions
     */
    public List<ServerDirectoryVersion> getServerDirectories(final int limit) throws OXException {
        return getStorage().wrapInTransaction(new StorageOperation<List<ServerDirectoryVersion>>() {

            @Override
            public List<ServerDirectoryVersion> call() throws OXException {
                Map<String, FileStorageFolder> folders;
                if (-1 != limit) {
                    folders = getStorage().getFolders(limit + 1);
                    if (folders.size() > limit) {
                        throw DriveExceptionCodes.TOO_MANY_DIRECTORIES.create(I(limit));
                    }
                } else {
                    folders = getStorage().getFolders();
                }
                return getServerDirectoryVersions(folders);
            }
        });
    }

    /**
     * Gets a single directory version from the server. Only synchronized folders are taken into account, i.e. invalid and ignored
     * directories are excluded. Missing directory checksums will be calculated on demand, and the folder is created implicitly in case
     * it not already exists.
     *
     * @param path The path to get the directory version for
     * @return The server directory versions
     */
    public ServerDirectoryVersion getServerDirectory(String path) throws OXException {
        return getStorage().wrapInTransaction(new StorageOperation<ServerDirectoryVersion>() {

            @Override
            public ServerDirectoryVersion call() throws OXException {
                FileStorageFolder folder = getStorage().getFolder(path, true);
                List<ServerDirectoryVersion> directoryVersions = getServerDirectoryVersions(Collections.singletonMap(path, folder));
                return 0 < directoryVersions.size() ? directoryVersions.get(0) : null;
            }
        });
    }

    /**
     * Gets a list of directory versions for the supplied mapping of paths to folders. Only synchronized folders are taken into account,
     * i.e. invalid and ignored directories are excluded from the result. Missing directory checksums will be calculated on demand.
     *
     * @param folders The mapped folders to get the directory checksums for
     * @return The server directory versions
     */
    List<ServerDirectoryVersion> getServerDirectoryVersions(Map<String, FileStorageFolder> folders) throws OXException {
        List<String> folderIDs = new ArrayList<String>(folders.size());
        for (Map.Entry<String, FileStorageFolder> entry : folders.entrySet()) {
            String path = entry.getKey();
            if (DriveUtils.isInvalidPath(path) || FilenameValidationUtils.isInvalidFolderName(entry.getValue().getName())) {
                trace("Skipping invalid server directory: " + path);
            } else if (DriveUtils.isIgnoredPath(this, path)) {
                trace("Skipping ignored server directory: " + path);
            } else {
                folderIDs.add(entry.getValue().getId());
            }
        }
        if (0 == folderIDs.size()) {
            trace("All server directories skipped.");
            return Collections.emptyList();
        }
        StringBuilder stringBuilder = isTraceEnabled() ? new StringBuilder("Server directories:\n") : null;
        List<ServerDirectoryVersion> serverDirectories = new ArrayList<ServerDirectoryVersion>(folderIDs.size());
        int lazyDirectoryChecksumChunkSize = 2 + getConfig().getMaxDirectoryActions();
        if (lazyDirectoryChecksumChunkSize < folderIDs.size() && getConfig().isLazyDirectoryChecksumCalculation()) {
            ChecksumSupplier checksumSupplier = new ChecksumSupplier(this, folderIDs, lazyDirectoryChecksumChunkSize);
            for (String folderID : folderIDs) {
                LazyServerDirectoryVersion directoryVersion = new LazyServerDirectoryVersion(getStorage().getPath(folderID), folderID, checksumSupplier);
                serverDirectories.add(directoryVersion);
                if (null != stringBuilder) {
                    DirectoryChecksum checksum = directoryVersion.optDirectoryChecksum();
                    stringBuilder.append(" [").append(folderID).append("] ")
                        .append(directoryVersion.getPath()).append(" | ").append(null != checksum ? checksum.getChecksum() : "<pending>")
                        .append(" (").append(null != checksum ? checksum.getSequenceNumber() : 0L).append(")\n");
                }
            }
        } else {
            List<DirectoryChecksum> checksums = ChecksumProvider.getChecksums(this, folderIDs);
            for (int i = 0; i < folderIDs.size(); i++) {
                ServerDirectoryVersion directoryVersion = new ServerDirectoryVersion(getStorage().getPath(folderIDs.get(i)), checksums.get(i));
                serverDirectories.add(directoryVersion);
                if (null != stringBuilder) {
                    stringBuilder.append(" [").append(directoryVersion.getDirectoryChecksum().getFolderID()).append("] ")
                        .append(directoryVersion.getPath()).append(" | ").append(directoryVersion.getChecksum())
                        .append(" (").append(directoryVersion.getDirectoryChecksum().getSequenceNumber()).append(")\n");
                }
            }
        }
        if (null != stringBuilder) {
            trace(stringBuilder);
        }
        return serverDirectories;
    }

    /**
     * Gets a value indicating whether the session user has a specific capability or not.
     *
     * @param capability The capability to check for, e.g. <code>spreadsheet</code>
     * @return <code>true</code> if the capability is set and enabled for the user, <code>false</code>, otherwise
     */
    public boolean hasCapability(String capability) {
        if (null == capabilities) {
            CapabilityService capabilityService = DriveServiceLookup.getService(CapabilityService.class);
            if (null != capabilityService) {
                try {
                    capabilities = capabilityService.getCapabilities(session.getServerSession());
                } catch (OXException e) {
                    org.slf4j.LoggerFactory.getLogger(SyncSession.class).warn("Error determining capabilities", e);
                }
            }
        }
        return null != capabilities && capabilities.contains(capability);
    }

    /**
     * Gets the maximum file length of uploads to be stored directly at the target location - others are going to be written to a
     * temporary upload file first.
     *
     * @param session The drive session
     * @return The optimistic save threshold in bytes
     */
    public long getOptimisticSaveThreshold() {
        if (null != session.getClientType() && session.getClientType().isDesktop()) {
            return getConfig().getOptimisticSaveThresholdDesktop();
        }
        return getConfig().getOptimisticSaveThresholdMobile();
    }

    @Override
    public String toString() {
        return session.getServerSession().getLoginName() + " [" + session.getServerSession().getContextId() + ':' +
            session.getServerSession().getUserId() + "] # " + session.getRootFolderID();
    }

}
