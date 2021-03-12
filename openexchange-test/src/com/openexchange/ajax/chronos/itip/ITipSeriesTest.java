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
 *    trademarks of the OX Software GmbH. group of companies.
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

package com.openexchange.ajax.chronos.itip;

import static com.openexchange.ajax.chronos.itip.ITipAssertion.assertAttendeePartStat;
import static com.openexchange.ajax.chronos.itip.ITipAssertion.assertSingleChange;
import static com.openexchange.ajax.chronos.itip.ITipAssertion.assertSingleDescription;
import static com.openexchange.ajax.chronos.itip.ITipAssertion.assertSingleEvent;
import static com.openexchange.ajax.chronos.itip.ITipUtil.constructBody;
import static com.openexchange.ajax.chronos.itip.ITipUtil.receiveIMip;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.chronos.scheduling.SchedulingMethod;
import com.openexchange.testing.httpclient.models.AnalysisChange;
import com.openexchange.testing.httpclient.models.AnalysisChangeNewEvent;
import com.openexchange.testing.httpclient.models.AnalyzeResponse;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.ChronosCalendarResultResponse;
import com.openexchange.testing.httpclient.models.ChronosMultipleCalendarResultResponse;
import com.openexchange.testing.httpclient.models.DeleteEventBody;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.EventId;
import com.openexchange.testing.httpclient.models.EventResponse;
import com.openexchange.testing.httpclient.models.MailData;
import com.openexchange.testing.httpclient.models.UpdateEventBody;

/**
 * {@link ITipSeriesTest}
 *
 * User A from context 1 will create a series with 10 occurrences with user B from context 2 as attendee.
 * User B will accept the event in the setup and the change will be accepted by the organizer.
 *
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.3
 */
public class ITipSeriesTest extends AbstractITipAnalyzeTest {

    private String summary;

    /** User B from context 2*/
    private Attendee replyingAttendee;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        summary = this.getClass().getName() + " " + UUID.randomUUID().toString();
        EventData event = EventFactory.createSeriesEvent(getUserId(), summary, 10, defaultFolderId);
        replyingAttendee = prepareCommonAttendees(event);

        createdEvent = eventManager.createEvent(event);

        /*
         * Receive mail as attendee
         */
        MailData inviteMail = receiveIMip(apiClientC2, userResponseC1.getData().getEmail1(), summary, 0, SchedulingMethod.REQUEST);
        AnalysisChangeNewEvent newEvent = assertSingleChange(analyze(apiClientC2, inviteMail)).getNewEvent();
        assertNotNull(newEvent);
        assertEquals(createdEvent.getUid(), newEvent.getUid());
        assertAttendeePartStat(newEvent.getAttendees(), replyingAttendee.getEmail(), PartStat.NEEDS_ACTION.status);

        /*
         * reply with "accepted"
         */
        EventData attendeeEvent = assertSingleEvent(accept(apiClientC2, constructBody(inviteMail), null), createdEvent.getUid());
        assertAttendeePartStat(attendeeEvent.getAttendees(), replyingAttendee.getEmail(), PartStat.ACCEPTED.status);

        MailData reply = receiveIMip(apiClient, replyingAttendee.getEmail(), summary, 0, SchedulingMethod.REPLY);
        analyze(reply.getId());

        /*
         * Take over accept and check in calendar
         */
        assertSingleEvent(update(constructBody(reply)), createdEvent.getUid());
        EventResponse eventResponse = chronosApi.getEvent(createdEvent.getId(), createdEvent.getFolder(), createdEvent.getRecurrenceId(), null, null);
        assertNull(eventResponse.getError(), eventResponse.getError());
        createdEvent = eventResponse.getData();
        for (Attendee attendee : createdEvent.getAttendees()) {
            assertThat("Participant status is not correct.", PartStat.ACCEPTED.status, is(attendee.getPartStat()));
        }
    }

    @Test
    public void testDeleteSingleOccurrence() throws Exception {
        /*
         * Delete a single occurrence as organizer
         */
        List<EventData> allEvents = getAllEventsOfCreatedEvent();
        String recurrenceId = allEvents.get(2).getRecurrenceId();

        DeleteEventBody body = new DeleteEventBody();
        EventId id = new EventId();
        id.setFolder(defaultFolderId);
        id.setId(createdEvent.getId());
        id.setRecurrenceId(recurrenceId);
        body.setEvents(Collections.singletonList(id));
        ChronosMultipleCalendarResultResponse result = chronosApi.deleteEvent(now(), body, null, null, null, null, null, null, null);

        /*
         * Check result
         */
        assertNotNull(result);
        assertNull(result.getError());
        assertNotNull(result.getData());
        assertTrue("Only one element should be deleted", result.getData().size() == 1);
        assertNotNull(result.getData().get(0).getUpdated());
        assertTrue("Only one element should be deleted", result.getData().get(0).getUpdated().size() == 1);
        assertTrue("Only one element should be deleted", result.getData().get(0).getUpdated().get(0).getDeleteExceptionDates().size() == 1);
        assertThat(result.getData().get(0).getUpdated().get(0).getDeleteExceptionDates().get(0), is(recurrenceId));

        /*
         * Receive deletion as attendee
         */
        MailData iMip = receiveIMip(apiClientC2, userResponseC1.getData().getEmail1(), "Appointment canceled: " + summary, 1, SchedulingMethod.CANCEL);
        AnalyzeResponse analyzeResponse = analyze(apiClientC2, iMip);
        analyze(analyzeResponse, CustomConsumers.CANCEL);
    }

    @Test
    public void testChangeSingleOccurrence() throws Exception {
        /*
         * Update a single occurrence as organizer
         */
        List<EventData> allEvents = getAllEventsOfCreatedEvent();
        String recurrenceId = allEvents.get(2).getRecurrenceId();

        EventData deltaEvent = prepareDeltaEvent(createdEvent);
        deltaEvent.setDescription("Totally new description for " + this.getClass().getName());

        UpdateEventBody body = getUpdateBody(deltaEvent);
        ChronosCalendarResultResponse result = chronosApi.updateEvent(defaultFolderId, createdEvent.getId(), now(), body, recurrenceId, null, null, null, null, null, null, null, null, null, null);

        /*
         * Check result
         */
        assertNotNull(result);
        assertNull(result.getError());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getCreated());
        assertTrue(result.getData().getCreated().size() == 1);
        assertFalse(createdEvent.getId().equals(result.getData().getCreated().get(0).getId()));
        assertTrue(createdEvent.getId().equals(result.getData().getCreated().get(0).getSeriesId()));

        /*
         * Receive update as attendee
         */
        MailData iMip = receiveIMip(apiClientC2, userResponseC1.getData().getEmail1(), summary, 1, SchedulingMethod.REQUEST);
        AnalyzeResponse analyzeResponse = analyze(apiClientC2, iMip);
        AnalysisChangeNewEvent newEvent = assertSingleChange(analyzeResponse).getNewEvent();
        assertNotNull(newEvent);
        assertAttendeePartStat(newEvent.getAttendees(), replyingAttendee.getEmail(), PartStat.ACCEPTED.getStatus());
        analyze(analyzeResponse, CustomConsumers.ALL);
        AnalysisChange change = assertSingleChange(analyzeResponse);
        assertSingleDescription(change, "The appointment description has changed");
    }
}
