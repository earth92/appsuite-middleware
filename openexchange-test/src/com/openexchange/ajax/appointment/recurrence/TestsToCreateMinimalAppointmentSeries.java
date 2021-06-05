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

package com.openexchange.ajax.appointment.recurrence;

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.fail;
import java.util.Calendar;
import org.junit.Test;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.calendar.OXCalendarExceptionCodes;
import com.openexchange.groupware.container.Appointment;
import com.openexchange.groupware.container.Changes;
import com.openexchange.groupware.container.Expectations;

/**
 * Find out which parameters are needed to create an appointment and which are not enough.
 *
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a>
 */
public class TestsToCreateMinimalAppointmentSeries extends ManagedAppointmentTest {

    public TestsToCreateMinimalAppointmentSeries() {
        super();
    }

    public void _testShouldFailWhenSendingUnneccessaryDayInformationForDailyAppointment() {
        /*
         * TODO: Fix!
         * This test fails as long as the Server side JSON-Writer/Parser is used.
         *
         * Details:
         * This does not send an exception, because the current JSON-writer does not set the days-value as it is not necessary for daily recurrence.
         */
        fail("Fails until an independent parser/writer is used for creating JSON-Objects");
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.DAILY));
        changes.put(Appointment.RECURRENCE_COUNT, I(7));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAYS, I(127));

        negativeAssertionOnCreate.check(changes, new OXException(998));
        negativeAssertionOnUpdate.check(changes, new OXException(999));
    }

    @Test
    public void testShouldCreateDailyIntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.DAILY));
        changes.put(Appointment.INTERVAL, I(1));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldCreateWeeklyIntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.WEEKLY));
        changes.put(Appointment.INTERVAL, I(1));

        Expectations expectations = new Expectations(changes);
        expectations.put(Appointment.DAYS, I(127)); // Should default to 127 as per HTTP API

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldCreateWeeklyIntervalWithDaysFieldDifferentThan127() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.WEEKLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAYS, I(Appointment.MONDAY + Appointment.TUESDAY));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldFailCreatingMonthlyIntervalWithoutDayInMonthInfo() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.MONTHLY));
        changes.put(Appointment.INTERVAL, I(1));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
    }

    @Test
    public void testShouldFailCreatingMonthly2IntervalWithoutDayInMonthInfo() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.MONTHLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAYS, I(Appointment.MONDAY));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
    }

    // first day every month    @Test
    @Test
    public void testShouldCreateMonthlyIntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.MONTHLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAY_IN_MONTH, I(1));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    // first monday every month    @Test
    @Test
    public void testShouldCreateMonthly2IntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.MONTHLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAY_IN_MONTH, I(1));
        changes.put(Appointment.DAYS, I(Appointment.MONDAY));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldFailCreatingYearlyIntervalWithoutDayInMonthInfo() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.YEARLY));
        changes.put(Appointment.INTERVAL, I(1));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
    }

    @Test
    public void testShouldFailCreatingYearly2IntervalWithoutDayInMonthInfo() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.YEARLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAYS, I(Appointment.MONDAY));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create());
    }

    @Test
    public void testShouldFailCreatingYearlyIntervalWithoutMonth() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.YEARLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAY_IN_MONTH, I(1));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_MONTH.create(I(1)));
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_MONTH.create(I(1)));
    }

    @Test
    public void testShouldCreateYearlyIntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.YEARLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAY_IN_MONTH, I(1));
        changes.put(Appointment.MONTH, I(Calendar.JANUARY));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldCreateYearly2IntervalWithMinimalData() throws Exception {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.YEARLY));
        changes.put(Appointment.INTERVAL, I(1));
        changes.put(Appointment.DAY_IN_MONTH, I(1));
        changes.put(Appointment.DAYS, I(Appointment.MONDAY));
        changes.put(Appointment.MONTH, I(Calendar.JANUARY));

        Expectations expectations = new Expectations(changes);

        positiveAssertionOnCreate.check(changes, expectations);
        positiveAssertionOnCreateAndUpdate.check(changes, new Expectations(changes));
    }

    @Test
    public void testShouldFailCreatingIntervalWithoutIntervalInformation() {
        Changes changes = new Changes();
        changes.put(Appointment.RECURRENCE_TYPE, I(Appointment.DAILY));

        negativeAssertionOnCreate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_INTERVAL.create());
        negativeAssertionOnUpdate.check(changes, OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_INTERVAL.create());
    }

}
