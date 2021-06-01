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

package com.openexchange.dav.caldav.bugs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.dav.StatusCodes;
import com.openexchange.dav.caldav.Abstract2UserCalDAVTest;
import com.openexchange.dav.caldav.ICalResource;
import com.openexchange.dav.caldav.UserAgents;
import com.openexchange.dav.caldav.ical.SimpleICal;
import com.openexchange.dav.caldav.ical.SimpleICal.Component;
import com.openexchange.dav.caldav.ical.SimpleICal.Property;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.test.CalendarTestManager;
import com.openexchange.test.common.groupware.calendar.TimeTools;

/**
 * {@link Bug64809Test} - Free time changed for other users when declining in Mac Calendar
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.2
 */
public class Bug64809Test extends Abstract2UserCalDAVTest {

    private CalendarTestManager catm2;

    @Override
    protected String getDefaultUserAgent() {
        return UserAgents.MACOS_10_7_3;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        catm2 = new CalendarTestManager(client2);
        catm2.setFailOnError(true);
    }

    @Test
    public void testTransparencyForDeclinedOccurrence() throws Exception {
        /*
         * as user b, create appointment series on server & invite user a
         */
        String uid = randomUID();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(TimeTools.D("next week at noon", TimeZone.getTimeZone("Europe/Berlin")));
        Appointment appointment = new Appointment();
        appointment.setShownAs(Appointment.RESERVED);
        appointment.setTimezone("Europe/Berlin");
        appointment.setUid(uid);
        appointment.setTitle("Bug64809Test");
        appointment.setIgnoreConflicts(true);
        appointment.setRecurrenceType(Appointment.DAILY);
        appointment.setInterval(1);
        appointment.setRecurrenceCount(20);
        appointment.setStartDate(calendar.getTime());
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        appointment.setEndDate(calendar.getTime());
        appointment.addParticipant(new UserParticipant(client2.getValues().getUserId()));
        appointment.addParticipant(new UserParticipant(getClient().getValues().getUserId()));
        appointment.setParentFolderID(catm2.getPrivateFolder());
        catm2.insert(appointment);
        /*
         * as user a, get series via caldav & decline the third occurrence
         */
        ICalResource iCalResource = get(appointment.getUid());
        assertNotNull("Event not found via CalDAV", iCalResource);
        assertEquals("Unexpected number of VEVENTs", 1, iCalResource.getVEvents().size());
        Property attendeeProperty = iCalResource.getVEvent().getAttendee(catm.getClient().getValues().getDefaultAddress());
        assertNotNull("Attendee not found", attendeeProperty);
        assertEquals("PARTSTAT wrong", "NEEDS-ACTION", attendeeProperty.getAttribute("PARTSTAT"));
        calendar.setTime(appointment.getStartDate());
        calendar.add(Calendar.DATE, 2);
        Date exceptionStart = calendar.getTime();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        Date exceptionEnd = calendar.getTime();
        Component exceptionComponent = SimpleICal.parse(iCalResource.getVEvent().toString(), "VEVENT");
        exceptionComponent.setTransp("TRANSPARENT");
        exceptionComponent.removeProperties("RRULE");
        exceptionComponent.setProperty("RECURRENCE-ID", formatAsUTC(exceptionStart));
        exceptionComponent.setDTStart(exceptionStart, appointment.getTimezone());
        exceptionComponent.setDTEnd(exceptionEnd, appointment.getTimezone());
        attendeeProperty = exceptionComponent.getAttendee(catm.getClient().getValues().getDefaultAddress());
        attendeeProperty.getAttributes().put("PARTSTAT", "DECLINED");
        iCalResource.addComponent(exceptionComponent);
        assertEquals("response code wrong", StatusCodes.SC_CREATED, putICalUpdate(iCalResource));
        /*
         * verify transparency of series and exception on server as user b
         */
        appointment = catm2.get(appointment);
        assertNotNull("Appointment not found on server", appointment);
        assertEquals("Shown as wrong", Appointment.RESERVED, appointment.getShownAs());
        List<Appointment> changeExceptions = catm2.getChangeExceptions(appointment.getParentFolderID(), appointment.getObjectID(), Appointment.ALL_COLUMNS);
        assertEquals("Unexpected number of change exceptions", 1, changeExceptions.size());
        assertEquals("Shown as wrong", Appointment.RESERVED, changeExceptions.get(0).getShownAs());
        assertNotNull(changeExceptions.get(0).getUsers());
        for (UserParticipant participant : changeExceptions.get(0).getUsers()) {
            if (catm.getClient().getValues().getUserId() == participant.getIdentifier()) {
                assertEquals("Wrong participation status", Appointment.DECLINE, participant.getConfirm());
            }
        }
        /*
         * get & check appointment via caldav as user a
         */
        iCalResource = get(appointment.getUid());
        assertNotNull("Event not found via CalDAV", iCalResource);
        assertEquals("Unexpected number of VEVENTs", 2, iCalResource.getVEvents().size());
        attendeeProperty = iCalResource.getVEvent().getAttendee(catm.getClient().getValues().getDefaultAddress());
        assertNotNull("Attendee not found", attendeeProperty);
        assertEquals("PARTSTAT wrong", "NEEDS-ACTION", attendeeProperty.getAttribute("PARTSTAT"));
        attendeeProperty = iCalResource.getVEvents().get(1).getAttendee(catm.getClient().getValues().getDefaultAddress());
        assertNotNull("Attendee not found in exception", attendeeProperty);
        assertEquals("PARTSTAT wrong in exception", "DECLINED", attendeeProperty.getAttribute("PARTSTAT"));
    }

}
