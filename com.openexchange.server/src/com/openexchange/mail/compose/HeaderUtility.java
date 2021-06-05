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

package com.openexchange.mail.compose;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.mail.internet.MimeUtility;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import com.openexchange.i18n.LocaleTools;
import com.openexchange.java.Strings;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.compose.Meta.MetaType;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.mime.utils.MimeMessageUtility;
import com.openexchange.mail.utils.MailPasswordUtil;

/**
 * {@link HeaderUtility} - Utility class to set/read headers used for composing a mail.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class HeaderUtility {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(HeaderUtility.class);
    }

    /** "X-OX-Shared-Attachments" */
    public static final String HEADER_X_OX_SHARED_ATTACHMENTS = MessageHeaders.HDR_X_OX_SHARED_ATTACHMENTS;

    /** "X-OX-Shared-Attachment-Reference" */
    public static final String HEADER_X_OX_SHARED_ATTACHMENT_REFERENCE = MessageHeaders.HDR_X_OX_SHARED_ATTACHMENT_REFERENCE;

    /** "X-OX-Shared-Folder-Reference" */
    public static final String HEADER_X_OX_SHARED_FOLDER_REFERENCE = MessageHeaders.HDR_X_OX_SHARED_FOLDER_REFERENCE;

    /** "X-OX-Security" */
    public static final String HEADER_X_OX_SECURITY = MessageHeaders.HDR_X_OX_SECURITY;

    /** "X-OX-Meta" */
    public static final String HEADER_X_OX_META = MessageHeaders.HDR_X_OX_META;

    /** "X-OX-Read-Receipt" */
    public static final String HEADER_X_OX_READ_RECEIPT = MessageHeaders.HDR_X_OX_READ_RECEIPT;

    /** "X-OX-Custom-Headers" */
    public static final String HEADER_X_OX_CUSTOM_HEADERS = MessageHeaders.HDR_X_OX_CUSTOM_HEADERS;

    /** "X-OX-Content-Type" */
    public static final String HEADER_X_OX_CONTENT_TYPE = MessageHeaders.HDR_X_OX_CONTENT_TYPE;

    /** "X-OX-Composition-Space-Id" */
    public static final String HEADER_X_OX_COMPOSITION_SPACE_ID = MessageHeaders.HDR_X_OX_COMPOSITION_SPACE_ID;

    /** {@value MessageHeaders#HDR_X_OX_CLIENT_TOKEN} */
    public static final String HEADER_X_OX_CLIENT_TOKEN = MessageHeaders.HDR_X_OX_CLIENT_TOKEN;

    /**
     * Initializes a new {@link HeaderUtility}.
     */
    private HeaderUtility() {
        super();
    }

    private static final String HEADER_PW = "open-xchange";

    /**
     * Encodes given header value
     *
     * @param used The number of already consumed characters in header line
     * @param raw The raw header value
     * @return The encoded header value
     */
    public static String encodeHeaderValue(int used, String raw) {
        if (null == raw) {
            return null;
        }

        try {
            return MimeMessageUtility.forceFold(used, MailPasswordUtil.encrypt(raw, HEADER_PW));
        } catch (GeneralSecurityException x) {
            LoggerHolder.LOG.debug("Failed to encode header value", x);
            return MimeMessageUtility.forceFold(used, raw);
        }
    }

    /**
     * Decodes given header value
     *
     * @param encoded The encoded header value
     * @return The decoded header value
     */
    public static String decodeHeaderValue(String encoded) {
        if (null == encoded) {
            return null;
        }

        try {
            return MailPasswordUtil.decrypt(MimeUtility.unfold(encoded), HEADER_PW);
        } catch (GeneralSecurityException x) {
            LoggerHolder.LOG.debug("Failed to decode header value", x);
            return MimeUtility.unfold(encoded);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Generates the header value for given shared folder reference.
     *
     * @param sharedFolderReference The shared folder reference
     * @return The resulting header value
     */
    public static String sharedFolderReference2HeaderValue(SharedFolderReference sharedFolderReference) {
        return new JSONObject(8).putSafe("folderId", sharedFolderReference.getFolderId()).toString();
    }

    /**
     * Parses given header value to appropriate shared folder reference.
     *
     * @param headerValue The header value to parse
     * @return The resulting shared folder reference
     */
    public static SharedFolderReference headerValue2SharedFolderReference(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return null;
        }

        try {
            JSONObject jSharedAttachment = new JSONObject(headerValue);
            String folderId = jSharedAttachment.optString("folderId", null);
            return SharedFolderReference.valueOf(folderId);
        } catch (JSONException e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to shared folder reference: {}", headerValue, e);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Generates the header value for given shared attachment reference.
     *
     * @param sharedAttachmentReference The shared attachment reference
     * @return The resulting header value
     */
    public static String sharedAttachmentReference2HeaderValue(SharedAttachmentReference sharedAttachmentReference) {
        return new JSONObject(8).putSafe("id", sharedAttachmentReference.getAttachmentId()).putSafe("folderId", sharedAttachmentReference.getFolderId()).toString();
    }

    /**
     * Parses given header value to appropriate shared attachment reference.
     *
     * @param headerValue The header value to parse
     * @return The resulting shared attachment reference
     */
    public static SharedAttachmentReference headerValue2SharedAttachmentReference(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return null;
        }

        try {
            JSONObject jSharedAttachment = new JSONObject(headerValue);
            String id = jSharedAttachment.optString("id", null);
            String folderId = jSharedAttachment.optString("folderId", null);
            return new SharedAttachmentReference(id, folderId);
        } catch (JSONException e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to shared attachment reference: {}", headerValue, e);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Generates the header value for given meta instance.
     *
     * @param meta The meta instance
     * @return The resulting header value
     */
    public static String meta2HeaderValue(Meta meta) {
        if (null == meta) {
            return new JSONObject(2).putSafe("type", Type.NEW.getId()).toString();
        }

        JSONObject jMeta = new JSONObject(8).putSafe("type", meta.getType().getId());
        {
            Date date = meta.getDate();
            if (null != date) {
                jMeta.putSafe("date", Long.valueOf(meta.getDate().getTime()));
            }
        }
        {
            MailPath replyFor = meta.getReplyFor();
            if (null != replyFor) {
                jMeta.putSafe("replyFor", replyFor.toString());
            }
        }
        {
            MailPath editFor = meta.getEditFor();
            if (null != editFor) {
                jMeta.putSafe("editFor", editFor.toString());
            }
        }
        {
            List<MailPath> forwardsFor = meta.getForwardsFor();
            if (null != forwardsFor) {
                JSONArray jForwardsFor = new JSONArray(forwardsFor.size());
                for (MailPath forwardFor : forwardsFor) {
                    jForwardsFor.put(forwardFor.toString());
                }
                jMeta.putSafe("forwardsFor", jForwardsFor);
            }
        }

        return jMeta.toString();
    }

    /**
     * Parses given header value to appropriate meta instance.
     *
     * @param headerValue The header value to parse
     * @return The resulting meta instance
     */
    public static Meta headerValue2Meta(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return Meta.META_NEW;
        }

        try {
            JSONObject jMeta = new JSONObject(headerValue);

            Meta.Builder meta = Meta.builder();
            meta.withType(MetaType.typeFor(jMeta.optString("type", Type.NEW.getId())));
            {
                long lDate = jMeta.optLong("date", -1L);
                meta.withDate(lDate < 0 ? null : new Date(lDate));
            }
            {
                String path = jMeta.optString("replyFor", null);
                meta.withReplyFor(Strings.isEmpty(path) ? null : new MailPath(path));
            }
            {
                String path = jMeta.optString("editFor", null);
                meta.withEditFor(Strings.isEmpty(path) ? null : new MailPath(path));
            }
            {
                JSONArray jPaths = jMeta.optJSONArray("forwardsFor");
                if (null != jPaths) {
                    List<MailPath> paths = new ArrayList<MailPath>(jPaths.length());
                    for (Object jPath : jPaths) {
                        paths.add(new MailPath(jPath.toString()));
                    }
                    meta.withForwardsFor(paths);
                }
            }
            return meta.build();
        } catch (Exception e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to meta information: {}", headerValue, e);
            return Meta.META_NEW;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Generates the header value for given custom headers.
     *
     * @param customHeaders The custom headers
     * @return The resulting header value
     */
    public static String customHeaders2HeaderValue(Map<String, String> customHeaders) {
        if (null == customHeaders) {
            return null;
        }

        JSONObject jCustomHeaders = new JSONObject(customHeaders.size());
        for (Map.Entry<String, String> customHeader : customHeaders.entrySet()) {
            jCustomHeaders.putSafe(customHeader.getKey(), customHeader.getValue());
        }
        return jCustomHeaders.toString();
    }

    /**
     * Parses given header value to appropriate custom headers.
     *
     * @param headerValue The header value
     * @return The resulting custom headers
     */
    public static Map<String, String> headerValue2CustomHeaders(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return null;
        }

        try {
            JSONObject jCustomHeaders = new JSONObject(headerValue);
            Map<String, String> customHeaders = new LinkedHashMap<>(jCustomHeaders.length());
            for (Map.Entry<String, Object> jCustomHeader : jCustomHeaders.entrySet()) {
                customHeaders.put(jCustomHeader.getKey(), jCustomHeader.getValue().toString());
            }
            return customHeaders;
        } catch (JSONException e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to custom headers: {}", headerValue, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private static final String JSON_SHARED_ATTACHMENTS_DISABLED = new JSONObject(4).putSafe("enabled", Boolean.FALSE).toString();

    /**
     * Generates the header value for given shared-attachment info instance.
     *
     * @param sharedAttachmentsInfo The shared-attachment info instance
     * @return The resulting header value
     */
    public static String sharedAttachments2HeaderValue(SharedAttachmentsInfo sharedAttachmentsInfo) {
        if (null == sharedAttachmentsInfo || sharedAttachmentsInfo.isDisabled()) {
            return JSON_SHARED_ATTACHMENTS_DISABLED;
        }

        return new JSONObject(8).putSafe("enabled", Boolean.valueOf(sharedAttachmentsInfo.isEnabled())).putSafe("language", sharedAttachmentsInfo.getLanguage() == null ? JSONObject.NULL : sharedAttachmentsInfo.getLanguage().toString())
            .putSafe("autoDelete", Boolean.valueOf(sharedAttachmentsInfo.isAutoDelete())).putSafe("expiryDate", sharedAttachmentsInfo.getExpiryDate() == null ? JSONObject.NULL : Long.valueOf(sharedAttachmentsInfo.getExpiryDate().getTime()))
            .putSafe("password", sharedAttachmentsInfo.getPassword() == null ? JSONObject.NULL : sharedAttachmentsInfo.getPassword()).toString();
    }

    /**
     * Parses given header value to appropriate shared-attachment info instance.
     *
     * @param headerValue The header value to parse
     * @return The resulting shared-attachment info instance
     */
    public static SharedAttachmentsInfo headerValue2SharedAttachments(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return SharedAttachmentsInfo.DISABLED;
        }

        try {
            JSONObject jSharedAttachments = new JSONObject(headerValue);

            SharedAttachmentsInfo.Builder sharedAttachments = SharedAttachmentsInfo.builder();
            sharedAttachments.withEnabled(jSharedAttachments.optBoolean("enabled", false));
            {
                String language = jSharedAttachments.optString("language", null);
                sharedAttachments.withLanguage(Strings.isEmpty(language) ? null : LocaleTools.getLocale(language));
            }
            sharedAttachments.withAutoDelete(jSharedAttachments.optBoolean("autoDelete", false));
            {
                long lDate = jSharedAttachments.optLong("expiryDate", -1L);
                sharedAttachments.withExpiryDate(lDate < 0 ? null : new Date(lDate));
            }
            sharedAttachments.withPassword(jSharedAttachments.optString("password", null));
            return sharedAttachments.build();
        } catch (JSONException e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to shared-attachments settings: {}", headerValue, e);
            return SharedAttachmentsInfo.DISABLED;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private static final String JSON_SECURITY_DISABLED = new JSONObject(4).putSafe("encrypt", Boolean.FALSE).putSafe("pgpInline", Boolean.FALSE).putSafe("sign", Boolean.FALSE).toString();

    /**
     * Generates the header value for given security instance.
     *
     * @param security The security instance
     * @return The resulting header value
     */
    public static String security2HeaderValue(Security security) {
        if (null == security || security.isDisabled()) {
            return JSON_SECURITY_DISABLED;
        }

        return new JSONObject(10)
            .putSafe("encrypt", Boolean.valueOf(security.isEncrypt()))
            .putSafe("pgpInline", Boolean.valueOf(security.isPgpInline()))
            .putSafe("sign", Boolean.valueOf(security.isSign()))
            .putSafe("language", security.getLanguage())
            .putSafe("message", security.getMessage())
            .putSafe("pin", security.getPin())
            .putSafe("msgRef", security.getMsgRef())
            .putSafe("authToken", security.getAuthToken())
            .toString();
    }

    /**
     * Parses given header value to appropriate security instance.
     *
     * @param headerValue The header value to parse
     * @return The resulting security instance
     */
    public static Security headerValue2Security(String headerValue) {
        if (Strings.isEmpty(headerValue)) {
            return Security.DISABLED;
        }

        try {
            JSONObject jSecurity = new JSONObject(headerValue);
            return Security.builder()
                .withEncrypt(jSecurity.optBoolean("encrypt"))
                .withPgpInline(jSecurity.optBoolean("pgpInline"))
                .withSign(jSecurity.optBoolean("sign"))
                .withLanguage(jSecurity.optString("language", null))
                .withMessage(jSecurity.optString("message", null))
                .withPin(jSecurity.optString("pin", null))
                .withMsgRef(jSecurity.optString("msgRef", null))
                .withAuthToken(jSecurity.optString("authToken", null))
                .build();
        } catch (JSONException e) {
            LoggerHolder.LOG.warn("Header value cannot be parsed to security settings: {}", headerValue, e);
            return Security.DISABLED;
        }
    }

}
