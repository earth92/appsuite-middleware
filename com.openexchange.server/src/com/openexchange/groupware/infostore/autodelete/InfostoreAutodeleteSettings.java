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

package com.openexchange.groupware.infostore.autodelete;

import org.slf4j.Logger;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.java.Strings;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;

/**
 * {@link InfostoreAutodeleteSettings}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.1
 */
public class InfostoreAutodeleteSettings {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(InfostoreAutodeleteSettings.class);
    }

    /** The property name for the flag whether a user may change/edit the auto-delete settings */
    private static final String PROPERTY_EDITABLE_AUTODELETE_SETTINGS = "com.openexchange.infostore.autodelete.editable";

    /** The property name for the default value of max. number of allowed versions */
    private static final String PROPERTY_DEFAULT_RETENTIONS_DAYS = "com.openexchange.infostore.autodelete.default.retentionDays";

    /** The property name for the default value of versions' retention days */
    private static final String PROPERTY_DEFAULT_MAX_VERSIONS = "com.openexchange.infostore.autodelete.default.maxVersions";


    /** The attribute name for max. number of allowed versions */
    private static final String ATTRIBUTE_MAX_VERSIONS = "com.openexchange.infostore.autodelete.maxVersions";

    /** The attribute name for versions' retention days */
    private static final String ATTRIBUTE_RETENTION_DAYS = "com.openexchange.infostore.autodelete.retentionDays";


    /** The <code>"autodelete_file_versions"</code> capability identifier */
    private static final String CAPABILITY_AUTODELETE_FILE_VERSIONS = "autodelete_file_versions";

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Initializes a new {@link InfostoreAutodeleteSettings}.
     */
    private InfostoreAutodeleteSettings() {
        super();
    }

    /**
     * Checks if session-associated user holds the capability to perform auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: The {@link UserPermissionBits#INFOSTORE "infostore" permission} is also required to finally enable the auto-delete
     * features.
     * </div>
     *
     * @param session The session providing user information
     * @return <code>true</code> if capability is granted; otherwise <code>false</code>
     * @throws OXException If capability cannot be checked
     */
    public static boolean hasAutodeleteCapability(Session session) throws OXException {
        CapabilityService capabilityService = ServerServiceRegistry.getInstance().getService(CapabilityService.class);
        return (null != capabilityService && capabilityService.getCapabilities(session).contains(CAPABILITY_AUTODELETE_FILE_VERSIONS));
    }

    /**
     * Checks if session-associated user holds the capability to perform auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: The {@link UserPermissionBits#INFOSTORE "infostore" permission} is also required to finally enable the auto-delete
     * features.
     * </div>
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if capability is granted; otherwise <code>false</code>
     * @throws OXException If capability cannot be checked
     */
    public static boolean hasAutodeleteCapability(int userId, int contextId) throws OXException {
        CapabilityService capabilityService = ServerServiceRegistry.getInstance().getService(CapabilityService.class);
        return (null != capabilityService && capabilityService.getCapabilities(userId, contextId).contains(CAPABILITY_AUTODELETE_FILE_VERSIONS));
    }

    /**
     * Checks if session-associated user may change/edit the auto-delete settings.
     *
     * @param session The session providing user data
     * @return <code>true</code> if user may change/edit the auto-delete settings; otherwise <code>false</code>
     * @throws OXException If testing the flag fails
     */
    public static boolean mayChangeAutodeleteSettings(Session session) throws OXException {
        return mayChangeAutodeleteSettings(session.getUserId(), session.getContextId());
    }

    /**
     * Checks if given user may change/edit the auto-delete settings.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if user may change/edit the auto-delete settings; otherwise <code>false</code>
     * @throws OXException If testing the flag fails
     */
    public static boolean mayChangeAutodeleteSettings(int userId, int contextId) throws OXException {
        ConfigViewFactory configViewFactory = ServerServiceRegistry.getInstance().getService(ConfigViewFactory.class);
        if (null == configViewFactory) {
            // Disabled by default
            return false;
        }

        ConfigView configView = configViewFactory.getView(userId, contextId);
        return ConfigViews.getDefinedBoolPropertyFrom(PROPERTY_EDITABLE_AUTODELETE_SETTINGS, true, configView);
    }

    /**
     * Gets the number of retention days to consider for auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: Does not check for required permissions/capabilities
     * </div>
     *
     * @param session The session to query for
     * @return The number of retention days or <code>0</code> (zero) if disabled
     * @throws OXException If number of retention days cannot be returned
     * @see #hasAutodeleteCapability(Session)
     */
    public static int getNumberOfRetentionDays(Session session) throws OXException {
        return getInt(ATTRIBUTE_RETENTION_DAYS, PROPERTY_DEFAULT_RETENTIONS_DAYS, 0, session.getUserId(), session.getContextId(), session);
    }

    /**
     * Gets the number of retention days to consider for auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: Does not check for required permissions/capabilities
     * </div>
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The number of retention days or <code>0</code> (zero) if disabled
     * @throws OXException If number of retention days cannot be returned
     * @see #hasAutodeleteCapability(Session)
     */
    public static int getNumberOfRetentionDays(int userId, int contextId) throws OXException {
        return getInt(ATTRIBUTE_RETENTION_DAYS, PROPERTY_DEFAULT_RETENTIONS_DAYS, 0, userId, contextId, null);
    }

    /**
     * Sets the number of retention days to consider for auto-delete.
     *
     * @param retentionDays The number of retention days to set
     * @param session The session to set for
     * @throws OXException If number of retention days cannot be set
     * @see #mayChangeAutodeleteSettings(Session)
     */
    public static void setNumberOfRetentionDays(int retentionDays, Session session) throws OXException {
        setInt(ATTRIBUTE_RETENTION_DAYS, retentionDays, session.getUserId(), session.getContextId(), session);
    }

    /**
     * Gets the max. number of file versions to consider for auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: Does not check for required permissions/capabilities
     * </div>
     *
     * @param session The session to query for
     * @return The max. number of file versions or <code>0</code> (zero) if disabled
     * @throws OXException If max. number of file versions cannot be returned
     * @see #hasAutodeleteCapability(Session)
     */
    public static int getMaxNumberOfFileVersions(Session session) throws OXException {
        return getInt(ATTRIBUTE_MAX_VERSIONS, PROPERTY_DEFAULT_MAX_VERSIONS, 0, session.getUserId(), session.getContextId(), session);
    }

    /**
     * Gets the max. number of file versions to consider for auto-delete.
     * <p>
     * <div style="margin-left: 0.1in; margin-right: 0.5in; margin-bottom: 0.1in; background-color:#FFDDDD;">
     * <b>Note</b>: Does not check for required permissions/capabilities
     * </div>
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return The max. number of file versions or <code>0</code> (zero) if disabled
     * @throws OXException If max. number of file versions cannot be returned
     * @see #hasAutodeleteCapability(Session)
     */
    public static int getMaxNumberOfFileVersions(int userId, int contextId) throws OXException {
        return getInt(ATTRIBUTE_MAX_VERSIONS, PROPERTY_DEFAULT_MAX_VERSIONS, 0, userId, contextId, null);
    }

    /**
     * Sets the max. number of file versions to consider for auto-delete.
     *
     * @param maxVersions The max. number of file versions to set
     * @param session The session to set for
     * @throws OXException If max. number of file versions cannot be set
     * @see #mayChangeAutodeleteSettings(Session)
     */
    public static void setMaxNumberOfFileVersions(int maxVersions, Session session) throws OXException {
        setInt(ATTRIBUTE_MAX_VERSIONS, maxVersions, session.getUserId(), session.getContextId(), session);
    }

    private static int getInt(String attrName, String propNameForDefault, int defaultValue, int userId, int contextId, Session optSession) throws OXException {
        // Get user
        User user = getUserBySession(userId, contextId, optSession);

        // Grab attribute by specified attribute name
        String attr = user.getAttributes().get(attrName);
        if (Strings.isEmpty(attr)) {
            // No such attribute. Determine value by configuration
            return getIntByConfig(propNameForDefault, defaultValue, userId, contextId);
        }

        try {
            return Integer.parseUnsignedInt(attr.trim());
        } catch (NumberFormatException e) {
            // Invalid attribute value. Determine value by configuration
            LoggerHolder.LOG.warn("Non-numeric value contained in user attribute {}", attrName, e);
            return getIntByConfig(propNameForDefault, defaultValue, userId, contextId);
        }
    }

    private static int getIntByConfig(String propNameForDefault, int defaultValue, int userId, int contextId) throws OXException {
        ConfigViewFactory configViewFactory = ServerServiceRegistry.getInstance().getService(ConfigViewFactory.class);
        if (null == configViewFactory) {
            // Disabled by default
            return defaultValue;
        }

        ConfigView configView = configViewFactory.getView(userId, contextId);
        return ConfigViews.getDefinedIntPropertyFrom(propNameForDefault, defaultValue, configView);
    }

    private static void setInt(String attrName, int maxVersions, int userId, int contextId, Session optSession) throws OXException {
        UserStorage.getInstance().setAttribute(attrName, Integer.toString(maxVersions), userId, getContextBySession(contextId, optSession));
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private static User getUserBySession(int userId, int contextId, Session optSession) throws OXException {
        return optSession instanceof ServerSession ? ((ServerSession) optSession).getUser() : UserStorage.getInstance().getUser(userId, contextId);
    }

    private static Context getContextBySession(int contextId, Session optSession) throws OXException {
        return optSession instanceof ServerSession ? ((ServerSession) optSession).getContext() : ContextStorage.getInstance().getContext(contextId);
    }

}
