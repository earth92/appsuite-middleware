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

package com.openexchange.tools.oxfolder.treeconsistency;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderEventConstants;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.userconfiguration.UserConfiguration;
import com.openexchange.server.impl.DBPool;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.session.Session;
import com.openexchange.tools.oxfolder.OXFolderSQL;

/**
 * {@link CheckPermission}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
abstract class CheckPermission {

    protected final Session session;

    protected final int sessionUser;

    protected final Context ctx;

    protected final Connection writeCon;

    /**
     * Initializes a new {@link CheckPermission}
     *
     * @param session The session
     * @param writeCon A connection with write capability
     * @param ctx The context
     */
    protected CheckPermission(final Session session, final Connection writeCon, final Context ctx) {
        super();
        this.ctx = ctx;
        this.writeCon = writeCon;
        this.session = session;
        sessionUser = session.getUserId();
    }

    /**
     * Gets the folder from master database
     *
     * @param folderId The folder ID
     * @return The folder from master database
     * @throws OXException If folder cannot be fetched from master database
     */
    protected FolderObject getFolderFromMaster(final int folderId) throws OXException {
        return getFolderFromMaster(folderId, false);
    }

    /**
     * Gets the folder from master database with or without subfolder IDs loaded
     *
     * @param folderId The folder ID
     * @param withSubfolders whether to load subfolder IDs, too
     * @return The folder from master database
     * @throws OXException If folder cannot be fetched from master database
     */
    protected FolderObject getFolderFromMaster(final int folderId, final boolean withSubfolders) throws OXException {
        /*
         * Use writable connection to ensure to fetch from master database
         */
        Connection wc = writeCon;
        if (wc == null) {
            try {
                wc = DBPool.pickupWriteable(ctx);
                return FolderObject.loadFolderObjectFromDB(folderId, ctx, wc, true, withSubfolders);
            } finally {
                if (wc != null) {
                    DBPool.closeWriterAfterReading(ctx, wc);
                }
            }
        }
        return FolderObject.loadFolderObjectFromDB(folderId, ctx, wc, true, withSubfolders);
    }

    /**
     * Gets the effective user permission
     *
     * @param userId The user ID
     * @param userConfig The user's configuration
     * @param folder The folder needed to determine type, module, etc.
     * @param permissions The basic permissions to check against
     * @return The effective user permission
     * @throws OXException If loading user's groups fails
     */
    protected static EffectivePermission getEffectiveUserPermission(final int userId, final UserConfiguration userConfig, final FolderObject folder, final OCLPermission[] permissions) throws OXException {
        final EffectivePermission maxPerm = new EffectivePermission(
            userId,
            folder.getObjectID(),
            folder.getType(userId),
            folder.getModule(),
            folder.getCreatedBy(),
            userConfig);
        maxPerm.setAllPermission(
            OCLPermission.NO_PERMISSIONS,
            OCLPermission.NO_PERMISSIONS,
            OCLPermission.NO_PERMISSIONS,
            OCLPermission.NO_PERMISSIONS);
        final int[] idArr;
        {
            int[] groups = userConfig.getGroups();
            if (null == groups) {
                groups = UserStorage.getInstance().getUser(userId, userConfig.getContext()).getGroups();
            }
            idArr = new int[groups.length + 1];
            idArr[0] = userId;
            System.arraycopy(groups, 0, idArr, 1, groups.length);
            Arrays.sort(idArr);
        }
        NextPerm: for (int i = 0; i < permissions.length; i++) {
            final OCLPermission oclPerm = permissions[i];
            if (Arrays.binarySearch(idArr, oclPerm.getEntity()) < 0) {
                continue NextPerm;
            }
            if (oclPerm.getFolderPermission() > maxPerm.getFolderPermission()) {
                maxPerm.setFolderPermission(oclPerm.getFolderPermission());
            }
            if (oclPerm.getReadPermission() > maxPerm.getReadPermission()) {
                maxPerm.setReadObjectPermission(oclPerm.getReadPermission());
            }
            if (oclPerm.getWritePermission() > maxPerm.getWritePermission()) {
                maxPerm.setWriteObjectPermission(oclPerm.getWritePermission());
            }
            if (oclPerm.getDeletePermission() > maxPerm.getDeletePermission()) {
                maxPerm.setDeleteObjectPermission(oclPerm.getDeletePermission());
            }
            if (!maxPerm.isFolderAdmin() && oclPerm.isFolderAdmin()) {
                maxPerm.setFolderAdmin(true);
            }
        }
        return maxPerm;
    }

    /**
     * Checks if specified permissions contain a system-read-folder permission for given entity
     *
     * @param permissions The permissions to check
     * @param entity The entity
     * @return <code>true</code> if specified permissions contain a system-read-folder permission for given entity; otherwise
     *         <code>false</code>
     */
    protected static boolean containsSystemPermission(final List<OCLPermission> permissions, final int entity) {
        for (final OCLPermission cur : permissions) {
            if (cur.getEntity() == entity && cur.isSystem()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Broadcast folder event.
     *
     * @param folderId The folder identifier
     * @param deleted <code>true</code> if deleted; otherwise changed
     * @param eventAdmin The event admin service reference
     * @throws OXException
     */
    protected void broadcastEvent(final int folderId, final boolean deleted, final EventAdmin eventAdmin) throws OXException {
        if (null != eventAdmin) {
            final Dictionary<String, Object> properties = new Hashtable<String, Object>(6);
            properties.put(FolderEventConstants.PROPERTY_CONTEXT, Integer.valueOf(session.getContextId()));
            properties.put(FolderEventConstants.PROPERTY_USER, Integer.valueOf(session.getUserId()));
            properties.put(FolderEventConstants.PROPERTY_SESSION, session);
            properties.put(FolderEventConstants.PROPERTY_FOLDER, String.valueOf(folderId));
            properties.put(FolderEventConstants.PROPERTY_CONTENT_RELATED, Boolean.valueOf(!deleted));
            if (deleted) {
              //get path to root and send it this is only needed if folder is changed
                String[] pathToRootString = OXFolderSQL.getFolderPath(folderId, writeCon, ctx);
                properties.put(FolderEventConstants.PROPERTY_FOLDER_PATH, pathToRootString);
            }
            /*
             * Create event with push topic
             */
            final Event event = new Event(FolderEventConstants.TOPIC, properties);
            /*
             * Finally deliver it
             */
            eventAdmin.sendEvent(event);
            final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CheckPermission.class);
            logger.debug("Notified content-related-wise changed folder \"{} in context {}", I(folderId), I(session.getContextId()));
        }
    }

}
