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

package com.openexchange.chronos.provider.composition.impl.idmangling;

import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.CalendarObjectResource;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.FreeBusyTime;
import com.openexchange.chronos.common.CalendarUtils;
import com.openexchange.chronos.common.DefaultCalendarObjectResource;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.AccountAwareCalendarFolder;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarFolder;
import com.openexchange.chronos.provider.groupware.GroupwareCalendarFolder;
import com.openexchange.chronos.service.ErrorAwareCalendarResult;
import com.openexchange.chronos.service.EventConflict;
import com.openexchange.chronos.service.EventID;
import com.openexchange.chronos.service.EventsResult;
import com.openexchange.chronos.service.FreeBusyResult;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionCode;

/**
 * {@link IDMangling}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class IDMangling extends com.openexchange.chronos.provider.composition.IDMangling {

    /** The pattern to lookup folder placeholders in calendar exception messages */
    private static final Pattern FOLDER_ARGUMENT_PATTERN = Pattern.compile("(?:\\[|, )folder %(\\d)\\$s(?:\\]|,)");

    /**
     * Adjusts a list of exceptions raised by a specific calendar account so that any referenced identifiers appear in their unique
     * composite representation.
     *
     * @param exceptions The exception to adjust
     * @param accountId The identifier of the account
     * @return The possibly adjusted exceptions
     */
    public static List<OXException> withUniqueID(List<OXException> exceptions, int accountId) {
        if (null == exceptions || exceptions.isEmpty()) {
            return exceptions;
        }
        List<OXException> adjustedExceptions = new ArrayList<OXException>(exceptions.size());
        for (OXException e : exceptions) {
            adjustedExceptions.add(withUniqueIDs(e, accountId));
        }
        return adjustedExceptions;
    }

    /**
     * Adjusts an exception raised by a specific calendar account so that any referenced identifiers appear in their unique composite
     * representation.
     *
     * @param e The exception to adjust, or <code>null</code> to do nothing
     * @param accountId The identifier of the account
     * @return The possibly adjusted exception
     */
    public static OXException withUniqueIDs(OXException e, int accountId) {
        if (null == e || false == e.isPrefix("CAL")) {
            return e;
        }
        switch (e.getCode()) {
            case 4091:
            case 4092:
                e = adjustEventConflicts(e, accountId);
                break;
            default:
                break;
        }
        return adjustFolderArguments(e, accountId);
    }

    /**
     * Gets a calendar folder equipped with unique composite identifiers representing a calendar folder from a specific calendar account.
     *
     * @param folders The calendar folder from the account
     * @param accountId The identifier of the account
     * @return The calendar folder representation with unique identifiers
     */
    public static AccountAwareCalendarFolder withUniqueID(CalendarFolder folder, CalendarAccount account) {
        if (GroupwareCalendarFolder.class.isInstance(folder)) {
            GroupwareCalendarFolder groupwareFolder = (GroupwareCalendarFolder) folder;
            String newId = getUniqueFolderId(account.getAccountId(), folder.getId(), true);
            String newParentId = getUniqueFolderId(account.getAccountId(), groupwareFolder.getParentId(), true);
            return new IDManglingAccountAwareGroupwareFolder(groupwareFolder, account, newId, newParentId);
        }
        return new IDManglingAccountAwareFolder(folder, account, getUniqueFolderId(account.getAccountId(), folder.getId()));
    }

    /**
     * Gets a list of calendar folders equipped with unique composite identifiers representing the supplied list of calendar folders from
     * a specific calendar account.
     *
     * @param folders The calendar folders from the account
     * @param account The calendar account
     * @return The calendar folder representations with unique identifiers
     */
    public static List<AccountAwareCalendarFolder> withUniqueID(List<? extends CalendarFolder> folders, CalendarAccount account) {
        if (null == folders) {
            return null;
        }
        List<AccountAwareCalendarFolder> foldersWithUniqueIDs = new ArrayList<AccountAwareCalendarFolder>(folders.size());
        for (CalendarFolder folder : folders) {
            foldersWithUniqueIDs.add(withUniqueID(folder, account));
        }
        return foldersWithUniqueIDs;
    }

    /**
     * Gets the account-relative representation for the supplied calendar folder with unique composite identifiers.
     *
     * @param folder The calendar folder
     * @return The calendar folder representation with relative identifiers
     */
    public static CalendarFolder withRelativeID(CalendarFolder folder) throws OXException {
        String newId = getRelativeFolderId(folder.getId());
        if (GroupwareCalendarFolder.class.isInstance(folder)) {
            GroupwareCalendarFolder groupwareFolder = (GroupwareCalendarFolder) folder;
            String newParentId = getRelativeFolderId(groupwareFolder.getParentId());
            return new IDManglingGroupwareFolder(groupwareFolder, newId, newParentId);
        }
        return new IDManglingFolder(folder, newId);
    }

    /**
     * Gets an event equipped with unique composite identifiers representing an event from a specific calendar account.
     *
     * @param event The event from the account, or <code>null</code> to pass through
     * @param accountId The identifier of the account
     * @return The event representation with unique identifiers
     */
    public static Event withUniqueID(Event event, int accountId) {
        if (null == event) {
            return null;
        }
        String newFolderId = getUniqueFolderId(accountId, event.getFolderId());
        return new IDManglingEvent(event, newFolderId);
    }

    /**
     * Gets a list of events equipped with unique composite identifiers representing the supplied list of events from a specific
     * calendar account.
     *
     * @param events The events from the account
     * @param accountId The identifier of the account
     * @return The event representations with unique identifiers
     */
    public static List<Event> withUniqueIDs(List<Event> events, int accountId) {
        if (null == events) {
            return null;
        }
        List<Event> eventsWithUniqueIDs = new ArrayList<Event>(events.size());
        for (Event event : events) {
            eventsWithUniqueIDs.add(withUniqueID(event, accountId));
        }
        return eventsWithUniqueIDs;
    }

    /**
     * Gets a free/busy time equipped with unique composite identifiers representing a free/busy time from a specific calendar account.
     *
     * @param freeBusyTime The free/busy time from the account
     * @param accountId The identifier of the account
     * @return The free/busy time representation with unique identifiers
     */
    public static FreeBusyTime withUniqueID(FreeBusyTime freeBusyTime, int accountId) {
        Event event = freeBusyTime.getEvent();
        if (null == event) {
            return freeBusyTime;
        }
        return new FreeBusyTime(freeBusyTime.getFbType(), freeBusyTime.getStartTime(), freeBusyTime.getEndTime(), withUniqueID(event, accountId));
    }

    /**
     * Gets a free/busy result equipped with unique composite identifiers representing a free/busy result from a specific calendar account.
     *
     * @param freeBusyResult The free/busy result from the account
     * @param accountId The identifier of the account
     * @return The free/busy result representation with unique identifiers
     */
    public static FreeBusyResult withUniqueID(FreeBusyResult freeBusyResult, int accountId) {
        List<FreeBusyTime> freeBusyTimes = freeBusyResult.getFreeBusyTimes();
        if (null == freeBusyTimes || freeBusyTimes.isEmpty()) {
            return freeBusyResult;
        }
        List<FreeBusyTime> timesWithUniqueIDs = new ArrayList<FreeBusyTime>(freeBusyTimes.size());
        for (FreeBusyTime freeBusyTime : freeBusyTimes) {
            timesWithUniqueIDs.add(withUniqueID(freeBusyTime, accountId));
        }
        freeBusyResult.setFreeBusyTimes(timesWithUniqueIDs);
        return freeBusyResult;
    }

    /**
     * Gets a map of events results equipped with unique composite identifiers representing results from a specific calendar account.
     *
     * @param relativeResults The event results from the account
     * @param accountId The identifier of the account
     * @return The events result representations with unique identifiers
     */
    public static Map<String, EventsResult> withUniqueIDs(Map<String, EventsResult> relativeResults, int accountId) {
        if (null == relativeResults || relativeResults.isEmpty()) {
            return relativeResults;
        }
        Map<String, EventsResult> results = new HashMap<String, EventsResult>(relativeResults.size());
        for (Map.Entry<String, EventsResult> entry : relativeResults.entrySet()) {
            results.put(getUniqueFolderId(accountId, entry.getKey()), new IDManglingEventsResult(entry.getValue(), accountId));
        }
        return results;
    }

    /**
     * Gets a map of calendar results equipped with unique composite identifiers representing results from a specific calendar account.
     *
     * @param relativeResults The results from the account
     * @param accountId The identifier of the account
     * @return The calendar result representations with unique identifiers
     */
    public static Map<EventID, ErrorAwareCalendarResult> withUniqueEventIDs(Map<EventID, ErrorAwareCalendarResult> relativeResults, int accountId) {
        if (null == relativeResults || relativeResults.isEmpty()) {
            return relativeResults;
        }
        Map<EventID, ErrorAwareCalendarResult> results = new HashMap<EventID, ErrorAwareCalendarResult>(relativeResults.size());
        for (Entry<EventID, ErrorAwareCalendarResult> entry : relativeResults.entrySet()) {
            results.put(getUniqueId(accountId, entry.getKey()), new IDManglingErrorAwareCalendarResult(entry.getValue(), accountId));
        }
        return results;
    }

    /**
     * Gets the account-relative representation for the supplied event with unique composite identifiers.
     *
     * @param event The event
     * @return The event representation with relative identifiers
     */
    public static Event withRelativeID(Event event) throws OXException {
        String newFolderId = getRelativeFolderId(event.getFolderId());
        return new IDManglingEvent(event, newFolderId);
    }

    /**
     * Gets the account-relative representation for the supplied calendar object resource with unique composite identifiers.
     *
     * @param resource The calendar object resource
     * @return The resource representation with relative identifiers
     */
    public static CalendarObjectResource withRelativeID(CalendarObjectResource resource) throws OXException {
        if (null == resource) {
            return resource;
        }
        List<Event> eventsWithRelativeIDs = new ArrayList<Event>(resource.getEvents().size());
        for (Event event : resource.getEvents()) {
            eventsWithRelativeIDs.add(withRelativeID(event));
        }
        return new DefaultCalendarObjectResource(eventsWithRelativeIDs);
    }

    /**
     * Gets the account identifier of a specific unique composite folder identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} as well as identifiers starting with {@link IDMangling#SHARED_PREFIX} will always yield the
     * identifier of the default account.
     *
     * @param uniqueFolderId The unique composite folder identifier, e.g. <code>cal://4/35</code>
     * @return The extracted account identifier
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_FOLDER} if the account identifier can't be extracted from the passed composite identifier
     */
    public static int getAccountId(String uniqueFolderId) throws OXException {
        if (ROOT_FOLDER_IDS.contains(uniqueFolderId) || uniqueFolderId.startsWith(SHARED_PREFIX)) {
            return CalendarAccount.DEFAULT_ACCOUNT.getAccountId();
        }
        try {
            return Integer.parseInt(unmangleFolderId(uniqueFolderId).get(1));
        } catch (IllegalArgumentException e) {
            throw CalendarExceptionCodes.UNSUPPORTED_FOLDER.create(e, uniqueFolderId, null);
        }
    }

    /**
     * Gets the relative representation of a list of unique composite folder identifier, mapped to their associated account identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} are passed as-is implicitly, mapped to the default account.
     *
     * @param uniqueFolderIds The unique composite folder identifiers, e.g. <code>cal://4/35</code>
     * @return The relative folder identifiers, mapped to their associated calendar account identifier
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_FOLDER} if the account identifier can't be extracted from a passed composite identifier
     */
    public static Map<Integer, List<String>> getRelativeFolderIdsPerAccountId(List<String> uniqueFolderIds) throws OXException {
        if (null == uniqueFolderIds) {
            return null;
        }
        Map<Integer, List<String>> foldersPerAccountId = new HashMap<Integer, List<String>>(uniqueFolderIds.size());
        for (String uniqueFolderId : uniqueFolderIds) {
            try {
                List<String> unmangledId = unmangleFolderId(uniqueFolderId);
                Integer accountId = Integer.valueOf(unmangledId.get(1));
                String relativeFolderId = unmangledId.get(2);
                com.openexchange.tools.arrays.Collections.put(foldersPerAccountId, accountId, relativeFolderId);
            } catch (IllegalArgumentException e) {
                throw CalendarExceptionCodes.UNSUPPORTED_FOLDER.create(e, uniqueFolderId, null);
            }
        }
        return foldersPerAccountId;
    }

    /**
     * Gets the relative representation of a list of unique composite folder identifier, mapped to their associated account identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} are passed as-is implicitly, mapped to the default account.
     *
     * @param uniqueFolderIds The unique composite folder identifiers, e.g. <code>cal://4/35</code>
     * @param errorsPerFolderId A map to track possible errors that occurred when parsing the supplied identifiers
     * @return The relative folder identifiers, mapped to their associated calendar account identifier
     */
    public static Map<Integer, List<String>> getRelativeFolderIdsPerAccountId(List<String> uniqueFolderIds, Map<String, OXException> errorsPerFolderId) {
        if (null == uniqueFolderIds || uniqueFolderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<String>> foldersPerAccountId = new HashMap<Integer, List<String>>(uniqueFolderIds.size());
        for (String uniqueFolderId : uniqueFolderIds) {
            try {
                List<String> unmangledId = unmangleFolderId(uniqueFolderId);
                Integer accountId = Integer.valueOf(unmangledId.get(1));
                String relativeFolderId = unmangledId.get(2);
                com.openexchange.tools.arrays.Collections.put(foldersPerAccountId, accountId, relativeFolderId);
            } catch (IllegalArgumentException e) {
                errorsPerFolderId.put(uniqueFolderId, CalendarExceptionCodes.UNSUPPORTED_FOLDER.create(e, uniqueFolderId, null));
            }
        }
        return foldersPerAccountId;
    }

    /**
     * Gets the relative representation of a list of unique composite event identifiers, mapped to their associated account identifier.
     * <p/>
     * Event IDs whose folder denotes one of the {@link IDMangling#ROOT_FOLDER_IDS} are passed as-is implicitly, mapped to the default account.
     *
     * @param eventIds The event ids with unique composite folder identifiers, e.g. <code>cal://4/35</code>
     * @param errorsPerEventId A map to track possible errors that occurred when parsing the supplied identifiers
     * @return Event identifiers with relative folder identifiers, mapped to their associated calendar account identifier
     */
    public static Map<Integer, List<EventID>> getRelativeEventIdsPerAccountId(List<EventID> uniqueEventIds, Map<EventID, OXException> errorsPerEventId) {
        if (null == uniqueEventIds || uniqueEventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<EventID>> eventIdsPerAccountId = new HashMap<Integer, List<EventID>>(uniqueEventIds.size());
        for (EventID uniqueEventId : uniqueEventIds) {
            try {
                Integer accountId = I(getAccountId(uniqueEventId.getFolderID()));
                EventID relativeEventId = getRelativeId(uniqueEventId);
                com.openexchange.tools.arrays.Collections.put(eventIdsPerAccountId, accountId, relativeEventId);
            } catch (OXException | IllegalArgumentException e) {
                errorsPerEventId.put(uniqueEventId, CalendarExceptionCodes.UNSUPPORTED_FOLDER.create(e, uniqueEventId, null));
            }
        }
        return eventIdsPerAccountId;
    }

    /**
     * Gets the relative representation of a list of unique full event identifier, mapped to their associated account identifier.
     *
     * @param uniqueFolderIds The unique composite folder identifiers, e.g. <code>cal://4/35</code>
     * @return The relative folder identifiers, mapped to their associated calendar account identifier
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_FOLDER} if the account identifier can't be extracted from a passed composite identifier
     */
    public static Map<Integer, List<EventID>> getRelativeIdsPerAccountId(List<EventID> uniqueEventIDs) throws OXException {
        Map<Integer, List<EventID>> idsPerAccountId = new HashMap<Integer, List<EventID>>();
        for (EventID eventID : uniqueEventIDs) {
            Integer accountId = I(getAccountId(eventID.getFolderID()));
            EventID relativeEventId = getRelativeId(eventID);
            com.openexchange.tools.arrays.Collections.put(idsPerAccountId, accountId, relativeEventId);
        }
        return idsPerAccountId;
    }

    /**
     * Gets a conflict exception with adjusted problematic attributes so that any contained events details will indicate the unique
     * composite identifiers from a specific calendar account.
     *
     * @param e The event conflict exception to adjust
     * @param accountId The identifier of the account
     * @return The event representations with unique identifiers, or the passed exception as-is in case there's nothing to adjust
     */
    private static OXException adjustEventConflicts(OXException e, int accountId) {
        if (4091 != e.getCode() && 4092 != e.getCode()) {
            return e;
        }
        List<EventConflict> eventConflicts = CalendarUtils.extractEventConflicts(e);
        if (null == eventConflicts || 0 == eventConflicts.size()) {
            return e;
        }
        OXException newException;
        if (4091 == e.getCode()) {
            newException = CalendarExceptionCodes.EVENT_CONFLICTS.create(e.getCause(), e.getLogArgs());
        } else {
            newException = CalendarExceptionCodes.HARD_EVENT_CONFLICTS.create(e.getCause(), e.getLogArgs());
        }
        for (EventConflict eventConflict : eventConflicts) {
            newException.addProblematic(new IDManglingEventConflict(eventConflict, accountId));
        }
        return newException;
    }

    /**
     * Adjusts the log arguments indicating a <code>folder</code> in an exception raised by a specific calendar account so that any
     * referenced folder identifiers appear in their unique composite representation.
     *
     * @param e The calendar exception to adjust
     * @param accountId The identifier of the account
     * @return The possibly adjusted exception
     */
    private static OXException adjustFolderArguments(OXException e, int accountId) {
        try {
            OXExceptionCode exceptionCode = e.getExceptionCode();
            Object[] logArgs = e.getLogArgs();
            if (null != logArgs && 0 < logArgs.length && null != exceptionCode && null != exceptionCode.getMessage()) {
                boolean adjusted = false;
                Matcher matcher = FOLDER_ARGUMENT_PATTERN.matcher(exceptionCode.getMessage());
                while (matcher.find()) {
                    int argumentIndex = Integer.parseInt(matcher.group(1));
                    if (0 < argumentIndex && argumentIndex <= logArgs.length && String.class.isInstance(logArgs[argumentIndex - 1])) {
                        logArgs[argumentIndex - 1] = getUniqueFolderId(accountId, (String) logArgs[argumentIndex - 1]);
                        adjusted = true;
                    }
                }
                if (adjusted) {
                    e.setLogMessage(exceptionCode.getMessage(), logArgs);
                }
            }
        } catch (Exception x) {
            LoggerFactory.getLogger(IDMangling.class).warn(
                "Unexpected error while attempting to replace exception log arguments for {}", e.getLogMessage(), x);
        }
        return e;
    }

}
