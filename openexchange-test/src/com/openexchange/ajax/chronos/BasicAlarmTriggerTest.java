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

import static com.openexchange.java.Autoboxing.L;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import com.openexchange.ajax.chronos.factory.AlarmFactory;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.ajax.chronos.manager.ChronosApiException;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.junit.Assert;
import com.openexchange.testing.httpclient.invoker.ApiException;
import com.openexchange.testing.httpclient.models.AlarmTrigger;
import com.openexchange.testing.httpclient.models.CalendarResult;
import com.openexchange.testing.httpclient.models.DateTimeData;
import com.openexchange.testing.httpclient.models.EventData;
import com.openexchange.testing.httpclient.models.EventId;
import com.openexchange.testing.httpclient.models.Trigger.RelatedEnum;

/**
 *
 * {@link BasicAlarmTriggerTest} tests alarm triggers.
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.0
 */
public class BasicAlarmTriggerTest extends AbstractUserTimezoneAlarmTriggerTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void testCreateSingleAlarmTrigger() throws Exception {
        // Create an event with alarm
        long currentTime = System.currentTimeMillis();
        DateTimeData startDate = DateTimeUtil.getDateTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        DateTimeData endDate = DateTimeUtil.getDateTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(2));

        EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testCreateSingleAlarmTrigger", startDate, endDate, AlarmFactory.createDisplayAlarm("-PT15M"), folderId);
        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        // Test alarm/until action with different time-slots
        // 1. Get alarms within the next hour
        List<AlarmTrigger> andCheckAlarmTrigger = getAndCheckAlarmTrigger(currentTime + TimeUnit.HOURS.toMillis(1), null, 0, event.getId()); // No triggers
        Assert.assertFalse(containsAlarm(andCheckAlarmTrigger, folderId, null, event.getId()));

        // 2. Get alarms within the next two days
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1); // At least one trigger

        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getFolder().equalsIgnoreCase(folderId)) {
                alarmTrigger = trigger;
                break;
            }
        }
        assertNotNull("Unable to find alarm trigger", alarmTrigger);
        assertTrue(alarmTrigger.getEventId().equals(event.getId()));
        // 3. Get only mail alarms within the next two days
        triggers = getAndCheckAlarmTrigger(currentTime + TimeUnit.DAYS.toMillis(2), "MAIL", 0, event.getId()); // No triggers
        Assert.assertFalse(containsAlarm(triggers, folderId, alarmTrigger.getAlarmId(), alarmTrigger.getEventId()));

    }

    @Test
    public void testSingleEventAlarmTriggerTime() throws Exception {

        // Create an event tomorrow 12 o clock
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, 1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testSingleEventAlarmTriggerTime", startDate, endDate, AlarmFactory.createDisplayAlarm("-PT15M"), folderId);
        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        Calendar today = Calendar.getInstance(UTC);
        today.setTime(new Date());

        // Check if next trigger is at correct time
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1); // At least one trigger

        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getFolder().equalsIgnoreCase(folderId)) {
                alarmTrigger = trigger;
                break;
            }
        }

        cal.add(Calendar.MINUTE, -15);
        checkAlarmTime(alarmTrigger, event.getId(), cal.getTimeInMillis());
    }

    @Test
    public void testSingleEventAlarmTriggerTimeAfterUpdate() throws Exception {

        // Create an event tomorrow 12 o clock
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, 1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testSingleEventAlarmTriggerTimeAfterUpdate", startDate, endDate, AlarmFactory.createDisplayAlarm("-PT15M"), folderId);
        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        Calendar today = Calendar.getInstance(UTC);
        today.setTime(new Date());

        // Check if next trigger is at correct time
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1);
        Calendar trigger1 = (Calendar) cal.clone();
        trigger1.add(Calendar.MINUTE, -15);

        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getFolder().equalsIgnoreCase(folderId)) {
                alarmTrigger = trigger;
                break;
            }
        }

        checkAlarmTime(alarmTrigger, event.getId(), trigger1.getTimeInMillis());

        // Shift the start time by one hour
        eventManager.shiftEvent(event.getId(), null, event, cal, TimeUnit.HOURS, 1, L(eventManager.getLastTimeStamp()));

        // Check if trigger time changed accordingly
        triggers = getAndCheckAlarmTrigger(1);
        Calendar trigger2 = (Calendar) cal.clone();
        trigger2.add(Calendar.HOUR, 1);
        trigger2.add(Calendar.MINUTE, -15);

        alarmTrigger = null;
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getFolder().equalsIgnoreCase(folderId)) {
                alarmTrigger = trigger;
                break;
            }
        }

        checkAlarmTime(alarmTrigger, event.getId(), trigger2.getTimeInMillis());
    }

    @Test
    public void testEventSeriesAlarmTriggerTime() throws Exception {
        // Create an event yesterday 12 o clock
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSeriesEvent(getCalendaruser(), "testSeriesAlarmTriggerTime", startDate, endDate, 4, folderId);
        toCreate.setAlarms(Collections.singletonList(AlarmFactory.createDisplayAlarm("-PT15M")));

        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        // Check if next trigger is at correct time
        long currentTime = System.currentTimeMillis();
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1); // No triggers
        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger t : triggers) {
            if (t.getEventId().equals(event.getId())) {
                alarmTrigger = t;
            }
        }
        assertNotNull("Missing trigger", alarmTrigger);
        if (currentTime < (cal.getTimeInMillis() - TimeUnit.MINUTES.toMillis(15) + TimeUnit.DAYS.toMillis(1))) {
            // The next trigger is today
            cal.add(Calendar.DAY_OF_MONTH, 1);
        } else {
            // The next trigger is tomorrow
            cal.add(Calendar.DAY_OF_MONTH, 2);
        }
        cal.add(Calendar.MINUTE, -15);
        checkAlarmTime(alarmTrigger, event.getId(), cal.getTimeInMillis());
    }

    @Test
    public void testEventSeriesAlarmTriggerTimeRoundtripForSingleUser() throws Exception {
        /*
         * 1. Create an event series and test if the time is correct
         */

        // Create an event yesterday 12 o clock
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, -1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSeriesEvent(getCalendaruser(), "testSeriesAlarmTriggerTimeRoundtrip", startDate, endDate, 4, folderId);
        toCreate.setAlarms(Collections.singletonList(AlarmFactory.createDisplayAlarm("-PT15M")));

        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        // Check if next trigger is at correct time
        long currentTime = System.currentTimeMillis();
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1); // The created alarm
        AlarmTrigger alarmTrigger = findTrigger(event.getId(), triggers);
        Calendar eventTime = Calendar.getInstance(UTC);
        eventTime.setTimeInMillis(cal.getTimeInMillis());
        if (currentTime < (cal.getTimeInMillis() - TimeUnit.MINUTES.toMillis(15) + TimeUnit.DAYS.toMillis(1))) {
            // The next trigger is today
            eventTime.add(Calendar.DAY_OF_MONTH, 1);
        } else {
            // The next trigger is tomorrow
            eventTime.add(Calendar.DAY_OF_MONTH, 2);
        }
        Calendar alarmTriggerTime = Calendar.getInstance(UTC);
        alarmTriggerTime.setTime(eventTime.getTime());
        alarmTriggerTime.add(Calendar.MINUTE, -15);
        checkAlarmTime(alarmTrigger, event.getId(), alarmTriggerTime.getTimeInMillis());

        /*
         * 2. create an exception for the event recurrence of the next trigger by shifting the start time by one hour
         */
        CalendarResult updateResult = eventManager.shiftEvent(event.getId(), alarmTrigger.getRecurrenceId(), event, eventTime, TimeUnit.HOURS, 1, L(eventManager.getLastTimeStamp()));
        assertNotNull(updateResult.getCreated());
        assertEquals(1, updateResult.getCreated().size());
        EventData exceptionEvent = updateResult.getCreated().get(0);

        // Check if trigger times are correct
        triggers = getAndCheckAlarmTrigger(2); // The alarm of the series and the alarm for the exception

        // Check the exception
        AlarmTrigger trigger = findTrigger(exceptionEvent.getId(), triggers);
        Calendar exceptionTriggerTime = Calendar.getInstance(UTC);
        exceptionTriggerTime.setTimeInMillis(alarmTriggerTime.getTimeInMillis());
        exceptionTriggerTime.add(Calendar.HOUR, 1); // Old alarm time shifted by one hour
        checkAlarmTime(trigger, exceptionEvent.getId(), exceptionTriggerTime.getTimeInMillis());

        // Check the normal alarm
        trigger = findTrigger(event.getId(), triggers);
        Calendar newAlarmTriggerTime = Calendar.getInstance(UTC);
        newAlarmTriggerTime.setTimeInMillis(alarmTriggerTime.getTimeInMillis());
        newAlarmTriggerTime.add(Calendar.DAY_OF_MONTH, 1); // Old alarm time shifted by one day (next recurrence)
        checkAlarmTime(trigger, event.getId(), newAlarmTriggerTime.getTimeInMillis());

        /*
         * 3. Delete the exception
         */
        EventId toDelete = new EventId();
        toDelete.setFolder(exceptionEvent.getFolder());
        toDelete.setId(exceptionEvent.getId());
        eventManager.deleteEvent(toDelete);

        // Check the normal alarm
        triggers = getAndCheckAlarmTrigger(1); // Only the alarm of the series
        Assert.assertFalse(containsAlarm(triggers, folderId, null, exceptionEvent.getId()));
        checkAlarmTime(findTrigger(event.getId(), triggers), event.getId(), newAlarmTriggerTime.getTimeInMillis());

        /*
         * 4. Delete series too
         */
        toDelete = new EventId();
        toDelete.setFolder(exceptionEvent.getFolder());
        toDelete.setId(event.getId());
        eventManager.deleteEvent(toDelete);

        // Check the normal alarm
        List<AlarmTrigger> emptyTriggers = getAndCheckAlarmTrigger(0); // No upcoming triggers
        Assert.assertFalse(containsAlarm(emptyTriggers, folderId, null, null));
    }

    @Test
    public void testFloatingEventAlarmTriggerTime() throws Exception {
        try {
            TimeZone tmpTimeZone = TimeZone.getTimeZone("Europe/Berlin");
            changeTimezone(tmpTimeZone);
            // Create an event tomorrow 12 o clock

            // Set floating date one day after the summer time change
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            Calendar cal = DateTimeUtil.getDaylightSavingDate(tmpTimeZone, currentYear + 1);
            cal.add(Calendar.DAY_OF_MONTH, 3);

            long offset = tmpTimeZone.getOffset(cal.getTimeInMillis());

            // Create an floating event with an alarm 3 days earlier
            DateTimeData startDate = DateTimeUtil.getDateTime(null, cal.getTimeInMillis());
            DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

            EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testFloatingEventAlarmTriggerTime", startDate, endDate, AlarmFactory.createAlarm("-PT4D", RelatedEnum.START), folderId);
            EventData event = eventManager.createEvent(toCreate, true);
            getAndAssertAlarms(event, 1, folderId);

            // Determine effective start date of event in user's timezone
            Date localStartDate;
            {
                DateTimeData localDateTimeData = new DateTimeData();
                localDateTimeData.setTzid(tmpTimeZone.getID());
                localDateTimeData.setValue(startDate.getValue());
                localStartDate = DateTimeUtil.parseDateTime(localDateTimeData);
            }

            // Check if next trigger is at correct time
            Calendar from = Calendar.getInstance(UTC);
            from.setTimeInMillis(localStartDate.getTime());
            from.add(Calendar.DAY_OF_MONTH, -5);

            List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(from.getTimeInMillis() + TimeUnit.DAYS.toMillis(10), null, 1, event.getId());
            Calendar instance = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
            instance.setTime(localStartDate);
            instance.add(Calendar.DAY_OF_MONTH, -4);

            long triggerTime = instance.getTimeInMillis();
            AlarmTrigger trigger = findTrigger(event.getId(), triggers);
            checkAlarmTime(trigger, event.getId(), triggerTime);

            // change timezone
            changeTimezone(TimeZone.getTimeZone("America/New_York"));

            List<AlarmTrigger> triggers2 = getAndCheckAlarmTrigger(from.getTimeInMillis() + TimeUnit.DAYS.toMillis(10), null, 1, event.getId());
            AlarmTrigger trigger2 = findTrigger(event.getId(), triggers2);
            Date parse = DateTimeUtil.parseZuluDateTime(trigger2.getTime());
            assertNotEquals(triggerTime, parse.getTime());

            int offsetNew = TimeZone.getTimeZone("America/New_York").getOffset(cal.getTimeInMillis());
            int offsetOld = TimeZone.getTimeZone("Europe/Berlin").getOffset(cal.getTimeInMillis());
            offset = offsetOld - offsetNew;
            checkAlarmTime(triggers2.get(0), event.getId(), triggerTime + offset);
        } finally {
            // Restore current Timezone
            changeTimezone(timeZone);
        }
    }

    @Test
    public void testPositiveDurationTrigger() throws ApiException, ParseException, ChronosApiException {
        // Create an event with an alarm with a positive duration
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, 1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testPositiveDurationTrigger", startDate, endDate, AlarmFactory.createAlarm("PT10M", RelatedEnum.START), folderId);
        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        // Get alarms within the next two days
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1); // one trigger
        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger t : triggers) {
            if (t.getEventId().equals(event.getId())) {
                alarmTrigger = t;
            }
        }
        assertNotNull("Missing trigger", alarmTrigger);
        checkAlarmTime(alarmTrigger, event.getId(), cal.getTimeInMillis() + TimeUnit.MINUTES.toMillis(10));
    }

    @Test
    public void testEndDateTrigger() throws ApiException, ParseException, ChronosApiException {
        int currentTriggers = getAlarmTriggers().size();

        // Create an event with an alarm related to the end date
        Calendar cal = DateTimeUtil.getUTCCalendar();
        cal.add(Calendar.DAY_OF_MONTH, 1);

        DateTimeData startDate = DateTimeUtil.getDateTime(cal);
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, TimeUnit.HOURS.toMillis(1));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSingleEventWithSingleAlarm(getCalendaruser(), "testPositiveDurationTrigger", startDate, endDate, AlarmFactory.createAlarm("-PT10M", RelatedEnum.END), folderId);
        EventData event = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(event, 1, folderId);

        // Get alarms within the next two days
        List<AlarmTrigger> triggers = getAndCheckAlarmTrigger(1 + currentTriggers); // one trigger
        AlarmTrigger alarmTrigger = null;
        for (AlarmTrigger trigger : triggers) {
            if (trigger.getFolder().equalsIgnoreCase(folderId)) {
                alarmTrigger = trigger;
                break;
            }
        }
        assertNotNull("No trigger found", alarmTrigger);
        checkAlarmTime(alarmTrigger, event.getId(), cal.getTimeInMillis() + TimeUnit.HOURS.toMillis(1) - TimeUnit.MINUTES.toMillis(10));
    }
}
