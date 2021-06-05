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

package com.openexchange.mail.mime;

/**
 * {@link MessageHeaders} - Various constants for MIME message headers.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class MessageHeaders {

    /**
     * Prevent instantiation
     */
    private MessageHeaders() {
        super();
    }

    /** "From" */
    public static final String HDR_FROM = "From".intern();

    public static final HeaderName FROM = HeaderName.valueOf(HDR_FROM);

    /** "To" */
    public static final String HDR_TO = "To".intern();

    public static final HeaderName TO = HeaderName.valueOf(HDR_TO);

    /** "Cc" */
    public static final String HDR_CC = "Cc".intern();

    public static final HeaderName CC = HeaderName.valueOf(HDR_CC);

    /** "Bcc" */
    public static final String HDR_BCC = "Bcc".intern();

    public static final HeaderName BCC = HeaderName.valueOf(HDR_BCC);

    /** "Date" */
    public static final String HDR_DATE = "Date".intern();

    public static final HeaderName DATE = HeaderName.valueOf(HDR_DATE);

    /** "Reply-To" */
    public static final String HDR_REPLY_TO = "Reply-To".intern();

    public static final HeaderName REPLY_TO = HeaderName.valueOf(HDR_REPLY_TO);

    /** "Subject" */
    public static final String HDR_SUBJECT = "Subject".intern();

    public static final HeaderName SUBJECT = HeaderName.valueOf(HDR_SUBJECT);

    /** "Message-ID" */
    public static final String HDR_MESSAGE_ID = "Message-ID".intern();

    public static final HeaderName MESSAGE_ID = HeaderName.valueOf(HDR_MESSAGE_ID);

    /** "In-Reply-To" */
    public static final String HDR_IN_REPLY_TO = "In-Reply-To".intern();

    public static final HeaderName IN_REPLY_TO = HeaderName.valueOf(HDR_IN_REPLY_TO);

    /** "References" */
    public static final String HDR_REFERENCES = "References".intern();

    public static final HeaderName REFERENCES = HeaderName.valueOf(HDR_REFERENCES);

    /** "X-Priority" */
    public static final String HDR_X_PRIORITY = "X-Priority".intern();

    public static final HeaderName X_PRIORITY = HeaderName.valueOf(HDR_X_PRIORITY);

    /** "Importance" */
    public static final String HDR_IMPORTANCE = "Importance".intern();

    public static final HeaderName IMPORTANCE = HeaderName.valueOf(HDR_IMPORTANCE);

    /** "Disposition-Notification-To" */
    public static final String HDR_DISP_NOT_TO = "Disposition-Notification-To".intern();

    public static final HeaderName DISP_NOT_TO = HeaderName.valueOf(HDR_DISP_NOT_TO);

    /** "Content-Disposition" */
    public static final String HDR_CONTENT_DISPOSITION = "Content-Disposition".intern();

    public static final HeaderName CONTENT_DISPOSITION = HeaderName.valueOf(HDR_CONTENT_DISPOSITION);

    /** "Content-Type" */
    public static final String HDR_CONTENT_TYPE = "Content-Type".intern();

    public static final HeaderName CONTENT_TYPE = HeaderName.valueOf(HDR_CONTENT_TYPE);

    /** "MIME-Version" */
    public static final String HDR_MIME_VERSION = "MIME-Version".intern();

    public static final HeaderName MIME_VERSION = HeaderName.valueOf(HDR_MIME_VERSION);

    public static final String HDR_DISP_TO = HDR_DISP_NOT_TO;

    public static final HeaderName DISP_TO = HeaderName.valueOf(HDR_DISP_TO);

    /** "Organization" */
    public static final String HDR_ORGANIZATION = "Organization".intern();

    public static final HeaderName ORGANIZATION = HeaderName.valueOf(HDR_ORGANIZATION);

    /** "X-Mailer" */
    public static final String HDR_X_MAILER = "X-Mailer".intern();

    public static final HeaderName X_MAILER = HeaderName.valueOf(HDR_X_MAILER);

    /** "X-Originating-Client" */
    public static final String HDR_X_ORIGINATING_CLIENT = "X-Originating-Client".intern();

    public static final HeaderName X_ORIGINATING_CLIENT = HeaderName.valueOf(HDR_X_ORIGINATING_CLIENT);

    /** "X-OXMsgref" */
    public static final String HDR_X_OXMSGREF = "X-OXMsgref".intern();

    public static final HeaderName X_OXMSGREF = HeaderName.valueOf(HDR_X_OXMSGREF);

    public static final String HDR_ADDR_DELIM = ",";

    /** "X-Spam-Flag" */
    public static final String HDR_X_SPAM_FLAG = "X-Spam-Flag".intern();

    public static final HeaderName X_SPAM_FLAG = HeaderName.valueOf(HDR_X_SPAM_FLAG);

    /** "Content-ID" */
    public static final String HDR_CONTENT_ID = "Content-ID".intern();

    public static final HeaderName CONTENT_ID = HeaderName.valueOf(HDR_CONTENT_ID);

    /** "Content-Transfer-Encoding" */
    public static final String HDR_CONTENT_TRANSFER_ENC = "Content-Transfer-Encoding".intern();

    public static final HeaderName CONTENT_TRANSFER_ENC = HeaderName.valueOf(HDR_CONTENT_TRANSFER_ENC);

    /** "Content-Disposition" */
    public static final String HDR_DISPOSITION = "Content-Disposition".intern();

    public static final HeaderName DISPOSITION = HeaderName.valueOf(HDR_DISPOSITION);

    /** "X-OX-Marker" */
    public static final String HDR_X_OX_MARKER = "X-OX-Marker".intern();

    public static final HeaderName X_OX_MARKER = HeaderName.valueOf(HDR_X_OX_MARKER);

    /** "Received" */
    public static final String HDR_RECEIVED = "Received".intern();

    public static final HeaderName RECEIVED = HeaderName.valueOf(HDR_RECEIVED);

    /** "Return-Path" */
    public static final String HDR_RETURN_PATH = "Return-Path".intern();

    public static final HeaderName RETURN_PATH = HeaderName.valueOf(HDR_RETURN_PATH);

    /** "X-OX-VCard-Attached" */
    public static final String HDR_X_OX_VCARD = "X-OX-VCard-Attached".intern();

    public static final HeaderName X_OX_VCARD = HeaderName.valueOf(HDR_X_OX_VCARD);

    /** "X-OX-Notification" */
    public static final String HDR_X_OX_NOTIFICATION = "X-OX-Notification".intern();

    public static final HeaderName X_OX_NOTIFICATION = HeaderName.valueOf(HDR_X_OX_NOTIFICATION);

    /** "X-Part-Id" */
    public static final String HDR_X_PART_ID = "X-Part-Id".intern();

    public static final HeaderName X_PART_ID = HeaderName.valueOf(HDR_X_PART_ID);

    /** "Authentication-Results" */
    public static final String HDR_AUTHENTICATION_RESULTS = "Authentication-Results".intern();

    public static final HeaderName AUTHENTICATION_RESULTS = HeaderName.valueOf(HDR_AUTHENTICATION_RESULTS);

    /** "X-Open-Xchange-Share-URL" */
    public static final String HDR_X_OPEN_XCHANGE_SHARE_URL = "X-Open-Xchange-Share-URL";

    public static final HeaderName X_OPEN_XCHANGE_SHARE_URL = HeaderName.valueOf(HDR_X_OPEN_XCHANGE_SHARE_URL);

    /** "X-Open-Xchange-Share-Reference" */
    public static final String HDR_X_OPEN_XCHANGE_SHARE_REFERENCE = "X-Open-Xchange-Share-Reference";

    public static final HeaderName X_OPEN_XCHANGE_SHARE_REFERENCE = HeaderName.valueOf(HDR_X_OPEN_XCHANGE_SHARE_REFERENCE);

    /** "X-Open-Xchange-Share-Type" */
    public static final String HDR_X_OPEN_XCHANGE_SHARE_TYPE = "X-Open-Xchange-Share-Type";

    public static final HeaderName X_OPEN_XCHANGE_SHARE_TYPE = HeaderName.valueOf(HDR_X_OPEN_XCHANGE_SHARE_TYPE);

    /** "X-OX-Shared-Attachments" */
    public static final String HDR_X_OX_SHARED_ATTACHMENTS = "X-OX-Shared-Attachments";

    public static final HeaderName X_OX_SHARED_ATTACHMENTS = HeaderName.valueOf(HDR_X_OX_SHARED_ATTACHMENTS);

    /** "X-OX-Shared-Attachment-Reference" */
    public static final String HDR_X_OX_SHARED_ATTACHMENT_REFERENCE = "X-OX-Shared-Attachment-Reference";

    public static final HeaderName X_OX_SHARED_ATTACHMENT_REFERENCE = HeaderName.valueOf(HDR_X_OX_SHARED_ATTACHMENT_REFERENCE);

    /** "X-OX-Shared-Folder-Reference" */
    public static final String HDR_X_OX_SHARED_FOLDER_REFERENCE = "X-OX-Shared-Folder-Reference";

    public static final HeaderName X_OX_SHARED_FOLDER_REFERENCE = HeaderName.valueOf(HDR_X_OX_SHARED_FOLDER_REFERENCE);

    /** "X-OX-Security" */
    public static final String HDR_X_OX_SECURITY = "X-OX-Security";

    public static final HeaderName X_OX_SECURITY = HeaderName.valueOf(HDR_X_OX_SECURITY);

    /** "X-OX-Meta" */
    public static final String HDR_X_OX_META = "X-OX-Meta";

    public static final HeaderName X_OX_META = HeaderName.valueOf(HDR_X_OX_META);

    /** "X-OX-Read-Receipt" */
    public static final String HDR_X_OX_READ_RECEIPT = "X-OX-Read-Receipt";

    public static final HeaderName X_OX_READ_RECEIPT = HeaderName.valueOf(HDR_X_OX_READ_RECEIPT);

    /** "X-OX-Custom-Headers" */
    public static final String HDR_X_OX_CUSTOM_HEADERS = "X-OX-Custom-Headers";

    public static final HeaderName X_OX_CUSTOM_HEADERS = HeaderName.valueOf(HDR_X_OX_CUSTOM_HEADERS);

    /**
     * "X-OX-NoReply-Personal"
     * <p>
     * The name of the special MIME header advertising a possible personal for the no-reply address.
     */
    public static final String HDR_X_OX_NO_REPLY_PERSONAL = "X-OX-NoReply-Personal";

    public static final HeaderName X_OX_NO_REPLY_PERSONAL = HeaderName.valueOf(HDR_X_OX_NO_REPLY_PERSONAL);

    /** "X-OX-Content-Type" */
    public static final String HDR_X_OX_CONTENT_TYPE = "X-OX-Content-Type";

    public static final HeaderName X_OX_CONTENT_TYPE = HeaderName.valueOf(HDR_X_OX_CONTENT_TYPE);

    /** {@value #HDR_X_OX_COMPOSITION_SPACE_ID} */
    public static final String HDR_X_OX_COMPOSITION_SPACE_ID = "X-OX-Composition-Space-Id";

    public static final HeaderName X_OX_COMPOSITION_SPACE_ID = HeaderName.valueOf(HDR_X_OX_COMPOSITION_SPACE_ID);

    /** "X-OX-Attachment-Origin" */
    public static final String HDR_X_OX_ATTACHMENT_ORIGIN = "X-OX-Attachment-Origin";

    public static final HeaderName X_OX_ATTACHMENT_ORIGIN = HeaderName.valueOf(HDR_X_OX_ATTACHMENT_ORIGIN);

    /** "Sender" */
    public static final String HDR_SENDER = "Sender";

    public static final HeaderName SENDER = HeaderName.valueOf(HDR_SENDER);

    /** "X-OX-Client-Token" */
    public static final String HDR_X_OX_CLIENT_TOKEN = "X-OX-Client-Token";

    public static final HeaderName X_OX_CLIENT_TOKEN = HeaderName.valueOf(HDR_X_OX_CLIENT_TOKEN);

}
