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

package com.openexchange.spamhandler.spamassassin;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Strings.asciiLowerCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.apache.spamassassin.spamc.Spamc;
import org.apache.spamassassin.spamc.Spamc.SpamdResponse;
import org.slf4j.Logger;
import com.openexchange.config.cascade.ComposedConfigProperty;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.exception.OXException;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.MailField;
import com.openexchange.mail.api.MailAccess;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.MimeTypes;
import com.openexchange.mail.mime.converters.DefaultConverterConfig;
import com.openexchange.mail.mime.converters.MimeMessageConverter;
import com.openexchange.mail.service.MailService;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.spamhandler.SpamHandler;
import com.openexchange.spamhandler.spamassassin.api.SpamdProvider;
import com.openexchange.spamhandler.spamassassin.api.SpamdService;
import com.openexchange.spamhandler.spamassassin.exceptions.SpamhandlerSpamassassinExceptionCode;

/**
 * {@link SpamAssassinSpamHandler} - The spam-assassin spam handler which expects spam mails being wrapped inside a mail created by
 * spam-assassin. Therefore handling a formerly spam mail as ham requires to extract the original mail.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
 */
public final class SpamAssassinSpamHandler extends SpamHandler {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SpamAssassinSpamHandler.class);

    private static class PlainAndNestedMessages {

        private final String[] nestedMessages;

        private final String[] plainMessages;

        public PlainAndNestedMessages(final String[] nestedMessages, final String[] plainMessages) {
            super();
            this.nestedMessages = nestedMessages;
            this.plainMessages = plainMessages;
        }


        public String[] getNestedMessages() {
            return nestedMessages;
        }


        public String[] getPlainMessages() {
            return plainMessages;
        }

    }

    private static class SpamdSettings {

        private final String hostname;

        private final int port;

        private final String username;

        public SpamdSettings(final String hostname, final int port, final String username) {
            this.hostname = hostname;
            this.port = port;
            this.username = username;
        }

        public String getHostname() {
            return hostname;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder(128);
            builder.append("[");
            if (hostname != null) {
                builder.append("hostname=").append(hostname).append(", ");
            }
            builder.append("port=").append(port).append(", ");
            if (username != null) {
                builder.append("username=").append(username);
            }
            builder.append("]");
            return builder.toString();
        }
    }

    private static class UnwrapParameter {

        private final MailAccess<?, ?> m_mailAccess;
        private final boolean m_move;
        private final String m_spamFullname;

        public UnwrapParameter(final String spamFullname, final boolean move, final MailAccess<?, ?> mailAccess) {
            m_spamFullname = spamFullname;
            m_move = move;
            m_mailAccess = mailAccess;
        }

        public MailAccess<?, ?> getMailAccess() {
            return m_mailAccess;
        }

        public String getSpamFullname() {
            return m_spamFullname;
        }

        public boolean isMove() {
            return m_move;
        }
    }

    private static final MailField[] FIELDS_HEADER_CT = { MailField.HEADERS, MailField.CONTENT_TYPE };

    private static final String NAME = "SpamAssassin";

    // ------------------------------------------------------------------------------------------------------------

    private final ServiceLookup services;

    /**
     * Initializes a new {@link SpamAssassinSpamHandler}.
     */
    public SpamAssassinSpamHandler(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public String getSpamHandlerName() {
        return NAME;
    }

    private <V> V getPropertyFor(Session session, String propertyName, V defaultValue, Class<V> clazz) throws OXException {
        ConfigViewFactory factory = services.getService(ConfigViewFactory.class);
        ConfigView view = factory.getView(session.getUserId(), session.getContextId());

        ComposedConfigProperty<V> property = view.property(propertyName, clazz);
        return (null != property && property.isDefined()) ? property.get() : defaultValue;
    }

    @Override
    public boolean isUnsubscribeSpamFolders(Session session) throws OXException {
        return getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.unsubscribeSpamFolders", Boolean.TRUE, Boolean.class).booleanValue();
    }

    @Override
    public void handleHam(final int accountId, final String spamFullName, final String[] mailIDs, final boolean move, final Session session) throws OXException {
        MailService mailService = services.getService(MailService.class);
        if (null == mailService) {
            throw SpamhandlerSpamassassinExceptionCode.MAILSERVICE_MISSING.create();
        }

        MailAccess<?, ?> mailAccess = null;
        try {
            mailAccess = mailService.getMailAccess(session, accountId);
            mailAccess.connect();

            SpamdSettings spamdSettings = getSpamdSettings(session);
            unwrap(new UnwrapParameter(spamFullName, move, mailAccess), mailIDs, spamdSettings, accountId, session);
        } finally {
            if (null != mailAccess) {
                mailAccess.close();
            }
        }
    }

    @Override
    public void handleSpam(final int accountId, final String fullName, final String[] mailIDs, final boolean move, final Session session) throws OXException {
        /*
         * Copy to confirmed spam folder
         */
        final MailService mailService = services.getService(MailService.class);
        if (null == mailService) {
            throw SpamhandlerSpamassassinExceptionCode.MAILSERVICE_MISSING.create();
        }
        LOGGER.debug("Handle spam for messages {} from folder {} in account {} (user={}, context={})", Arrays.toString(mailIDs), fullName, I(accountId), I(session.getUserId()), I(session.getContextId()));
        MailAccess<?, ?> mailAccess = null;
        try {
            mailAccess = mailService.getMailAccess(session, accountId);
            mailAccess.connect();
            if (isCreateConfirmedSpam(session)) {
                final String confirmedSpamFullname = mailAccess.getFolderStorage().getConfirmedSpamFolder();
                mailAccess.getMessageStorage().copyMessages(fullName, confirmedSpamFullname, mailIDs, true);
                LOGGER.debug("Spam messages {} from folder {} in account {} copied to confirmed-spam folder {} (user={}, context={})", Arrays.toString(mailIDs), fullName, I(accountId), confirmedSpamFullname, I(session.getUserId()), I(session.getContextId()));
            }

            SpamdSettings spamdSettings = getSpamdSettings(session);
            if (null != spamdSettings) {
                final MailMessage[] mails = mailAccess.getMessageStorage().getMessages(fullName, mailIDs, new MailField[]{MailField.ID, MailField.FOLDER_ID});
                spamdMessageProcessing(mails, spamdSettings, true, accountId, mailAccess, session);
            }

            if (move) {
                /*
                 * Move to spam folder
                 */
                final String spamFullname = mailAccess.getFolderStorage().getSpamFolder();
                mailAccess.getMessageStorage().moveMessages(fullName, spamFullname, mailIDs, true);
                LOGGER.debug("Spam messages {} from folder {} in account {} moved to spam folder {} (user={}, context={})", Arrays.toString(mailIDs), fullName, I(accountId), spamFullname, I(session.getUserId()), I(session.getContextId()));
            }
        } finally {
            if (null != mailAccess) {
                mailAccess.close(true);
            }
        }
    }

    private void copyMessagesToConfirmedHamAndInbox(final UnwrapParameter paramObject, final String[] plainIDsArr, final String confirmedHamFullname, final SpamdSettings spamdSettings, final int accountId, final Session session) throws OXException {
        final MailAccess<?, ?> mailAccess = paramObject.getMailAccess();
        final String spamFullname = paramObject.getSpamFullname();
        if (isCreateConfirmedHam(session)) {
            mailAccess.getMessageStorage().copyMessages(spamFullname, confirmedHamFullname, plainIDsArr, false);
        }
        if (null != spamdSettings) {
            final MailMessage[] mails = mailAccess.getMessageStorage().getMessages(spamFullname, plainIDsArr, new MailField[]{MailField.ID, MailField.FOLDER_ID});
            spamdMessageProcessing(mails, spamdSettings, false, accountId, mailAccess, session);
        }
        if (paramObject.isMove()) {
            mailAccess.getMessageStorage().moveMessages(spamFullname, SpamHandler.FULLNAME_INBOX, plainIDsArr, true);
        }
    }

    private MailMessage[] getNestedMailsAndHandleOthersAsPlain(final UnwrapParameter paramObject, final String confirmedHamFullname, final String[] nestedMessages, final SpamdSettings spamdSettings, final int accountId, final Session session) throws OXException {
        MailAccess<?, ?> mailAccess = paramObject.getMailAccess();
        String spamFullname = paramObject.getSpamFullname();

        int nestedmessagelength = nestedMessages.length;
        List<MailMessage> nestedMails = new ArrayList<MailMessage>(nestedmessagelength);
        String[] exc = new String[1];
        for (int i = 0; i < nestedmessagelength; i++) {
            MailPart wrapped;
            try {
                wrapped = mailAccess.getMessageStorage().getAttachment(spamFullname, nestedMessages[i], "2");
            } catch (OXException e) {
                if (false == MailExceptionCode.ATTACHMENT_NOT_FOUND.equals(e)) {
                    throw e;
                }
                // The original message seems not to be nested
                wrapped = mailAccess.getMessageStorage().getMessage(spamFullname, nestedMessages[i], false);
            }
            wrapped.loadContent();

            MailMessage tmp = null;
            {
                if (wrapped instanceof MailMessage) {
                    tmp = (MailMessage) wrapped;
                } else if (wrapped.getContentType().startsWith(MimeTypes.MIME_MESSAGE_RFC822)) {
                    Object content = wrapped.getContent();
                    if (content instanceof MailMessage) {
                        tmp = (MailMessage) content;
                    } else if (content instanceof MimeMessage) {
                        tmp = MimeMessageConverter.convertMessage((MimeMessage) content, new DefaultConverterConfig(mailAccess.getMailConfig(), false, false));
                    } else if (content instanceof InputStream) {
                        try {
                            tmp = MimeMessageConverter.convertMessage(new MimeMessage(MimeDefaultSession.getDefaultSession(), (InputStream) content), new DefaultConverterConfig(mailAccess.getMailConfig()));
                        } catch (MessagingException e) {
                            throw MimeMailException.handleMessagingException(e);
                        }
                    } else if (content instanceof String) {
                        try {
                            tmp = MimeMessageConverter.convertMessage(new MimeMessage(MimeDefaultSession.getDefaultSession(), new ByteArrayInputStream(((String) content).getBytes("UTF-8"))), new DefaultConverterConfig(mailAccess.getMailConfig()));
                        } catch (UnsupportedEncodingException e) {
                            throw MailExceptionCode.ENCODING_ERROR.create(e, e.getMessage());
                        } catch (MessagingException e) {
                            throw MimeMailException.handleMessagingException(e);
                        }
                    }
                }
            }

            if (null == tmp) {
                /*
                 * Handle like a plain spam message
                 */
                exc[0] = nestedMessages[i];
                copyMessagesToConfirmedHamAndInbox(paramObject, exc, confirmedHamFullname, spamdSettings, accountId, session);
            } else {
                nestedMails.add(tmp);
            }
        }

        return nestedMails.toArray(new MailMessage[nestedMails.size()]);
    }

    private SpamdSettings getSpamdSettings(Session session) throws OXException {
        SpamdSettings spamdSettings = null;
        if (getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.spamd", Boolean.FALSE, Boolean.class).booleanValue()) {
            SpamdService spamdservice = services.getService(SpamdService.class);
            if (null == spamdservice) {
                String hostName = getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.hostname", "localhost", String.class);
                int port = getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.port", Integer.valueOf(783), Integer.class).intValue();
                String userName = getUsername(session);
                spamdSettings = new SpamdSettings(hostName, port, userName);
                LOGGER.debug("Fetched SpamAssassin configuration from properties (user={}, context={}): {}", I(session.getUserId()), I(session.getContextId()), spamdSettings.toString());
            } else {
                // We have a special service providing login information, so we use that one...
                try {
                    SpamdProvider provider = spamdservice.getProvider(session);
                    spamdSettings = new SpamdSettings(provider.getHostname(), provider.getPort(), provider.getUsername());
                    LOGGER.debug("Fetched SpamAssassin configuration from SpamdService instance {} (user={}, context={}): {}", spamdservice.getClass().getSimpleName(), I(session.getUserId()), I(session.getContextId()), spamdSettings.toString());
                } catch (OXException e) {
                    throw SpamhandlerSpamassassinExceptionCode.ERROR_GETTING_SPAMD_PROVIDER.create(e, e.getMessage());
                }
            }
        }
        return spamdSettings;
    }

    private String getUsername(Session session) {
        return session.getLogin();
    }

    /**
     * @param source - A string containing the message
     * @param spam - Whether the message should be marked as spam or ham, if set to false it's marked as ham
     * @param spamdsettings the settings how spamd can be reached
     * @throws OXException
     */
    private void sendToSpamd(final String source, final boolean spam, final SpamdSettings spamdsettings, final String mailId, final String fullName, final int accountId, final Session session) throws OXException {
        try {
            // Configure service access
            SSLSocketFactoryProvider factoryProvider = services.getOptionalService(SSLSocketFactoryProvider.class);
            final Spamc spamc = new Spamc(null == factoryProvider ? null : factoryProvider.getDefault());
            spamc.setHost(spamdsettings.getHostname());
            spamc.setPort(spamdsettings.getPort());
            spamc.setUserName(spamdsettings.getUsername());
            spamc.setRetrySleep(getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.retrysleep", Integer.valueOf(1), Integer.class).intValue());
            spamc.setConnectRetries(getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.retries", Integer.valueOf(1), Integer.class).intValue());
            spamc.setTimeout(getPropertyFor(session, "com.openexchange.spamhandler.spamassassin.timeout", Long.valueOf(10), Long.class).longValue());

            // Provide message as spam/ham
            LOGGER.debug("Going to send {} message {} from folder {} in account {} to SpamAssassin service {} (user={}, context={})", spam ? "spam" : "ham", mailId, fullName, I(accountId), spamdsettings.getHostname(), I(session.getUserId()), I(session.getContextId()));
            final SpamdResponse resp = spamc.tell(source, spam, true, true, false, false);

            // Examine response code
            final int responseCode = resp.getResponseCode();
            LOGGER.debug("SpamAssassin service {} response code {} for {} message {} from folder {} in account {} (user={}, context={})", spamdsettings.getHostname(), I(responseCode), spam ? "spam" : "ham", mailId, fullName, I(accountId), I(session.getUserId()), I(session.getContextId()));
            if (Spamc.ExitCodes.EX_OK != responseCode) {
                throw SpamhandlerSpamassassinExceptionCode.WRONG_SPAMD_EXIT.create(I(responseCode));
            }
        } catch (IllegalArgumentException e) {
            throw SpamhandlerSpamassassinExceptionCode.WRONG_TELL_CMD_ARGS.create(e, e.getMessage());
        } catch (IOException e) {
            throw SpamhandlerSpamassassinExceptionCode.COMMUNICATION_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw SpamhandlerSpamassassinExceptionCode.COMMUNICATION_ERROR.create(e, e.getMessage());
        }
    }

    private PlainAndNestedMessages separatePlainAndNestedMessages(String[] mailIDs, MailMessage[] mails) {
        /*
         * Separate the plain from the nested messages inside spam folder
         */
        List<String> plainIDs = new ArrayList<String>(mailIDs.length);
        List<String> extractIDs = new ArrayList<String>(mailIDs.length);
        for (int i = 0; i < mails.length; i++) {
            MailMessage mail = mails[i];
            if (null != mail) {
                boolean add2plainIDs = true;

                String spamHdr = asciiLowerCase(mail.getFirstHeader(MessageHeaders.HDR_X_SPAM_FLAG));
                if (spamHdr != null && spamHdr.startsWith("yes")) {
                    String spamChecker = asciiLowerCase(mail.getFirstHeader("X-Spam-Checker-Version"));
                    if ((spamChecker == null) || (asciiLowerCase(spamChecker).indexOf("spamassassin") >= 0)) {
                        ContentType contentType = mail.getContentType();
                        if (contentType.startsWith("multipart/")) {
                            extractIDs.add(mailIDs[i]);
                            add2plainIDs = false;
                        }
                    }
                }

                if (add2plainIDs) {
                    plainIDs.add(mailIDs[i]);
                }
            }
        }
        return new PlainAndNestedMessages(extractIDs.toArray(new String[extractIDs.size()]), plainIDs.toArray(new String[plainIDs.size()]));
    }

    private void spamdMessageProcessing(MailMessage[] mails, SpamdSettings spamdSettings, boolean spam, int accountId, MailAccess<?, ?> mailAccess, Session session) throws OXException {
        for (final MailMessage mail : mails) {
            if (null != mail) {
                // ...then get the plaintext of the mail as spamhandler is not able to cope with our mail objects ;-) ...
                String fullName = mail.getFolder();
                String mailId = mail.getMailId();
                final String source = mailAccess.getMessageStorage().getMessage(fullName, mailId, false).getSource();

                // ...last send the plaintext over to the spamassassin daemon
                sendToSpamd(source, spam, spamdSettings, mailId, fullName, accountId, session);
            }
        }
    }

    private void unwrap(final UnwrapParameter parameterObject, final String[] mailIDs, final SpamdSettings spamdSettings, final int accountId, final Session session) throws OXException {
        MailAccess<?, ?> mailAccess = parameterObject.getMailAccess();
        /*
         * Mark as ham. In contrast to mark as spam this is a very time sucking operation. In order to deal with the original messages
         * that are wrapped inside a SpamAssassin-created message it must be extracted. Therefore we need to access message's content
         * and cannot deal only with UIDs
         */
        MailMessage[] mails = mailAccess.getMessageStorage().getMessages(parameterObject.getSpamFullname(), mailIDs, FIELDS_HEADER_CT);
        PlainAndNestedMessages plainAndNestedMessages = separatePlainAndNestedMessages(mailIDs, mails);
        String confirmedHamFullname = mailAccess.getFolderStorage().getConfirmedHamFolder();
        /*
         * Copy plain messages to confirmed ham and INBOX
         */
        copyMessagesToConfirmedHamAndInbox(parameterObject, plainAndNestedMessages.getPlainMessages(), confirmedHamFullname, spamdSettings, accountId, session);
        /*
         * Handle spamassassin messages
         */
        String[] nestedMessages = plainAndNestedMessages.getNestedMessages();

        MailMessage[] nestedMails = getNestedMailsAndHandleOthersAsPlain(parameterObject, confirmedHamFullname, nestedMessages, spamdSettings, accountId, session);
        if (null != spamdSettings) {
            spamdMessageProcessing(nestedMails, spamdSettings, false, accountId, mailAccess, session);
        }
        String[] ids = mailAccess.getMessageStorage().appendMessages(confirmedHamFullname, nestedMails);
        if (parameterObject.isMove()) {
            mailAccess.getMessageStorage().copyMessages(confirmedHamFullname, FULLNAME_INBOX, ids, true);
            mailAccess.getMessageStorage().deleteMessages(parameterObject.getSpamFullname(), nestedMessages, true);
        }
    }

}
