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

package com.openexchange.chronos.provider.ical;

import static com.openexchange.chronos.provider.CalendarFolderProperty.COLOR;
import static com.openexchange.chronos.provider.CalendarFolderProperty.COLOR_LITERAL;
import static com.openexchange.chronos.provider.CalendarFolderProperty.DESCRIPTION;
import static com.openexchange.chronos.provider.CalendarFolderProperty.DESCRIPTION_LITERAL;
import static com.openexchange.chronos.provider.CalendarFolderProperty.optPropertyValue;
import static com.openexchange.chronos.provider.CalendarFolderProperty.USED_FOR_SYNC_LITERAL;
import static com.openexchange.chronos.provider.ical.ICalCalendarConstants.NAME;
import static com.openexchange.chronos.provider.ical.ICalCalendarConstants.PROVIDER_ID;
import static com.openexchange.chronos.provider.ical.ICalCalendarConstants.REFRESH_INTERVAL;
import static com.openexchange.chronos.provider.ical.ICalCalendarConstants.URI;
import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.L;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.dmfs.rfc5545.Duration;
import org.json.JSONObject;
import com.openexchange.auth.info.AuthType;
import com.openexchange.chronos.ExtendedProperties;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarAccountAttribute;
import com.openexchange.chronos.provider.CalendarCapability;
import com.openexchange.chronos.provider.UsedForSync;
import com.openexchange.chronos.provider.basic.BasicCalendarAccess;
import com.openexchange.chronos.provider.basic.CalendarSettings;
import com.openexchange.chronos.provider.caching.CachingCalendarUtils;
import com.openexchange.chronos.provider.caching.basic.BasicCachingCalendarAccess;
import com.openexchange.chronos.provider.caching.basic.BasicCachingCalendarProvider;
import com.openexchange.chronos.provider.ical.auth.AdvancedAuthInfo;
import com.openexchange.chronos.provider.ical.auth.ICalAuthParser;
import com.openexchange.chronos.provider.ical.conn.ICalFeedClient;
import com.openexchange.chronos.provider.ical.exception.ICalProviderExceptionCodes;
import com.openexchange.chronos.provider.ical.result.GetResponse;
import com.openexchange.chronos.provider.ical.result.GetResponseState;
import com.openexchange.chronos.provider.ical.utils.ICalProviderUtils;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Strings;
import com.openexchange.session.Session;

/**
 *
 * {@link BasicICalCalendarProvider}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since v7.10.0
 */
public class BasicICalCalendarProvider extends BasicCachingCalendarProvider {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BasicICalCalendarProvider.class);

    @Override
    public String getId() {
        return ICalCalendarConstants.PROVIDER_ID;
    }

    @Override
    public String getDisplayName(Locale locale) {
        return StringHelper.valueOf(locale).getString(ICalCalendarStrings.PROVIDER_NAME);
    }

    @Override
    public EnumSet<CalendarCapability> getCapabilities() {
        return CalendarCapability.getCapabilities(BasicICalCalendarAccess.class);
    }

    @Override
    public BasicCalendarAccess connect(Session session, CalendarAccount account, CalendarParameters parameters) throws OXException {
        return new BasicICalCalendarAccess(session, account, parameters);
    }

    @Override
    public void onAccountCreatedOpt(Session session, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    public void onAccountUpdatedOpt(Session session, CalendarAccount account, CalendarParameters parameters) throws OXException {
        // Update default alarms
        BasicCachingCalendarAccess connect = (BasicCachingCalendarAccess) connect(session, account, parameters);
        connect.updateDefaultAlarms();
    }

    @Override
    public void onAccountDeletedOpt(Session session, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    public void onAccountDeletedOpt(Context context, CalendarAccount account, CalendarParameters parameters) {
        // nothing to do
    }

    @Override
    public CalendarSettings probe(Session session, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * check feed uri
         */
        JSONObject config = settings.getConfig();
        if (null == config || false == config.hasAndNotNull(URI)) {
            throw ICalProviderExceptionCodes.MISSING_FEED_URI.create();
        }
        String uri = config.optString(URI, null);
        /*
         * attempt to read and parse feed & take over extracted metadata as needed
         */
        ICalCalendarFeedConfig feedConfig = new ICalCalendarFeedConfig.EncryptedBuilder(session, new JSONObject(settings.getConfig()), new JSONObject()).build();
        ICalFeedClient feedClient = new ICalFeedClient(session, feedConfig);
        GetResponse feedResponse = feedClient.executeRequest();
        if (feedResponse.getState() == GetResponseState.REMOVED) {
            throw ICalProviderExceptionCodes.NO_FEED.create(feedConfig.getFeedUrl());
        }

        Long refreshInterval = null;
        if (Strings.isNotEmpty(feedResponse.getRefreshInterval())) {
            try {
                Duration duration = org.dmfs.rfc5545.Duration.parse(feedResponse.getRefreshInterval());
                refreshInterval = L(TimeUnit.MILLISECONDS.toMinutes(duration.toMillis()));
            } catch (IllegalArgumentException e) {
                LOG.warn("Ignoring unparsable refresh interval \"{}\" from calendar feed \"{}\".", feedResponse.getRefreshInterval(), uri, e);
            }
        }
        //TODO: check against some minimum refresh interval?
        String color = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL, String.class);
        if (Strings.isEmpty(color)) {
            color = feedResponse.getFeedColor();
        }
        String description = optPropertyValue(settings.getExtendedProperties(), DESCRIPTION_LITERAL, String.class);
        if (Strings.isEmpty(description)) {
            description = feedResponse.getFeedDescription();
        }
        String name = settings.getName();
        if (Strings.isEmpty(name)) {
            name = Strings.isNotEmpty(feedResponse.getFeedName()) ? feedResponse.getFeedName() : "Calendar";
        }
        /*
         * prepare & return proposed settings, taking over client-supplied values if applicable
         */
        CalendarSettings proposedSettings = new CalendarSettings();
        JSONObject proposedConfig = new JSONObject();
        ExtendedProperties proposedExtendedProperties = new ExtendedProperties();
        proposedConfig.putSafe(URI, uri);
        if (null != refreshInterval) {
            proposedConfig.putSafe(REFRESH_INTERVAL, refreshInterval);
        }
        AdvancedAuthInfo authInfo = feedConfig.getAuthInfo();
        addAuthInfo(proposedConfig, authInfo);

        if (null != color) {
            proposedExtendedProperties.add(COLOR(color, false));
        }
        if (null != description) {
            proposedExtendedProperties.add(DESCRIPTION(description, false));
        }
        if (canBeUsedForSync(session) == false) {
            proposedSettings.setUsedForSync(UsedForSync.DEACTIVATED);
        } else if (settings.getUsedForSync().isPresent()) {
            proposedSettings.setUsedForSync(settings.getUsedForSync().get());
        }
        proposedSettings.setConfig(proposedConfig);
        proposedSettings.setExtendedProperties(proposedExtendedProperties);
        proposedSettings.setName(name);
        proposedSettings.setSubscribed(true);
        return proposedSettings;
    }

    /**
     * Checks if the provider can be used for sync
     *
     * @param session The users session
     */
    private boolean canBeUsedForSync(Session session) {
        return CachingCalendarUtils.canBeUsedForSync(PROVIDER_ID, session);
    }

    private void addAuthInfo(JSONObject proposedConfig, AdvancedAuthInfo authInfo) {
        if (authInfo != null && authInfo.getAuthType() == AuthType.BASIC) {
            String login = authInfo.getLogin();
            if (Strings.isNotEmpty(login)) {
                proposedConfig.putSafe(CalendarAccountAttribute.LOGIN_LITERAL.getName(), login);
            }
            String password = authInfo.getPassword();
            if (Strings.isNotEmpty(password)) {
                proposedConfig.putSafe(CalendarAccountAttribute.PASSWORD_LITERAL.getName(), password);
            }
        }
    }

    @Override
    public JSONObject configureAccountOpt(Session session, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * check & adjust passed user config as needed
         */
        JSONObject config = settings.getConfig();
        if (null == config || false == config.hasAndNotNull(URI)) {
            throw ICalProviderExceptionCodes.MISSING_FEED_URI.create();
        }
        String uri = config.optString(URI, null);
        ICalProviderUtils.verifyURI(uri);
        ICalCalendarFeedConfig iCalFeedConfig = new ICalCalendarFeedConfig.EncryptedBuilder(session, new JSONObject(config), new JSONObject()).build();
        if (AuthType.BASIC.equals(iCalFeedConfig.getAuthInfo().getAuthType())) {
            ICalAuthParser.encrypt(config, session.getPassword());
        }
        if (config.hasAndNotNull(ICalCalendarConstants.REFRESH_INTERVAL)) {
            Object opt = config.opt(ICalCalendarConstants.REFRESH_INTERVAL);

            if (opt != null && !(opt instanceof Number)) {
                throw ICalProviderExceptionCodes.BAD_PARAMETER.create(ICalCalendarConstants.REFRESH_INTERVAL, opt);
            }
        }
        /*
         * prepare & return internal config, taking over client-supplied values if applicable
         */
        JSONObject internalConfig = new JSONObject();
        internalConfig.putSafe(NAME, Strings.isNotEmpty(settings.getName()) ? settings.getName() : "Calendar");
        internalConfig.putSafe("subscribed", B(settings.isSubscribed()));
        String color = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL, String.class);
        if (Strings.isNotEmpty(color)) {
            internalConfig.putSafe("color", color);
        }
        String description = optPropertyValue(settings.getExtendedProperties(), DESCRIPTION_LITERAL, String.class);
        if (Strings.isNotEmpty(description)) {
            internalConfig.putSafe("description", description);
        }
        Optional<UsedForSync> optUsedForSync = settings.getUsedForSync();
        if (optUsedForSync.isPresent()) {
            boolean usedForSync = optUsedForSync.get().isUsedForSync();
            if (false == internalConfig.has(USED_FOR_SYNC_LITERAL) || usedForSync != internalConfig.optBoolean(USED_FOR_SYNC_LITERAL, false)) {
                if (canBeUsedForSync(session) == false) {
                    throw CalendarExceptionCodes.INVALID_CONFIGURATION.create(USED_FOR_SYNC_LITERAL);
                }
                internalConfig.putSafe(USED_FOR_SYNC_LITERAL, B(usedForSync));
            }
        }
        return internalConfig;
    }

    @Override
    public JSONObject reconfigureAccountOpt(Session session, CalendarAccount account, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * check & adjust passed user config as needed
         */
        if (settings.containsConfig()) {
            JSONObject config = settings.getConfig();
            if (null == config || false == config.hasAndNotNull(URI)) {
                throw ICalProviderExceptionCodes.MISSING_FEED_URI.create();
            }
            String uri = config.optString(URI, null);
            ICalProviderUtils.verifyURI(uri);
            if (!uri.equals(account.getUserConfiguration().optString(URI))) {
                throw ICalProviderExceptionCodes.NOT_ALLOWED_CHANGE.create("uri");
            }
            ICalCalendarFeedConfig iCalFeedConfig = new ICalCalendarFeedConfig.EncryptedBuilder(session, new JSONObject(config), new JSONObject()).build();
            if (AuthType.BASIC.equals(iCalFeedConfig.getAuthInfo().getAuthType())) {
                ICalAuthParser.encrypt(config, session.getPassword());
            }
        }
        /*
         * check & apply changes to internal config
         */
        boolean changed = false;
        JSONObject internalConfig = null != account.getInternalConfiguration() ? new JSONObject(account.getInternalConfiguration()) : new JSONObject();
        if (settings.containsExtendedProperties()) {
            String color = optPropertyValue(settings.getExtendedProperties(), COLOR_LITERAL, String.class);
            if (false == Objects.equals(color, internalConfig.optString("color", null))) {
                internalConfig.putSafe("color", color);
                changed = true;
            }
            String description = optPropertyValue(settings.getExtendedProperties(), DESCRIPTION_LITERAL, String.class);
            if (false == Objects.equals(description, internalConfig.optString("description", null))) {
                internalConfig.putSafe("description", description);
                changed = true;
            }
        }
        Optional<UsedForSync> optUsedForSync = settings.getUsedForSync();
        if(optUsedForSync.isPresent()) {
            boolean usedForSync = optUsedForSync.get().isUsedForSync();
            if (!internalConfig.has(USED_FOR_SYNC_LITERAL) || usedForSync != internalConfig.optBoolean(USED_FOR_SYNC_LITERAL)) {
                if (usedForSync && false == canBeUsedForSync(session)) {
                    throw CalendarExceptionCodes.INVALID_CONFIGURATION.create(USED_FOR_SYNC_LITERAL);
                }
                internalConfig.putSafe(USED_FOR_SYNC_LITERAL, B(usedForSync));
                changed = true;
            }
        }
        if (settings.containsName() && false == Objects.equals(settings.getName(), internalConfig.optString("name", null))) {
            internalConfig.putSafe("name", settings.getName());
            changed = true;
        }
        if (settings.containsSubscribed() && settings.isSubscribed() != internalConfig.optBoolean("subscribed", true)) {
            internalConfig.putSafe("subscribed", B(settings.isSubscribed()));
            changed = true;
        }
        return changed ? internalConfig : null;
    }

    @Override
    public boolean triggerCacheInvalidation(Session session, JSONObject originUserConfiguration, JSONObject newUserConfiguration) throws OXException {
        ICalCalendarFeedConfig oldFeedConfig = new ICalCalendarFeedConfig.DecryptedBuilder(session, new JSONObject(originUserConfiguration), new JSONObject()).build();
        JSONObject newUserConfigurationCopy = new JSONObject(newUserConfiguration);
        ICalCalendarFeedConfig newFeedConfig = new ICalCalendarFeedConfig.EncryptedBuilder(session, newUserConfigurationCopy, new JSONObject()).build();
        return oldFeedConfig.mandatoryChanges(newFeedConfig);
    }

    @Override
    public void checkAllowedUpdate(Session session, JSONObject originUserConfiguration, JSONObject newUserConfiguration) throws OXException {
        ICalCalendarFeedConfig oldFeedConfig = new ICalCalendarFeedConfig.DecryptedBuilder(session, new JSONObject(originUserConfiguration), new JSONObject()).build();
        JSONObject newUserConfigurationCopy = new JSONObject(newUserConfiguration);
        ICalCalendarFeedConfig newFeedConfig = new ICalCalendarFeedConfig.EncryptedBuilder(session, newUserConfigurationCopy, new JSONObject()).build();

        if (!newFeedConfig.getFeedUrl().equalsIgnoreCase(oldFeedConfig.getFeedUrl())) {
            throw ICalProviderExceptionCodes.NOT_ALLOWED_CHANGE.create("URI");
        }
    }

    @Override
    public int getDefaultMaxAccounts() {
        return 50;
    }
}
