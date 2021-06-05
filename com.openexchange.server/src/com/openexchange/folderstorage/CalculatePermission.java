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

package com.openexchange.folderstorage;

import java.util.ArrayList;
import java.util.Collections;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.internal.EffectivePermission;
import com.openexchange.folderstorage.internal.performers.AbstractPerformer;
import com.openexchange.folderstorage.type.PrivateType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.groupware.userconfiguration.UserPermissionBitsStorage;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.userconf.UserPermissionService;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * {@link CalculatePermission} - Utility class to obtain an effective permission.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class CalculatePermission {

    /**
     * Initializes a new {@link CalculatePermission}.
     */
    private CalculatePermission() {
        super();
    }

    /**
     * Calculates the effective user permissions for given folder.
     *
     * @param folder The folder whose effective user permissions shall be calculated
     * @param context The context
     */
    public static void calculateUserPermissions(Folder folder, Context context) {
        Permission[] staticPermissions = folder.getPermissions();
        if (null == staticPermissions || 0 == staticPermissions.length) {
            return;
        }
        UserPermissionBitsStorage userConfStorage = UserPermissionBitsStorage.getInstance();
        String id = folder.getID();
        Type type = folder.getType();
        ContentType contentType = folder.getContentType();

        Permission[] userizedPermissions = new Permission[staticPermissions.length];
        TIntIntHashMap toLoad = new TIntIntHashMap(staticPermissions.length);
        for (int index = 0; index < staticPermissions.length; index++) {
            Permission staticPermission = staticPermissions[index];
            if (0 == staticPermission.getSystem()) {
                // A non-system permission
                if (staticPermission.isGroup() || 0 >= staticPermission.getEntity()) {
                    userizedPermissions[index] = staticPermission;
                } else {
                    // Load appropriate user configuration
                    toLoad.put(staticPermission.getEntity(), index);
                }
            }
        }
        /*
         * Batch-load user configurations
         */
        if (!toLoad.isEmpty()) {
            int[] userIds = toLoad.keys();
            try {
                UserPermissionBits[] configurations = userConfStorage.getUserPermissionBits(context, userIds);
                for (int i = 0; i < configurations.length; i++) {
                    int userId = userIds[i];
                    if (toLoad.containsKey(userId)) {
                        int index = toLoad.get(userId);
                        UserPermissionBits userPermissionBits = configurations[i];
                        userizedPermissions[index] = new EffectivePermission(
                            staticPermissions[index],
                            id,
                            type,
                            contentType,
                            userPermissionBits,
                            Collections.<ContentType> emptyList()).setEntityInfo(userId, context);
                    }
                }
            } catch (OXException e) {
                final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CalculatePermission.class);
                logger.warn("User configuration could not be loaded. Ignoring user permissions.", e);
            }
        }
        /*
         * Remove possible null values & apply to folder
         */
        java.util.List<Permission> tmp = new ArrayList<Permission>(userizedPermissions.length);
        for (int i = 0; i < userizedPermissions.length; i++) {
            Permission p = userizedPermissions[i];
            if (null != p) {
                tmp.add(p);
            }
        }
        folder.setPermissions(tmp.toArray(new Permission[tmp.size()]));
    }

    /**
     * Calculates the effective permission for given user in given folder.
     *
     * @param folder The folder
     * @param performer The performer to calculate for
     * @param allowedContentTypes The allowed content types; an empty list indicates all are allowed
     * @return The effective permission for given user in given folder
     * @throws OXException If calculating the effective permission fails
     */
    public static Permission calculate(final Folder folder, final AbstractPerformer performer, final java.util.List<ContentType> allowedContentTypes) throws OXException {
        final ServerSession session = performer.getSession();
        if (null == session) {
            return calculate(folder, performer.getUser(), performer.getContext(), allowedContentTypes);
        }
        return calculate(folder, session, allowedContentTypes);
    }

    /**
     * Calculates the effective permission for given user in given folder.
     *
     * @param folder The folder
     * @param user The user
     * @param context The context
     * @param allowedContentTypes The allowed content types; an empty list indicates all are allowed
     * @return The effective permission for given user in given folder
     * @throws OXException If calculating the effective permission fails
     */
    public static Permission calculate(final Folder folder, final User user, final Context context, final java.util.List<ContentType> allowedContentTypes) throws OXException {
        try {
            UserPermissionBits userPermissionBits = getUserPermissionBits(user.getId(), context);
            return new EffectivePermission(getMaxPermission(folder.getPermissions(), userPermissionBits), folder.getID(), folder.getType(), folder.getContentType(), userPermissionBits, allowedContentTypes).setEntityInfo(user.getId(), context);
        } catch (OXException e) {
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e.getMessage(), e);
        }
    }

    public static boolean isVisible(final Folder folder, final User user, final Context context, final java.util.List<ContentType> allowedContentTypes) throws OXException {
        final UserPermissionBits userPermissionBits;
        try {
            userPermissionBits = getUserPermissionBits(user.getId(), context);
        } catch (OXException e) {
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e.getMessage(), e);
        }


        /*
         * Check visibility by folder
         */
        Type type = folder.getType();
        ContentType contentType = folder.getContentType();
        if (PrivateType.getInstance().equals(type)) {
            int createdBy = folder.getCreatedBy();
            if (createdBy <= 0 || createdBy == user.getId()) {
                return hasAccess(contentType, userPermissionBits, allowedContentTypes);
            }
        }

        /*
         * Check visibility by effective permission
         */
        return new EffectivePermission(getMaxPermission(folder.getPermissions(), userPermissionBits), folder.getID(), type, contentType, userPermissionBits, allowedContentTypes).isVisible();
    }

    private static boolean hasAccess(final ContentType contentType, final UserPermissionBits permissionBits, final java.util.List<ContentType> allowedContentTypes) {
        final int module = contentType.getModule();
        if (!permissionBits.hasModuleAccess(module)) {
            return false;
        }
        if (null == allowedContentTypes || allowedContentTypes.isEmpty()) {
            return true;
        }
        final TIntSet set = new TIntHashSet(allowedContentTypes.size() + 2);
        for (final ContentType allowedContentType : allowedContentTypes) {
            set.add(allowedContentType.getModule());
        }
        // Module SYSTEM is allowed in any case
        set.add(FolderObject.SYSTEM_MODULE);
        set.add(FolderObject.UNBOUND);
        return set.isEmpty() ? true : set.contains(module);
    }

    /**
     * Calculates the effective permission for given session's user in given folder.
     *
     * @param folder The folder
     * @param session The session
     * @param allowedContentTypes The allowed content types; an empty list indicates all are allowed
     * @return The effective permission for given session's user in given folder
     */
    public static Permission calculate(final Folder folder, final ServerSession session, final java.util.List<ContentType> allowedContentTypes) {
        if (session.isAnonymous()) {
            // Deny for anonymous user
            BasicPermission p = new BasicPermission();
            p.setNoPermissions();
            return p;
        }

        UserPermissionBits userPermissionBits = session.getUserPermissionBits();
        return new EffectivePermission(
            getMaxPermission(folder.getPermissions(), userPermissionBits),
            folder.getID(),
            folder.getType(),
            folder.getContentType(),
            userPermissionBits,
            allowedContentTypes);
    }

    private static UserPermissionBits getUserPermissionBits(int userId, Context context) throws OXException {
        UserPermissionService service = ServerServiceRegistry.getInstance().getService(UserPermissionService.class);
        if (null == service) {
            throw ServiceExceptionCode.absentService(UserPermissionService.class);
        }

        return service.getUserPermissionBits(userId, context);
    }

    private static Permission getMaxPermission(final Permission[] permissions, final UserPermissionBits userPermissionBits) {
        BasicPermission p = new BasicPermission();
        p.setNoPermissions();
        p.setEntity(userPermissionBits.getUserId());

        if (null == permissions || 0 == permissions.length) {
            return p;
        }

        TIntSet ids = getEntityIdsFor(userPermissionBits);
        int fp = 0;
        int rp = 0;
        int wp = 0;
        int dp = 0;
        boolean admin = false;
        for (Permission cur : permissions) {
            if ((0 < cur.getEntity() || cur.isGroup() && 0 == cur.getEntity()) && ids.contains(cur.getEntity())) {
                // Folder permission
                int tmp = cur.getFolderPermission();
                if (tmp > fp) {
                    fp = tmp;
                }
                // Read permission
                tmp = cur.getReadPermission();
                if (tmp > rp) {
                    rp = tmp;
                }
                // Write permission
                tmp = cur.getWritePermission();
                if (tmp > wp) {
                    wp = tmp;
                }
                // Delete permission
                tmp = cur.getDeletePermission();
                if (tmp > dp) {
                    dp = tmp;
                }
                // Admin flag
                if (!admin) {
                    admin = cur.isAdmin();
                }

            }
        }
        if (admin) {
            p.setAdmin(admin);
        }
        p.setAllPermissions(fp, rp, wp, dp);

        return p;
    }

    private static TIntSet getEntityIdsFor(final UserPermissionBits userPermissionBits) {
        int[] groups = userPermissionBits.getGroups();
        TIntSet ids = new TIntHashSet(groups.length + 1);
        ids.add(userPermissionBits.getUserId());
        ids.addAll(groups);
        return ids;
    }

}
