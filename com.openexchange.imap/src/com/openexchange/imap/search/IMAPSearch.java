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

package com.openexchange.imap.search;

import javax.mail.FetchProfile;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.StoreClosedException;
import javax.mail.search.SearchException;
import javax.mail.search.SearchTerm;
import com.openexchange.exception.OXException;
import com.openexchange.imap.IMAPFolderWorker;
import com.openexchange.imap.config.IMAPConfig;
import com.openexchange.imap.config.IMAPProperties;
import com.openexchange.mail.MailField;
import com.openexchange.mail.MailFields;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.session.Session;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.IMAPProtocol;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * {@link IMAPSearch}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class IMAPSearch {

    /**
     * No instantiation
     */
    private IMAPSearch() {
        super();
    }

    /**
     * Searches messages in given IMAP folder
     *
     * @param imapFolder The IMAP folder
     * @param searchTerm The search term
     * @return Filtered messages' sequence numbers according to search term
     * @throws MessagingException If a messaging error occurs
     * @throws OXException If a searching fails
     */
    public static int[] searchMessages(IMAPFolder imapFolder, com.openexchange.mail.search.SearchTerm<?> searchTerm, IMAPConfig imapConfig, Session session) throws MessagingException, OXException {
        int msgCount = imapFolder.getMessageCount();
        if (msgCount <= 0) {
            return new int[0];
        }

        MailFields mailFields = new MailFields(MailField.getMailFieldsFromSearchTerm(searchTerm));
        if (mailFields.contains(MailField.BODY) || mailFields.contains(MailField.FULL)) {
            if (imapConfig.forceImapSearch() || (msgCount >= imapConfig.getIMAPProperties().getMailFetchLimit())) {
                // Too many messages in IMAP folder or IMAP-based search should be forced.
                // Fall-back to IMAP-based search and accept a non-type-sensitive search.
                int[] seqNums = issueIMAPSearch(imapFolder, searchTerm, session.getUserId(), session.getContextId());
                if (null != seqNums) {
                    return seqNums;
                }
            }

            // In-application search needed in case of body search since IMAP's SEARCH command does not perform type-sensitive search;
            // e.g. extract plain-text out of HTML content.
            return searchByTerm(imapFolder, searchTerm, 100, msgCount);
        }

        // Perform an IMAP-based search if IMAP search is forces through configuration or is enabled and number of messages exceeds limit.
        if (imapConfig.isImapSearch() || (msgCount >= MailProperties.getInstance().getMailFetchLimit())) {
            int[] seqNums = issueIMAPSearch(imapFolder, searchTerm, session.getUserId(), session.getContextId());
            if (null != seqNums) {
                return seqNums;
            }
        }

        // Search in application
        return searchByTerm(imapFolder, searchTerm, -1, msgCount);
    }

    /**
     * Search in given IMAP folder using specified search term
     *
     * @param imapFolder The IMAP folder to search in
     * @param searchTerm The search term to fulfill
     * @param chunkSize The chunk size or <code>-1</code> to fetch all messages at once
     * @param msgCount The total message count in IMAP folder
     * @return The sequence numbers of matching messages
     * @throws MessagingException If a messaging error occurs
     * @throws OXException If an Open-Xchange error occurs
     */
    public static int[] searchByTerm(IMAPFolder imapFolder, com.openexchange.mail.search.SearchTerm<?> searchTerm, int chunkSize, int msgCount) throws MessagingException, OXException {
        TIntList list = new TIntArrayList(msgCount);
        FetchProfile fp = new FetchProfile();
        searchTerm.contributeTo(fp);
        if (chunkSize <= 0 || msgCount <= chunkSize) {
            Message[] allMsgs = imapFolder.getMessages();
            imapFolder.fetch(allMsgs, fp);
            for (int i = 0; i < allMsgs.length; i++) {
                Message msg = allMsgs[i];
                if (searchTerm.matches(msg)) {
                    list.add(msg.getMessageNumber());
                }
            }
            IMAPFolderWorker.clearCache(imapFolder);
        } else {
            // Chunk-wise retrieval
            int start = 1;
            while (start < msgCount) {
                int end = start + chunkSize - 1;
                if (end > msgCount) {
                    end = msgCount;
                }

                Message[] msgs = imapFolder.getMessages(start, end);
                imapFolder.fetch(msgs, fp);
                for (int i = 0; i < msgs.length; i++) {
                    Message msg = msgs[i];
                    if (searchTerm.matches(msg)) {
                        list.add(msg.getMessageNumber());
                    }
                }

                IMAPFolderWorker.clearCache(imapFolder);
                start = end + 1;
            }
        }
        return list.toArray();
    }

    public static int umlautFilterThreshold(int userId, int contextId) {
        return IMAPProperties.getInstance().getUmlautFilterThreshold(userId, contextId);
    }

    public static int[] issueIMAPSearch(IMAPFolder imapFolder, com.openexchange.mail.search.SearchTerm<?> searchTerm, int userId, int contextId) throws OXException, MessagingException {
        try {
            if (searchTerm.containsWildcard()) {
                /*
                 * Try to pre-select with non-wildcard part
                 */
                return issueNonWildcardSearch(searchTerm.getNonWildcardJavaMailSearchTerm(), imapFolder);
            }
            final int[] seqNums = issueNonWildcardSearch(searchTerm.getJavaMailSearchTerm(), imapFolder);
            final int umlautFilterThreshold = umlautFilterThreshold(userId, contextId);
            if ((umlautFilterThreshold > 0) && (seqNums.length <= umlautFilterThreshold) && !searchTerm.isAscii()) {
                /*
                 * Search with respect to umlauts in pre-selected messages
                 */
                return searchWithUmlautSupport(searchTerm, seqNums, imapFolder);
            }
            return seqNums;
        } catch (FolderClosedException e) {
            /*
             * Caused by a protocol error such as a socket error. No retry in this case.
             */
            throw e;
        } catch (StoreClosedException e) {
            /*
             * Caused by a protocol error such as a socket error. No retry in this case.
             */
            throw e;
        } catch (MessagingException e) {
            final Exception nextException = e.getNextException();
            if (nextException instanceof ProtocolException) {
                final ProtocolException protocolException = (ProtocolException) nextException;
                final Response response = protocolException.getResponse();
                if (response != null && response.isBYE()) {
                    /*
                     * The BYE response is always untagged, and indicates that the server is about to close the connection.
                     */
                    throw new StoreClosedException(imapFolder.getStore(), protocolException.getMessage());
                }
                final Throwable cause = protocolException.getCause();
                if (cause instanceof StoreClosedException) {
                    /*
                     * Connection is down. No retry.
                     */
                    throw ((StoreClosedException) cause);
                } else if (cause instanceof FolderClosedException) {
                    /*
                     * Connection is down. No retry.
                     */
                    throw ((FolderClosedException) cause);
                }
            }

            throw e;
        }
    }

    /**
     * Executes {@link IMAPFolder#search(SearchTerm)} with specified search term passed to invocation.
     * <p>
     * The search term is considered to not contain any wildcard characters, but may contain non-ascii characters since IMAP search is
     * capable to deal with non-ascii characters through specifying a proper charset like UTF-8.
     *
     * @param term The search term to pass
     * @param imapFolder The IMAP folder to search in
     * @return The matching messages as an array
     * @throws MessagingException If a messaging error occurs
     */
    public static int[] issueNonWildcardSearch(SearchTerm term, IMAPFolder imapFolder) throws MessagingException {
        /*-
         * JavaMail already searches dependent on whether pattern contains non-ascii characters. If yes a charset is used:
         * SEARCH CHARSET UTF-8 <one or more search criteria>
         */
        return search(term, imapFolder);
    }

    /**
     * Searches with respect to umlauts
     */
    public static int[] searchWithUmlautSupport(com.openexchange.mail.search.SearchTerm<?> searchTerm, int[] seqNums, IMAPFolder imapFolder) throws OXException {
        try {
            IMAPFolderWorker.clearCache(imapFolder);

            TIntList results = new TIntArrayList(seqNums.length);
            Message[] messages = imapFolder.getMessages(seqNums);

            FetchProfile fp = new FetchProfile();
            searchTerm.contributeTo(fp);
            imapFolder.fetch(messages, fp);

            for (Message message : messages) {
                if (searchTerm.matches(message)) {
                    results.add(message.getMessageNumber());
                }
            }

            IMAPFolderWorker.clearCache(imapFolder);
            return results.toArray();
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        }
    }

    // --------------------------- IMAP commands ------------------------------

    /**
     * Searches in specified IMAP folder using given search term
     *
     * @param term The search term
     * @param imapFolder The IMAP folder to search in
     * @return The sequence number of matching messages
     * @throws MessagingException If a messaging error occurs
     */
    private static int[] search(SearchTerm term, IMAPFolder imapFolder) throws MessagingException {
        final int messageCount = imapFolder.getMessageCount();
        if (0 >= messageCount) {
            return new int[0];
        }

        final Object oSeqNums = imapFolder.doCommand(new IMAPFolder.ProtocolCommand() {
            @Override
            public Object doCommand(IMAPProtocol protocol) throws ProtocolException {
                try {
                    return protocol.search(term);
                } catch (SearchException e) {
                    throw new ProtocolException(e.getMessage(), e);
                }
            }
        });

        return oSeqNums instanceof TIntList ? ((TIntList) oSeqNums).toArray() : (int[]) oSeqNums;
    }

}
