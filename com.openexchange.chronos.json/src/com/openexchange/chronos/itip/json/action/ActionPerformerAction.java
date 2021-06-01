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

package com.openexchange.chronos.itip.json.action;

import static com.openexchange.chronos.itip.json.action.Utils.initCalendarSession;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONException;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.chronos.Attachment;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.itip.ITipAction;
import com.openexchange.chronos.itip.ITipActionPerformer;
import com.openexchange.chronos.itip.ITipActionPerformerFactoryService;
import com.openexchange.chronos.itip.ITipAnalysis;
import com.openexchange.chronos.itip.ITipAnalyzerService;
import com.openexchange.chronos.itip.ITipAttributes;
import com.openexchange.chronos.itip.ITipChange;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.conversion.ConversionService;
import com.openexchange.conversion.Data;
import com.openexchange.conversion.DataArguments;
import com.openexchange.conversion.DataSource;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.tools.session.ServerSession;

/**
 *
 * {@link ActionPerformerAction}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 * @since v7.10.0
 */
public class ActionPerformerAction extends AbstractITipAction {

    private final RankingAwareNearRegistryServiceTracker<ITipActionPerformerFactoryService> factoryListing;

    public ActionPerformerAction(ServiceLookup services, RankingAwareNearRegistryServiceTracker<ITipAnalyzerService> analyzerListing, RankingAwareNearRegistryServiceTracker<ITipActionPerformerFactoryService> factoryListing) {
        super(services, analyzerListing);
        this.factoryListing = factoryListing;
    }

    @Override
    protected AJAXRequestResult process(List<ITipAnalysis> analysis, AJAXRequestData request, ServerSession session, TimeZone tz) throws JSONException, OXException {
        ITipAnalysis analysisToProcess = analysis.get(0);
        ITipActionPerformerFactoryService factory = getFactory();
        ITipAction action = ITipAction.valueOf(request.getParameter("action", String.class).toUpperCase());
        ITipAttributes attributes = new ITipAttributes();
        if (request.containsParameter("message")) {
            String message = request.getParameter("message", String.class);
            if (!message.trim().equals("")) {
                attributes.setConfirmationMessage(message);
            }
        }
        ITipActionPerformer performer = factory.getPerformer(action);
        CalendarSession calendarSession = initCalendarSession(services, session);

        // Parse for attachments
        attach(request, analysisToProcess);

        List<Event> list = performer.perform(request, action, analysisToProcess, calendarSession, attributes);
        return Utils.convertToResult(session, tz, list);
    }

    private static final EventField[] ATTACH = new EventField[] { EventField.ATTACHMENTS };

    /**
     * Adds the actual data to the attachments
     *
     * @param analysisToProcess The analysis already made
     * @param session The {@link CalendarSession}
     */
    private void attach(AJAXRequestData request, ITipAnalysis analysisToProcess) throws OXException {
        for (ITipChange change : analysisToProcess.getChanges()) {
            // Changed attachments? No diff means no original event, so we have a new event
            if (null == change.getDiff() || containsAttachmentChange(change)) {
                Event event = change.getNewEvent();
                if (null != event && event.containsAttachments() && 0 < event.getAttachments().size()) {
                    for (Iterator<Attachment> iterator = event.getAttachments().iterator(); iterator.hasNext();) {
                        Attachment attachment = iterator.next();
                        if (Strings.isEmpty(attachment.getUri())) {
                            continue;
                        }
                        ThresholdFileHolder attachmentData = optAttachmentData(request, getContentId(attachment.getUri()));
                        if (null == attachmentData) {
                            attachmentData = optAttachmentData(request, prepareUri(attachment.getUri()));
                            if (null == attachmentData) {
                                LOG.warn("Unable to find attachment with CID {}. Removing attachment from event.", attachment.getUri());
                                iterator.remove();
                                continue;
                            }
                        }
                        attachment.setData(attachmentData);
                        attachment.setChecksum(attachmentData.getMD5());
                        attachment.setUri(null);
                        if (Strings.isNotEmpty(attachmentData.getName())) {
                            attachment.setFilename(attachmentData.getName());
                        }
                        if (Strings.isNotEmpty(attachmentData.getContentType())) {
                            attachment.setFormatType(attachmentData.getContentType());
                        }
                    }
                }
            }
        }
    }

    private boolean containsAttachmentChange(ITipChange change) throws OXException {
        if (change.getDiff().containsAnyChangeOf(ATTACH)) {

            Event original = change.getDiff().getOriginal();
            Event update = change.getDiff().getUpdate();

            List<Attachment> originals = original.containsAttachments() && original.getAttachments() != null ? new LinkedList<>(original.getAttachments()) : new LinkedList<>();
            List<Attachment> updated = update.containsAttachments() && update.getAttachments() != null ? new LinkedList<>(update.getAttachments()) : new LinkedList<>();

            if (originals.size() != updated.size()) {
                return true;
            }

            Iterator<Attachment> iterator = originals.iterator();
            while (iterator.hasNext()) {
                Attachment attach = iterator.next();
                if (updated.stream().anyMatch(u -> null != attach.getUri() && prepareUri(attach.getUri()).equals(prepareUri(u.getUri())) || null != attach.getFilename() && attach.getFilename().equals(u.getFilename()))) {
                    iterator.remove();
                } else {
                    return true;
                }
            }
            // 'Remove' attachment diff
            // XXX diff still has AttachmentUpdates
            update.setAttachments(original.getAttachments());
        }
        return false;
    }

    private String prepareUri(String uri) {
        if (Strings.isNotEmpty(uri) && uri.startsWith("CID:")) {
            return uri.substring(4);
        }
        return uri;
    }

    public Collection<String> getActionNames() throws OXException {
        ITipActionPerformerFactoryService factory = getFactory();
        Collection<ITipAction> supportedActions = factory.getSupportedActions();
        List<String> actionNames = new ArrayList<String>(supportedActions.size());
        for (ITipAction action : supportedActions) {
            actionNames.add(action.name().toLowerCase());
        }

        return actionNames;
    }

    private ITipActionPerformerFactoryService getFactory() throws OXException {
        if (factoryListing == null) {
            throw ServiceExceptionCode.serviceUnavailable(ITipActionPerformerFactoryService.class);
        }
        List<ITipActionPerformerFactoryService> serviceList = factoryListing.getServiceList();
        if (serviceList == null || serviceList.isEmpty()) {
            throw ServiceExceptionCode.serviceUnavailable(ITipActionPerformerFactoryService.class);
        }
        ITipActionPerformerFactoryService service = serviceList.get(0);
        if (service == null) {
            throw ServiceExceptionCode.serviceUnavailable(ITipActionPerformerFactoryService.class);
        }
        return service;

    }

    /**
     * Attempts to retrieve data from a MIME attachment referenced by a specific content identifier and store it into a file holder.
     *
     * @param requestData The underlying request data providing the targeted e-mail message and session
     * @param contentId The content identifier of the attachment to retrieve
     * @return The attachment data loaded into a file holder, or <code>null</code> if not found
     */
    private ThresholdFileHolder optAttachmentData(AJAXRequestData requestData, String contentId) throws OXException {
        ConversionService conversionEngine = services.getServiceSafe(ConversionService.class);
        DataSource dataSource = conversionEngine.getDataSource("com.openexchange.mail.attachment");
        if (null == dataSource) {
            LOG.warn("Data source \"com.openexchange.mail.attachment\" not available. Unable to access mail attachment data.");
            return null;
        }

        ThresholdFileHolder fileHolder = null;
        InputStream inputStream = null;
        try {
            DataArguments dataArguments = getDataSource(requestData);
            dataArguments.put("com.openexchange.mail.conversion.cid", contentId);
            Data<InputStream> data = dataSource.getData(InputStream.class, dataArguments, requestData.getSession());
            if (null != data) {
                inputStream = data.getData();
                fileHolder = new ThresholdFileHolder();
                fileHolder.write(inputStream);
                if (null != data.getDataProperties()) {
                    fileHolder.setContentType(data.getDataProperties().get("com.openexchange.conversion.content-type"));
                    fileHolder.setName(data.getDataProperties().get("com.openexchange.conversion.name"));
                }
                ThresholdFileHolder retval = fileHolder;
                fileHolder = null;
                return retval;
            }
        } catch (OXException e) {
            if (e.equalsCode(49, "MSG")) {
                // Attachment not found
                return null;
            }
            throw e;
        } finally {
            Streams.close(inputStream, fileHolder);
        }

        return null;
    }

    /**
     * Converts a "cid" URL to its corresponding <code>Content-ID</code> message header,
     *
     * @param cidUrl The "cid" URL to convert
     * @return The corresponding contentId, or the passed value as-is if not possible
     */
    private static String getContentId(String cidUrl) {
        if (Strings.isEmpty(cidUrl)) {
            return cidUrl;
        }
        /*
         * https://tools.ietf.org/html/rfc2392#section-2:
         * A "cid" URL is converted to the corresponding Content-ID message header [MIME] by removing the "cid:" prefix, converting the
         * % encoded character to their equivalent US-ASCII characters, and enclosing the remaining parts with an angle bracket pair,
         * "<" and ">".
         */
        String contentId = cidUrl;
        if (contentId.toLowerCase().startsWith("cid:")) {
            contentId = contentId.substring(4);
        }
        try {
            contentId = URLDecoder.decode(contentId, Charsets.UTF_8_NAME);
        } catch (UnsupportedEncodingException e) {
            LOG.warn("Unexpected error decoding {}", contentId, e);
        }
        if (Strings.isEmpty(contentId)) {
            return contentId;
        }
        if ('<' != contentId.charAt(0)) {
            contentId = '<' + contentId;
        }
        if ('>' != contentId.charAt(contentId.length() - 1)) {
            contentId = contentId + '>';
        }
        return contentId;
    }

}
