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

package com.openexchange.chronos.exception;

import com.openexchange.i18n.LocalizableStrings;

/**
 * {@link CalendarExceptionMessages}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class CalendarExceptionMessages implements LocalizableStrings {

    public static final String EVENT_NOT_FOUND_MSG = "The requested appointment was not found.";

    public static final String EVENT_SEQUENCE_NOT_FOUND_MSG = "The requested version of the appointment was not found.";

    public static final String FOLDER_NOT_FOUND_MSG = "The requested folder was not found.";

    public static final String ATTENDEE_NOT_FOUND_MSG = "The requested participant was not found.";

    public static final String ALARM_NOT_FOUND_MSG = "The requested alarm was not found.";

    public static final String ACCOUNT_NOT_FOUND_MSG = "The requested calendar account was not found.";

    public static final String PROVIDER_NOT_AVAILABLE_MSG = "The calendar provider \"%1$s\" is not available.";

    public static final String STORAGE_NOT_AVAILABLE_MSG = "The calendar storage is temporarily not available. Please try again later.";

    public static final String ACCOUNT_DISABLED_MSG = "The requested calendar account is currently disabled. Please enable the account and try again.";

    public static final String IO_ERROR_MSG = "An I/O error occurred: %1$s";

    public static final String UNSUPPORTED_OPERATION_FOR_PROVIDER_MSG = "The requested operation is not supported for calendar provider \"%1$s\".";

    public static final String NO_PERMISSION_MSG = "The operation could not be completed due to insufficient permissions.";

    public static final String NOT_ORGANIZER_MSG = "This modification can only be performed by the organizer of the appointment.";

    public static final String MISSING_ORGANIZER_MSG = "Organizer must also be an attendee.";

    public static final String DIFFERENT_ORGANIZER_MSG = "All appointments of a series must contain the same organizer.";

    public static final String MISSING_CAPABILITY_MSG = "The operation could not be completed due to missing capabilities.";

    public static final String UNSUPPORTED_FOLDER_MSG = "The supplied folder is not supported. Please select a valid folder and try again.";

    public static final String CONCURRENT_MODIFICATION_MSG = "The operation could not be completed due to a concurrent modification. Please reload the data and try again.";

    public static final String OUT_OF_SEQUENCE_MSG = "The changes could not be applied because a newer version of the appointment already exists.";

    public static final String UID_CONFLICT_MSG = "The appointment could not be created due to another conflicting appointment with the same unique identifier.";

    public static final String EVENT_CONFLICTS_MSG = "The appointment conflicts with one or more other appointments.";

    public static final String MANDATORY_FIELD_MSG = "The field \"%1$s\" is mandatory. Please supply a valid value and try again.";

    public static final String END_BEFORE_START_MSG = "The end date lies before the start date. Please correct the appointment times and try again.";

    public static final String UNSUPPORTED_CLASSIFICATION_FOR_FOLDER_MSG = "Appointments in non-personal folders must not be classified as \"private\" or \"secret\".";

    public static final String UNSUPPORTED_CLASSIFICATION_FOR_RESOURCE_MSG = "Appointments with resources must not be classified as \"secret\".";

    public static final String UNSUPPORTED_CLASSIFICATION_FOR_MOVE_MSG = "Appointments classified as \"private\" or \"secret\" cannot be moved to this type of folder.";

    public static final String UNSUPPORTED_CLASSIFICATION_FOR_OCCURRENCE_MSG = "Occurrences of appointment series must not be classified differently.";

    public static final String INVALID_RRULE_MSG = "The supplied recurrence rule is invalid. Please correct the rule and try again.";

    public static final String INVALID_ALARM_MSG = "The supplied reminder is invalid. Please correct the reminder and try again.";

    public static final String INVALID_TIMEZONE_MSG = "The supplied timezone is invalid. Please select a valid timezone and try again.";

    public static final String INVALID_GEO_LOCATION_MSG = "The supplied geographical location is invalid. Please select valid coordinates and try again.";

    public static final String INCOMPATIBLE_DATE_TYPES_MSG = "The supplied types of start and end date are incompatible. Please correct the appointment times and try again.";

    public static final String UNSUPPORTED_RRULE_MSG = "The supplied recurrence rule is not supported. Please use adjust the rule and try again.";

    public static final String INVALID_RECURRENCE_ID_MSG = "The targeted occurrence is not part of the appointment series. Please select a valid recurrence identifier and try again.";

    public static final String INVALID_SPLIT_MSG = "The split of the appointment series cannot be performed. Please select a valid split point and try again.";

    public static final String MOVE_SERIES_NOT_SUPPORTED_MSG = "Moving an appointment series into another folder is not supported.";

    public static final String MOVE_OCCURRENCE_NOT_SUPPORTED_MSG = "Moving an occurrence of an appointment series into another folder is not supported.";

    public static final String INVALID_CALENDAR_USER_MSG = "The calendar user \"%1$s\" is invalid.";

    public static final String INVALID_CONFIGURATION_MSG = "The supplied configuration is invalid. Please correct the configuration and try again.";

    public static final String QUERY_TOO_SHORT_MSG = "In order to accomplish the search, %1$d or more characters are required.";

    public static final String INCORRECT_STRING_MSG = "The character \"%1$s\" in field \"%2$s\" can't be saved. Please remove the problematic character and try again.";

    public static final String DATA_TRUNCATION_MSG = "Some data entered exceeded the field limit. Please shorten the value for \"%1$s\" (limit: %2$d, current: %3$d) and try again.";

    public static final String MAX_ACCOUNTS_EXCEEDED_MSG = "The maximum number of calendar subscriptions is exceeded.";

    public static final String IGNORED_INVALID_DATA_MSG = "The value for \"%2$s\" is invalid and wasn't applied.";

    public static final String INVALID_DATA_MSG = "The value for \"%1$s\" is invalid and cannot be applied.";

    public static final String UNSUPPORTED_DATA_MSG = "The value for \"%2$s\" is not supported and wasn't applied.";

    public static final String UNKNOWN_INTERNAL_ATTENDEE_MSG = "The '%1$s' is either not an internal user or does not exist.";

    public static final String AUTH_FAILED_MSG = "Authentication failed.";

    public static final String BAD_AUTH_CONFIGURATION_MSG = "Unable to create account. There have been issues with the provided authentication.";

    public static final String QUOTA_EXCEEDED = "The quota for account '%1$s' in context '%2$s' exceeded the limit.";

    public static final String ATTACHMENT_NOT_FOUND_MSG = "The requested attachment was not found.";

    public static final String TOO_MANY_EVENTS_MSG = "Too many appointments are queried. Please choose a shorter timeframe.";

    public static final String TOO_MANY_ATTENDEES_MSG = "The appointment contains too many participants.";

    public static final String TOO_MANY_ALARMS_MSG = "The appointment contains too many reminders.";

    public static final String TOO_MANY_CONFERENCES_MSG = "The appointment contains too many conferences.";

    public static final String UNSUPPORTED_METHOD_MSG = "The method \"%1$s\" is not supported.";
    
    public static final String WRONG_CANCELATION_MSG = "The event %1$s was not canceled for the user %2$s and won't be deleted.";

    /**
     * Initializes a new {@link CalendarExceptionMessages}.
     */
    private CalendarExceptionMessages() {
        super();
    }
}
