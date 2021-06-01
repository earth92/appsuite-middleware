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

package com.openexchange.chronos.provider.composition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.basic.BasicCalendarAccess;
import com.openexchange.chronos.service.EventID;
import com.openexchange.exception.OXException;
import com.openexchange.tools.id.IDMangler;

/**
 * {@link IDMangling}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class IDMangling {

    /** The fixed prefix used to quickly identify calendar folder identifiers. */
    protected static final String CAL_PREFIX = "cal";

    /** A set of fixed root folder identifiers excluded from ID mangling for the default account */
    protected static final Set<String> ROOT_FOLDER_IDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        null, // no parent
        "0",  // com.openexchange.folderstorage.FolderStorage.ROOT_ID
        "1",  // com.openexchange.folderstorage.FolderStorage.PRIVATE_ID
        "2",  // com.openexchange.folderstorage.FolderStorage.PUBLIC_ID
        "3"   // com.openexchange.folderstorage.FolderStorage.SHARED_ID
    )));

    /** The prefix indicating a the virtual <i>shared</i> root (com.openexchange.groupware.container.FolderObject.SHARED_PREFIX) */
    protected static final String SHARED_PREFIX = "u:";

    /**
     * Gets the qualified identifier for of a calendar account, as used in external APIs.
     * 
     * @param accountId The identifier of the account to get the qualified identifier for
     * @return The qualified account identifier
     */
    public static String getQualifiedAccountId(int accountId) {
        return IDMangler.mangle(CAL_PREFIX, String.valueOf(accountId));
    }

    /**
     * Gets the relative representation of a specific unique composite folder identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} are passed as-is implicitly, same goes for identifiers starting with {@link IDMangling#SHARED_PREFIX}.
     *
     * @param uniqueFolderId The unique composite folder identifier, e.g. <code>cal://4/35</code>
     * @return The extracted relative folder identifier
     * @throws OXException {@link CalendarExceptionCodes#UNSUPPORTED_FOLDER} if passed identifier can't be unmangled to its relative representation
     */
    public static String getRelativeFolderId(String uniqueFolderId) throws OXException {
        if (ROOT_FOLDER_IDS.contains(uniqueFolderId) || uniqueFolderId.startsWith(SHARED_PREFIX)) {
            return uniqueFolderId;
        }
        try {
            return unmangleFolderId(uniqueFolderId).get(2);
        } catch (IllegalArgumentException e) {
            throw CalendarExceptionCodes.UNSUPPORTED_FOLDER.create(e, uniqueFolderId, null);
        }
    }

    /**
     * Gets the fully qualified composite representation of a specific relative folder identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} as well as identifiers starting with {@link IDMangling#SHARED_PREFIX} are passed as-is implicitly,
     * in case the account is the default groupware calendar account.
     *
     * @param accountId The identifier of the account the folder originates in
     * @param relativeFolderId The relative folder identifier
     * @return The unique folder identifier
     */
    public static String getUniqueFolderId(int accountId, String relativeFolderId) {
        return getUniqueFolderId(accountId, relativeFolderId, CalendarAccount.DEFAULT_ACCOUNT.getAccountId() == accountId);
    }

    /**
     * Gets the fully qualified composite representation of a specific relative folder identifier.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} as well as identifiers starting with {@link IDMangling#SHARED_PREFIX} are passed as-is implicitly,
     * in case a <i>groupware</i> calendar access is indicated.
     *
     * @param accountId The identifier of the account the folder originates in
     * @param relativeFolderId The relative folder identifier
     * @param groupwareAccess <code>true</code> if the identifier originates from a <i>groupware</i> calendar access, <code>false</code>, otherwise
     * @return The unique folder identifier
     */
    public static String getUniqueFolderId(int accountId, String relativeFolderId, boolean groupwareAccess) {
        if (groupwareAccess || CalendarAccount.DEFAULT_ACCOUNT.getAccountId() == accountId) {
            if (ROOT_FOLDER_IDS.contains(relativeFolderId) || relativeFolderId.startsWith(SHARED_PREFIX)) {
                return relativeFolderId;
            }
        } else if (null == relativeFolderId) {
            return mangleFolderId(accountId, BasicCalendarAccess.FOLDER_ID);
        }
        return mangleFolderId(accountId, relativeFolderId);
    }

    /**
     * Gets the fully qualified composite representations of a list of specific relative folder identifiers.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} as well as identifiers starting with {@link IDMangling#SHARED_PREFIX} are passed as-is implicitly,
     * in case the account is the default groupware calendar account.
     *
     * @param accountId The identifier of the account the folders originate in
     * @param relativeFolderIds The relative folder identifiers
     * @return The unique folder identifiers
     */
    public static List<String> getUniqueFolderIds(int accountId, List<String> relativeFolderIds) {
        return getUniqueFolderIds(accountId, relativeFolderIds, CalendarAccount.DEFAULT_ACCOUNT.getAccountId() == accountId);
    }

    /**
     * Gets the fully qualified composite representations of a list of specific relative folder identifiers.
     * <p/>
     * {@link IDMangling#ROOT_FOLDER_IDS} as well as identifiers starting with {@link IDMangling#SHARED_PREFIX} are passed as-is implicitly,
     * in case a <i>groupware</i> calendar access is indicated.
     *
     * @param accountId The identifier of the account the folders originate in
     * @param relativeFolderIds The relative folder identifiers
     * @param groupwareAccess <code>true</code> if the identifier originates from a <i>groupware</i> calendar access, <code>false</code>, otherwise
     * @return The unique folder identifiers
     */
    public static List<String> getUniqueFolderIds(int accountId, List<String> relativeFolderIds, boolean groupwareAccess) {
        if (null == relativeFolderIds) {
            return null;
        }
        List<String> uniqueFolderIds = new ArrayList<String>(relativeFolderIds.size());
        for (String relativeFolderId : relativeFolderIds) {
            uniqueFolderIds.add(getUniqueFolderId(accountId, relativeFolderId, groupwareAccess));
        }
        return uniqueFolderIds;
    }

    /**
     * Gets the relative representation of a specific unique full event identifier consisting of composite parts.
     *
     * @param uniqueId The unique full event identifier
     * @return The relative full event identifier
     */
    public static EventID getRelativeId(EventID uniqueEventID) throws OXException {
        if (null == uniqueEventID) {
            return uniqueEventID;
        }
        return new EventID(getRelativeFolderId(uniqueEventID.getFolderID()), uniqueEventID.getObjectID(), uniqueEventID.getRecurrenceID());
    }

    /**
     * Gets the fully qualified composite representation of a specific relative event identifier.
     *
     * @param accountId The identifier of the account the event originates in
     * @param relativeID The relative full event identifier
     * @return The unique full event identifier
     */
    public static EventID getUniqueId(int accountId, EventID relativeID) {
        return new EventID(getUniqueFolderId(accountId, relativeID.getFolderID()), relativeID.getObjectID(), relativeID.getRecurrenceID());
    }

    /**
     * <i>Mangles</i> the supplied relative folder identifier, together with its corresponding account information.
     *
     * @param accountId The identifier of the account the folder originates in
     * @param relativeFolderId The relative folder identifier
     * @return The mangled folder identifier
     */
    protected static String mangleFolderId(int accountId, String relativeFolderId) {
        return IDMangler.mangle(CAL_PREFIX, String.valueOf(accountId), relativeFolderId);
    }

    /**
     * <i>Unmangles</i> the supplied unique folder identifier into its distinct components.
     *
     * @param uniqueFolderId The unique composite folder identifier, e.g. <code>cal://4/35</code>
     * @return The unmangled components of the folder identifier
     * @throws IllegalArgumentException If passed identifier can't be unmangled into its distinct components
     */
    protected static List<String> unmangleFolderId(String uniqueFolderId) {
        if (null == uniqueFolderId || false == uniqueFolderId.startsWith(CAL_PREFIX)) {
            throw new IllegalArgumentException(uniqueFolderId);
        }
        List<String> unmangled = IDMangler.unmangle(uniqueFolderId);
        if (null == unmangled || 3 > unmangled.size() || false == CAL_PREFIX.equals(unmangled.get(0))) {
            throw new IllegalArgumentException(uniqueFolderId);
        }
        return unmangled;
    }

}
