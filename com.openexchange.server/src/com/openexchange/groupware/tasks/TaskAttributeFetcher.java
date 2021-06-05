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

import java.util.Map;
import com.google.common.collect.ImmutableMap;
import com.openexchange.ajax.fields.CalendarFields;
import com.openexchange.ajax.fields.CommonFields;
import com.openexchange.ajax.fields.DataFields;
import com.openexchange.ajax.fields.FolderChildFields;
import com.openexchange.ajax.fields.ParticipantsFields;
import com.openexchange.ajax.fields.TaskFields;
import com.openexchange.search.SearchAttributeFetcher;

/**
 * {@link TaskAttributeFetcher}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class TaskAttributeFetcher implements SearchAttributeFetcher<Task> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AttributeGetter.class);

    private static interface AttributeGetter {

        public Object getObject(Task candidate);
    }

    private static final Map<String, AttributeGetter> GETTERS;

    static {
        ImmutableMap.Builder<String, AttributeGetter> m = ImmutableMap.builder();

        m.put(TaskFields.ACTUAL_COSTS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getActualCosts();
            }
        });

        m.put(TaskFields.ACTUAL_DURATION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getActualDuration();
            }
        });

        m.put(TaskFields.AFTER_COMPLETE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getAfterComplete();
            }
        });

        m.put(TaskFields.BILLING_INFORMATION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getBillingInformation();
            }
        });

        m.put(TaskFields.COMPANIES, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getCompanies();
            }
        });

        m.put(TaskFields.CURRENCY, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getCurrency();
            }
        });

        m.put(TaskFields.DATE_COMPLETED, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getDateCompleted();
            }
        });

        m.put(TaskFields.PERCENT_COMPLETED, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getPercentComplete());
            }
        });

        m.put(TaskFields.PRIORITY, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getPriority();
            }
        });

        m.put(TaskFields.PROJECT_ID, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getProjectID());
            }
        });

        m.put(TaskFields.STATUS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getStatus());
            }
        });

        m.put(TaskFields.TARGET_COSTS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getTargetCosts();
            }
        });

        m.put(TaskFields.TARGET_DURATION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getTargetDuration();
            }
        });

        m.put(TaskFields.TRIP_METER, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getTripMeter();
            }
        });

        /*-
         * Calendar Fields
         */

        m.put(CalendarFields.ALARM, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getAlarm();
            }
        });

        m.put(CalendarFields.CHANGE_EXCEPTIONS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getChangeException();
            }
        });

        m.put(ParticipantsFields.CONFIRM_MESSAGE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getConfirmMessage();
            }
        });

        m.put(ParticipantsFields.CONFIRMATION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getConfirm());
            }
        });

        m.put(CalendarFields.DAY_IN_MONTH, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getDayInMonth());
            }
        });

        m.put(CalendarFields.DAYS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getDays());
            }
        });

        m.put(CalendarFields.DELETE_EXCEPTIONS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getDeleteException();
            }
        });

        m.put(CalendarFields.END_DATE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getEndDate();
            }
        });

        m.put(CalendarFields.INTERVAL, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getInterval());
            }
        });

        m.put(CalendarFields.MONTH, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getMonth());
            }
        });

        m.put(CalendarFields.NOTE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getNote();
            }
        });

        m.put(CalendarFields.NOTIFICATION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Boolean.valueOf(candidate.getNotification());
            }
        });

        m.put(CalendarFields.OCCURRENCES, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getOccurrence());
            }
        });

        m.put(CalendarFields.PARTICIPANTS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getParticipants();
            }
        });

        m.put(CalendarFields.RECURRENCE_CALCULATOR, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getRecurrenceCalculator());
            }
        });

        m.put(CalendarFields.RECURRENCE_DATE_POSITION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getRecurrenceDatePosition();
            }
        });

        m.put(CalendarFields.RECURRENCE_ID, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getRecurrenceID());
            }
        });

        m.put(CalendarFields.RECURRENCE_POSITION, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getRecurrencePosition());
            }
        });

        m.put(CalendarFields.RECURRENCE_START, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                // TODO: Proper recurrence start date
                return candidate.getStartDate();
            }
        });

        m.put(CalendarFields.RECURRENCE_TYPE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getRecurrenceType());
            }
        });

        m.put(CalendarFields.START_DATE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getStartDate();
            }
        });

        m.put(CalendarFields.TITLE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getTitle();
            }
        });

        m.put(CalendarFields.UNTIL, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getUntil();
            }
        });
        m.put(CalendarFields.USERS, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getUsers();
            }
        });

        /*-
         * Common fields
         */

        m.put(CommonFields.CATEGORIES, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getCategories();
            }
        });

        m.put(CommonFields.COLORLABEL, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getLabel());
            }
        });

        m.put(DataFields.CREATED_BY, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getCreatedBy());
            }
        });

        m.put(DataFields.CREATION_DATE, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getCreationDate();
            }
        });

        m.put(FolderChildFields.FOLDER_ID, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getParentFolderID());
            }
        });

        m.put(DataFields.ID, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getObjectID());
            }
        });

        m.put(DataFields.LAST_MODIFIED, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getLastModified();
            }
        });

        m.put(DataFields.MODIFIED_BY, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Integer.valueOf(candidate.getModifiedBy());
            }
        });

        m.put(CommonFields.PRIVATE_FLAG, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return Boolean.valueOf(candidate.getPrivateFlag());
            }
        });

        m.put(CommonFields.EXTENDED_PROPERTIES, new AttributeGetter() {

            @Override
            public Object getObject(final Task candidate) {
                return candidate.getExtendedProperties();
            }
        });

        GETTERS = m.build();
    }

    private static final TaskAttributeFetcher instance = new TaskAttributeFetcher();

    /**
     * Gets the task attribute fetcher instance.
     *
     * @return The task attribute fetcher instance.
     */
    public static TaskAttributeFetcher getInstance() {
        return instance;
    }

    /**
     * Prevent instantiation.
     */
    private TaskAttributeFetcher() {
        super();
    }

    @Override
    public <T> T getAttribute(final String attributeName, final Task candidate) {
        final AttributeGetter getter = GETTERS.get(attributeName);
        if (null == getter) {
            LOG.info("No getter for field: {}", attributeName);
            return null;
        }
        return (T) getter.getObject(candidate);
    }

}
