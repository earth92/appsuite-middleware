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

package com.openexchange.mail.compose.json.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONValue;
import com.openexchange.ajax.requesthandler.AJAXActionService;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestData.StreamParams;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.annotation.NonNull;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.exception.OXException;
import com.openexchange.i18n.LocaleTools;
import com.openexchange.java.Strings;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.compose.Address;
import com.openexchange.mail.compose.Attachment;
import com.openexchange.mail.compose.Attachment.ContentDisposition;
import com.openexchange.mail.compose.AttachmentOrigin;
import com.openexchange.mail.compose.ClientToken;
import com.openexchange.mail.compose.CompositionSpaceId;
import com.openexchange.mail.compose.CompositionSpaceService;
import com.openexchange.mail.compose.CompositionSpaceServiceFactory;
import com.openexchange.mail.compose.CompositionSpaceServiceFactoryRegistry;
import com.openexchange.mail.compose.CompositionSpaces;
import com.openexchange.mail.compose.DefaultAttachment;
import com.openexchange.mail.compose.Message.ContentType;
import com.openexchange.mail.compose.Message.Priority;
import com.openexchange.mail.compose.MessageDescription;
import com.openexchange.mail.compose.Security;
import com.openexchange.mail.compose.SharedAttachmentsInfo;
import com.openexchange.mail.compose.UploadLimits;
import com.openexchange.mail.compose.json.MailComposeActionFactory;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link AbstractMailComposeAction} - Abstract mail compose action.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = MailComposeActionFactory.MODULE, type = RestrictedAction.Type.WRITE)
public abstract class AbstractMailComposeAction implements AJAXActionService {

    /**
     * The service look-up
     */
    protected final ServiceLookup services;

    /**
     * Initializes a new {@link AbstractMailComposeAction}.
     *
     * @param services The service look-up
     */
    protected AbstractMailComposeAction(ServiceLookup services) {
        super();
        this.services = services;
    }

    /**
     * Gets the composition space service with highest ranking.
     *
     * @param session The session
     * @return The composition space service
     * @throws OXException If composition space service cannot be returned
     */
    protected CompositionSpaceService getHighestRankedService(Session session) throws OXException {
        CompositionSpaceServiceFactoryRegistry registry = services.getServiceSafe(CompositionSpaceServiceFactoryRegistry.class);
        CompositionSpaceServiceFactory factory = registry.getHighestRankedFactoryFor(session);
        return factory.createServiceFor(session);
    }

    /**
     * Gets the composition space service for given service identifier.
     *
     * @param serviceId The service identifier
     * @param session The session
     * @return The composition space service
     * @throws OXException If composition space service cannot be returned
     */
    protected CompositionSpaceService getCompositionSpaceService(String serviceId, Session session) throws OXException {
        CompositionSpaceServiceFactoryRegistry registry = services.getServiceSafe(CompositionSpaceServiceFactoryRegistry.class);
        return registry.getFactoryFor(serviceId, session).createServiceFor(session);
    }

    /**
     * Gets the composition space services.
     *
     * @param serviceId The service identifier
     * @param session The session
     * @return The composition space service
     * @throws OXException If composition space service cannot be returned
     */
    protected List<CompositionSpaceService> getCompositionSpaceServices(Session session) throws OXException {
        CompositionSpaceServiceFactoryRegistry registry = services.getServiceSafe(CompositionSpaceServiceFactoryRegistry.class);
        List<CompositionSpaceServiceFactory> factories = registry.getFactoriesFor(session);
        if (factories.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompositionSpaceService> services = new ArrayList<>(factories.size());
        for (CompositionSpaceServiceFactory factory : factories) {
            services.add(factory.createServiceFor(session));
        }
        return services;
    }

    /**
     * Checks for present file uploads in the AJAX request and applies according size limitations.
     *
     * @param uploadLimits The upload limits for affected composition space
     * @param request The AJAX request
     * @return {@code true} if request contains file uploads
     * @throws OXException - If upload files cannot be processed
     */
    protected boolean hasUploads(UploadLimits uploadLimits, AJAXRequestData request) throws OXException {
        return request.hasUploads(uploadLimits.getPerAttachmentLimit(), uploadLimits.getPerRequestLimit(), StreamParams.streamed(false));
    }

    /**
     * Parses specified JSON representation of a message to given <code>MessageDescription</code> instance.
     *
     * @param jMessage The message's JSON representation
     * @param md The <code>MessageDescription</code> instance to parse to
     * @throws JSONException If a JSON error occurs
     * @throws OXException
     */
    protected static void parseJSONMessage(JSONObject jMessage, MessageDescription md) throws JSONException, OXException {
        {
            JSONArray jFrom = jMessage.optJSONArray("from");
            if (null != jFrom) {
                JSONArray jAddress = jFrom.optJSONArray(0);
                Optional<Address> optionalAddress = toAddress(null == jAddress ? jFrom : jAddress);
                if (optionalAddress.isPresent()) {
                    md.setFrom(optionalAddress.get());
                }
            }
        }

        {
            JSONArray jTo = jMessage.optJSONArray("to");
            if (null != jTo) {
                md.setTo(toAddresses(jTo));
            }
        }

        {
            JSONArray jCc = jMessage.optJSONArray("cc");
            if (null != jCc) {
                md.setCc(toAddresses(jCc));
            }
        }

        {
            JSONArray jBcc = jMessage.optJSONArray("bcc");
            if (null != jBcc) {
                md.setBcc(toAddresses(jBcc));
            }
        }

        {
            JSONArray jReplyTo = jMessage.optJSONArray("reply_to");
            if (null != jReplyTo) {
                JSONArray jAddress = jReplyTo.optJSONArray(0);
                Optional<Address> optionalAddress = toAddress(null == jAddress ? jReplyTo : jAddress);
                if (optionalAddress.isPresent()) {
                    md.setReplyTo(optionalAddress.get());
                }
            }
        }

        {
            String subject = jMessage.optString("subject", null);
            if (null != subject) {
                md.setSubject(subject);
            }
        }

        {
            String content = jMessage.optString("content", null);
            if (null != content) {
                md.setContent(content);
            }
        }

        {
            String contentType = jMessage.optString("contentType", null);
            if (null != contentType) {
                md.setContentType(ContentType.contentTypeFor(contentType));
            }
        }

        // Hm... Should we allow manually altering attachment references?
        //        {
        //            JSONArray jAttachments = jMessage.optJSONArray("attachments");
        //            if (null != jAttachments) {
        //                md.setAttachments(toAttachments(jAttachments, compositionSpaceId));
        //            }
        //        }

        // Meta must not be changed by clients

        {
            Object opt = jMessage.optRaw("requestReadReceipt");
            Boolean bool = JSONObject.booleanFor(opt);
            if (null != bool) {
                md.setRequestReadReceipt(bool.booleanValue());
            }
        }

        {
            String priority = jMessage.optString("priority", null);
            if (null != priority) {
                Priority p = Priority.priorityFor(priority);
                md.setPriority(p == null ? Priority.NORMAL : p);
            }
        }

        {
            JSONObject jSecurity = jMessage.optJSONObject("security");
            if (null != jSecurity) {
                md.setSecurity(toSecurity(jSecurity));
            }
        }

        {
            JSONObject jSharedAttachments = jMessage.optJSONObject("sharedAttachments");
            if (null != jSharedAttachments) {
                md.setsharedAttachmentsInfo(toSharedAttachmentsInfo(jSharedAttachments));
            }
        }

        {
            JSONObject jCustomHeaders = jMessage.optJSONObject("customHeaders");
            if (null != jCustomHeaders) {
                md.setCustomHeaders(toCustomHeaders(jCustomHeaders));
            }
        }

        {
            String clientToken = jMessage.optString("claim", null);
            if (null != clientToken) {
                try {
                    md.setClientToken(ClientToken.of(clientToken));
                } catch (IllegalArgumentException e) {
                    throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create("claim", clientToken);
                }
            }
        }
    }

    private static List<Address> toAddresses(JSONArray jAddresses) throws JSONException {
        int length = jAddresses.length();
        if (length <= 0) {
            return Collections.emptyList();
        }

        if (length == 1) {
            Object jAddress = jAddresses.get(0);
            Address address;
            if (jAddress instanceof JSONArray) {
                address = toAddress((JSONArray) jAddress).orElse(null);
            } else {
                address = JSONObject.NULL.equals(jAddress) ? null : new Address(null, jAddress.toString());
            }
            return address == null ? Collections.emptyList() : Collections.singletonList(address);
        }

        List<Address> addresses = new ArrayList<Address>(length);
        for (Object jAddress : jAddresses) {
            if (jAddress instanceof JSONArray) {
                Optional<Address> optionalAddress = toAddress((JSONArray) jAddress);
                if (optionalAddress.isPresent()) {
                    addresses.add(optionalAddress.get());
                }
            } else {
                if (false == JSONObject.NULL.equals(jAddress)) {
                    addresses.add(new Address(null, jAddress.toString()));
                }
            }
        }
        return addresses;
    }

    private static Optional<Address> toAddress(JSONArray jAddress) throws JSONException {
        if (jAddress == null) {
            return Optional.empty();
        }

        int length = jAddress.length();
        if (length <= 0) {
            return Optional.empty();
        }

        return Optional.of(length == 1 ? new Address(null, jAddress.getString(0)) : new Address(jAddress.optString(0, null), jAddress.getString(1)));
    }

    private static List<Attachment> toAttachments(JSONArray jAttachments, UUID compositionSpaceId) throws JSONException, OXException {
        List<Attachment> attachments = new ArrayList<Attachment>(jAttachments.length());
        for (Object jAttachment : jAttachments) {
            attachments.add(toAttachment((JSONObject) jAttachment, compositionSpaceId));
        }
        return attachments;
    }

    private static Attachment toAttachment(JSONObject jAttachment, UUID compositionSpaceId) throws JSONException, OXException {
        String sId = jAttachment.getString("id");
        UUID uuid = parseAttachmentId(sId);

        DefaultAttachment.Builder attachment = DefaultAttachment.builder(uuid);
        attachment.withCompositionSpaceId(compositionSpaceId);

        {
            String mimeType = jAttachment.optString("mimeType", null);
            if (null != mimeType) {
                attachment.withMimeType(mimeType);
            }
        }

        {
            String disposition = jAttachment.optString("contentDisposition", null);
            if (null != disposition) {
                attachment.withContentDisposition(ContentDisposition.dispositionFor(disposition));
            }
        }

        {
            String name = jAttachment.optString("name", null);
            if (null != name) {
                attachment.withName(name);
            }
        }

        {
            long size = jAttachment.optLong("size", -1L);
            if (size >= 0) {
                attachment.withSize(size);
            }
        }

        {
            String cid = jAttachment.optString("cid", null);
            if (null != cid) {
                attachment.withContentId(cid);
            }
        }

        {
            String origin = jAttachment.optString("origin", null);
            if (null != origin) {
                attachment.withOrigin(AttachmentOrigin.getOriginFor(origin));
            }
        }

        return attachment.build();
    }

    /**
     * Parses given JSON object to shared attachments information
     *
     * @param jSharedAttachments The JSON object to parse (must not be <code>null</code>)
     * @return The parsed shared attachments information
     */
    protected static SharedAttachmentsInfo toSharedAttachmentsInfo(JSONObject jSharedAttachments) {
        SharedAttachmentsInfo.Builder sharedAttachments = SharedAttachmentsInfo.builder();

        sharedAttachments.withEnabled(jSharedAttachments.optBoolean("enabled", false));

        {
            String language = jSharedAttachments.optString("language", null);
            if (Strings.isNotEmpty(language)) {
                sharedAttachments.withLanguage(LocaleTools.getLocale(language));
            }
        }

        sharedAttachments.withAutoDelete(jSharedAttachments.optBoolean("autodelete", false));

        {
            long expiryDate = jSharedAttachments.optLong("expiryDate", -1L);
            if (expiryDate >= 0) {
                sharedAttachments.withExpiryDate(new Date(expiryDate));
            }
        }

        {
            String password = jSharedAttachments.optString("password", null);
            if (Strings.isNotEmpty(password)) {
                sharedAttachments.withPassword(password);
            }
        }

        return sharedAttachments.build();
    }

    /**
     * Parses given JSON object to custom headers
     *
     * @param jCustomHeaders The JSON object to parse (must not be <code>null</code>)
     * @return The parsed custom headers
     */
    protected static Map<String, String> toCustomHeaders(JSONObject jCustomHeaders) {
        Map<String, String> customHeaders = new LinkedHashMap<>(jCustomHeaders.length());
        for (Map.Entry<String, Object> jCustomHeader : jCustomHeaders.entrySet()) {
            customHeaders.put(jCustomHeader.getKey(), jCustomHeader.getValue().toString());
        }
        return customHeaders;
    }

    /**
     * Parses given JSON object to security settings
     *
     * @param jSecurity The JSON object to parse (must not be <code>null</code>)
     * @return The parsed security settings
     */
    protected static Security toSecurity(JSONObject jSecurity) {
        return Security.builder()
            .withEncrypt(jSecurity.optBoolean("encrypt"))
            .withPgpInline(jSecurity.optBoolean("pgpInline"))
            .withSign(jSecurity.optBoolean("sign"))
            .withLanguage(getNonEmptyElseNull(jSecurity.optString("language", null)))
            .withMessage(getNonEmptyElseNull(jSecurity.optString("message", null)))
            .withPin(getNonEmptyElseNull(jSecurity.optString("pin", null)))
            .withMsgRef(getNonEmptyElseNull(jSecurity.optString("msgRef", null)))
            .withAuthToken(getNonEmptyElseNull(jSecurity.optString("authToken", null)))
            .build();
    }

    private static String getNonEmptyElseNull(String s) {
        return Strings.isEmpty(s) ? null : s;
    }

    /**
     * Requires a JSON content in given request's data.
     *
     * @param requestData The request data to read from
     * @return The JSON content
     * @throws OXException If JSON content cannot be returned
     */
    protected JSONValue requireJSONBody(AJAXRequestData requestData) throws OXException {
        Object data = requestData.getData();
        if (null == data) {
            throw AjaxExceptionCodes.MISSING_REQUEST_BODY.create();
        }
        JSONValue jBody = requestData.getData(JSONValue.class);
        if (null == jBody) {
            throw AjaxExceptionCodes.INVALID_REQUEST_BODY.create(JSONValue.class, data.getClass());
        }
        return (JSONValue) data;
    }

    /**
     * Gets the referenced mail from given request data.
     *
     * @param requestData The request data to extract from
     * @return The mail path for the referenced mail
     * @throws OXException If mail path for the referenced mail cannot be returned
     */
    protected MailPath requireReferencedMail(AJAXRequestData requestData) throws OXException {
        JSONValue jBody = requireJSONBody(requestData);
        try {
            if (jBody.isObject()) {
                JSONObject jMailPath = jBody.toObject();
                return new MailPath(jMailPath.getString("folderId"), jMailPath.getString("id"));
            }

            JSONObject jMailPath = jBody.toArray().getJSONObject(0);
            return new MailPath(jMailPath.getString("folderId"), jMailPath.getString("id"));
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets the referenced mails from given request data.
     *
     * @param requestData The request data to extract from
     * @return The mail paths for the referenced mail
     * @throws OXException If mail paths for the referenced mail cannot be returned
     */
    protected List<MailPath> requireReferencedMails(AJAXRequestData requestData) throws OXException {
        JSONValue jBody = requireJSONBody(requestData);
        try {
            if (jBody.isObject()) {
                JSONObject jMailPath = jBody.toObject();
                String folderId = jMailPath.getString("folderId");
                String mailId = jMailPath.getString("id");
                return Collections.singletonList(new MailPath(folderId, mailId));
            }

            JSONArray jMailPaths = jBody.toArray();
            int length = jMailPaths.length();
            List<MailPath> maiLPaths = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                JSONObject jMailPath = jMailPaths.getJSONObject(i);
                maiLPaths.add(new MailPath(jMailPath.getString("folderId"), jMailPath.getString("id")));
            }
            return maiLPaths;
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Gets a composition space's UUID from specified unformatted string.
     *
     * @param id The composition space identifier; e.g. <code>rdb://067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID
     * @throws OXException If passed string in invalid
     */
    protected static CompositionSpaceId parseCompositionSpaceId(String id) throws OXException {
        try {
            return new CompositionSpaceId(id);
        } catch (IllegalArgumentException e) {
            throw OXException.general("Invalid composition space identifier: " + id, e);
        }
    }

    /**
     * Gets an attachment's UUID from specified unformatted string.
     *
     * @param id The attachment identifier as an unformatted string; e.g. <code>067e61623b6f4ae2a1712470b63dff00</code>
     * @return The UUID
     * @throws OXException If passed string in invalid
     */
    protected static UUID parseAttachmentId(String id) throws OXException {
        return CompositionSpaces.parseAttachmentId(id);
    }

    /**
     * Gets the parsed {@code} claim request parameter. During {@code open} and {@code PATCH}, a {@link ClientToken}
     * can be claimed by a client to take over editing of a composition space.
     *
     * @param requestData The AJAX request
     * @return The token
     * @throws OXException If parameter value has invalid syntax
     */
    protected static @NonNull ClientToken getClaimedClientToken(AJAXRequestData requestData) throws OXException {
        return parseClientToken(requestData, "claim");
    }

    /**
     * Gets the parsed clientToken request parameter
     *
     * @param requestData The AJAX request
     * @return The token
     * @throws OXException If parameter value has invalid syntax
     */
    protected static @NonNull ClientToken getClientToken(AJAXRequestData requestData) throws OXException {
        return parseClientToken(requestData, "clientToken");
    }

    /**
     * Parses a {@link ClientToken} from given request using given parameter name
     *
     * @param requestData The AJAX request
     * @param param The parameter name
     * @return The token
     * @throws OXException If parameter value has invalid syntax
     */
    private static @NonNull ClientToken parseClientToken(AJAXRequestData requestData, String param) throws OXException {
        String sClientToken = requestData.getParameter(param);
        if (sClientToken == null) {
            return ClientToken.NONE;
        }

        try {
            return ClientToken.of(sClientToken);
        } catch (IllegalArgumentException e) {
            throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(param, sClientToken);
        }
    }

    private static final String CAPABILITY_GUARD = "guard";

    /**
     * Checks if session-associated user has <code>"guard"</code> capability enabled.
     *
     * @param session The session
     * @return <code>true</code> if <code>"guard"</code> capability is enabled; otherwise <code>false</code>
     * @throws OXException If check for capabilities fails
     */
    protected boolean hasGuardCapability(ServerSession session) throws OXException {
        CapabilityService optionalCapabilityService = services.getOptionalService(CapabilityService.class);
        return null == optionalCapabilityService ? false : optionalCapabilityService.getCapabilities(session).contains(CAPABILITY_GUARD);
    }

    /**
     * Checks if session-associated user has <b>no</b> <code>"guard"</code> capability enabled.
     *
     * @param session The session
     * @return <code>true</code> if <b>no</b> <code>"guard"</code> capability is enabled; otherwise <code>false</code>
     * @throws OXException If check for capabilities fails
     */
    protected boolean hasNoGuardCapability(ServerSession session) throws OXException {
        return hasGuardCapability(session) == false;
    }

    @Override
    public AJAXRequestResult perform(AJAXRequestData requestData, ServerSession session) throws OXException {
        if (!session.getUserPermissionBits().hasWebMail()) {
            throw AjaxExceptionCodes.NO_PERMISSION_FOR_MODULE.create("mail");
        }
        try {
            return doPerform(requestData, session);
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Performs given mail compose request.
     *
     * @param requestData The request data
     * @param session The session providing user information
     * @return The AJAX result
     * @throws OXException If performing request fails
     */
    protected abstract AJAXRequestResult doPerform(AJAXRequestData requestData, ServerSession session) throws OXException, JSONException;

}
