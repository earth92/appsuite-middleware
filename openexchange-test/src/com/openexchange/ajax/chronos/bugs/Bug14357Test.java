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

package com.openexchange.ajax.chronos.bugs;

import static org.junit.Assert.assertEquals;
import java.util.Calendar;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.ajax.chronos.AbstractChronosTest;
import com.openexchange.ajax.chronos.factory.EventFactory;
import com.openexchange.ajax.chronos.factory.EventFactory.RecurringFrequency;
import com.openexchange.ajax.chronos.factory.EventFactory.Weekday;
import com.openexchange.ajax.chronos.factory.RRuleFactory;
import com.openexchange.ajax.chronos.util.DateTimeUtil;
import com.openexchange.testing.httpclient.models.EventData;

/**
 *
 * {@link Bug14357Test}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.3
 */
public class Bug14357Test extends AbstractChronosTest {

    public Bug14357Test() {
        super();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        EventData event = EventFactory.createSingleTwoHourEvent(getCalendaruser(), "Bug14357Test", folderId);
        Calendar cal = Calendar.getInstance();
        cal.set(2009, 1, 1, 12, 0, 0);
        event.setStartDate(DateTimeUtil.getDateTime(cal));
        cal.set(2009, 1, 1, 13, 0, 0);
        event.setEndDate(DateTimeUtil.getDateTime(cal));
        String rrule = RRuleFactory.RRuleBuilder.create().addFrequency(RecurringFrequency.YEARLY).addByDay(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR).addBySetPosition(2).addByMonth(2).build();
        event.setRrule(rrule); // "FREQ=YEARLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=2;BYMONTH=2"
        eventManager.createEvent(event, true);
    }

    @Test
    public void testBug14357() throws Exception {
        checkYear(2010, 2);
        checkYear(2011, 2);
        checkYear(2012, 2);
        checkYear(2013, 4);
        checkYear(2014, 4);
        checkYear(2015, 3);
        checkYear(2016, 2);
        checkYear(2017, 2);
        checkYear(2018, 2);
        checkYear(2019, 4);
    }

    private void checkYear(int year, int expectedDay) throws Exception {
        Calendar expected = Calendar.getInstance();
        expected.set(year, 1, expectedDay, 12, 0, 0);
        expected.set(Calendar.MILLISECOND, 0);

        Calendar from = Calendar.getInstance();
        from.set(year, 0, 1);

        Calendar end = Calendar.getInstance();
        end.set(year, 2, 1);

        List<EventData> allEvents = eventManager.getAllEvents(from.getTime(), end.getTime(), true, folderId);
        assertEquals(1, allEvents.size());

        assertEquals(expected.getTimeInMillis(), DateTimeUtil.parseDateTime(allEvents.get(0).getStartDate()).getTime());
    }

}
