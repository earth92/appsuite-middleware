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

package com.openexchange.ajax.appointment.bugtests;

import static org.junit.Assert.fail;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import com.openexchange.ajax.appointment.action.ConflictObject;
import com.openexchange.ajax.appointment.action.DeleteRequest;
import com.openexchange.ajax.appointment.action.InsertRequest;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.framework.AbstractAJAXSession;
import com.openexchange.ajax.framework.CommonInsertResponse;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.CalendarObject;
import com.openexchange.test.common.groupware.calendar.TimeTools;

/**
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 */
public class Bug12842Test extends AbstractAJAXSession {

    public Bug12842Test() {
        super();
    }

    /**
     * Tests if an appointment conflicts, if the new appointment is between the start and end date of an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testConflictBetween() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:      [----]
         */
        rangeTest(8, 12, 9, 11, CalendarObject.DAILY, true);
        rangeTest(8, 12, 9, 11, CalendarObject.WEEKLY, true);
        rangeTest(8, 12, 9, 11, CalendarObject.MONTHLY, true);
        rangeTest(8, 12, 9, 11, CalendarObject.YEARLY, true);
    }

    /**
     * Tests, if an appointment conflicts, if the new appointment overlaps the start date of an occurrence, but not the end date.
     *
     * @throws Throwable
     */
    @Test
    public void testConflictOverlappingStartDate() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:  [----]
         */
        rangeTest(8, 12, 7, 9, CalendarObject.DAILY, true);
        rangeTest(8, 12, 7, 9, CalendarObject.WEEKLY, true);
        rangeTest(8, 12, 7, 9, CalendarObject.MONTHLY, true);
        rangeTest(8, 12, 7, 9, CalendarObject.YEARLY, true);
    }

    /**
     * Tests, if an appointment conflicts, if the new appointment overlaps the end date of an occurrence, but not the start date.
     *
     * @throws Throwable
     */
    @Test
    public void testConflictOverlappingEndDate() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:          [----]
         */
        rangeTest(8, 12, 11, 13, CalendarObject.DAILY, true);
        rangeTest(8, 12, 11, 13, CalendarObject.WEEKLY, true);
        rangeTest(8, 12, 11, 13, CalendarObject.MONTHLY, true);
        rangeTest(8, 12, 11, 13, CalendarObject.YEARLY, true);
    }

    /**
     * Tests, if an appointment conflicts, if the the new appointment overlaps the start and end date of an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testConflictOverlapping() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:  [------------]
         */
        rangeTest(8, 12, 7, 13, CalendarObject.DAILY, true);
        rangeTest(8, 12, 7, 13, CalendarObject.WEEKLY, true);
        rangeTest(8, 12, 7, 13, CalendarObject.MONTHLY, true);
        rangeTest(8, 12, 7, 13, CalendarObject.YEARLY, true);
    }

    /**
     * Tests, if an appointment conflicts, if the the new appointment touches the start date of an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testBoundaryStart() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment: [--]
         */
        rangeTest(8, 12, 6, 8, CalendarObject.DAILY, false);
        rangeTest(8, 12, 6, 8, CalendarObject.WEEKLY, false);
        rangeTest(8, 12, 6, 8, CalendarObject.MONTHLY, false);
        rangeTest(8, 12, 6, 8, CalendarObject.YEARLY, false);
    }

    /**
     * Tests, if an appointment conflicts, if the the new appointment touches the end date of an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testBoundaryEnd() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:             [--]
         */
        rangeTest(8, 12, 12, 14, CalendarObject.DAILY, false);
        rangeTest(8, 12, 12, 14, CalendarObject.WEEKLY, false);
        rangeTest(8, 12, 12, 14, CalendarObject.MONTHLY, false);
        rangeTest(8, 12, 12, 14, CalendarObject.YEARLY, false);
    }

    /**
     * - Tests, if an appointment conflicts, if the the new appointment is before an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testBeforeStart() throws Throwable {
        /*-
         * Occurrence:      [--------]
         * Appointment:[--]
         */
        rangeTest(8, 12, 4, 6, CalendarObject.DAILY, false);
        rangeTest(8, 12, 4, 6, CalendarObject.WEEKLY, false);
        rangeTest(8, 12, 4, 6, CalendarObject.MONTHLY, false);
        rangeTest(8, 12, 4, 6, CalendarObject.YEARLY, false);
    }

    /**
     * Tests, if an appointment conflicts, if the the new appointment is after an occurrence.
     *
     * @throws Throwable
     */
    @Test
    public void testAfterEnd() throws Throwable {
        /*-
         * Occurrence:     [--------]
         * Appointment:               [--]
         */
        rangeTest(8, 12, 14, 16, CalendarObject.DAILY, false);
        rangeTest(8, 12, 14, 16, CalendarObject.WEEKLY, false);
        rangeTest(8, 12, 14, 16, CalendarObject.MONTHLY, false);
        rangeTest(8, 12, 14, 16, CalendarObject.YEARLY, false);
    }

    /**
     * Each test-method does nearly the same, there is only a small variance in the timeframe of the conflicting appointment. This Method
     * does the main work.
     *
     * @param start start hour of the sequence
     * @param end end hour of the sequence
     * @param conflictStart start hour of the conflicting appointment
     * @param conflictEnd end hour of the conflicting appointment
     * @param type recurrence type
     * @param shouldConflict
     * @throws Throwable
     */
    private void rangeTest(final int start, final int end, final int conflictStart, final int conflictEnd, final int type, final boolean shouldConflict) throws Throwable {
        AJAXClient client = null;
        Appointment appointment = new Appointment();
        final Appointment conflictAppointment = new Appointment();

        try {
            client = getClient();
            final int folderId = getClient().getValues().getPrivateAppointmentFolder();
            final TimeZone tz = getClient().getValues().getTimeZone();

            // Sequence
            appointment = new Appointment();
            appointment.setTitle("Bug12842Test");
            appointment.setParentFolderID(folderId);
            appointment.setIgnoreConflicts(true);
            Calendar calendar = TimeTools.createCalendar(tz);
            calendar.set(Calendar.DAY_OF_MONTH, 28); // Use the last possible day, which occurs in every month.
            calendar.set(Calendar.HOUR_OF_DAY, start);
            appointment.setStartDate(calendar.getTime());
            calendar.set(Calendar.HOUR_OF_DAY, end);
            appointment.setEndDate(calendar.getTime());
            appointment.setRecurrenceType(type);
            appointment.setInterval(1);

            switch (type) {
                case CalendarObject.YEARLY:
                    appointment.setMonth(calendar.get(Calendar.MONTH));
                    break;
                case CalendarObject.MONTHLY:
                    appointment.setDayInMonth(calendar.get(Calendar.DAY_OF_MONTH));
                    break;
                case CalendarObject.WEEKLY:
                    appointment.setDays((int) Math.pow(2, (calendar.get(Calendar.DAY_OF_WEEK) - 1))); // Transforming
                    // java.util.Calendar.DAY_OF_WEEK to
                    // com.openexchange.groupware.container.CalendarObject.days
                    break;
                case CalendarObject.DAILY:
                    break;
                default:
                    break;
            }

            InsertRequest request = new InsertRequest(appointment, tz);
            CommonInsertResponse response = getClient().execute(request);
            response.fillObject(appointment);

            // Conflicting appointment
            conflictAppointment.setTitle("conflict");
            conflictAppointment.setParentFolderID(folderId);
            conflictAppointment.setIgnoreConflicts(false);
            calendar = TimeTools.createCalendar(tz);
            calendar.set(Calendar.DAY_OF_MONTH, 28);

            switch (type) {
                case CalendarObject.YEARLY:
                    calendar.add(Calendar.YEAR, 1);
                    break;
                case CalendarObject.MONTHLY:
                    calendar.add(Calendar.MONTH, 1);
                    break;
                case CalendarObject.WEEKLY:
                    // Adding 2 weeks to get into the future. Appointments in the past do not conflict.
                    calendar.add(Calendar.WEEK_OF_YEAR, 2);
                    break;
                case CalendarObject.DAILY:
                    // Adding 8 days to get into the future. Appointments in the past do not conflict.
                    calendar.add(Calendar.DAY_OF_MONTH, 8);
                    break;
                default:
                    break;
            }

            calendar.set(Calendar.HOUR_OF_DAY, conflictStart);
            conflictAppointment.setStartDate(calendar.getTime());
            calendar.set(Calendar.HOUR_OF_DAY, conflictEnd);
            conflictAppointment.setEndDate(calendar.getTime());
            request = new InsertRequest(conflictAppointment, tz, false);
            response = getClient().execute(request);

            if (shouldConflict) {
                if (!response.hasConflicts()) {
                    conflictAppointment.setObjectID(response.getId());
                    conflictAppointment.setLastModified(response.getTimestamp());
                    fail("Conflict expected.");
                }
            } else {
                if (response.hasConflicts()) {
                    for (final ConflictObject conflict : response.getConflicts()) {
                        if (conflict.getTitle().startsWith("Bug12842Test")) {
                            fail("No conflict expected.");
                        }
                    }
                }
                conflictAppointment.setObjectID(response.getId());
                conflictAppointment.setLastModified(response.getTimestamp());
            }
        } finally {
            if (client != null && conflictAppointment.getObjectID() != 0 && conflictAppointment.getLastModified() != null) {
                final DeleteRequest deleteRequest = new DeleteRequest(conflictAppointment.getObjectID(), getClient().getValues().getPrivateAppointmentFolder(), conflictAppointment.getLastModified());
                getClient().execute(deleteRequest);
            }
            if (client != null && appointment.getObjectID() != 0 && appointment.getLastModified() != null) {
                final DeleteRequest deleteRequest = new DeleteRequest(appointment.getObjectID(), getClient().getValues().getPrivateAppointmentFolder(), appointment.getLastModified());
                getClient().execute(deleteRequest);
            }
        }
    }

}
