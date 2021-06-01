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

package com.openexchange.imap.threader.references;

import static com.openexchange.imap.command.MailMessageFetchIMAPCommand.getFetchCommand;
import static com.openexchange.imap.command.MailMessageFetchIMAPCommand.handleFetchRespone;
import static com.openexchange.imap.util.ImapUtility.prepareImapCommandForLogging;
import static com.openexchange.mail.MailServletInterface.mailInterfaceMonitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.mail.FetchProfile;
import javax.mail.FetchProfile.Item;
import javax.mail.MessagingException;
import javax.mail.UIDFolder;
import com.openexchange.exception.OXException;
import com.openexchange.imap.IMAPServerInfo;
import com.openexchange.imap.command.MailMessageFetchIMAPCommand;
import com.openexchange.imap.threadsort.MessageInfo;
import com.openexchange.imap.threadsort.ThreadSortNode;
import com.openexchange.java.Reference;
import com.openexchange.log.LogProperties;
import com.openexchange.mail.MailField;
import com.openexchange.mail.OrderDirection;
import com.openexchange.mail.PreviewMode;
import com.openexchange.mail.dataobjects.IDMailMessage;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.utils.MimeStorageUtility;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.iap.ResponseInterceptor;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.FetchResponse;
import com.sun.mail.imap.protocol.IMAPProtocol;

/**
 * {@link Conversations} - Utility class.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class Conversations {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Conversations.class);

    /**
     * Initializes a new {@link Conversations}.
     */
    private Conversations() {
        super();
    }

    private static FetchProfile newFetchProfile(boolean byEnvelope) {
        FetchProfile fp = new FetchProfile();
        fp.add(UIDFolder.FetchProfileItem.UID);
        fp.add("References");

        if (byEnvelope) {
            fp.add(MailMessageFetchIMAPCommand.ENVELOPE_ONLY);
        } else {
            fp.add("Message-Id");
            fp.add("In-Reply-To");
        }
        return fp;
    }

    static final FetchProfile FETCH_PROFILE_CONVERSATION_BY_HEADERS = newFetchProfile(false);
    static final FetchProfile FETCH_PROFILE_CONVERSATION_BY_ENVELOPE = newFetchProfile(true);

    /**
     * Gets the <i>"by envelope"</i> fetch profile including specified fields.
     *
     * @param considerUserFlags Whether to consider user flags to determine "has attachment(s)" flag
     * @param previewMode Whether target IMAP server supports any preview capability
     * @param fields The fields to add
     * @return The <i>"by envelope"</i> fetch profile
     */
    public static FetchProfile getFetchProfileConversationByEnvelope(boolean considerUserFlags, PreviewMode previewMode, MailField... fields) {
        FetchProfile fp = newFetchProfile(true);
        if (null != fields) {
            for (MailField field : fields) {
                if (!MimeStorageUtility.isEnvelopeField(field)) {
                    MimeStorageUtility.addFetchItem(fp, field, considerUserFlags, previewMode);
                }
            }
        }
        return fp;
    }

    /**
     * Gets the <i>"by headers"</i> fetch profile including specified fields.
     *
     * @param considerUserFlags Whether to consider user flags to determine "has attachment(s)" flag
     * @param previewMode Whether target IMAP server supports any preview capability
     * @param fields The fields to add
     * @return The <i>"by headers"</i> fetch profile
     */
    public static FetchProfile getFetchProfileConversationByHeaders(boolean considerUserFlags, PreviewMode previewMode, MailField... fields) {
        FetchProfile fp = newFetchProfile(false);
        if (null != fields) {
            for (MailField field : fields) {
                if (MailField.RECEIVED_DATE.equals(field)) {
                    fp.add(MailMessageFetchIMAPCommand.INTERNALDATE);
                } else {
                    MimeStorageUtility.addFetchItem(fp, field, considerUserFlags, previewMode);
                }
            }
        }
        return fp;
    }

    /**
     * Checks specified {@link FetchProfile} to contain needed items/headers for building up conversations.
     *
     * @param fetchProfile The fetch profile to check
     * @param byEnvelope <code>true</code> to use ENVELOPE fetch item; other <code>false</code> to use single headers (<i>"References"</i>, <i>"In-Reply-To"</i>, and <i>"Message-Id"</i>)
     * @return The fetch profile ready for building up conversations
     */
    public static FetchProfile checkFetchProfile(FetchProfile fetchProfile, boolean byEnvelope) {
        // Add 'References' to FetchProfile if absent
        {
            String hdrReferences = MessageHeaders.HDR_REFERENCES;
            fetchProfile.add(hdrReferences);
        }
        // Add UID item to FetchProfile if absent
        {
            Item uid = UIDFolder.FetchProfileItem.UID;
            fetchProfile.add(uid);
        }
        // ------ Either by-envelope or by headers ------
        if (byEnvelope) {
            Item envelopeOnly = MailMessageFetchIMAPCommand.ENVELOPE_ONLY;
            if (false == fetchProfile.contains(FetchProfile.Item.ENVELOPE, envelopeOnly)) {
                fetchProfile.add(envelopeOnly);
            }
        } else {
            // Add 'Message-Id' and 'In-Reply-To' to FetchProfile if absent
            if (false == fetchProfile.contains(FetchProfile.Item.ENVELOPE, MailMessageFetchIMAPCommand.ENVELOPE_ONLY)) {
                fetchProfile.add(MessageHeaders.HDR_MESSAGE_ID);
                fetchProfile.add(MessageHeaders.HDR_IN_REPLY_TO);
            }
        }
        return fetchProfile;
    }

    /**
     * Retrieves <b><small>UNFOLDED</small></b> conversations for specified IMAP folder.
     *
     * @param imapFolder The IMAP folder
     * @param lookAhead The limit
     * @param order The order direction that controls which chunk (oldest vs. most recent) to select
     * @param fetchProfile The fetch profile
     * @param serverInfo The IMAP server information
     * @param byEnvelope Whether to build-up using ENVELOPE; otherwise <code>false</code>
     * @param examineHasAttachmentUserFlags Whether has-attachment user flags should be considered
     * @param previewMode Whether target IMAP server supports any preview capability
     * @return The unfolded conversations
     * @throws MessagingException If a messaging error occurs
     */
    public static List<Conversation> conversationsFor(IMAPFolder imapFolder, int lookAhead, OrderDirection order, FetchProfile fetchProfile, IMAPServerInfo serverInfo, boolean byEnvelope, boolean examineHasAttachmentUserFlags, PreviewMode previewMode) throws MessagingException {
        final List<MailMessage> messages = messagesFor(imapFolder, lookAhead, order, fetchProfile, serverInfo, byEnvelope, examineHasAttachmentUserFlags, previewMode);
        if (null == messages || messages.isEmpty()) {
            return Collections.<Conversation> emptyList();
        }
        final List<Conversation> conversations = new ArrayList<Conversation>(messages.size());
        for (MailMessage message : messages) {
            conversations.add(new Conversation(message));
        }
        return conversations;
    }

    /**
     * Retrieves messages for specified IMAP folder.
     *
     * @param imapFolder The IMAP folder
     * @param lookAhead The limit
     * @param order The order direction that controls which chunk (oldest vs. most recent) to select
     * @param fetchProfile The fetch profile
     * @param serverInfo The IMAP server information
     * @param byEnvelope Whether to build-up using ENVELOPE; otherwise <code>false</code>
     * @param examineHasAttachmentUserFlags Whether has-attachment user flags should be considered
     * @param previewMode Whether target IMAP server supports any preview capability
     * @return The messages with conversation information (References, In-Reply-To, Message-Id)
     * @throws MessagingException If a messaging error occurs
     */
    @SuppressWarnings("unchecked")
    public static List<MailMessage> messagesFor(IMAPFolder imapFolder, int lookAhead, OrderDirection order, FetchProfile fetchProfile, IMAPServerInfo serverInfo, boolean byEnvelope, boolean examineHasAttachmentUserFlags, PreviewMode previewMode) throws MessagingException {
        final int messageCount = imapFolder.getMessageCount();
        if (messageCount <= 0) {
            /*
             * Empty folder...
             */
            return Collections.<MailMessage> emptyList();
        }

        final org.slf4j.Logger log = LOG;
        return (List<MailMessage>) (imapFolder.doCommand(new IMAPFolder.ProtocolCommand() {

            @Override
            public Object doCommand(IMAPProtocol protocol) throws ProtocolException {
                // Compile command
                final List<MailMessage> mails;
                final String command;
                {
                    StringBuilder sb = new StringBuilder(128).append("FETCH ");
                    if (1 == messageCount) {
                        sb.append("1");
                        mails = new ArrayList<MailMessage>(1);
                    } else {
                        if (lookAhead < 0 || lookAhead >= messageCount) {
                            sb.append("1:*");
                            mails = new ArrayList<MailMessage>(messageCount);
                        } else {
                            if (OrderDirection.DESC.equals(order)) {
                                sb.append(messageCount - lookAhead + 1).append(':').append('*');
                            } else {
                                sb.append(1).append(':').append(lookAhead);
                            }
                            mails = new ArrayList<MailMessage>(lookAhead);
                        }
                    }
                    final FetchProfile fp = null == fetchProfile ? (byEnvelope ? FETCH_PROFILE_CONVERSATION_BY_ENVELOPE : FETCH_PROFILE_CONVERSATION_BY_HEADERS) : checkFetchProfile(fetchProfile, byEnvelope);
                    sb.append(" (").append(getFetchCommand(protocol.isREV1(), fp, false, serverInfo, previewMode)).append(')');
                    command = sb.toString();
                }

                // Response interceptor
                final String fullName = imapFolder.getFullName();
                final String sInReplyTo = "In-Reply-To";
                final String sReferences = "References";
                final Reference<ProtocolException> exceptionRef = new Reference<>(null);
                ResponseInterceptor interceptor = new ResponseInterceptor() {

                    @Override
                    public boolean intercept(Response r) {
                        if (!(r instanceof FetchResponse)) {
                            return false;
                        }

                        if (exceptionRef.hasNoValue()) {
                            try {
                                MailMessage message = handleFetchRespone((FetchResponse) r, fullName, serverInfo.getAccountId(), examineHasAttachmentUserFlags);
                                if (null == message.getFirstHeader(sReferences)) {
                                    // No "References" header
                                    final String inReplyTo = message.getFirstHeader(sInReplyTo);
                                    if (null != inReplyTo) {
                                        message.setHeader(sReferences, inReplyTo);
                                    }
                                }
                                mails.add(message);
                            } catch (MessagingException e) {
                                exceptionRef.setValue(new ProtocolException(e.getMessage(), e));
                            } catch (OXException e) {
                                exceptionRef.setValue(new ProtocolException(e.getMessage(), e));
                            }
                        }
                        return true;
                    }
                };

                // Execute command
                Response[] r;
                {
                    long start = System.currentTimeMillis();
                    r = protocol.command(command, null, Optional.of(interceptor));
                    final long dur = System.currentTimeMillis() - start;
                    log.debug("\"{}\" for \"{}\" ({}) took {}msec.", command, imapFolder.getFullName(), imapFolder.getStore(), Long.valueOf(dur));
                    mailInterfaceMonitor.addUseTime(dur);
                }

                // Check for exception during interception
                if (exceptionRef.hasValue()) {
                    throw exceptionRef.getValue();
                }

                // Handle remaining responses
                protocol.notifyResponseHandlers(r);

                // Handle status response
                LogProperties.putProperty(LogProperties.Name.MAIL_COMMAND, prepareImapCommandForLogging(command));
                LogProperties.putProperty(LogProperties.Name.MAIL_FULL_NAME, imapFolder.getFullName());
                protocol.handleResult(r[r.length - 1]);

                // Return mails
                return mails;
            }
        }));
    }

    /**
     * Transforms conversations to list of <tt>ThreadSortNode</tt>s.
     *
     * @param conversations The conversations to transform
     * @return The resulting list of <tt>ThreadSortNode</tt>s
     */
    public static List<ThreadSortNode> toNodeList(List<Conversation> conversations) {
        if (null == conversations) {
            return Collections.emptyList();
        }
        final List<ThreadSortNode> list = new ArrayList<ThreadSortNode>(conversations.size());
        for (Conversation conversation : conversations) {
            final List<MailMessage> messages = conversation.getMessages();
            final ThreadSortNode root = toThreadSortNode((IDMailMessage) messages.remove(0));
            root.addChildren(toThreadSortNodes(messages));
            list.add(root);
        }
        return list;
    }

    private static ThreadSortNode toThreadSortNode(IDMailMessage message) {
        return new ThreadSortNode(new MessageInfo(message.getSeqnum()).setFullName(message.getFolder()), message.getUid());
    }

    private static List<ThreadSortNode> toThreadSortNodes(List<MailMessage> messages) {
        final List<ThreadSortNode> ret = new ArrayList<ThreadSortNode>(messages.size());
        for (MailMessage message : messages) {
            ret.add(toThreadSortNode((IDMailMessage) message));
        }
        return ret;
    }

    /**
     * Folds specified conversations.
     *
     * @param toFold The conversations to fold
     * @return The folded conversations
     */
    public static List<Conversation> fold(List<Conversation> toFold) {
        fold(toFold, null);
        return toFold;
    }

    private static void fold(List<Conversation> toFold, Map<String, Conversation> lookupTable) {
        // first collect a temporal lookup table and fold for the first time
        {
            Map<String, Conversation> tempLookupTable = new HashMap<String, Conversation>(toFold.size());
            for (Iterator<Conversation> iter = toFold.iterator(); iter.hasNext();) {
                Conversation conversation = iter.next();

                boolean removed = false;
                for (String messageId : conversation.getMessageIds()) {
                    Conversation existing = tempLookupTable.putIfAbsent(messageId, conversation);
                    if (null != existing && !conversation.equals(existing)) {
                        if (!removed) {
                            iter.remove();
                            removed = true;
                        }
                        Conversation joined = existing.join(conversation);
                        if (!joined.equals(existing)) {
                            tempLookupTable.put(messageId, joined);
                        }
                    }
                }

                for (String reference : conversation.getReferences()) {
                    Conversation existing = tempLookupTable.putIfAbsent(reference, conversation);
                    if (null != existing && !conversation.equals(existing)) {
                        if (!removed) {
                            iter.remove();
                            removed = true;
                        }
                        Conversation joined = existing.join(conversation);
                        if (!joined.equals(existing)) {
                            tempLookupTable.put(reference, joined);
                        }
                    }
                }
            }
        }
        // iterate a second time and remove all that are not main Conversations
        {
            for (Iterator<Conversation> iter = toFold.iterator(); iter.hasNext();) {
                Conversation conversation = iter.next();
                if (!conversation.isMain()) {
                    iter.remove();
                }
            }
        }
        // now fill in the results if lookupTable is present
        if (null == lookupTable) {
            return;
        }
        {
            for (Iterator<Conversation> iter = toFold.iterator(); iter.hasNext();) {
                Conversation conversation = iter.next();
                for (String messageId : conversation.getMessageIds()) {
                    Conversation existing = lookupTable.putIfAbsent(messageId, conversation);
                    if (null != existing && !conversation.equals(existing)) {
                        LOG.debug("folding of Conversations failed for {} and {}", existing, conversation);
                    }
                }
                for (String reference : conversation.getReferences()) {
                    Conversation existing = lookupTable.putIfAbsent(reference, conversation);
                    if (null != existing && !conversation.equals(existing)) {
                        LOG.debug("folding of Conversations failed for {} and {}", existing, conversation);
                    }
                }
            }
        }
    }

    /**
     * Folds specified conversations and adds MaillMessages to the list.
     *
     * @param toFold The conversations to fold
     * @param toMergeWith The List of MaillMessages to be added
     * @return The folded conversations
     */
    public static List<Conversation> foldAndMergeWithList(List<Conversation> toFold, List<MailMessage> toMergeWith) {
        HashMap<String, Conversation> lookupTable = new HashMap<String, Conversation>(toFold.size() * 2);
        fold(toFold, lookupTable);
        for (MailMessage mailMessage : toMergeWith) {
            String messageId = mailMessage.getMessageId();
            if (null != messageId) {
                Conversation conversation = lookupTable.get(messageId);
                if (null != conversation) {
                    conversation.addMessage(mailMessage);
                }
            }

            String[] references = mailMessage.getReferences();
            if (null != references) {
                for (String reference : references) {
                    Conversation conversation = lookupTable.get(reference);
                    if (null != conversation) {
                        conversation.addMessage(mailMessage);
                    }
                }
            }

        }
        return toFold;
    }

}
