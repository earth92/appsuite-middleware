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

package com.openexchange.groupware.notify;

import static com.openexchange.java.Autoboxing.I;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.mail.Multipart;
import com.openexchange.config.ConfigurationService;
import com.openexchange.event.impl.TaskEventInterface2;
import com.openexchange.exception.OXException;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.calendar.Constants;
import com.openexchange.groupware.container.CalendarObject;
import com.openexchange.groupware.container.ExternalUserParticipant;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.container.mail.MailObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.i18n.Notifications;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.groupware.notify.NotificationConfig.NotificationProperty;
import com.openexchange.groupware.notify.State.Type;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.groupware.userconfiguration.CapabilityUserConfigurationStorage;
import com.openexchange.groupware.userconfiguration.UserConfiguration;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.i18n.tools.RenderMap;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.i18n.tools.StringTemplate;
import com.openexchange.i18n.tools.Template;
import com.openexchange.i18n.tools.TemplateReplacement;
import com.openexchange.i18n.tools.TemplateToken;
import com.openexchange.i18n.tools.replacement.ChangeExceptionsReplacement;
import com.openexchange.i18n.tools.replacement.CommentsReplacement;
import com.openexchange.i18n.tools.replacement.ConfirmationActionReplacement;
import com.openexchange.i18n.tools.replacement.CreationDateReplacement;
import com.openexchange.i18n.tools.replacement.DeleteExceptionsReplacement;
import com.openexchange.i18n.tools.replacement.EndDateReplacement;
import com.openexchange.i18n.tools.replacement.FolderReplacement;
import com.openexchange.i18n.tools.replacement.FormatLocalizedStringReplacement;
import com.openexchange.i18n.tools.replacement.ParticipantsReplacement;
import com.openexchange.i18n.tools.replacement.ResourcesReplacement;
import com.openexchange.i18n.tools.replacement.SeriesReplacement;
import com.openexchange.i18n.tools.replacement.StartDateReplacement;
import com.openexchange.i18n.tools.replacement.StringReplacement;
import com.openexchange.i18n.tools.replacement.TaskActionReplacement;
import com.openexchange.i18n.tools.replacement.TaskPriorityReplacement;
import com.openexchange.i18n.tools.replacement.TaskStatusReplacement;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.mail.usersetting.UserSettingMailStorage;
import com.openexchange.regional.RegionalSettingsService;
import com.openexchange.resource.Resource;
import com.openexchange.resource.storage.ResourceStorage;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.tools.TimeZoneUtils;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public class ParticipantNotify implements TaskEventInterface2 {

    // TODO: Signature?

    private static final String STR_UNKNOWN = "UNKNOWN";

    private final static org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ParticipantNotify.class);

    public static ParticipantNotify messageSender = new ParticipantNotify();

    /**
     * Initializes a new {@link ParticipantNotify}
     */
    public ParticipantNotify() {
        super();
    }

    /**
     * Sends specified message
     *
     * @param mmsg The message
     * @param session The session
     * @param obj The calendar object
     * @param state The state
     */
    protected static void sendMessage(final MailMessage mmsg, final ServerSession session, final CalendarObject obj, final State state) {
        messageSender.sendMessage(mmsg, session, obj, state, false);
    }

    protected void sendMessage(final MailMessage msg, final ServerSession session, final CalendarObject obj, final State state, final boolean suppressOXReminderHeader) {
        if (LOG.isDebugEnabled()) {
            String message;
            if (Multipart.class.isInstance(msg.message)) {
                try {
                    Object content = ((Multipart) msg.message).getBodyPart(0).getContent();
                    String appendix = "\n\n(With ICal attached)";
                    if (Multipart.class.isInstance(content)) {
                        content = ((Multipart) content).getBodyPart(0).getContent();
                        appendix += "\n(With file attachments)";
                    }
                    message = content.toString() + appendix;
                } catch (@SuppressWarnings("unused") final Exception e) {
                    message = "";
                }
            } else {
                message = msg.message.toString();
            }
            LOG.debug("Sending message to: {}\n=====[{}]====\n\n{}\n\n", msg.addresses, msg.title, message);
        }

        int fuid = msg.folderId;
        if (fuid == -1) {
            fuid = obj.getParentFolderID();
        }

        if (suppressOXReminderHeader) {
            fuid = MailObject.DONT_SET;
        }
        final String type = (msg.overrideType != null) ? msg.overrideType.toString() : state.getType().toString();
        final MailObject mail = new MailObject(session, obj.getObjectID(), fuid, state.getModule(), type);
        final User sender = session.getUser();

        String fromAddr;
        final String senderSource = NotificationConfig.getProperty(NotificationProperty.FROM_SOURCE, "primaryMail");
        if (senderSource.equals("defaultSenderAddress")) {
            try {
                fromAddr = getUserSettingMail(session.getUserId(), session.getContext()).getSendAddr();
            } catch (OXException e) {
                LOG.error("", e);
                fromAddr = sender.getMail();
            }
        } else {
            fromAddr = sender.getMail();
        }

        if (sender == null) {
            mail.setFromAddr(fromAddr);
        } else {
            final QuotedInternetAddress addr = new QuotedInternetAddress();
            addr.setAddress(fromAddr);
            try {
                addr.setPersonal(sender.getDisplayName(), "UTF-8");
            } catch (@SuppressWarnings("unused") final UnsupportedEncodingException e) {
                // Cannot occur
            }
            mail.setFromAddr(addr.toString());
        }

        mail.setToAddrs(msg.addresses.toArray(new String[msg.addresses.size()]));
        mail.setText(msg.message);
        mail.setSubject(msg.title);

        if (Multipart.class.isInstance(msg.message)) {
            mail.setContentType(((Multipart) msg.message).getContentType());
            mail.setInternalRecipient(false);
        } else {
            mail.setContentType("text/plain; charset=UTF-8");
        }

        if (state.getModule() == Types.TASK) {
            if (msg.internal) {
                state.modifyInternal(mail, obj, session);
            } else {
                state.modifyExternal(mail, obj, session);
            }
        }

        try {
            mail.send();
        } catch (OXException e) {
            log(e);
        }
    }

    // Override for testing

    protected TIntSet loadAllUsersSet(final Context ctx) throws OXException {
        final int[] uids = UserStorage.getInstance().listAllUser(ctx);
        final TIntSet allIds = new TIntHashSet(uids.length);
        for (final int id : uids) {
            allIds.add(id);
        }
        return allIds;
    }

    protected User[] resolveUsers(final Context ctx, final int... ids) throws OXException {
        final User[] r = new User[ids.length];
        for (int i = 0; i < ids.length; i++) {
            r[i] = UserStorage.getInstance().getUser(ids[i], ctx);
        }
        return r;
    }

    protected Group[] resolveGroups(final Context ctx, final int... ids) throws OXException {
        GroupService groupService = ServerServiceRegistry.getServize(GroupService.class, true);
        final Group[] r = new Group[ids.length];
        int i = 0;
        for (final int id : ids) {
            r[i++] = groupService.getGroup(ctx, id);
        }
        return r;
    }

    protected Resource[] resolveResources(final Context ctx, final int... ids) throws OXException {
        final ResourceStorage resources = ServerServiceRegistry.getServize(ResourceStorage.class, true);
        final Resource[] r = new Resource[ids.length];
        int i = 0;
        for (final int id : ids) {
            r[i++] = resources.getResource(id, ctx);
        }
        return r;
    }

    protected UserConfiguration getUserConfiguration(final int id, final int[] groups, final Context context) throws OXException {
        return CapabilityUserConfigurationStorage.loadUserConfiguration(id, groups, context);
    }

    protected UserSettingMail getUserSettingMail(final int id, final Context context) throws OXException {
        return UserSettingMailStorage.getInstance().loadUserSettingMail(id, context);
    }

    @Override
    public void taskCreated(final Task taskObj, final Session session) {
        sendNotification(null, taskObj, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_NEW), Notifications.TASK_CREATE_MAIL, State.Type.NEW), false, false, false);
    }

    @Override
    public void taskModified(final Task taskObj, final Session session) {
        taskModified(null, taskObj, session);
    }

    @Override
    public void taskModified(final Task oldTask, final Task newTask, final Session session) {
        sendNotification(oldTask, newTask, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_CHANGED), Notifications.TASK_UPDATE_MAIL, State.Type.MODIFIED), false, false, true);
    }

    @Override
    public void taskAccepted(final Task taskObj, final Session session) {
        taskAccepted(null, taskObj, session);
    }

    @Override
    public void taskAccepted(final Task oldTask, final Task taskObj, final Session session) {
        sendNotification(oldTask, taskObj, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_ACCEPTED), new ConfirmationActionReplacement(ConfirmationActionReplacement.ACTION_ACCEPTED), Notifications.TASK_CONFIRMATION_MAIL, State.Type.ACCEPTED), false, false, false);
    }

    @Override
    public void taskDeclined(final Task taskObj, final Session session) {
        taskDeclined(null, taskObj, session);
    }

    @Override
    public void taskDeclined(final Task oldTask, final Task taskObj, final Session session) {
        sendNotification(oldTask, taskObj, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_DECLINED), new ConfirmationActionReplacement(ConfirmationActionReplacement.ACTION_DECLINED), Notifications.TASK_CONFIRMATION_MAIL, State.Type.DECLINED), false, false, false);
    }

    @Override
    public void taskTentativelyAccepted(final Task taskObj, final Session session) {
        taskTentativelyAccepted(null, taskObj, session);
    }

    @Override
    public void taskTentativelyAccepted(final Task oldTask, final Task taskObj, final Session session) {
        sendNotification(oldTask, taskObj, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_TENTATIVE), new ConfirmationActionReplacement(ConfirmationActionReplacement.ACTION_TENTATIVELY_ACCEPTED), Notifications.TASK_CONFIRMATION_MAIL, State.Type.TENTATIVELY_ACCEPTED), false, false, false);
    }

    @Override
    public void taskDeleted(final Task taskObj, final Session session) {
        /*
         * Clear calendar object from notification pool
         */
        NotificationPool.getInstance().removeByObject(taskObj.getObjectID(), session.getContextId());
        /*
         * Send delete notification
         */
        sendNotification(null, taskObj, session, new TaskState(new TaskActionReplacement(TaskActionReplacement.ACTION_DELETED), Notifications.TASK_DELETE_MAIL, State.Type.DELETED), false, true, true);
    }

    /**
     * TODO new object should have all necessary data when coming through the event system.
     */
    private void sendNotification(final CalendarObject oldObj, final CalendarObject newObj, final Session session, final State state, final boolean forceNotifyOthers, final boolean suppressOXReminderHeader, final boolean isUpdate) {
        if (Types.TASK != state.getModule()) {
            throw new UnsupportedOperationException();
        }

        // Safety check for passed session reference
        if (session.getUserId() <= 0 || session.getContextId() <= 0) {
            // Illegal session
            final String sessionId = session.getSessionID();
            if (null != sessionId) {
                ServerServiceRegistry.getInstance().getService(SessiondService.class).removeSession(sessionId);
            }
            return;
        }

        boolean onlyIrrelevantFieldsChanged = onlyIrrelevantFieldsChanged(session, oldObj, newObj, state);
        if (onlyIrrelevantFieldsChanged) {
            return;
        }

        final ServerSession serverSession;
        try {
            serverSession = ServerSessionAdapter.valueOf(session);
        } catch (OXException e) {
            LOG.error("", e);
            return;
        }
        /*
         * Remember object's title
         */
        final String title = newObj.getTitle() == null ? (oldObj == null ? "" : (oldObj.getTitle() == null ? "" : oldObj.getTitle())) : newObj.getTitle();
        /*
         * Check if notification shall be dropped
         */
        if (newObj.containsNotification() && !newObj.getNotification() && newObj.getCreatedBy() == serverSession.getUserId() && !forceNotifyOthers) {
            LOG.debug("Dropping notification for task {} ({}) since it indicates to discard its notification", title, I(newObj.getObjectID()));
            return;
        }
        if (newObj.getParticipants() == null) {
            if (oldObj == null || oldObj.getParticipants() == null) {
                LOG.debug("Dropping notification for task {} ({}) since it contains NO participants", title, I(newObj.getObjectID()));
                return;
            }
            /*
             * Grab participants/users from old object
             */
            newObj.setParticipants(oldObj.getParticipants());
            newObj.setUsers(oldObj.getUsers());
        }
        /*
         * Ensure start/end is set
         */
        if (newObj.getStartDate() == null && oldObj != null && oldObj.getStartDate() != null) {
            newObj.setStartDate(oldObj.getStartDate());
        }
        if (newObj.getEndDate() == null && oldObj != null && oldObj.getEndDate() != null) {
            newObj.setEndDate(oldObj.getEndDate());
        }
        if (CalendarObject.NO_RECURRENCE == newObj.getRecurrenceType() && oldObj != null && CalendarObject.NO_RECURRENCE != oldObj.getRecurrenceType()) {
            newObj.setRecurrenceType(oldObj.getRecurrenceType());
            if (oldObj.containsOccurrence()) {
                newObj.setOccurrence(oldObj.getOccurrence());
            }
            if (oldObj.containsInterval()) {
                newObj.setInterval(oldObj.getInterval());
            }
            if (oldObj.containsDays()) {
                newObj.setDays(oldObj.getDays());
            }
            if (oldObj.containsDayInMonth()) {
                newObj.setDayInMonth(oldObj.getDayInMonth());
            }
            if (oldObj.containsUntil()) {
                newObj.setUntil(oldObj.getUntil());
            }
        }
        if (!checkStartAndEndDate(newObj, state.getModule())) {
            return;
        }
        /*
         * Ensure that important fields are set
         */
        if (!newObj.containsCreatedBy() && oldObj != null && oldObj.containsCreatedBy()) {
            newObj.setCreatedBy(oldObj.getCreatedBy());
        }
        if (!newObj.containsCreationDate() && oldObj != null && oldObj.containsCreationDate()) {
            newObj.setCreationDate(oldObj.getCreationDate());
        }
        /*
         * A map to remember receivers
         */
        final Map<Locale, List<EmailableParticipant>> receivers = new HashMap<Locale, List<EmailableParticipant>>();
        /*
         * Generate a render map filled with object-specific information
         */
        final RenderMap renderMap = createRenderMap(newObj, oldObj, isUpdate, title, state, receivers, serverSession);

        /*
         * Add confirmation action replacement to render map if non-null
         */
        {
            final TemplateReplacement confirmActionRepl = state.getConfirmationAction();
            if (confirmActionRepl != null) {
                renderMap.put(confirmActionRepl);
            }
        }

        /*
         * Create message list
         */
        final List<MailMessage> messages = createMessageList(oldObj, newObj, state, forceNotifyOthers, isUpdate, serverSession, receivers, title, renderMap);

        /*
         * Send messages
         */
        for (final MailMessage mmsg : messages) {
            sendMessage(mmsg, serverSession, newObj, state, suppressOXReminderHeader);
        }
    }

    private boolean onlyIrrelevantFieldsChanged(@SuppressWarnings("unused") final Session session, final CalendarObject oldObj, final CalendarObject newObj, final State state) {
        if (oldObj == null || newObj == null) {
            return false;
        }
        return state.onlyIrrelevantFieldsChanged(oldObj, newObj);
    }

    private List<MailMessage> createMessageList(final CalendarObject oldObj, final CalendarObject newObj, final State state, final boolean forceNotifyOthers, final boolean isUpdate, final ServerSession session, final Map<Locale, List<EmailableParticipant>> receivers, final String title, final RenderMap renderMap) {
        if (Types.TASK != state.getModule()) {
            throw new UnsupportedOperationException();
        }
        final OXFolderAccess access = new OXFolderAccess(session.getContext());
        final StringBuilder b = new StringBuilder(2048);
        TIntSet allUserIds = null;
        try {
            allUserIds = loadAllUsersSet(session.getContext());
        } catch (OXException e) {
            log(e);
            return Collections.emptyList();
        }

        RegionalSettingsService regionalSettingsService = ServerServiceRegistry.getInstance().getService(RegionalSettingsService.class);
        final List<MailMessage> messages = new ArrayList<MailMessage>();
        for (final Map.Entry<Locale, List<EmailableParticipant>> entry : receivers.entrySet()) {
            final Locale locale = entry.getKey();
            /*
             * Apply new locale to replacements
             */
            final TemplateReplacement actionRepl = state.getAction();
            actionRepl.setLocale(locale);
            renderMap.applyLocale(locale);

            /*
             * Iterate over locale's participants
             */
            final List<EmailableParticipant> participants = entry.getValue();
            for (final EmailableParticipant p : participants) {
                TimeZone tz = TimeZone.getDefault();
                boolean sendMail = true;

                if (p.type != Participant.EXTERNAL_USER && allUserIds.contains(p.id)) {
                    try {
                        sendMail = !p.ignoreNotification && state.sendMail(getUserSettingMail(p.id, session.getContext()), newObj.getCreatedBy(), p.id, session.getUserId()) && ((!newObj.containsNotification() || newObj.getNotification()) || (forceNotifyOthers && p.id != session.getUserId()));
                        tz = p.timeZone;
                    } catch (OXException e) {
                        log(e);
                    }
                    if (null != regionalSettingsService) {
                        DateFormat df = regionalSettingsService.getDateFormat(p.cid, p.id, locale, DateFormat.DEFAULT);
                        actionRepl.setDateFormat(df);
                    }
                } else {
                    sendMail = !p.ignoreNotification && (!newObj.containsNotification() || newObj.getNotification()) || (newObj.getModifiedBy() != p.id && forceNotifyOthers);
                    sendMail = sendMail && (!ParticipantNotify.isStatusUpdate(state) || p.email.equals(newObj.getOrganizer()));
                    //                    sendMail = sendMail && (!EnumSet.of(State.Type.ACCEPTED, State.Type.DECLINED, State.Type.TENTATIVELY_ACCEPTED).contains(
                    //                        state.getType()) || p.email.equals(newObj.getOrganizer()));
                    if (p.timeZone != null) {
                        tz = p.timeZone;
                    }
                }

                if (sendMail) {
                    /*
                     * Apply time zone
                     */
                    renderMap.applyTimeZone(tz);

                    /*
                     * Folder
                     */
                    final int folderId = p.folderId > 0 ? p.folderId : newObj.getParentFolderID();
                    if (folderId > 0) {
                        final String folderName = getFolderName(folderId, locale, access);
                        final FolderReplacement folderRepl = new FolderReplacement(folderName);
                        folderRepl.setLocale(locale);
                        if (oldObj != null) {
                            if (p.folderId > 0) {
                                checkChangedFolder(oldObj, p.email, folderId, folderRepl, session);
                            } else {
                                folderRepl.setChanged(newObj.getParentFolderID() != oldObj.getParentFolderID());
                            }
                        }
                        renderMap.put(folderRepl);
                    }

                    /*
                     * Special information(s)
                     */
                    state.addSpecial(newObj, oldObj, renderMap, p);

                    ConfigurationService configService = ServerServiceRegistry.getServize(ConfigurationService.class);
                    boolean usePool = configService != null && configService.getBoolProperty("com.openexchange.calendar.notify.poolenabled", true);
                    if (usePool && isUpdate && EmailableParticipant.STATE_NONE == p.state) {
                        /*
                         * Add to pool
                         */
                        NotificationPool.getInstance().put(new PooledNotification(p, title, state, locale, (RenderMap) renderMap.clone(), session, newObj));
                        LOG.debug("Task update (id = {}) notification added to pool for receiver {}", I(newObj.getObjectID()), p.email);
                    } else {
                        /*
                         * Compose message
                         */
                        MailMessage message = null;
                        if (Participant.USER == p.type) {
                            message = createUserMessage(session, newObj, p, (userCanReadObject(p, newObj, session)), title, actionRepl, state, locale, renderMap, isUpdate, b);
                        } else {
                            message = createParticipantMessage(session, newObj, p, title, actionRepl, state, locale, renderMap, isUpdate, b);
                        }
                        if (null != message) {
                            messages.add(message);
                            LOG.debug("Task (id = {}) \"{}\" notification message generated for receiver {}", I(newObj.getObjectID()), EmailableParticipant.STATE_NEW == p.state ? "New" : (EmailableParticipant.STATE_REMOVED == p.state ? "Deleted" : state.getType().toString()), p.email);
                        }
                    }
                }
            }
        }
        return messages;
    }

    /**
     * Checks if specified user participant has read permission on given calendar object.
     *
     * @param participant The user participant
     * @param obj The calendar object
     * @param session The session providing needed user data
     * @return <code>true</code> if specified user participant has read permission on given calendar object; otherwise <code>false</code>
     */
    public static boolean userCanReadObject(final EmailableParticipant participant, final CalendarObject obj, final ServerSession session) {
        UserConfiguration userConfig;
        try {
            userConfig = UserConfigurationStorage.getInstance().getUserConfiguration(participant.id, session.getContext());
            final OXFolderAccess oxfa = new OXFolderAccess(session.getContext());

            if (oxfa.getFolderType(obj.getParentFolderID()) == FolderObject.PRIVATE) {
                return true;
            }

            final EffectivePermission permission = oxfa.getFolderPermission(obj.getParentFolderID(), participant.id, userConfig);

            if (permission.canReadAllObjects()) {
                return true;
            }

            if (permission.canReadOwnObjects() && obj.getCreatedBy() == participant.id) {
                return true;
            }
        } catch (OXException e) {
            log(e);
        }

        return false;
    }

    /**
     * Gets the folder name.
     *
     * @param folderId The folder ID
     * @param locale The locale
     * @param access The folder access instance
     * @return The folder name
     */
    protected String getFolderName(final int folderId, final Locale locale, final OXFolderAccess access) {
        String folderName = FolderObject.getFolderString(folderId, locale);
        if (folderName == null) {
            try {
                folderName = access.getFolderName(folderId);
            } catch (OXException e) {
                LOG.error("", e);
                folderName = "";
            }
        }
        return folderName;
    }

    /**
     * Creates a message for specified user.
     *
     * @param session
     * @param p The participant
     * @param canRead <code>true</code> if provided participant has read permission; otherwise <code>false</code>
     * @param title The object's title
     * @param actionRepl The action replacement to compose the message's title
     * @param state The object's state
     * @param locale The locale
     * @param renderMap The render map
     * @param isUpdate <code>true</code> if an update event triggered the notification; otherwise <code>false</code>
     * @param b A string builder
     * @return The created message
     */
    protected static MailMessage createUserMessage(final ServerSession session, final CalendarObject cal, final EmailableParticipant p, final boolean canRead, final String title, final TemplateReplacement actionRepl, final State state, final Locale locale, final RenderMap renderMap, final boolean isUpdate, final StringBuilder b) {
        return createParticipantMessage0(session, cal, p, canRead, title, actionRepl, state, locale, renderMap, isUpdate, b);
    }

    /**
     * Creates a message for specified participant.
     *
     * @param session
     * @param p The participant
     * @param title The object's title
     * @param actionRepl The action replacement to compose the message's title
     * @param state The object's state
     * @param locale The locale
     * @param renderMap The render map
     * @param isUpdate <code>true</code> if an update event triggered the notification; otherwise <code>false</code>
     * @param b A string builder
     * @return The created message
     */
    protected static MailMessage createParticipantMessage(final ServerSession session, final CalendarObject cal, final EmailableParticipant p, final String title, final TemplateReplacement actionRepl, final State state, final Locale locale, final RenderMap renderMap, final boolean isUpdate, final StringBuilder b) {
        return createParticipantMessage0(session, cal, p, true, title, actionRepl, state, locale, renderMap, isUpdate, b);
    }

    private static final Pattern PATTERN_PREFIX_MODIFIED = Pattern.compile("(^|\r?\n)" + Pattern.quote(TemplateReplacement.PREFIX_MODIFIED));

    private static MailMessage createParticipantMessage0(@SuppressWarnings("unused") final ServerSession session, @SuppressWarnings("unused") final CalendarObject cal, final EmailableParticipant p, final boolean canRead, final String title, final TemplateReplacement actionRepl, final State state, final Locale locale, final RenderMap renderMap, final boolean isUpdate, final StringBuilder b) {
        if (Types.TASK != state.getModule()) {
            throw new UnsupportedOperationException();
        }
        final MailMessage msg = new MailMessage();
        final Template createTemplate = state.getTemplate();
        final StringHelper strings = StringHelper.valueOf(locale);

        b.setLength(0);
        actionRepl.setLocale(locale);
        msg.title = b.append(actionRepl.getReplacement()).append(": ").append(title).toString();
        b.setLength(0);
        if (isUpdate) {
            if (EmailableParticipant.STATE_REMOVED == p.state) {
                /*
                 * Current participant is removed by caught update event
                 */
                msg.overrideType = State.Type.DELETED;
                /*
                 * Get cloned version of render map to apply changed status
                 */
                final RenderMap clone = clonedRenderMap(renderMap);
                msg.title = b.append(new TaskActionReplacement(TaskActionReplacement.ACTION_DELETED, locale).getReplacement()).append(": ").append(title).toString();
                b.setLength(0);
                /*
                 * Render proper message for removed participant
                 */
                msg.message = new StringTemplate(Notifications.TASK_DELETE_MAIL).render(p.getLocale(), clone);
            } else if (EmailableParticipant.STATE_NEW == p.state) {
                /*
                 * Current participant is added by caught update event
                 */
                msg.overrideType = State.Type.NEW;
                /*
                 * Get cloned version of render map to apply changed status
                 */
                final RenderMap clone = clonedRenderMap(renderMap);
                msg.title = b.append(new TaskActionReplacement(TaskActionReplacement.ACTION_NEW, locale).getReplacement()).append(": ").append(title).toString();
                b.setLength(0);
                /*
                 * Render proper message for removed participant
                 */
                final String message = getTaskCreateMessage(p, canRead);
                msg.message = new StringTemplate(message).render(p.getLocale(), clone);
            } else {
                {
                    final List<TemplateReplacement> changes = renderMap.getChanges();
                    if (changes.isEmpty()) {
                        /*
                         * No element contains relevant changes to notify about...
                         */
                        return null;
                    }
                    boolean relevantChanges = false;
                    for (final TemplateReplacement templateReplacement : changes) {
                        if (templateReplacement.relevantChange()) {
                            relevantChanges = true;
                            break;
                        }
                    }
                    if (!relevantChanges) {
                        /*
                         * No element contains relevant changes to notify about...
                         */
                        return null;
                    }
                }

                String textMessage = "";
                if (p.type == Participant.EXTERNAL_USER || p.type == Participant.RESOURCE) {
                    final String template = Notifications.TASK_UPDATE_MAIL_EXT;
                    textMessage = new StringTemplate(template).render(p.getLocale(), renderMap);
                } else if (!canRead) {
                    final String template = Notifications.TASK_UPDATE_MAIL_NO_ACCESS;
                    textMessage = new StringTemplate(template).render(p.getLocale(), renderMap);
                } else {
                    textMessage = createTemplate.render(p.getLocale(), renderMap);
                }

                /*
                 * Check for PREFIX_MODIFIED
                 */
                if (!PATTERN_PREFIX_MODIFIED.matcher(textMessage).find()) {
                    /*
                     * No element contains relevant changes to notify about...
                     */
                    return null;
                }

                msg.message = textMessage;
            }
        } else {
            if (State.Type.NEW.equals(state.getType())) {
                String textMessage = "";
                if ((p.type == Participant.EXTERNAL_USER || p.type == Participant.RESOURCE)) {
                    final String template = strings.getString(Notifications.TASK_CREATE_MAIL_EXT);
                    textMessage = new StringTemplate(template).render(p.getLocale(), renderMap);
                } else if (!canRead) {
                    final String template = strings.getString(Notifications.TASK_CREATE_MAIL_NO_ACCESS);
                    textMessage = new StringTemplate(template).render(p.getLocale(), renderMap);
                } else {
                    textMessage = createTemplate.render(p.getLocale(), renderMap);
                }
                msg.message = textMessage;
            } else if (ParticipantNotify.isStatusUpdate(state)) {
                String textMessage = "";
                if ((p.type == Participant.EXTERNAL_USER || p.type == Participant.RESOURCE)) {
                    final String template = strings.getString(Notifications.TASK_CONFIRMATION_MAIL_EXT);
                    textMessage = new StringTemplate(template).render(p.getLocale(), renderMap);
                } else {
                    textMessage = createTemplate.render(p.getLocale(), renderMap);
                }
                // Attach IMIP Magic only for external users on secondary events, to tell them the state of the appointment, but don't
                // bother with internal users.
                msg.message = textMessage;
            } else if (state.getType() == State.Type.DELETED) {
                msg.message = createTemplate.render(p.getLocale(), renderMap);
            } else {
                msg.message = createTemplate.render(p.getLocale(), renderMap);
            }
        }
        if (Participant.RESOURCE == p.type) {
            /*-
             * Special prefixes for resource participant receivers.
             *
             * Prefix already applied to multipart/* content, therefore only check for text/plain content
             */
            final Object content = msg.message;
            if (content instanceof String) {
                /*
                 * Prepend prefix to text content
                 */
                msg.message = b.append(String.format(strings.getString(Notifications.RESOURCE_PREFIX), p.displayName)).append(": ").append(content).toString();
                b.setLength(0);
            }
            /*
             * Prefix title
             */
            msg.title = b.append('[').append(strings.getString(Notifications.RESOURCE_TITLE_PREFIX)).append("] ").append(msg.title).toString();
            b.setLength(0);
        }
        msg.addresses.add(p.email);
        msg.folderId = p.folderId;
        msg.internal = p.type != Participant.EXTERNAL_USER;
        return msg;
    }

    private static String getTaskCreateMessage(final EmailableParticipant p, final boolean canRead) {
        if (p.type == Participant.EXTERNAL_USER || p.type == Participant.RESOURCE) {
            return Notifications.TASK_CREATE_MAIL_EXT;
        } else if (!canRead) {
            return Notifications.TASK_CREATE_MAIL_NO_ACCESS;
        } else {
            return Notifications.TASK_CREATE_MAIL;
        }
    }

    private RenderMap createRenderMap(final CalendarObject newObj, final CalendarObject oldObj, final boolean isUpdate, final String title, final State state, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session) {
        final int module = state.getModule();
        /*
         * Containers for traversed participants
         */
        final SortedSet<EmailableParticipant> participantSet = new TreeSet<EmailableParticipant>();
        final SortedSet<EmailableParticipant> resourceSet = new TreeSet<EmailableParticipant>();
        final Map<String, EmailableParticipant> all = new HashMap<String, EmailableParticipant>();
        /*
         * Traverse participants and fill containers
         */
        final UserParticipant[] users = newObj.getUsers();
        if (null == users) {
            Participant[] oldParticipants = new Participant[0];
            if (oldObj != null) {
                oldParticipants = oldObj.getParticipants();
            }
            sortParticipants(oldParticipants, newObj.getParticipants(), participantSet, resourceSet, receivers, session, all);
        } else {
            UserParticipant[] oldUsers = new UserParticipant[0];
            if (oldObj != null) {
                oldUsers = oldObj.getUsers();
            }
            Participant[] oldParticipants = new Participant[0];
            if (oldObj != null) {
                oldParticipants = oldObj.getParticipants();
            }

            sortUserParticipants(oldUsers, newObj.getUsers(), participantSet, isUpdate, receivers, session, all, state);
            sortExternalParticipantsAndResources(oldParticipants, newObj.getParticipants(), participantSet, resourceSet, isUpdate, receivers, session, all, newObj.getOrganizer(), state);
        }
        // Add task owner to receivers list to make him receive mails about changed participants states.
        if (newObj instanceof Task) {
            addTaskOwner(oldObj, newObj, receivers, all, session);
        }
        /*
         * Generate a render map
         */
        final RenderMap renderMap = new RenderMap();
        renderMap.put(new FormatLocalizedStringReplacement(TemplateToken.TITLE, Notifications.FORMAT_DESCRIPTION, title).setChanged(isUpdate ? (oldObj == null ? false : !compareObjects(title, oldObj.getTitle())) : false));
        renderMap.put(new ParticipantsReplacement(participantSet).setChanged(isUpdate));
        renderMap.put(new ResourcesReplacement(resourceSet).setChanged(isUpdate));
        {
            String createdByDisplayName = STR_UNKNOWN;
            final Context ctx = session.getContext();
            if (0 != newObj.getCreatedBy()) {
                try {
                    createdByDisplayName = resolveUsers(ctx, newObj.getCreatedBy())[0].getDisplayName();
                } catch (OXException e) {
                    createdByDisplayName = STR_UNKNOWN;
                    log(e);
                }
            }
            String modifiedByDisplayName = STR_UNKNOWN;
            try {
                modifiedByDisplayName = resolveUsers(ctx, session.getUserId())[0].getDisplayName();
            } catch (OXException e) {
                modifiedByDisplayName = STR_UNKNOWN;
                log(e);
            }

            String onBehalfDisplayName = STR_UNKNOWN;
            final OXFolderAccess oxfa = new OXFolderAccess(session.getContext());
            try {
                onBehalfDisplayName = resolveUsers(ctx, oxfa.getFolderOwner(newObj.getParentFolderID()))[0].getDisplayName();
            } catch (OXException e) {
                log(e);
            }
            renderMap.put(new StringReplacement(TemplateToken.CREATED_BY, createdByDisplayName));
            renderMap.put(new StringReplacement(TemplateToken.CHANGED_BY, modifiedByDisplayName));
            renderMap.put(new StringReplacement(TemplateToken.BEHALF_OF, onBehalfDisplayName));
        }
        {
            final String note = null == newObj.getNote() ? "" : newObj.getNote();
            renderMap.put(new CommentsReplacement(note).setChanged(isUpdate ? (oldObj == null ? false : !compareStrings(note, oldObj.getNote())) : false));
        }
        /*
         * Add task-specific replacements
         */
        if (Types.TASK == module) {
            final Task task = (Task) newObj;
            final Task oldTask = (Task) oldObj;
            {
                final Integer priority = task.getPriority();
                final Integer oldPriority = oldTask == null ? null : oldTask.getPriority();
                if (null != priority) {
                    TaskPriorityReplacement replacement = new TaskPriorityReplacement(priority.intValue());
                    renderMap.put(replacement);
                    replacement.setChanged(oldTask == null ? false : !priority.equals(oldPriority));
                } else {
                    renderMap.put(TaskPriorityReplacement.emptyTaskPriorityReplacement());
                }
            }
            {
                final int status = task.getStatus();
                final int percentComplete = task.getPercentComplete();
                boolean changed = false;
                if (status != 0 && oldTask != null) {
                    changed |= (status != oldTask.getStatus());
                    changed |= (percentComplete != oldTask.getPercentComplete());
                }
                try {
                    renderMap.put(new TaskStatusReplacement(status, percentComplete).setChanged(changed));
                } catch (@SuppressWarnings("unused") final IllegalArgumentException e) {
                    renderMap.put(TaskStatusReplacement.emptyTaskStatusReplacement());
                }
            }
        }
        /*
         * Generate replacements which got modified by participant data
         */
        {
            final boolean isTask = (Types.TASK == module);
            final boolean isFulltime;
            if (isTask) {
                isFulltime = true;
            } else {
                isFulltime = false;
            }
            final Date start = newObj.getStartDate();
            renderMap.put(new StartDateReplacement(start, isFulltime).setChanged(isUpdate ? (oldObj == null ? false : !compareObjects(start, oldObj.getStartDate())) : false));
            Date end = newObj.getEndDate();
            /*
             * Determine changed status with original end time
             */
            final boolean endChanged = isUpdate ? (oldObj == null ? false : !compareObjects(end, oldObj.getEndDate())) : false;
            /*
             * Set end time to first occurrence's end time if necessary
             */

            if (newObj.getRecurrenceType() != CalendarObject.NO_RECURRENCE) {
                if (start != null && end != null) {
                    end = computeFirstOccurrenceEnd(start.getTime(), end.getTime());
                }
            }

            renderMap.put(new EndDateReplacement(end, isFulltime, isTask).setChanged(endChanged));
        }
        renderMap.put(new CreationDateReplacement(newObj.containsCreationDate() ? newObj.getCreationDate() : (oldObj == null ? null : oldObj.getCreationDate()), null));
        {
            final SeriesReplacement seriesRepl;
            if (newObj.containsRecurrenceType() || newObj.getRecurrenceType() != CalendarObject.NO_RECURRENCE) {
                seriesRepl = new SeriesReplacement(newObj, (Types.TASK == module), session.getContextId(), session.getUserId());
                seriesRepl.setChanged(isUpdate ? (oldObj == null ? false : !compareRecurrenceInformation(newObj, oldObj)) : false);
            } else if (oldObj != null && oldObj.containsRecurrenceType()) {
                seriesRepl = new SeriesReplacement(oldObj, (Types.TASK == module), session.getContextId(), session.getUserId());
                seriesRepl.setChanged(false);
            } else {
                seriesRepl = new SeriesReplacement(newObj, (Types.TASK == module), session.getContextId(), session.getUserId());
                seriesRepl.setChanged(false);
            }
            renderMap.put(seriesRepl);
        }
        {
            final DeleteExceptionsReplacement deleteExceptionsReplacement;
            final Date[] deleteExcs = newObj.getDeleteException();
            if (newObj.containsDeleteExceptions() || deleteExcs != null) {
                deleteExceptionsReplacement = new DeleteExceptionsReplacement(deleteExcs);
                deleteExceptionsReplacement.setChanged(isUpdate ? (oldObj == null ? false : !compareDates(deleteExcs, oldObj.getDeleteException())) : false);
            } else if (oldObj != null && oldObj.containsDeleteExceptions()) {
                deleteExceptionsReplacement = new DeleteExceptionsReplacement(oldObj.getDeleteException());
                deleteExceptionsReplacement.setChanged(false);
            } else {
                deleteExceptionsReplacement = new DeleteExceptionsReplacement(deleteExcs);
                deleteExceptionsReplacement.setChanged(false);
            }
            renderMap.put(deleteExceptionsReplacement);
        }
        {
            final ChangeExceptionsReplacement changeExceptionsReplacement;
            final Date[] changeExcs = newObj.getChangeException();
            if (newObj.containsChangeExceptions() || changeExcs != null) {
                changeExceptionsReplacement = new ChangeExceptionsReplacement(changeExcs);
                changeExceptionsReplacement.setChanged(isUpdate ? (oldObj == null ? false : !compareDates(changeExcs, oldObj.getChangeException())) : false);
            } else if (oldObj != null && oldObj.containsChangeExceptions()) {
                final Date[] oldChangeExcs = oldObj.getChangeException();
                changeExceptionsReplacement = new ChangeExceptionsReplacement(oldChangeExcs);
                changeExceptionsReplacement.setChanged(false);
            } else {
                changeExceptionsReplacement = new ChangeExceptionsReplacement(changeExcs);
                changeExceptionsReplacement.setChanged(false);
            }
            renderMap.put(changeExceptionsReplacement);
        }
        return renderMap;
    }

    private static RenderMap clonedRenderMap(final RenderMap renderMap) {
        return ((RenderMap) renderMap.clone()).applyChangedStatus(false);
    }

    private void checkChangedFolder(final CalendarObject oldObj, final String email, final int folderId, final FolderReplacement folderRepl, final ServerSession session) {
        final Participant[] oldParticipants = oldObj.getParticipants();
        final Context ctx = session.getContext();
        if (oldParticipants != null) {
            for (final Participant participant : oldParticipants) {
                switch (participant.getType()) {
                    case Participant.USER:
                        EmailableParticipant p = getUserParticipant(participant, ctx);
                        if (p != null && p.type == Participant.USER && p.folderId > 0 && p.email.equalsIgnoreCase(email)) {
                            folderRepl.setChanged(p.folderId != folderId);
                            return;
                        }
                        break;
                    case Participant.EXTERNAL_USER:
                        p = getExternalParticipant(participant, session);
                        if (p != null && p.type == Participant.USER && p.folderId > 0 && p.email.equalsIgnoreCase(email)) {
                            folderRepl.setChanged(p.folderId != folderId);
                            return;
                        }
                        break;
                    case Participant.RESOURCE:
                        p = getResourceParticipant(participant, session);
                        if (p != null && p.type == Participant.USER && p.folderId > 0 && p.email.equalsIgnoreCase(email)) {
                            folderRepl.setChanged(p.folderId != folderId);
                            return;
                        }
                        break;
                    case Participant.GROUP:
                        try {
                            // FIXME 101 SELECT problem
                            final Group group = resolveGroups(ctx, participant.getIdentifier())[0];
                            final int[] members = group.getMember();
                            final User[] memberObjects = resolveUsers(ctx, members);
                            for (final User user : memberObjects) {

                                final int[] groups = user.getGroups();
                                final TimeZone tz = TimeZoneUtils.getTimeZone(user.getTimeZone());

                                if (user.getMail() != null) {
                                    p = new EmailableParticipant(ctx.getContextId(), Participant.USER, user.getId(), groups, user.getMail(), user.getDisplayName(), user.getLocale(), tz, 10, -1, CalendarObject.NONE, null, participant.isIgnoreNotification());
                                    if (p.type == Participant.USER && p.folderId > 0 && p.email.equalsIgnoreCase(email)) {
                                        folderRepl.setChanged(p.folderId != folderId);
                                        return;
                                    }
                                }
                            }
                        } catch (OXException e) {
                            log(e);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown Participant Type: " + participant.getType());
                }
            }
        }
    }

    private void sortExternalParticipantsAndResources(final Participant[] oldParticipants, final Participant[] newParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final boolean isUpdate, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session, final Map<String, EmailableParticipant> all, final String organizer, final State state) {
        if (state.getType().equals(Type.DELETED)) {
            sortOldExternalParticipantsAndResources(newParticipants, participantSet, resourceSet, isUpdate, receivers, all, session, new Participant[0], organizer, state);
        } else {
            sortNewExternalParticipantsAndResources(newParticipants, participantSet, resourceSet, receivers, session, all, oldParticipants);
            sortOldExternalParticipantsAndResources(oldParticipants, participantSet, resourceSet, isUpdate, receivers, all, session, newParticipants, organizer, state);
        }
    }

    private void sortOldExternalParticipantsAndResources(final Participant[] oldParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final boolean isUpdate, final Map<Locale, List<EmailableParticipant>> receivers, final Map<String, EmailableParticipant> all, final ServerSession session, final Participant[] newParticipants, final String organizer, final State state) {
        if (oldParticipants == null) {
            return;
        }
        final Context ctx = session.getContext();
        // List<Participant> mergedWithOrganizer = new ArrayList<Participant>(Arrays.asList(oldParticipants));
        // if (organizer != null && !organizer.trim().equals(""))
        // mergedWithOrganizer.add(new ExternalUserParticipant(organizer));

        for (final Participant participant : oldParticipants) {
            switch (participant.getType()) {
                case Participant.USER:
                    break;
                case Participant.EXTERNAL_USER:
                    EmailableParticipant p = getExternalParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }
                    break;
                case Participant.RESOURCE:
                    p = getResourceParticipant(participant, session);
                    if (p == null) {
                        // Might be user added as resource (!)
                        p = getUserParticipant(participant, ctx);
                    }
                    if (p != null) {
                        p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, true);
                    }
                    break;
                case Participant.GROUP:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Participant Type: " + participant.getType());
            }
        }

        if ((isUpdate || ParticipantNotify.isStatusUpdate(state)) && organizer != null) {
            EmailableParticipant externalParticipant = getExternalParticipant(new ExternalUserParticipant(organizer), session);
            if (externalParticipant != null) {
                addSingleParticipant(externalParticipant, participantSet, resourceSet, receivers, all, false);
            }
        }
    }

    private void sortNewExternalParticipantsAndResources(final Participant[] newParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session, final Map<String, EmailableParticipant> all, final Participant[] oldParticipants) {
        if (newParticipants == null) {
            return;
        }
        final Context ctx = session.getContext();
        for (final Participant participant : newParticipants) {
            switch (participant.getType()) {
                case Participant.USER:
                    break;
                case Participant.EXTERNAL_USER:
                    EmailableParticipant p = getExternalParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }

                    break;
                case Participant.RESOURCE:
                    p = getResourceParticipant(participant, session);
                    if (p == null) {
                        // Might be user added as resource (!)
                        p = getUserParticipant(participant, ctx);
                    }
                    if (p != null) {
                        p.state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, true);
                    }
                    break;
                case Participant.GROUP:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Participant Type: " + participant.getType());
            }
        }
    }

    private void sortParticipants(final Participant[] oldParticipants, final Participant[] newParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session, final Map<String, EmailableParticipant> all) {
        sortNewParticipants(newParticipants, participantSet, resourceSet, receivers, session, all, oldParticipants);
        sortOldParticipants(oldParticipants, participantSet, resourceSet, receivers, all, session, newParticipants);
    }

    private void sortOldParticipants(final Participant[] oldParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final Map<Locale, List<EmailableParticipant>> receivers, final Map<String, EmailableParticipant> all, final ServerSession session, final Participant[] newParticipants) {
        if (oldParticipants == null) {
            return;
        }
        final Context ctx = session.getContext();
        for (final Participant participant : oldParticipants) {
            switch (participant.getType()) {
                case Participant.USER:
                    EmailableParticipant p = getUserParticipant(participant, ctx);
                    if (p != null) {
                        p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }
                    break;
                case Participant.EXTERNAL_USER:
                    p = getExternalParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }
                    break;
                case Participant.RESOURCE:
                    p = getResourceParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, true);
                    }
                    break;
                case Participant.GROUP:
                    try {
                        // FIXME 101 SELECT problem
                        final int state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                        final Group group = resolveGroups(ctx, participant.getIdentifier())[0];
                        final int[] members = group.getMember();
                        final User[] memberObjects = resolveUsers(ctx, members);
                        for (final User user : memberObjects) {
                            // final String lang = user.getPreferredLanguage();
                            final int[] groups = user.getGroups();
                            final TimeZone tz = TimeZoneUtils.getTimeZone(user.getTimeZone());

                            if (user.getMail() != null) {
                                p = new EmailableParticipant(ctx.getContextId(), Participant.USER, user.getId(), groups, user.getMail(), user.getDisplayName(), user.getLocale(), tz, 10, -1, CalendarObject.NONE, null, participant.isIgnoreNotification());
                                p.state = state;
                                addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                            }
                        }
                    } catch (OXException e) {
                        log(e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Participant Type: " + participant.getType());
            }

        }
    }

    private void sortNewParticipants(final Participant[] newParticipants, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session, final Map<String, EmailableParticipant> all, final Participant[] oldParticipants) {
        if (newParticipants == null) {
            return;
        }
        final Context ctx = session.getContext();
        for (final Participant participant : newParticipants) {
            switch (participant.getType()) {
                case Participant.USER:
                    EmailableParticipant p = getUserParticipant(participant, ctx);
                    if (p != null) {
                        p.state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }
                    break;
                case Participant.EXTERNAL_USER:
                    p = getExternalParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                    }

                    break;
                case Participant.RESOURCE:
                    p = getResourceParticipant(participant, session);
                    if (p != null) {
                        p.state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        addSingleParticipant(p, participantSet, resourceSet, receivers, all, true);
                    }
                    break;
                case Participant.GROUP:
                    try {
                        // FIXME 101 SELECT problem
                        final int state = contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                        final Group group = resolveGroups(ctx, participant.getIdentifier())[0];
                        final int[] members = group.getMember();
                        final User[] memberObjects = resolveUsers(ctx, members);
                        for (final User user : memberObjects) {
                            // final String lang = user.getPreferredLanguage();
                            final int[] groups = user.getGroups();
                            final TimeZone tz = TimeZoneUtils.getTimeZone(user.getTimeZone());

                            if (user.getMail() != null) {
                                p = new EmailableParticipant(ctx.getContextId(), Participant.USER, user.getId(), groups, user.getMail(), user.getDisplayName(), user.getLocale(), tz, 10, -1, CalendarObject.NONE, null, participant.isIgnoreNotification());
                                p.state = state;
                                addSingleParticipant(p, participantSet, resourceSet, receivers, all, false);
                            }
                        }
                    } catch (OXException e) {
                        log(e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Participant Type: " + participant.getType());
            }

        }
    }

    private static void log(final OXException e) {
        switch (e.getCategories().get(0).getLogLevel()) {
            case TRACE:
                LOG.trace("", e);
                break;
            case DEBUG:
                LOG.debug("", e);
                break;
            case INFO:
                LOG.info("", e);
                break;
            case WARNING:
                LOG.warn("", e);
                break;
            case ERROR:
                LOG.error("", e);
                break;
            default:
                break;
        }
    }

    private EmailableParticipant getExternalParticipant(final Participant participant, final ServerSession session) {
        if (null == participant.getEmailAddress()) {
            return null;
        }
        /*
         * Store session user's locale and time zone which are used for external participants
         */
        final User user;
        Locale l;
        TimeZone tz;
        try {
            user = resolveUsers(session.getContext(), session.getUserId())[0];
            l = user.getLocale();
            tz = TimeZone.getTimeZone(user.getTimeZone());
        } catch (OXException e) {
            // Should not happen
            LOG.warn("Could not resolve user from session: UserId: {} in Context: {}", I(session.getUserId()), I(session.getContextId()), e);
            l = Locale.getDefault();
            tz = TimeZone.getDefault();
        }
        return new EmailableParticipant(session.getContextId(), participant.getType(), -1, new int[0], participant.getEmailAddress(), participant.getDisplayName(), l, tz, 0, -1, CalendarObject.NONE, null, participant.isIgnoreNotification());
    }

    private EmailableParticipant getUserParticipant(final Participant participant, final Context ctx) {

        int[] groups = null;
        TimeZone tz = null;
        String mail = null;
        String displayName = null;
        int folderId = -1;
        Locale locale = null;

        try {
            final User user = resolveUsers(ctx, participant.getIdentifier())[0];
            locale = user.getLocale();
            mail = user.getMail();
            if (mail == null) {
                mail = participant.getEmailAddress();
            }
            displayName = user.getDisplayName();
            if (displayName == null) {
                displayName = participant.getDisplayName();
            }
            groups = user.getGroups();
            tz = TimeZone.getTimeZone(user.getTimeZone());
            if (participant instanceof UserParticipant) {
                final UserParticipant userParticipant = (UserParticipant) participant;
                folderId = userParticipant.getPersonalFolderId();
                // System.out.println("PERSONAL FOLDER ID FOR PARTICIPANT "+
                // userParticipant.getIdentifier()+": "+folderId);
            }
        } catch (OXException e) {
            log(e);
        }

        if (mail != null) {
            if (participant instanceof UserParticipant) {
                final UserParticipant up = (UserParticipant) participant;
                return new EmailableParticipant(ctx.getContextId(), up.getType(), up.getIdentifier(), groups, mail, displayName, locale, tz, 10, folderId, up.getConfirm(), up.getConfirmMessage(), participant.isIgnoreNotification());
            }
            return new EmailableParticipant(ctx.getContextId(), participant.getType(), participant.getIdentifier(), groups, mail, displayName, locale, tz, 10, folderId, CalendarObject.NONE, null, participant.isIgnoreNotification());
        }
        return null;
    }

    private EmailableParticipant getResourceParticipant(final Participant participant, final ServerSession session) {
        final int[] groups = new int[0];
        String mail = null;
        String displayName = null;
        final Context ctx = session.getContext();
        try {
            final Resource resource = resolveResources(ctx, participant.getIdentifier())[0];
            mail = resource.getMail();
            if (mail == null) {
                mail = participant.getEmailAddress();
            }
            displayName = resource.getDisplayName();
            if (displayName == null) {
                displayName = participant.getDisplayName();
            }
        } catch (OXException e) {
            log(e);
        }

        Locale l;
        try {
            final User user = resolveUsers(session.getContext(), session.getUserId())[0];
            l = user.getLocale();
        } catch (OXException e) {
            // Should not happen
            LOG.warn("Could not resolve user from session: UserId: {} in Context: {}", I(session.getUserId()), I(session.getContextId()), e);
            l = Locale.getDefault();
        }

        EmailableParticipant p;
        if (mail != null) {
            p = new EmailableParticipant(ctx.getContextId(), participant.getType(), participant.getIdentifier(), groups, mail, displayName, l, TimeZone.getDefault(), -1, MailObject.DONT_SET, CalendarObject.NONE, null, participant.isIgnoreNotification());
            return p;
        }
        return null;
    }

    private void sortUserParticipants(final UserParticipant[] oldParticipants, final UserParticipant[] newParticipants, final Set<EmailableParticipant> participantSet, final boolean forUpdate, final Map<Locale, List<EmailableParticipant>> receivers, final ServerSession session, final Map<String, EmailableParticipant> all, State state) {
        if (newParticipants == null) {
            return;
        }
        final Context ctx = session.getContext();
        for (final UserParticipant participant : newParticipants) {
            final EmailableParticipant p = getUserParticipant(participant, ctx);
            if (p != null) {
                p.state = state.getType().equals(State.Type.DELETED) ? EmailableParticipant.STATE_REMOVED : contains(participant, oldParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_NEW;
                addSingleParticipant(p, participantSet, null, receivers, all, false);
            }
        }

        if (null != oldParticipants) {
            for (final UserParticipant participant : oldParticipants) {
                final EmailableParticipant p = getUserParticipant(participant, ctx);
                if (p != null) {
                    p.state = contains(participant, newParticipants) ? EmailableParticipant.STATE_NONE : EmailableParticipant.STATE_REMOVED;
                    if (forUpdate) {
                        addSingleParticipant(p, participantSet, null, receivers, all, false);
                    } else {
                        addReceiver(p, receivers, all);
                    }
                }
            }
        }

    }

    private void addTaskOwner(final CalendarObject oldTask, final CalendarObject newTask, final Map<Locale, List<EmailableParticipant>> receivers, final Map<String, EmailableParticipant> all, final ServerSession session) {
        final Context ctx = session.getContext();
        final int creatorId = newTask.getCreatedBy();
        final int folderId = null != oldTask ? oldTask.getParentFolderID() : -1;
        try {
            final User user = resolveUsers(ctx, creatorId)[0];
            final EmailableParticipant emailable = new EmailableParticipant(ctx.getContextId(), Participant.USER, creatorId, user.getGroups(), user.getMail(), user.getDisplayName(), user.getLocale(), TimeZone.getTimeZone(user.getTimeZone()), 10, folderId, CalendarObject.NONE, null, false);
            addReceiver(emailable, receivers, all);
        } catch (OXException e) {
            log(e);
        }
    }

    private void addReceiver(final EmailableParticipant participant, final Map<Locale, List<EmailableParticipant>> receivers, final Map<String, EmailableParticipant> all) {

        if (all.containsKey(participant.email)) {
            final EmailableParticipant other = all.get(participant.email);
            if (other.reliability < participant.reliability) {
                if (other.getLocale().equals(participant.getLocale())) {
                    other.copy(participant);
                    return;
                }
                final List<EmailableParticipant> p = receivers.get(other.getLocale());
                p.remove(p.indexOf(other));
            }
            return;
        }
        final Locale l = participant.getLocale();

        List<EmailableParticipant> p = receivers.get(l);
        if (p == null) {
            p = new ArrayList<EmailableParticipant>();
            receivers.put(l, p);
        }

        all.put(participant.email, participant);
        p.add(participant);

    }

    private void addSingleParticipant(final EmailableParticipant participant, final Set<EmailableParticipant> participantSet, final Set<EmailableParticipant> resourceSet, final Map<Locale, List<EmailableParticipant>> receivers, final Map<String, EmailableParticipant> all, final boolean /*
                                                                                                                                                                                                                                                                                                * HACK
                                                                                                                                                                                                                                                                                                */ resource) {
        addReceiver(participant, receivers, all);
        if (resource) {
            resourceSet.add(participant);
        } else {
            participantSet.add(participant);
        }
    }

    static final class MailMessage {

        public Type overrideType;

        /**
         * Initializes a new MailMessage
         */
        public MailMessage() {
            super();
        }

        public Object message;

        public String title;

        public List<String> addresses = new ArrayList<String>();

        public int folderId;

        public boolean internal;
    }

    /**
     * Checks if the participant is included in the participants array.
     *
     * @param toSearch - the participant to search for
     * @param participants - the array to search within
     * @return true, if the toSearch participant is included within the array. Otherwise false.
     */
    protected static final boolean contains(final Participant toSearch, final Participant[] participants) {
        if (null == participants) {
            return false;
        }
        for (final Participant participant : participants) {
            if (participant != null && participant.equals(toSearch)) {
                return true;
            }
        }
        return false;
    }

    private static final boolean compareRecurrenceInformation(final CalendarObject o1, final CalendarObject o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1.getRecurrenceType() != o2.getRecurrenceType()) {
            return false;
        }
        if (CalendarObject.DAILY == o1.getRecurrenceType()) {
            return o1.getInterval() == o2.getInterval();
        }
        if (CalendarObject.WEEKLY == o1.getRecurrenceType()) {
            if (o1.getInterval() != o2.getInterval()) {
                return false;
            }
            return o1.getDays() == o2.getDays();
        }
        if (CalendarObject.MONTHLY == o1.getRecurrenceType()) {
            if (o1.getInterval() != o2.getInterval()) {
                return false;
            }
            if (o1.getDays() != o2.getDays()) {
                return false;
            }
            return o1.getDayInMonth() == o2.getDayInMonth();
        }
        if (CalendarObject.YEARLY == o1.getRecurrenceType()) {
            if (o1.getMonth() != o2.getMonth()) {
                return false;
            }
            if (o1.getDays() != o2.getDays()) {
                return false;
            }
            return o1.getDayInMonth() == o2.getDayInMonth();
        }
        return true;
    }

    /**
     * Compares given {@link Object} references.
     *
     * @param o1 The first object
     * @param o2 The second object
     * @return <code>true</code> if both {@link Object} references are considered to be equal; otherwise <code>false</code>
     */
    static final boolean compareObjects(final Object o1, final Object o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null) {
            if (o2 == null) {
                return true;
            }
            return false;
        }
        return o1.equals(o2);
    }

    static final boolean compareDates(final Date[] dates1, final Date[] dates2) {
        if (dates1 == dates2) {
            return true;
        }
        if (dates1 == null) {
            if (dates2 == null) {
                return true;
            }
            return dates2.length == 0 ? true : false;
        }
        if (dates2 == null && dates1.length == 0) {
            return true;
        }
        return Arrays.equals(dates1, dates2);
    }

    /**
     * Compares given {@link String} references.
     * <p>
     * Note: A <code>null</code> reference and an empty string are considered to be equal. Otherwise use
     * {@link #compareObjects(Object, Object)}
     *
     * @param s1 The first string
     * @param s2 The second string
     * @return <code>true</code> if both {@link String} references are considered to be equal; otherwise <code>false</code>
     */
    static final boolean compareStrings(final String s1, final String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null) {
            if (s2 == null) {
                return true;
            }
            return s2.length() == 0 ? true : false;
        }
        if (s2 == null && s1.length() == 0) {
            return true;
        }
        return s1.trim().equals(s2 == null ? null : s2.trim());
    }

    /**
     * Checks the dates of specified calendar object.
     * <ul>
     * <li>For a single event: if it is not more than 30 minutes in the past and its end date is not in the past compared to
     * {@link System#currentTimeMillis()}.</li>
     * <li>For a recurring event: if its until date is in the past</li>
     * </ul>
     *
     * @param calendarObj The calendar object whose start and end date is ought to be checked
     * @param module The module
     * @return <code>true</code> if notifications shall be dropped; otherwise <code>false</code>.
     */
    static final boolean checkStartAndEndDate(final CalendarObject calendarObj, final int module) {
        final long now = System.currentTimeMillis();
        if (CalendarObject.NO_RECURRENCE == calendarObj.getRecurrenceType()) {
            {
                // Do not send notification mails for tasks and appointments in the
                // past. Bug #12063
                final Date endDate = calendarObj.getEndDate();
                if (endDate != null) {
                    if (Types.APPOINTMENT == module && endDate.getTime() < now) {
                        LOG.debug("Ignoring notification(s) for single appointment object {} since its end date is in the past", I(calendarObj.getObjectID()));
                        return false;
                    }
                    if (Types.TASK == module && !compare2Date(endDate.getTime(), now)) {
                        LOG.debug("Ignoring notification(s) for single task object {} since its end date is in the past", I(calendarObj.getObjectID()));
                        return false;
                    }
                }
            }
        } else {
            final Date untilDate = calendarObj.getUntil();
            if (null != untilDate) {
                if (Types.APPOINTMENT == module && untilDate.getTime() < now) {
                    LOG.debug("Ignoring notification(s) for recurring appointment object {} since its until date is in the past", I(calendarObj.getObjectID()));
                    return false;
                }
                if (Types.TASK == module && !compare2Date(untilDate.getTime(), now)) {
                    LOG.debug("Ignoring notification(s) for recurring task object {} since its until date is in the past", I(calendarObj.getObjectID()));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compares if given time milliseconds fit into the date denoted by specified date milliseconds.
     *
     * @param date The date milliseconds
     * @param millis The time milliseconds
     * @return <code>true</code> if given time milliseconds fit into the date denoted by specified date milliseconds; otherwise <code>false</code>
     */
    private static boolean compare2Date(final long date, final long millis) {
        return date >= (millis - (millis % Constants.MILLI_DAY));
    }

    /**
     * Computes the first occurence's end time.
     *
     * @param startMillis The start time in UTC milliseconds
     * @param endMillis The end time in UTC milliseconds
     * @return The first occurence's end time.
     */
    private static Date computeFirstOccurrenceEnd(final long startMillis, final long endMillis) {
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        cal.setTimeInMillis(endMillis);
        final int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        final int minutes = cal.get(Calendar.MINUTE);
        cal.setTimeInMillis(startMillis);
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
        cal.set(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    /**
     * Gets a value indicating whether the supplied notification {@link State}
     * reflects an update of the accept/decline status or not.
     *
     * @param state The {@link State} to check
     * @return <code>true</code>, if it is a status update, <code>false</code>, otherwise
     */
    private static boolean isStatusUpdate(final State state) {
        return null != state && (State.Type.ACCEPTED.equals(state.getType()) || State.Type.DECLINED.equals(state.getType()) || State.Type.TENTATIVELY_ACCEPTED.equals(state.getType()) || State.Type.NONE_ACCEPTED.equals(state.getType()));
    }

}
