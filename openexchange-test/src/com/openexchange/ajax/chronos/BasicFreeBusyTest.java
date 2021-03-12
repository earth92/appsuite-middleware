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

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.l;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import com.openexchange.ajax.chronos.factory.AttendeeFactory;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.test.pool.TestUser;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.Attendee;
import com.openexchange.testing.httpclient.models.Attendee.CuTypeEnum;
import com.openexchange.testing.httpclient.models.ChronosFreeBusyResponse;
import com.openexchange.testing.httpclient.models.ChronosFreeBusyResponseData;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.EventData.TranspEnum;
import com.openexchange.testing.httpclient.models.EventId;
import com.openexchange.testing.httpclient.models.FreeBusyBody;
import com.openexchange.testing.httpclient.models.FreeBusyTime;

/**
 *
 * {@link BasicFreeBusyTest}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.0
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class BasicFreeBusyTest extends AbstractChronosTest {

    @SuppressWarnings("hiding")
    private String folderId;

    private EventData createSingleEvent(String summary, long startDate, long endDate, List<Attendee> attendees) {
        EventData singleEvent = new EventData();
        singleEvent.setPropertyClass("PUBLIC");
        if (attendees == null) {
            singleEvent.setAttendees(Collections.singletonList(AttendeeFactory.createIndividual(I(apiClient.getUserId().intValue()))));
        } else {
            singleEvent.setAttendees(attendees);
        }
        singleEvent.setStartDate(DateTimeUtil.getDateTime(startDate));
        singleEvent.setEndDate(DateTimeUtil.getDateTime(endDate));
        singleEvent.setTransp(TranspEnum.OPAQUE);
        singleEvent.setSummary(summary);
        return singleEvent;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        folderId = createAndRememberNewFolder(defaultUserApi, getDefaultFolder(), getCalendaruser());
    }

    @Test
    public void testMaskId() throws Exception {
        Date now = new Date();
        long day1 = 1000 * (now.getTime() / 1000);
        long day3 = day1 + TimeUnit.DAYS.toMillis(2);
        long day5 = day3 + TimeUnit.DAYS.toMillis(2);
        long nextWeek = day1 + TimeUnit.DAYS.toMillis(7);
        createEvent("dayOne", day1, day1 + TimeUnit.HOURS.toMillis(1));
        EventData createEvent = createEvent("dayThree", day3, day3 + TimeUnit.HOURS.toMillis(1));
        createEvent("dayFive", day5, day5 + TimeUnit.HOURS.toMillis(1));

        ChronosFreeBusyResponse freeBusy = chronosApi.freebusy(DateTimeUtil.getZuluDateTime(day1).getValue(), DateTimeUtil.getZuluDateTime(nextWeek).getValue(), createAttendeesBody(getCalendaruser()), createEvent.getId(), Boolean.TRUE);

        assertEquals(freeBusy.getError(), null, freeBusy.getErrorDesc());
        assertNotNull(freeBusy.getData());
        List<ChronosFreeBusyResponseData> data = freeBusy.getData();
        //Expect one result for the given attendee
        assertEquals(1, data.size());
        List<FreeBusyTime> freeBusyTimes = data.get(0).getFreeBusyTime();

        // Expect free busy times events on day one and five, but not for day 3
        FreeBusyTime time1 = null;
        FreeBusyTime time3 = null;
        FreeBusyTime time5 = null;
        for (FreeBusyTime freeBusyTime : freeBusyTimes) {
            if (day1 == l(freeBusyTime.getStartTime())) {
                time1 = freeBusyTime;
            } else if (day3 == l(freeBusyTime.getStartTime())) {
                time3 = freeBusyTime;
            } else if (day5 == l(freeBusyTime.getStartTime())) {
                time5 = freeBusyTime;
            }
        }

        assertNotNull("No free/busy time for event on " + day1, time1);
        assertEquals(day1 + TimeUnit.HOURS.toMillis(1), time1.getEndTime().longValue());
        assertEquals("BUSY", time1.getFbType());

        assertNotNull("No free/busy time for event on " + day5, time5);
        assertEquals(day5 + TimeUnit.HOURS.toMillis(1), time5.getEndTime().longValue());
        assertEquals("BUSY", time5.getFbType());

        assertNull("Unexpected free/busy time for masked event on " + day3, time3);
    }

    private EventData createEvent(String summary, long start, long end, TestUser... users) throws ApiException {
        List<Attendee> attendees = null;
        if (users != null && users.length > 0) {
            attendees = new ArrayList<>(users.length);
            for (TestUser user : users) {
                Attendee att = new Attendee();
                att.setCuType(CuTypeEnum.INDIVIDUAL);
                att.setEntity(user.getUserId());
                attendees.add(att);
            }
        }
        EventData event = createSingleEvent(summary, start, end, attendees);
        event.setFolder(folderId);
        event = eventManager.createEvent(event, true);
        EventId eventId = new EventId();
        eventId.setId(event.getId());
        eventId.setFolder(folderId);
        rememberEventId(eventId);
        return event;
    }

    private FreeBusyBody createAttendeesBody(int... attendees) {
        FreeBusyBody b = new FreeBusyBody();
        for (int i : attendees) {
            Attendee attendee = new Attendee();
            attendee.setCuType(CuTypeEnum.INDIVIDUAL);
            attendee.setEntity(Integer.valueOf(i));
            b.addAttendeesItem(attendee);
        }
        return b;
    }

}
