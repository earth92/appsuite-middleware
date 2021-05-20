/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.chronos.impl;

import static com.openexchange.chronos.common.CalendarUtils.contains;
import static com.openexchange.chronos.common.CalendarUtils.find;
import static com.openexchange.chronos.common.CalendarUtils.getExceptionDateUpdates;
import static com.openexchange.chronos.common.CalendarUtils.getRecurrenceIds;
import static com.openexchange.chronos.common.CalendarUtils.hasFurtherOccurrences;
import static com.openexchange.chronos.common.CalendarUtils.isAttendee;
import static com.openexchange.chronos.common.CalendarUtils.isClassifiedFor;
import static com.openexchange.chronos.common.CalendarUtils.isGroupScheduled;
import static com.openexchange.chronos.common.CalendarUtils.isInternal;
import static com.openexchange.chronos.common.CalendarUtils.isLastNonHiddenUserAttendee;
import static com.openexchange.chronos.common.CalendarUtils.isLastUserAttendee;
import static com.openexchange.chronos.common.CalendarUtils.isOrganizer;
import static com.openexchange.chronos.common.CalendarUtils.isSeriesMaster;
import static com.openexchange.chronos.common.CalendarUtils.matches;
import static com.openexchange.chronos.common.CalendarUtils.optTimeZone;
import static com.openexchange.chronos.common.SearchUtils.getSearchTerm;
import static com.openexchange.chronos.compat.Event2Appointment.asInt;
import static com.openexchange.chronos.service.CalendarParameters.PARAMETER_CONNECTION;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.b;
import static com.openexchange.java.Autoboxing.i2I;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import com.openexchange.annotation.NonNull;
import com.openexchange.chronos.Attendee;
import com.openexchange.chronos.AttendeeField;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.CalendarStrings;
import com.openexchange.chronos.CalendarUser;
import com.openexchange.chronos.CalendarUserType;
import com.openexchange.chronos.Classification;
import com.openexchange.chronos.DelegatingEvent;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.Organizer;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.RecurrenceId;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DataAwareRecurrenceId;
import com.openexchange.chronos.common.DefaultCalendarObjectResource;
import com.openexchange.chronos.common.DefaultCalendarParameters;
import com.openexchange.chronos.common.DefaultRecurrenceData;
import com.openexchange.chronos.common.mapping.AttendeeMapper;
import com.openexchange.chronos.common.mapping.EventMapper;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.ical.LastRuleAware;
import com.openexchange.chronos.impl.osgi.Services;
import com.openexchange.chronos.impl.performer.AttendeeUsageTracker;
import com.openexchange.chronos.impl.performer.ResolvePerformer;
import com.openexchange.chronos.impl.session.DefaultEntityResolver;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.scheduling.ChangeNotification;
import com.openexchange.chronos.scheduling.SchedulingBroker;
import com.openexchange.chronos.scheduling.SchedulingMessage;
import com.openexchange.chronos.service.CalendarConfig;
import com.openexchange.chronos.service.CalendarEvent;
import com.openexchange.chronos.service.CalendarEventNotificationService;
import com.openexchange.chronos.service.CalendarHandler;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EntityResolver;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.EventUpdate;
import com.openexchange.chronos.service.RecurrenceData;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.type.PrivateType;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.folderstorage.type.SharedType;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.modules.Module;
import com.openexchange.groupware.tools.mappings.Mapping;
import com.openexchange.groupware.tools.mappings.common.CollectionUpdate;
import com.openexchange.groupware.tools.mappings.common.ItemUpdate;
import com.openexchange.groupware.tools.mappings.common.SimpleCollectionUpdate;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.java.Strings;
import com.openexchange.quota.Quota;
import com.openexchange.quota.QuotaType;
import com.openexchange.quota.groupware.AmountQuotas;
import com.openexchange.search.CompositeSearchTerm;
import com.openexchange.search.CompositeSearchTerm.CompositeOperation;
import com.openexchange.search.SearchTerm;
import com.openexchange.search.SingleSearchTerm.SingleOperation;
import com.openexchange.search.internal.operands.ColumnFieldOperand;
import com.openexchange.server.ServiceLookup;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.server.impl.OCLPermission;
import com.openexchange.session.Session;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.oxfolder.property.FolderSubscriptionHelper;
import com.openexchange.tools.session.ServerSessionAdapter;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link Utils}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class Utils {

    /** The fixed identifier for an account of the internal calendar provider */
    public static final int ACCOUNT_ID = CalendarAccount.DEFAULT_ACCOUNT.getAccountId();

    /** The fixed identifier for the internal calendar provider */
    public static final String PROVIDER_ID = CalendarAccount.DEFAULT_ACCOUNT.getProviderId();

    /** The event fields that are also available if an event's classification is not {@link Classification#PUBLIC} */
    public static final EventField[] NON_CLASSIFIED_FIELDS = {
        EventField.CLASSIFICATION, EventField.CREATED, EventField.UID, EventField.FILENAME, EventField.CREATED_BY,
        EventField.CALENDAR_USER, EventField.CHANGE_EXCEPTION_DATES, EventField.DELETE_EXCEPTION_DATES, EventField.END_DATE,
        EventField.ID, EventField.TIMESTAMP, EventField.MODIFIED_BY, EventField.FOLDER_ID, EventField.SERIES_ID,
        EventField.RECURRENCE_RULE, EventField.SEQUENCE, EventField.START_DATE, EventField.TRANSP
    };

    /**
     * Attendee fields that, when modified, indicate that a <i>reply</i> of the associated calendar object resource is assumed, usually
     * leading to appropriate notifications and scheduling messages being sent out to the organizer.
     */
    private static final AttendeeField[] REPLY_FIELDS = { AttendeeField.PARTSTAT, AttendeeField.COMMENT };

    /**
     * Event fields that, when modified, indicate that a <i>re-scheduling</i> of the calendar object resource is assumed, usually leading
     * to appropriate notifications and scheduling messages being sent out to the attendees.
     * <p/>
     * This does not contain {@link EventField#ATTENDEES}, as some more sophisticated logic to determine re-schedulings is required here.
     */
    private static final EventField[] RESCHEDULE_FIELDS = new EventField[] {
        EventField.SUMMARY, EventField.LOCATION, EventField.DESCRIPTION, EventField.ATTACHMENTS, EventField.GEO, EventField.CONFERENCES,
        EventField.ORGANIZER, EventField.START_DATE, EventField.END_DATE, EventField.TRANSP, EventField.RECURRENCE_RULE
    };

    /** Private fields of {@link SimpleTimeZone} that are accessed when comparing the underlying transition rules of timezones */
    private static final java.lang.reflect.Field[] SIMPLETIMEZONE_RULE_FIELDS = initSimpleTimeZoneRuleFields();

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Utils.class);

    /**
     * Gets a value indicating whether the current calendar user should be added as default attendee to events implicitly or not,
     * independently of the event being group-scheduled or not, based on the value of {@link CalendarParameters#PARAMETER_DEFAULT_ATTENDEE}
     * in the supplied parameters.
     * <p/>
     * If the <i>legacy</i> storage is in use, the default attendee is enforced statically.
     *
     * @param session The calendar session to evaluate
     * @return <code>true</code> the current calendar user should be added as default attendee to events implicitly, <code>false</code>, otherwise
     * @see CalendarParameters#PARAMETER_DEFAULT_ATTENDEE
     */
    public static boolean isEnforceDefaultAttendee(CalendarSession session) {
        // enabled by default for now (as legacy storage still in use)
        return session.get(CalendarParameters.PARAMETER_DEFAULT_ATTENDEE, Boolean.class, Boolean.TRUE).booleanValue();
    }

    /**
     * Gets a value indicating whether a recurring event series should be resolved to individual occurrences or not, based on the value
     * of {@link CalendarParameters#PARAMETER_EXPAND_OCCURRENCES} in the supplied parameters.
     *
     * @param parameters The calendar parameters to evaluate
     * @return <code>true</code> if individual occurrences should be resolved, <code>false</code>, otherwise
     * @see CalendarParameters#PARAMETER_EXPAND_OCCURRENCES
     */
    public static boolean isResolveOccurrences(CalendarParameters parameters) {
        return parameters.get(CalendarParameters.PARAMETER_EXPAND_OCCURRENCES, Boolean.class, Boolean.FALSE).booleanValue();
    }

    /**
     * Gets a value indicating whether (soft) conflicts of internal attendees should be checked during event creation or update or not,
     * based on the value of {@link CalendarParameters#PARAMETER_CHECK_CONFLICTS} in the supplied parameters.
     *
     * @param parameters The calendar parameters to evaluate
     * @return <code>true</code> if (soft) conflicts should be checked, <code>false</code>, otherwise
     * @see CalendarParameters#PARAMETER_CHECK_CONFLICTS
     */
    public static boolean isCheckConflicts(CalendarParameters parameters) {
        return parameters.get(CalendarParameters.PARAMETER_CHECK_CONFLICTS, Boolean.class, Boolean.FALSE).booleanValue();
    }

    /**
     * Gets a value indicating whether the checks of (external) attendee URIs are disabled or not, considering both the calendar
     * parameters and general configuration.
     *
     * @param session The calendar session to evaluate
     * @return <code>true</code> if the URI checks are disabled, <code>false</code>, otherwise
     * @see CalendarParameters#PARAMETER_SKIP_EXTERNAL_ATTENDEE_URI_CHECKS
     * @see CalendarConfig#isSkipExternalAttendeeURIChecks()
     */
    public static boolean isSkipExternalAttendeeURIChecks(CalendarSession session) {
        return b(session.get(CalendarParameters.PARAMETER_SKIP_EXTERNAL_ATTENDEE_URI_CHECKS, Boolean.class, Boolean.FALSE)) ||
             session.getConfig().isSkipExternalAttendeeURIChecks();
    }

    /**
     * Gets the timezone valid for the supplied calendar session, which is either the (possibly overridden) timezone defined via
     * {@link CalendarParameters#PARAMETER_TIMEZONE}, or as fallback, the session user's default timezone.
     *
     * @param session The calendar session to get the timezone for
     * @return The timezone
     * @see CalendarParameters#PARAMETER_TIMEZONE
     * @see User#getTimeZone()
     */
    public static TimeZone getTimeZone(CalendarSession session) throws OXException {
        TimeZone timeZone = session.get(CalendarParameters.PARAMETER_TIMEZONE, TimeZone.class);
        return null != timeZone ? timeZone : session.getEntityResolver().getTimeZone(session.getUserId());
    }

    /**
     * Extracts the "from" date used for range-queries from the parameter {@link CalendarParameters#PARAMETER_RANGE_START}.
     *
     * @param parameters The calendar parameters to evaluate
     * @return The "from" date, or <code>null</code> if not set
     */
    public static Date getFrom(CalendarParameters parameters) {
        return parameters.get(CalendarParameters.PARAMETER_RANGE_START, Date.class);
    }

    /**
     * Extracts the "until" date used for range-queries from the parameter {@link CalendarParameters#PARAMETER_RANGE_END}.
     *
     * @param parameters The calendar parameters to evaluate
     * @return The "until" date, or <code>null</code> if not set
     */
    public static Date getUntil(CalendarParameters parameters) {
        return parameters.get(CalendarParameters.PARAMETER_RANGE_END, Date.class);
    }

    /**
     * Gets a value indicating whether two email addresses share the same domain part.
     *
     * @param email1 The first email address to check
     * @param email2 The second email address to check
     * @return <code>true</code> if both addresses end with the same domain, <code>false</code>, otherwise
     */
    @SuppressWarnings("null") // Guarded by idx1 and idx2
    public static boolean isSameMailDomain(String email1, String email2) {
        int idx1 = null != email1 ? email1.lastIndexOf('@') : -1;
        if (0 > idx1) {
            return false;
        }
        int idx2 = null != email2 ? email2.lastIndexOf('@') : -1;
        if (0 > idx2) {
            return false;
        }
        return Objects.equals(email1.substring(idx1), email2.substring(idx2));
    }

    /**
     * Constructs a search term to match events located in a specific folder. Depending on the folder's type, either a search term for
     * the {@link EventField#FOLDER_ID} and/or for the {@link AttendeeField#FOLDER_ID} is built.
     * <p/>
     * The session user's read permissions in the folder (<i>own</i> vs <i>all</i>) are considered automatically, too, by restricting via
     * {@link EventField#CREATED_BY} if needed.
     *
     * @param session The calendar session
     * @param folder The folder to construct the search term for
     * @return The search term
     */
    public static SearchTerm<?> getFolderIdTerm(CalendarSession session, CalendarFolder folder) {
        SearchTerm<?> searchTerm;
        if (PublicType.getInstance().equals(folder.getType())) {
            /*
             * match the event's common folder identifier
             */
            searchTerm = getSearchTerm(EventField.FOLDER_ID, SingleOperation.EQUALS, folder.getId());
        } else {
            /*
             * for personal folders, match against the corresponding attendee's folder
             */
            searchTerm = new CompositeSearchTerm(CompositeOperation.AND)
                .addSearchTerm(getSearchTerm(AttendeeField.ENTITY, SingleOperation.EQUALS, I(folder.getCreatedBy())))
                .addSearchTerm(getSearchTerm(AttendeeField.FOLDER_ID, SingleOperation.EQUALS, folder.getId()));
            if (false == isEnforceDefaultAttendee(session)) {
                /*
                 * also match the event's common folder identifier if no default attendee is enforced
                 */
                searchTerm = new CompositeSearchTerm(CompositeOperation.OR)
                    .addSearchTerm(getSearchTerm(EventField.FOLDER_ID, SingleOperation.EQUALS, folder.getId()))
                    .addSearchTerm(searchTerm);
            }
        }
        if (folder.getOwnPermission().getReadPermission() < Permission.READ_ALL_OBJECTS) {
            /*
             * if only access to "own" objects, restrict to events created by the current session's user
             */
            searchTerm = new CompositeSearchTerm(CompositeOperation.AND)
                .addSearchTerm(searchTerm)
                .addSearchTerm(getSearchTerm(EventField.CREATED_BY, SingleOperation.EQUALS, I(folder.getSession().getUserId())));
        }
        return searchTerm;
    }

    /**
     * Selects a well-known and valid timezone based on a client-supplied timezone, using different fallbacks if no exactly matching
     * timezone is available.
     *
     * @param session The session
     * @param calendarUserId The identifier of the calendar user
     * @param timeZone The timezone as supplied by the client
     * @param originalTimeZone The original timezone in case of updates, or <code>null</code> if not available
     * @return The selected timezone, or <code>null</code> if passed timezoen reference was <code>null</code>
     */
    public static TimeZone selectTimeZone(Session session, int calendarUserId, TimeZone timeZone, TimeZone originalTimeZone) throws OXException {
        if (null == timeZone) {
            return null;
        }
        /*
         * try to match by timezone identifier first
         */
        TimeZone matchingTimeZone = optTimeZone(timeZone.getID(), null);
        if (null != matchingTimeZone) {
            return matchingTimeZone;
        }
        /*
         * try and match the original timezone with the same rules if supplied
         */
        if (haveSameLastRules(timeZone, originalTimeZone)) {
            LOG.debug("No matching timezone found for '{}', falling back to original timezone '{}'.", timeZone.getID(), originalTimeZone);
            return originalTimeZone;
        }
        /*
         * use calendar user's / session user's timezone if same rules are effective
         */
        EntityResolver entityResolver = new DefaultEntityResolver(session.getContextId(), Services.getServiceLookup());
        TimeZone calendarUserTimeZone = entityResolver.getTimeZone(calendarUserId);
        if (haveSameLastRules(timeZone, calendarUserTimeZone)) {
            LOG.debug("No matching timezone found for '{}', falling back to calendar user's timezone '{}'.", timeZone.getID(), calendarUserTimeZone);
            return calendarUserTimeZone;
        }
        if (session.getUserId() != calendarUserId) {
            TimeZone sessionUserTimeZone = entityResolver.getTimeZone(session.getUserId());
            if (haveSameLastRules(timeZone, sessionUserTimeZone)) {
                LOG.debug("No matching timezone found for '{}', falling back to session user's timezone '{}'.", timeZone.getID(), sessionUserTimeZone);
                return sessionUserTimeZone;
            }
        }
        /*
         * select matching olson timezone for a known windows/exchange timezone
         */
        TimeZone mappedTimeZone = optTimeZone(TimeZoneMapping.get(timeZone.getID()));
        if (null != mappedTimeZone) {
            LOG.debug("No matching timezone found for '{}', falling back to mapped olson timezone '{}'.", timeZone.getID(), mappedTimeZone);
            return mappedTimeZone;
        }
        /*
         * select the timezone with the same rules, and most similar identifier, briefly re-checking the user timezone in lenient mode
         */
        List<TimeZone> timeZonesWithSameLastRules = getWithSameLastRules(timeZone, false);
        if (timeZonesWithSameLastRules.isEmpty()) {
            if (haveSameLastRules(timeZone, calendarUserTimeZone, true)) {
                LOG.debug("No matching timezone found for '{}', falling back to calendar user's timezone '{}'.", timeZone.getID(), calendarUserTimeZone);
                return calendarUserTimeZone;
            }
            timeZonesWithSameLastRules = getWithSameLastRules(timeZone, true);
            if (timeZonesWithSameLastRules.isEmpty()) {
                LOG.warn("No timezone with matching rules found for '{}', falling back to calendar user timezone '{}'.", timeZone.getID(), calendarUserTimeZone);
                return calendarUserTimeZone;
            }
        }
        timeZonesWithSameLastRules.sort(Comparator.comparingInt(tz -> levenshteinDistance(tz.getID(), timeZone.getID())));
        TimeZone fallbackTimeZone = timeZonesWithSameLastRules.get(0);
        LOG.warn("No matching timezone found for '{}', falling back to '{}'.", timeZone.getID(), fallbackTimeZone);
        return fallbackTimeZone;
    }

    /**
     * Gets a value indicating whether the last observed rule instance of two timezones have the same rule and offset, considering the
     * {@link TimeZone#hasSameRules(TimeZone)} implementation of both objects with each other to be sure.
     *
     * @param timeZone1 The first timezone to check, or <code>null</code> to get a <code>false</code> result
     * @param timeZone2 The second timezone to check, or <code>null</code> to get a <code>false</code> result
     * @return <code>true</code> if the last observed rules of the timezones have the same rule and offset, <code>false</code>, otherwise
     */
    private static boolean haveSameLastRules(TimeZone timeZone1, TimeZone timeZone2) {
        return haveSameLastRules(timeZone1, timeZone2, false);
    }

    /**
     * Gets a value indicating whether the last observed rule instance of two timezones have the same rule and offset, considering the
     * {@link TimeZone#hasSameRules(TimeZone)} implementation of both objects with each other to be sure.
     *
     * @param timeZone1 The first timezone to check, or <code>null</code> to get a <code>false</code> result
     * @param timeZone2 The second timezone to check, or <code>null</code> to get a <code>false</code> result
     * @param ignoreTimes <code>true</code> to ignore the actual hour of the day of when the transition to/from DST occurs, <code>false</code>, otherwise
     * @return <code>true</code> if the last observed rules of the timezones have the same rule and offset, <code>false</code>, otherwise
     */
    private static boolean haveSameLastRules(TimeZone timeZone1, TimeZone timeZone2, boolean ignoreTimes) {
        if (null == timeZone1 || null == timeZone2) {
            return false;
        }
        SimpleTimeZone lastRule1 = optLastRuleInstance(timeZone1);
        SimpleTimeZone lastRule2 = optLastRuleInstance(timeZone2);
        if (null == lastRule1 && null == lastRule2 && timeZone1.getRawOffset() == timeZone2.getRawOffset()) {
            return true;
        }
        if (null == lastRule1 || null == lastRule2 || timeZone1.getRawOffset() != timeZone2.getRawOffset()) {
            return false;
        }
        if (ignoreTimes && null != SIMPLETIMEZONE_RULE_FIELDS) {
            try {
                for (java.lang.reflect.Field field : SIMPLETIMEZONE_RULE_FIELDS) {
                    if (field.getInt(lastRule1) != field.getInt(lastRule2)) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                LOG.debug("Error comparing SimpleTimeZone properties", e);
            }
        }
        return lastRule1.hasSameRules(lastRule2) && lastRule2.hasSameRules(lastRule1);
    }

    private static SimpleTimeZone optLastRuleInstance(TimeZone timeZone) {
        if (null != timeZone) {
            if (SimpleTimeZone.class.isInstance(timeZone)) {
                return (SimpleTimeZone) timeZone;
            }
            if (LastRuleAware.class.isInstance(timeZone)) {
                return ((LastRuleAware) timeZone).getLastRuleInstance();
            }
            try {
                java.lang.reflect.Method getLastRuleInstance = timeZone.getClass().getDeclaredMethod("getLastRuleInstance", (Class<?>[]) null);
                return (SimpleTimeZone) getLastRuleInstance.invoke(timeZone, (Object[]) null);
            } catch (Exception e) {
                LOG.debug("Error invoking \"getLastRuleInstance\" on {}", timeZone, e);
            }
        }
        return null;
    }

    private static List<TimeZone> getWithSameLastRules(TimeZone timeZone, boolean ignoreTimes) {
        List<TimeZone> timeZones = new ArrayList<TimeZone>();
        for (String timeZoneId : TimeZone.getAvailableIDs(timeZone.getRawOffset())) {
            if (0 < timeZoneId.indexOf('/')) {
                TimeZone candidateTimeZone = optTimeZone(timeZoneId);
                if (haveSameLastRules(timeZone, candidateTimeZone, ignoreTimes)) {
                    timeZones.add(candidateTimeZone);
                }
            }
        }
        return timeZones;
    }

    /**
     * Measures the distance between two strings, based on the <i>Levenshtein</i> algorithm.
     *
     * @param a The first string
     * @param b The second string
     * @return The result
     * @see <a href="http://rosettacode.org/wiki/Levenshtein_distance#Java">Levenshtein Distance</a>
     */
    private static int levenshteinDistance(String a, String b) {
        a = a.toLowerCase();
        b = b.toLowerCase();
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    /**
     * <i>Anonymizes</i> an event in case it is not marked as {@link Classification#PUBLIC}, and the session's user is neither creator, nor
     * attendee of the event.
     * <p/>
     * After anonymization, the event will only contain those properties defined in {@link #NON_CLASSIFIED_FIELDS}, as well as the
     * generic summary "Private".
     *
     * @param session The calendar session
     * @param event The event to anonymize
     * @return The potentially anonymized event
     */
    public static Event anonymizeIfNeeded(CalendarSession session, Event event) throws OXException {
        if (false == isClassifiedFor(event, session.getUserId())) {
            return event;
        }

        return anonymize(event, session.getEntityResolver().getLocale(session.getUserId()));
    }

    /**
     * <i>Anonymizes</i> an event.
     * <p/>
     * After anonymization, the event will only contain those properties defined in {@link #NON_CLASSIFIED_FIELDS}, as well as the
     * generic summary "Private".
     *
     * @param event The event to anonymize
     * @param locale The locale to translate the generic summary, or <code>null</code> to use the default locale
     * @return The anonymized event
     */
    public static Event anonymize(Event event, Locale locale) throws OXException {
        Event anonymizedEvent = EventMapper.getInstance().copy(event, new Event(), NON_CLASSIFIED_FIELDS);
        anonymizedEvent.setSummary(StringHelper.valueOf(locale).getString(CalendarStrings.SUMMARY_PRIVATE));
        return anonymizedEvent;
    }

    /**
     * Adds one or more warnings in the calendar session.
     *
     * @param session The calendar session
     * @param warnings The warnings to add, or <code>null</code> to ignore
     */
    public static void addWarnings(CalendarSession session, Collection<OXException> warnings) {
        if (null != warnings && 0 < warnings.size()) {
            for (OXException warning : warnings) {
                session.addWarning(warning);
            }
        }
    }

    /**
     * Gets a user.
     *
     * @param session The calendar session
     * @param userId The identifier of the user to get
     * @return The user
     */
    public static User getUser(CalendarSession session, int userId) throws OXException {
        return Services.getService(UserService.class).getUser(userId, session.getContextId());
    }

    /**
     * Gets the actual target calendar user for a specific folder. This is either the current session's user for "private" or "public"
     * folders, or the folder owner for "shared" calendar folders.
     *
     * @param session The calendar session
     * @param folder The folder to get the calendar user for
     * @return The calendar user
     */
    public static CalendarUser getCalendarUser(CalendarSession session, CalendarFolder folder) throws OXException {
        int calendarUserId = getCalendarUserId(folder);
        return session.getEntityResolver().applyEntityData(new CalendarUser(), calendarUserId);
    }

    /**
     * Gets the identifier of the actual target calendar user for a specific folder. This is either the current session's user for
     * "private" or "public" folders, or the folder owner for "shared" calendar folders.
     *
     * @param folder The folder to get the calendar user for
     * @return The identifier of the calendar user
     */
    public static int getCalendarUserId(CalendarFolder folder) {
        if (SharedType.getInstance().equals(folder.getType())) {
            return folder.getCreatedBy();
        }
        return folder.getSession().getUserId();
    }

    /**
     * Resolves the event ID for the given UID and the given calendar user. In case the recurrence ID is unknown,
     * the master event will be returned.
     *
     * @param session The session to use
     * @param storage The storage to lookup the event from
     * @param uid The event UID to load
     * @param recurrenceId The recurrence identifier of the event, can be <code>null</code>
     * @param calendarUserId The identifier of the calendar user the unique identifier should be resolved for
     * @return The eventID The event ID of the resolved event, userized for the calendar user
     * @throws OXException If the event can not be found
     */
    public static @NonNull EventID resolveEventId(CalendarSession session, CalendarStorage storage, String uid, RecurrenceId recurrenceId, int calendarUserId) throws OXException {
        ResolvePerformer resolvePerformer = new ResolvePerformer(session, storage);
        return resolveEventId(session, storage, uid, recurrenceId, calendarUserId, resolvePerformer);
    }

    private static @NonNull EventID resolveEventId(CalendarSession session, CalendarStorage storage, String uid, RecurrenceId recurrenceId, int calendarUserId, ResolvePerformer resolvePerformer) throws OXException {
        EventID eventID = resolvePerformer.resolveByUid(uid, recurrenceId, calendarUserId);
        if (null == eventID) {
            if (null != recurrenceId) {
                /*
                 * Might be a new exception, try to resolve the master event instead
                 */
                return resolveEventId(session, storage, uid, null, calendarUserId, resolvePerformer);
            }
            throw CalendarExceptionCodes.EVENT_NOT_FOUND.create(uid);
        }
        return eventID;
    }

    /**
     * Get the calendar folder to use based on the first event obtained from the scheduled resource provided by the message
     *
     * @param session The session to use
     * @param storage The storage to lookup the event from
     * @param uid The UID of the event to get the folder for
     * @param recurrenceId The recurrence identifier of the event, can be <code>null</code>
     * @param calendarUserId The identifier of the calendar user the unique identifier should be resolved for
     * @return The {@link CalendarFolder}
     * @throws OXException If folder can't be determined or is not visible for the user
     */
    public static CalendarFolder getCalendarFolder(CalendarSession session, CalendarStorage storage, String uid, RecurrenceId recurrenceId, int calendarUserId) throws OXException {
        EventField[] oldParameterFields = session.get(CalendarParameters.PARAMETER_FIELDS, EventField[].class);
        List<Event> resolvedEvents;
        try {
            session.set(CalendarParameters.PARAMETER_FIELDS, new EventField[] { EventField.FOLDER_ID });
            resolvedEvents = session.getCalendarService().getUtilities().resolveEventsByUID(session, uid, calendarUserId);
        } finally {
            session.set(CalendarParameters.PARAMETER_FIELDS, oldParameterFields);
        }
        if (resolvedEvents.isEmpty()) {
            throw CalendarExceptionCodes.EVENT_NOT_FOUND.create(uid);
        }
        return getFolder(session, resolvedEvents.get(0).getFolderId());
    }

    /**
     * Get the calendar folder to use based on the first event obtained from the scheduled resource provided by the message
     *
     * @param session The session to use
     * @param storage The storage to lookup the event from
     * @param uid The UID of the event to get the folder for
     * @param calendarUserId The identifier of the calendar user the unique identifier should be resolved for
     * @return The {@link CalendarFolder}
     * @throws OXException If folder can't be determined or is not visible for the user
     */
    public static CalendarFolder getCalendarFolder(CalendarSession session, CalendarStorage storage, String uid, int calendarUserId) throws OXException {

        //TODO: let resolveperformer resolve the folderid directly 

        EventField[] oldParameterFields = session.get(CalendarParameters.PARAMETER_FIELDS, EventField[].class);
        List<Event> resolvedEvents;
        try {
            session.set(CalendarParameters.PARAMETER_FIELDS, new EventField[] { EventField.FOLDER_ID });
            resolvedEvents = session.getCalendarService().getUtilities().resolveEventsByUID(session, uid, calendarUserId);
        } finally {
            session.set(CalendarParameters.PARAMETER_FIELDS, oldParameterFields);
        }
        if (resolvedEvents.isEmpty()) {
            throw CalendarExceptionCodes.EVENT_NOT_FOUND.create(uid);
        }
        return getFolder(session, resolvedEvents.get(0).getFolderId());
    }

    /**
     * Gets a value indicating whether a specific event is actually present in the supplied folder. Based on the folder type, the
     * event's public folder identifier or the attendee's personal calendar folder is checked, as well as the attendee's <i>hidden</i>
     * marker.
     *
     * @param event The event to check
     * @param folder The folder where the event should appear in
     * @return <code>true</code> if the event <i>is</i> in the folder, <code>false</code>, otherwise
     */
    public static boolean isInFolder(Event event, CalendarFolder folder) {
        if (PublicType.getInstance().equals(folder.getType()) || false == isGroupScheduled(event) && null != event.getFolderId()) {
            return folder.getId().equals(event.getFolderId());
        }
        Attendee userAttendee = CalendarUtils.find(event.getAttendees(), folder.getCreatedBy());
        return null != userAttendee && folder.getId().equals(userAttendee.getFolderId()) && false == userAttendee.isHidden();
    }

    /**
     * Gets a value indicating whether an event in a specific folder is visible to the current user or not, either based on the user's
     * permissions in the calendar folder representing the actual view on the event, together with its classification, or based on the
     * user participating in the event as organizer or attendee.
     *
     * @param folder The calendar folder the event is read in
     * @param event The event to check
     * @return <code>true</code> if the event can be read, <code>false</code>, otherwise
     */
    public static boolean isVisible(CalendarFolder folder, Event event) {
        int userId = folder.getSession().getUserId();
        if (Classification.PRIVATE.equals(event.getClassification()) && isClassifiedFor(event, userId)) {
            return false;
        }
        Permission ownPermission = folder.getOwnPermission();
        if (ownPermission.getReadPermission() >= Permission.READ_ALL_OBJECTS) {
            return true;
        }
        if (ownPermission.getReadPermission() == Permission.READ_OWN_OBJECTS && matches(event.getCreatedBy(), userId)) {
            return true;
        }
        if ((PublicType.getInstance().equals(folder.getType()) || PrivateType.getInstance().equals(folder.getType())) &&
            (matches(event.getCalendarUser(), userId) || isAttendee(event, userId) || isOrganizer(event, userId))) {
            return true;
        }
        return false;
    }

    /**
     * Gets a <i>userized</i> calendar folder by its identifier.
     *
     * @param session The calendar session
     * @param folderId The identifier of the folder to get
     * @return The folder
     */
    public static CalendarFolder getFolder(CalendarSession session, String folderId) throws OXException {
        return getFolder(session, folderId, true);
    }

    /**
     * Gets a <i>userized</i> calendar folder by its identifier.
     *
     * @param session The calendar session
     * @param folderId The identifier of the folder to get
     * @param failIfNotVisible <code>true</code> to fail if the folder is not visible for the current session's user, <code>false</code>, otherwise
     * @return The folder
     */
    public static CalendarFolder getFolder(CalendarSession session, String folderId, boolean failIfNotVisible) throws OXException {
        FolderObject folder = getEntityResolver(session).getFolder(asInt(folderId), optConnection(session));
        UserPermissionBits permissionBits = ServerSessionAdapter.valueOf(session.getSession()).getUserPermissionBits();
        EffectivePermission permission = folder.getEffectiveUserPermission(session.getUserId(), permissionBits);
        if (failIfNotVisible && false == permission.isFolderVisible()) {
            throw CalendarExceptionCodes.NO_READ_PERMISSION.create(folderId);
        }
        return new CalendarFolder(session.getSession(), folder, permission);
    }

    /**
     * Gets the folders that are actually visible for the current session's user from a list of possible folder identifiers.
     *
     * @param session The calendar session
     * @param folderIds The possible identifiers of the folders to get
     * @return The visible folders, or an empty list if none are visible
     */
    public static List<CalendarFolder> getVisibleFolders(CalendarSession session, Collection<String> folderIds) throws OXException {
        if (null == folderIds || 0 == folderIds.size()) {
            return Collections.emptyList();
        }
        List<CalendarFolder> folders = new ArrayList<CalendarFolder>(folderIds.size());
        DefaultEntityResolver entityResolver = getEntityResolver(session);
        UserPermissionBits permissionBits = ServerSessionAdapter.valueOf(session.getSession()).getUserPermissionBits();
        Connection connection = optConnection(session);
        for (String folderId : folderIds) {
            try {
                FolderObject folder = entityResolver.getFolder(asInt(folderId), connection);
                EffectivePermission permission = folder.getEffectiveUserPermission(session.getUserId(), permissionBits);
                if (permission.isFolderVisible()) {
                    folders.add(new CalendarFolder(session.getSession(), folder, permission));
                }
            } catch (OXException | NumberFormatException e) {
                LOG.warn("Error evaluating if folder {} is visible, skipping.", folderId, e);
                continue;
            }
        }
        return folders;
    }

    /**
     * Maps the corresponding event occurrences from two collections based on their common object- and recurrence identifiers.
     *
     * @param originalOccurrences The original event occurrences
     * @param updatedOccurrences The updated event occurrences
     * @return A list of entries holding each of the matching original and updated event occurrences, with one of them possibly <code>null</code>
     */
    public static List<Entry<Event, Event>> mapEventOccurrences(List<Event> originalOccurrences, List<Event> updatedOccurrences) {
        List<Entry<Event, Event>> mappedEvents = new ArrayList<Entry<Event, Event>>(Math.max(originalOccurrences.size(), updatedOccurrences.size()));
        for (Event originalOccurrence : originalOccurrences) {
            Event updatedOccurrence = find(updatedOccurrences, originalOccurrence.getId(), originalOccurrence.getRecurrenceId());
            mappedEvents.add(new AbstractMap.SimpleEntry<Event, Event>(originalOccurrence, updatedOccurrence));
        }
        for (Event updatedOccurrence : updatedOccurrences) {
            if (null == find(originalOccurrences, updatedOccurrence.getId(), updatedOccurrence.getRecurrenceId())) {
                mappedEvents.add(new AbstractMap.SimpleEntry<Event, Event>(null, updatedOccurrence));
            }
        }
        return mappedEvents;
    }

    /**
     * Get the configured quota and the actual usage of the underlying calendar account.
     *
     * @param session The calendar session
     * @param storage The calendar storage to use
     * @return The quota
     * @throws OXException In case of an error
     */
    public static Quota getQuota(CalendarSession session, CalendarStorage storage) throws OXException {
        /*
         * get configured amount quota limit
         */
        ConfigViewFactory configViewFactory = Services.getService(ConfigViewFactory.class, true);
        Connection connection = optConnection(session);
        long limit;
        if (null != connection) {
            limit = AmountQuotas.getLimit(session.getSession(), Module.CALENDAR.getName(), configViewFactory, connection);
        } else {
            DatabaseService databaseService = Services.getService(DatabaseService.class, true);
            limit = AmountQuotas.getLimit(session.getSession(), Module.CALENDAR.getName(), configViewFactory, databaseService);
        }
        if (Quota.UNLIMITED == limit) {
            return Quota.UNLIMITED_AMOUNT;
        }
        /*
         * get actual usage & wrap in quota structure appropriately
         */
        long usage = storage.getEventStorage().countEvents();
        return new Quota(QuotaType.AMOUNT, limit, usage);
    }

    /**
     * Initializes a {@link DelegatingEvent} that overrides the participation status of the attendee matching a specific calendar user.
     *
     * @param event The event to override the participation status in
     * @param calendarUser The calendar user to override the participation status for
     * @param partStat The participation status to indicate for the matching attendee
     * @return A delegating event that overrides the participation status accordingly
     */
    public static Event overridePartStat(Event event, CalendarUser calendarUser, ParticipationStatus partStat) {
        return new DelegatingEvent(event) {

            @Override
            public List<Attendee> getAttendees() {
                List<Attendee> attendees = super.getAttendees();
                if (null != attendees && 0 < attendees.size()) {
                    List<Attendee> modifiedAttendees = new ArrayList<Attendee>(attendees.size());
                    for (Attendee attendee : attendees) {
                        if (matches(calendarUser, attendee)) {
                            try {
                                attendee = AttendeeMapper.getInstance().copy(attendee, null, (AttendeeField[]) null);
                            } catch (OXException e) {
                                org.slf4j.LoggerFactory.getLogger(Utils.class).warn("Unexpected error copying attendee data", e);
                            }
                            attendee.setPartStat(partStat);
                        }
                        modifiedAttendees.add(attendee);
                    }
                }
                return attendees;
            }
        };
    }

    /**
     * Initializes a calendar object resource based on {@link DelegatingEvent}s that override the participation status of the attendee
     * matching a specific calendar user.
     *
     * @param resource The resource to override the participation status in
     * @param calendarUser The calendar user to override the participation status for
     * @param partStat The participation status to indicate for the matching attendee
     * @return A new calendar object resource that overrides the participation status accordingly
     */
    public static CalendarObjectResource overridePartStat(CalendarObjectResource resource, CalendarUser calendarUser, ParticipationStatus partStat) {
        List<Event> overriddenEvents = new ArrayList<Event>();
        for (Event event : resource.getEvents()) {
            overriddenEvents.add(overridePartStat(event, calendarUser, partStat));
        }
        return new DefaultCalendarObjectResource(overriddenEvents);
    }

    /**
     * Gets a valued indicating whether changed properties in an updated attendee represents a modification that needs to be reported as
     * <i>reply</i> to the of a group scheduled event organizer or not.
     *
     * @param originalAttendee The original attendee
     * @param updatedAttendee The updated attendee
     * @return <code>true</code> if the changed properties represent a reply, <code>false</code>, otherwise
     * @see <a href="https://tools.ietf.org/html/rfc6638#section-3.2.2.3">RFC 6638, section 3.2.2.3</a>
     */
    public static boolean isReply(Attendee originalAttendee, Attendee updatedAttendee) throws OXException {
        if (null == originalAttendee) {
            return false;
        }
        if (null == updatedAttendee) {
            return true;
        }
        return false == AttendeeMapper.getInstance().get(AttendeeField.PARTSTAT).equals(originalAttendee, updatedAttendee) ||
            false == AttendeeMapper.getInstance().get(AttendeeField.COMMENT).equals(originalAttendee, updatedAttendee);
    }

    /**
     * Gets a value indicating whether the applied changes represent an attendee reply of a specific calendar user for the associated
     * calendar object resource or not, depending on the modified attendee fields.
     *
     * @param attendeeUpdates The attendee updates to check
     * @param calendarUser The calendar user to check the reply for
     * @return <code>true</code> if the underlying calendar resource is replied to along with the update, <code>false</code>, otherwise
     */
    public static boolean isReply(CollectionUpdate<Attendee, AttendeeField> attendeeUpdates, CalendarUser calendarUser) {
        for (ItemUpdate<Attendee, AttendeeField> itemUpdate : attendeeUpdates.getUpdatedItems()) {
            if (matches(itemUpdate.getOriginal(), calendarUser)) {
                return itemUpdate.containsAnyChangeOf(REPLY_FIELDS);
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether one of the applied changes represent an attendee reply of a specific calendar user for the associated
     * calendar object resource or not, depending on the modified attendee fields.
     * 
     * @param eventUpdates The event updates to check
     * @param calendarUser The calendar user to check the reply for
     * @return <code>true</code> if the underlying calendar resource is replied to along with the update, <code>false</code>, otherwise
     */
    public static boolean isReply(List<? extends EventUpdate> eventUpdates, CalendarUser calendarUser) {
        if (null != eventUpdates && 0 < eventUpdates.size()) {
            for (EventUpdate eventUpdate : eventUpdates) {
                if (isReply(eventUpdate.getAttendeeUpdates(), calendarUser)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether an event update represents a <i>re-scheduling</i> of the calendar object resource or not, depending
     * on the modified event fields.
     * <p/>
     * Besides changes to the event's recurrence, start- or end-time, this also includes further important event properties, or changes
     * in the attendee line-up.
     *
     * @param eventUpdate The event update to check
     * @return <code>true</code> if the updated event is considered as re-scheduled, <code>false</code>, otherwise
     * @see #RESCHEDULE_FIELDS
     */
    public static boolean isReschedule(EventUpdate eventUpdate) {
        for (EventField updatedField : eventUpdate.getUpdatedFields()) {
            if (isReschedule(eventUpdate.getOriginal(), eventUpdate.getUpdate(), updatedField)) {
                return true; // field relevant for re-scheduling changed
            }
        }
        if (0 < eventUpdate.getAttendeeUpdates().getAddedItems().size() || 0 < eventUpdate.getAttendeeUpdates().getRemovedItems().size()) {
            return true; // attendee lineup changed
        }
        return false;
    }

    /**
     * Gets a value indicating whether at least one of the supplied event updates represents a <i>re-scheduling</i> of the calendar object
     * resource or not, depending on the modified event fields.
     * <p/>
     * Besides changes to an event's recurrence, start- or end-time, this also includes further important event properties, or changes
     * in the attendee line-up.
     *
     * @param eventUpdates The event updates to check
     * @return <code>true</code> if one of the updated events is considered as re-scheduled, <code>false</code>, otherwise
     * @see #RESCHEDULE_FIELDS
     */
    public static boolean isReschedule(List<? extends EventUpdate> eventUpdates) {
        if (null != eventUpdates && 0 < eventUpdates.size()) {
            for (EventUpdate eventUpdate : eventUpdates) {
                if (isReschedule(eventUpdate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets a value indicating whether an event update represents a <i>re-scheduling</i> of the calendar object resource or not, depending
     * on the modified event fields.
     * <p/>
     * Besides changes to the event's recurrence, start- or end-time, this also includes further important event properties, or changes
     * in the attendee line-up.
     *
     * @param originalEvent The original event
     * @param updatedEvent The updated event
     * @return <code>true</code> if the updated event is considered as re-scheduled, <code>false</code>, otherwise
     * @see #RESCHEDULE_FIELDS
     */
    public static boolean isReschedule(Event originalEvent, Event updatedEvent) {
        /*
         * re-scheduled if one of reschedule fields, or the attendee lineup changes
         */
        for (EventField rescheduleField : RESCHEDULE_FIELDS) {
            if (isReschedule(originalEvent, updatedEvent, rescheduleField)) {
                return true; // field relevant for re-scheduling changed
            }
        }
        if (false == matches(originalEvent.getAttendees(), updatedEvent.getAttendees())) {
            return true; // attendee lineup changed
        }
        return false;
    }

    /**
     * Gets a value indicating whether a particular property of an event update represents a <i>re-scheduling</i> of the calendar object
     * resource or not, based on the property's value in the original and updated event data.
     *
     * @param originalEvent The original event
     * @param updatedEvent The updated event
     * @param field The event field to check
     * @return <code>true</code> if the property in the updated event makes it re-scheduled, <code>false</code>, otherwise
     * @see #RESCHEDULE_FIELDS
     */
    private static boolean isReschedule(Event originalEvent, Event updatedEvent, EventField field) {
        if (null == field || false == com.openexchange.tools.arrays.Arrays.contains(RESCHEDULE_FIELDS, field)) {
            return false; // field not relevant
        }
        Mapping<? extends Object, Event> mapping = EventMapper.getInstance().opt(field);
        if (null == mapping || mapping.equals(originalEvent, updatedEvent)) {
            return false; // no change
        }
        Object originalValue = mapping.get(originalEvent);
        Object updatedValue = mapping.get(updatedEvent);
        if ((null == originalValue || String.class.isInstance(originalValue) && Strings.isEmpty((String) originalValue)) &&
            (null == updatedValue || String.class.isInstance(updatedValue) && Strings.isEmpty((String) updatedValue))) {
            return false; // no relevant change in textual field
        }
        return true; // relevant change, otherwise
    }

    /**
     * Extracts those event updates that represent a <i>reply</i> scheduling operation from a specific calendar user's point of view.
     *
     * @param eventUpdates The event updates to extract the replies from
     * @param calendarUser The calendar user to extract the replies for
     * @return The event updates representing <i>reply</i> scheduling operations, or an empty list if there are none
     * @see #isReply(CollectionUpdate, CalendarUser)
     */
    public static List<EventUpdate> extractReplies(List<EventUpdate> eventUpdates, CalendarUser calendarUser) {
        if (null == eventUpdates || eventUpdates.isEmpty()) {
            return Collections.emptyList();
        }
        List<EventUpdate> replyEventUpdates = new ArrayList<EventUpdate>(eventUpdates);
        for (Iterator<EventUpdate> iterator = replyEventUpdates.iterator(); iterator.hasNext();) {
            if (false == isReply(iterator.next().getAttendeeUpdates(), calendarUser)) {
                iterator.remove();
            }
        }
        return replyEventUpdates;
    }

    /**
     * Applies <i>userized</i> versions of change- and delete-exception dates in the series master event based on the user's actual
     * attendance.
     *
     * @param storage A reference to the calendar storage to use
     * @param seriesMaster The series master event
     * @param forUser The identifier of the user to apply the exception dates for
     * @return The passed event reference, with possibly adjusted exception dates
     * @see <a href="https://tools.ietf.org/html/rfc6638#section-3.2.6">RFC 6638, section 3.2.6</a>
     */
    public static Event applyExceptionDates(CalendarStorage storage, Event seriesMaster, int forUser) throws OXException {
        if (false == isSeriesMaster(seriesMaster) || false == isGroupScheduled(seriesMaster) || isOrganizer(seriesMaster, forUser) ||
            isLastUserAttendee(seriesMaster.getAttendees(), forUser)) {
            /*
             * "real" delete exceptions for all attendees, take over as-is
             */
            return seriesMaster;
        }
        /*
         * check which change exceptions exist where the user is attending
         */
        SortedSet<RecurrenceId> changeExceptionDates = seriesMaster.getChangeExceptionDates();
        if (null == changeExceptionDates || 0 == changeExceptionDates.size()) {
            return seriesMaster;
        }
        CompositeSearchTerm searchTerm = new CompositeSearchTerm(CompositeOperation.AND)
            .addSearchTerm(getSearchTerm(EventField.SERIES_ID, SingleOperation.EQUALS, seriesMaster.getSeriesId()))
            .addSearchTerm(getSearchTerm(EventField.ID, SingleOperation.NOT_EQUALS, new ColumnFieldOperand<EventField>(EventField.SERIES_ID)))
            .addSearchTerm(getSearchTerm(AttendeeField.ENTITY, SingleOperation.EQUALS, I(forUser)))
            .addSearchTerm(new CompositeSearchTerm(CompositeOperation.OR)
                .addSearchTerm(getSearchTerm(AttendeeField.HIDDEN, SingleOperation.ISNULL))
                .addSearchTerm(getSearchTerm(AttendeeField.HIDDEN, SingleOperation.NOT_EQUALS, Boolean.TRUE)))
        ;
        EventField[] fields = new EventField[] { EventField.ID, EventField.SERIES_ID, EventField.RECURRENCE_ID };
        List<Event> attendedChangeExceptions = storage.getEventStorage().searchEvents(searchTerm, null, fields);
        if (attendedChangeExceptions.size() == changeExceptionDates.size()) {
            return seriesMaster;
        }
        /*
         * apply userized exception dates
         */
        return applyExceptionDates(seriesMaster, getRecurrenceIds(attendedChangeExceptions));
    }

    /**
     * Applies <i>userized</i> versions of change- and delete-exception dates in the series master event based on the user's actual
     * attendance.
     *
     * @param seriesMaster The series master event
     * @param attendedChangeExceptionDates The exception dates the user actually attends
     * @return The passed event reference, with possibly adjusted exception dates
     * @see <a href="https://tools.ietf.org/html/rfc6638#section-3.2.6">RFC 6638, section 3.2.6</a>
     */
    public static Event applyExceptionDates(Event seriesMaster, SortedSet<RecurrenceId> attendedChangeExceptionDates) {
        /*
         * check which change exceptions exist where the user is attending
         */
        SortedSet<RecurrenceId> changeExceptionDates = seriesMaster.getChangeExceptionDates();
        if (null == changeExceptionDates || 0 == changeExceptionDates.size()) {
            return seriesMaster;
        }
        /*
         * apply a 'userized' version of exception dates by moving exception date from change- to delete-exceptions
         */
        SortedSet<RecurrenceId> userizedChangeExceptions = new TreeSet<RecurrenceId>(attendedChangeExceptionDates);
        SortedSet<RecurrenceId> userizedDeleteExceptions = new TreeSet<RecurrenceId>();
        if (null != seriesMaster.getDeleteExceptionDates()) {
            userizedDeleteExceptions.addAll(seriesMaster.getDeleteExceptionDates());
        }
        for (RecurrenceId originalChangeExceptionDate : seriesMaster.getChangeExceptionDates()) {
            if (false == contains(userizedChangeExceptions, originalChangeExceptionDate)) {
                userizedDeleteExceptions.add(originalChangeExceptionDate);
            }
        }
        return new DelegatingEvent(seriesMaster) {

            @Override
            public SortedSet<RecurrenceId> getDeleteExceptionDates() {
                return userizedDeleteExceptions;
            }

            @Override
            public boolean containsDeleteExceptionDates() {
                return true;
            }

            @Override
            public SortedSet<RecurrenceId> getChangeExceptionDates() {
                return userizedChangeExceptions;
            }

            @Override
            public boolean containsChangeExceptionDates() {
                return true;
            }
        };
    }

    /**
     * Replaces a change exception's recurrence identifier to piggyback the recurrence data of the corresponding event series. This aids the
     * <i>legacy</i> storage to calculate the correct recurrence positions properly.
     *
     * @param changeException The change exception event to edit the recurrence identifier in
     * @param recurrenceData The recurrence data to inject
     * @return The passed change exception event, with an replaced data-aware recurrence identifier
     * @see DataAwareRecurrenceId
     */
    public static Event injectRecurrenceData(Event changeException, RecurrenceData recurrenceData) {
        RecurrenceId recurrenceId = changeException.getRecurrenceId();
        if (null != recurrenceId) {
            changeException.setRecurrenceId(new DataAwareRecurrenceId(recurrenceData, recurrenceId.getValue()));
        }
        return changeException;
    }

    /**
     * Replaces the recurrence identifier in a list of change exceptions to piggyback the recurrence data of the corresponding event
     * series. This aids the <i>legacy</i> storage to calculate the correct recurrence positions properly.
     *
     * @param changeExceptions The change exception events to edit the recurrence identifier in
     * @param recurrenceData The recurrence data to inject
     * @return The passed change exception events, with an replaced data-aware recurrence identifier
     * @see DataAwareRecurrenceId
     */
    public static List<Event> injectRecurrenceData(List<Event> changeExceptions, RecurrenceData recurrenceData) {
        if (null != changeExceptions) {
            for (Event changeException : changeExceptions) {
                injectRecurrenceData(changeException, recurrenceData);
            }
        }
        return changeExceptions;
    }

    /**
     * Gets a list containing all elements provided by the supplied iterator.
     *
     * @param itrerator The iterator to get the list for
     * @return The list
     */
    public static <T> List<T> asList(Iterator<T> iterator) {
        List<T> list = new ArrayList<T>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Gets a list containing all elements provided by the supplied iterator.
     *
     * @param itrerator The iterator to get the list for
     * @param limit The maximum number of items to include
     * @return The list
     */
    public static <T> List<T> asList(Iterator<T> iterator, int limit) {
        List<T> list = new ArrayList<T>();
        while (iterator.hasNext() && list.size() < limit) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Gets a value indicating whether a delete operation performed in the supplied folder from the calendar user's perspective would lead
     * to a <i>real</i> deletion of the event from the storage, or if only the calendar user is removed from the attendee list, hence
     * rather an update is performed.
     * <p/>
     * A deletion leads to a complete removal if
     * <ul>
     * <li>the event is located in a <i>public folder</i></li>
     * <li>or the event is not <i>group-scheduled</i></li>
     * <li>or the calendar user is the organizer of the event</li>
     * <li>or the calendar user is the last <i>non-hidden</i> internal user attendee in the event</li>
     * </ul>
     * <p/>
     * Note: Even if attendees are allowed to modify the event, deletion is out of scope.
     *
     * @param folder The calendar folder the event is located in
     * @param originalEvent The original event to check
     * @return <code>true</code> if a deletion would lead to a removal of the event, <code>false</code>, otherwise
     */
    public static boolean deleteRemovesEvent(CalendarFolder folder, Event originalEvent) {
        return PublicType.getInstance().equals(folder.getType()) || false == isGroupScheduled(originalEvent) ||
            isOrganizer(originalEvent, folder.getCalendarUserId()) || isLastNonHiddenUserAttendee(originalEvent.getAttendees(), folder.getCalendarUserId());
    }

    /**
     * Gets all calendar folders accessible by the current sesssion's user.
     *
     * @param session The underlying calendar session
     * @return The folders, or an empty list if there are none
     */
    public static List<CalendarFolder> getVisibleFolders(CalendarSession session) throws OXException {
        return getVisibleFolders(session, true, Permission.READ_FOLDER, Permission.NO_PERMISSIONS, Permission.NO_PERMISSIONS, Permission.NO_PERMISSIONS);
    }

    /**
     * Gets all calendar folders accessible by the current sesssion's user, where a minimum set of permissions are set.
     *
     * @param session The underlying calendar session
     * @param all <code>true</code> to also include currently unsubscribed folders, <code>false</code> to only include subscribed ones
     * @param requiredFolderPermission The required folder permission, or {@link Permission#NO_PERMISSIONS} if none required
     * @param requiredReadPermission The required read object permission, or {@link Permission#NO_PERMISSIONS} if none required
     * @param requiredWritePermission The required write object permission, or {@link Permission#NO_PERMISSIONS} if none required
     * @param requiredDeletePermission The required delete object permission, or {@link Permission#NO_PERMISSIONS} if none required
     * @return The folders, or an empty list if there are none
     */
    public static List<CalendarFolder> getVisibleFolders(CalendarSession session, boolean includeUnsubscribed, int requiredFolderPermission, int requiredReadPermission, int requiredWritePermission, int requiredDeletePermission) throws OXException {
        FolderSubscriptionHelper subscriptionHelper = includeUnsubscribed ? null : Services.optService(FolderSubscriptionHelper.class);
        Connection connection = optConnection(session);
        List<FolderObject> folders = getEntityResolver(session).getVisibleFolders(session.getUserId(), connection);
        UserPermissionBits permissionBits = ServerSessionAdapter.valueOf(session.getSession()).getUserPermissionBits();
        List<CalendarFolder> calendarFolders = new ArrayList<CalendarFolder>(folders.size());
        for (FolderObject folder : folders) {
            if (false == includeUnsubscribed && null != subscriptionHelper) {
                Optional<Boolean> subscribed = subscriptionHelper.isSubscribed(
                    Optional.ofNullable(connection), session.getContextId(), session.getUserId(), folder.getObjectID(), folder.getModule());
                if (subscribed.isPresent() && Boolean.FALSE.equals(subscribed.get())) {
                    continue;
                }
            }
            EffectivePermission ownPermission;
            try {
                ownPermission = folder.getEffectiveUserPermission(session.getUserId(), permissionBits, connection);
            } catch (SQLException e) {
                LOG.warn("Error getting effective user permission for folder {}; skipping.", I(folder.getObjectID()), e);
                continue;
            }
            if (ownPermission.getFolderPermission() >= requiredFolderPermission &&
                ownPermission.getReadPermission() >= requiredReadPermission &&
                ownPermission.getWritePermission() >= requiredWritePermission &&
                ownPermission.getDeletePermission() >= requiredDeletePermission) {
                calendarFolders.add(new CalendarFolder(session.getSession(), folder, ownPermission));
            }
        }
        return calendarFolders;
    }

    /**
     * Calculates a map holding the identifiers of all folders a user is able to access, based on the supplied collection of folder
     * identifiers.
     *
     * @param session The calendar session
     * @param folderIds The identifiers of all folders to determine the users with access for
     * @return The identifiers of the affected folders for each user
     */
    public static Map<Integer, List<String>> getAffectedFoldersPerUser(CalendarSession session, Collection<String> folderIds) throws OXException {
        return getAffectedFoldersPerUser(getEntityResolver(session), folderIds);
    }

    /**
     * Calculates a map holding the identifiers of all folders a user is able to access, based on the supplied collection of folder
     * identifiers.
     *
     * @param contextId The context identifier
     * @param entityResolver The entity resolver, or <code>null</code> if not available
     * @param folderIds The identifiers of all folders to determine the users with access for
     * @return The identifiers of the affected folders for each user
     */
    public static Map<Integer, List<String>> getAffectedFoldersPerUser(int contextId, EntityResolver entityResolver, Collection<String> folderIds) throws OXException {
        DefaultEntityResolver defaultEntityResolver;
        if (null != entityResolver && contextId == entityResolver.getContextID() && DefaultEntityResolver.class.isInstance(entityResolver)) {
            defaultEntityResolver = (DefaultEntityResolver) entityResolver;
        } else {
            defaultEntityResolver = new DefaultEntityResolver(contextId, Services.getServiceLookup());
        }
        return getAffectedFoldersPerUser(defaultEntityResolver, folderIds);
    }

    private static Map<Integer, List<String>> getAffectedFoldersPerUser(DefaultEntityResolver entityResolver, Collection<String> folderIds) {
        Map<Integer, List<String>> affectedFoldersPerUser = new HashMap<Integer, List<String>>();
        for (String folderId : folderIds) {
            try {
                FolderObject folder = entityResolver.getFolder(asInt(folderId));
                for (Integer userId : getAffectedUsers(folder, entityResolver)) {
                    com.openexchange.tools.arrays.Collections.put(affectedFoldersPerUser, userId, folderId);
                }
            } catch (Exception e) {
                LOG.warn("Error collecting affected users for folder {}; skipping.", folderId, e);
            }
        }
        return affectedFoldersPerUser;
    }

    /**
     * Gets a list of the personal folder identifiers representing all internal user attendee's view for the supplied collection of
     * attendees.
     *
     * @param attendees The attendees to collect the folder identifiers for
     * @return The personal folder identifiers of the internal user attendees, or an empty list if there are none
     */
    public static List<String> getPersonalFolderIds(List<Attendee> attendees) {
        List<String> folderIds = new ArrayList<String>();
        for (Attendee attendee : CalendarUtils.filter(attendees, Boolean.TRUE, CalendarUserType.INDIVIDUAL)) {
            String folderId = attendee.getFolderId();
            if (Strings.isNotEmpty(folderId)) {
                folderIds.add(folderId);
            }
        }
        return folderIds;
    }

    /**
     * Gets a collection of all user identifiers for whom a specific folder is visible, i.e. a list of user identifiers who'd be affected
     * by a change in this folder.
     *
     * @param folder The folder to get the affected user identifiers for
     * @param entityResolver The entity resolver to use
     * @return The identifiers of the affected folders for each user
     */
    public static Set<Integer> getAffectedUsers(FolderObject folder, EntityResolver entityResolver) {
        List<OCLPermission> permissions = folder.getPermissions();
        if (null == permissions || 0 == permissions.size()) {
            return Collections.emptySet();
        }
        Set<Integer> affectedUsers = new HashSet<Integer>();
        for (OCLPermission permission : permissions) {
            if (permission.isFolderVisible()) {
                if (permission.isGroupPermission()) {
                    try {
                        int[] groupMembers = entityResolver.getGroupMembers(permission.getEntity());
                        affectedUsers.addAll(Arrays.asList(i2I(groupMembers)));
                    } catch (OXException e) {
                        LOG.warn("Error resolving members of group {} for for folder {}; skipping.", I(permission.getEntity()), I(folder.getObjectID()), e);
                    }
                } else {
                    affectedUsers.add(I(permission.getEntity()));
                }
            }
        }
        return affectedUsers;
    }

    /**
     * Gets a collection of all user identifiers for whom a specific folder is visible, i.e. a list of user identifiers who'd be affected
     * by a change in this folder.
     *
     * @param folder The folder to get the affected user identifiers for
     * @param entityResolver The entity resolver to use
     * @return The identifiers of the affected folders for each user
     */
    public static Set<Integer> getAffectedUsers(CalendarFolder folder, EntityResolver entityResolver) {
        Permission[] permissions = folder.getPermissions();
        if (null == permissions || 0 == permissions.length) {
            return Collections.emptySet();
        }
        Set<Integer> affectedUsers = new HashSet<Integer>();
        for (Permission permission : permissions) {
            if (permission.isVisible()) {
                if (permission.isGroup()) {
                    try {
                        int[] groupMembers = entityResolver.getGroupMembers(permission.getEntity());
                        affectedUsers.addAll(Arrays.asList(i2I(groupMembers)));
                    } catch (OXException e) {
                        LOG.warn("Error resolving members of group {} for for folder {}; skipping.", I(permission.getEntity()), folder.getId(), e);
                    }
                } else {
                    affectedUsers.add(I(permission.getEntity()));
                }
            }
        }
        return affectedUsers;
    }

    /**
     * Gets a value indicating whether an event update will cover a different time period than the original event. If this is the case,
     * conflicts may have to be re-checked or if the attendee's participation status should be reseted.
     *
     * @param originalEvent The original event being updated
     * @param updatedEvent The updated event, as passed by the client
     * @return <code>true</code> if the updated event covers a different times period, <code>false</code>, otherwise
     * @see <a href="https://tools.ietf.org/html/rfc6638#section-3.2.8">RFC 6638, section 3.2.8</a>
     */
    public static boolean coversDifferentTimePeriod(Event originalEvent, Event updatedEvent) throws OXException {
        if (false == EventMapper.getInstance().get(EventField.RECURRENCE_RULE).equals(originalEvent, updatedEvent)) {
            /*
             * true if there are 'new' occurrences (caused by a modified or extended rule)
             */
            if (hasFurtherOccurrences(originalEvent.getRecurrenceRule(), updatedEvent.getRecurrenceRule())) {
                return true;
            }
        }
        if (false == EventMapper.getInstance().get(EventField.DELETE_EXCEPTION_DATES).equals(originalEvent, updatedEvent)) {
            /*
             * true if there are 'new' occurrences (caused by the reinstatement of previous delete exceptions)
             */
            SimpleCollectionUpdate<RecurrenceId> exceptionDateUpdates = getExceptionDateUpdates(originalEvent.getDeleteExceptionDates(), updatedEvent.getDeleteExceptionDates());
            if (false == exceptionDateUpdates.getRemovedItems().isEmpty()) {
                return true;
            }
        }
        if (false == EventMapper.getInstance().get(EventField.RECURRENCE_DATES).equals(originalEvent, updatedEvent)) {
            /*
             * true if there are 'new' occurrences (caused by newly introduced recurrence dates)
             */
            SimpleCollectionUpdate<RecurrenceId> exceptionDateUpdates = getExceptionDateUpdates(originalEvent.getRecurrenceDates(), updatedEvent.getRecurrenceDates());
            if (false == exceptionDateUpdates.getAddedItems().isEmpty()) {
                return true;
            }
        }
        if (false == EventMapper.getInstance().get(EventField.START_DATE).equals(originalEvent, updatedEvent)) {
            /*
             * true if updated start is before the original start
             */
            if (updatedEvent.getStartDate().before(originalEvent.getStartDate())) {
                return true;
            }
        }
        if (false == EventMapper.getInstance().get(EventField.END_DATE).equals(originalEvent, updatedEvent)) {
            /*
             * true if updated end is after the original end
             */
            if (updatedEvent.getEndDate().after(originalEvent.getEndDate())) {
                return true;
            }
        }
        /*
         * no different time period, otherwise
         */
        return false;
    }

    /**
     * Gets an iterator for the recurrence set of the supplied series master event, iterating over the occurrences of the event series.
     * <p/>
     * Any exception dates (as per {@link Event#getDeleteExceptionDates()}) and overridden instances (as per {@link
     * Event#getChangeExceptionDates()}) are skipped implicitly, so that those occurrences won't be included in the resulting iterator.
     * <p/>
     * Start- and end of the considered range are taken from the corresponding parameters in the supplied session.
     *
     * @param session The calendar session
     * @param masterEvent The recurring event master
     * @return The recurrence iterator
     */
    public static Iterator<Event> resolveOccurrences(CalendarSession session, Event masterEvent) throws OXException {
        return resolveOccurrences(session, masterEvent, getFrom(session), getUntil(session));
    }

    /**
     * Gets an iterator for the recurrence set of the supplied series master event, iterating over the occurrences of the event series.
     * <p/>
     * Any exception dates (as per {@link Event#getDeleteExceptionDates()}) and overridden instances (as per {@link
     * Event#getChangeExceptionDates()}) are skipped implicitly, so that those occurrences won't be included in the resulting iterator.
     *
     * @param session The calendar session
     * @param masterEvent The recurring event master
     * @param from The start of the iteration interval, or <code>null</code> to start with the first occurrence
     * @param until The end of the iteration interval, or <code>null</code> to iterate until the last occurrence
     * @return The recurrence iterator
     */
    public static Iterator<Event> resolveOccurrences(CalendarSession session, Event masterEvent, Date from, Date until) throws OXException {
        return session.getRecurrenceService().iterateEventOccurrences(masterEvent, from, until);
    }

    /**
     * Gets an iterator for the recurrence set of the supplied series master event, iterating over the recurrence identifiers of the event.
     * <p/>
     * Any exception dates (as per {@link Event#getDeleteExceptionDates()}) and overridden instances (as per {@link
     * Event#getChangeExceptionDates()}) are skipped implicitly, so that those occurrences won't be included in the resulting iterator.
     * <p/>
     * Start- and end of the considered range are taken from the corresponding parameters in the supplied session.
     *
     * @param session The calendar session
     * @param masterEvent The recurring event master
     * @return The recurrence iterator
     */
    public static Iterator<RecurrenceId> getRecurrenceIterator(CalendarSession session, Event masterEvent) throws OXException {
        return getRecurrenceIterator(session, masterEvent, getFrom(session), getUntil(session));
    }

    /**
     * Gets a recurrence iterator for the supplied series master event, iterating over the recurrence identifiers of the event.
     * <p/>
     * Any exception dates (as per {@link Event#getDeleteExceptionDates()}) and overridden instances (as per {@link
     * Event#getChangeExceptionDates()}) are skipped implicitly, so that those occurrences won't be included in the resulting iterator.
     *
     * @param session The calendar session
     * @param masterEvent The recurring event master
     * @param from The start of the iteration interval, or <code>null</code> to start with the first occurrence
     * @param until The end of the iteration interval, or <code>null</code> to iterate until the last occurrence
     * @return The recurrence iterator
     */
    public static Iterator<RecurrenceId> getRecurrenceIterator(CalendarSession session, Event masterEvent, Date from, Date until) throws OXException {
        return session.getRecurrenceService().iterateRecurrenceIds(new DefaultRecurrenceData(masterEvent), from, until);
    }

    /**
     * Tracks newly added attendees from creations and updates found in the supplied calendar result, in case attendee tracking is
     * enabled as per {@link CalendarParameters#PARAMETER_TRACK_ATTENDEE_USAGE}.
     *
     * @param session The calendar session
     * @param event The calendar event to track
     */
    public static void trackAttendeeUsage(CalendarSession session, CalendarEvent event) {
        if (null != event && b(session.get(CalendarParameters.PARAMETER_TRACK_ATTENDEE_USAGE, Boolean.class, Boolean.FALSE))) {
            new AttendeeUsageTracker(session.getEntityResolver()).track(event);
        }
    }

    /**
     *
     * Processes an {@link InternalCalendarResult}. Informs {@link CalendarHandler} and triggers the {@link SchedulingBroker} to send messages.
     * <p>
     * Does <b>NOT</b> track attendee usage as per {@link #trackAttendeeUsage(CalendarSession, CalendarEvent)}
     *
     * @param <T> The type of the {@link InternalCalendarResult}
     * @param services The {@link ServiceLookup} to obtain the {@link CalendarEventNotificationService} and the {@link SchedulingBroker} from
     * @param result The actual result
     * @return The given result, unmodified.
     */
    public static <T extends InternalCalendarResult> T postProcess(ServiceLookup services, T result) {
        return postProcess(services, result, false);
    }

    /**
     * Processes an {@link InternalCalendarResult}. Tracks attendee usage, informs {@link CalendarHandler} and triggers
     * the {@link SchedulingBroker} to send messages.
     *
     * @param <T> The type of the {@link InternalCalendarResult}
     * @param services The {@link ServiceLookup} to obtain the {@link CalendarEventNotificationService} and the {@link SchedulingBroker} from
     * @param result The actual result
     * @param trackAttendeeUsage whether to track the attendee usage as per {@link #trackAttendeeUsage(CalendarSession, CalendarEvent)} or not.
     * @return The given result, unmodified.
     */
    public static <T extends InternalCalendarResult> T postProcess(ServiceLookup services, T result, boolean trackAttendeeUsage) {
        return postProcess(services, Collections.singletonList(result), trackAttendeeUsage).get(0);
    }

    /**
     * Processes {@link InternalCalendarResult}s. Tracks attendee usage, informs {@link CalendarHandler}s and triggers
     * the {@link SchedulingBroker} to send messages.
     *
     * @param <T> The type of the {@link InternalCalendarResult}s
     * @param services The {@link ServiceLookup} to obtain the {@link CalendarEventNotificationService} and the {@link SchedulingBroker} from
     * @param results The actual results
     * @param trackAttendeeUsage whether to track the attendee usage as per {@link #trackAttendeeUsage(CalendarSession, CalendarEvent)} or not.
     * @return The given results, unmodified.
     */
    public static <T extends InternalCalendarResult> List<T> postProcess(ServiceLookup services, List<T> results, boolean trackAttendeeUsage) {
        if (null == results || results.isEmpty()) {
            return results;
        }
        ThreadPools.submitElseExecute(ThreadPools.task(() -> {
            for (T result : results) {
                /*
                 * track attendee usage as needed & notify registered calendar handlers
                 */
                CalendarEvent calendarEvent = result.getCalendarEvent();
                if (trackAttendeeUsage) {
                    trackAttendeeUsage(result.getSession(), calendarEvent);
                }
                CalendarEventNotificationService notificationService = services.getService(CalendarEventNotificationService.class);
                if (null != notificationService) {
                    notificationService.notifyHandlers(calendarEvent, false);
                }
                /*
                 * handle pending scheduling messages
                 */
                SchedulingBroker schedulingBroker = services.getService(SchedulingBroker.class);
                if (null != schedulingBroker) {
                    List<SchedulingMessage> messages = result.getSchedulingMessages();
                    if (null != messages && 0 < messages.size()) {
                        schedulingBroker.handleScheduling(result.getSession().getSession(), messages);
                    }
                    List<ChangeNotification> notifications = result.getChangeNotifications();
                    if (null != notifications && 0 < notifications.size()) {
                        schedulingBroker.handleNotifications(result.getSession().getSession(), notifications);
                    }
                }
            }
        }));
        return results;
    }

    /**
     * Gets the whitelist of identifiers of those entities that should be resolved automatically when data of the event is passed to the
     * entity resolver.
     * <p/>
     * For externally organized events, only the calendar user itself should be resolved, otherwise, there are no restrictions.
     *
     * @param session The calendar session
     * @param folder The parent folder of the event being processed
     * @param event The event being processed
     * @return The identifiers of those entities that should be resolved automatically as used by the entity resolver
     */
    public static int[] getResolvableEntities(CalendarSession session, CalendarFolder folder, Event event) {
        if (false == isGroupScheduled(event)) {
            return null;
        }
        int[] calendarUserOnlyEntities = new int[] { folder.getCalendarUserId() };
        try {
            CalendarUser preparedOrganizer = session.getEntityResolver().prepare(
                event.getOrganizer(), CalendarUserType.INDIVIDUAL, calendarUserOnlyEntities);
            if (isInternal(preparedOrganizer, CalendarUserType.INDIVIDUAL)) {
                return null;
            }
        } catch (OXException e) {
            LOG.warn("Error checking if event has internal organizer, resolving calendar user, only.", e);
        }
        return calendarUserOnlyEntities;
    }

    /**
     * Prepares the organizer for an event, taking over an external organizer if specified.
     *
     * @param session The calendar session
     * @param folder The target calendar folder of the event
     * @param organizerData The organizer as defined by the client, or <code>null</code> to prepare the default organizer for the target folder
     * @param resolvableEntities A whitelist of identifiers of those entities that should be resolved by their URI value, or
     *            <code>null</code> to resolve all resolvable entities
     * @return The prepared organizer
     */
    public static Organizer prepareOrganizer(CalendarSession session, CalendarFolder folder, Organizer organizerData, int[] resolvableEntities) throws OXException {
        CalendarUser calendarUser = getCalendarUser(session, folder);
        Organizer organizer;
        if (null != organizerData) {
            organizer = session.getEntityResolver().prepare(organizerData, CalendarUserType.INDIVIDUAL, resolvableEntities);
            if (isInternal(organizer, CalendarUserType.INDIVIDUAL)) {
                /*
                 * internal organizer must match the actual calendar user if specified
                 */
                if (organizer.getEntity() != calendarUser.getEntity()) {
                    throw CalendarExceptionCodes.INVALID_CALENDAR_USER.create(organizer.getUri(), I(organizer.getEntity()), CalendarUserType.INDIVIDUAL);
                }
            } else {
                /*
                 * take over external organizer as-is
                 */
                return isSkipExternalAttendeeURIChecks(session) ? organizer : Check.requireValidEMail(organizer);
            }
        } else {
            /*
             * prepare a default organizer for calendar user
             */
            organizer = session.getEntityResolver().applyEntityData(new Organizer(), calendarUser.getEntity());
        }
        /*
         * apply "sent-by" property if someone is acting on behalf of the calendar user
         */
        if (calendarUser.getEntity() != session.getUserId()) {
            organizer.setSentBy(session.getEntityResolver().applyEntityData(new CalendarUser(), session.getUserId()));
        }
        return organizer;
    }

    /**
     * Optionally gets a database connection set in a specific calendar session.
     *
     * @param session The calendar session to get the connection from
     * @return The connection, or <code>null</code> if not defined
     */
    public static Connection optConnection(CalendarSession session) {
        return session.get(PARAMETER_CONNECTION(), Connection.class, null);
    }

    /**
     * Extracts (and optionally removes) specific calendar parameters from the supplied parameter container.
     *
     * @param parameters The parameters to extract
     * @param remove <code>true</code> to remove the extracted parameters from the original parameter container, <code>false</code> to
     *            keep them
     * @param parameterNames The names of the parameters to extract
     * @return The extracted calendar parameters in a new parameter container
     */
    public static CalendarParameters extract(CalendarParameters parameters, boolean remove, String... parameterNames) {
        DefaultCalendarParameters extractedParameters = new DefaultCalendarParameters();
        if (null != parameterNames) {
            for (String parameterName : parameterNames) {
                Object value = parameters.get(parameterName, Object.class);
                if (null != value) {
                    extractedParameters.set(parameterName, value);
                    if (remove) {
                        parameters.set(parameterName, null);
                    }
                }
            }
        }
        return extractedParameters;
    }

    /**
     * Copies all calendar parameters from one parameter container into another one.
     *
     * @param from The calendar parameters to copy
     * @param to The target calendar parameters to copy into
     * @return The possibly modified target calendar parameters
     */
    public static CalendarParameters copy(CalendarParameters from, CalendarParameters to) {
        if (null != to && null != from) {
            for (Entry<String, Object> entry : from.entrySet()) {
                to.set(entry.getKey(), entry.getValue());
            }
        }
        return to;
    }

    private static DefaultEntityResolver getEntityResolver(CalendarSession session) throws OXException {
        if (DefaultEntityResolver.class.isInstance(session.getEntityResolver())) {
            return (DefaultEntityResolver) session.getEntityResolver();
        }
        return new DefaultEntityResolver(ServerSessionAdapter.valueOf(session.getSession()), Services.getServiceLookup());
    }

    private static java.lang.reflect.Field[] initSimpleTimeZoneRuleFields() {
        try {
            java.lang.reflect.Field[] fields = new java.lang.reflect.Field[] {
                SimpleTimeZone.class.getDeclaredField("rawOffset"),
                SimpleTimeZone.class.getDeclaredField("startMonth"),
                SimpleTimeZone.class.getDeclaredField("startDay"),
                SimpleTimeZone.class.getDeclaredField("startDayOfWeek"),
                SimpleTimeZone.class.getDeclaredField("endMonth"),
                SimpleTimeZone.class.getDeclaredField("endDay"),
                SimpleTimeZone.class.getDeclaredField("endDayOfWeek"),
                SimpleTimeZone.class.getDeclaredField("dstSavings")
            };
            java.lang.reflect.Field.setAccessible(fields, true);
            return fields;
        } catch (NoSuchFieldException | SecurityException e) {
            LOG.warn("Error initialzing SimpleTimeZone rule fields through reflection, smart timezone selection won't be available.", e);
            return null;
        }
    }

}
