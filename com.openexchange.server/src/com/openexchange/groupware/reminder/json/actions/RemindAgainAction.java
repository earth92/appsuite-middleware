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

package com.openexchange.groupware.reminder.json.actions;

import static com.openexchange.tools.TimeZoneUtils.getTimeZone;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.parser.ReminderParser;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.ajax.writer.ReminderWriter;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmField;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.RelatedTo;
import com.openexchange.chronos.Trigger;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultRecurrenceData;
import com.openexchange.chronos.common.mapping.AlarmMapper;
import com.openexchange.chronos.compat.Event2Appointment;
import com.openexchange.chronos.service.CalendarResult;
import com.openexchange.chronos.service.CalendarService;
import com.openexchange.chronos.service.CalendarServiceUtilities;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.RecurrenceData;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.Types;
import com.openexchange.groupware.reminder.ReminderExceptionCode;
import com.openexchange.groupware.reminder.ReminderObject;
import com.openexchange.groupware.reminder.ReminderService;
import com.openexchange.groupware.reminder.json.ReminderAJAXRequest;
import com.openexchange.java.Strings;
import com.openexchange.server.ServiceLookup;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link RemindAgainAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractReminderAction.MODULE, type = RestrictedAction.Type.WRITE)
public final class RemindAgainAction extends AbstractReminderAction {

    /**
     * Initializes a new {@link RemindAgainAction}.
     *
     * @param services
     */
    public RemindAgainAction(final ServiceLookup services) {
        super(services);
    }

    @Override
    protected AJAXRequestResult perform(final ReminderAJAXRequest req) throws OXException, JSONException {
        // timestamp = DataParser.checkDate(jsonObject, AJAXServlet.PARAMETER_TIMESTAMP);
        final long longId = req.checkLong(AJAXServlet.PARAMETER_ID);
        final JSONObject jreminder = req.getData();
        final TimeZone tz = req.getTimeZone();
        final TimeZone timeZone;
        {
            final String timeZoneId = req.getParameter(AJAXServlet.PARAMETER_TIMEZONE);
            timeZone = null == timeZoneId ? tz : getTimeZone(timeZoneId);
        }
        final ReminderWriter reminderWriter = new ReminderWriter(timeZone);

        if (longId > Integer.MAX_VALUE){

            // reminder is an event reminder
            int alarmId = (int) (longId >> 32);
            int eventIdInt = (int) longId;
            CalendarService calendarService = ServerServiceRegistry.getInstance().getService(CalendarService.class);
            CalendarSession calendarSession = calendarService.init(req.getSession());
            CalendarServiceUtilities calendarServiceUtilities = calendarService.getUtilities();
            Event event = calendarServiceUtilities.resolveByID(calendarSession, String.valueOf(eventIdInt), null);
            List<Alarm> alarms = event.getAlarms();
            Alarm alarmToSnooze = null;
            for (Alarm alarm : alarms) {
                if (alarm.getId() == alarmId) {
                    alarm.setAcknowledged(new Date());
                    alarmToSnooze = alarm;
                    break;
                }
            }
            EventID eventId = null;
            if (event.containsRecurrenceId()) {
                eventId = new EventID(event.getFolderId(), event.getId(), event.getRecurrenceId());
            } else {
                eventId = new EventID(event.getFolderId(), event.getId());
            }

            if (alarmToSnooze == null) {
                throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(AJAXServlet.PARAMETER_ID, "Unable to find an alarm with id " + alarmId);
            }

            final ReminderObject reminder = new ReminderObject();
            new ReminderParser(tz).parse(reminder, jreminder);

            Alarm snoozeAlarm = AlarmMapper.getInstance().copy(alarmToSnooze, null, (AlarmField[]) null);
            Trigger trigger = new Trigger(reminder.getDate());
            snoozeAlarm.setTrigger(trigger);
            String uid = alarmToSnooze.getUid();
            if (Strings.isEmpty(uid)) {
                uid = UUID.randomUUID().toString().toUpperCase();
                alarmToSnooze.setUid(uid);
            }
            snoozeAlarm.setRelatedTo(new RelatedTo("SNOOZE", uid));
            snoozeAlarm.removeUid();
            snoozeAlarm.removeId();
            snoozeAlarm.removeAcknowledged();

            alarms.add(snoozeAlarm);

            // Remove old snooze in case it was snoozed again
            if (alarmToSnooze.getRelatedTo() != null && alarmToSnooze.getRelatedTo().getRelType().equals("SNOOZE")) {
                alarms.remove(alarmToSnooze);
            }

            CalendarResult updateAlarms = calendarService.updateAlarms(calendarSession, eventId, alarms, event.getTimestamp());
            Alarm result = snoozeAlarm;
            if (updateAlarms.getUpdates() != null && updateAlarms.getUpdates().size() == 1){
                result = updateAlarms.getUpdates().get(0).getAlarmUpdates().getUpdatedItems().get(0).getUpdate();
            }
            return new AJAXRequestResult(this.createResponse(reminder.getDate(), event, result, calendarService, calendarSession, reminderWriter), event.getLastModified(), "json");
        }

        final int reminderId = (int) longId;
        /*
         * Parse reminder from JSON
         */
        final ReminderObject reminder = new ReminderObject();
        new ReminderParser(tz).parse(reminder, jreminder);
        if (null == reminder.getDate()) {
            throw ReminderExceptionCode.MANDATORY_FIELD_ALARM.create();
        }
        reminder.setObjectId(reminderId);
        /*
         * Load storage version and check permission
         */
        final ServerSession session = req.getSession();
        final ReminderService reminderService = ServerServiceRegistry.getInstance().getService(ReminderService.class, true);
        {
            final ReminderObject storageReminder = reminderService.loadReminder(session, reminder.getObjectId());
            /*
             * Check module permission
             */
            if (!hasModulePermission(storageReminder, session)) {
                throw ReminderExceptionCode.UNEXPECTED_ERROR.create("No module permission.");
            }
            /*
             * Set other fields
             */
            reminder.setModule(storageReminder.getModule());
            reminder.setDescription(storageReminder.getDescription());
            reminder.setFolder(storageReminder.getFolder());
            reminder.setTargetId(storageReminder.getTargetId());
            reminder.setUser(storageReminder.getUser());
        }
        /*
         * Trigger action
         */
        reminderService.remindAgain(reminder, session, session.getContext());
        final Date timestamp = reminder.getLastModified();
        /*
         * Write updated reminder
         */
        final JSONObject jsonReminderObj = new JSONObject();
        reminderWriter.writeObject(reminder, jsonReminderObj);
        return new AJAXRequestResult(jsonReminderObj, timestamp, "json");
    }


    private JSONObject createResponse(Date time, Event event, Alarm alarm, CalendarService calService, CalendarSession calSession, ReminderWriter reminderWriter) throws JSONException, OXException {
        final JSONObject jsonReminderObj = new JSONObject(12);

        ReminderObject reminder = new ReminderObject();
        reminder.setDate(time);
        reminder.setLastModified(event.getLastModified());
        reminder.setFolder(Integer.parseInt(event.getFolderId()));
        reminder.setModule(Types.APPOINTMENT);
        reminder.setUser(calSession.getUserId());
        reminder.setObjectId(Integer.parseInt(event.getId())); // AlarmTrigger don't have an id
        reminder.setTargetId(alarm.getId());

        if (CalendarUtils.isSeriesMaster(event)) {
            SortedSet<RecurrenceId> exceptions = event.getDeleteExceptionDates();
            List<Event> changeExceptions = calService.getChangeExceptions(calSession, event.getFolderId(), event.getSeriesId());
            for (Event exception : changeExceptions) {
                exceptions.add(exception.getRecurrenceId());
            }
            long[] exceptionDates = new long[exceptions.size()];
            int x = 0;
            for (RecurrenceId id : exceptions) {
                exceptionDates[x++] = id.getValue().getTimestamp();
            }
            RecurrenceData data = new DefaultRecurrenceData(event.getRecurrenceRule(), event.getStartDate(), exceptionDates);

            RecurrenceService recurrenceService = ServerServiceRegistry.getInstance().getService(RecurrenceService.class);
            int pos = Event2Appointment.getRecurrencePosition(recurrenceService, data, event.getRecurrenceId());
            reminder.setRecurrencePosition(pos);
            reminder.setRecurrenceAppointment(true);
        } else {
            reminder.setRecurrenceAppointment(false);
        }

        reminder.setDescription(alarm.getDescription());

        reminderWriter.writeObject(reminder, jsonReminderObj);
        return jsonReminderObj;
    }

}
