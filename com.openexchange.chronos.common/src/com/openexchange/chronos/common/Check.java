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

package com.openexchange.chronos.common;

import static com.openexchange.chronos.common.CalendarUtils.filter;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.SearchStrings.lengthWithoutWildcards;
import static com.openexchange.tools.arrays.Arrays.contains;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import com.openexchange.chronos.Alarm;
import com.openexchange.chronos.AlarmAction;
import com.openexchange.chronos.AlarmField;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.Available;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.SelfProtectionFactory.SelfProtection;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.EventsResult;
import com.openexchange.chronos.service.RecurrenceData;
import com.openexchange.chronos.service.RecurrenceService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.java.Strings;

/**
 * {@link Check}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class Check {

    /**
     * Checks an event's geo location for validity.
     *
     * @param event The event to check
     * @return The passed event's geo location, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_GEO_LOCATION}
     */
    public static double[] geoLocationIsValid(Event event) throws OXException {
        double[] geo = event.getGeo();
        if (null != geo) {
            if (2 != geo.length) {
                throw CalendarExceptionCodes.INVALID_GEO_LOCATION.create(geo);
            }
            double latitude = geo[0];
            double longitude = geo[1];
            if (90 < latitude || -90 > latitude || 180 < longitude || -180 > longitude) {
                throw CalendarExceptionCodes.INVALID_GEO_LOCATION.create(geo);
            }
        }
        return geo;
    }

    /**
     * Checks an event's recurrence rule for validity.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param event The event to check
     * @return The passed event's recurrence rule, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    public static String recurrenceRuleIsValid(RecurrenceService recurrenceService, Event event) throws OXException {
        String recurrenceRule = event.getRecurrenceRule();
        if (event.containsRecurrenceRule() && null != recurrenceRule) {
            Check.recurrenceDataIsValid(recurrenceService, new DefaultRecurrenceData(recurrenceRule, event.getStartDate(), null));
        }
        return recurrenceRule;
    }

    /**
     * Checks a recurrence data object for validity.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param recurrenceData The recurrence data to check, or <code>null</code> for a no-op
     * @return The passed recurrence data, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    public static RecurrenceData recurrenceDataIsValid(RecurrenceService recurrenceService, RecurrenceData recurrenceData) throws OXException {
        if (null != recurrenceData) {
            recurrenceService.validate(recurrenceData);
        }
        return recurrenceData;
    }

    /**
     * Ensures that all recurrence identifiers are valid for a specific recurring event series, i.e. the targeted occurrences
     * are actually part of the series.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param seriesMaster The series master event providing the recurrence information
     * @param recurrenceIDs The recurrence identifier
     * @return The passed list of recurrence identifiers, after their existence was checked
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RECURRENCE_ID}
     */
    public static SortedSet<RecurrenceId> recurrenceIdsExist(RecurrenceService recurrenceService, Event seriesMaster, SortedSet<RecurrenceId> recurrenceIDs) throws OXException {
        if (null != recurrenceIDs) {
            RecurrenceData recurrenceData = new DefaultRecurrenceData(seriesMaster.getRecurrenceRule(), seriesMaster.getStartDate(), null);
            SortedSet<RecurrenceId> validRecurrenceIds = CalendarUtils.removeInvalid(recurrenceIDs, recurrenceData, recurrenceService);
            for (RecurrenceId recurrenceID : recurrenceIDs) {
                if (false == CalendarUtils.contains(validRecurrenceIds, recurrenceID)) {
                    throw CalendarExceptionCodes.INVALID_RECURRENCE_ID.create(String.valueOf(recurrenceID), recurrenceData);
                }
            }
        }
        return recurrenceIDs;
    }

    /**
     * Ensures that a specific recurrence identifier is valid for a specific recurring event series, i.e. the targeted occurrence
     * is actually part of the series.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param seriesMaster The series master event providing the recurrence information
     * @param recurrenceID The recurrence identifier
     * @return The passed recurrence identifier, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RECURRENCE_ID}
     */
    public static RecurrenceId recurrenceIdExists(RecurrenceService recurrenceService, Event seriesMaster, RecurrenceId recurrenceID) throws OXException {
        SortedSet<RecurrenceId> recurrenceIds = new TreeSet<RecurrenceId>();
        recurrenceIds.add(recurrenceID);
        return recurrenceIdsExist(recurrenceService, seriesMaster, recurrenceIds).first();
    }

    /**
     * Checks that an incoming event update has no sequence number smaller than the original event's sequence number.
     *
     * @param originalEvent The original event being updated
     * @param eventUpdate The updated event data
     * @return The passed event update, after the sequence number was checked
     * @throws OXException {@link CalendarExceptionCodes#OUT_OF_SEQUENCE}
     */
    public static Event requireInSequence(Event originalEvent, Event eventUpdate) throws OXException {
        if (eventUpdate.containsSequence() && eventUpdate.getSequence() < originalEvent.getSequence()) {
            throw CalendarExceptionCodes.OUT_OF_SEQUENCE.create(originalEvent.getId(), I(eventUpdate.getSequence()), I(originalEvent.getSequence()));
        }
        return eventUpdate;
    }

    /**
     * Checks that the folder identifier matches a specific expected folder id.
     *
     * @param folderId The folder identifier to check
     * @param expectedFolderId The expected folder id to check against
     * @return The passed folder identifier, after it was checked
     * @throws OXException {@link CalendarExceptionCodes#FOLDER_NOT_FOUND}
     */
    public static String folderMatches(String folderId, String expectedFolderId) throws OXException {
        if (false == Objects.equals(expectedFolderId, folderId)) {
            throw CalendarExceptionCodes.FOLDER_NOT_FOUND.create(folderId);
        }
        return folderId;
    }

    /**
     * Checks that the folder identifier within the supplied full event identifier matches a specific expected folder id.
     *
     * @param eventID The full event identifier to check
     * @param expectedFolderId The expected folder id to check against
     * @return The passed event identifier, after it was checked
     * @throws OXException {@link CalendarExceptionCodes#EVENT_NOT_FOUND_IN_FOLDER}
     */
    public static EventID parentFolderMatches(EventID eventID, String expectedFolderId) throws OXException {
        if (null != eventID && false == Objects.equals(expectedFolderId, eventID.getFolderID())) {
            throw CalendarExceptionCodes.EVENT_NOT_FOUND_IN_FOLDER.create(eventID.getFolderID(), eventID.getObjectID());
        }
        return eventID;
    }

    /**
     * Checks that all folder identifiers within the supplied list of full event identifiers match a specific expected folder id.
     *
     * @param eventIDs The list of full event identifiers to check
     * @param expectedFolderId The expected folder id to check against
     * @return The passed event identifiers, after all were checked
     * @throws OXException {@link CalendarExceptionCodes#EVENT_NOT_FOUND_IN_FOLDER}
     */
    public static List<EventID> parentFolderMatches(List<EventID> eventIDs, String expectedFolderId) throws OXException {
        if (null != eventIDs) {
            for (EventID eventID : eventIDs) {
                parentFolderMatches(eventID, expectedFolderId);
            }
        }
        return eventIDs;
    }

    /**
     * Checks that the supplied timezone identifier is valid, i.e. a corresponding Java timezone exists.
     *
     * @param timeZoneID The timezone identifier to check, or <code>null</code> to skip the check
     * @return The identifier of the matching timezone
     * @throws OXException {@link CalendarExceptionCodes#INVALID_TIMEZONE}
     */
    public static String timeZoneExists(String timeZoneID) throws OXException {
        TimeZone timeZone = CalendarUtils.optTimeZone(timeZoneID, null);
        if (null == timeZone) {
            throw CalendarExceptionCodes.INVALID_TIMEZONE.create(timeZoneID);
        }
        return timeZone.getID();
    }

    /**
     * Checks that a list of alarms are valid, i.e. they all contain all mandatory properties.
     *
     * @param alarms The alarms to check
     * @return The passed alarms, after they were checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_RRULE}
     */
    public static List<Alarm> alarmsAreValid(List<Alarm> alarms) throws OXException {
        if (null != alarms && 0 < alarms.size()) {
            for (Alarm alarm : alarms) {
                alarmIsValid(alarm);
            }
        }
        return alarms;
    }

    /**
     * Checks that the supplied alarm is valid, i.e. it contains all mandatory properties.
     *
     * @param alarm The alarm to check
     * @return The passed alarm, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_ALARM}
     */
    public static Alarm alarmIsValid(Alarm alarm) throws OXException {
        return alarmIsValid(alarm, null);
    }

    /**
     * Checks that the supplied alarm is valid, i.e. it contains all mandatory properties.
     *
     * @param alarm The alarm to check
     * @param fields The alarm fields to check, or <code>null</code> to check all fields
     * @return The passed alarm, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_ALARM}
     */
    public static Alarm alarmIsValid(Alarm alarm, AlarmField[] fields) throws OXException {
        /*
         * action and trigger are both required for any type of alarm
         */
        if (null == fields || contains(fields, AlarmField.ACTION)) {
            if (null == alarm.getAction() || Strings.isEmpty(alarm.getAction().getValue())) {
                Exception cause = CalendarExceptionCodes.MANDATORY_FIELD.create(AlarmField.ACTION.toString());
                throw CalendarExceptionCodes.INVALID_ALARM.create(cause, String.valueOf(alarm.getId()));
            }
        }
        if ((null == alarm.getTrigger() || null == alarm.getTrigger().getDateTime() && null == alarm.getTrigger().getDuration()) &&
            (null == fields || contains(fields, AlarmField.TRIGGER))) {
            Exception cause = CalendarExceptionCodes.MANDATORY_FIELD.create(AlarmField.TRIGGER.toString());
            throw CalendarExceptionCodes.INVALID_ALARM.create(cause, String.valueOf(alarm.getId()));
        }
        /*
         * check further properties based on alarm type
         */
        if (AlarmAction.DISPLAY.equals(alarm.getAction())) {
            if (null == alarm.getDescription() && (null == fields || contains(fields, AlarmField.DESCRIPTION))) {
                Exception cause = CalendarExceptionCodes.MANDATORY_FIELD.create(AlarmField.DESCRIPTION.toString());
                throw CalendarExceptionCodes.INVALID_ALARM.create(cause, String.valueOf(alarm.getId()));
            }
        }
        if ( AlarmAction.SMS.equals(alarm.getAction())) {
            if (!alarm.containsAttendees()) {
                Exception cause = CalendarExceptionCodes.MANDATORY_FIELD.create(AlarmField.ATTENDEES.toString());
                throw CalendarExceptionCodes.INVALID_ALARM.create(cause, String.valueOf(alarm.getId()));
            }
            for(Attendee att : alarm.getAttendees()) {
                if (!att.containsUri() || !att.getUri().toLowerCase().contains("tel:")) {
                    Exception cause = CalendarExceptionCodes.INVALID_CALENDAR_USER.create(att.getUri(), I(att.getEntity()), String.valueOf(att.getCuType()));
                    throw CalendarExceptionCodes.INVALID_ALARM.create(cause, String.valueOf(alarm.getId()));
                }
            }
        }
        return alarm;
    }

    /**
     * Checks that the supplied alarm has a <i>relative</i> trigger defined.
     *
     * @param alarm The alarm to check
     * @return The passed alarm, after it was checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_ALARM}
     */
    public static Alarm hasReleativeTrigger(Alarm alarm) throws OXException {
        if (false == AlarmUtils.hasRelativeTrigger(alarm)) {
            throw CalendarExceptionCodes.INVALID_ALARM.create(String.valueOf(alarm));
        }
        return alarm;
    }

    /**
     * Checks that each alarm in the supplied list has a <i>relative</i> trigger defined.
     *
     * @param alarms The alarms to check
     * @return The passed alarms, after they were checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_ALARM}
     */
    public static List<Alarm> haveReleativeTriggers(List<Alarm> alarms) throws OXException {
        if (null == alarms || 0 >= alarms.size()) {
            return alarms;
        }
        for (Alarm alarm : alarms) {
            if (false == AlarmUtils.hasRelativeTrigger(alarm)) {
                throw CalendarExceptionCodes.INVALID_ALARM.create(String.valueOf(alarm));
            }
        }
        return alarms;
    }

    /**
     * Checks that the supplied availability is valid, i.e. its available definitions contain all mandatory properties.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param availability The availability to check
     * @return The passed availability, after it was checked for validity
     */
    public static Available[] availabilityIsValid(RecurrenceService recurrenceService, Available[] availability) {
        if (null != availability) {
            for (int j = availability.length; j-- > 0;) {
                availability[j] = Check.availableIsValid(recurrenceService, availability[j]);
            }
        }
        return availability;
    }

    /**
     * Checks that the supplied available definition is valid, i.e. it contains all mandatory properties.
     *
     * @param recurrenceService A reference to the recurrence service
     * @param available The available to check
     * @return The passed available, after it was checked for validity
     */
    private static Available availableIsValid(RecurrenceService recurrenceService, Available available) {
        //TODO
        return available;
    }

    /**
     * Checks that the supplied calendar user's URI denotes a valid e-mail address.
     * <p/>
     * This method should only be invoked for <i>external</i> calendar users.
     *
     * @param calendarUser The (external) calendar user to check
     * @return The calendar user, after its URI has been checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_CALENDAR_USER}
     */
    public static <T extends CalendarUser> T requireValidEMail(T calendarUser) throws OXException {
        String address = CalendarUtils.extractEMailAddress(calendarUser.getUri());
        if (null == address) {
            throw CalendarExceptionCodes.INVALID_CALENDAR_USER.create(calendarUser.getUri(), I(calendarUser.getEntity()), "");
        }
        try {
            new InternetAddress(address).validate();
        } catch (AddressException e) {
            throw CalendarExceptionCodes.INVALID_CALENDAR_USER.create(e, calendarUser.getUri(), I(calendarUser.getEntity()), "");
        }
        return calendarUser;
    }

    /**
     * Checks that the supplied string represents a valid URI.
     *
     * @param uri The URI to check
     * @param field The <i>field</i> the value originates in for the error message
     * @return The URI string, after it has been checked for validity
     * @throws OXException {@link CalendarExceptionCodes#INVALID_DATA}
     */
    @SuppressWarnings("unused")
    public static String requireValidURI(String uri, String field) throws OXException {
        if (Strings.isEmpty(uri)) {
            throw CalendarExceptionCodes.INVALID_DATA.create(field, uri);
        }
        if (uri.startsWith("tel:") && 4 < uri.length()) {
            // allow any URI with "tel:" scheme
            return uri;
        }
        try {
            new URI(uri);
        } catch (URISyntaxException e) {
            throw CalendarExceptionCodes.INVALID_DATA.create(e, field, e.getMessage());
        }
        return uri;
    }

    /**
     * Checks that all specified mandatory fields are <i>set</i> and not <code>null</code> in the event.
     *
     * @param event The event to check
     * @param fields The mandatory fields
     * @throws OXException {@link CalendarExceptionCodes#MANDATORY_FIELD}
     */
    public static void mandatoryFields(Event event, EventField... fields) throws OXException {
        if (null != fields) {
            for (EventField field : fields) {
                Mapping<? extends Object, Event> mapping = EventMapper.getInstance().get(field);
                if (false == mapping.isSet(event) || null == mapping.get(event)) {
                    String readableName = String.valueOf(field); //TODO i18n
                    throw CalendarExceptionCodes.MANDATORY_FIELD.create(readableName, String.valueOf(field));
                }
            }
        }
    }

    /**
     * Checks that the supplied search pattern length is equal to or greater than a configured minimum.
     *
     * @param minimumPatternLength The minimum search pattern length, or <code>0</code> for no limitation
     * @param pattern The pattern to check
     * @return The passed pattern, after the length was checked
     * @throws OXException {@link CalendarExceptionCodes#QUERY_TOO_SHORT}
     */
    public static String minimumSearchPatternLength(String pattern, int minimumPatternLength) throws OXException {
        if (null != pattern && 0 < minimumPatternLength && lengthWithoutWildcards(pattern) < minimumPatternLength) {
            throw CalendarExceptionCodes.QUERY_TOO_SHORT.create(I(minimumPatternLength), pattern);
        }
        return pattern;
    }

    /**
     * Checks that each of the supplied search patterns length is equal to or greater than a configured minimum.
     *
     * @param minimumPatternLength The minimum search pattern length, or <code>0</code> for no limitation
     * @param patterns The patterns to check
     * @return The passed patterns, after their length was checked
     * @throws OXException {@link CalendarExceptionCodes#QUERY_TOO_SHORT}
     */
    public static List<String> minimumSearchPatternLength(List<String> patterns, int minimumPatternLength) throws OXException {
        if (null != patterns && 0 < minimumPatternLength) {
            for (String pattern : patterns) {
                Check.minimumSearchPatternLength(pattern, minimumPatternLength);
            }
        }
        return patterns;
    }

    /**
     * Checks that the size of an event collection does not exceed the maximum allowed size.
     *
     * @param selfProtection A reference to the self protection helper
     * @param events The collection of events to check
     * @param requestedFields The requested fields, or <code>null</code> if all event fields were requested
     * @return The passed collection, after the size was checked
     */
    public static <T extends Collection<Event>> T resultSizeNotExceeded(SelfProtection selfProtection, T events, EventField[] requestedFields) throws OXException {
        if (null == events) {
            return null;
        }
        selfProtection.checkEventCollection(events, requestedFields);
        return events;
    }

    /**
     * Checks that the size of an event collection does not exceed the maximum allowed size.
     *
     * @param selfProtection A reference to the self protection helper
     * @param eventsResults The event results map to check
     * @param requestedFields The requested fields, or <code>null</code> if all event fields were requested
     * @return The passed event result map, after the size was checked
     */
    public static <K> Map<K, ? extends EventsResult> resultSizeNotExceeded(SelfProtection selfProtection, Map<K, ? extends EventsResult> eventsResults, EventField[] requestedFields) throws OXException {
        if (null == eventsResults) {
            return null;
        }
        selfProtection.checkEventResults(eventsResults, requestedFields);
        return eventsResults;
    }

    /**
     * Checks that the given event contains no attendees fulfilling certain criteria.
     *
     * @param event The event to check the attendees in
     * @param internal {@link Boolean#TRUE} to prevent internal entities, {@link Boolean#FALSE} to prevent non-internal ones,
     *            or <code>null</code> to not check against internal/external
     * @param cuTypes The {@link CalendarUserType}s to prevent, or <code>null</code> to not check the calendar user type
     * @return The passed event, after checking that no attendees matching the criteria are contained
     * @throws OXException {@link CalendarExceptionCodes#INVALID_CALENDAR_USER}
     * @see CalendarUtils#filter(List, Boolean, CalendarUserType...)
     */
    public static Event containsNoSuchAttendees(Event event, Boolean internal, CalendarUserType... cuTypes) throws OXException {
        List<Attendee> invalidAttendees = filter(event.getAttendees(), internal, cuTypes);
        if (0 < invalidAttendees.size()) {
            Attendee attendee = invalidAttendees.get(0);
            throw CalendarExceptionCodes.INVALID_CALENDAR_USER.create(attendee.getUri(), I(attendee.getEntity()), attendee.getCuType());
        }
        return event;
    }

}
