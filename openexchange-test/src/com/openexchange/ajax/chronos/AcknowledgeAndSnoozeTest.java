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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import com.openexchange.ajax.chronos.factory.AlarmFactory;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.testing.httpclient.models.Alarm;
import com.openexchange.testing.httpclient.models.AlarmTrigger;
import com.openexchange.testing.httpclient.models.DateTimeData;
import com.openexchange.testing.httpclient.models.EventData;

/**
 *
 * {@link AcknowledgeAndSnoozeTest} tests acknowledge and snooze actions.
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.0
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class AcknowledgeAndSnoozeTest extends AbstractAlarmTriggerTest {

    /**
     * Initializes a new {@link AcknowledgeAndSnoozeTest}.
     */
    public AcknowledgeAndSnoozeTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Tests a simple acknowledgement of an alarm
     */
    @Test
    public void testSimpleAcknowledge() throws Exception {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        DateTimeData startDate = DateTimeUtil.getDateTime("UTC", cal.getTimeInMillis() + TimeUnit.DAYS.toMillis(1));
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, (cal.getTimeInMillis() + TimeUnit.HOURS.toMillis(2)));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSingleEvent(defaultUserApi.getCalUser().intValue(), "testSimpleAcknowledge", null, startDate, endDate, folderId);
        toCreate.setAlarms(Collections.singletonList(AlarmFactory.createDisplayAlarm("-PT15M")));

        EventData expectedEventData = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(expectedEventData, 1, folderId);


        //AlarmTriggerData triggerData = getAndCheckAlarmTrigger(1);
        List<AlarmTrigger> triggerData = getAndCheckAlarmTrigger(1);
        AlarmTrigger alarmTrigger = findTrigger(expectedEventData.getId(), triggerData);

        EventData getEvent = getAndAssertAlarms(expectedEventData, 1, folderId);
        assertNull(getEvent.getAlarms().get(0).getAcknowledged());

        EventData updated = eventManager.acknowledgeAlarm(expectedEventData.getId(), Integer.valueOf(alarmTrigger.getAlarmId()).intValue(), folderId);
        Long acknowledged = updated.getAlarms().get(0).getAcknowledged();
        assertNotNull(acknowledged);

        getEvent = getAndAssertAlarms(updated, 1, folderId);
        assertNotNull(getEvent.getAlarms().get(0).getAcknowledged());
        assertEquals(acknowledged, getEvent.getAlarms().get(0).getAcknowledged());
        assertTrue("Acknowdledge time (" + acknowledged.longValue() + ") is not later than the creation date (" + expectedEventData.getCreated().longValue() + ").", acknowledged.longValue() >= expectedEventData.getCreated().longValue());
    }

    /**
     * Tests the snooze function of the alarm
     */
    @Test
    public void testSimpleSnooze() throws Exception {
        DateTimeData startDate = DateTimeUtil.getDateTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        DateTimeData endDate = DateTimeUtil.incrementDateTimeData(startDate, (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)));

        // Create an event with alarm
        EventData toCreate = EventFactory.createSingleEvent(defaultUserApi.getCalUser().intValue(), "testSimpleSnooze", null, startDate, endDate, folderId);
        toCreate.setAlarms(Collections.singletonList(AlarmFactory.createDisplayAlarm("-PT15M")));

        EventData expectedEventData = eventManager.createEvent(toCreate, true);
        getAndAssertAlarms(expectedEventData, 1, folderId);

        //AlarmTriggerData triggerData = getAndCheckAlarmTrigger(1);
        List<AlarmTrigger> triggerData = getAndCheckAlarmTrigger(1);
        AlarmTrigger alarmTrigger = findTrigger(expectedEventData.getId(), triggerData);

        EventData getEvent = getAndAssertAlarms(expectedEventData, 1, folderId);
        assertNull(getEvent.getAlarms().get(0).getAcknowledged());
        int alarmId = Integer.valueOf(alarmTrigger.getAlarmId()).intValue();

        // Snooze the alarm by 5 minutes
        Calendar expectedTimeCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expectedTimeCal.setTime(new Date());
        expectedTimeCal.add(Calendar.MINUTE, 5);
        long expectedTime = expectedTimeCal.getTimeInMillis();
        EventData eventData = eventManager.snoozeAlarm(expectedEventData.getId(), alarmId, TimeUnit.MINUTES.toMillis(5), folderId);
        List<Alarm> alarms = eventData.getAlarms();
        assertEquals(2, alarms.size());
        Integer snoozeId = null;
        if (alarms.get(0).getId().toString().equals(alarmTrigger.getAlarmId())) {
            snoozeId = alarms.get(1).getId();
        } else {
            snoozeId = alarms.get(0).getId();
        }

        triggerData = getAndCheckAlarmTrigger(2);
        AlarmTrigger alarmTrigger2 = findTriggerByAlarm(snoozeId, triggerData);
        assertNotEquals(alarmTrigger2.getAlarmId(), alarmTrigger.getAlarmId());
        Date parsedTime = DateTimeUtil.parseZuluDateTime(alarmTrigger2.getTime());
        checkAlarmTimeRoughly(expectedTime, parsedTime.getTime());

        // Snooze the alarm again by 10 minutes
        expectedTimeCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expectedTimeCal.setTime(new Date());
        expectedTimeCal.add(Calendar.MINUTE, 10);
        expectedTime = expectedTimeCal.getTimeInMillis();
        EventData eventData2 = eventManager.snoozeAlarm(expectedEventData.getId(), snoozeId.intValue(), TimeUnit.MINUTES.toMillis(10), folderId);

        List<Alarm> alarms2 = eventData2.getAlarms();
        assertEquals(2, alarms2.size());
        Integer snoozeId2 = null;
        if (alarms2.get(0).getId().toString().equals(alarmTrigger.getAlarmId())) {
            snoozeId2 = alarms2.get(1).getId();
        } else {
            snoozeId2 = alarms2.get(0).getId();
        }

        triggerData = getAndCheckAlarmTrigger(2);
        alarmTrigger2 = findTriggerByAlarm(snoozeId2, triggerData);
        assertNotEquals(alarmTrigger2.getAlarmId(), alarmTrigger.getAlarmId());
        assertNotEquals(alarmTrigger2.getAlarmId(), snoozeId);
        parsedTime = DateTimeUtil.parseZuluDateTime(alarmTrigger2.getTime());
        checkAlarmTimeRoughly(expectedTime, parsedTime.getTime());

    }


    /**
     * Checks the alarm time
     * @param expectedTime The expected time
     * @param value The actual time
     */
    private void checkAlarmTimeRoughly(long expectedTime, long value) {
        Calendar exp = Calendar.getInstance();
        exp.setTimeInMillis(expectedTime);
        exp.set(Calendar.SECOND, 0);
        exp.set(Calendar.MILLISECOND, 0);

        Calendar valCal = Calendar.getInstance();
        valCal.setTimeInMillis(value);
        valCal.set(Calendar.SECOND, 0);
        valCal.set(Calendar.MILLISECOND, 0);

        assertEquals(exp.getTimeInMillis(), valCal.getTimeInMillis());
    }

}
