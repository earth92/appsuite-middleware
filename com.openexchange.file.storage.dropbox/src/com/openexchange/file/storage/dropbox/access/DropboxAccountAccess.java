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

package com.openexchange.file.storage.dropbox.access;

import com.openexchange.exception.OXException;
import com.openexchange.file.storage.CapabilityAware;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageCapability;
import com.openexchange.file.storage.FileStorageCapabilityTools;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStorageService;
import com.openexchange.file.storage.dropbox.DropboxServices;
import com.openexchange.oauth.KnownApi;
import com.openexchange.oauth.OAuthUtil;
import com.openexchange.oauth.access.AbstractOAuthAccess;
import com.openexchange.oauth.access.OAuthAccess;
import com.openexchange.oauth.access.OAuthAccessRegistry;
import com.openexchange.oauth.access.OAuthAccessRegistryService;
import com.openexchange.session.Session;

/**
 * {@link DropboxAccountAccess}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class DropboxAccountAccess implements FileStorageAccountAccess, CapabilityAware {

    private final FileStorageAccount account;
    private final Session session;
    private final FileStorageService service;
    private OAuthAccess dropboxOAuthAccess;

    /**
     * Initializes a new {@link DropboxAccountAccess}.
     */
    public DropboxAccountAccess(final FileStorageService service, final FileStorageAccount account, final Session session) {
        super();
        this.service = service;
        this.account = account;
        this.session = session;
    }

    @Override
    public Boolean supports(FileStorageCapability capability) {
        if (capability.isFileAccessCapability()) {
            return FileStorageCapabilityTools.supportsByClass(DropboxFileAccess.class, capability);
        }
        return FileStorageCapabilityTools.supportsFolderCapabilityByClass(DropboxFolderAccess.class, capability);
    }

    /**
     * Gets the associated account
     *
     * @return The account
     */
    public FileStorageAccount getAccount() {
        return account;
    }

    @Override
    public void connect() throws OXException {
        OAuthAccessRegistryService service = DropboxServices.getService(OAuthAccessRegistryService.class);
        OAuthAccessRegistry registry = service.get(KnownApi.DROPBOX.getServiceId());
        int accountId = OAuthUtil.getAccountId(account.getConfiguration());
        OAuthAccess dropboxOAuthAccess = registry.get(session.getContextId(), session.getUserId(), accountId);
        if (dropboxOAuthAccess == null) {
            AbstractOAuthAccess newInstance = new DropboxOAuth2Access(account, session);
            dropboxOAuthAccess = registry.addIfAbsent(session.getContextId(), session.getUserId(), accountId, newInstance);
            if (dropboxOAuthAccess == null) {
                newInstance.initialize();
                dropboxOAuthAccess = newInstance;
            }
        } else {
            this.dropboxOAuthAccess = dropboxOAuthAccess.ensureNotExpired();
        }
        this.dropboxOAuthAccess = dropboxOAuthAccess;
    }

    @Override
    public boolean isConnected() {
        return null != dropboxOAuthAccess;
    }

    @Override
    public void close() {
        dropboxOAuthAccess = null;
    }

    @Override
    public boolean ping() throws OXException {
        return dropboxOAuthAccess.ping();
    }

    @Override
    public boolean cacheable() {
        return false;
    }

    @Override
    public String getAccountId() {
        return account.getId();
    }

    @Override
    public FileStorageFileAccess getFileAccess() throws OXException {
        OAuthAccess dropboxOAuthAccess = this.dropboxOAuthAccess;
        if (null == dropboxOAuthAccess) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        return new DropboxFileAccess((AbstractOAuthAccess) dropboxOAuthAccess, account, session, this, getDropboxFolderAccess());
    }

    @Override
    public FileStorageFolderAccess getFolderAccess() throws OXException {
        return getDropboxFolderAccess();
    }

    private DropboxFolderAccess getDropboxFolderAccess() throws OXException {
        OAuthAccess dropboxOAuthAccess = this.dropboxOAuthAccess;
        if (null == dropboxOAuthAccess) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        return new DropboxFolderAccess((AbstractOAuthAccess) dropboxOAuthAccess, account, session);
    }

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        connect();
        return getFolderAccess().getRootFolder();
    }

    @Override
    public FileStorageService getService() {
        return service;
    }

}
