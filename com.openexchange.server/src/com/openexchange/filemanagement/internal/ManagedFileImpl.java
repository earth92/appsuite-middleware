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

package com.openexchange.filemanagement.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.openexchange.exception.OXException;
import com.openexchange.filemanagement.ManagedFile;
import com.openexchange.filemanagement.ManagedFileExceptionErrorMessage;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.groupware.notify.hostname.HostnameService;
import com.openexchange.image.ImageLocation;
import com.openexchange.java.Streams;
import com.openexchange.session.Session;

/**
 * {@link ManagedFileImpl} - Implementation of a managed file.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ManagedFileImpl implements ManagedFile, FileRemovedRegistry, TtlAware {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ManagedFileImpl.class);

    private final ManagedFileManagementImpl management;
    private final String id;
    private final File file;
    private final int optTtl;
    private final String dispatcherPrefix;

    private final BlockingQueue<FileRemovedListener> listeners;

    private volatile long lastAccessed;
    private volatile String contentType;
    private volatile String fileName;
    private volatile long size;
    private volatile String contentDisposition;
    private volatile String affiliation;

    /**
     * Initializes a new {@link ManagedFileImpl}.
     *
     * @param id The unique ID
     * @param file The kept file
     * @param dispatcherPrefix The dispatcher servlet prefix
     */
    public ManagedFileImpl(ManagedFileManagementImpl management, String id, File file, String dispatcherPrefix) {
        this(management, id, file, -1, dispatcherPrefix);
    }

    /**
     * Initializes a new {@link ManagedFileImpl}.
     *
     * @param id The unique ID
     * @param file The kept file
     * @param optTtl The optional TTL
     * @param dispatcherPrefix The dispatcher servlet prefix
     */
    public ManagedFileImpl(ManagedFileManagementImpl management, String id, File file, int optTtl, String dispatcherPrefix) {
        super();
        this.management = management;
        this.optTtl = optTtl;
        this.id = id;
        this.file = file;
        this.dispatcherPrefix = dispatcherPrefix;
        lastAccessed = System.currentTimeMillis();
        listeners = new LinkedBlockingQueue<FileRemovedListener>();
    }

    @Override
    public int optTimeToLive() {
        return optTtl;
    }

    @Override
    public String constructURL(Session session, boolean withRoute) throws OXException {
        if (null != contentType && contentType.regionMatches(true, 0, "image/", 0, 6)) {
            return new ManagedFileImageDataSource(management).generateUrl(new ImageLocation.Builder(id).build(), session);
        }
        final StringBuilder sb = new StringBuilder(64);
        final String prefix;
        final String httpSessionID;
        {
            final HostData hostData = (HostData) session.getParameter(HostnameService.PARAM_HOST_DATA);
            if (hostData == null) {
                /*
                 * Compose relative URL
                 */
                prefix = "";
                httpSessionID = null;
            } else {
                /*
                 * Compose absolute URL
                 */
                sb.append(hostData.isSecure() ? "https://" : "http://");
                sb.append(hostData.getHost());
                final int port = hostData.getPort();
                if ((hostData.isSecure() && port != 443) || (!hostData.isSecure() && port != 80)) {
                    sb.append(':').append(port);
                }
                prefix = sb.toString();
                sb.setLength(0);
                if (withRoute) {
                    httpSessionID = (null != hostData.getHTTPSession() ? hostData.getHTTPSession() : "0123456789." + hostData.getRoute());
                } else {
                    httpSessionID = null;
                }
            }
        }
        /*
         * Compose URL parameters
         */
        sb.append(prefix).append(dispatcherPrefix).append("file");
        if (null != httpSessionID) {
            sb.append(";jsessionid=").append(httpSessionID);
        }
        sb.append('?').append("id=").append(id);
        sb.append('&').append("session=").append(session.getSessionID());
        sb.append('&').append("action=get");
        return sb.toString();
    }

    @Override
    public void delete() {
        deletePlain();
        management.removeFromFiles(id);
    }

    /**
     * Deletes the backing file, but w/o removing from file-management collection.
     */
    public void deletePlain() {
        if (file.exists()) {
            for (FileRemovedListener frl; (frl = listeners.poll()) != null;) {
                frl.removePerformed(file);
            }
            if (!file.delete()) {
                LOG.warn("Temporary file could not be deleted: {}", file.getPath());
            }
        }
    }

    @Override
    public File getFile() {
        if (!file.exists()) {
            return null;
        }
        touch();
        return file;
    }

    /**
     * Gets the file reference w/o touching last-accessed time stamp
     *
     * @return The file
     */
    public File getFilePlain() {
        return file;
    }

    @Override
    public long getLastAccess() {
        return lastAccessed;
    }

    @Override
    public boolean isDeleted() {
        return !file.exists();
    }

    @Override
    public void touch() {
        long now = System.currentTimeMillis();
        lastAccessed = now;
        file.setLastModified(now);
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public InputStream getInputStream() throws OXException {
        if (!file.exists()) {
            return null;
        }
        touch();
        try {
            final CallbackInputStream retval = new CallbackInputStream(new BufferedInputStream(new FileInputStream(file), 65536), this);
            listeners.offer(retval);
            return retval;
        } catch (FileNotFoundException e) {
            throw ManagedFileExceptionErrorMessage.FILE_NOT_FOUND.create(e, file.getPath());
        }
    }

    @Override
    public int writeTo(OutputStream out, int off, int len) throws OXException {
        if (null == out) {
            return 0;
        }
        if (!file.exists()) {
            return -1;
        }
        touch();
        RandomAccessFile raf = null;
        try {
            final File tmpFile = file;
            raf = new RandomAccessFile(tmpFile, "r");
            final long total = raf.length();
            if (off >= total) {
                return 0;
            }
            // Check available bytes
            {
                final long actualLen = total - off;
                if (actualLen < len) {
                    len = (int) actualLen;
                }
            }
            // Set file pointer & start reading
            raf.seek(off);
            final int buflen = 2048;
            final byte[] bytes = new byte[buflen];
            int n = 0;
            while (n < len) {
                final int available = len - n;
                final int read = raf.read(bytes, 0, buflen > available ? available : buflen);
                if (read > 0) {
                    out.write(bytes, 0, read);
                    n += read;
                } else {
                    break;
                }
            }
            return n;
        } catch (IOException e) {
            throw ManagedFileExceptionErrorMessage.IO_ERROR.create(e, e.getMessage());
        } finally {
            Streams.close(raf);
        }

    }

    @Override
    public void removeListener(final FileRemovedListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    @Override
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(final long size) {
        this.size = size;
    }

    @Override
    public String getAffiliation() {
        return affiliation;
    }

    @Override
    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    @Override
    public String getContentDisposition() {
        return contentDisposition;
    }

    @Override
    public void setContentDisposition(final String contentDisposition) {
        this.contentDisposition = contentDisposition;
    }
}
