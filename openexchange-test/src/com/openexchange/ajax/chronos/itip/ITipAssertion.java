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

package com.openexchange.ajax.chronos.itip;

import static com.openexchange.java.Autoboxing.I;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.LinkedList;
import java.util.List;
import com.openexchange.ajax.chronos.itip.AbstractITipTest.PartStat;
import com.openexchange.testing.httpclient.models.ActionResponse;
import com.openexchange.testing.httpclient.models.Analysis;
import com.openexchange.testing.httpclient.models.AnalysisChange;
import com.openexchange.testing.httpclient.models.AnalyzeResponse;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.EventData;

/**
 * {@link ITipAssertion}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.3
 */
public class ITipAssertion {

    private ITipAssertion() {}

    /**
     * Asserts that only one analyze with exact one change was provided in the response
     *
     * @param analyzeResponse The response to check
     * @return The {@link AnalysisChange} provided by the server
     */
    public static AnalysisChange assertSingleChange(AnalyzeResponse analyzeResponse) {
        assertNull("error during analysis: " + analyzeResponse.getError(), analyzeResponse.getCode());
        assertEquals("unexpected analysis number in response", 1, analyzeResponse.getData().size());
        Analysis analysis = analyzeResponse.getData().get(0);
        assertEquals("unexpected number of changes in analysis. Changes: " + analysis.getChanges().toString(), 1, analysis.getChanges().size());
        return analysis.getChanges().get(0);
    }

    /**
     * Asserts that only one analyze with exact one change was provided in the response
     *
     * @param analyzeResponse The response to check
     * @param size The expected size of events
     * @param index The index of events to return
     * @return The {@link AnalysisChange} provided by the server
     */
    public static AnalysisChange assertChanges(AnalyzeResponse analyzeResponse, int size, int index) {
        assertNull("error during analysis: " + analyzeResponse.getError(), analyzeResponse.getCode());
        assertEquals("unexpected analysis number in response", 1, analyzeResponse.getData().size());
        Analysis analysis = analyzeResponse.getData().get(0);
        assertEquals("unexpected number of changes in analysis", size, analysis.getChanges().size());
        return analysis.getChanges().get(index);
    }

    /**
     * Asserts that the given attendee represented by its mail has the desired participant status
     *
     * @param attendees The attendees of the event
     * @param email The attendee to check represented by its mail
     * @param partStat The participant status of the attendee
     * @return The attendee to check as {@link Attendee} object
     */
    public static Attendee assertAttendeePartStat(List<Attendee> attendees, String email, PartStat partStat) {
        return assertAttendeePartStat(attendees, email, partStat.getStatus());
    }

    /**
     * Asserts that the given attendee represented by its mail has the desired participant status
     *
     * @param attendees The attendees of the event
     * @param email The attendee to check represented by its mail
     * @param expectedPartStat The participant status of the attendee
     * @return The attendee to check as {@link Attendee} object
     */
    public static Attendee assertAttendeePartStat(List<Attendee> attendees, String email, String expectedPartStat) {
        Attendee attendee = extractAttendee(attendees, email);
        assertNotNull(attendee);
        assertEquals(expectedPartStat, attendee.getPartStat());
        return attendee;
    }

    private static Attendee extractAttendee(List<Attendee> attendees, String email) {
        if (null != attendees) {
            for (Attendee attendee : attendees) {
                String uri = attendee.getUri();
                if (null != uri && uri.toLowerCase().contains(email.toLowerCase())) {
                    return attendee;
                }
            }
        }
        return null;
    }

    /**
     * Asserts that the given attendee represented by its mail has the desired participant status
     *
     * @param attendees The attendees of the event
     * @param email The attendee to check represented by its mail
     * @param expectedPartStat The participant status of the attendee as {@link com.openexchange.chronos.ParticipationStatus}
     * @return The attendee to check as {@link com.openexchange.chronos.Attendee} object
     */
    public static com.openexchange.chronos.Attendee assertAttendeePartStat(List<com.openexchange.chronos.Attendee> attendees, String email, com.openexchange.chronos.ParticipationStatus expectedPartStat) {
        com.openexchange.chronos.Attendee matchingAttendee = null;
        if (null != attendees) {
            for (com.openexchange.chronos.Attendee attendee : attendees) {
                String uri = attendee.getUri();
                if (null != uri && uri.toLowerCase().contains(email.toLowerCase())) {
                    matchingAttendee = attendee;
                    break;
                }
            }
        }
        assertNotNull(matchingAttendee);
        assertEquals(expectedPartStat, matchingAttendee.getPartStat());
        return matchingAttendee;
    }

    /**
     * Asserts that exactly one event was handled by the server
     *
     * @param actionResponse The {@link ActionResponse} from the server
     * @return The {@link EventData} of the handled event
     */
    public static EventData assertSingleEvent(ActionResponse actionResponse) {
        return assertSingleEvent(actionResponse, null);
    }

    /**
     * Asserts that exactly one event was handled by the server
     *
     * @param actionResponse The {@link ActionResponse} from the server
     * @param uid The uid the event should have or <code>null</code>
     * @return The {@link EventData} of the handled event
     */
    public static EventData assertSingleEvent(ActionResponse actionResponse, String uid) {
        return assertEvents(actionResponse, uid, 1).get(0);
    }

    /**
     * Asserts that the given count on events was handled by the server
     *
     * @param actionResponse The {@link ActionResponse} from the server
     * @param uid The uid the event should have or <code>null</code>
     * @param size The expected size of the returned events
     * @return The {@link EventData} of the handled event
     */
    public static List<EventData> assertEvents(ActionResponse actionResponse, String uid, int size) {
        assertNotNull(actionResponse.getData());
        assertThat("Only one object should have been handled", Integer.valueOf(actionResponse.getData().size()), is(I(size)));
        List<EventData> events = new LinkedList<>();
        for (EventData eventData : actionResponse.getData()) {
            if (null != uid) {
                assertEquals(uid, eventData.getUid());
            }
            events.add(eventData);
        }
        return events;
    }

    /**
     * Asserts that a single description is given
     *
     * @param change The change to check
     * @param descriptionToMatch The string that must be part of the change description
     */
    public static void assertSingleDescription(AnalysisChange change, String descriptionToMatch) {
        assertTrue(change.getDiffDescription().size() == 1);
        assertTrue("Description does not contain expected update: (" + change.getDiffDescription().get(0) + ")", change.getDiffDescription().get(0).contains(descriptionToMatch));
    }

    /**
     * Asserts that a single description is given
     *
     * @param change The change to check
     * @param descriptionsToMatch The strings that must be part of the change description
     */
    public static void assertMultipleDescription(AnalysisChange change, String... descriptionsToMatch) {
        assertTrue(change.getDiffDescription().size() == descriptionsToMatch.length);
        Outter: for (String desc : descriptionsToMatch) {
            for (String diff : change.getDiffDescription()) {
                if (diff.contains(desc)) {
                    break Outter;
                }
            }
            fail("Unable to find desired description \"" + desc + "\" in: " + change.getDiffDescription());
        }
    }

}
