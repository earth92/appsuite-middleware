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

package com.openexchange.groupware.tasks;

/**
 * This class lists different names used for fields of a Task. TODO: Remove this enumeration and use the {@link Mapper}.
 *
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias 'Tierlieb' Prinz</a>
 */
public enum TaskField {
    OBJECTID(Task.OBJECT_ID, "ObjectID"),
    CREATEDBY(Task.CREATED_BY, "CreatedBy"),
    CREATIONDATE(Task.CREATION_DATE, "CreationDate"),
    MODIFIEDBY(Task.MODIFIED_BY, "ModifiedBy"),
    LASTMODIFIED(Task.LAST_MODIFIED, "LastModified"),
    PARENTFOLDERID(Task.FOLDER_ID, "ParentFolderID"),
    TITLE(Task.TITLE, "Title"),
    STARTDATE(Task.START_DATE, "StartDate"),
    ENDDATE(Task.END_DATE, "EndDate"),
    NOTE(Task.NOTE, "Note"),
    ACTUALCOSTS(Task.ACTUAL_COSTS, "ActualCosts"),
    ACTUALDURATION(Task.ACTUAL_DURATION, "ActualDuration"),
    BILLINGINFORMATION(Task.BILLING_INFORMATION, "BillingInformation"),
    CATEGORIES(Task.CATEGORIES, "Categories"),
    COMPANIES(Task.COMPANIES, "Companies"),
    CURRENCY(Task.CURRENCY, "Currency"),
    DATECOMPLETED(Task.DATE_COMPLETED, "DateCompleted"),
    PERCENTCOMPLETE(Task.PERCENT_COMPLETED, "PercentComplete"),
    PRIORITY(Task.PRIORITY, "Priority"),
    STATUS(Task.STATUS, "Status"),
    TARGETCOSTS(Task.TARGET_COSTS, "TargetCosts"),
    TARGETDURATION(Task.TARGET_DURATION, "TargetDuration"),
    TRIPMETER(Task.TRIP_METER, "TripMeter"),
    RECURRENCETYPE(Task.RECURRENCE_TYPE, "RecurrenceType"),
    LABEL(Task.COLOR_LABEL, "Label"),
    UID(Task.UID, "UID");

    private final int taskID; // ID used in Task.*

    private final String name; // name used for getters & setters

    private TaskField(final int taskID, final String name) {
        this.taskID = taskID;
        this.name = name;
    }

    public static TaskField getByTaskID(final int id) {
        for (final TaskField field : values()) {
            if (field.getTaskID() == id) {
                return field;
            }
        }
        return null;
    }

    public static TaskField getByName(final String name) {
        for (final TaskField field : values()) {
            if (name.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public int getTaskID() {
        return taskID;
    }

    public String getICalName() {
        return name; // TODO get real ICAL element name
    }
}
