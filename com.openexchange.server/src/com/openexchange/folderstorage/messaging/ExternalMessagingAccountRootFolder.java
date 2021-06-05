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

package com.openexchange.folderstorage.messaging;

import com.openexchange.folderstorage.AbstractFolder;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.type.MailType;
import com.openexchange.folderstorage.type.SystemType;
import com.openexchange.messaging.MessagingAccount;
import com.openexchange.messaging.MessagingFolder;
import com.openexchange.session.Session;
import com.openexchange.tools.id.IDMangler;

/**
 * {@link ExternalMessagingAccountRootFolder} - A mail folder.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ExternalMessagingAccountRootFolder extends AbstractFolder {

    private static final long serialVersionUID = -7259106085690350497L;

    private final com.openexchange.folderstorage.messaging.MessagingFolderImpl.MessagingFolderType messagingFolderType;

    /**
     * Initializes a new {@link ExternalMessagingAccountRootFolder} from given mail account.
     * <p>
     * Subfolder identifiers and tree identifier are not set within this constructor.
     *
     * @param msgAccount The underlying messaging account
     * @param serviceId The service identifier
     * @param session The session
     */
    public ExternalMessagingAccountRootFolder(final MessagingAccount msgAccount, final String serviceId, final Session session) {
        this(msgAccount, serviceId, session, null);
    }

    /**
     * Initializes a new {@link ExternalMessagingAccountRootFolder} from given mail account.
     * <p>
     * Subfolder identifiers and tree identifier are not set within this constructor.
     *
     * @param msgAccount The underlying messaging account
     * @param serviceId The service identifier
     * @param session The session
     */
    public ExternalMessagingAccountRootFolder(final MessagingAccount msgAccount, final String serviceId, final Session session, final int[] rootPerms) {
        super();
        final String fullname = MessagingFolder.ROOT_FULLNAME;
        id = MessagingFolderIdentifier.getFQN(serviceId, msgAccount.getId(), fullname);
        parent = FolderStorage.PRIVATE_ID;
        /*
         * Set proper name
         */
        name = msgAccount.getDisplayName();
        super.accountId = IDMangler.mangle(serviceId, Integer.toString(msgAccount.getId()));
        final MessagingPermissionImpl mp = new MessagingPermissionImpl();
        mp.setEntity(session.getUserId());
        if (null == rootPerms) {
            mp.setAllPermissions(Permission.READ_FOLDER, Permission.READ_ALL_OBJECTS, Permission.NO_PERMISSIONS, Permission.NO_PERMISSIONS);
        } else {
            mp.setAllPermissions(rootPerms[0], rootPerms[1], rootPerms[2], rootPerms[3]);
        }
        mp.setAdmin(false);
        permissions = new Permission[] { mp };
        type = SystemType.getInstance();
        subscribed = true;
        subscribedSubfolders = true;
        capabilities = 0;
        summary = "";
        deefault = false;
        total = 0;
        nu = 0;
        unread = 0;
        deleted = 0;
        messagingFolderType = com.openexchange.folderstorage.messaging.MessagingFolderImpl.MessagingFolderType.ROOT;
    }

    @Override
    public ExternalMessagingAccountRootFolder clone() {
        return (ExternalMessagingAccountRootFolder) super.clone();
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public ContentType getContentType() {
        return messagingFolderType.getContentType();
    }

    @Override
    public int getDefaultType() {
        return messagingFolderType.getType();
    }

    @Override
    public void setDefaultType(final int defaultType) {
        // Nothing to do
    }

    @Override
    public Type getType() {
        return MailType.getInstance();
    }

    @Override
    public void setContentType(final ContentType contentType) {
        // Nothing to do
    }

    @Override
    public void setType(final Type type) {
        // Nothing to do
    }

    @Override
    public boolean isGlobalID() {
        return false;
    }

}
