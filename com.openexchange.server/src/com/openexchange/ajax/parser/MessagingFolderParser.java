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

package com.openexchange.ajax.parser;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.Folder;
import com.openexchange.ajax.fields.DataFields;
import com.openexchange.ajax.fields.FolderChildFields;
import com.openexchange.ajax.fields.FolderFields;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.BasicPermission;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.messaging.MessagingFolderIdentifier;
import com.openexchange.folderstorage.messaging.contentType.MessagingContentType;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.messaging.DefaultMessagingFolder;
import com.openexchange.messaging.DefaultMessagingPermission;
import com.openexchange.messaging.MessagingExceptionCodes;
import com.openexchange.messaging.MessagingPermission;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.session.Session;

/**
 * {@link MessagingFolderParser}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since Open-Xchange v6.16
 */
public final class MessagingFolderParser {

    /**
     * Initializes a new {@link MessagingFolderParser}.
     */
    private MessagingFolderParser() {
        super();
    }

    /**
     * A folder used for parsing.
     */
    public static final class ParsedMessagingFolder extends com.openexchange.folderstorage.AbstractFolder {

        private static final long serialVersionUID = -2941835670844893577L;

        /**
         * Initializes a new {@link ParsedMessagingFolder}.
         */
        public ParsedMessagingFolder() {
            super();
        }

        @Override
        public boolean isGlobalID() {
            return false;
        }

        /**
         * Parses from given JSON object.
         *
         * @param folderJsonObject The JSON object containing folder data
         * @throws OXException If parsing folder fails
         */
        public void parse(final JSONObject folderJsonObject) throws OXException {
            try {

                if (folderJsonObject.hasAndNotNull(DataFields.ID)) {
                    setID(folderJsonObject.getString(DataFields.ID));
                }

                if (folderJsonObject.hasAndNotNull(FolderChildFields.FOLDER_ID)) {
                    setParentID(folderJsonObject.getString(FolderChildFields.FOLDER_ID));
                }

                if (folderJsonObject.hasAndNotNull(FolderFields.TITLE)) {
                    setName(folderJsonObject.getString(FolderFields.TITLE));
                }

                setContentType(MessagingContentType.getInstance());

                if (folderJsonObject.hasAndNotNull(FolderFields.SUBSCRIBED)) {
                    try {
                        setSubscribed(folderJsonObject.getInt(FolderFields.SUBSCRIBED) > 0);
                    } catch (JSONException e) {
                        /*
                         * Not an integer value
                         */
                        setSubscribed(folderJsonObject.getBoolean(FolderFields.SUBSCRIBED));
                    }
                } else {
                    // If not present consider as subscribed
                    setSubscribed(true);
                }

                if (folderJsonObject.hasAndNotNull(FolderFields.PERMISSIONS)) {
                    final JSONArray jsonArr = folderJsonObject.getJSONArray(FolderFields.PERMISSIONS);
                    final Permission[] permissions = parsePermission(jsonArr);
                    setPermissions(permissions);
                }

            } catch (JSONException e) {
                throw MessagingExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            }
        }

        /**
         * Parses permissions from given JSON array.
         *
         * @param permissionsAsJSON The JSON array containing permissions data
         * @return The parsed permissions
         * @throws OXException If parsing permissions fails
         */
        public static Permission[] parsePermission(final JSONArray permissionsAsJSON) throws OXException {
            try {
                final int numberOfPermissions = permissionsAsJSON.length();
                final Permission[] perms = new Permission[numberOfPermissions];
                for (int i = 0; i < numberOfPermissions; i++) {
                    final JSONObject elem = permissionsAsJSON.getJSONObject(i);

                    if (!elem.hasAndNotNull(FolderFields.ENTITY)) {
                        throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.ENTITY);
                    }
                    final int entity = elem.getInt(FolderFields.ENTITY);

                    final Permission oclPerm = new BasicPermission();
                    oclPerm.setEntity(entity);
                    if (!elem.has(FolderFields.BITS)) {
                        throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.BITS);
                    }
                    final int[] permissionBits = parsePermissionBits(elem.getInt(FolderFields.BITS));
                    oclPerm.setFolderPermission(permissionBits[0]);
                    oclPerm.setReadPermission(permissionBits[1]);
                    oclPerm.setWritePermission(permissionBits[2]);
                    oclPerm.setDeletePermission(permissionBits[3]);

                    oclPerm.setAdmin(permissionBits[4] > 0 ? true : false);

                    if (!elem.has(FolderFields.GROUP)) {
                        throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.GROUP);
                    }
                    oclPerm.setGroup(elem.getBoolean(FolderFields.GROUP));

                    perms[i] = oclPerm;
                }
                return perms;
            } catch (JSONException e) {
                throw MessagingExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            }
        }

    }

    /**
     * Parses given instance of {@link JSONObject} to given instance of {@link DefaultMessagingFolder}.
     *
     * @param jsonObj The JSON object (source)
     * @param messagingFolder The messaging folder (target), which should be empty
     * @param session The session
     * @throws OXException If parsing fails
     */
    public static void parse(final JSONObject jsonObj, final DefaultMessagingFolder messagingFolder, final Session session) throws OXException {
        try {
            if (jsonObj.has(FolderFields.TITLE)) {
                messagingFolder.setName(jsonObj.getString(FolderFields.TITLE));
            }
            if (jsonObj.has(FolderChildFields.FOLDER_ID)) {
                try {
                    final MessagingFolderIdentifier mfi = new MessagingFolderIdentifier(jsonObj.getString(FolderChildFields.FOLDER_ID));
                    messagingFolder.setParentId(mfi.getFullname());
                } catch (OXException e) {
                    throw e;
                }
            }
            if (jsonObj.has(FolderFields.MODULE) && !jsonObj.getString(FolderFields.MODULE).equalsIgnoreCase(AJAXServlet.MODULE_MESSAGING)) {
                throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.MODULE);
            }
            if (jsonObj.hasAndNotNull(FolderFields.SUBSCRIBED)) {
                try {
                    messagingFolder.setSubscribed(jsonObj.getInt(FolderFields.SUBSCRIBED) > 0);
                } catch (JSONException e) {
                    /*
                     * Not an integer value
                     */
                    messagingFolder.setSubscribed(jsonObj.getBoolean(FolderFields.SUBSCRIBED));
                }
            }
            if (jsonObj.hasAndNotNull(FolderFields.PERMISSIONS)) {
                final JSONArray jsonArr = jsonObj.getJSONArray(FolderFields.PERMISSIONS);
                final int len = jsonArr.length();
                if (len > 0) {
                    final List<MessagingPermission> mailPerms = new ArrayList<MessagingPermission>(len);
                    final UserStorage us = UserStorage.getInstance();
                    for (int i = 0; i < len; i++) {
                        final JSONObject elem = jsonArr.getJSONObject(i);
                        if (!elem.has(FolderFields.ENTITY)) {
                            throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.ENTITY);
                        }
                        int entity;
                        try {
                            entity = elem.getInt(FolderFields.ENTITY);
                        } catch (JSONException e) {
                            final String entityStr = elem.getString(FolderFields.ENTITY);
                            try {
                                entity = us.getUserId(entityStr, ContextStorage.getStorageContext(session.getContextId()));
                            } catch (OXException e1) {
                                throw e1;
                            }
                        }
                        final MessagingPermission dmp = DefaultMessagingPermission.newInstance();
                        dmp.setEntity(entity);
                        if (!elem.has(FolderFields.BITS)) {
                            throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.BITS);
                        }
                        final int[] permissionBits = parsePermissionBits(elem.getInt(FolderFields.BITS));
                        dmp.setAllPermissions(permissionBits[0], permissionBits[1], permissionBits[2], permissionBits[3]);
                        dmp.setAdmin(permissionBits[4] > 0 ? true : false);
                        if (!elem.has(FolderFields.GROUP)) {
                            throw MessagingExceptionCodes.MISSING_PARAMETER.create(FolderFields.GROUP);
                        }
                        dmp.setGroup(elem.getBoolean(FolderFields.GROUP));
                        mailPerms.add(dmp);
                    }
                    messagingFolder.setPermissions(mailPerms);
                }
            }
        } catch (JSONException e) {
            throw MessagingExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        } catch (OXException e) {
            throw e;
        }
    }

    private static final int[] mapping = { 0, 2, 4, -1, 8 };

    static int[] parsePermissionBits(final int bitsArg) {
        int bits = bitsArg;
        final int[] retval = new int[5];
        for (int i = retval.length - 1; i >= 0; i--) {
            final int shiftVal = (i * 7); // Number of bits to be shifted
            retval[i] = bits >> shiftVal;
            bits -= (retval[i] << shiftVal);
            if (retval[i] == Folder.MAX_PERMISSION) {
                retval[i] = OCLPermission.ADMIN_PERMISSION;
            } else if (i < (retval.length - 1)) {
                retval[i] = mapping[retval[i]];
            } else {
                retval[i] = retval[i];
            }
        }
        return retval;
    }

}
