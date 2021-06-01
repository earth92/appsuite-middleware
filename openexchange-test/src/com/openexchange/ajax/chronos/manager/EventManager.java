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

package com.openexchange.ajax.chronos.manager;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.NOT;
import static com.openexchange.java.Autoboxing.l;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import com.openexchange.ajax.chronos.UserApi;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.chronos.service.SortOrder;
import com.openexchange.java.Strings;
import com.openexchange.test.common.asset.Asset;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.AlarmTrigger;
import com.openexchange.testing.httpclient.models.AlarmTriggerData;
import com.openexchange.testing.httpclient.models.AlarmTriggerResponse;
import com.openexchange.testing.httpclient.models.AttendeeAndAlarm;
import com.openexchange.testing.httpclient.models.CalendarResult;
import com.openexchange.testing.httpclient.models.CalendarUser;
import com.openexchange.testing.httpclient.models.ChangeOrganizerBody;
import com.openexchange.testing.httpclient.models.ChronosAttachment;
import com.openexchange.testing.httpclient.models.ChronosCalendarResultResponse;
import com.openexchange.testing.httpclient.models.ChronosErrorAwareCalendarResult;
import com.openexchange.testing.httpclient.models.ChronosMultipleCalendarResultResponse;
import com.openexchange.testing.httpclient.models.ChronosUpdatesResponse;
import com.openexchange.testing.httpclient.models.DeleteEventBody;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.EventId;
import com.openexchange.testing.httpclient.models.EventResponse;
import com.openexchange.testing.httpclient.models.EventsResponse;
import com.openexchange.testing.httpclient.models.UpdateEventBody;
import com.openexchange.testing.httpclient.models.UpdatesResult;

/**
 * {@link EventManager}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 */
public class EventManager extends AbstractManager {

    public enum RecurrenceRange {
        THISANDFUTURE,
        THISANDPRIOR,
    }

    private final UserApi userApi;
    private final String defaultFolder;

    private List<EventId> eventIds;
    private long lastTimeStamp;
    private boolean ignoreConflicts;

    private static final Boolean EXPAND_SERIES = Boolean.FALSE;

    private static final SimpleDateFormat UTC_DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    /**
     * Initializes a new {@link EventManager}.
     *
     * @param userApi The {@link UserApi}
     * @param defaultFolder The default Folder
     */
    public EventManager(UserApi userApi, String defaultFolder) {
        super();
        this.userApi = userApi;
        this.defaultFolder = defaultFolder;
        eventIds = new ArrayList<>();
        ignoreConflicts = false;
    }

    /**
     * Removes all events that were created with this event manager during the session
     */
    public void cleanUp() {
        try {
        	DeleteEventBody body = new DeleteEventBody();
            body.setEvents(eventIds);
            userApi.getChronosApi().deleteEvent(L(System.currentTimeMillis()), body, null, null, EXPAND_SERIES, Boolean.FALSE, null, null, null);
        } catch (Exception e) {
            System.err.println("Could not clean up the events for user " + userApi.getCalUser() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates an event and does <b>NOT</b> ignore conflicts
     *
     * @param eventData The data of the event
     * @return The created {@link EventData}
     * @throws ApiException if an API error is occurred
     */
    public EventData createEvent(EventData eventData) throws ApiException {
        return createEvent(eventData, ignoreConflicts);
    }

    /**
     * Creates an event
     *
     * @param eventData The data of the event
     * @param ignoreConflicts <code>true</code> to ignore conflicts. If set to <code>false</code> conflicts will be checked and throw an appropriated error
     * @return The created {@link EventData}
     * @throws ApiException if an API error is occurred
     */
    public EventData createEvent(EventData eventData, boolean ignoreConflicts) throws ApiException {
        ChronosCalendarResultResponse createEvent = userApi.getChronosApi().createEvent(getFolder(eventData), eventData, B(ignoreConflicts == false), null, Boolean.FALSE, null, null, null, EXPAND_SERIES, null);
        EventData event = handleCreation(createEvent);
        return event;
    }

    /**
     * Creates an event and attaches the specified {@link Asset}
     *
     * @param eventData The {@link EventData}
     * @param asset The {@link Asset} to attach
     * @return The created {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public JSONObject createEventWithAttachment(EventData eventData, Asset asset) throws ApiException, ChronosApiException {
        return createEventWithAttachment(eventData, asset, false);
    }

    /**
     * Creates an event and attaches the specified {@link Asset}
     *
     * @param eventData The {@link EventData}
     * @param asset The {@link Asset} to attach
     * @param expectException flag to indicate that an exception is expected
     * @return The created {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public JSONObject createEventWithAttachment(EventData eventData, Asset asset, boolean expectException) throws ApiException, ChronosApiException {
        String response = userApi.getEnhancedChronosApi().createEventWithAttachments(getFolder(eventData), eventData.toJson(), new File(asset.getAbsolutePath()), NOT(ignoreConflicts), null, Boolean.FALSE, null);
        JSONObject responseData = extractBody(response);
        if (expectException) {
            assertNotNull("An error was expected", responseData.optString("error"));
            throw new ChronosApiException(responseData.optString("code"), responseData.optString("error"));
        }
        return handleCreation(response);
    }

    /**
     * Creates an event and attaches the specified {@link Asset}s
     *
     * @param eventData The {@link EventData}
     * @param assets The {@link Asset}s to attach
     * @return The created {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException On JSON errors
     */
    public JSONObject createEventWithAttachments(EventData eventData, List<Asset> assets) throws ApiException, ChronosApiException {
        List<File> files = new ArrayList<>();
        for (Asset asset : assets) {
            files.add(new File(asset.getAbsolutePath()));
        }
        return handleCreation(userApi.getEnhancedChronosApi().createEventWithAttachments(getFolder(eventData), eventData.toJson(), files, NOT(ignoreConflicts), null));
    }

    /**
     * Update the specified event and attach the specified {@link Asset}
     *
     * @param eventData The event
     * @param asset The {@link Asset} to attach
     * @return The updated {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException On JSON errors
     */
    public JSONObject updateEventWithAttachment(EventData eventData, Asset asset) throws ApiException, ChronosApiException {
        prepareEventAttachment(eventData, asset);
        return handleUpdate(userApi.getEnhancedChronosApi().updateEventWithAttachments(getFolder(eventData), eventData.getId(), eventData.getLastModified(), eventData.toJson(), new File(asset.getAbsolutePath()), null, NOT(ignoreConflicts), null, Boolean.FALSE, null));
    }

    /**
     * Update the specified event and attach the specified {@link Asset}. Notifies attendees of the event.
     *
     * @param eventData The event
     * @param asset The {@link Asset} to attach
     * @param comment The comment to set in the notification mail
     * @return The updated {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException On JSON errors
     */
    public JSONObject updateEventWithAttachmentAndNotification(EventData eventData, Asset asset, String comment) throws ApiException, ChronosApiException {
        prepareEventAttachment(eventData, asset);
        StringBuilder sb = new StringBuilder();
        if (Strings.isNotEmpty(comment)) {
            sb.append("{\"event\":");
            sb.append(eventData.toJson());
            sb.append(", ");
            sb.append("\"comment\":");
            sb.append("\"").append(comment).append("\"");
            sb.append("}");
        } else {
            sb.append(eventData.toJson());
        }
        return handleUpdate(userApi.getEnhancedChronosApi().updateEventWithAttachments(getFolder(eventData), eventData.getId(), eventData.getLastModified(), sb.toString(), new File(asset.getAbsolutePath()), null, NOT(ignoreConflicts), null, Boolean.FALSE, null));
    }

    private void prepareEventAttachment(EventData eventData, Asset asset) {
        if (eventData.getAttachments() == null || eventData.getAttachments().isEmpty()) {
            ChronosAttachment attachment = new ChronosAttachment();
            attachment.setFilename(asset.getFilename());
            attachment.setFmtType(asset.getAssetType().name());
            attachment.setUri("cid:file_0");
            eventData.setAttachments(Collections.singletonList(attachment));
        }
    }

    private String getFolder(EventData eventData) {
        return eventData.getFolder() == null ? defaultFolder : eventData.getFolder();
    }

    /**
     * Get an event
     *
     * @param folderId The folder identifier
     * @param eventId The {@link EventId}
     * @return The event as {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     * @throws ApiException if API error occurred
     */
    public EventData getEvent(String folderId, String eventId) throws ApiException, ChronosApiException {
        return getEvent(folderId, eventId, false);
    }
    
    /**
     * Get an event
     *
     * @param folderId The folder identifier
     * @param eventId The {@link EventId}
     * @param fields The fields to return
     * @return The event as {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     * @throws ApiException if API error occurred
     */
    public EventData getEvent(String folderId, String eventId, String fields) throws ApiException, ChronosApiException {
        return getRecurringEvent(folderId, eventId, null, fields, false, false);
    }

    public EventData getEvent(String folderId, String eventId, String recurrenceId, boolean expectException) throws ApiException, ChronosApiException {
        EventResponse eventsResponse = userApi.getChronosApi().getEvent(eventId, null == folderId ? defaultFolder : folderId, recurrenceId, null, null);
        if (expectException) {
            assertNotNull("An error was expected", eventsResponse.getError());
            throw new ChronosApiException(eventsResponse.getCode(), eventsResponse.getError());
        }
        assertNull(eventsResponse.getError());
        setLastTimeStamp(eventsResponse.getTimestamp());
        return eventsResponse.getData();
    }

    /**
     * Get an event
     *
     * @param folder The folder
     * @param eventId The {@link EventId}
     * @param expectException flag to indicate that an exception is expected
     * @return the {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData getEvent(String folder, String eventId, boolean expectException) throws ApiException, ChronosApiException {
        return getRecurringEvent(folder, eventId, null, expectException);
    }

    /**
     * Gets the occurrence of an event
     *
     * @param folder The folder id or null
     * @param eventId The {@link EventId}
     * @param reccurenceId The recurrence identifier
     * @param expectException flag to indicate that an exception is expected
     * @return the {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData getRecurringEvent(String folder, String eventId, String reccurenceId, boolean expectException) throws ApiException, ChronosApiException {
        return getRecurringEvent(folder != null ? folder : defaultFolder, eventId, reccurenceId, expectException, false);
    }

    /**
     * Gets the occurrence of an event
     *
     * @param folder The folder or null
     * @param eventId The {@link EventId}
     * @param reccurenceId The recurrence identifier
     * @param expectException flag to indicate that an exception is expected
     * @param extendedEntities Whether attendees should be extended with contact field or not
     * @return the {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData getRecurringEvent(String folder, String eventId, String reccurenceId, boolean expectException, boolean extendedEntities) throws ApiException, ChronosApiException {
        return getRecurringEvent(folder, eventId, reccurenceId, null, expectException, extendedEntities);
    }
    
    /**
     * Gets the occurrence of an event
     *
     * @param folder The folder or null
     * @param eventId The {@link EventId}
     * @param reccurenceId The recurrence identifier
     * @param fields The fields to return
     * @param expectException flag to indicate that an exception is expected
     * @param extendedEntities Whether attendees should be extended with contact field or not
     * @return the {@link EventData}
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData getRecurringEvent(String folder, String eventId, String reccurenceId, String fields, boolean expectException, boolean extendedEntities) throws ApiException, ChronosApiException {
        EventResponse eventsResponse = userApi.getChronosApi().getEvent(eventId, folder != null ? folder : defaultFolder, reccurenceId, fields, B(extendedEntities));
        if (expectException) {
            assertNotNull("An error was expected", eventsResponse.getError());
            throw new ChronosApiException(eventsResponse.getCode(), eventsResponse.getError());
        }
        checkResponse(eventsResponse.getError(), eventsResponse.getError(), eventsResponse.getCategories(), eventsResponse.getData());
        setLastTimeStamp(eventsResponse.getTimestamp());
        return eventsResponse.getData();
    }

    /**
     * Shifts a given event by the given amount
     *
     * @param eventId The event id
     * @param recurrence The recurrence id or null
     * @param event The event data to change
     * @param startTime The start time of the event
     * @param unit The unit of the shift
     * @param value The shifting amount
     * @param timestamp The timestamp of the last request
     * @return The {@link CalendarResult}
     * @throws ApiException if an API error is occurred
     */
    public CalendarResult shiftEvent(String eventId, String recurrence, EventData event, Calendar startTime, TimeUnit unit, int value, Long timestamp) throws ApiException {
        Calendar newStartTime = Calendar.getInstance(startTime.getTimeZone());
        newStartTime.setTimeInMillis(startTime.getTimeInMillis() + unit.toMillis(value));
        event.setStartDate(DateTimeUtil.getDateTime(newStartTime));

        Calendar endTime = Calendar.getInstance(startTime.getTimeZone());
        endTime.setTimeInMillis(startTime.getTimeInMillis());
        endTime.add(Calendar.HOUR, 1);
        event.setEndDate(DateTimeUtil.getDateTime(endTime));
		UpdateEventBody body = new UpdateEventBody();
        body.setEvent(event);
        ChronosCalendarResultResponse updateEvent = userApi.getChronosApi().updateEvent(getFolder(event), eventId, timestamp == null ? L(lastTimeStamp) : timestamp, body, recurrence, null, NOT(ignoreConflicts), null, Boolean.FALSE, null, null, null, null, EXPAND_SERIES, null);
        assertNull(updateEvent.getErrorDesc(), updateEvent.getError());
        assertNotNull("Missing timestamp", updateEvent.getTimestamp());
        setLastTimeStamp(updateEvent.getTimestamp());
        return checkResponse(updateEvent.getError(), updateEvent.getErrorDesc(), updateEvent.getCategories(), updateEvent.getData());
    }

    /**
     * Retrieves the attachment of the specified event
     *
     * @param eventId The event identifier
     * @param attachmentId The attachment's identifier
     * @return The binary data of the attachment
     * @throws ApiException if an API error is occurred
     */
    public byte[] getAttachment(String eventId, int attachmentId) throws ApiException {
        return getAttachment(eventId, attachmentId, defaultFolder);
    }

    /**
     * Retrieves the attachment of the specified event
     *
     * @param eventId The event identifier
     * @param attachmentId The attachment's identifier
     * @param folderId The folder id
     * @return The binary data of the attachment
     * @throws ApiException if an API error is occurred
     */
    public byte[] getAttachment(String eventId, int attachmentId, String folderId) throws ApiException {
        byte[] eventAttachment = userApi.getChronosApi().getEventAttachment(eventId, folderId, I(attachmentId));
        assertNotNull(eventAttachment);
        return eventAttachment;
    }

    /**
     * Retrieves the attachment of the specified event
     *
     * @param eventId The event identifier
     * @param attachmentId The attachment's identifier
     * @param folderId The folder id
     * @return The binary data of the attachment
     * @throws ApiException if an API error is occurred
     */
    public byte[] getZippedAttachments(String eventId, String folderId, int... attachmentIds) throws ApiException {
        if (null == attachmentIds || attachmentIds.length <= 0) {
            throw new ApiException("Missing attachment identifiers");
        }
        List<String> attachIds = new ArrayList<String>(attachmentIds.length);
        for (int attachmentId : attachmentIds) {
            attachIds.add(Integer.toString(attachmentId));
        }
        byte[] zippedAttachments = userApi.getChronosApi().getZippedEventAttachments(eventId, folderId, attachIds);
        assertNotNull(zippedAttachments);
        return zippedAttachments;
    }

    /**
     * Retrieves all events with in the specified interval (occurrences will not be expanded)
     *
     * @param from The starting date
     * @param until The ending date
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> getAllEvents(Date from, Date until) throws ApiException {
        return getAllEvents(defaultFolder, from, until, false);
    }

    /**
     * Retrieves all events within the specified interval
     *
     * @param folder The folder
     * @param from The starting date
     * @param until The ending date
     * @param expand Flag to expand the occurrences
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> getAllEvents(String folder, Date from, Date until, boolean expand) throws ApiException {
        return getAllEvents(from, until, expand, folder == null ? defaultFolder : folder);
    }

    /**
     * Retrieves all events within the specified interval in the specified folder
     *
     * @param from The starting date
     * @param until The ending date
     * @param expand Flag to expand occurrences
     * @param folder The folder identifier
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> getAllEvents(Date from, Date until, boolean expand, String folder) throws ApiException {
        return getAllEvents(from, until, expand, folder, null);
    }

    /**
     * Retrieves all events within the specified interval in the specified folder
     *
     * @param from The starting date
     * @param until The ending date
     * @param expand Flag to expand occurrences
     * @param folder The folder identifier
     * @param sortOrder The sortOder of the events
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> getAllEvents(Date from, Date until, boolean expand, String folder, SortOrder sortOrder) throws ApiException {
        return getAllEvents(from, until, expand, folder, sortOrder, null);
    }

    /**
     * Retrieves all events within the specified interval in the specified folder
     *
     * @param from The starting date
     * @param until The ending date
     * @param expand Flag to expand occurrences
     * @param folder The folder identifier
     * @param sortOrder The sortOder of the events
     * @param fields The considered event fields
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> getAllEvents(Date from, Date until, boolean expand, String folder, SortOrder sortOrder, String fields) throws ApiException {
        return getAllEvents(from, until, expand, folder, sortOrder, fields, true);
    }

    public List<EventData> getAllEvents(Date from, Date until, boolean expand, String folder, SortOrder sortOrder, String fields, boolean extendedEntities) throws ApiException {
        String sort = null;
        String order = null;
        if (sortOrder != null) {
            sort = sortOrder.getBy().name();
            order = sortOrder.isDescending() ? SortOrder.Order.DESC.name() : SortOrder.Order.ASC.name();
        }
        EventsResponse eventsResponse = userApi.getChronosApi().getAllEvents(DateTimeUtil.getZuluDateTime(from.getTime()).getValue(), DateTimeUtil.getZuluDateTime(until.getTime()).getValue(), folder, fields, order, sort, B(expand), B(extendedEntities), Boolean.FALSE);
        if (eventsResponse.getTimestamp() != null) {
            lastTimeStamp = eventsResponse.getTimestamp().longValue();
        }
        return checkResponse(eventsResponse.getErrorDesc(), eventsResponse.getError(), eventsResponse.getCategories(), eventsResponse.getData());
    }

    /**
     * Lists the events with the specified identifiers
     *
     * @param ids The event identifiers
     * @return A {@link List} with {@link EventData}
     * @throws ApiException if an API error occurs
     */
    public List<EventData> listEvents(List<EventId> ids) throws ApiException {
        EventsResponse listResponse = userApi.getChronosApi().getEventList(ids, null, Boolean.FALSE);
        return checkResponse(listResponse.getErrorDesc(), listResponse.getError(), listResponse.getCategories(), listResponse.getData());
    }

    /**
     * Deletes the event with the specified identifier
     *
     * @param event The event to delete
     * @param folderId The folder to delete the event from, optional, can be <code>null</code> to use the default folder
     * @throws ApiException if an API error is occurred
     */
    public void deleteEvent(EventData event, String folderId) throws ApiException {
        EventId eventId = new EventId();
        eventId.setFolder(null == folderId ? defaultFolder : folderId);
        eventId.setId(event.getId());
        eventId.setRecurrenceId(event.getRecurrenceId());
        deleteEvent(eventId);
    }

    /**
     * Deletes the event with the specified identifier
     *
     * @param eventId The {@link EventId}
     * @throws ApiException if an API error is occurred
     */
    public void deleteEvent(EventId eventId) throws ApiException {
        deleteEvent(eventId, System.currentTimeMillis(), true);
    }

    /**
     * Deletes the event with the specified identifier
     *
     * @param eventId The {@link EventId}
     * @param timestamp Timestamp of the last update of the events.
     * @param checkForResultErrors Checks if the calendar results contain any errors
     * @throws ApiException if an API error is occurred
     */
    public void deleteEvent(EventId eventId, long timestamp, boolean checkForResultErrors) throws ApiException {
        DeleteEventBody body = new DeleteEventBody();
        body.addEventsItem(eventId);
        ChronosMultipleCalendarResultResponse deleteResponse = userApi.getChronosApi().deleteEvent(L(timestamp), body, null, null, EXPAND_SERIES, Boolean.FALSE, null, null, null);
        assertNull(deleteResponse.getErrorDesc(), deleteResponse.getError());
        if (checkForResultErrors) {
            checkForErrors(deleteResponse.getData());
        }
        forgetEventId(eventId);
        setLastTimeStamp(deleteResponse.getTimestamp());
    }

    /**
     * Checks if the list of results contains any errors
     *
     * @param list The list to check
     */
    private void checkForErrors(List<ChronosErrorAwareCalendarResult> list) {
        for (ChronosErrorAwareCalendarResult result : list) {
            String message = result.getError() != null ? result.getError().getErrorDesc() : null;
            assertNull(message, result.getError());
        }
    }

    /**
     * Updates the specified event and ignores conflicts
     *
     * @param eventData The data of the event
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData updateEvent(EventData eventData) throws ApiException, ChronosApiException {
        return updateEvent(eventData, false, false);
    }

    /**
     * Updates the specified event and ignores conflicts
     *
     * @param eventData The data of the event
     * @param expectException Whether an exception is expected or not
     * @param checkconflicts Whether to check for conflicts or not
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData updateEvent(EventData eventData, boolean expectException, boolean checkconflicts) throws ApiException, ChronosApiException {
        UpdateEventBody body = new UpdateEventBody();
        body.setEvent(eventData);
        ChronosCalendarResultResponse updateResponse = userApi.getChronosApi().updateEvent(getFolder(eventData), eventData.getId(), L(lastTimeStamp), body, null, null, B(checkconflicts), null, Boolean.FALSE, null, null, null, null, EXPAND_SERIES, null);
        return handleUpdate(updateResponse, expectException);
    }

    /**
     * Updates the specified recurrence event and ignores conflicts
     *
     * @param eventData The data of the event
     * @param recurrenceId the recurrence identifier
     * @param ignoreConflicts Whether to ignore conflicts or not
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData updateOccurenceEvent(EventData eventData, String recurrenceId, boolean ignoreConflicts) throws ApiException, ChronosApiException {
        return updateOccurenceEvent(eventData, recurrenceId, false, ignoreConflicts);
    }

    /**
     * Updates the specified recurrence event and ignores conflicts
     *
     * @param eventData The data of the event
     * @param recurrenceId the recurrence identifier
     * @param expectException Whether an exception is expected or not
     * @param ignoreConflicts Whether to ignore conflicts or not
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData updateOccurenceEvent(EventData eventData, String recurrenceId, boolean expectException, boolean ignoreConflicts) throws ApiException, ChronosApiException {
        return updateOccurenceEvent(eventData, recurrenceId, null, expectException, ignoreConflicts);
    }

    /**
     * Updates the specified recurrence event and ignores conflicts
     *
     * @param eventData The data of the event
     * @param recurrenceId the recurrence identifier
     * @param recurrenceRange The {@link RecurrenceRange} to use
     * @param expectException Whether an exception is expected or not
     * @param ignoreConflicts Whether to ignore conflicts or not
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData updateOccurenceEvent(EventData eventData, String recurrenceId, RecurrenceRange recurrenceRange, boolean expectException, boolean ignoreConflicts) throws ApiException, ChronosApiException {
        UpdateEventBody body = new UpdateEventBody();
        body.setEvent(eventData);
        ChronosCalendarResultResponse updateResponse = userApi.getChronosApi().updateEvent(getFolder(eventData), eventData.getId(), L(this.lastTimeStamp), body, recurrenceId, null == recurrenceRange ? null : recurrenceRange.name(), NOT(ignoreConflicts), null, Boolean.FALSE, null, null, null, null, EXPAND_SERIES, null);
        return handleUpdate(updateResponse, expectException);
    }

    /**
     * Updates an organizer on the given event
     *
     * @param eventData The data of the event
     * @param organizer The new organizer to set
     * @param comment An optional comment to send to the attendees
     * @param expectException <code>true</code> if the action should have caused an exception
     * @return The updated event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public EventData changeEventOrganizer(EventData eventData, CalendarUser organizer, String comment, boolean expectException) throws ApiException, ChronosApiException {
        String folder = getFolder(eventData);
        ChronosCalendarResultResponse updateResponse = userApi.getChronosApi().changeOrganizer(folder, eventData.getId(), L(this.lastTimeStamp), new ChangeOrganizerBody().organizer(organizer).comment(comment), null, null, Boolean.TRUE, null, null, null, null, null);
        if (expectException) {
            assertNotNull("An error was expected", updateResponse.getError());
            throw new ChronosApiException(updateResponse.getCode(), updateResponse.getError());
        }
        CalendarResult calendarResult = checkResponse(updateResponse.getErrorDesc(), updateResponse.getError(), updateResponse.getCategories(), updateResponse.getData());
        // Search for the correct result, folders might have been shared and thus more than one result is returned
        EventData update = calendarResult.getUpdated().stream().filter(u -> eventData.getId().equals(u.getId()) && folder.equals(u.getFolder())).findFirst().orElse(null);
        Assert.assertThat("No matching event found", update, notNullValue());
        setLastTimeStamp(update.getTimestamp());
        return update;
    }

    /**
     * Updates an organizer on the given event. Performs a series split with the given recurrence ID and range.
     *
     * @param eventData The data of the event
     * @param organizer The new organizer to set
     * @param comment An optional comment to send to the attendees
     * @param recurrenceId the recurrence identifier
     * @param range The {@link RecurrenceRange}
     * @param expectException <code>true</code> if the action should have caused an exception
     * @return The updated <b>master</b> event
     * @throws ApiException if an API error is occurred
     * @throws ChronosApiException if a Chronos API error is occurred
     * @throws NoSuchElementException If master was not updated
     */
    public EventData changeEventOrganizer(EventData eventData, CalendarUser organizer, String comment, String recurrenceId, RecurrenceRange range, boolean expectException) throws ApiException, ChronosApiException {
        String masterId = null == eventData.getSeriesId() ? eventData.getId() : eventData.getSeriesId();
        ChronosCalendarResultResponse updateResponse = userApi.getChronosApi().changeOrganizer(getFolder(eventData), eventData.getId(), L(this.lastTimeStamp), new ChangeOrganizerBody().organizer(organizer).comment(comment), recurrenceId, null == range ? null : range.name(), Boolean.TRUE, null, null, null, null, null);
        if (expectException) {
            assertNotNull("An error was expected", updateResponse.getError());
            throw new ChronosApiException(updateResponse.getCode(), updateResponse.getError());
        }
        CalendarResult calendarResult = checkResponse(updateResponse.getErrorDesc(), updateResponse.getError(), updateResponse.getCategories(), updateResponse.getData());
        setLastTimeStamp(calendarResult.getUpdated().get(0).getTimestamp());
        return calendarResult.getUpdated().stream().filter(e -> masterId.equalsIgnoreCase(e.getId())).findFirst().get();
    }

    /**
     * Gets all changed events since the given timestamp (recurring events will not be expanded).
     *
     * @param since The timestamp
     * @return The {@link UpdatesResult}
     * @throws ApiException if an API error is occurred
     */
    public UpdatesResult getUpdates(Date since) throws ApiException {
        return getUpdates(since, false);
    }

    /**
     * Gets all changed events since the given timestamp.
     *
     * @param since The timestamp
     * @param expand Flag to expand any recurring events
     * @return The {@link UpdatesResult}
     * @throws ApiException if an API error is occurred
     */
    public UpdatesResult getUpdates(Date since, boolean expand) throws ApiException {
        return getUpdates(since, expand, defaultFolder);
    }

    /**
     * Gets all changed events in the specified folder since the given timestamp.
     *
     * @param since The timestamp
     * @param expand Flag to expand any recurring events
     * @param folderId The folder identifier
     * @return The {@link UpdatesResult}
     * @throws ApiException if an API error is occurred
     */
    public UpdatesResult getUpdates(Date since, boolean expand, String folderId) throws ApiException {
        return getUpdates(since, null, null, expand, folderId);
    }

    /**
     * Gets all changed events in the specified folder since the given timestamp.
     *
     * @param since The timestamp
     * @param start The start {@link Date}
     * @param end The end {@link Date}
     * @param expand Flag to expand any recurring events
     * @param folderId The folder identifier
     * @return The {@link UpdatesResult}
     * @throws ApiException if an API error is occurred
     */
    public UpdatesResult getUpdates(Date since, Date start, Date end, boolean expand, String folderId) throws ApiException {
        ChronosUpdatesResponse updatesResponse = userApi.getChronosApi().getUpdates(folderId, L(since.getTime()), start != null ? UTC_DATE_FORMATTER.format(start) : null, end != null ? UTC_DATE_FORMATTER.format(end) : null, null, null, null, B(expand), Boolean.FALSE);
        setLastTimeStamp(updatesResponse.getTimestamp());
        return checkResponse(updatesResponse.getErrorDesc(), updatesResponse.getError(), updatesResponse.getCategories(), updatesResponse.getData());
    }

    /**
     * Acknowledges the alarm with the specified identifier for the specified event
     *
     * @param eventId The event identifier
     * @param alarmId The alarm identifier
     * @param folderId The folder identifier
     * @return The updated {@link EventData} with the acknowledged alarm
     * @throws ApiException if an API error is occurred
     */
    public EventData acknowledgeAlarm(String eventId, int alarmId, String folderId) throws ApiException {
        ChronosCalendarResultResponse acknowledgeAlarm = userApi.getChronosApi().acknowledgeAlarm(eventId, folderId != null ? folderId : defaultFolder, I(alarmId), Boolean.FALSE, null, null);
        CalendarResult checkResponse = checkResponse(acknowledgeAlarm.getError(), acknowledgeAlarm.getErrorDesc(), acknowledgeAlarm.getCategories(), acknowledgeAlarm.getData());
        assertEquals(1, checkResponse.getUpdated().size());
        EventData updated = checkResponse.getUpdated().get(0);
        Long acknowledged = updated.getAlarms().get(0).getAcknowledged();
        assertNotNull(acknowledged);

        return updated;
    }

    /**
     * Snoozes the alarm with the specified identifier for the specified event
     *
     * @param eventId The event identifier
     * @param alarmId The alarm identifier
     * @param snoozeTime The snooze time
     * @param folderId The folder identifier
     * @return The updated {@link EventData}
     * @throws ApiException if an API error is occurred
     */
    public EventData snoozeAlarm(String eventId, int alarmId, long snoozeTime, String folderId) throws ApiException {
        ChronosCalendarResultResponse snoozeResponse = userApi.getChronosApi().snoozeAlarm(eventId, folderId != null ? folderId : defaultFolder, I(alarmId), L(snoozeTime), Boolean.FALSE, null, null);
        CalendarResult snoozeResult = checkResponse(snoozeResponse.getError(), snoozeResponse.getErrorDesc(), snoozeResponse.getCategories(), snoozeResponse.getData());
        assertEquals(1, snoozeResult.getUpdated().size());
        EventData updatedEvent = snoozeResult.getUpdated().get(0);
        assertEquals(2, updatedEvent.getAlarms().size());

        return updatedEvent;
    }

    /**
     * Retrieves not acknowledged alarm triggers.
     *
     * @param until Upper exclusive limit of the queried range as a utc date-time value as specified
     *            in RFC 5545 chapter 3.3.5. E.g. \"20170708T220000Z\". Only events which should trigger before this date are returned.
     * @return The {@link AlarmTriggerData}
     * @throws ApiException if an API error is occurred
     */
    public List<AlarmTrigger> getAlarmTrigger(long until) throws ApiException {
        return getAlarmTrigger(until, null);
    }

    /**
     * Retrieves not acknowledged alarm triggers.
     *
     * @param until Upper exclusive limit of the queried range as a utc date-time value as specified
     *            in RFC 5545 chapter 3.3.5. E.g. \"20170708T220000Z\". Only events which should trigger before this date are returned.
     * @param actions The actions to retrieve (comma separated string)
     * @return The {@link AlarmTriggerData}
     * @throws ApiException if an API error is occurred
     */
    public List<AlarmTrigger> getAlarmTrigger(long until, String actions) throws ApiException {
        AlarmTriggerResponse triggerResponse = userApi.getChronosApi().getAlarmTrigger(DateTimeUtil.getZuluDateTime(until).getValue(), DateTimeUtil.getZuluDateTime(0).getValue(), actions);
        return checkResponse(triggerResponse.getError(), triggerResponse.getErrorDesc(), triggerResponse.getCategories(), triggerResponse.getData());
    }

    /**
     * Updates the attendee status of the event with the specified identifier.
     *
     * @param eventId The event identifier
     * @param reccurenceId The recurrence id or <code>null</code> for the master event
     * @param folderId The folder identifier or <code>null</code>
     * @param attendeeAndAlarm The status of the attendee
     * @param expectException If an error is expected when updating the attendee
     * @throws ApiException if an API error occurs
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public void updateAttendee(String eventId, String reccurenceId, String folderId, AttendeeAndAlarm attendeeAndAlarm, boolean expectException) throws ApiException, ChronosApiException {
        ChronosCalendarResultResponse updateAttendee = userApi.getChronosApi().updateAttendee(folderId == null ? defaultFolder : folderId, eventId, L(lastTimeStamp), attendeeAndAlarm, reccurenceId, null, Boolean.TRUE, null, null, Boolean.FALSE, null, null, EXPAND_SERIES);
        if (expectException) {
            assertNotNull("An error was expected", updateAttendee.getError());
            throw new ChronosApiException(updateAttendee.getCode(), updateAttendee.getError());
        }
        checkResponse(updateAttendee.getError(), updateAttendee.getErrorDesc(), updateAttendee.getCategories(), updateAttendee.getData());
        setLastTimeStamp(updateAttendee.getTimestamp());
    }


    /**
     * Updates the attendee status of the event with the specified identifier.
     *
     * @param eventId The event identifier
     * @param attendeeAndAlarm The status of the attendee
     * @param expectException If an error is expected when updating the attendee
     * @throws ApiException if an API error occurs
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public void updateAttendee(String eventId, AttendeeAndAlarm attendeeAndAlarm, boolean expectException) throws ApiException, ChronosApiException {
        updateAttendee(eventId, null, null, attendeeAndAlarm, expectException);
    }

    //////////////////////////////////////////////// HELPERS ///////////////////////////////////////////////////

    private JSONObject extractBody(String response) throws ChronosApiException {
        try {
            JSONObject responseData = new JSONObject(response);
            return checkResponse(responseData.optString("error", null), responseData.optString("error_desc", null), responseData.optString("categories", null), responseData.optJSONObject("data"));
        } catch (JSONException e) {
            throw new ChronosApiException("JSON_ERROR", e.getMessage());
        }
    }

    public final JSONObject handleCreation(String response) throws ChronosApiException {
        try {
            JSONObject result = extractBody(response);
            JSONArray optJSONArray = result.optJSONArray("conflicts");
            if (optJSONArray == null) {
                optJSONArray = new JSONArray();
            }
            assertEquals("Found unexpected conflicts", 0, optJSONArray.length());
            JSONObject event = result.optJSONArray("created").getJSONObject(0);

            EventId eventId = new EventId();
            eventId.setId(event.optString("id"));
            eventId.setFolder(event.optString("folder"));
            rememberEventId(eventId);
            lastTimeStamp = event.optLong("timestamp");

            return event;
        } catch (JSONException e) {
            throw new ChronosApiException("JSON_ERROR", e.getMessage());
        }
    }

    private JSONObject handleUpdate(String response) throws ChronosApiException {
        try {
            JSONObject result = extractBody(response);
            JSONArray array = result.optJSONArray("updated");
            assertTrue(array.length() == 1);
            lastTimeStamp = array.getJSONObject(0).optLong("timestamp");
            return array.getJSONObject(0);
        } catch (JSONException e) {
            throw new ChronosApiException("JSON_ERROR", e.getMessage());
        }
    }

    /**
     * Handles the result response of an event creation
     *
     * @param createEvent The result
     * @return The created event
     */
    public final EventData handleCreation(ChronosCalendarResultResponse createEvent) {
        CalendarResult result = checkResponse(createEvent.getError(), createEvent.getErrorDesc(), createEvent.getCategories(), createEvent.getData());
        assertEquals("Found unexpected conflicts", 0, result.getConflicts().size());
        EventData event = result.getCreated().get(0);

        EventId eventId = new EventId();
        eventId.setId(event.getId());
        eventId.setFolder(event.getFolder());
        rememberEventId(eventId);
        setLastTimeStamp(createEvent.getTimestamp());

        return event;
    }

    /**
     * Handles the result response of an update event
     *
     * @param updateEvent The result
     * @return The updated event
     * @throws ChronosApiException if a Chronos API error is occurred
     */
    public final EventData handleUpdate(ChronosCalendarResultResponse updateEvent, boolean expectException) throws ChronosApiException {
        if (expectException) {
            assertNotNull("An error was expected", updateEvent.getError());
            throw new ChronosApiException(updateEvent.getCode(), updateEvent.getError());
        }
        CalendarResult calendarResult = checkResponse(updateEvent.getErrorDesc(), updateEvent.getError(), updateEvent.getCategories(), updateEvent.getData());
        assertEquals("Found unexpected conflicts", 0, calendarResult.getConflicts().size());
        List<EventData> updates = calendarResult.getUpdated();
        assertEquals(1, updates.size());
        setLastTimeStamp(updates.get(0).getTimestamp());
        return updates.get(0);
    }

    /**
     * Keeps track of the specified {@link EventId} for the specified user
     *
     * @param eventData The {@link EventData} to generate the {@link EventId} from
     * @see #rememberEventId(EventId)
     */
    public void rememberEvent(EventData eventData) {
        if (null == eventData) {
            return;
        }

        EventId eventId = new EventId();
        eventId.setId(eventData.getId());
        eventId.setFolder(null == eventData.getFolder() ? defaultFolder : eventData.getFolder());
        eventId.setRecurrenceId(eventData.getRecurrenceId());
        rememberEventId(eventId);
    }

    /**
     * Keeps track of the specified {@link EventId} for the specified user
     *
     * @param eventId The {@link EventId}
     */
    public void rememberEventId(EventId eventId) {
        if (eventIds == null) {
            eventIds = new ArrayList<>();
        }
        eventIds.add(eventId);
    }

    /**
     * Keeps track of the specified list of {@link EventId} for the specified user
     *
     * @param eventIdList A list of {@link EventId}
     */
    public void rememberEventIds(List<EventId> eventIdList) {
        if (eventIds == null) {
            eventIds = new ArrayList<>();
        }
        eventIds.addAll(eventIdList);
    }

    /**
     * Removes the specified {@link EventId} for the specified user from the cache
     *
     * @param eventId The {@link EventId}
     */
    protected void forgetEventId(EventId eventId) {
        if (eventIds == null) {
            eventIds = new ArrayList<>();
        }
        eventIds.remove(eventId);
    }

    /**
     * Gets the lastTimeStamp
     *
     * @return The lastTimeStamp
     */
    public long getLastTimeStamp() {
        return lastTimeStamp;
    }

    /**
     * Sets the lastTimeStamp
     *
     * @param lastTimeStamp The lastTimeStamp to set
     */
    public void setLastTimeStamp(long lastTimeStamp) {
        this.lastTimeStamp = lastTimeStamp;
    }

    /**
     * Sets the lastTimeStamp
     *
     * @param lastTimeStamp The lastTimeStamp to set
     */
    public void setLastTimeStamp(Long lastTimeStamp) {
        if (null != lastTimeStamp) {
            this.lastTimeStamp = l(lastTimeStamp);
        }
    }

    /**
     * Sets the ignoreConflicts flag for all requests if not explicitly specified.
     *
     * @param The ignoreConflicts flag
     */
    public void setIgnoreConflicts(boolean ignoreConflicts) {
        this.ignoreConflicts = ignoreConflicts;
    }

    /**
     *
     * Returns the ignoreCoflicts flag.
     *
     * @return The ignoreConflicts flag
     */
    public boolean isIgnoreCoflicts() {
        return ignoreConflicts;
    }

    public static boolean isSeriesMaster(EventData event) {
        return null != event && null != event.getId() && event.getId().equals(event.getSeriesId()) && null == event.getRecurrenceId();
    }

    /**
     * Filters the given list of event
     *
     * @param events The event to filter by summary
     * @param summary The summary each event must begin with
     * @return A list of events with the same summary
     */
    public static List<EventData> filterEventBySummary(List<EventData> events, String summary) {
        return filterEventBy(events, summary, (e) -> e.getSummary());
    }

    /**
     * Filters the given list of event
     *
     * @param events The event to filter by summary
     * @param comparee The summary each event must begin with
     * @param f The function to get a string from the event data to compare to the comparee
     * @return A list of events with the same summary
     */
    public static List<EventData> filterEventBy(List<EventData> events, String comparee, Function<EventData, String> f) {
        return events.stream().filter(e -> f.apply(e).startsWith(comparee)).collect(Collectors.toList());
    }
}
