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

package com.openexchange.file.storage.webdav;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import com.google.common.io.BaseEncoding;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess.IDTuple;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.webdav.utils.WebDAVEndpointConfig;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.webdav.client.WebDAVClient;
import com.openexchange.webdav.client.WebDAVClientException;

/**
 * {@link AbstractWebDAVAccess} - Abstract WebDAV access to either files or folders.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since 7.10.4
 */
public abstract class AbstractWebDAVAccess {

    protected final WebDAVClient client;
    protected final AbstractWebDAVAccountAccess accountAccess;
    protected final Session session;
    protected final FileStorageAccount account;
    protected final WebDAVPath rootPath;

    /**
     * Initializes a new {@link AbstractWebDAVAccess}.
     *
     * @param webdavClient The WebDAV client to use
     * @param accountAccess A WebDAV account access reference
     * @throws {@link OXException} in case the account is not properly configured
     */
    protected AbstractWebDAVAccess(WebDAVClient webdavClient, AbstractWebDAVAccountAccess accountAccess) throws OXException {
        super();
        this.client = webdavClient;
        this.accountAccess = accountAccess;
        this.account = accountAccess.getAccount();
        this.session = accountAccess.getSession();
        this.rootPath = getRootPath(account);
    }

    /**
     * Gets the underlying account access.
     *
     * @return The WebDAV account access
     */
    public AbstractWebDAVAccountAccess getAccountAccess() {
        return accountAccess;
    }

    /**
     * Extracts the root WebDAV path from a file storage accounts configuration, which is the decoded path component of the endpoint
     * configured for the file storage account.
     * <p/>
     * This method is invoked during instantiation of the WebDAV access, override if applicable.
     *
     * @param account The account to get the root path from
     * @return The root WebDAV path
     * @throws OXException If the path cannot be extracted
     */
    protected WebDAVPath getRootPath(FileStorageAccount account) throws OXException {
        String endpoint = new WebDAVEndpointConfig.Builder(this.session, this.accountAccess.getWebDAVFileStorageService(), (String) account.getConfiguration().get(WebDAVFileStorageConstants.WEBDAV_URL)).build().getUrl();

        if (Strings.isEmpty(endpoint)) {
            throw FileStorageExceptionCodes.MISSING_CONFIG.create(WebDAVFileStorageConstants.WEBDAV_URL, account.getId());
        }
        if (endpoint.endsWith("/") == false) {
            endpoint += "/";
        }
        try {
            return new WebDAVPath(new URI(new URI(endpoint).normalize().getRawPath()));
        } catch (URISyntaxException e) {
            throw FileStorageExceptionCodes.INVALID_URL.create(e, e.getMessage());
        }
    }

    /**
     * Gets the file storage folder identifier, as used within the middleware, of a WebDAV collection for the given WebDAV path.
     *
     * @param webdavPath The collection's WebDAV path to get the folder identifier for
     * @return The file storage folder identifier
     * @throws UnsupportedOperationException if invoked on a path that does not reference a collection
     * @throws IllegalArgumentException if the path does not start with this account's root path
     */
    protected String getFolderId(WebDAVPath webdavPath) {
        if (rootPath.equals(webdavPath)) {
            return FileStorageFolder.ROOT_FULLNAME;
        }
        if (false == webdavPath.isCollection()) {
            throw new UnsupportedOperationException();
        }
        String rawPath = webdavPath.toURI().getRawPath();
        String rawRootPath = rootPath.toURI().getRawPath();
        if (false == rawPath.startsWith(rawRootPath)) {
            throw new IllegalArgumentException(rawPath);
        }
        String relativePath = rawPath.substring(rawRootPath.length());
        return BaseEncoding.base64Url().omitPadding().encode(relativePath.getBytes(Charsets.UTF_8));
    }

    /**
     * Gets the file storage file identifier, as used within the middleware, of a WebDAV resource for the given WebDAV path.
     *
     * @param webdavPath The resource's WebDAV path to get the file identifier for
     * @return The file file folder identifier
     * @throws UnsupportedOperationException if invoked on a path referencing a collection
     */
    protected IDTuple getFileId(WebDAVPath webdavPath) {
        if (webdavPath.isCollection()) {
            throw new UnsupportedOperationException();
        }
        String folderId = getFolderId(webdavPath.getParent());
        String fileId = BaseEncoding.base64Url().omitPadding().encode(webdavPath.getName().getBytes(Charsets.UTF_8));
        return new IDTuple(folderId, fileId);
    }

    /**
     * Gets a value indicating whether the supplied folder identifier denotes the root folder of the WebDAV account or not.
     *
     * @param folderId The folder identifier to check
     * @return <code>true</code> if the folder identifier represents the root folder, <code>false</code>, otherwise
     */
    protected static boolean isRoot(String folderId) {
        return FileStorageFolder.ROOT_FULLNAME.equals(folderId);
    }

    /**
     * Gets the (relative) path to a WebDAV collection for the supplied file storage folder identifier.
     *
     * @param folderId The file storage folder identifier to get the WebDAV path for
     * @return The WebDAV path
     * @throws OXException {@link FileStorageExceptionCodes.INVALID_FOLDER_IDENTIFIER}
     */
    protected WebDAVPath getWebDAVPath(String folderId) throws OXException {
        if (isRoot(folderId)) {
            return rootPath;
        }
        try {
            String rawRelativePath = new String(BaseEncoding.base64Url().decode(folderId), Charsets.UTF_8);
            String rawRootPath = rootPath.toURI().getRawPath();
            String path = rawRootPath + rawRelativePath;
            return new WebDAVPath(URI.create(path));
        } catch (IllegalArgumentException e) {
            throw FileStorageExceptionCodes.INVALID_FOLDER_IDENTIFIER.create(e, folderId);
        }
    }

    /**
     * Gets the (relative) path to a WebDAV resource for the supplied tuple of file storage folder and file identifier.
     *
     * @param id The file storage folder and file identifier to get the WebDAV path for
     * @return The WebDAV path
     * @throws OXException {@link FileStorageExceptionCodes.INVALID_FILE_IDENTIFIER} or
     *             {@link FileStorageExceptionCodes.INVALID_FOLDER_IDENTIFIER}
     */
    protected WebDAVPath getWebDAVPath(IDTuple id) throws OXException {
        return getWebDAVPath(id.getFolder(), id.getId());
    }

    /**
     * Gets the (relative) path to a WebDAV resource for the supplied file storage folder and file identifiers.
     *
     * @param folderId The file storage folder identifier to get the WebDAV path for
     * @param fileId The file storage file identifier to get the WebDAV path for
     * @return The WebDAV path
     * @throws OXException {@link FileStorageExceptionCodes.INVALID_FILE_IDENTIFIER} or
     *             {@link FileStorageExceptionCodes.INVALID_FOLDER_IDENTIFIER}
     */
    protected WebDAVPath getWebDAVPath(String folderId, String fileId) throws OXException {
        String name;
        try {
            name = new String(BaseEncoding.base64Url().decode(fileId), Charsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw FileStorageExceptionCodes.INVALID_FILE_IDENTIFIER.create(e, fileId);
        }
        return getWebDAVPath(folderId).append(name, false);
    }

    /**
     * Gets an appropriate file storage exception for the supplied WebDAV client exception that occurred during communication with the
     * remote WebDAV server.
     *
     * @param e The {@link WebDAVClientException} to get the {@link OXException} for
     * @return The exception to re-throw
     */
    protected OXException asOXException(WebDAVClientException e) {
        switch (e.getStatusCode()) {
            case HttpStatus.SC_NOT_IMPLEMENTED:
            case HttpStatus.SC_METHOD_NOT_ALLOWED:
                return FileStorageExceptionCodes.OPERATION_NOT_SUPPORTED.create(e, account.getFileStorageService().getId());
            case HttpStatus.SC_UNAUTHORIZED:
                return FileStorageExceptionCodes.AUTHENTICATION_FAILED.create(e, account.getId(), account.getFileStorageService().getId(), e.getMessage());
            default:
                if (indicatesInvalidUrl(e.getCause())) {
                    String endpoint = (String) account.getConfiguration().get(WebDAVFileStorageConstants.WEBDAV_URL);
                    return FileStorageExceptionCodes.INVALID_URL.create(e.getCause(), endpoint, e.getCause().getMessage());
                }
                return FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }
    
    private static boolean indicatesInvalidUrl(Throwable t) {
        return null != t && (
            java.net.UnknownHostException.class.isInstance(t) || java.net.MalformedURLException.class.isInstance(t) ||
            java.net.NoRouteToHostException.class.isInstance(t));
    }

}
