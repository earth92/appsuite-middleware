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

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Strings.toLowerCase;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringOutputStream;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.Mail;
import com.openexchange.ajax.SessionServlet;
import com.openexchange.ajax.container.FileHolder;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.ajax.helper.DownloadUtility;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.ajax.requesthandler.converters.preview.AbstractPreviewResultConverter;
import com.openexchange.ajax.requesthandler.responseRenderers.FileResponseRenderer;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.exception.OXException;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.MailServletInterface;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.json.MailRequest;
import com.openexchange.mail.json.converters.MailConverter;
import com.openexchange.mail.json.osgi.MailJSONActivator;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MimeDefaultSession;
import com.openexchange.mail.mime.MimeFilter;
import com.openexchange.mail.mime.MimeMailException;
import com.openexchange.mail.mime.converters.MimeMessageConverter;
import com.openexchange.mail.parser.MailMessageParser;
import com.openexchange.mail.parser.handlers.NonInlineForwardPartHandler;
import com.openexchange.preferences.ServerUserSetting;
import com.openexchange.preview.PreviewOutput;
import com.openexchange.preview.PreviewService;
import com.openexchange.preview.RemoteInternalPreviewService;
import com.openexchange.server.ServiceLookup;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.startup.ThreadControlService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.threadpool.ThreadRenamer;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link GetAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractMailAction.MODULE, type = RestrictedAction.Type.READ)
public final class GetAction extends AbstractMailAction {

    /** The logger constant */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GetAction.class);

    private static final byte[] CHUNK1 = "{\"data\":\"".getBytes();
    private static final byte[] CHUNK2 = "\"}".getBytes();
    private static final String PARAMETER_PREGENERATE_PREVIEWS = "pregenerate_previews";

    /**
     * Initializes a new {@link GetAction}.
     *
     * @param services
     */
    public GetAction(ServiceLookup services) {
        super(services);
    }

    @Override
    protected AJAXRequestResult perform(MailRequest req) throws OXException, JSONException {
        Object data = req.getRequest().getData();
        if (null == data) {
            return performGet(req);
        }
        if (!(data instanceof JSONArray)) {
            throw AjaxExceptionCodes.INVALID_JSON_REQUEST_BODY.create();
        }
        return performPut(req, (JSONArray) data);
    }

    private AJAXRequestResult performPut(MailRequest req, JSONArray paths) throws OXException {
        try {
            final int length = paths.length();
            if (length != 1) {
                throw new IllegalArgumentException("JSON array's length is not 1");
            }
            final AJAXRequestData requestData = req.getRequest();
            for (int i = 0; i < length; i++) {
                final JSONObject folderAndID = paths.getJSONObject(i);
                requestData.putParameter(AJAXServlet.PARAMETER_FOLDERID, folderAndID.getString(AJAXServlet.PARAMETER_FOLDERID));
                requestData.putParameter(AJAXServlet.PARAMETER_ID, folderAndID.get(AJAXServlet.PARAMETER_ID).toString());
            }
            /*
             * ... and fake a GET request
             */
            return performGet(new MailRequest(requestData, req.getSession()));
        } catch (JSONException e) {
            throw MailExceptionCode.JSON_ERROR.create(e, e.getMessage());
        }
    }

    private static final Pattern SPLIT = Pattern.compile(" *, *");

    private AJAXRequestResult performGet(MailRequest req) throws OXException, JSONException {
        try {
            ServerSession session = req.getSession();
            /*
             * Read in parameters
             */
            String folderPath = req.checkParameter(AJAXServlet.PARAMETER_FOLDERID);
            String tmp = req.getParameter(Mail.PARAMETER_SHOW_SRC);
            boolean showMessageSource = ("1".equals(tmp) || Boolean.parseBoolean(tmp));
            tmp = req.getParameter(Mail.PARAMETER_SHOW_HEADER);
            boolean showMessageHeaders = ("1".equals(tmp) || Boolean.parseBoolean(tmp));
            boolean saveToDisk;
            {
                String saveParam = req.getParameter(Mail.PARAMETER_SAVE);
                saveToDisk = AJAXRequestDataTools.parseBoolParameter(saveParam) || "download".equals(toLowerCase(req.getParameter(AJAXServlet.PARAMETER_DELIVERY)));
            }
            tmp = req.getParameter(Mail.PARAMETER_UNSEEN);
            boolean unseen = (tmp != null && ("1".equals(tmp) || Boolean.parseBoolean(tmp)));

            // Check for possible MIME filter
            tmp = req.getParameter("ignorable");
            MimeFilter mimeFilter;
            if (isEmpty(tmp)) {
                mimeFilter = null;
            } else {
                final String[] strings = SPLIT.split(tmp, 0);
                final int length = strings.length;
                MimeFilter mf;
                if (1 == length && (mf = MimeFilter.filterFor(strings[0])) != null) {
                    mimeFilter = mf;
                } else {
                    final List<String> ignorableContentTypes = new ArrayList<String>(length);
                    for (int i = 0; i < length; i++) {
                        final String cts = strings[i];
                        if ("ics".equalsIgnoreCase(cts)) {
                            ignorableContentTypes.add("text/calendar");
                            ignorableContentTypes.add("application/ics");
                        } else {
                            ignorableContentTypes.add(cts);
                        }
                    }
                    mimeFilter = MimeFilter.filterFor(ignorableContentTypes);
                }
            }
            tmp = req.getParameter("attach_src");
            final boolean attachMessageSource = ("1".equals(tmp) || Boolean.parseBoolean(tmp));
            tmp = null;

            // Warnings container
            List<OXException> warnings = new ArrayList<OXException>(2);

            // Get mail interface
            MailServletInterface mailInterface = getMailInterface(req);

            // Determine mail identifier
            final String uid;
            {
                String tmp2 = req.getParameter(AJAXServlet.PARAMETER_ID);
                if (null == tmp2) {
                    tmp2 = req.getParameter(Mail.PARAMETER_MESSAGE_ID);
                    if (null == tmp2) {
                        throw AjaxExceptionCodes.MISSING_PARAMETER.create(AJAXServlet.PARAMETER_ID);
                    }
                    uid = mailInterface.getMailIDByMessageID(folderPath, tmp2);
                } else {
                    uid = tmp2;
                }
            }

            LogProperties.put(LogProperties.Name.MAIL_MAIL_ID, uid);
            LogProperties.put(LogProperties.Name.MAIL_FULL_NAME, folderPath);
            AJAXRequestResult data = getJSONNullResult();
            if (showMessageSource) {
                // Get message
                final MailMessage mail = mailInterface.getMessage(folderPath, uid, !unseen);
                if (mail == null) {
                    throw MailExceptionCode.MAIL_NOT_FOUND.create(uid, folderPath);
                }

                // Direct response if possible
                if (null == mimeFilter) {
                    AJAXRequestData requestData = req.getRequest();
                    HttpServletResponse resp = requestData.optHttpServletResponse();
                    if (null != resp) {
                        if (saveToDisk) {
                            if (requestData.setResponseHeader("Content-Type", "application/octet-stream")) {
                                final StringBuilder sb = new StringBuilder(64).append("attachment");
                                {
                                    final String subject = mail.getSubject();
                                    final String fileName = isEmpty(subject) ? "mail.eml" : saneForFileName(subject) + ".eml";
                                    DownloadUtility.appendFilenameParameter(fileName, requestData.getUserAgent(), sb);
                                }
                                requestData.setResponseHeader("Content-Disposition", sb.toString());
                                requestData.removeCachingHeader();
                                OutputStream directOutputStream = resp.getOutputStream();
                                mail.writeTo(directOutputStream);
                                directOutputStream.flush();
                                return new AJAXRequestResult(AJAXRequestResult.DIRECT_OBJECT, "direct");
                            }
                        } else {
                            // As JSON response: {"data":"..."}
                            OutputStream directOutputStream = resp.getOutputStream();
                            directOutputStream.write(CHUNK1); // {"data":"...
                            {
                                final JSONStringOutputStream jsonStringOutputStream = new JSONStringOutputStream(directOutputStream);
                                mail.writeTo(jsonStringOutputStream);
                                jsonStringOutputStream.flush();
                            }
                            directOutputStream.write(CHUNK2); // ..."}
                            directOutputStream.flush();
                            return new AJAXRequestResult(AJAXRequestResult.DIRECT_OBJECT, "direct");
                        }
                    }
                }

                // The regular way...
                ThresholdFileHolder fileHolder = getMimeSource(mail, mimeFilter);
                try {
                    // Proceed
                    final boolean wasUnseen = (mail.containsPrevSeen() && !mail.isPrevSeen());
                    final boolean doUnseen = (unseen && wasUnseen);
                    if (doUnseen) {
                        mail.setFlag(MailMessage.FLAG_SEEN, false);
                        final int unreadMsgs = mail.getUnreadMessages();
                        mail.setUnreadMessages(unreadMsgs < 0 ? 0 : unreadMsgs + 1);
                    }
                    if (doUnseen) {
                        /*
                         * Leave mail as unseen
                         */
                        mailInterface.updateMessageFlags(folderPath, new String[] { uid }, MailMessage.FLAG_SEEN, false);
                    } else if (wasUnseen) {
                        /*
                         * Trigger contact collector
                         */
                        try {
                            boolean memorizeAddresses = ServerUserSetting.getInstance().isContactCollectOnMailAccess(session.getContextId(), session.getUserId()).booleanValue();
                            if (memorizeAddresses) {
                                triggerContactCollector(session, mail, true, false);
                            }
                        } catch (Exception e) {
                            LOG.warn("Contact collector could not be triggered.", e);
                        }
                    }
                    if (saveToDisk) {
                        /*
                         * Create appropriate file holder
                         */
                        final AJAXRequestData requestData = req.getRequest();
                        if (requestData.setResponseHeader("Content-Type", "application/octet-stream")) {
                            final OutputStream directOutputStream = requestData.optOutputStream();
                            if (null != directOutputStream) {
                                // Direct output
                                final StringBuilder sb = new StringBuilder(64).append("attachment");
                                {
                                    final String subject = mail.getSubject();
                                    final String fileName = isEmpty(subject) ? "mail.eml" : saneForFileName(subject) + ".eml";
                                    DownloadUtility.appendFilenameParameter(fileName, requestData.getUserAgent(), sb);
                                }
                                requestData.setResponseHeader("Content-Disposition", sb.toString());
                                requestData.removeCachingHeader();
                                final InputStream is = fileHolder.getStream();
                                try {
                                    final int len = 2048;
                                    final byte[] buf = new byte[len];
                                    for (int read; (read = is.read(buf, 0, len)) > 0;) {
                                        directOutputStream.write(buf, 0, read);
                                    }
                                } finally {
                                    Streams.close(is);
                                }
                                directOutputStream.flush();
                                return new AJAXRequestResult(AJAXRequestResult.DIRECT_OBJECT, "direct");
                            }
                        }
                        // As file holder
                        requestData.setFormat("file");
                        fileHolder.setContentType("application/octet-stream");
                        fileHolder.setDelivery("download");
                        // Set file name
                        final String subject = mail.getSubject();
                        fileHolder.setName(new StringBuilder(isEmpty(subject) ? "mail" : saneForFileName(subject)).append(".eml").toString());
                        IFileHolder temp = fileHolder;
                        fileHolder = null; // Avoid premature closing
                        return new AJAXRequestResult(temp, "file");
                    }
                    final ContentType ct = mail.getContentType();
                    if (ct.containsCharsetParameter() && CharsetDetector.isValid(ct.getCharsetParameter())) {
                        data = new AJAXRequestResult(new String(fileHolder.toByteArray(), ct.getCharsetParameter()), "string");
                    } else {
                        data = new AJAXRequestResult(new String(fileHolder.toByteArray(), "UTF-8"), "string");
                    }
                } finally {
                    Streams.close(fileHolder);
                }
            } else if (showMessageHeaders) {
                /*
                 * Get message
                 */
                final MailMessage mail = mailInterface.getMessage(folderPath, uid, !unseen);
                if (mail == null) {
                    throw MailExceptionCode.MAIL_NOT_FOUND.create(uid, folderPath);
                }
                final boolean wasUnseen = (mail.containsPrevSeen() && !mail.isPrevSeen());
                final boolean doUnseen = (unseen && wasUnseen);
                final ContentType rct = new ContentType("text/plain");
                final ContentType ct = mail.getContentType();
                if (ct.containsCharsetParameter() && CharsetDetector.isValid(ct.getCharsetParameter())) {
                    rct.setCharsetParameter(ct.getCharsetParameter());
                } else {
                    rct.setCharsetParameter("UTF-8");
                }
                data = new AJAXRequestResult(formatMessageHeaders(mail.getHeadersIterator()), "string");
                if (doUnseen) {
                    /*
                     * Leave mail as unseen
                     */
                    mailInterface.updateMessageFlags(folderPath, new String[] { uid }, MailMessage.FLAG_SEEN, false);
                } else if (wasUnseen) {
                    try {
                        boolean memorizeAddresses = ServerUserSetting.getInstance().isContactCollectOnMailAccess(session.getContextId(), session.getUserId()).booleanValue();
                        if (memorizeAddresses) {
                            triggerContactCollector(session, mail, true, false);
                        }
                    } catch (Exception e) {
                        LOG.warn("Contact collector could not be triggered.", e);
                    }
                }
            } else {
                /*
                 * Get & check message
                 */
                MailMessage mail = mailInterface.getMessage(folderPath, uid, !unseen);
                if (mail == null) {
                    OXException oxe = MailExceptionCode.MAIL_NOT_FOUND.create(uid, folderPath);
                    if (VIEW_DOCUMENT.equals(req.getParameter(Mail.PARAMETER_VIEW))) {
                        HttpServletResponse resp = req.getRequest().optHttpServletResponse();
                        if (resp != null) {
                            SessionServlet.writeErrorPage(HttpServletResponse.SC_NOT_FOUND, oxe.getDisplayMessage(session.getUser().getLocale()), resp);
                            return new AJAXRequestResult(AJAXRequestResult.DIRECT_OBJECT, "direct").setType(AJAXRequestResult.ResultType.DIRECT);
                        }
                    }
                    throw oxe;
                }
                /*
                 * Mail found...
                 */
                if (!mail.containsAccountId()) {
                    mail.setAccountId(mailInterface.getAccountID());
                }
                /*
                 * Check whether preview should be pre-generated
                 */
                if (AJAXRequestDataTools.parseBoolParameter(req.getParameter(PARAMETER_PREGENERATE_PREVIEWS)) && hasPreviewEnabled(req)) {
                    PreviewService previewService = ServerServiceRegistry.getInstance().getService(PreviewService.class);
                    ThreadPoolService threadPool = ServerServiceRegistry.getInstance().getService(ThreadPoolService.class);
                    if (null != previewService && null != threadPool) {
                        threadPool.submit(new TriggerPreviewServiceTask(folderPath, uid, req, previewService, ServerServiceRegistry.getInstance().getService(ThreadControlService.class)));
                    }
                }
                /*
                 * Check if source shall be attached
                 */
                ThresholdFileHolder fileHolder = null;
                try {
                    if (attachMessageSource) {
                        fileHolder = getMimeSource(mail, mimeFilter);
                    }
                    /*
                     * Check whether to trigger contact collector
                     */
                    if (!unseen && (mail.containsPrevSeen() && !mail.isPrevSeen())) {
                        try {
                            boolean memorizeAddresses = ServerUserSetting.getInstance().isContactCollectOnMailAccess(session.getContextId(), session.getUserId()).booleanValue();
                            if (memorizeAddresses) {
                                triggerContactCollector(session, mail, true, false);
                            }
                        } catch (Exception e) {
                            LOG.warn("Contact collector could not be triggered.", e);
                        }
                    }
                    /*
                     * Create result dependent on "attachMessageSource" flag
                     */
                    if (null == fileHolder) {
                        data = new AJAXRequestResult(mail, "mail");
                    } else {
                        AJAXRequestResult requestResult = new AJAXRequestResult(mail, "mail");
                        MailConverter.getInstance().convert2JSON(req.getRequest(), requestResult, session);
                        JSONObject jMail = (JSONObject) requestResult.getResultObject();
                        if (null != jMail) {
                            if (fileHolder.isInMemory()) {
                                jMail.put("source", new String(fileHolder.toByteArray(), Charsets.UTF_8));
                            } else {
                                jMail.put("source", new BufferedReader(new InputStreamReader(fileHolder.getClosingStream(), Charsets.UTF_8), 65536));
                                fileHolder = null; // Avoid preliminary closing
                            }
                        }
                        data = requestResult;
                    }
                } finally {
                    Streams.close(fileHolder);
                }
            }
            data.addWarnings(warnings);
            return data;
        } catch (OXException e) {
            if (MailExceptionCode.MAIL_NOT_FOUND.equals(e)) {
                LOG.debug("Requested mail could not be found. Most likely this is caused by concurrent access of multiple clients while one performed a delete on affected mail.", e);
                final String uid = getUidFromException(e);
                if ("undefined".equalsIgnoreCase(uid)) {
                    throw MailExceptionCode.PROCESSING_ERROR.create(e, new Object[0]);
                }
            } else {
                LOG.error("", e);
            }
            throw e;
        } catch (MessagingException e) {
            throw MimeMailException.handleMessagingException(e);
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    private boolean hasPreviewEnabled(MailRequest req) throws OXException {
        CapabilityService capabilityService = MailJSONActivator.SERVICES.get().getService(CapabilityService.class);
        return null != capabilityService && capabilityService.getCapabilities(req.getSession()).contains("document_preview");
    }

    private ThresholdFileHolder getMimeSource(MailMessage mail, MimeFilter mimeFilter) throws OXException, MessagingException, IOException {
        ThresholdFileHolder fileHolder = new ThresholdFileHolder();
        try {
            try {
                mail.writeTo(fileHolder.asOutputStream());
            } catch (OXException e) {
                if (!MailExceptionCode.NO_CONTENT.equals(e)) {
                    throw e;
                }
                LOG.debug("", e);
                Streams.close(fileHolder);
                fileHolder = new ThresholdFileHolder();
                fileHolder.writeZeroBytes();
            }

            // Filter
            if (null != mimeFilter) {
                InputStream is = fileHolder.getStream();
                try {
                    // Store to MIME message
                    MimeMessage mimeMessage = new MimeMessage(MimeDefaultSession.getDefaultSession(), is);
                    // Clean-up
                    Streams.close(is);
                    is = null;
                    Streams.close(fileHolder);
                    // Filter MIME message
                    MimeMessageConverter.saveChanges(mimeMessage);
                    mimeMessage = mimeFilter.filter(mimeMessage);
                    fileHolder = new ThresholdFileHolder();
                    mimeMessage.writeTo(fileHolder.asOutputStream());
                } finally {
                    Streams.close(is);
                }
            }
            ThresholdFileHolder retval = fileHolder;
            fileHolder = null;
            return retval;
        } finally {
            Streams.close(fileHolder);
        }
    }

    private static final String formatMessageHeaders(Iterator<Map.Entry<String, String>> iter) {
        final StringBuilder sb = new StringBuilder(1024);
        final String delim = ": ";
        final String crlf = "\r\n";
        while (iter.hasNext()) {
            final Map.Entry<String, String> entry = iter.next();
            sb.append(entry.getKey()).append(delim).append(entry.getValue()).append(crlf);
        }
        return sb.toString();
    }

    private static String saneForFileName(String fileName) {
        if (isEmpty(fileName)) {
            return fileName;
        }
        final int len = fileName.length();
        final StringBuilder sb = new StringBuilder(len);
        char prev = '\0';
        for (int i = 0; i < len; i++) {
            final char c = fileName.charAt(i);
            if (Strings.isWhitespace(c)) {
                if (prev != '_') {
                    prev = '_';
                    sb.append(prev);
                }
            } else if ('/' == c) {
                if (prev != '_') {
                    prev = '_';
                    sb.append(prev);
                }
            } else if ('\\' == c) {
                if (prev != '_') {
                    prev = '_';
                    sb.append(prev);
                }
            } else {
                prev = '\0';
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class TriggerPreviewServiceTask extends AbstractTask<Void> {

        private final ThreadControlService threadControl;
        private final ServerSession session;
        private final AJAXRequestData requestData;
        private final PreviewService previewService;
        private final String folderId;
        private final String mailId;

        TriggerPreviewServiceTask(String folderId, String mailId, MailRequest request, PreviewService previewService, ThreadControlService threadControl) {
            super();
            this.folderId = folderId;
            this.mailId = mailId;
            this.session = request.getSession();
            this.previewService = previewService;
            this.threadControl = null == threadControl ? ThreadControlService.DUMMY_CONTROL : threadControl;

            AJAXRequestData requestData = request.getRequest().copyOf();
            requestData.putParameter("width", Integer.toString(FileResponseRenderer.THUMBNAIL_WIDTH));
            requestData.putParameter("height", Integer.toString(FileResponseRenderer.THUMBNAIL_HEIGHT));
            requestData.putParameter("delivery", FileResponseRenderer.THUMBNAIL_DELIVERY);
            requestData.putParameter("scaleType", FileResponseRenderer.THUMBNAIL_SCALE_TYPE);
            this.requestData = requestData;
        }

        @Override
        public void setThreadName(ThreadRenamer threadRenamer) {
            threadRenamer.renamePrefix("Async-Mail-DC-Trigger");
        }

        @Override
        public Void call() {
            MailServletInterface mailInterface = null;
            try {
                mailInterface = MailServletInterface.getInstance(session);

                // Get mail
                MailMessage mail = mailInterface.getMessage(folderId, mailId, false);
                if (null == mail) {
                    return null;
                }

                // Determine non-inline parts
                NonInlineForwardPartHandler handler = new NonInlineForwardPartHandler();
                new MailMessageParser().setInlineDetectorBehavior(true).parseMailMessage(mail, handler);
                List<MailPart> nonInlineParts = handler.getNonInlineParts();

                // Check non-inline parts
                if (null == nonInlineParts || nonInlineParts.isEmpty()) {
                    // Mail has no file attachments
                    return null;
                }

                // Trigger preview generation
                Thread currentThread = Thread.currentThread();
                boolean added = threadControl.addThread(currentThread);
                try {
                    for (MailPart mailPart : nonInlineParts) {
                        try {
                            triggerFor(mailPart);
                        } catch (Exception e) {
                            LOG.warn("Failed to pre-generate preview for attachment {} of mail {} in folder {} of user {} in context {}", mailPart.getSequenceId(), mailId, folderId, I(session.getUserId()), I(session.getContextId()), e);
                        }
                    }
                } finally {
                    if (added) {
                        threadControl.removeThread(currentThread);
                    }
                }
            } catch (Exception e) {
                LOG.debug("Failed to pre-generate preview image for mail {} in folder {} of user {} in context {}", mailId, folderId, I(session.getUserId()), I(session.getContextId()), e);
            } finally {
                if (null != mailInterface) {
                    mailInterface.close();
                }
            }
            return null;
        }

        private void triggerFor(MailPart mailPart) {
            RemoteInternalPreviewService candidate = AbstractPreviewResultConverter.getRemoteInternalPreviewServiceFrom(previewService, mailPart.getFileName(), PreviewOutput.IMAGE, session);
            if (null != candidate) {
                // Create appropriate IFileHolder instance
                IFileHolder.InputStreamClosure isClosure = new IFileHolder.InputStreamClosure() {

                    @Override
                    public InputStream newStream() throws OXException {
                        InputStream inputStream = mailPart.getInputStream();
                        if ((inputStream instanceof BufferedInputStream) || (inputStream instanceof ByteArrayInputStream)) {
                            return inputStream;
                        }
                        return new BufferedInputStream(inputStream, 65536);
                    }
                };
                FileHolder fileHolder = new FileHolder(isClosure, -1, mailPart.getContentType().getBaseType(), mailPart.getFileName());

                AbstractPreviewResultConverter.triggerPreviewService(session, fileHolder, requestData, candidate, PreviewOutput.IMAGE);
                LOG.debug("Triggered to create preview from file attachment {} of mail {} in folder {} for user {} in context {}", mailPart.getFileName(), mailId, folderId, I(session.getUserId()), I(session.getContextId()));
            } else {
                LOG.debug("Found no suitable {} service to trigger preview creation from file attachment {} of mail {} in folder {} for user {} in context {}", RemoteInternalPreviewService.class.getSimpleName(), mailPart.getFileName(), mailId,
                    folderId, I(session.getUserId()), I(session.getContextId()));
            }
        }
    }

}
