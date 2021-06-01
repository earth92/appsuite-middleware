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

package com.openexchange.mail.json.actions;

import static com.openexchange.mail.utils.MailFolderUtility.prepareFullname;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.Mail;
import com.openexchange.ajax.fields.DataFields;
import com.openexchange.ajax.fields.FolderChildFields;
import com.openexchange.ajax.helper.ParamContainer;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.DispatcherNotes;
import com.openexchange.ajax.requesthandler.EnqueuableAJAXActionService;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.ajax.requesthandler.jobqueue.JobKey;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.configuration.ServerConfig.Property;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.upload.impl.UploadEvent;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.MailJSONField;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.MailServletInterface;
import com.openexchange.mail.api.IMailFolderStorage;
import com.openexchange.mail.api.IMailMessageStorage;
import com.openexchange.mail.api.MailAccess;
import com.openexchange.mail.api.MailConfig.PasswordSource;
import com.openexchange.mail.cache.MailMessageCache;
import com.openexchange.mail.compose.old.OldCompositionSpace;
import com.openexchange.mail.compose.old.OldCompositionSpaces;
import com.openexchange.mail.config.MailConfigException;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.compose.ComposeType;
import com.openexchange.mail.dataobjects.compose.ComposedMailMessage;
import com.openexchange.mail.dataobjects.compose.ContentAwareComposedMailMessage;
import com.openexchange.mail.event.EventPool;
import com.openexchange.mail.event.PooledEvent;
import com.openexchange.mail.json.MailRequest;
import com.openexchange.mail.json.compose.ComposeDraftResult;
import com.openexchange.mail.json.compose.ComposeHandler;
import com.openexchange.mail.json.compose.ComposeHandlerRegistry;
import com.openexchange.mail.json.compose.ComposeRequest;
import com.openexchange.mail.json.compose.ComposeTransportResult;
import com.openexchange.mail.json.parser.MessageParser;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.MimeMailExceptionCode;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.mail.mime.converters.MimeMessageConverter;
import com.openexchange.mail.mime.dataobjects.MimeMailMessage;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.transport.MailTransport;
import com.openexchange.mail.transport.MtaStatusInfo;
import com.openexchange.mail.usersetting.UserSettingMail;
import com.openexchange.mail.utils.MailFolderUtility;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.MailAccounts;
import com.openexchange.preferences.ServerUserSetting;
import com.openexchange.server.ServiceLookup;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link NewAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@DispatcherNotes(preferStream = true, enqueueable = true)
@RestrictedAction(module = AbstractMailAction.MODULE, type = RestrictedAction.Type.WRITE)
public final class NewAction extends AbstractMailAction implements EnqueuableAJAXActionService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NewAction.class);

    private static final String FLAGS = MailJSONField.FLAGS.getKey();
    private static final String FROM = MailJSONField.FROM.getKey();
    private static final String UPLOAD_FORMFIELD_MAIL = AJAXServlet.UPLOAD_FORMFIELD_MAIL;
    private static final String PLAIN_JSON = AJAXServlet.PARAM_PLAIN_JSON;

    private final EnumSet<ComposeType> draftTypes;

    /**
     * Initializes a new {@link NewAction}.
     *
     * @param services
     */
    public NewAction(ServiceLookup services) {
        super(services);
        draftTypes = EnumSet.of(ComposeType.DRAFT, ComposeType.DRAFT_DELETE_ON_TRANSPORT, ComposeType.DRAFT_EDIT, ComposeType.DRAFT_NO_DELETE_ON_TRANSPORT);
    }

    private boolean hasUploads(AJAXRequestData request, ServerSession session) throws OXException {
        long maxSize;
        long maxFileSize;
        {
            UserSettingMail usm = session.getUserSettingMail();
            maxFileSize = usm.getUploadQuotaPerFile();
            if (maxFileSize <= 0) {
                maxFileSize = -1L;
            }
            maxSize = usm.getUploadQuota();
            if (maxSize <= 0) {
                if (maxSize == 0) {
                    maxSize = -1L;
                } else {
                    LOG.debug("Upload quota is less than zero. Using global server property \"MAX_UPLOAD_SIZE\" instead.");
                    long globalQuota;
                    try {
                        globalQuota = ServerConfig.getLong(Property.MAX_UPLOAD_SIZE).longValue();
                    } catch (OXException e) {
                        LOG.error("", e);
                        globalQuota = 0L;
                    }
                    maxSize = globalQuota <= 0 ? -1L : globalQuota;
                }
            }
        }

        return request.hasUploads(maxFileSize, maxSize);
    }

    @Override
    public EnqueuableAJAXActionService.Result isEnqueueable(AJAXRequestData request, ServerSession session) throws OXException {
        // Only allowed if no JS call-back is expected
        if (hasUploads(request, session)) {
            if (false == AJAXRequestDataTools.parseBoolParameter(PLAIN_JSON, request)) {
                return EnqueuableAJAXActionService.resultFor(false);
            }

            UploadEvent uploadEvent = request.getUploadEvent();
            String json0 = uploadEvent.getFormField(UPLOAD_FORMFIELD_MAIL);
            if (json0 == null || json0.trim().length() == 0) {
                throw MailExceptionCode.PROCESSING_ERROR.create(MailExceptionCode.MISSING_PARAM.create(UPLOAD_FORMFIELD_MAIL), new Object[0]);
            }

            try {
                JSONObject jMail = new JSONObject(json0);

                JSONObject jKeyDesc = new JSONObject(6);
                jKeyDesc.put("module", "mail");
                jKeyDesc.put("action", "new");
                jKeyDesc.put("uploads", true);
                {
                    Object from = jMail.opt(FROM);
                    jKeyDesc.put("from", null == from ? JSONObject.NULL : from);
                }
                {
                    Object to = jMail.opt(MailJSONField.RECIPIENT_TO.getKey());
                    jKeyDesc.put("to", null == to ? JSONObject.NULL : to);
                }
                {
                    Object subject = jMail.opt(MailJSONField.SUBJECT.getKey());
                    jKeyDesc.put("subject", null == subject ? "" : subject);
                }
                jKeyDesc.put("draft", (jMail.optInt(FLAGS, 0) & MailMessage.FLAG_DRAFT) > 0);

                return EnqueuableAJAXActionService.resultFor(true, new JobKey(session.getUserId(), session.getContextId(), jKeyDesc.toString()), this);
            } catch (JSONException e) {
                throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
            }
        }

        return EnqueuableAJAXActionService.resultFor(true);
    }

    @Override
    protected AJAXRequestResult perform(MailRequest req) throws OXException {
        try {
            AJAXRequestData request = req.getRequest();
            if (hasUploads(request, req.getSession()) || request.getParameter(UPLOAD_FORMFIELD_MAIL) != null) {
                return performWithUploads(req, request);
            }
            return performWithoutUploads(req);
        } catch (JSONException e) {
            throw MailExceptionCode.JSON_ERROR.create(e, e.getMessage());
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        } catch (RuntimeException e) {
            throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    private AJAXRequestResult performWithUploads(MailRequest req, AJAXRequestData request) throws OXException, JSONException {
        ServerSession session = req.getSession();
        String csid = req.getParameter(AJAXServlet.PARAMETER_CSID);
        UploadEvent uploadEvent = request.getUploadEvent();
        List<OXException> warnings = new ArrayList<OXException>();
        String msgIdentifier = null;
        UserSettingMail userSettingMail = null;
        ComposeTransportResult transportResult = null;
        try {
            JSONObject jMail;
            {
                String json0 = uploadEvent.getFormField(UPLOAD_FORMFIELD_MAIL);
                if (json0 == null || json0.trim().length() == 0) {
                    throw MailExceptionCode.PROCESSING_ERROR.create(MailExceptionCode.MISSING_PARAM.create(UPLOAD_FORMFIELD_MAIL), new Object[0]);
                }
                jMail = new JSONObject(json0);

                if (null == csid) {
                    csid = jMail.optString("csid", null);
                }
            }
            /*-
             * Parse
             *
             * Resolve "From" to proper mail account to select right transport server
             */
            InternetAddress from;
            try {
                final InternetAddress[] fromAddresses = MessageParser.parseAddressKey(FROM, jMail, true);
                from = null == fromAddresses || 0 == fromAddresses.length ? null : fromAddresses[0];
            } catch (AddressException e) {
                throw MimeMailException.handleMessagingException(e);
            }

            int accountId;
            try {
                accountId = resolveFrom2Account(session, from, true, true);
            } catch (OXException e) {
                if (MailExceptionCode.NO_TRANSPORT_SUPPORT.equals(e) || MailExceptionCode.INVALID_SENDER.equals(e)) {
                    // Re-throw
                    throw e;
                }
                LOG.warn("{}. Using default account's transport.", e.getMessage());
                // Send with default account's transport provider
                accountId = MailAccount.DEFAULT_ID;
            }

            boolean newMessageId = AJAXRequestDataTools.parseBoolParameter(AJAXServlet.ACTION_NEW, request);
            boolean isDraft = (jMail.optInt(FLAGS, 0) & MailMessage.FLAG_DRAFT) > 0;

            // Determine send type
            ComposeType sendType = jMail.hasAndNotNull(Mail.PARAMETER_SEND_TYPE) ? ComposeType.getType(jMail.getInt(Mail.PARAMETER_SEND_TYPE)) : (isDraft ? null : ComposeType.NEW);

            // Create compose request to process
            ComposeRequest composeRequest = new ComposeRequest(accountId, jMail, sendType, uploadEvent, request, warnings);

            // Determine appropriate compose handler
            ComposeHandlerRegistry handlerRegistry = ServerServiceRegistry.getInstance().getService(ComposeHandlerRegistry.class);
            ComposeHandler composeHandler = handlerRegistry.getComposeHandlerFor(composeRequest);

            // Process compose request
            if (isDraft) {
                // Save as draft
                ComposeDraftResult draftResult = composeHandler.createDraftResult(composeRequest);
                ComposedMailMessage composedMail = draftResult.getDraftMessage();
                UserSettingMail usm = session.getUserSettingMail();
                usm.setNoSave(true);
                checkAndApplyLineWrapAfter(request, usm);
                composedMail.setMailSettings(usm);
                MailPath msgref = composedMail.getMsgref();
                if (null != sendType) {
                    composedMail.setSendType(sendType);
                }
                if (newMessageId) {
                    composedMail.removeHeader("Message-ID");
                    composedMail.removeMessageId();
                }

                MailServletInterface mailInterface = getMailInterface(req);

                msgIdentifier = mailInterface.saveDraft(composedMail, false, accountId).toString();
                if (msgIdentifier == null) {
                    throw MailExceptionCode.DRAFT_FAILED_UNKNOWN.create();
                }

                if (null != csid) {
                    if (null != msgref && ComposeType.DRAFT_EDIT.equals(sendType)) {
                        OldCompositionSpace space = OldCompositionSpace.getCompositionSpace(csid, session);
                        if (!space.isMarkedAsReplyOrForward(msgref)) {
                            space.addCleanUp(msgref);
                        }
                    }

                    OldCompositionSpaces.applyCompositionSpace(csid, session, mailInterface.getMailAccess(), false);
                    OldCompositionSpaces.destroy(csid, session);
                }

                warnings.addAll(mailInterface.getWarnings());

                boolean plainJson = AJAXRequestDataTools.parseBoolParameter(PLAIN_JSON, request);
                AJAXRequestResult result = resultFor(msgIdentifier, plainJson);
                result.addWarnings(warnings);
                return result;
            }

            // As new/transport message
            transportResult = composeHandler.createTransportResult(composeRequest);
            List<? extends ComposedMailMessage> composedMails = transportResult.getTransportMessages();
            ComposedMailMessage sentMessage = transportResult.getSentMessage();
            boolean transportEqualToSent = transportResult.isTransportEqualToSent();

            if (newMessageId) {
                for (ComposedMailMessage composedMail : composedMails) {
                    if (null != composedMail) {
                        composedMail.removeHeader("Message-ID");
                        composedMail.removeMessageId();
                    }
                }
                sentMessage.removeHeader("Message-ID");
                sentMessage.removeMessageId();
            }

            // Adjust send type if needed
            if (null != csid) {
                if (ComposeType.DRAFT.equals(sendType) || ComposeType.DRAFT_DELETE_ON_TRANSPORT.equals(sendType)) {
                    for (ComposedMailMessage composedMail : composedMails) {
                        MailPath msgref = composedMail.getMsgref();
                        if (null != msgref) {
                            OldCompositionSpace space = OldCompositionSpace.getCompositionSpace(csid, session);
                            if (space.isMarkedAsReply(msgref)) {
                                sendType = ComposeType.REPLY;
                            } else if (space.isMarkedAsForward(msgref)) {
                                sendType = ComposeType.FORWARD;
                            }
                        }
                    }
                }
            }

            String folder = req.getParameter(AJAXServlet.PARAMETER_FOLDERID);
            if (null != folder) {
                // Do the "fake" transport by providing poison address
                MailTransport mailTransport = MailTransport.getInstance(session, accountId);
                MailMessage mm = mailTransport.sendMailMessage(sentMessage, sendType, new javax.mail.Address[] { MimeMessageUtility.POISON_ADDRESS });

                // Apply composition space state(s)
                MailServletInterface mailInterface = getMailInterface(req);
                mailInterface.openFor(folder);
                if (null != csid) {
                    OldCompositionSpaces.applyCompositionSpace(csid, session, mailInterface.getMailAccess(), false);
                    OldCompositionSpaces.destroy(csid, session);
                }

                // Append messages
                String[] ids = mailInterface.appendMessages(folder, new MailMessage[] { mm }, false);

                // Commit results as messages were appended
                {
                    transportResult.commit();
                    transportResult.finish();
                    transportResult = null;
                }

                msgIdentifier = ids[0];
                mailInterface.updateMessageFlags(folder, ids, MailMessage.FLAG_SEEN, true);

                return resultFor(new JSONObject(2).put(FolderChildFields.FOLDER_ID, folder).put(DataFields.ID, ids[0]));
            }

            // Normal transport
            if (!newMessageId && draftTypes.contains(sendType)) {
                for (ComposedMailMessage cm : composedMails) {
                    if (null != cm) {
                        cm.removeHeader("Message-ID");
                        cm.removeMessageId();
                    }
                }
            }
            if (ComposeType.DRAFT.equals(sendType)) {
                String paramName = "deleteDraftOnTransport";
                if (jMail.hasAndNotNull(paramName)) { // Provided by JSON body
                    Object object = jMail.opt(paramName);
                    if (null != object) {
                        if (AJAXRequestDataTools.parseBoolParameter(object.toString())) {
                            sendType = ComposeType.DRAFT_DELETE_ON_TRANSPORT;
                        } else if (false && Boolean.FALSE.equals(AJAXRequestDataTools.parseFalseBoolParameter(object.toString()))) {
                            // Explicitly deny deletion of draft message
                            sendType = ComposeType.DRAFT_NO_DELETE_ON_TRANSPORT;
                        }
                    }
                } else if (request.containsParameter(paramName)) { // Provided as URL parameter
                    String sDeleteDraftOnTransport = request.getParameter(paramName);
                    if (null != sDeleteDraftOnTransport) {
                        if (AJAXRequestDataTools.parseBoolParameter(sDeleteDraftOnTransport)) {
                            sendType = ComposeType.DRAFT_DELETE_ON_TRANSPORT;
                        } else if (false && Boolean.FALSE.equals(AJAXRequestDataTools.parseFalseBoolParameter(sDeleteDraftOnTransport))) {
                            // Explicitly deny deletion of draft message
                            sendType = ComposeType.DRAFT_NO_DELETE_ON_TRANSPORT;
                        }
                    }
                }
            } else if (ComposeType.DRAFT.equals(sendType)) {

            }
            for (ComposedMailMessage cm : composedMails) {
                if (null != cm) {
                    cm.setSendType(sendType);
                }
            }

            // User settings
            UserSettingMail usm = session.getUserSettingMail();
            usm.setNoSave(true);
            {
                final String paramName = "copy2Sent";
                if (jMail.hasAndNotNull(paramName)) { // Provided by JSON body
                    Object object = jMail.opt(paramName);
                    if (null != object) {
                        if (AJAXRequestDataTools.parseBoolParameter(object.toString())) {
                            usm.setNoCopyIntoStandardSentFolder(false);
                        } else if (Boolean.FALSE.equals(AJAXRequestDataTools.parseFalseBoolParameter(object.toString()))) {
                            // Explicitly deny copy to sent folder
                            usm.setNoCopyIntoStandardSentFolder(true);
                        }
                    }
                } else if (request.containsParameter(paramName)) { // Provided as URL parameter
                    String sCopy2Sent = request.getParameter(paramName);
                    if (null != sCopy2Sent) {
                        if (AJAXRequestDataTools.parseBoolParameter(sCopy2Sent)) {
                            usm.setNoCopyIntoStandardSentFolder(false);
                        } else if (Boolean.FALSE.equals(AJAXRequestDataTools.parseFalseBoolParameter(sCopy2Sent))) {
                            // Explicitly deny copy to sent folder
                            usm.setNoCopyIntoStandardSentFolder(true);
                        }
                    }
                } else {
                    MailAccountStorageService mass = ServerServiceRegistry.getInstance().getService(MailAccountStorageService.class);
                    if (mass != null && MailAccounts.isGmailTransport(mass.getTransportAccount(accountId, session.getUserId(), session.getContextId()))) {
                        // Deny copy to sent folder for Gmail
                        usm.setNoCopyIntoStandardSentFolder(true);
                    }
                }
            }
            checkAndApplyLineWrapAfter(request, usm);
            for (ComposedMailMessage cm : composedMails) {
                if (null != cm) {
                    cm.setMailSettings(usm);
                }
            }
            if (null != sentMessage) {
                sentMessage.setMailSettings(usm);
            }
            userSettingMail = usm;

            // ------------------------------------ Send the messages --------------------------------------
            MailServletInterface mailInterface = getMailInterface(req);

            HttpServletRequest servletRequest = request.optHttpServletRequest();
            String remoteAddress = null == servletRequest ? request.getRemoteAddress() : servletRequest.getRemoteAddr();
            List<String> ids = mailInterface.sendMessages(composedMails, sentMessage, transportEqualToSent, sendType, accountId, usm, new MtaStatusInfo(), remoteAddress);
            msgIdentifier = null == ids || ids.isEmpty() ? null : ids.get(0);
            if (null != msgIdentifier) {
                // Commit results as actual transport was executed
                {
                    transportResult.commit();
                    transportResult.finish();
                    transportResult = null;
                }
            }

            // Apply composition space state(s)
            if (null != csid) {
                OldCompositionSpaces.applyCompositionSpace(csid, session, null, true);
                OldCompositionSpaces.destroy(csid, session);
            }

            warnings.addAll(mailInterface.getWarnings());

            // Trigger contact collector
            try {
                boolean memorizeAddresses = ServerUserSetting.getInstance().isContactCollectOnMailTransport(session.getContextId(), session.getUserId()).booleanValue();
                triggerContactCollector(session, composedMails, memorizeAddresses, true);
            } catch (Exception e) {
                LOG.warn("Contact collector could not be triggered.", e);
            }

            if (msgIdentifier == null) {
                if (userSettingMail.isNoCopyIntoStandardSentFolder()) {
                    final AJAXRequestResult result = resultFor(null, false);
                    result.addWarnings(warnings);
                    return result;
                }
                if (warnings.isEmpty()) {
                    throw MailExceptionCode.SEND_FAILED_UNKNOWN.create();
                }
                final AJAXRequestResult result = resultFor(null, false);
                result.addWarnings(warnings);
                return result;
            }

            /*
             * Create JSON response object
             */
            boolean plainJson = AJAXRequestDataTools.parseBoolParameter(PLAIN_JSON, request);
            AJAXRequestResult result = resultFor(msgIdentifier, plainJson);
            result.addWarnings(warnings);
            return result;
        } finally {
            if (transportResult != null) {
                transportResult.rollback();
                transportResult.finish();
                transportResult = null;
            }
        }
    }

    private AJAXRequestResult resultFor(String msgIdentifier, boolean asJsonObject) {
        if (null == msgIdentifier) {
            return new AJAXRequestResult(JSONObject.NULL, "json");
        }

        if (false == asJsonObject) {
            return new AJAXRequestResult(msgIdentifier, "string");
        }

        try {
            MailPath mp = new MailPath(msgIdentifier);
            return new AJAXRequestResult(new JSONObject(2).put(FolderChildFields.FOLDER_ID, prepareFullname(mp.getAccountId(), mp.getFolder())).put(DataFields.ID, mp.getMailID()), "json");
        } catch (Exception e) {
            // Invalid mail path
            return new AJAXRequestResult(msgIdentifier, "string");
        }
    }

    private AJAXRequestResult resultFor(JSONObject jObject) {
        return new AJAXRequestResult(null == jObject ? JSONObject.NULL : jObject, "json");
    }

    private void checkAndApplyLineWrapAfter(AJAXRequestData request, UserSettingMail usm) throws OXException {
        String paramName = "lineWrapAfter";
        if (request.containsParameter(paramName)) { // Provided as URL parameter
            String sLineWrapAfter = request.getParameter(paramName);
            if (null != sLineWrapAfter) {
                try {
                    int lineWrapAfter = Integer.parseInt(sLineWrapAfter.trim());
                    usm.setAutoLinebreak(lineWrapAfter <= 0 ? 0 : lineWrapAfter);
                } catch (NumberFormatException nfe) {
                    throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(nfe, paramName, sLineWrapAfter);
                }
            }
        }
    }

    /**
     * Performs sending a data block.
     *
     * @param req The mail request
     * @param warnings The warnings to fill
     * @return The request result
     */
    protected AJAXRequestResult performWithoutUploads(MailRequest req) throws OXException, MessagingException, JSONException {
        /*
         * Non-POST
         */
        final ServerSession session = req.getSession();
        /*
         * Read in parameters
         */
        boolean newMessageId = AJAXRequestDataTools.parseBoolParameter(AJAXServlet.ACTION_NEW, req.getRequest());
        String folder = req.getParameter(AJAXServlet.PARAMETER_FOLDERID);
        int flags;
        {
            final int i = req.optInt(Mail.PARAMETER_FLAGS);
            flags = MailRequest.NOT_FOUND == i ? 0 : i;
        }
        boolean force;
        {
            final String tmp = req.getParameter("force");
            if (null == tmp) {
                force = false;
            } else {
                force = AJAXRequestDataTools.parseBoolParameter(tmp);
            }
        }

        // Get RFC822 bytes and create corresponding mail message
        QuotedInternetAddress defaultSendAddr = new QuotedInternetAddress(getDefaultSendAddress(session), false);
        PutNewMailData data;
        {
            MimeMessage message = loadMimeMessageFrom(req);
            message.removeHeader("x-original-headers");
            if (newMessageId) {
                message.removeHeader("Message-ID");
            }
            message.foldAllHeaderLines();
            String fromAddr = message.getHeader(MessageHeaders.HDR_FROM, null);
            if (isEmpty(fromAddr)) {
                // Add from address
                InternetAddress fromAddress = defaultSendAddr;
                message.setFrom(fromAddress);
                data = new PutNewMailDataImpl(MimeMessageConverter.convertMessage(message), fromAddress);
            } else {
                data = new PutNewMailDataImpl(MimeMessageConverter.convertMessage(message), new QuotedInternetAddress(fromAddr, true));
            }
        }

        // Check if "folder" element is present which indicates to save given message as a draft or append to denoted folder
        if (folder == null) {
            AJAXRequestResult result = transportMessage(session, flags, force, data.getFromAddress(), data.getMail(), req.getRequest());
            if (null == result) {
                result = new AJAXRequestResult(new JSONObject(), "json");
            }
            return result;
        }

        String[] ids;
        MailServletInterface mailInterface = MailServletInterface.getInstance(session);
        try {
            ids = mailInterface.appendMessages(folder, new MailMessage[] { data.getMail() }, force);
            if (flags > 0) {
                mailInterface.updateMessageFlags(folder, ids, flags, true);
            }
        } finally {
            mailInterface.close(true);
        }

        JSONObject responseObj = new JSONObject(3);
        responseObj.put(FolderChildFields.FOLDER_ID, folder);
        responseObj.put(DataFields.ID, ids[0]);

        return new AJAXRequestResult(responseObj, "json");
    }

    private MimeMessage loadMimeMessageFrom(MailRequest req) throws OXException {
        HttpServletRequest httpRequest = req.getRequest().optHttpServletRequest();
        if (null == httpRequest) {
            return MimeMessageUtility.newMimeMessage(Streams.newByteArrayInputStream(Charsets.toAsciiBytes((String) req.getRequest().requireData())), true);
        }

        Object requestBody = req.getRequest().getData();
        if (null != requestBody) {
            return MimeMessageUtility.newMimeMessage(Streams.newByteArrayInputStream(Charsets.toAsciiBytes((String) requestBody)), true);
        }

        // Not yet loaded
        try {
            return MimeMessageUtility.newMimeMessage(httpRequest.getInputStream(), true);
        } catch (IOException e) {
            throw AjaxExceptionCodes.IO_ERROR.create(e, e.getMessage());
        }
    }

    private interface PutNewMailData {

        InternetAddress getFromAddress();

        MailMessage getMail();
    }

    private static class PutNewMailDataImpl implements PutNewMailData {

        private final MailMessage mail;
        private final InternetAddress fromAddress;

        PutNewMailDataImpl(MailMessage mail, InternetAddress fromAddress) {
            super();
            this.mail = mail;
            this.fromAddress = fromAddress;
        }

        @Override
        public MailMessage getMail() {
            return mail;
        }

        @Override
        public InternetAddress getFromAddress() {
            return fromAddress;
        }
    }

    private AJAXRequestResult transportMessage(ServerSession session, int flags, boolean force, InternetAddress from, MailMessage m, AJAXRequestData request) throws OXException, JSONException {
        /*
         * Determine the account to transport with
         */
        final int accountId;
        {
            int accId;
            try {
                accId = resolveFrom2Account(session, from, true, !force);
            } catch (OXException e) {
                if (MailExceptionCode.NO_TRANSPORT_SUPPORT.equals(e)) {
                    // Re-throw
                    throw e;
                }
                if (!force && MailExceptionCode.INVALID_SENDER.equals(e)) {
                    throw e;
                }
                LOG.warn("{}. Using default account's transport.", e.getMessage());
                // Send with default account's transport provider
                accId = MailAccount.DEFAULT_ID;
            }
            accountId = accId;
        }
        /*
         * Check for OAuth request
         */
        if (isOAuthRequest(request)) {
            if (MailAccount.DEFAULT_ID == accountId) {
                PasswordSource passwordSource = MailProperties.getInstance().getPasswordSource(session.getUserId(), session.getContextId());
                switch (passwordSource) {
                    case GLOBAL: {
                        // Just for convenience
                        String masterPassword = MailProperties.getInstance().getMasterPassword(session.getUserId(), session.getContextId());
                        if (masterPassword == null) {
                            throw MailConfigException.create("Property \"com.openexchange.mail.masterPassword\" not set");
                        }
                        break;
                    }
                    case SESSION:
                        // Fall-through
                    default: {
                        if (null == session.getPassword()) {
                            throw MailExceptionCode.MISSING_CONNECT_PARAM.create("password");
                        }
                        break;
                    }
                }
            } else {
                if (null == session.getPassword()) {
                    // Deny for now.
                    // ( Might be possible to check SecretService if a proper secret is determineable although no password available )
                    throw MailExceptionCode.MISSING_CONNECT_PARAM.create("password");
                }
            }
        }
        /*
         * Missing "folder" element indicates to send given message via default mail account
         */
        final MailTransport transport = MailTransport.getInstance(session, accountId);
        try {
            /*
             * Send raw message source
             */
            MailMessage sentMail;
            OXException oxError = null;
            try {
                if (m instanceof MimeMailMessage) {
                    sentMail = transport.sendMailMessage(new ContentAwareComposedMailMessage(((MimeMailMessage) m).getMimeMessage(), session, null), ComposeType.NEW);
                } else {
                    sentMail = transport.sendRawMessage(m.getSourceBytes());
                }
            } catch (OXException e) {
                if (!MimeMailExceptionCode.SEND_FAILED_EXT.equals(e) && !MimeMailExceptionCode.SEND_FAILED_MSG_ERROR.equals(e)) {
                    throw e;
                }

                MailMessage ma = (MailMessage) e.getArgument("sent_message");
                if (null == ma) {
                    throw e;
                }

                sentMail = ma;
                oxError = e;
            }
            /*
             * User settings
             */
            final UserSettingMail usm = session.getUserSettingMail();
            usm.setNoSave(true);
            {
                String paramName = "copy2Sent";
                if (request.containsParameter(paramName)) { // Provided as URL parameter
                    String sCopy2Sent = request.getParameter(paramName);
                    if (null != sCopy2Sent) {
                        if (AJAXRequestDataTools.parseBoolParameter(sCopy2Sent)) {
                            usm.setNoCopyIntoStandardSentFolder(false);
                        } else if (Boolean.FALSE.equals(AJAXRequestDataTools.parseFalseBoolParameter(sCopy2Sent))) {
                            // Explicitly deny copy to sent folder
                            usm.setNoCopyIntoStandardSentFolder(true);
                        }
                    }
                }
            }
            AJAXRequestResult result = null;
            if (!usm.isNoCopyIntoStandardSentFolder()) {
                /*
                 * Copy in sent folder allowed
                 */
                List<OXException> warnings = new LinkedList<>();
                MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess = null;
                try {
                    mailAccess = MailAccess.getInstance(session, accountId);
                    String sentFullname;
                    String[] uidArr;
                    try {
                        mailAccess.connect();
                        sentFullname = MailFolderUtility.prepareMailFolderParamOrElseReturn(mailAccess.getFolderStorage().getSentFolder());
                        /*
                         * Append to default "sent" folder
                         */
                        if (flags != ParamContainer.NOT_FOUND) {
                            sentMail.setFlags(flags);
                        }
                        uidArr = mailAccess.getMessageStorage().appendMessages(sentFullname, new MailMessage[] { sentMail });
                        /*
                         * Post event
                         */
                        if (MailAccount.DEFAULT_ID == accountId) {
                            /*
                             * TODO: Event only for primary account?
                             */
                            EventPool.getInstance().put(new PooledEvent(session.getContextId(), session.getUserId(), accountId, prepareFullname(accountId, sentFullname), true, true, true, session));
                        }
                        /*
                         * Update cache
                         */
                        try {
                            MailMessageCache.getInstance().removeFolderMessages(accountId, sentFullname, session.getUserId(), session.getContext().getContextId());
                        } catch (OXException e) {
                            LOG.error("", e);
                        }
                    } catch (OXException e) {
                        if (e.getMessage().indexOf("quota") != -1) {
                            throw MailExceptionCode.COPY_TO_SENT_FOLDER_FAILED_QUOTA.create(e, new Object[0]);
                        }
                        throw MailExceptionCode.COPY_TO_SENT_FOLDER_FAILED.create(e, new Object[0]);
                    }
                    if ((uidArr != null) && (uidArr[0] != null)) {
                        /*
                         * Mark appended sent mail as seen
                         */
                        try {
                            mailAccess.getMessageStorage().updateMessageFlags(sentFullname, uidArr, MailMessage.FLAG_SEEN, true);
                        } catch (OXException e) {
                            warnings.add(e.setCategory(Category.CATEGORY_WARNING));
                        }
                    }
                    /*
                     * Compose JSON object
                     */
                    JSONObject responseData = new JSONObject(2);
                    responseData.put(FolderChildFields.FOLDER_ID, MailFolderUtility.prepareFullname(MailAccount.DEFAULT_ID, sentFullname));
                    responseData.put(DataFields.ID, uidArr[0]);
                    result = new AJAXRequestResult(responseData, "json");
                } finally {
                    if (null != mailAccess) {
                        mailAccess.close(true);
                    }
                }
            }
            if (null != oxError) {
                throw oxError;
            }
            return result;
        } finally {
            transport.close();
        }
    }

}
