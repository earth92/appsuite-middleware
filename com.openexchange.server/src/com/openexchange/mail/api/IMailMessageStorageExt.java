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

package com.openexchange.mail.api;

import com.openexchange.exception.OXException;
import com.openexchange.mail.IndexRange;
import com.openexchange.mail.MailField;
import com.openexchange.mail.MailSortField;
import com.openexchange.mail.OrderDirection;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.search.SearchTerm;

/**
 * {@link IMailMessageStorageExt} - Extends {@link IMailMessageStorage} for mail systems which support to request single header names.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public interface IMailMessageStorageExt extends IMailMessageStorage {

    /**
     * Clears message-related cache.
     *
     * @throws OXException If operation fails
     */
    public void clearCache() throws OXException;

    /**
     * Gets the mails located in given folder whose mail ID matches specified ID. The constant {@link #EMPTY_RETVAL} may be returned, if
     * folder contains no messages.
     * <p>
     * The returned instances of {@link MailMessage} are pre-filled with specified fields through argument <code>fields</code>.
     * <p>
     * If any mail ID is invalid, <code>null</code> is returned for that entry.
     *
     * @param fullName The folder full name
     * @param mailIds The mail IDs
     * @param fields The fields to pre-fill in returned instances of {@link MailMessage}
     * @param headerNames The header names to pre-fill in returned instances of {@link MailMessage}
     * @return Corresponding mails as an array
     * @throws OXException If message could not be returned
     */
    public MailMessage[] getMessages(String fullName, String[] mailIds, MailField[] fields, String[] headerNames) throws OXException;

    /**
     * Gets the identifiers if the mails whose <i>"Message-ID"</i> header is contained in specified list.
     * <p>
     * If any <i>"Message-ID"</i> header cannot be found, <code>null</code> is located at associated index position in returned array.
     *
     * @param messageIDs The <i>"Message-ID"</i> header list
     * @return An array providing the looked-up mails with ID and folder set
     * @throws OXException If mail identifiers could not be returned
     */
    public MailMessage[] getMessagesByMessageID(String... messageIDs) throws OXException;

    /**
     * Gets the identifiers if the mails whose <i>"Message-ID"</i> header is contained in specified list.
     * <p>
     * If any <i>"Message-ID"</i> header cannot be found, <code>null</code> is located at associated index position in returned array.
     *
     * @param fullName The folder full name
     * @param messageIDs The <i>"Message-ID"</i> header list
     * @return An array providing the looked-up mails with ID and folder set
     * @throws OXException If mail identifiers could not be returned
     */
    public MailMessage[] getMessagesByMessageIDByFolder(String fullName, String... messageIDs) throws OXException;

    /**
     * Searches mails located in given folder. If the search yields no results, the constant {@link #EMPTY_RETVAL} may be returned. This
     * method's purpose is to return filtered mails' headers for a <b>fast</b> list view. Therefore this method's <code>fields</code>
     * parameter should only contain instances of {@link MailField} which are marked as <b>[low cost]</b>. Otherwise pre-filling of returned
     * messages may take a long time and does no more fit to generate a fast list view.
     * <p>
     * <b>Note</b> that sorting needs not to be supported by underlying mailing system. This can be done on application side, too.<br>
     * Same is for search, but in most cases it's faster to search on mailing system, but this heavily depends on how mails are accessed.
     *
     * @param fullName The folder full name
     * @param indexRange The index range specifying the desired sub-list in sorted list; may be <code>null</code> to obtain complete list.
     *            Range begins at the specified start index and extends to the message at index <code>end - 1</code>. Thus the length of the
     *            range is <code>end - start</code>.
     * @param sortField The sort field
     * @param order Whether ascending or descending sort order
     * @param searchTerm The search term to filter messages; may be <code>null</code> to obtain all messages
     * @param fields The fields to pre-fill in returned instances of {@link MailMessage}
     * @param headerNames The header names to pre-fill in returned instances of {@link MailMessage}
     * @return The desired, pre-filled instances of {@link MailMessage}
     * @throws OXException If mails cannot be returned
     */
    public MailMessage[] searchMessages(String fullName, IndexRange indexRange, MailSortField sortField, OrderDirection order, SearchTerm<?> searchTerm, MailField[] fields, String[] headerNames) throws OXException;

}
