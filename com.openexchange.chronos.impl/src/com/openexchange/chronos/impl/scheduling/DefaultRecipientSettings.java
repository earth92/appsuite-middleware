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

package com.openexchange.chronos.impl.scheduling;

import static com.openexchange.chronos.common.CalendarUtils.getFolderView;
import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.chronos.common.CalendarUtils.prependDefaultAccount;
import java.net.InetAddress;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.dmfs.rfc5545.DateTime;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.scheduling.RecipientSettings;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.modules.Module;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.groupware.notify.hostname.HostnameService;
import com.openexchange.i18n.LocaleTools;
import com.openexchange.java.Strings;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.regional.RegionalSettings;
import com.openexchange.regional.RegionalSettingsService;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;

/**
 * {@link DefaultRecipientSettings}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.3
 */
public class DefaultRecipientSettings implements RecipientSettings {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultRecipientSettings.class);
    private static final Pattern PATTERN_SLASH_FIXER = Pattern.compile("^//+|[^:]//+");

    private final ServiceLookup services;
    private final int contextId;
    private final CalendarUser recipient;
    private final CalendarUserType recipientType;
    private final Locale locale;
    private final TimeZone timeZone;
    private final RegionalSettings regionalSettings;
    private final HostData hostData;
    private final int msgFormat;

    /**
     * Initializes a new {@link DefaultRecipientSettings}.
     *
     * @param services A service lookup reference
     * @param session The calendar session
     * @param originator The originator of the message
     * @param recipient The recipient of the message
     * @param recipientType The calendar user type of the recipient
     * @param resource The resource
     */
    public DefaultRecipientSettings(ServiceLookup services, CalendarSession session, CalendarUser originator, CalendarUser recipient, CalendarUserType recipientType, CalendarObjectResource resource) {
        super();
        this.services = services;
        this.contextId = session.getContextId();
        this.recipient = recipient;
        this.recipientType = recipientType;
        this.hostData = session.getHostData();
        this.msgFormat = selectMsgFormat(session, recipient, recipientType);
        this.locale = selectLocale(session, originator, recipient, recipientType);
        this.timeZone = selectTimeZone(session, originator, recipient, resource, recipientType);
        this.regionalSettings = optRegionalSettings(services, contextId, recipient, recipientType);
    }

    @Override
    public CalendarUser getRecipient() {
        return recipient;
    }

    @Override
    public CalendarUserType getRecipientType() {
        return recipientType;
    }

    @Override
    public int getMsgFormat() {
        return msgFormat;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public TimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public RegionalSettings getRegionalSettings() {
        return regionalSettings;
    }

    @Override
    public String getDirectLink(Event event) {
        if (null == event || false == isInternal(recipient, recipientType) || false == CalendarUserType.INDIVIDUAL.matches(recipientType)) {
            return null;
        }
        /*
         * select correct folder identifier for recipient
         */
        String folderId;
        try {
            folderId = prependDefaultAccount(getFolderView(event, recipient.getEntity()));
        } catch (OXException e) {
            LOG.warn("Could not get folder view for {} for recipient {}, unable to generate direct link.", event, recipient, e);
            return null;
        }
        /*
         * generate link from template
         */
        ConfigurationService configurationService = services.getOptionalService(ConfigurationService.class);
        if (null == configurationService) {
            LOG.warn("", ServiceExceptionCode.absentService(ConfigurationService.class));
            return null;
        }
        String objectLinkTemplate = PATTERN_SLASH_FIXER.matcher(
            configurationService.getProperty("object_link", "https://[hostname]/[uiwebpath]#m=[module]&i=[object]&f=[folder]")).replaceAll("/");
        String webpath = configurationService.getProperty("com.openexchange.UIWebPath", "/appsuite/");
        if (webpath.startsWith("/")) {
            webpath = webpath.substring(1, webpath.length());
        }
        return objectLinkTemplate
            .replaceAll("\\[hostname\\]", getHostname())
            .replaceAll("\\[uiwebpath\\]", webpath)
            .replaceAll("\\[module\\]", Module.CALENDAR.getName())
            .replaceAll("\\[object\\]", event.getId())
            .replaceAll("\\[folder\\]", folderId)
        ;
    }

    private String getHostname() {
        String hostname = null != hostData ? hostData.getHost() : null;
        HostnameService hostnameService = services.getOptionalService(HostnameService.class);
        if (null != hostnameService) {
            String configuredHostname = hostnameService.getHostname(recipient.getEntity(), contextId);
            if (Strings.isNotEmpty(configuredHostname)) {
                hostname = configuredHostname;
            }
        }
        if (Strings.isEmpty(hostname)) {
            try {
                hostname = InetAddress.getLocalHost().getCanonicalHostName();
                LOG.warn("Could not determine hostname for {}, falling back to {}.", recipient, hostname);
            } catch (Exception e) {
                hostname = "localhost";
                LOG.warn("Could not determine hostname for {}, falling back to {}.", recipient, hostname, e);
            }
        }
        return hostname;
    }

    private static RegionalSettings optRegionalSettings(ServiceLookup services, int contextId, CalendarUser recipient, CalendarUserType recipientType) {
        if (isInternal(recipient, recipientType) && CalendarUserType.INDIVIDUAL.matches(recipientType)) {
            RegionalSettingsService regionalSettingsService = services.getOptionalService(RegionalSettingsService.class);
            if (null == regionalSettingsService) {
                LOG.warn("", ServiceExceptionCode.absentService(RegionalSettingsService.class));
                return null;
            }
            return regionalSettingsService.get(contextId, recipient.getEntity());
        }
        return null;
    }

    private static int selectMsgFormat(CalendarSession session, CalendarUser recipient, CalendarUserType recipientType) {
        if (isInternal(recipient, recipientType) && CalendarUserType.INDIVIDUAL.matches(recipientType)) {
            return session.getConfig().getMsgFormat(recipient.getEntity());
        }
        return UserSettingMail.MSG_FORMAT_BOTH;
    }

    private static Locale selectLocale(CalendarSession session, CalendarUser originator, CalendarUser recipient, CalendarUserType recipientType) {
        try {
            if (isInternal(recipient, recipientType) && CalendarUserType.INDIVIDUAL.matches(recipientType)) {
                return session.getEntityResolver().getLocale(recipient.getEntity());
            }
            if (isInternal(originator, CalendarUserType.INDIVIDUAL)) {
                return session.getEntityResolver().getLocale(originator.getEntity());
            }
        } catch (OXException e) {
            LOG.warn("Unexpected error determining target locale, falling back to defaults.", e);
        }
        return LocaleTools.DEFAULT_LOCALE;
    }

    private static TimeZone selectTimeZone(CalendarSession session, CalendarUser originator, CalendarUser recipient, CalendarObjectResource resource, CalendarUserType recipientType) {
        try {
            if (isInternal(recipient, recipientType) && CalendarUserType.INDIVIDUAL.matches(recipientType)) {
                return session.getEntityResolver().getTimeZone(recipient.getEntity());
            }
            if (isInternal(originator, CalendarUserType.INDIVIDUAL)) {
                return session.getEntityResolver().getTimeZone(originator.getEntity());
            }
        } catch (OXException e) {
            LOG.warn("Unexpected error determining target timezone, falling back to defaults.", e);
        }
        if (null != resource) {
            DateTime startDate = resource.getFirstEvent().getStartDate();
            if (null != startDate && false == startDate.isFloating()) {
                return startDate.getTimeZone();
            }
        }
        return TimeZone.getDefault();
    }

}
