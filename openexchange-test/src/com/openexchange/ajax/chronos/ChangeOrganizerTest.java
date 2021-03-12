/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.ajax.chronos;

import static com.openexchange.ajax.chronos.manager.EventManager.RecurrenceRange.THISANDFUTURE;
import static com.openexchange.ajax.chronos.manager.EventManager.RecurrenceRange.THISANDPRIOR;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import com.openexchange.ajax.chronos.factory.AttendeeFactory;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.ajax.chronos.manager.ChronosApiException;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.CalendarUser;
import com.openexchange.testing.httpclient.models.EventData;

/**
 * {@link ChangeOrganizerTest}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.2
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class ChangeOrganizerTest extends AbstractOrganizerTest {

    private CalendarUser newOrganizer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // The new organizer
        newOrganizer = AttendeeFactory.createOrganizerFrom(actingAttendee);
    }

    @Override
    String getEventName() {
        return "AttendeePrivilegesTest";
    }

    @Test
    public void testUpdateToInternal() throws Exception {
        // Create event
        event = createEvent();

        // Update to internal
        // Might fail if the original organizer shared the calendar with the new organizer, so both updated events will be returned to the client
        EventData data = eventManager.changeEventOrganizer(event, newOrganizer, null, false);
        assertThat("Organizer did not change", data.getOrganizer().getUri(), is(newOrganizer.getUri()));
    }

    @Test
    public void testUpdateToInternalWithComment() throws Exception {
        // Create event
        event = createEvent();

        // Update to internal and set comment
        EventData data = eventManager.changeEventOrganizer(event, newOrganizer, "Comment4U", false);
        assertThat("Organizer did not change", data.getOrganizer().getUri(), is(newOrganizer.getUri()));
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateToNone() throws Exception {
        // Create event
        event = createEvent();

        // Update to 'null'
        eventManager.changeEventOrganizer(event, null, null, true);
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateOnNonGroupScheduled() throws Exception {
        event.setAttendees(null);

        // Create event
        event = createEvent();

        // Update an non group scheduled
        eventManager.changeEventOrganizer(event, newOrganizer, null, true);
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateToExternal() throws Exception {
        // Create event
        event = createEvent();

        // Update to external
        newOrganizer = AttendeeFactory.createOrganizerFrom(AttendeeFactory.createIndividual("external@example.org"));
        eventManager.changeEventOrganizer(event, newOrganizer, null, true);
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateWithExternalAttendee() throws Exception {
        event.getAttendees().add(AttendeeFactory.createIndividual("external@example.org"));

        // Create event
        event = createEvent();

        // Update with external attendee
        eventManager.changeEventOrganizer(event, newOrganizer, null, true);
    }

    @Test(expected = ChronosApiException.class)
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
        eventManager.changeEventOrganizer(occurrence, newOrganizer, null, occurrence.getRecurrenceId(), THISANDFUTURE, true);
    }

    @Test
    public void testUpdateThisAndFuture() throws Exception {
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        EventData occurrence = getSecondOccurrence();
        // THISANDFUTURE
        EventData data = eventManager.changeEventOrganizer(event, newOrganizer, null, occurrence.getRecurrenceId(), THISANDFUTURE, false);
        assertThat("Organizer did not change", data.getOrganizer().getUri(), is(newOrganizer.getUri()));
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateThisAndPrior() throws Exception {
        event.setRrule("FREQ=" + EventFactory.RecurringFrequency.DAILY.name() + ";COUNT=" + 10);

        // Create event
        event = createEvent();

        // THISANDPRIOR
        eventManager.changeEventOrganizer(event, newOrganizer, null, getSecondOccurrence().getRecurrenceId(), THISANDPRIOR, true);
    }

    @Test(expected = ChronosApiException.class)
    public void testUpdateAsAttendee() throws Exception {
        // Create event
        event = createEvent();

        // Load from users view
        EventData data = eventManager2.getEvent(folderId2, event.getId());

        // Update as attendee
        eventManager2.changeEventOrganizer(data, newOrganizer, null, true);
    }

    @Test
    public void testDeleteOriginalOrganizer() throws Exception {
        // Create event
        event = createEvent();

        // Update to internal
        EventData data = eventManager.changeEventOrganizer(event, newOrganizer, null, false);
        assertThat("Organizer did not change", data.getOrganizer().getUri(), is(newOrganizer.getUri()));

        // Remove original organizer
        ArrayList<Attendee> attendees = new ArrayList<>();
        attendees.add(AttendeeFactory.createIndividual("external@example.org"));
        attendees.add(actingAttendee);

        data = eventManager2.getEvent(folderId2, data.getId());
        data.setAttendees(attendees);
        data.setLastModified(Long.valueOf(System.currentTimeMillis()));
        data = eventManager2.updateEvent(data);

        // Check if original has been removed
        for (Attendee attendee : data.getAttendees()) {
            Assert.assertThat("Old organizer found!", attendee.getUri(), is(not(organizerCU.getUri())));
        }

        // Check if changes as new organizer are possible
        String summary = "New summary: ChangeOrganizerTest";
        data.setSummary(summary);
        data = eventManager2.updateEvent(data);
        Assert.assertThat("Summary can't be changed by new organizer", data.getSummary(), is(summary));
    }

    private EventData createEvent() throws ApiException {
        return eventManager.createEvent(event, true);
    }

}
