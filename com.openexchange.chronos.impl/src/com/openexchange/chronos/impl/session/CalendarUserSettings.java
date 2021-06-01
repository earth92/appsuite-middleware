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

package com.openexchange.chronos.impl.session;

import static com.openexchange.chronos.impl.Utils.PROVIDER_ID;
import static com.openexchange.chronos.service.CalendarParameters.PARAMETER_CONNECTION;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.osgi.Tools.requireService;
import java.sql.Connection;
import java.util.List;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.Available;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.common.UserConfigWrapper;
import com.openexchange.chronos.compat.Appointment2Event;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.account.AdministrativeCalendarAccountService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.context.ContextService;
import com.openexchange.conversion.ConversionService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.mail.usersetting.UserSettingMailStorage;
import com.openexchange.preferences.ServerUserSetting;
import com.openexchange.server.ServiceLookup;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link CalendarUserSettings}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class CalendarUserSettings {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CalendarUserSettings.class);

    private final CalendarSession optSession;
    private final ServiceLookup services;
    private final int contextId;
    private final int userId;

    /**
     * Initializes a new {@link CalendarUserSettings}.
     *
     * @param session The underlying calendar session
     * @param userId The user identifier to provide the settings for
     * @param services A service lookup reference
     */
    public CalendarUserSettings(CalendarSession session, int userId, ServiceLookup services) {
        this(session, session.getContextId(), userId, services);
    }

    /**
     * Initializes a new {@link CalendarUserSettings}.
     *
     * @param contextId The context identifier
     * @param userId The user identifier to provide the settings for
     * @param services A service lookup reference
     */
    public CalendarUserSettings(int contextId, int userId, ServiceLookup services) {
        this(null, contextId, userId, services);
    }

    private CalendarUserSettings(CalendarSession session, int contextId, int userId, ServiceLookup services) {
        super();
        this.contextId = contextId;
        this.userId = userId;
        this.services = services;
        this.optSession = session;
    }

    /**
     * Gets the default alarm to be applied to events whose start-date is of type <i>date</i> from the underlying user configuration.
     *
     * @return The default alarm, or <code>null</code> if not defined
     */
    public List<Alarm> getDefaultAlarmDate() throws OXException {
        UserConfigWrapper userConfig = getUserConfig(userId);
        if (null == userConfig) {
            LOG.debug("No user configuration available for user {} in context {}, unable to get default alarms.", I(userId), I(contextId));
            return null;
        }
        return userConfig.getDefaultAlarmDate();
    }

    /**
     * Gets the default alarm to be applied to events whose start-date is of type <i>date-time</i> from the underlying user configuration.
     *
     * @return The default alarm, or <code>null</code> if not defined
     */
    public List<Alarm> getDefaultAlarmDateTime() throws OXException {
        UserConfigWrapper userConfig = getUserConfig(userId);
        if (null == userConfig) {
            LOG.debug("No user configuration available for user {} in context {}, unable to get default alarms.", I(userId), I(contextId));
            return null;
        }
        return userConfig.getDefaultAlarmDateTime();
    }

    /**
     * Gets the defined availability (in form of one or more available definitions) from the underlying user configuration.
     *
     * @return The availability, or <code>null</code> if not defined
     */
    public Available[] getAvailability() throws OXException {
        UserConfigWrapper userConfig = getUserConfig(userId);
        if (null == userConfig) {
            LOG.debug("No user configuration available for user {} in context {}, unable to get availability.", I(userId), I(contextId));
            return null;
        }
        return userConfig.getAvailability();
    }

    /**
     * Gets the identifier of the user's default personal calendar folder.
     *
     * @return The default calendar folder identifier
     */
    public String getDefaultFolderId() throws OXException {
        return String.valueOf(getFolderAccess().getDefaultFolderID(userId, FolderObject.CALENDAR));
    }

    /**
     * Gets the initial participation status to use for new events in a specific folder.
     *
     * @param inPublicFolder <code>true</code> if the event is located in a <i>public</i> folder, <code>false</code>, otherwise
     * @return The initial participation status, or {@link ParticipationStatus#NEEDS_ACTION} if not defined
     */
    public ParticipationStatus getInitialPartStat(boolean inPublicFolder) {
        Integer defaultStatus = null;
        try {
            if (inPublicFolder) {
                defaultStatus = getServerUserSettings().getDefaultStatusPublic(optSession.getContextId(), userId);
            } else {
                defaultStatus = getServerUserSettings().getDefaultStatusPrivate(optSession.getContextId(), userId);
            }
        } catch (OXException e) {
            LOG.warn("Error getting default participation status for user {}, falling back to \"{}\"",
                I(userId), ParticipationStatus.NEEDS_ACTION, e);
        }
        return null != defaultStatus ? Appointment2Event.getParticipationStatus(defaultStatus.intValue()) : ParticipationStatus.NEEDS_ACTION;
    }

    /**
     * Gets a value indicating whether notifications for newly created / scheduled events are enabled or not.
     * <p/>
     * This setting is either used for internal user attendees when the operation is performed by the organizer, or for the organizer or
     * calendar owner in case the operation is performed by another user on his behalf.
     * 
     * @return <code>true</code> of notifications are enabled, <code>false</code>, otherwise
     * @see com.openexchange.mail.usersetting.UserSettingMail#isNotifyAppointments()
     */
    public boolean isNotifyOnCreate() {
        return isNotifyOnUpdate();
    }

    /**
     * Gets a value indicating whether notifications for updated / re-scheduled events are enabled or not.
     * <p/>
     * This setting is either used for internal user attendees when the operation is performed by the organizer, or for the organizer or
     * calendar owner in case the operation is performed by another user on his behalf.
     * <p/>
     * This setting is <b>not</b> used for changes towards a user's participation status (reply operations).
     * 
     * @return <code>true</code> of notifications are enabled, <code>false</code>, otherwise
     * @see com.openexchange.mail.usersetting.UserSettingMail#isNotifyAppointments()
     */
    public boolean isNotifyOnUpdate() {
        try {
            return getUserSettingMail().isNotifyAppointments();
        } catch (OXException e) {
            LOG.warn("Error getting notification preferences from mail settings", e);
        }
        return true;
    }

    /**
     * Gets a value indicating whether notifications for deleted (cancelled) events are enabled or not.
     * <p/>
     * This setting is either used for internal user attendees when the operation is performed by the organizer, or for the organizer or
     * calendar owner in case the operation is performed by another user on his behalf.
     * 
     * @return <code>true</code> of notifications are enabled, <code>false</code>, otherwise
     * @see com.openexchange.mail.usersetting.UserSettingMail#isNotifyAppointments()
     */
    public boolean isNotifyOnDelete() {
        return isNotifyOnUpdate();
    }

    /**
     * Gets a value indicating whether notifications for replies of attendees are enabled or not.
     * <p/>
     * This setting used if the user is the event organizer and the operation is performed by an invited attendee.
     * 
     * @return <code>true</code> of notifications are enabled, <code>false</code>, otherwise
     * @see com.openexchange.mail.usersetting.UserSettingMail#isNotifyAppointmentsConfirmOwner()
     */
    public boolean isNotifyOnReply() {
        try {
            return getUserSettingMail().isNotifyAppointmentsConfirmOwner();
        } catch (OXException e) {
            LOG.warn("Error getting notification preferences from mail settings", e);
        }
        return true;
    }

    /**
     * Gets a value indicating whether notifications for replies of attendees are enabled or not.
     * <p/>
     * This setting used if the user is an attendee and the operation is performed by other invited attendee.
     * 
     * @return <code>true</code> of notifications are enabled, <code>false</code>, otherwise
     * @see com.openexchange.mail.usersetting.UserSettingMail#isNotifyAppointmentsConfirmParticipant()
     */
    public boolean isNotifyOnReplyAsAttendee() {
        try {
            return getUserSettingMail().isNotifyAppointmentsConfirmParticipant();
        } catch (OXException e) {
            LOG.warn("Error getting notification preferences from mail settings", e);
        }
        return true;
    }

    /**
     * Gets a value indicating the preferred message format of notification mails.
     * <p>
     * The returned <code>int</code> value is one of {@link UserSettingMail#MSG_FORMAT_TEXT_ONLY},
     * {@link UserSettingMail#MSG_FORMAT_HTML_ONLY}, and {@link UserSettingMail#MSG_FORMAT_BOTH}.
     *
     * @return The desired message format
     * @see com.openexchange.mail.usersetting.UserSettingMail#getMsgFormat()
     */
    public int getMsgFormat() {
        try {
            return getUserSettingMail().getMsgFormat();
        } catch (OXException e) {
            LOG.warn("Error getting notification preferences from mail settings", e);
        }
        return UserSettingMail.MSG_FORMAT_BOTH;
    }

    private OXFolderAccess getFolderAccess() throws OXException {
        Connection connection = optConnection();
        Context context;
        if (null == optSession) {
            context = requireService(ContextService.class, services).getContext(contextId);
        } else {
            context = ServerSessionAdapter.valueOf(optSession.getSession()).getContext();
        }
        return null != connection ? new OXFolderAccess(connection, context) : new OXFolderAccess(context);
    }

    private UserSettingMail getUserSettingMail() throws OXException {
        return UserSettingMailStorage.getInstance().getUserSettingMail(userId, contextId);
    }

    private ServerUserSetting getServerUserSettings() {
        Connection connection = optConnection();
        return null != connection ? ServerUserSetting.getInstance(connection) : ServerUserSetting.getInstance();
    }

    private Connection optConnection() {
        return null == optSession ? null : optSession.get(PARAMETER_CONNECTION(), Connection.class, null);
    }

    private UserConfigWrapper getUserConfig(int userId) throws OXException {
        CalendarAccount account = getAccount(userId);
        return null == account ? null : new UserConfigWrapper(requireService(ConversionService.class, services), account.getUserConfiguration());
    }

    private CalendarAccount getAccount(int userId) throws OXException {
        AdministrativeCalendarAccountService accountService = requireService(AdministrativeCalendarAccountService.class, services);
        return accountService.getAccount(optSession.getContextId(), userId, PROVIDER_ID);
    }

}
