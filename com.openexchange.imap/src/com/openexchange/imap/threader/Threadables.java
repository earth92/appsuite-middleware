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

package com.openexchange.imap.threader;

import static com.openexchange.imap.command.MailMessageFetchIMAPCommand.getFetchCommand;
import static com.openexchange.imap.command.MailMessageFetchIMAPCommand.handleFetchRespone;
import static com.openexchange.imap.util.ImapUtility.prepareImapCommandForLogging;
import static com.openexchange.mail.MailServletInterface.mailInterfaceMonitor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.mail.FetchProfile;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import com.google.common.collect.ImmutableMap;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.exception.OXException;
import com.openexchange.imap.IMAPMessageStorage;
import com.openexchange.imap.IMAPServerInfo;
import com.openexchange.imap.config.IMAPReloadable;
import com.openexchange.imap.services.Services;
import com.openexchange.imap.threader.nntp.ThreadableImpl;
import com.openexchange.imap.threadsort.ThreadSortNode;
import com.openexchange.java.Reference;
import com.openexchange.log.LogProperties;
import com.openexchange.mail.PreviewMode;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.session.Session;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.iap.ResponseInterceptor;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.protocol.BODY;
import com.sun.mail.imap.protocol.ENVELOPE;
import com.sun.mail.imap.protocol.FetchResponse;
import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.imap.protocol.Item;
import com.sun.mail.imap.protocol.RFC822DATA;
import com.sun.mail.imap.protocol.UID;
import gnu.trove.set.TIntSet;

/**
 * {@link Threadables} - Utility class for {@code Threadable}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class Threadables {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Threadables.class);

    /**
     * Initializes a new {@link Threadables}.
     */
    private Threadables() {
        super();
    }

    private static volatile Boolean useCommonsNetThreader;

    /**
     * Whether to use {@code org.apache.commons.net.nntp.Threader} instead of {@code Threader}.
     *
     * @return <code>true</code> to use Commons Net threader; otherwise <code>false</code>
     */
    public static boolean useCommonsNetThreader() {
        Boolean b = useCommonsNetThreader;
        if (null == b) {
            synchronized (IMAPMessageStorage.class) {
                b = useCommonsNetThreader;
                if (null == b) {
                    final ConfigurationService service = Services.getService(ConfigurationService.class);
                    b = Boolean.valueOf(null != service && service.getBoolProperty("com.openexchange.imap.useCommonsNetThreader", false));
                    useCommonsNetThreader = b;
                }
            }
        }
        return b.booleanValue();
    }

    static {
        IMAPReloadable.getInstance().addReloadable(new Reloadable() {

            @SuppressWarnings("synthetic-access")
            @Override
            public void reloadConfiguration(ConfigurationService configService) {
                useCommonsNetThreader = null;
            }

            @Override
            public Interests getInterests() {
                return Reloadables.interestsForProperties("com.openexchange.imap.useCommonsNetThreader");
            }
        });
    }

    /**
     * Performs threading algorithm on specified {@code Threadable} instance.
     *
     * @param threadable
     * @return
     */
    public static Threadable applyThreaderTo(Threadable threadable) {
        if (useCommonsNetThreader()) {
            ThreadableImpl threadableImpl = ((ThreadableImpl) new org.apache.commons.net.nntp.Threader().thread(new ThreadableImpl(threadable)));
            if (threadableImpl != null) {
                return threadableImpl.getDelegatee();
            }
            LOG.warn("Unable to use apache commom net threader. Falling back to default threader.");
        }
        return new Threader().thread(threadable);
    }

    /**
     * Simple container class.
     */
    public static final class ThreadableResult {

        /** The associated {@code Threadable} */
        public final Threadable threadable;
        /** The cached flag */
        public final boolean cached;

        protected ThreadableResult(Threadable threadable, boolean cached) {
            super();
            this.threadable = threadable;
            this.cached = cached;
        }
    }

    /**
     * Gets the <tt>Threadable</tt> with cache look-up.
     *
     * @param imapFolder The IMAP folder
     * @param sorted Whether the returned <tt>Threadable</tt> is supposed to be thread-sorted
     * @param cache <code>true</code> to immediately return a possibly cached element; otherwise <code>false</code>
     * @param lookAhead The max. number of messages
     * @param accountId The account identifier
     * @param session The associated user session
     * @return The <tt>Threadable</tt> either from cache or newly generated
     * @throws MessagingException If <tt>Threadable</tt> cannot be returned for any reason
     */
    public static ThreadableResult getThreadableFor(IMAPFolder imapFolder, boolean sorted, boolean cache, int lookAhead, int accountId, Session session) throws MessagingException {
        Threadable threadable = getAllThreadablesFrom(imapFolder, lookAhead);
        if (sorted) {
            if (useCommonsNetThreader()) {
                ThreadableImpl threadableImpl = ((ThreadableImpl) new org.apache.commons.net.nntp.Threader().thread(new ThreadableImpl(threadable)));
                if (threadableImpl != null) {
                    threadable = threadableImpl.getDelegatee();
                }
            } else {
                threadable = new Threader().thread(threadable);
            }
        }
        return new ThreadableResult(threadable, false);
    }

    private static interface HeaderHandler {

        void handle(Header hdr, Threadable threadable) throws MessagingException;

    }

    private static final Map<String, HeaderHandler> HANDLERS;
    static {
        final Map<String, HeaderHandler> m = new HashMap<String, HeaderHandler>(4);
        m.put(MessageHeaders.HDR_SUBJECT, new HeaderHandler() {

            @Override
            public void handle(Header hdr, Threadable threadable) throws MessagingException {
                threadable.subject = MimeMessageUtility.decodeMultiEncodedHeader(MimeMessageUtility.checkNonAscii(hdr.getValue()));
            }
        });
        m.put(MessageHeaders.HDR_REFERENCES, new HeaderHandler() {

            private final Pattern split = Pattern.compile(" +");

            @Override
            public void handle(Header hdr, Threadable threadable) throws MessagingException {
                threadable.refs = split.split(MimeMessageUtility.decodeMultiEncodedHeader(hdr.getValue()));
            }
        });
        m.put(MessageHeaders.HDR_MESSAGE_ID, new HeaderHandler() {

            @Override
            public void handle(Header hdr, Threadable threadable) throws MessagingException {
                threadable.messageId = MimeMessageUtility.decodeMultiEncodedHeader(hdr.getValue());
            }
        });
        m.put(MessageHeaders.HDR_IN_REPLY_TO, new HeaderHandler() {

            @Override
            public void handle(Header hdr, Threadable threadable) throws MessagingException {
                threadable.inReplyTo = MimeMessageUtility.decodeMultiEncodedHeader(hdr.getValue());
            }
        });
        HANDLERS = ImmutableMap.copyOf(m);
    }

    /**
     * Gets the <tt>MailMessage</tt>s for given IMAP folder.
     *
     * @param imapFolder The IMAP folders
     * @param limit The max. number of messages or <code>-1</code>
     * @param fetchProfile The FETCH profile
     * @param serverInfo The IMAP server information
     * @param examineHasAttachmentUserFlags Whether has-attachment user flags should be considered
     * @param previewMode Whether target IMAP server supports any preview capability
     * @return The fetched <tt>MailMessage</tt>s
     * @throws MessagingException If an error occurs
     */
    @SuppressWarnings("unchecked")
    public static List<MailMessage> getAllMailsFrom(IMAPFolder imapFolder, int limit, FetchProfile fetchProfile, IMAPServerInfo serverInfo, boolean examineHasAttachmentUserFlags, PreviewMode previewMode) throws MessagingException {
        final int messageCount = imapFolder.getMessageCount();
        if (messageCount <= 0) {
            /*
             * Empty folder...
             */
            return Collections.emptyList();
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
                        if (limit < 0 || limit >= messageCount) {
                            sb.append("1:*");
                            mails = new ArrayList<MailMessage>(messageCount);
                        } else {
                            sb.append(messageCount - limit + 1).append(':').append('*');
                            mails = new ArrayList<MailMessage>(limit);
                        }
                    }
                    sb.append(" (").append(getFetchCommand(protocol.isREV1(), fetchProfile, false, serverInfo, previewMode)).append(')');
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
                                    String inReplyTo = message.getFirstHeader(sInReplyTo);
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
     * Gets the <tt>Threadable</tt>s for given IMAP folder.
     *
     * @param imapFolder The IMAP folders
     * @param lookAhead The max. number of messages or <code>-1</code>
     * @return The fetched <tt>Threadable</tt>s
     * @throws MessagingException If an error occurs
     */
    public static Threadable getAllThreadablesFrom(IMAPFolder imapFolder, int lookAhead) throws MessagingException {
        return getAllThreadablesFrom(imapFolder, lookAhead, false);
    }

    /**
     * Whether to include <tt>"References"</tt> header.
     *
     * @return <code>true</code> to include "References" header; else <code>false</code>
     */
    static boolean includeReferences() {
        return true;
    }

    /**
     * Gets the <tt>Threadable</tt>s for given IMAP folder.
     *
     * @param imapFolder The IMAP folders
     * @param limit The max. number of messages or <code>-1</code>
     * @param fetchSingleFields <code>true</code> to fetch single fields; otherwise <code>false</code> for complete headers
     * @return The fetched <tt>Threadable</tt>s
     * @throws MessagingException If an error occurs
     */
    public static Threadable getAllThreadablesFrom(IMAPFolder imapFolder, int limit, boolean fetchSingleFields) throws MessagingException {
        final int messageCount = imapFolder.getMessageCount();
        if (messageCount <= 0) {
            /*
             * Empty folder...
             */
            return null;
        }
        final org.slf4j.Logger log = LOG;
        final Map<String, HeaderHandler> handlers = HANDLERS;
        return (Threadable) (imapFolder.doCommand(new IMAPFolder.ProtocolCommand() {

            @Override
            public Object doCommand(IMAPProtocol protocol) throws ProtocolException {
                final List<Threadable> threadables;
                final String command;
                {
                    StringBuilder sb = new StringBuilder(128).append("FETCH ");
                    if (1 == messageCount) {
                        sb.append("1");
                        threadables = new ArrayList<>(1);
                    } else {
                        if (limit < 0 || limit >= messageCount) {
                            sb.append("1:*");
                            threadables = new ArrayList<>(messageCount);
                        } else {
                            sb.append(messageCount - limit + 1).append(':').append(messageCount);
                            threadables = new ArrayList<>(limit);
                        }
                    }
                    sb.append(" (");
                    final boolean rev1 = protocol.isREV1();
                    if (fetchSingleFields) {
                        if (rev1) {
                            sb.append("UID BODY.PEEK[HEADER.FIELDS (Subject Message-Id References In-Reply-To)]");
                        } else {
                            sb.append("UID RFC822.HEADER.LINES (Subject Message-Id References In-Reply-To)");
                        }
                    } else {
                        sb.append("UID ENVELOPE");
                        if (includeReferences()) {
                            if (rev1) {
                                sb.append(" BODY.PEEK[HEADER.FIELDS (References)]");
                            } else {
                                sb.append(" RFC822.HEADER.LINES (References)");
                            }
                        }
                    }
                    sb.append(')');
                    command = sb.toString();
                }

                String fullName = imapFolder.getFullName();
                final Reference<ProtocolException> exceptionRef = new Reference<>(null);
                ResponseInterceptor interceptor = new ResponseInterceptor() {

                    @Override
                    public boolean intercept(Response r) {
                        if (!(r instanceof FetchResponse)) {
                            return false;
                        }

                        if (exceptionRef.hasNoValue()) {
                            try {
                                if (fetchSingleFields) {
                                    handleByFields(handlers, threadables, fullName, (FetchResponse) r);
                                } else {
                                    boolean includeReferences = includeReferences();
                                    String sReferences = MessageHeaders.HDR_REFERENCES;
                                    HeaderHandler refsHeaderHandler = handlers.get(sReferences);
                                    handleByEnvelope(threadables, fullName, includeReferences, sReferences, refsHeaderHandler, (FetchResponse) r);
                                }
                            } catch (MessagingException e) {
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

                // Return
                Threadable first = threadables.remove(0);
                {
                    Threadable cur = first;
                    for (Threadable threadable : threadables) {
                        cur.next = threadable;
                        cur = threadable;
                    }
                }
                return first;
            }

            void handleByFields(Map<String, HeaderHandler> handlers, List<Threadable> threadables, String fullName, FetchResponse fetchResponse) throws MessagingException {
                // Check for BODY / RFC822DATA
                final InternetHeaders h;
                {
                    final InputStream headerStream;
                    final BODY body = getItemOf(BODY.class, fetchResponse);
                    if (null == body) {
                        final RFC822DATA rfc822data = getItemOf(RFC822DATA.class, fetchResponse);
                        headerStream = null == rfc822data ? null : rfc822data.getByteArrayInputStream();
                    } else {
                        headerStream = body.getByteArrayInputStream();
                    }
                    if (null == headerStream) {
                        h = null;
                    } else {
                        h = new InternetHeaders();
                        h.load(headerStream);
                    }
                }
                final Threadable t;
                if (h == null) {
                    t = null;
                } else {
                    t = new Threadable().setFullName(fullName);
                    t.messageNumber = fetchResponse.getNumber();
                    for (Enumeration<?> e = h.getAllHeaders(); e.hasMoreElements();) {
                        final Header hdr = (Header) e.nextElement();
                        final HeaderHandler headerHandler = handlers.get(hdr.getName());
                        if (null != headerHandler) {
                            headerHandler.handle(hdr, t);
                        }
                    }
                    // Check for UID
                    final UID uid = getItemOf(UID.class, fetchResponse);
                    if (null != uid) {
                        t.uid = uid.uid;
                    }
                }
                if (null != t) {
                    add2List(t, threadables);
                }
            }

            void handleByEnvelope(List<Threadable> threadables, String fullName, boolean includeReferences, String sReferences, HeaderHandler refsHeaderHandler, FetchResponse fetchResponse) throws MessagingException {
                final ENVELOPE envelope = getItemOf(ENVELOPE.class, fetchResponse);
                final Threadable t;
                if (null == envelope) {
                    t = null;
                } else {
                    t = new Threadable().setFullName(fullName);
                    t.messageNumber = fetchResponse.getNumber();
                    t.subject = MimeMessageUtility.decodeEnvelopeSubject(envelope.subject);
                    t.messageId = envelope.messageId;
                    t.inReplyTo = envelope.inReplyTo;
                    // Check for UID
                    final UID uid = getItemOf(UID.class, fetchResponse);
                    if (null != uid) {
                        t.uid = uid.uid;
                    }
                    if (includeReferences) {
                        InputStream headerStream;
                        BODY body = getItemOf(BODY.class, fetchResponse);
                        if (null == body) {
                            final RFC822DATA rfc822data = getItemOf(RFC822DATA.class, fetchResponse);
                            headerStream = null == rfc822data ? null : rfc822data.getByteArrayInputStream();
                        } else {
                            headerStream = body.getByteArrayInputStream();
                        }
                        body = null;
                        if (null != headerStream) {
                            final InternetHeaders h = new InternetHeaders();
                            h.load(headerStream);
                            headerStream = null;
                            final String refs = h.getHeader(sReferences, null);
                            if (null != refs && null != refsHeaderHandler) {
                                refsHeaderHandler.handle(new Header(sReferences, refs), t);
                            }
                        }
                    }
                }
                if (null != t) {
                    add2List(t, threadables);
                }
            }

            private void add2List(Threadable t, List<Threadable> threadables) {
                // Check References and In-Reply-To
                if (null != t.inReplyTo) {
                    if (null == t.refs) {
                        t.refs = new String[] { t.inReplyTo };
                    } else {
                        final String[] tmp = t.refs;
                        t.refs = new String[tmp.length + 1];
                        System.arraycopy(tmp, 0, t.refs, 0, tmp.length);
                        t.refs[tmp.length] = t.inReplyTo;
                    }
                }
                threadables.add(t);
            }

        }));
    }

    /**
     * Gets the item associated with given class in specified <i>FETCH</i> response.
     *
     * @param <I> The returned item's class
     * @param clazz The item class to look for
     * @param fetchResponse The <i>FETCH</i> response
     * @return The item associated with given class in specified <i>FETCH</i> response or <code>null</code>.
     * @see #getItemOf(Class, FetchResponse, String)
     */
    protected static <I extends Item> I getItemOf(Class<? extends I> clazz, FetchResponse fetchResponse) {
        final int len = fetchResponse.getItemCount();
        for (int i = 0; i < len; i++) {
            final Item item = fetchResponse.getItem(i);
            if (clazz.isInstance(item)) {
                return clazz.cast(item);
            }
        }
        return null;
    }

    /**
     * Converts passed {@link Threadable} to an IMAP-conform THREAD=REFERENCES result string; <br>
     * e.g:&nbsp;<code>"((1)(2)(3)(4)(5)(6)(7)(8)(9)(10)(11)(12)(13))"</code>.
     *
     * @param threadable The instance
     * @param filter The filter
     * @return The resulting THREAD=REFERENCES string
     */
    public static String toThreadReferences(Threadable threadable, TIntSet filter) {
        final StringBuilder sb = new StringBuilder(256);
        toThreadReferences0(threadable, filter, sb);
        return sb.toString();
    }

    private static void toThreadReferences0(Threadable threadable, TIntSet filter, StringBuilder sb) {
        Threadable t = threadable;
        if (null == filter) {
            while (null != t) {
                sb.append('(');
                if (t.messageNumber > 0) {
                    sb.append(t.toMessageInfo());
                }
                final Threadable kid = t.kid;
                if (null != kid) {
                    if (t.messageNumber > 0) {
                        sb.append(' ');
                    }
                    toThreadReferences0(kid, null, sb);
                }
                final int lastPos = sb.length() - 1;
                if ('(' == sb.charAt(lastPos)) {
                    sb.deleteCharAt(lastPos);
                } else {
                    sb.append(')');
                }
                t = t.next;
            }
        } else {
            while (null != t) {
                if (filter.contains(t.messageNumber)) {
                    sb.append('(');
                    if (t.messageNumber > 0) {
                        sb.append(t.toMessageInfo());
                    }
                    final Threadable kid = t.kid;
                    if (null != kid) {
                        if (t.messageNumber > 0) {
                            sb.append(' ');
                        }
                        toThreadReferences0(kid, null, sb);
                    }
                    final int lastPos = sb.length() - 1;
                    if ('(' == sb.charAt(lastPos)) {
                        sb.deleteCharAt(lastPos);
                    } else {
                        sb.append(')');
                    }
                } else {
                    final Threadable kid = t.kid;
                    if (null != kid) {
                        toThreadReferences0(kid, filter, sb);
                    }
                }
                t = t.next;
            }
        }
    }

    /**
     * Appends the latter <tt>Threadable</tt> to the first <tt>Threadable</tt> instance.
     *
     * @param threadable The <tt>Threadable</tt> instance
     * @param toAppend The <tt>Threadable</tt> instance to append
     */
    public static void append(Threadable threadable, Threadable toAppend) {
        if (null == threadable) {
            return;
        }
        Threadable t = threadable;
        while (null != t.next) {
            t = t.next;
        }
        t.next = toAppend;
    }

    /**
     * Transforms <tt>Threadable</tt> to list of <tt>ThreadSortNode</tt>s.
     *
     * @param t The <tt>Threadable</tt> to transform
     * @return The resulting list of <tt>ThreadSortNode</tt>s
     */
    public static List<ThreadSortNode> toNodeList(Threadable t) {
        if (null == t) {
            return Collections.emptyList();
        }
        final List<ThreadSortNode> list = new LinkedList<ThreadSortNode>();
        fillInList(t, list);
        return list;
    }

    private static void fillInList(Threadable t, List<ThreadSortNode> list) {
        Threadable cur = t;
        while (null != cur) {
            if (cur.isDummy()) {
                fillInList(cur.kid, list);
            } else {
                final ThreadSortNode node = new ThreadSortNode(cur.toMessageInfo(), cur.uid);
                list.add(node);
                // Check kids
                final Threadable kid = cur.kid;
                if (null != kid) {
                    final List<ThreadSortNode> sublist = new LinkedList<ThreadSortNode>();
                    fillInList(kid, sublist);
                    node.addChildren(sublist);
                }
            }
            // Proceed to next
            cur = cur.next;
        }
    }

    /**
     * Filters from <tt>Threadable</tt> those sub-trees which solely consist of specified <tt>Threadable</tt>s associated with given full
     * name
     *
     * @param fullName The full name to filter with
     * @param t The <tt>Threadable</tt> instance
     */
    public static Threadable filterFullName(String fullName, Threadable t) {
        Threadable first = t;
        Threadable prev = null;
        Threadable cur = t;
        while (null != cur) {
            if (checkFullName(fullName, cur)) {
                final Threadable c = cur;
                cur = cur.next;
                if (null == prev) { // First one needs to be removed
                    first = cur;
                } else { // re-point
                    prev.next = cur;
                }
                c.next = null;
            } else {
                prev = cur;
                cur = cur.next;
            }
        }
        return first;

        // final List<Threadable> list = unfold(t);
        // if (list.isEmpty()) {
        // return t;
        // }
        // // Filter
        // for (Iterator<Threadable> iterator = list.iterator(); iterator.hasNext();) {
        // final Threadable cur = iterator.next();
        // if (checkFullName(fullName, cur)) {
        // iterator.remove();
        // }
        // }
        // // Fold
        // return fold(list);
    }

    /**
     * Unfolds specified <tt>Threadable</tt>.
     *
     * @param t The <tt>Threadable</tt> to unfold
     * @return The resulting list
     */
    public static List<Threadable> unfold(Threadable t) {
        final List<Threadable> list = new LinkedList<Threadable>();
        Threadable cur = t;
        while (null != cur) {
            list.add(cur);
            cur = cur.next;
        }
        return list;
    }

    /**
     * Folds specified list to returned <tt>Threadable</tt>.
     *
     * @param list The list to fold
     * @return The folded <tt>Threadable</tt> instance
     */
    public static Threadable fold(List<Threadable> list) {
        if (null == list) {
            return null;
        }
        final Threadable first = list.remove(0);

        Threadable cur = first;
        for (Threadable threadable : list) {
            cur.next = threadable;
            cur = threadable;
        }

        return first;
    }

    private static boolean checkFullName(String fullName, Threadable t) {
        Threadable cur = t;
        while (null != cur) {
            if (cur.messageNumber > 0 && !fullName.equals(cur.fullName)) {
                return false;
            }
            final Threadable kid = cur.kid;
            if (null != kid) {
                if (!checkFullName(fullName, kid)) {
                    return false;
                }
            }
            cur = cur.next;
        }
        // Solely consists of threadables associated with given full name
        return true;
    }

}
