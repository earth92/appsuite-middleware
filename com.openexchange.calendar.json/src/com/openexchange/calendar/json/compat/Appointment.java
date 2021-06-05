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
import static com.openexchange.java.Autoboxing.L;
import java.util.HashSet;
import java.util.Set;
import com.openexchange.groupware.calendar.Constants;
import com.openexchange.groupware.container.CalendarObject;
import com.openexchange.groupware.container.Differ;

/**
 * moved from com.openexchange.groupware.container.Appointment
 *
 * The appointment object.
 *
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 */
public class Appointment extends CalendarObject implements Cloneable {

    private static final long serialVersionUID = 8822932551489524746L;

    public static final int LOCATION = 400;

    public static final int SHOWN_AS = 402;

    public static final int TIMEZONE = 408;

    public static final int RECURRENCE_START = 410;

    public static final int[] ALL_COLUMNS = {
        // From AppointmentObject itself
        LOCATION, SHOWN_AS,
        TIMEZONE,
        RECURRENCE_START,
        // From CalendarObject
        TITLE, START_DATE, END_DATE, NOTE, ALARM, RECURRENCE_ID, RECURRENCE_POSITION, RECURRENCE_DATE_POSITION, RECURRENCE_TYPE,
        CHANGE_EXCEPTIONS, DELETE_EXCEPTIONS, DAYS, DAY_IN_MONTH, MONTH, INTERVAL, UNTIL, NOTIFICATION, RECURRENCE_CALCULATOR,
        PARTICIPANTS, USERS, CONFIRMATIONS, RECURRENCE_COUNT, UID, ORGANIZER, SEQUENCE, ORGANIZER_ID, PRINCIPAL, PRINCIPAL_ID, FULL_TIME,
        // From CommonObject
        CATEGORIES, PRIVATE_FLAG,
        COLOR_LABEL, NUMBER_OF_ATTACHMENTS,
        // From FolderChildObject
        FOLDER_ID,
        // From DataObject
        OBJECT_ID, CREATED_BY, MODIFIED_BY, CREATION_DATE, LAST_MODIFIED, LAST_MODIFIED_UTC };

    public static final int[] ALL_COLUMNS_FOR_CLONE = {
        // From AppointmentObject itself
        LOCATION, SHOWN_AS,
        TIMEZONE,
        RECURRENCE_START,
        // From CalendarObject
        TITLE, START_DATE, END_DATE, NOTE, ALARM, RECURRENCE_ID, RECURRENCE_POSITION, RECURRENCE_DATE_POSITION, RECURRENCE_TYPE,
        CHANGE_EXCEPTIONS, DELETE_EXCEPTIONS, DAYS, DAY_IN_MONTH, MONTH, INTERVAL, UNTIL, NOTIFICATION, RECURRENCE_CALCULATOR,
        RECURRENCE_COUNT, UID, ORGANIZER, SEQUENCE, ORGANIZER_ID, PRINCIPAL, PRINCIPAL_ID, FULL_TIME,
        // From CommonObject
        CATEGORIES, PRIVATE_FLAG,
        COLOR_LABEL, NUMBER_OF_ATTACHMENTS,
        // From FolderChildObject
        FOLDER_ID,
        // From DataObject
        OBJECT_ID, CREATED_BY, MODIFIED_BY, CREATION_DATE, LAST_MODIFIED, LAST_MODIFIED_UTC };

    public static final int RESERVED = 1;

    public static final int TEMPORARY = 2;

    public static final int ABSENT = 3;

    public static final int FREE = 4;

    protected int DEFAULTFOLDER = -1;

    protected String location;

    protected int shown_as;

    protected int alarm;

    protected long recurring_start;

    protected boolean ignoreConflicts;

    protected String timezone;

    protected boolean ignoreOutdatedSequence;

    protected boolean b_location;

    protected boolean b_shown_as;

    protected boolean b_Alarm;

    protected boolean b_timezone;

    protected boolean b_recurring_start;

    /**
     * Initializes a new {@link Appointment}
     */
    public Appointment() {
        super();
        topic = "ox/common/appointment";
    }

    // GET METHODS
    public String getLocation() {
        return location;
    }

    public int getShownAs() {
        return shown_as;
    }

    public int getAlarm() {
        return alarm;
    }

    public boolean getIgnoreConflicts() {
        return ignoreConflicts;
    }

    public final long getRecurringStart() {
        return recurring_start;
    }

    /**
     * Returns the time zone essential for recurring appointments.
     *
     * @return the time zone if it has been set otherwise <code>null</code>.
     */
    public String getTimezone() {
        return timezone;
    }

    /**
     * Returns the time zone essential for recurring appointments.
     *
     * @return the time zone if it has been set otherwise UTC.
     * @deprecated use {@link #getTimezone()} and handle fallback to UTC yourself.
     */
    @Deprecated
    public String getTimezoneFallbackUTC() {
        if (timezone != null) {
            return timezone;
        }
        return "UTC";
    }

    /**
     * TODO Recalculation to UTC start of day must be done outside this data object.
     */
    public final void setRecurringStart(final long recurring_start) {
        this.recurring_start = recurring_start - (recurring_start % Constants.MILLI_DAY);
        b_recurring_start = true;
    }

    // SET METHODS
    public void setLocation(final String location) {
        this.location = location;
        b_location = true;
    }

    public void setShownAs(final int shown_as) {
        this.shown_as = shown_as;
        b_shown_as = true;
    }

    public void setAlarm(final int alarm) {
        this.alarm = alarm;
        b_Alarm = true;
    }

    public void setIgnoreConflicts(final boolean ignoreConflicts) {
        this.ignoreConflicts = ignoreConflicts;
    }

    public void setTimezone(final String timezone) {
        this.timezone = timezone;
        b_timezone = true;
    }

    // REMOVE METHODS
    public void removeLocation() {
        location = null;
        b_location = false;
    }

    public void removeShownAs() {
        shown_as = 0;
        b_shown_as = false;
    }

    public void removeAlarm() {
        alarm = 0;
        b_Alarm = false;
    }

    public void removeTimezone() {
        timezone = null;
        b_timezone = false;
    }

    public void removeRecurringStart() {
        recurring_start = 0;
        b_recurring_start = false;
    }

    // CONTAINS METHODS

    public boolean containsRecurringStart() {
        return b_recurring_start;
    }

    public boolean containsLocation() {
        return b_location;
    }

    public boolean containsShownAs() {
        return b_shown_as;
    }

    public boolean containsAlarm() {
        return b_Alarm;
    }

    public boolean containsTimezone() {
        return b_timezone;
    }

    public boolean isIgnoreOutdatedSequence() {
        return ignoreOutdatedSequence;
    }

    public void setIgnoreOutdatedSequence(boolean ignoreOutdatedSequence) {
        this.ignoreOutdatedSequence = ignoreOutdatedSequence;
    }

    @Override
    public void reset() {
        super.reset();

        location = null;
        shown_as = 0;
        alarm = 0;
        timezone = null;

        b_location = false;
        b_shown_as = false;
        b_Alarm = false;
        b_timezone = false;
    }

    @Override
    public int hashCode() {
        return objectId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof Appointment) {
            if (((Appointment) o).hashCode() == hashCode()) {
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public Appointment clone() {
        Appointment appointmentobject = (Appointment) super.clone();

        for (int field : ALL_COLUMNS_FOR_CLONE) {
            if (contains(field)) {
                appointmentobject.set(field, get(field));
            }
        }

        if (containsLabel()) {
            appointmentobject.setLabel(getLabel());
        }
        if (containsLocation()) {
            appointmentobject.setLocation(getLocation());
        }
        if (containsShownAs()) {
            appointmentobject.setShownAs(getShownAs());
        }
        if (containsOccurrence()) {
            appointmentobject.setOccurrence(getOccurrence());
        }
        if (containsTimezone()) {
            appointmentobject.setTimezone(getTimezoneFallbackUTC());
        }
        if (containsUid()) {
            appointmentobject.setUid(getUid());
        }
        if (containsOrganizer()) {
            appointmentobject.setOrganizer(getOrganizer());
        }
        if (containsPrincipal()) {
            appointmentobject.setPrincipal(getPrincipal());
        }
        if (containsOrganizerId()) {
            appointmentobject.setOrganizerId(getOrganizerId());
        }
        if (containsPrincipalId()) {
            appointmentobject.setPrincipalId(getPrincipalId());
        }
        return appointmentobject;
    }

    @Override
    public void set(int field, Object value) {
        switch (field) {
        case SHOWN_AS:
            setShownAs(((Integer) value).intValue());
            break;
        case ALARM:
            setAlarm(((Integer) value).intValue());
            break;
        case TIMEZONE:
            setTimezone((String) value);
            break;
        case RECURRENCE_START:
            setRecurringStart(((Long) value).longValue());
            break;
        case LOCATION:
            setLocation((String) value);
            break;
        default:
            super.set(field, value);
        }
    }

    @Override
    public Object get(int field) {
        switch (field) {
        case SHOWN_AS:
            return I(getShownAs());
        case ALARM:
            return I(getAlarm());
        case TIMEZONE:
            return getTimezone();
        case RECURRENCE_START:
            return L(getRecurringStart());
        case LOCATION:
            return getLocation();
        default:
            return super.get(field);
        }
    }

    @Override
    public boolean contains(int field) {
        switch (field) {
        case SHOWN_AS:
            return containsShownAs();
        case ALARM:
            return containsAlarm();
        case TIMEZONE:
            return containsTimezone();
        case RECURRENCE_START:
            return containsRecurringStart();
        case LOCATION:
            return containsLocation();
        default:
            return super.contains(field);
        }
    }

    @Override
    public void remove(int field) {
        switch (field) {
        case SHOWN_AS:
            removeShownAs();
            break;
        case ALARM:
            removeAlarm();
            break;
        case TIMEZONE:
            removeTimezone();
            break;
        case RECURRENCE_START:
            removeRecurringStart();
            break;
        case LOCATION:
            removeLocation();
            break;
        default:
            super.remove(field);
        }
    }

    public static Set<Differ<? super Appointment>> differ = new HashSet<Differ<? super Appointment>>();

    static {
        differ.addAll(CalendarObject.differ);
    }

}
