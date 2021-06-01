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

package com.openexchange.ajax.chronos;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import com.openexchange.ajax.chronos.factory.AttendeeFactory;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.ajax.chronos.manager.ChronosApiException;
import com.openexchange.ajax.chronos.manager.EventManager;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.CalendarUser;
import com.openexchange.testing.httpclient.models.ChronosCalendarResultResponse;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.EventId;
import com.openexchange.testing.httpclient.models.UpdateEventBody;

/**
 * {@link AttendeePrivilegesTest}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.2
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class AttendeePrivilegesTest extends AbstractOrganizerTest {

    public enum Privileges {
        DEFAULT,
        MODIFY;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setAttendeePrivileges(event);
    }

    @Override
    String getEventName() {
        return "AttendeePrivilegesTest";
    }

    @Test
    public void testCorrectAttendeePrivileges() throws Exception {
        // Create event
        event = createEvent();
        assertThat("Not the correct permission!", event.getAttendeePrivileges(), is(Privileges.MODIFY.name()));
    }

    @Test
    public void testDefaultAttendeePrivileges() throws Exception {
        // Create event
        event.setAttendeePrivileges(null);
        event = createEvent();
        assertThat("Not the correct permission!", event.getAttendeePrivileges(), anyOf(is(Privileges.DEFAULT.name()), nullValue()));
    }

    @Test
    public void testAddExternalAttendee() throws Exception {
        // Create event
        event = createEvent();

        addExternalAttendee(eventManager2.getEvent(null, event.getId()), false);

        // Re-check as organizer
        EventData data = eventManager.getEvent(event.getFolder(), event.getId());
        assertThat("Attendee were not added", Integer.valueOf(data.getAttendees().size()), is(Integer.valueOf(3)));
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateWithExternalOrganizer() throws Exception {
        // Create event
        event.setAttendeePrivileges(null);
        Attendee external = AttendeeFactory.createIndividual("organizer@example.org");
        event.getAttendees().add(external);
        event.setOrganizer(AttendeeFactory.createOrganizerFrom(external));
        event = createEvent();

        addExternalAttendee(event, true);
    }

    @Test
    public void testDeleteEventAsAttendee() throws Exception {
        // Create event
        event = createEvent();

        EventId eventId = new EventId();
        eventId.setId(event.getId());
        eventId.setFolder(folderId2);

        // Delete event as attendee
        eventManager2.deleteEvent(eventId);

        // Assert that the event was "deleted" from the attendee point of view, not for other
        EventData data = eventManager.getEvent(event.getFolder(), event.getId());
        assertThat("Should not be null", data, notNullValue());
        assertThat("Attendee should not have been removed", Integer.valueOf(data.getAttendees().size()), is(Integer.valueOf(2)));
        Attendee hiden = data.getAttendees().stream().filter(a -> a.getEntity() == actingAttendee.getEntity()).findFirst().orElse(null);
        assertThat("Attendee is missing!", hiden, notNullValue());
        assertThat("Attendee status should be 'declined' from the organizer view", hiden.getPartStat(), is("DECLINED"));
    }

    @Test(expected = ChronosApiException.class)
    public void testRemoveOrganizerAsAttendee() throws Exception {
        // Create event
        event = createEvent();

        EventData eventUpdate = prepareEventUpdate(event);
        eventUpdate.setAttendees(Collections.singletonList(actingAttendee));

        eventManager2.updateEvent(eventUpdate, true, false);
    }

    @Test
    public void testUpdateWithExternalAttendee() throws Exception {
        Attendee external = AttendeeFactory.createIndividual("external@example.org");
        event.getAttendees().add(external);

        // Create event
        event = createEvent();
        event = eventManager2.getEvent(null, event.getId());
        String summary = "AttendeePrivilegesTest: Modify summary";
        EventData eventUpdate = prepareEventUpdate(event);
        eventUpdate.setSummary(summary);
        eventUpdate.setAttendees(event.getAttendees());
        EventData data = eventManager2.updateEvent(eventUpdate, false, false);
        assertThat("Summary should have changed", data.getSummary(), is(summary));
    }

    @Test
    public void testDeleteSingleOccurrence() throws Exception {
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        EventData occurrence = getSecondOccurrence();

        EventData exception = prepareException(occurrence);
        EventData master = eventManager.updateOccurenceEvent(exception, exception.getRecurrenceId(), true);

        assertThat("Too many change exceptions", Integer.valueOf(master.getChangeExceptionDates().size()), is(Integer.valueOf(1)));
        assertThat("Unable to find change exception", (occurrence = getOccurrence(eventManager, master.getChangeExceptionDates().get(0), master.getId())), is(notNullValue()));

        EventId eventId = new EventId();
        eventId.setId(occurrence.getId());
        eventId.setFolder(folderId2);

        eventManager2.deleteEvent(eventId);

        // Assert that the event was "deleted" from the attendees point of view
        EventData data = eventManager.getEvent(event.getFolder(), occurrence.getId());
        assertThat("Should not be null", data, notNullValue());
        assertThat("Attendee should not have been removed", Integer.valueOf(data.getAttendees().size()), is(Integer.valueOf(2)));
        Attendee hiden = data.getAttendees().stream().filter(a -> a.getEntity() == actingAttendee.getEntity()).findFirst().orElse(null);
        assertThat("Attendee is missing!", hiden, notNullValue());
        assertThat("Attendee status should be 'declined' from the organizer view", hiden.getPartStat(), is("DECLINED"));
    }

    @Test
    public void testUpdateOnSingleOccurrence() throws Exception {
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        EventData occurrence = getSecondOccurrence();

        EventData exception = prepareException(occurrence);
        EventData master = eventManager.updateOccurenceEvent(exception, exception.getRecurrenceId(), true);

        assertThat("Too many change exceptions", Integer.valueOf(master.getChangeExceptionDates().size()), is(Integer.valueOf(1)));
        assertThat("Unable to find change exception", (occurrence = getOccurrence(eventManager, master.getChangeExceptionDates().get(0), master.getId())), is(notNullValue()));

        // update on occurrence
        exception = getOccurrence(eventManager2, exception.getRecurrenceId(), master.getSeriesId());
        exception = eventManager2.getEvent(folderId2, exception.getId());
        addExternalAttendee(exception, false);
    }

    @Test
    public void testUpdateThisAndFutureAsAttendee() throws Exception {
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        EventData occurrence = getSecondOccurrence(eventManager2);
        occurrence = eventManager2.getEvent(null, occurrence.getId(), occurrence.getRecurrenceId(), false);

        // Update as attendee
        EventData exception = prepareException(occurrence);
        occurrence.getAttendees().add(AttendeeFactory.createIndividual("external@example.org"));
        EventData master = eventManager2.updateOccurenceEvent(exception, exception.getRecurrenceId(), EventManager.RecurrenceRange.THISANDFUTURE, false, true);
        master = eventManager2.getEvent(null, master.getId());
        assertThat("Start date", Integer.valueOf(master.getAttendees().size()), is(Integer.valueOf(3)));

    }

    @Test(expected = ChronosApiException.class)
    public void testSetPropertyAsAttendee() throws Exception {
        // Create event
        event.setExtendedProperties(null);
        event = createEvent();

        // Set extended properties and update as an attendee
        EventData data = eventManager2.getEvent(folderId2, event.getId());
        EventData eventUpdate = prepareEventUpdate(data);
        setAttendeePrivileges(eventUpdate);
        eventManager2.updateEvent(eventUpdate, true, false);
    }

    @Test
    public void testPrivilegesAsOrganizer() throws Exception {
        // Create event
        event.setExtendedProperties(null);
        event = createEvent();

        // Set extended properties and update as an attendee
        EventData data = eventManager.getEvent(null, event.getId());
        EventData eventUpdate = prepareEventUpdate(data);
        setAttendeePrivileges(eventUpdate);
        eventUpdate.setAttendees(event.getAttendees());
        data = eventManager.updateEvent(eventUpdate, false, false);

        assertThat("Attendee privileges are not correct", data.getAttendeePrivileges(), is(Privileges.MODIFY.name()));

        data = eventManager2.getEvent(null, event.getId());
        assertThat("Attendee privileges are not correct", data.getAttendeePrivileges(), is(Privileges.MODIFY.name()));
    }

    @Test
    public void testChangeOrganizerAsAttendee() throws Exception {
        // Create event
        event = createEvent();

        // Get data
        EventData data = eventManager2.getEvent(null, event.getId());
        EventData eventUpdate = prepareEventUpdate(data);

        // Set organizer as attendee
        CalendarUser newOrganizer = AttendeeFactory.createOrganizerFrom(actingAttendee);
        eventUpdate = eventManager2.changeEventOrganizer(eventUpdate, newOrganizer, null, false);

        assertThat("Organizer not set!", eventUpdate.getOrganizer(), notNullValue());
        assertThat("Organizer should have changes", eventUpdate.getOrganizer().getUri(), is(newOrganizer.getUri()));
    }

    @Test
    public void testRemoveAttendeeViaUpdate() throws Exception {
        // Create event
        event = createEvent();

        // Get data
        EventData data = eventManager2.getEvent(null, event.getId());
        EventData eventUpdate = prepareEventUpdate(data);

        eventUpdate.setAttendees(Collections.singletonList(organizerAttendee));

        EventData updateEvent = eventManager2.updateEvent(eventUpdate, false, false);
        assertThat("Acting attendee should not be removed", Integer.valueOf(updateEvent.getAttendees().size()), is(Integer.valueOf(2)));
    }

    @Test
    public void testUpdateMasterAndExceptions() throws Exception {
        event.setAttendeePrivileges(null);
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        EventData occurrence = getSecondOccurrence(eventManager);
        occurrence = eventManager.getEvent(null, occurrence.getId(), occurrence.getRecurrenceId(), false);

        // Update as attendee
        EventData exception = prepareException(occurrence);
        exception.getAttendees().add(AttendeeFactory.createIndividual("external@example.org"));
        EventData master = eventManager.updateOccurenceEvent(exception, exception.getRecurrenceId(), false, true);
        master = eventManager.getEvent(null, master.getId());

        EventData masterUpdate = prepareEventUpdate(event);
        setAttendeePrivileges(masterUpdate);
        masterUpdate.setChangeExceptionDates(master.getChangeExceptionDates());

        UpdateEventBody body = new UpdateEventBody();
        body.setEvent(masterUpdate);
        ChronosCalendarResultResponse updateResponse = defaultUserApi.getChronosApi().updateEvent(defaultFolderId, masterUpdate.getId(), masterUpdate.getLastModified(), body, null, null, Boolean.FALSE, null, Boolean.FALSE, null, null, null, null, Boolean.FALSE, null);
        assertThat(Integer.valueOf(updateResponse.getData().getUpdated().size()), is(Integer.valueOf(2)));

        master = updateResponse.getData().getUpdated().stream().filter(e -> e.getId().equals(e.getSeriesId())).findAny().orElse(null);
        assertThat("\"Modify\" privilege should have been set", master.getAttendeePrivileges(), is(Privileges.MODIFY.name()));

        occurrence = updateResponse.getData().getUpdated().stream().filter(e -> false == e.getId().equals(e.getSeriesId())).findAny().orElse(null);
        assertThat("Exception should have new privilege", occurrence.getAttendeePrivileges(), is(Privileges.MODIFY.name()));
    }

    // ----------------------------- HELPER -----------------------------

    private void addExternalAttendee(EventData eventData, boolean expectException) throws ApiException, ChronosApiException {
        ArrayList<Attendee> attendees = new ArrayList<>(eventData.getAttendees());
        attendees.add(AttendeeFactory.createIndividual("external@example.org"));

        EventData data = new EventData();
        data.setId(eventData.getId());
        data.setFolder(folderId2);
        data.setAttendees(attendees);
        data.setLastModified(Long.valueOf(System.currentTimeMillis()));
        data = eventManager2.updateEvent(data, expectException, false);
        assertThat("Attendees were not updated", Integer.valueOf(data.getAttendees().size()), is(Integer.valueOf(3)));
    }

    private void setAttendeePrivileges(EventData data) {
        data.setAttendeePrivileges(Privileges.MODIFY.name());
    }

    private EventData createEvent() throws ApiException {
        return eventManager.createEvent(event, true);
    }

}
