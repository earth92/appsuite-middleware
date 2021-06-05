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

package com.openexchange.folder.json.writer;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.json.ImmutableJSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.customizer.folder.AdditionalFolderField;
import com.openexchange.ajax.customizer.folder.AdditionalFolderFieldList;
import com.openexchange.ajax.customizer.folder.BulkFolderField;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.tools.JSONCoercion;
import com.openexchange.exception.OXException;
import com.openexchange.folder.json.FolderField;
import com.openexchange.folder.json.FolderFieldRegistry;
import com.openexchange.folder.json.services.ServiceRegistry;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.FolderPath;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.folderstorage.FolderProperty;
import com.openexchange.folderstorage.FolderResponse;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.FolderServiceDecorator;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Permissions;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.UsedForSync;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.folderstorage.database.contentType.InfostoreContentType;
import com.openexchange.groupware.EntityInfo;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.java.Strings;
import com.openexchange.java.util.Tools;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.subscribe.SubscriptionSource;
import com.openexchange.subscribe.SubscriptionSourceDiscoveryService;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import gnu.trove.ConcurrentTIntObjectHashMap;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * {@link FolderWriter} - Write methods for folder module.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class FolderWriter {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FolderWriter.class);

    /**
     * The default locale: en_US.
     */
    public static final Locale DEFAULT_LOCALE = Locale.US;

    /**
     * Initializes a new {@link FolderWriter}.
     */
    private FolderWriter() {
        super();
    }

    private static final class OriginPathFolderFieldWriter implements FolderFieldWriter {

        private static final String USER_INFOSTORE_FOLDER_ID   = Integer.toString(FolderObject.SYSTEM_USER_INFOSTORE_FOLDER_ID);
        private static final String PUBLIC_INFOSTORE_FOLDER_ID = Integer.toString(FolderObject.SYSTEM_PUBLIC_INFOSTORE_FOLDER_ID);
        private static final String TREE_ID = "1";

        /**
         * Initializes a new {@link OriginPathFolderFieldWriter}.
         */
        OriginPathFolderFieldWriter() {
            super();
        }

        @Override
        public void writeField(JSONValuePutter jsonPutter, UserizedFolder theFolder, Map<String, Object> state, ServerSession session) throws JSONException {
            FolderPath originPath = theFolder.getOriginPath();
            if (null == originPath) {
                jsonPutter.put(jsonPutter.withKey() ? FolderField.ORIGIN.getName() : null, JSONObject.NULL);
                return;
            }

            try {
                String folderId;
                switch (originPath.getType()) {
                    case PRIVATE:
                        folderId = getInfoStoreDefaultFolder(state, session).getID();
                        break;
                    case PUBLIC:
                        folderId = PUBLIC_INFOSTORE_FOLDER_ID;
                        break;
                    case SHARED:
                        folderId = USER_INFOSTORE_FOLDER_ID;
                        break;
                    case UNDEFINED: /* fall-through */
                    default:
                        folderId = getInfoStoreDefaultFolder(state, session).getID();
                        originPath = FolderPath.EMPTY_PATH;
                }

                UserizedFolder folder = getFolderFor(folderId, state, session);
                Locale locale = session.getUser().getLocale();
                StringBuilder sb = new StringBuilder();
                sb.append(folder.getLocalizedName(locale));

                if (!originPath.isEmpty()) {
                    boolean searchInSubfolders = true;
                    for (String folderName : originPath.getPathForRestore()) {
                        boolean found = false;
                        if (searchInSubfolders) {
                            UserizedFolder[] subfolders = getSuboldersFor(folderId, state, session);
                            for (int i = 0; !found && i < subfolders.length; i++) {
                                UserizedFolder subfolder = subfolders[i];
                                if (folderName.equals(subfolder.getName())) {
                                    found = true;
                                    sb.append("/").append(subfolder.getLocalizedName(locale));
                                    folder = subfolder;
                                }
                            }
                        }

                        if (false == found) {
                            sb.append("/").append(folderName);
                            searchInSubfolders = false;
                        }
                    }
                }

                jsonPutter.put(jsonPutter.withKey() ? FolderField.ORIGIN.getName() : null, sb.toString());
            } catch (OXException e) {
                LOG.debug("Failed to determine original path", e);
                jsonPutter.put(jsonPutter.withKey() ? FolderField.ORIGIN.getName() : null, JSONObject.NULL);
            }
        }

        private UserizedFolder getInfoStoreDefaultFolder(Map<String, Object> state, ServerSession session) throws OXException {
            UserizedFolder defaultFolder = (UserizedFolder) state.get("__personal__");
            if (null == defaultFolder) {
                FolderServiceDecorator decorator = initDecorator(session);
                FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
                defaultFolder = folderService.getDefaultFolder(session.getUser(), TREE_ID, InfostoreContentType.getInstance(), session, decorator);
                state.put(defaultFolder.getID(), defaultFolder);
                state.put("__personal__", defaultFolder);
            }
            return defaultFolder;
        }

        private UserizedFolder getFolderFor(String folderId, Map<String, Object> state, ServerSession session) throws OXException {
            UserizedFolder folder = (UserizedFolder) state.get(folderId);
            if (null == folder) {
                FolderServiceDecorator decorator = initDecorator(session);
                FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
                folder = folderService.getFolder(TREE_ID, folderId, session, decorator);
                state.put(folderId, folder);
            }
            return folder;
        }

        private UserizedFolder[] getSuboldersFor(String folderId, Map<String, Object> state, ServerSession session) throws OXException {
            String key = "sub_" + folderId;
            UserizedFolder[] subfolders = (UserizedFolder[]) state.get(key);
            if (null == subfolders) {
                FolderServiceDecorator decorator = initDecorator(session);
                FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
                FolderResponse<UserizedFolder[]> subfolderResponse = folderService.getSubfolders(TREE_ID, folderId, true, session, decorator);
                subfolders = subfolderResponse.getResponse();
                state.put(key, subfolders);
            }
            return subfolders;
        }

        /**
         * Creates and initializes a folder service decorator ready to use with calls to the underlying folder service.
         *
         * @return A new folder service decorator
         */
        private FolderServiceDecorator initDecorator(ServerSession session) {
            FolderServiceDecorator decorator = new FolderServiceDecorator();
            Object connection = session.getParameter(Connection.class.getName());
            if (null != connection) {
                decorator.put(Connection.class.getName(), connection);
            }
            decorator.put("altNames", Boolean.TRUE.toString());
            decorator.setLocale(session.getUser().getLocale());
            return decorator;
        }
    }

    /**
     * {@link AdditionalFolderFieldWriter}
     *
     * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
     */
    private static final class AdditionalFolderFieldWriter implements FolderFieldWriter {

        private final AdditionalFolderField aff;
        private final AJAXRequestData requestData;

        /**
         * Initializes a new {@link AdditionalFolderFieldWriter}.
         *
         * @param requestData The underlying request data
         * @param aff The additional folder field
         */
        protected AdditionalFolderFieldWriter(AJAXRequestData requestData, final AdditionalFolderField aff) {
            super();
            this.requestData = requestData;
            this.aff = aff;
        }

        @Override
        public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
            Object value = aff.getValue(folder, requestData.getSession());
            jsonPutter.put(jsonPutter.withKey() ? aff.getColumnName() : null, aff.renderJSON(requestData, value));
        }
    }

    private static interface JSONValuePutter {

        void put(String key, Object value) throws JSONException;

        /**
         * Puts given name-value-pair into this data's parameters.
         * <p>
         * A <code>null</code> value removes the mapping.
         *
         * @param name The parameter name
         * @param value The parameter value
         * @throws NullPointerException If name is <code>null</code>
         */
        void putParameter(String name, Object value);

        /**
         * Gets specified parameter's value.
         *
         * @param name The parameter name
         * @return The <code>String</code> representing the single value of the parameter
         * @throws NullPointerException If name is <code>null</code>
         */
        <V> V getParameter(String name);

        /**
         * Gets the available parameters' names.
         *
         * @return The parameter names
         */
        Set<String> getParamterNames();

        /**
         * Gets the parameters reference.
         *
         * @return The parameters reference.
         */
        ConcurrentMap<String, Object> parameters();

        /**
         * Signals whether a key is required
         *
         * @return <code>true</code> if a key is required; otherwise <code>false</code>
         */
        boolean withKey();
    }

    private static abstract class AbstractJSONValuePutter implements JSONValuePutter {

        /** The parameters map */
        protected final ConcurrentMap<String, Object> parameters;

        protected AbstractJSONValuePutter() {
            super();
            parameters = new ConcurrentHashMap<String, Object>(4, 0.9f, 1);
        }

        @Override
        public Set<String> getParamterNames() {
            return new LinkedHashSet<String>(parameters.keySet());
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V> V getParameter(String name) {
            return (V) parameters.get(name);
        }

        @Override
        public void putParameter(String name, Object value) {
            if (null == value) {
                parameters.remove(name);
            } else {
                parameters.put(name, value);
            }
        }

        @Override
        public ConcurrentMap<String, Object> parameters() {
            return parameters;
        }
    }

    private static final class JSONArrayPutter extends AbstractJSONValuePutter {

        private JSONArray jsonArray;

        public JSONArrayPutter(final Map<String, Object> parameters) {
            super();
            if (null != parameters) {
                this.parameters.putAll(parameters);
            }
        }

        @Override
        public boolean withKey() {
            return false;
        }

        public void setJSONArray(final JSONArray jsonArray) {
            this.jsonArray = jsonArray;
        }

        @Override
        public void put(final String key, final Object value) throws JSONException {
            jsonArray.put(value);
        }

    }

    private static final class JSONObjectPutter extends AbstractJSONValuePutter {

        private JSONObject jsonObject;

        public JSONObjectPutter(final Map<String, Object> parameters) {
            super();
            if (null != parameters) {
                this.parameters.putAll(parameters);
            }
        }

        @Override
        public boolean withKey() {
            return true;
        }

        public JSONObjectPutter(final JSONObject jsonObject, final Map<String, Object> parameters) {
            this(parameters);
            this.jsonObject = jsonObject;
        }

        @Override
        public void put(final String key, final Object value) throws JSONException {
            if (null == value || JSONObject.NULL.equals(value)) {
                // Don't write NULL value
                return;
            }
            jsonObject.put(key, value);
        }

    }

    private static interface FolderFieldWriter {

        /**
         * Writes associated field's value to given JSON value.
         *
         * @param jsonValue The JSON value
         * @param folder The folder
         * @param state A state useful for caching
         * @param session The associated session
         * @throws JSONException If a JSON error occurs
         * @throws NecessaryValueMissingException If a necessary value is missing; such as identifier
         */
        void writeField(JSONValuePutter jsonValue, UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException;
    }

    protected static final FolderFieldWriter UNKNOWN_FIELD_FFW = new FolderFieldWriter() {

        private static final String NAME = "unknown_field";

        @Override
        public void writeField(final JSONValuePutter jsonValue, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
            jsonValue.put(jsonValue.withKey() ? NAME : null, JSONObject.NULL);
        }
    };

    private static final TIntObjectMap<FolderFieldWriter> STATIC_WRITERS_MAP;

    private static final int[] ALL_FIELDS;

    static {
        final TIntObjectMap<FolderFieldWriter> m = new TIntObjectHashMap<FolderFieldWriter>(32);
        m.put(FolderField.ID.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final String id = folder.getID();
                if (null == id) {
                    throw new NecessaryValueMissingException("Missing folder identifier.");
                }
                jsonPutter.put(jsonPutter.withKey() ? FolderField.ID.getName() : null, id);
            }
        });
        m.put(FolderField.CREATED_BY.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int createdBy = folder.getCreatedBy();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.CREATED_BY.getName() : null, -1 == createdBy ? JSONObject.NULL : Integer.valueOf(createdBy));
            }
        });
        m.put(FolderField.MODIFIED_BY.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int modifiedBy = folder.getModifiedBy();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.MODIFIED_BY.getName() : null, -1 == modifiedBy ? JSONObject.NULL : Integer.valueOf(modifiedBy));
            }
        });
        m.put(FolderField.CREATION_DATE.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Date d = folder.getCreationDate();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.CREATION_DATE.getName() : null, null == d ? JSONObject.NULL : Long.valueOf(d.getTime()));
            }
        });
        m.put(FolderField.LAST_MODIFIED.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Date d = folder.getLastModified();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.LAST_MODIFIED.getName() : null, null == d ? JSONObject.NULL : Long.valueOf(d.getTime()));
            }
        });
        m.put(FolderField.LAST_MODIFIED_UTC.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Date d = folder.getLastModifiedUTC();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.LAST_MODIFIED_UTC.getName() : null, null == d ? JSONObject.NULL : Long.valueOf(d.getTime()));
            }
        });
        m.put(FolderField.FOLDER_ID.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final String pid = folder.getParentID();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.FOLDER_ID.getName() : null, pid);
            }
        });
        m.put(FolderField.ACCOUNT_ID.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final String accountId = folder.getAccountID();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.ACCOUNT_ID.getName() : null, accountId == null ? JSONObject.NULL : accountId);
            }
        });
        m.put(FolderField.FOLDER_NAME.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Locale locale = folder.getLocale();
                if (folder.supportsAltName()) {
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.FOLDER_NAME.getName() : null, folder.getLocalizedName(locale == null ? DEFAULT_LOCALE : locale, folder.isAltNames()));
                } else {
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.FOLDER_NAME.getName() : null, folder.getLocalizedName(locale == null ? DEFAULT_LOCALE : locale));
                }
            }
        });
        m.put(FolderField.FOLDER_NAME_RAW.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                jsonPutter.put(jsonPutter.withKey() ? FolderField.FOLDER_NAME_RAW.getName() : null, folder.getName());
            }
        });
        m.put(FolderField.ORIGIN.getColumn(), new OriginPathFolderFieldWriter());
        m.put(FolderField.MODULE.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final ContentType obj = folder.getContentType();
                Object value;
                if (null == obj) {
                    value = JSONObject.NULL;
                } else {
                    int optFolderId;
                    try {
                        optFolderId = Integer.parseInt(folder.getID());
                    } catch (NumberFormatException | NullPointerException e) {
                        optFolderId = 0;
                    }
                    String module = AJAXServlet.getModuleString(obj.getModule(), optFolderId);
                    value = Strings.isEmpty(module) ? obj.toString() : module;
                }
                jsonPutter.put(jsonPutter.withKey() ? FolderField.MODULE.getName() : null, value);
            }
        });
        m.put(FolderField.TYPE.getColumn(), new FolderFieldWriter() {

            @SuppressWarnings("deprecation")
            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Type obj = folder.getType();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.TYPE.getName() : null, null == obj ? JSONObject.NULL : Integer.valueOf(obj.getType()));
            }
        });
        m.put(FolderField.SUBFOLDERS.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final String[] obj = folder.getSubfolderIDs();
                if (null == obj) {
                    LOG.warn("Got null as subfolders for folder {}. Marking this folder to hold subfolders...", folder.getID());
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.SUBFOLDERS.getName() : null, Boolean.TRUE);
                } else {
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.SUBFOLDERS.getName() : null, Boolean.valueOf(obj.length > 0));
                }
            }
        });
        m.put(FolderField.OWN_RIGHTS.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int bits = folder.getBits();
                if (bits < 0) {
                    final Permission obj = folder.getOwnPermission();
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.OWN_RIGHTS.getName() : null,
                        null == obj ? JSONObject.NULL : Integer.valueOf(Permissions.createPermissionBits(
                            obj.getFolderPermission(),
                            obj.getReadPermission(),
                            obj.getWritePermission(),
                            obj.getDeletePermission(),
                            obj.isAdmin())));
                } else {
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.OWN_RIGHTS.getName() : null, Integer.valueOf(bits));
                }
            }
        });
        m.put(FolderField.PERMISSIONS_BITS.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final JSONArray ja;
                {
                    final Permission[] obj = folder.getPermissions();
                    if (null == obj) {
                        ja = null;
                    } else {
                        ja = new JSONArray();
                        for (final Permission permission : obj) {
                            if (permission.getType() == FolderPermissionType.INHERITED) {
                                continue;
                            }
                            final JSONObject jo = new JSONObject(4);
                            jo.put(FolderField.IDENTIFIER.getName(), null != permission.getIdentifier() ? permission.getIdentifier() : String.valueOf(permission.getEntity()));
                            jo.put(FolderField.BITS.getName(), Permissions.createPermissionBits(permission));
                            if (0 < permission.getEntity() || 0 == permission.getEntity() && permission.isGroup()) {
                                jo.put(FolderField.ENTITY.getName(), permission.getEntity());
                            }
                            jo.put(FolderField.GROUP.getName(), permission.isGroup());
                            ja.put(jo);
                        }
                    }
                }
                jsonPutter.put(jsonPutter.withKey() ? FolderField.PERMISSIONS_BITS.getName() : null, null == ja ? JSONObject.NULL : ja);
            }
        });
        m.put(FolderField.SUMMARY.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final String obj = folder.getSummary();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.SUMMARY.getName() : null, null == obj ? JSONObject.NULL : obj);
            }
        });
        m.put(FolderField.STANDARD_FOLDER.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                jsonPutter.put(jsonPutter.withKey() ? FolderField.STANDARD_FOLDER.getName() : null, Boolean.valueOf(folder.isDefault()));
            }
        });
        m.put(FolderField.STANDARD_FOLDER_TYPE.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                jsonPutter.put(jsonPutter.withKey() ? FolderField.STANDARD_FOLDER_TYPE.getName() : null, Integer.valueOf(folder.getDefaultType()));
            }
        });
        m.put(FolderField.TOTAL.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int obj = folder.getTotal();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.TOTAL.getName() : null, -1 == obj ? JSONObject.NULL : Integer.valueOf(obj));
            }
        });
        m.put(FolderField.NEW.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int obj = folder.getNew();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.NEW.getName() : null, -1 == obj ? JSONObject.NULL : Integer.valueOf(obj));
            }
        });
        m.put(FolderField.UNREAD.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int obj = folder.getUnread();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.UNREAD.getName() : null, -1 == obj ? JSONObject.NULL : Integer.valueOf(obj));
            }
        });
        m.put(FolderField.DELETED.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int obj = folder.getDeleted();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.DELETED.getName() : null, -1 == obj ? JSONObject.NULL : Integer.valueOf(obj));
            }
        });
        m.put(FolderField.SUBSCRIBED.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                jsonPutter.put(jsonPutter.withKey() ? FolderField.SUBSCRIBED.getName() : null, Boolean.valueOf(folder.isSubscribed()));
            }
        });
        m.put(FolderField.SUBSCR_SUBFLDS.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                /*-
                 *
                final String[] obj = folder.getSubfolderIDs();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.SUBSCR_SUBFLDS.getName() : null, null == obj ? JSONObject.NULL : Boolean.valueOf(obj.length > 0));
                 */
                jsonPutter.put(jsonPutter.withKey() ? FolderField.SUBSCR_SUBFLDS.getName() : null, Boolean.valueOf(folder.hasSubscribedSubfolders()));
            }
        });
        m.put(FolderField.USED_FOR_SYNC.getColumn(), new FolderFieldWriter() {

            private final JSONObject defaultJUsedForSync = ImmutableJSONObject.immutableFor(
                new JSONObject(2)
                .putSafe("value", String.valueOf(UsedForSync.DEFAULT.isUsedForSync()))
                .putSafe("protected", String.valueOf(UsedForSync.DEFAULT.isProtected()))
            );

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                UsedForSync usedForSync = folder.getUsedForSync();
                if (usedForSync == null) {
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.USED_FOR_SYNC.getName() : null, defaultJUsedForSync);
                } else {
                    JSONObject jUsedForSync = new JSONObject(2);
                    jUsedForSync.put("value", String.valueOf(usedForSync.isUsedForSync()));
                    jUsedForSync.put("protected", String.valueOf(usedForSync.isProtected()));
                    jsonPutter.put(jsonPutter.withKey() ? FolderField.USED_FOR_SYNC.getName() : null, jUsedForSync);
                }
            }
        });
        // Capabilities
        m.put(FolderField.CAPABILITIES.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final int caps = folder.getCapabilities();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.CAPABILITIES.getName() : null, -1 == caps ? JSONObject.NULL : Integer.valueOf(caps));
            }
        });
        // Meta
        m.put(FolderField.META.getColumn(), new FolderFieldWriter() {
            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                // Get meta map
                Map<String, Object> map = folder.getMeta();
                jsonPutter.put(jsonPutter.withKey() ? FolderField.META.getName() : null, null == map || map.isEmpty() ? JSONObject.NULL : JSONCoercion.coerceToJSON(map));
            }

        });
        // Supported capabilities
        m.put(FolderField.SUPPORTED_CAPABILITIES.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                final Set<String> caps = getSupportedCapabilities(folder);
                jsonPutter.put(jsonPutter.withKey() ? FolderField.SUPPORTED_CAPABILITIES.getName() : null, null == caps ? JSONObject.NULL : new JSONArray(caps));
            }
        });
        m.put(FolderField.CREATED_FROM.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(JSONValuePutter jsonValue, UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                EntityInfo entityInfo = folder.getCreatedFrom();
                if (null == entityInfo && 0 < folder.getCreatedBy()) {
                    entityInfo = resolveEntityInfo(session, folder.getCreatedBy());
                }
                jsonValue.put(jsonValue.withKey() ? FolderField.CREATED_FROM.getName() : null, null == entityInfo ? JSONObject.NULL : entityInfo.toJSON());
            }
        });
        m.put(FolderField.MODIFIED_FROM.getColumn(), new FolderFieldWriter() {

            @Override
            public void writeField(JSONValuePutter jsonValue, UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
                EntityInfo entityInfo = folder.getModifiedFrom();
                if (null == entityInfo && 0 < folder.getModifiedBy()) {
                    entityInfo = resolveEntityInfo(session, folder.getModifiedBy());
                }
                jsonValue.put(jsonValue.withKey() ? FolderField.MODIFIED_FROM.getName() : null, null == entityInfo ? JSONObject.NULL : entityInfo.toJSON());
            }
        });
        STATIC_WRITERS_MAP = m;

        final FolderField[] all = FolderField.values();
        final int[] allFields = new int[all.length];
        int j = 0;
        for (int i = 0; i < allFields.length; i++) {
            final int val = all[i].getColumn();
            if (val > 0) {
                allFields[j++] = val;
            }
        }
        ALL_FIELDS = new int[j];
        System.arraycopy(allFields, 0, ALL_FIELDS, 0, j);
    }

    /**
     * Resolve entity information for given user identifier
     *
     * @param session The calling user's session
     * @param forUserId The user identifier to get the entity info for
     * @return The entity information or <code>null</code> in case it cannot be resolved
     */
    static EntityInfo resolveEntityInfo(ServerSession session, int forUserId) {
        try {
            User user = ServiceRegistry.getInstance().getService(UserService.class, true).getUser(forUserId, session.getContext());
            EntityInfo.Type type = user.isGuest() ? (user.isAnonymousGuest() ? EntityInfo.Type.ANONYMOUS : EntityInfo.Type.GUEST) : EntityInfo.Type.USER;
            return new EntityInfo(String.valueOf(user.getId()), user.getDisplayName(), null, user.getGivenName(), user.getSurname(), user.getMail(), user.getId(), null, type);
        } catch (OXException e) {
            LOG.debug("Error resolving entity information for user {} in context {}.", I(forUserId), I(session.getContextId()), e);
        }
        return new EntityInfo(String.valueOf(forUserId), null, null, null, null, null, forUserId, null, EntityInfo.Type.USER);
    }

    @SuppressWarnings("deprecation")
    static FolderObject turnIntoFolderObject(UserizedFolder folder) {
        FolderObject fo = new FolderObject();
        final int numFolderId = Tools.getUnsignedInteger(folder.getID());
        if (numFolderId < 0) {
            fo.setFullName(folder.getID());
        } else {
            fo.setObjectID(numFolderId);
        }
        fo.setFolderName(folder.getName());
        fo.setModule(folder.getContentType().getModule());
        if (null != folder.getType()) {
            fo.setType(folder.getType().getType());
        }
        fo.setCreatedBy(folder.getCreatedBy());
        fo.setPermissions(turnIntoOCLPermissions(numFolderId, folder.getPermissions()));
        return fo;
    }

    static List<FolderObject> turnIntoFolderObjects(final UserizedFolder[] folders) {
        List<FolderObject> retval = new ArrayList<FolderObject>(folders.length);
        for (UserizedFolder folder : folders) {
            retval.add(turnIntoFolderObject(folder));
        }
        return retval;
    }

    /**
     * Converts an array of permissions as used in userized folders into a list of OCL permissions as used by folder objects.
     *
     * @param folderID The folder identifier
     * @param permissions The permissions
     * @return The OXL permissions
     */
    private static List<OCLPermission> turnIntoOCLPermissions(int folderID, Permission[] permissions) {
        if (null == permissions) {
            return null;
        }
        List<OCLPermission> oclPermissions = new ArrayList<OCLPermission>(permissions.length);
        for (Permission permission : permissions) {
            OCLPermission oclPermission = new OCLPermission(permission.getEntity(), folderID);
            oclPermission.setAllPermission(permission.getFolderPermission(), permission.getReadPermission(),
                permission.getWritePermission(), permission.getDeletePermission());
            oclPermission.setFolderAdmin(permission.isAdmin());
            oclPermission.setGroupPermission(permission.isGroup());
            oclPermission.setType(permission.getType() == null ? FolderPermissionType.NORMAL : permission.getType());
            oclPermission.setPermissionLegator(permission.getPermissionLegator());
            oclPermissions.add(oclPermission);
        }
        return oclPermissions;
    }

    /**
     * Writes requested fields of given folders into a JSON array consisting of JSON arrays.
     *
     * @param requestData The underlying request data
     * @param fields The fields to write or <code>null</code> to write all
     * @param folders The folders
     * @param additionalFolderFieldList The additional folder fields to write
     * @return The JSON array carrying JSON arrays of given folders
     * @throws OXException If writing JSON array fails
     */
    public static JSONArray writeMultiple2Array(AJAXRequestData requestData, final int[] fields, final UserizedFolder[] folders, final AdditionalFolderFieldList additionalFolderFieldList) throws OXException {
        final int[] cols = null == fields ? ALL_FIELDS : fields;
        final FolderFieldWriter[] ffws = new FolderFieldWriter[cols.length];
        final TIntObjectMap<com.openexchange.folderstorage.FolderField> fieldSet = FolderFieldRegistry.getInstance().getFields();
        for (int i = 0; i < ffws.length; i++) {
            final int curCol = cols[i];
            FolderFieldWriter ffw = STATIC_WRITERS_MAP.get(curCol);
            if (null == ffw) {
                AdditionalFolderField aff = additionalFolderFieldList.opt(curCol);
                if (null != aff) {
                    aff = new BulkFolderField(aff, folders.length);
                    aff.getValues(Arrays.asList(folders), requestData.getSession());
                    ffw = new AdditionalFolderFieldWriter(requestData, aff);
                } else {
                    ffw = getPropertyByField(curCol, fieldSet);
                }
            }
            ffws[i] = ffw;
        }

        ServerSession session = requestData.getSession();
        Map<String, Object> state = new HashMap<String, Object>();
        try {
            final JSONArray jsonArray = new JSONArray(folders.length);
            final JSONArrayPutter jsonPutter = new JSONArrayPutter(null);
            for (final UserizedFolder folder : folders) {
                try {
                    final JSONArray folderArray = new JSONArray(ffws.length);
                    jsonPutter.setJSONArray(folderArray);
                    for (final FolderFieldWriter ffw : ffws) {
                        ffw.writeField(jsonPutter, folder, state, session);
                    }
                    jsonArray.put(folderArray);
                } catch (NecessaryValueMissingException e) {
                    LOG.warn(e.getMessage());
                }
            }
            return jsonArray;
        } catch (JSONException e) {
            throw FolderExceptionErrorMessage.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Writes requested fields of given folder into a JSON object.
     *
     * @param requestData The underlying request data
     * @param fields The fields to write or <code>null</code> to write all
     * @param folder The folder
     * @param additionalFolderFieldList The additional folder fields to write
     * @return The JSON object carrying requested fields of given folder
     * @throws OXException If writing JSON object fails
     */
    public static JSONObject writeSingle2Object(AJAXRequestData requestData, final int[] fields, final UserizedFolder folder, final AdditionalFolderFieldList additionalFolderFieldList) throws OXException {
        final int[] cols = null == fields ? getAllFields(additionalFolderFieldList) : fields;
        final FolderFieldWriter[] ffws = new FolderFieldWriter[cols.length];
        final TIntObjectMap<com.openexchange.folderstorage.FolderField> fieldSet = FolderFieldRegistry.getInstance().getFields();
        for (int i = 0; i < ffws.length; i++) {
            final int curCol = cols[i];
            FolderFieldWriter ffw = STATIC_WRITERS_MAP.get(curCol);
            if (null == ffw) {
                AdditionalFolderField aff = additionalFolderFieldList.opt(curCol);
                if (null != aff) {
                    ffw = new AdditionalFolderFieldWriter(requestData, aff);
                } else {
                    ffw = getPropertyByField(curCol, fieldSet);
                }
            }
            ffws[i] = ffw;
        }

        ServerSession session = requestData.getSession();
        Map<String, Object> state = new HashMap<String, Object>();
        try {
            final JSONObject jsonObject = new JSONObject(ffws.length);
            final JSONValuePutter jsonPutter = new JSONObjectPutter(jsonObject, null);
            for (final FolderFieldWriter ffw : ffws) {
                ffw.writeField(jsonPutter, folder, state, session);
            }
            return jsonObject;
        } catch (JSONException e) {
            throw FolderExceptionErrorMessage.JSON_ERROR.create(e, e.getMessage());
        } catch (NecessaryValueMissingException e) {
            throw FolderExceptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    private static int[] getAllFields(final AdditionalFolderFieldList additionalFolderFieldList) {
        final TIntList list = new TIntArrayList();
        list.add(ALL_FIELDS);
        list.add(additionalFolderFieldList.getKnownFields());
        list.addAll(FolderFieldRegistry.getInstance().getFields().keys());
        return list.toArray();
    }

    /*
     * Helper methods
     */

    private static final class PropertyFieldWriter implements FolderFieldWriter {

        private final com.openexchange.folderstorage.FolderField field;

        private final String name;

        protected PropertyFieldWriter(final com.openexchange.folderstorage.FolderField field) {
            super();
            this.field = field;
            final String name = field.getName();
            this.name = null == name ? Integer.toString(field.getField()) : name;
        }

        @Override
        public void writeField(final JSONValuePutter jsonPutter, final UserizedFolder folder, Map<String, Object> state, ServerSession session) throws JSONException {
            final FolderProperty property = folder.getProperties().get(field);
            jsonPutter.put(jsonPutter.withKey() ? name : null, field.write(property, session));
        }

    }

    private static final ConcurrentTIntObjectHashMap<PropertyFieldWriter> PROPERTY_WRITERS = new ConcurrentTIntObjectHashMap<PropertyFieldWriter>(4);

    private static FolderFieldWriter getPropertyByField(final int field, final TIntObjectMap<com.openexchange.folderstorage.FolderField> fields) {
        final com.openexchange.folderstorage.FolderField fieldNamePair = fields.get(field);
        if (null == fieldNamePair) {
            LOG.debug("Client requested an unknown field: {}", Integer.valueOf(field));
            return UNKNOWN_FIELD_FFW;
        }
        PropertyFieldWriter pw = PROPERTY_WRITERS.get(field);
        if (null == pw) {
            final PropertyFieldWriter npw = new PropertyFieldWriter(fieldNamePair);
            pw = PROPERTY_WRITERS.putIfAbsent(field, npw);
            if (null == pw) {
                pw = npw;
            }
        }
        return pw;
    }

    /**
     * Determines the supported capabilities for a userized folder to indicate to clients. This is based on the storage folder
     * capabilities, but may be restricted further based on the availability of further services.
     *
     * @param folder The folder to determine the supported capabilities for
     * @return The supported capabilities
     */
    static Set<String> getSupportedCapabilities(UserizedFolder folder) {
        Set<String> capabilities = folder.getSupportedCapabilities();
        if (null == capabilities || 0 == capabilities.size()) {
            return capabilities;
        }
        Set<String> supportedCapabilities = new HashSet<String>(capabilities.size());
        for (String capability : capabilities) {
            try {
                switch (capability) {
                    case "subscription":
                        if (supportsSubscriptions(folder)) {
                            supportedCapabilities.add(capability);
                        }
                        break;
                    case "publication":
                        // Removed with MW-1089 in version 7.10.2
                        LOG.info("Publication has been removed. Capability can't be applied");
                        break;
                    default:
                        supportedCapabilities.add(capability);
                        break;
                }
            } catch (OXException e) {
                LOG.warn("Error evaluating capability '{}' for folder {}", capability, folder.getID(), e);
            }
        }
        return supportedCapabilities;
    }

    /**
     * Gets a value indicating whether a specific folder supports subscriptions.
     *
     * @param folder The folder to check
     * @return <code>true</code> if subscriptions are supported, <code>false</code>, otherwise
     */
    private static boolean supportsSubscriptions(UserizedFolder folder) throws OXException {
        SubscriptionSourceDiscoveryService sourceDiscoveryService = ServiceRegistry.getInstance().getService(SubscriptionSourceDiscoveryService.class);
        if (null != sourceDiscoveryService) {
            sourceDiscoveryService = sourceDiscoveryService.filter(folder.getUser().getId(), folder.getContext().getContextId());
            List<SubscriptionSource> sources;
            if (null != folder.getContentType()) {
                sources = sourceDiscoveryService.getSources(folder.getContentType().getModule());
            } else {
                sources = sourceDiscoveryService.getSources();
            }
            if (null != sources && 0 < sources.size()) {
                for (SubscriptionSource source : sources) {
                    if (source.getSubscribeService().isCreateModifyEnabled()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
