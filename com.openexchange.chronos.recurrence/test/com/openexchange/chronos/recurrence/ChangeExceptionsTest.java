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

package com.openexchange.chronos.recurrence;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.time.TimeTools.D;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import org.dmfs.rfc5545.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.DefaultRecurrenceId;

/**
 * {@link ChangeExceptionsTest}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @since v7.10.0
 */
@RunWith(Parameterized.class)
public class ChangeExceptionsTest extends AbstractSingleTimeZoneTest {

    public ChangeExceptionsTest(String timeZone) {
        super(timeZone);
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
    }

    @Test
    public void simple() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> deleteExceptionDates = new TreeSet<RecurrenceId>();
        deleteExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(deleteExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        outer: while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
                    break;
                case 4:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                case 5:
                    compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                    break outer;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void multiple() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("01.10.2008 14:45:00", tz, false)));
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(getInstance(master, D("01.10.2008 14:45:00", tz), D("01.10.2008 18:45:00", tz), D("01.10.2008 19:45:00", tz)));
        changeExceptions.add(getInstance(master, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:12:00", tz), D("03.10.2008 18:13:00", tz)));

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        outer: while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareChangeExceptionWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 18:45:00", tz), D("01.10.2008 19:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:12:00", tz), D("03.10.2008 18:13:00", tz));
                    break;
                case 4:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                case 5:
                    compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                    break outer;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void moveBeforeFirst() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("30.09.2008 18:45:00", tz), D("30.09.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        outer: while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("30.09.2008 18:45:00", tz), D("30.09.2008 19:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 4:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                case 5:
                    compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                    break outer;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void moveOnAnother() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        outer: while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 4:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                case 5:
                    compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                    break outer;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void limit() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, I(3), changeExceptions);
        int count = 0;
        while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
                    break;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 3, count);
    }

    @Test
    public void changeOutsideLimit() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("05.10.2008 18:45:00", tz), D("05.10.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, I(3), changeExceptions);
        int count = 0;
        while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 3, count);
    }

    @Test
    public void moveOutsideRightBoundary() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, getCal("01.10.2008 14:00:00"), getCal("04.10.2008 17:00:00"), null, changeExceptions);
        int count = 0;
        while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 3, count);
    }

    @Test
    public void moveOutsideLeftBoundary() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(getInstance(master, D("03.10.2008 14:45:00", tz), D("30.09.2008 18:45:00", tz), D("30.09.2008 19:45:00", tz)));

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, getCal("01.10.2008 14:00:00"), getCal("04.10.2008 17:00:00"), null, changeExceptions);
        int count = 0;
        while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                    break;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 3, count);
    }

    @Test
    public void createFullTimeException() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("03.10.2008 00:00:00", utc), D("04.10.2008 00:00:00", utc));
        change.setStartDate(DT("03.10.2008 00:00:00", utc, true));
        change.setEndDate(DT("04.10.2008 00:00:00", utc, true));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        boolean found1 = false, found2 = false, found3 = false, found4 = false, found5 = false;
        int count = 0;
        DateTime previous = null;
        while (instances.hasNext()) {
            Event instance = instances.next();
            if (instance.getStartDate().equals(DT("01.10.2008 14:45:00", tz, false))) {
                compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                found1 = true;
            } else if (instance.getStartDate().equals(DT("02.10.2008 14:45:00", tz, false))) {
                compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                found2 = true;
            } else if (instance.getStartDate().equals(DT("03.10.2008 00:00:00", utc, true))) {
                compareFullTimeChangeExceptionWithMaster(master, instance, DT("03.10.2008 14:45:00", tz, false), DT("03.10.2008 00:00:00", utc, true), DT("04.10.2008 00:00:00", utc, true));
                found3 = true;
            } else if (instance.getStartDate().equals(DT("04.10.2008 14:45:00", tz, false))) {
                compareInstanceWithMaster(master, instance, D("04.10.2008 14:45:00", tz), D("04.10.2008 15:45:00", tz));
                found4 = true;
            } else if (instance.getStartDate().equals(DT("05.10.2008 14:45:00", tz, false))) {
                compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                found5 = true;
            } else {
                fail("Bad occurrence/exception found.");
            }
            if (count++ > 0) {
                assertNotNull(previous);
                assertFalse("Bad order of occurrences.", previous.after(instance.getStartDate()));
            }
            if (count >= 5) {
                break;
            }
            previous = instance.getStartDate();
        }
        assertTrue("Missing instance.", found1);
        assertTrue("Missing instance.", found2);
        assertTrue("Missing instance.", found3);
        assertTrue("Missing instance.", found4);
        assertTrue("Missing instance.", found5);
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void createNormalExceptionFromFullTimeSeries() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 00:00:00", "01.10.2008 00:00:00", true, utc);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 00:00:00", utc, true)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("03.10.2008 00:00:00", utc), D("03.10.2008 14:35:00", tz), D("03.10.2008 16:35:00", tz));
        change.setStartDate(DT("03.10.2008 14:35:00", tz, false));
        change.setEndDate(DT("03.10.2008 16:35:00", tz, false));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        boolean found1 = false, found2 = false, found3 = false, found4 = false, found5 = false;
        int count = 0;
        DateTime previous = null;
        while (instances.hasNext()) {
            Event instance = instances.next();
            if (instance.getStartDate().equals(DT("01.10.2008 00:00:00", utc, true))) {
                compareInstanceWithMaster(master, instance, D("01.10.2008 00:00:00", utc), D("01.10.2008 00:00:00", utc));
                found1 = true;
            } else if (instance.getStartDate().equals(DT("02.10.2008 00:00:00", utc, true))) {
                compareInstanceWithMaster(master, instance, D("02.10.2008 00:00:00", utc), D("02.10.2008 00:00:00", utc));
                found2 = true;
            } else if (instance.getStartDate().equals(DT("03.10.2008 14:35:00", tz, false))) {
                compareChangeExceptionWithFullTimeMaster(master, instance, DT("03.10.2008 00:00:00", utc, true), DT("03.10.2008 14:35:00", tz, false), DT("03.10.2008 16:35:00", tz, false));
                found3 = true;
            } else if (instance.getStartDate().equals(DT("04.10.2008 00:00:00", utc, true))) {
                compareInstanceWithMaster(master, instance, D("04.10.2008 00:00:00", utc), D("04.10.2008 00:00:00", utc));
                found4 = true;
            } else if (instance.getStartDate().equals(DT("05.10.2008 00:00:00", utc, true))) {
                compareInstanceWithMaster(master, instance, D("05.10.2008 00:00:00", utc), D("05.10.2008 00:00:00", utc));
                found5 = true;
            } else {
                fail("Bad occurrence/exception found.");
            }
            if (count++ > 0) {
                assertNotNull(previous);
                assertFalse("Bad order of occurrences.", previous.after(instance.getStartDate()));
            }
            if (count >= 5) {
                break;
            }
            previous = instance.getStartDate();
        }
        assertTrue("Missing instance.", found1);
        assertTrue("Missing instance.", found2);
        assertTrue("Missing instance.", found3);
        assertTrue("Missing instance.", found4);
        assertTrue("Missing instance.", found5);
        assertEquals("Missing instance.", 5, count);
    }

    @Test
    public void afterLastRegularOccurrence() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1;COUNT=3");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> changeExceptionDates = new TreeSet<RecurrenceId>();
        changeExceptionDates.add(new DefaultRecurrenceId(DT("02.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(changeExceptionDates);

        Event change = getInstance(master, D("02.10.2008 14:45:00", tz), D("23.10.2008 18:45:00", tz), D("23.10.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("03.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareChangeExceptionWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("23.10.2008 18:45:00", tz), D("23.10.2008 19:45:00", tz));
                    break;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 3, count);
    }

    @Test
    public void changeAndDeleteException() throws Exception {
        Event master = new Event();
        master.setRecurrenceRule("FREQ=DAILY;INTERVAL=1");
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        setStartAndEndDates(master, "01.10.2008 14:45:00", "01.10.2008 15:45:00", false, tz);
        SortedSet<RecurrenceId> exceptionDates = new TreeSet<RecurrenceId>();
        exceptionDates.add(new DefaultRecurrenceId(DT("03.10.2008 14:45:00", tz, false)));
        exceptionDates.add(new DefaultRecurrenceId(DT("04.10.2008 14:45:00", tz, false)));
        master.setDeleteExceptionDates(exceptionDates);

        Event change = getInstance(master, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
        List<Event> changeExceptions = new ArrayList<Event>();
        changeExceptions.add(change);

        Iterator<Event> instances = service.calculateInstancesRespectExceptions(master, null, null, null, changeExceptions);
        int count = 0;
        outer: while (instances.hasNext()) {
            Event instance = instances.next();
            switch (++count) {
                case 1:
                    compareInstanceWithMaster(master, instance, D("01.10.2008 14:45:00", tz), D("01.10.2008 15:45:00", tz));
                    break;
                case 2:
                    compareInstanceWithMaster(master, instance, D("02.10.2008 14:45:00", tz), D("02.10.2008 15:45:00", tz));
                    break;
                case 3:
                    compareChangeExceptionWithMaster(master, instance, D("03.10.2008 14:45:00", tz), D("03.10.2008 18:45:00", tz), D("03.10.2008 19:45:00", tz));
                    break;
                case 4:
                    compareInstanceWithMaster(master, instance, D("05.10.2008 14:45:00", tz), D("05.10.2008 15:45:00", tz));
                    break;
                case 5:
                    compareInstanceWithMaster(master, instance, D("06.10.2008 14:45:00", tz), D("06.10.2008 15:45:00", tz));
                    break outer;
                default:
                    fail("Too many instances.");
                    break;
            }
        }
        assertEquals("Missing instance.", 5, count);
    }
}
