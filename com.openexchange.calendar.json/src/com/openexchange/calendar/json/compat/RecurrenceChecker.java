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

package com.openexchange.calendar.json.compat;

import static com.openexchange.java.Autoboxing.I;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.calendar.OXCalendarExceptionCodes;
import com.openexchange.groupware.container.CalendarObject;

/**
 * moved from com.openexchange.calendar.RecurrenceChecker
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 */
public class RecurrenceChecker {

    public static void check(final CalendarObject cdao) throws OXException {
        if (!containsRecurrenceInformation(cdao)) {
            return;
        }

        if (!cdao.containsRecurrenceType()) {
            throw OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_TYPE.create();
        }

        if (cdao.getRecurrenceType() == CalendarObject.NO_RECURRENCE) {
            checkNo(cdao);
            return;
        }

        if (!cdao.containsInterval()) {
            throw OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_INTERVAL.create();
        }

        if (cdao.containsUntil() && cdao.containsOccurrence() && cdao.getUntil() != null && cdao.getOccurrence() != 0) {
            throw OXCalendarExceptionCodes.REDUNDANT_UNTIL_OCCURRENCES.create();
        }

        if (cdao.getRecurrenceType() == CalendarObject.DAILY) {
            checkDaily(cdao);
        }

        if (cdao.getRecurrenceType() == CalendarObject.WEEKLY) {
            checkWeekly(cdao);
        }

        if (cdao.getRecurrenceType() == CalendarObject.MONTHLY) {
            checkMonthly(cdao);
        }

        if (cdao.getRecurrenceType() == CalendarObject.YEARLY) {
            checkYearly(cdao);
        }
    }

    private static void checkNo(final CalendarObject cdao) throws OXException {
        if (containsRecurrenceInformation(cdao, CalendarObject.RECURRENCE_TYPE)) {
            throw OXCalendarExceptionCodes.UNNECESSARY_RECURRENCE_INFORMATION_NO.create();
        }
    }

    private static void checkDaily(final CalendarObject cdao) throws OXException {
        if (cdao.containsDays()) {
            throw unnecessary("days", "daily");
        }

        if (cdao.containsDayInMonth()) {
            throw unnecessary("dayInMonth", "daily");
        }

        if (cdao.containsMonth()) {
            throw unnecessary("month", "daily");
        }
    }

    private static void checkWeekly(final CalendarObject cdao) throws OXException {
        if (cdao.containsDayInMonth()) {
            throw unnecessary("dayInMonth", "weekly");
        }

        if (cdao.containsMonth()) {
            throw unnecessary("month", "weekly");
        }

        if (!cdao.containsDays()) {
            throw OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_WEEKDAY.create();
        }

        final int days = cdao.getDays();
        if (days < 1 || days > 127) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_OR_WRONG_VALUE_DAYS.create(Integer.valueOf(days));
        }
    }

    private static void checkMonthly(final CalendarObject cdao) throws OXException {
        if (cdao.containsMonth()) {
            throw unnecessary("month", "monthly");
        }

        if (!cdao.containsDayInMonth()) {
            throw OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create();
        }

        if (!cdao.containsDays()) {
            checkMonthly1(cdao);
        } else {
            checkMonthly2(cdao);
        }
    }

    private static void checkMonthly1(final CalendarObject cdao) throws OXException {
        if (cdao.containsDays()) {
            throw unnecessary("days", "monthly1");
        }

        if (cdao.getDayInMonth() < 1 || cdao.getDayInMonth() > 31) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_MONTLY_INTERVAL.create(Integer.valueOf(cdao.getDayInMonth()));
        }
    }

    private static void checkMonthly2(final CalendarObject cdao) throws OXException {
        if (!cdao.containsDays()) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_OR_WRONG_VALUE_DAYS.create();
        }

        if (cdao.getDays() < 1 || cdao.getDays() > 127) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_OR_WRONG_VALUE_DAYS.create(Integer.valueOf(cdao.getDays()));
        }

        if (cdao.getDayInMonth() < 1 || cdao.getDayInMonth() > 5) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_MONTLY_DAY_2.create(Integer.valueOf(cdao.getDayInMonth()));
        }
    }

    private static void checkYearly(final CalendarObject cdao) throws OXException {
        if (!cdao.containsDayInMonth()) {
            throw OXCalendarExceptionCodes.INCOMPLETE_REC_INFOS_MONTHDAY.create();
        }

        if (!cdao.containsMonth()) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_MONTH.create(I(-1));
        }

        if (!cdao.containsDays()) {
            checkYearly1(cdao);
        } else {
            checkYearly2(cdao);
        }
    }

    private static void checkYearly1(final CalendarObject cdao) throws OXException {
        if (cdao.containsDays()) {
            throw unnecessary("days", "yearly1");
        }

        if (cdao.getDayInMonth() < 1 || cdao.getDayInMonth() > 31) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_INTERVAL.create(Integer.valueOf(cdao.getDayInMonth()));
        }

        checkDayInMonthOnYearly1(cdao);
    }

    private static void checkDayInMonthOnYearly1(CalendarObject cdao) throws OXException {
        boolean fail = false;
        switch (cdao.getMonth()) {
        case Calendar.FEBRUARY:
            fail = cdao.getDayInMonth() > 29; break;
        case Calendar.APRIL:
        case Calendar.JUNE:
        case Calendar.SEPTEMBER:
        case Calendar.NOVEMBER:
            fail = cdao.getDayInMonth() > 30; break;
        }

        if (fail) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_INTERVAL.create(Integer.valueOf(cdao.getDayInMonth()));
        }
    }

    private static void checkYearly2(final CalendarObject cdao) throws OXException {
        if (!cdao.containsDays()) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_OR_WRONG_VALUE_DAYS.create();
        }

        if (cdao.getDays() < 1 || cdao.getDays() > 127) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_OR_WRONG_VALUE_DAYS.create(Integer.valueOf(cdao.getDays()));
        }

        if (cdao.getDayInMonth() < 1 || cdao.getDayInMonth() > 5) {
            throw OXCalendarExceptionCodes.RECURRING_MISSING_YEARLY_INTERVAL.create(Integer.valueOf(cdao.getDayInMonth()));
        }
    }

    /**
     * Checks, if the given CalendarDataObject contains any recurrence information.
     *
     * @param cdao
     * @param exceptions Recurrence fields, which will not be checked
     * @return
     */
    public static boolean containsRecurrenceInformation(final CalendarObject cdao, final int... exceptions) {
        final Set<Integer> recurrenceFields = new HashSet<Integer>(6);
        recurrenceFields.add(Integer.valueOf(CalendarObject.INTERVAL));
        recurrenceFields.add(Integer.valueOf(CalendarObject.DAYS));
        recurrenceFields.add(Integer.valueOf(CalendarObject.DAY_IN_MONTH));
        recurrenceFields.add(Integer.valueOf(CalendarObject.MONTH));
        recurrenceFields.add(Integer.valueOf(CalendarObject.RECURRENCE_COUNT));
        recurrenceFields.add(Integer.valueOf(CalendarObject.UNTIL));

        boolean skipType = false;

        final int recurrenceType = CalendarObject.RECURRENCE_TYPE;
        for (final int exception : exceptions) {
            recurrenceFields.remove(Integer.valueOf(exception));
            if (exception == recurrenceType) {
                skipType = true;
            }
        }

        if (!skipType && cdao.contains(recurrenceType) && ((Integer) cdao.get(recurrenceType)).intValue() != CalendarObject.NO_RECURRENCE) {
            return true;
        }

        for (final int recurrenceField : recurrenceFields) {
            if (cdao.contains(recurrenceField)) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsOneOfFields(final CalendarObject cdao, final int... fields) {
        for (final int field : fields) {
            if (cdao.contains(field)) {
                return true;
            }
        }
        return false;
    }

    private static OXException unnecessary(final String field, final String type) {
        return OXCalendarExceptionCodes.UNNECESSARY_RECURRENCE_INFORMATION.create(field, type);
    }

}
