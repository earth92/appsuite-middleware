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

package com.openexchange.chronos.provider.schedjoules;

import static com.openexchange.chronos.provider.CalendarFolderProperty.COLOR;
import static com.openexchange.chronos.provider.CalendarFolderProperty.COLOR_LITERAL;
import static com.openexchange.chronos.provider.CalendarFolderProperty.USED_FOR_SYNC_LITERAL;
import static com.openexchange.chronos.provider.CalendarFolderProperty.optPropertyValue;
import static com.openexchange.chronos.provider.basic.CommonCalendarConfigurationFields.NAME;
import static com.openexchange.chronos.provider.schedjoules.SchedJoulesFields.ITEM_ID;
import static com.openexchange.chronos.provider.schedjoules.SchedJoulesFields.LOCALE;
import static com.openexchange.chronos.provider.schedjoules.SchedJoulesFields.REFRESH_INTERVAL;
import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.chronos.ExtendedProperties;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarCapability;
import com.openexchange.chronos.provider.CalendarProviders;
import com.openexchange.chronos.provider.UsedForSync;
import com.openexchange.chronos.provider.basic.BasicCalendarAccess;
import com.openexchange.chronos.provider.basic.CalendarSettings;
import com.openexchange.chronos.provider.basic.CommonCalendarConfigurationFields;
import com.openexchange.chronos.provider.caching.CachingCalendarUtils;
import com.openexchange.chronos.provider.caching.basic.BasicCachingCalendarAccess;
import com.openexchange.chronos.provider.caching.basic.BasicCachingCalendarProvider;
import com.openexchange.chronos.provider.schedjoules.exception.SchedJoulesProviderExceptionCodes;
import com.openexchange.chronos.provider.schedjoules.osgi.Services;
import com.openexchange.chronos.schedjoules.SchedJoulesService;
import com.openexchange.chronos.schedjoules.exception.SchedJoulesAPIExceptionCodes;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;

/**
 * {@link BasicSchedJoulesCalendarProvider}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class BasicSchedJoulesCalendarProvider extends BasicCachingCalendarProvider {

    public static final String PROVIDER_ID = CalendarProviders.ID_SCHEDJOULES;
    private static final String DISPLAY_NAME = "SchedJoules";

    /**
     * The minumum value for the refreshInterval in minutes (1 day)
     */
    private static final int MINIMUM_REFRESH_INTERVAL = 1440;

    @Override
    public EnumSet<CalendarCapability> getCapabilities() {
        return CalendarCapability.getCapabilities(BasicSchedJoulesCalendarAccess.class);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName(Locale locale) {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable(Session session) {
        SchedJoulesService schedJoulesService = Services.getService(SchedJoulesService.class);
        return null != schedJoulesService && schedJoulesService.isAvailable(session.getContextId());
    }

    @Override
    public BasicCalendarAccess connect(Session session, CalendarAccount account, CalendarParameters parameters) throws OXException {
        return new BasicSchedJoulesCalendarAccess(session, account, parameters);
    }

    @Override
    protected void onAccountCreatedOpt(Session session, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    protected void onAccountUpdatedOpt(Session session, CalendarAccount account, CalendarParameters parameters) throws OXException {
        BasicCachingCalendarAccess connect = (BasicCachingCalendarAccess) connect(session, account, parameters);
        connect.updateDefaultAlarms();
    }

    @Override
    protected void onAccountDeletedOpt(Session session, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    protected void onAccountDeletedOpt(Context context, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    public CalendarSettings probe(Session session, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * check config & fetch calendar metadata for referenced item
         */
        JSONObject userConfig = settings.getConfig();
        if (null == userConfig) {
            throw SchedJoulesProviderExceptionCodes.MISSING_ITEM_ID_FROM_CONFIG.create(I(-1), I(session.getUserId()), I(session.getContextId()));
        }
        String locale = getLocale(session, userConfig);
        int itemId = getItemId(session, userConfig);
        long refreshInterval = getRefreshInterval(session, userConfig);

        JSONObject calendarMetadata = fetchItem(session.getContextId(), itemId, locale);
        String color = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL, String.class);
        String name = settings.containsName() && null != settings.getName() ? settings.getName() : calendarMetadata.optString(NAME, "Calendar");
        /*
         * prepare & return proposed settings, taking over client-supplied values if applicable
         */
        CalendarSettings proposedSettings = new CalendarSettings();
        JSONObject proposedConfig = new JSONObject();
        ExtendedProperties proposedExtendedProperties = new ExtendedProperties();
        proposedConfig.putSafe(ITEM_ID, I(itemId));
        proposedConfig.putSafe(LOCALE, locale);
        proposedConfig.putSafe(REFRESH_INTERVAL, L(refreshInterval));
        if (null != color) {
            proposedExtendedProperties.add(COLOR(color, false));
        }
        proposedSettings.setConfig(proposedConfig);
        proposedSettings.setExtendedProperties(proposedExtendedProperties);
        proposedSettings.setName(name);
        proposedSettings.setSubscribed(true);
        return proposedSettings;
    }

    @Override
    protected JSONObject configureAccountOpt(Session session, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * initialize & check user configuration for new account
         */
        JSONObject userConfig = settings.getConfig();

        String locale = getLocale(session, userConfig);
        userConfig.putSafe(SchedJoulesFields.LOCALE, locale);
        userConfig.putSafe(SchedJoulesFields.REFRESH_INTERVAL, L(getRefreshInterval(session, userConfig)));

        JSONObject item = fetchItem(session.getContextId(), getItemId(session, userConfig), locale);
        /*
         * prepare & return internal configuration for new account, taking over client-supplied values if set
         */
        JSONObject internalConfig = new JSONObject();
        Object colorValue = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL);
        if (null != colorValue && String.class.isInstance(colorValue)) {
            internalConfig.putSafe(CommonCalendarConfigurationFields.COLOR, colorValue);
        }
        Optional<UsedForSync> optUsedForSync = settings.getUsedForSync();
        if (optUsedForSync.isPresent() && CachingCalendarUtils.canBeUsedForSync(PROVIDER_ID, session)) {
            internalConfig.putSafe(USED_FOR_SYNC_LITERAL, B(optUsedForSync.get().isUsedForSync()));
        }
        try {
            if (Strings.isNotEmpty(settings.getName())) {
                internalConfig.putSafe(CommonCalendarConfigurationFields.NAME, settings.getName());
            } else {
                internalConfig.putSafe(CommonCalendarConfigurationFields.NAME, item.getString(CommonCalendarConfigurationFields.NAME));
            }

            internalConfig.putSafe(SchedJoulesFields.URL, item.getString(SchedJoulesFields.URL));
            internalConfig.put(SchedJoulesFields.USER_KEY, generateUserKey(session));

        } catch (JSONException e) {
            throw SchedJoulesProviderExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }

        return internalConfig;
    }

    @Override
    protected JSONObject reconfigureAccountOpt(Session session, CalendarAccount account, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        JSONObject internalConfig = null != account.getInternalConfiguration() ? new JSONObject(account.getInternalConfiguration()) : new JSONObject();
        boolean changed = applyLocaleChange(session, account, settings, internalConfig);
        // Check & apply changes to extended properties
        Object colorValue = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL);
        if (null != colorValue && String.class.isInstance(colorValue) && false == colorValue.equals(internalConfig.opt(CommonCalendarConfigurationFields.COLOR))) {
            internalConfig.putSafe(CommonCalendarConfigurationFields.COLOR, colorValue);
            changed = true;
        }
        if (Strings.isNotEmpty(settings.getName()) && false == settings.getName().equals(internalConfig.opt(CommonCalendarConfigurationFields.NAME))) {
            internalConfig.putSafe(CommonCalendarConfigurationFields.NAME, settings.getName());
            changed = true;
        }
        Optional<UsedForSync> optUsedForSync = settings.getUsedForSync();
        if (optUsedForSync.isPresent()) {
            boolean usedForSync = optUsedForSync.get().isUsedForSync();
            if (!internalConfig.has(USED_FOR_SYNC_LITERAL) || usedForSync != internalConfig.optBoolean(USED_FOR_SYNC_LITERAL)) {
                if (usedForSync && false == CachingCalendarUtils.canBeUsedForSync(PROVIDER_ID, session)) {
                    throw CalendarExceptionCodes.INVALID_CONFIGURATION.create(USED_FOR_SYNC_LITERAL);
                }
                internalConfig.putSafe(USED_FOR_SYNC_LITERAL, B(usedForSync));
                changed = true;
            }
        }
        return changed ? internalConfig : null;
    }

    @Override
    public boolean triggerCacheInvalidation(Session session, JSONObject originUserConfiguration, JSONObject newUserConfiguration) {
        return false;
    }

    /**
     * Checks if the locale changed and if so applies that change.
     *
     * @param session The groupware {@link Session}
     * @param account The {@link CalendarAccount}
     * @param settings The {@link CalendarSettings}
     * @param internalConfig The internal configuration
     * @return <code>true</code> if the locale was changed and the change was applied successfully, <code>false</code> otherwise
     * @throws OXException if the internal configuration contains a malformed URL, or if any JSON error is occurred
     */
    private boolean applyLocaleChange(Session session, CalendarAccount account, CalendarSettings settings, JSONObject internalConfig) throws OXException {
        JSONObject userConfig = settings.getConfig();
        if (userConfig == null) {
            return false;
        }
        getRefreshInterval(session, userConfig);
        String locale = getLocale(session, userConfig);
        try {
            String url = internalConfig.optString(SchedJoulesFields.URL);
            URL u = new URL(url);
            String path = u.getQuery();
            int startIndex = path.indexOf("l=");
            int endIndex = path.indexOf('&', startIndex);
            String l = path.substring(startIndex + 2, endIndex);
            if (false == l.equals(locale)) {
                JSONObject item = fetchItem(session.getContextId(), getItemId(session, account, userConfig), locale);
                internalConfig.putSafe(SchedJoulesFields.URL, item.getString(SchedJoulesFields.URL));
                internalConfig.putSafe(CommonCalendarConfigurationFields.NAME, item.getString(CommonCalendarConfigurationFields.NAME));
                return true;
            }
        } catch (MalformedURLException e) {
            throw SchedJoulesProviderExceptionCodes.INVALID_URL.create(e, I(account.getAccountId()));
        } catch (JSONException e) {
            throw SchedJoulesProviderExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
        return false;
    }

    /**
     * Get the optional locale attribute from the specified user configuration. If not present,
     * the locale is extracted from the specified {@link Session}.
     *
     * @param session The groupware {@link Session}
     * @param userConfig The {@link JSONObject} containing the user configuration
     * @return The value of the {@link SchedJoulesFields#LOCALE} attribute if present, otherwise the locale from the specified {@link Session}
     * @throws OXException if the context of the specified {@link Session} cannot be resolved
     */
    private String getLocale(Session session, JSONObject userConfig) throws OXException {
        return userConfig.optString(SchedJoulesFields.LOCALE, ServerSessionAdapter.valueOf(session).getUser().getLocale().getLanguage());
    }

    /**
     * Retrieves the item identifier form the specified {@link JSONObject}. If not present it tries to retrieve it
     * from the already existing user configuration of the specified {@link CalendarAccount}
     *
     * @param session The groupware {@link Session}
     * @param account The {@link CalendarAccount}
     * @param userConfig The user configuration
     * @return The item identifier
     * @throws OXException if the item identifier is missing from the user configuration
     */
    private int getItemId(Session session, CalendarAccount account, JSONObject userConfig) throws OXException {
        int itemId = userConfig.optInt(SchedJoulesFields.ITEM_ID, 0);
        if (0 == itemId) {
            return getItemId(session, account.getUserConfiguration());
        }
        return itemId;
    }

    /**
     * Retrieves the item identifier form the specified {@link JSONObject}
     *
     * @param session The groupware {@link Session}
     * @param userConfig The user configuration
     * @return the item identifier
     * @throws OXException if the item identifier is missing from the user configuration
     */
    private int getItemId(Session session, JSONObject userConfig) throws OXException {
        int itemId = userConfig.optInt(SchedJoulesFields.ITEM_ID, 0);
        if (0 == itemId) {
            throw SchedJoulesProviderExceptionCodes.MISSING_ITEM_ID_FROM_CONFIG.create(I(-1), I(session.getUserId()), I(session.getContextId()));
        }
        return itemId;
    }

    /**
     * Retrieves the refresh interval from the specified user configuration
     *
     * @param session The groupware {@link Session}
     * @param userConfig The user configuration
     * @return The value of the refresh interval
     * @throws OXException if the refresh interval is less than minimum allowed value
     */
    private long getRefreshInterval(Session session, JSONObject userConfig) throws OXException {
        long refreshInterval = userConfig.optLong(SchedJoulesFields.REFRESH_INTERVAL, MINIMUM_REFRESH_INTERVAL);
        if (MINIMUM_REFRESH_INTERVAL > refreshInterval) {
            throw SchedJoulesProviderExceptionCodes.INVALID_REFRESH_MINIMUM_INTERVAL.create(I(-1), I(session.getUserId()), I(session.getContextId()));
        }
        return refreshInterval;
    }

    /**
     * Fetches the calendar's metadata with the specified item and the specified locale from the SchedJoules server
     *
     * @param itemId The item identifier
     * @param locale The optional locale
     * @return The calendar's metadata as JSONObject
     * @throws OXException if the calendar is not found, or any other error is occurred
     */
    private JSONObject fetchItem(int contextId, int itemId, String locale) throws OXException {
        try {
            SchedJoulesService schedJoulesService = Services.getService(SchedJoulesService.class);
            JSONValue jsonValue = schedJoulesService.getPage(contextId, itemId, locale, Collections.emptySet()).getData();
            if (!jsonValue.isObject()) {
                throw SchedJoulesProviderExceptionCodes.PAGE_DOES_NOT_DENOTE_TO_JSON.create(I(itemId));
            }

            JSONObject page = jsonValue.toObject();
            if (!page.hasAndNotNull(SchedJoulesFields.URL)) {
                throw SchedJoulesProviderExceptionCodes.NO_CALENDAR.create(I(itemId));
            }
            return page;
        } catch (OXException e) {
            if (SchedJoulesAPIExceptionCodes.PAGE_NOT_FOUND.equals(e)) {
                throw SchedJoulesProviderExceptionCodes.CALENDAR_DOES_NOT_EXIST.create(e, I(itemId));
            }
            throw e;
        }
    }

    /**
     * Generates a user key from the user's primary e-mail address
     *
     * @param session The session to retrieve the user information
     * @return The user key
     */
    private String generateUserKey(Session session) throws OXException {
        ServerSession serverSession = ServerSessionAdapter.valueOf(session);
        User user = serverSession.getUser();
        return DigestUtils.sha256Hex(user.getMail());
    }
}
