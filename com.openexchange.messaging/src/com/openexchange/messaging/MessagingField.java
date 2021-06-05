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

package com.openexchange.messaging;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import com.google.common.collect.ImmutableMap;
import com.openexchange.exception.OXException;
import com.openexchange.messaging.MessagingHeader.KnownHeader;

/**
 * {@link MessagingField}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since Open-Xchange v6.16
 */
public enum MessagingField {

    /**
     * The implementation-specific unique message ID<br>
     * <b>[low cost]</b>
     */
    ID("id"),
    /**
     * The folder ID or fullname<br>
     * <b>[low cost]</b>
     */
    FOLDER_ID("folder"),
    /**
     * The Content-Type; includes whether message contains attachments or not<br>
     * <b>[low cost]</b>
     */
    CONTENT_TYPE("contentType"),
    /**
     * From<br>
     * <b>[low cost]</b>
     */
    FROM("from"),
    /**
     * To<br>
     * <b>[low cost]</b>
     */
    TO("to"),
    /**
     * Cc<br>
     * <b>[low cost]</b>
     */
    CC("cc"),
    /**
     * Bcc<br>
     * <b>[low cost]</b>
     */
    BCC("bcc"),
    /**
     * Subject<br>
     * <b>[low cost]</b>
     */
    SUBJECT("subject"),
    /**
     * Size<br>
     * <b>[low cost]</b>
     */
    SIZE("size"),
    /**
     * Sent date corresponds to <code>Date</code> header<br>
     * <b>[low cost]</b>
     */
    SENT_DATE("sentDate"),
    /**
     * Received date represent the internal mail server's timestamp on arrival<br>
     * <b>[low cost]</b>
     */
    RECEIVED_DATE("receivedDate"),
    /**
     * Flags<br>
     * <b>[low cost]</b>
     */
    FLAGS("flags"),
    /**
     * Thread level<br>
     * <b>[low cost]</b>
     */
    THREAD_LEVEL("threadLevel"),
    /**
     * Email address in <code>Disposition-Notification-To</code> header<br>
     * <b>[low cost]</b>
     */
    DISPOSITION_NOTIFICATION_TO("dispositionNotificationTo"),
    /**
     * Integer value of <code>X-Priority</code> header<br>
     * <b>[low cost]</b>
     */
    PRIORITY("priority"),
    /**
     * Color Label<br>
     * <b>[low cost]</b>
     */
    COLOR_LABEL("colorLabel"),
    /**
     * Account name<br>
     * <b>[low cost]</b>
     */
    ACCOUNT_NAME("accountName"),
    /**
     * Picture url<br>
     * <b>[low cost]</b>
     */
     PICTURE("picture"),
    /**
     * To peek the mail body (\Seen flag is left unchanged)<br>
     * <b>[high cost]</b>
     */
    BODY("body"),
    /**
     * To fetch all message headers<br>
     * <b>[high cost]</b>
     */
    HEADERS("headers"),
    /**
     * To fully pre-fill mail incl. headers and peeked body (\Seen flag is left unchanged)<br>
     * <b>[high cost]</b>
     */
    FULL("full"),
    /**
     * URL<br>
     * <b>[low cost]</b>
     */
    URL("url");

    private final String name;

    private MessagingField(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * All low cost fields
     */
    public static final MessagingField[] FIELDS_LOW_COST =
        {
            ID, FOLDER_ID, CONTENT_TYPE, FROM, TO, CC, BCC, SUBJECT, SIZE, SENT_DATE, RECEIVED_DATE, FLAGS, THREAD_LEVEL,
            DISPOSITION_NOTIFICATION_TO, PRIORITY, COLOR_LABEL, PICTURE };

    /**
     * All fields except {@link #BODY} and {@link #FULL}
     */
    public static final MessagingField[] FIELDS_WO_BODY =
        {
            ID, FOLDER_ID, CONTENT_TYPE, FROM, TO, CC, BCC, SUBJECT, SIZE, SENT_DATE, RECEIVED_DATE, FLAGS, THREAD_LEVEL,
            DISPOSITION_NOTIFICATION_TO, PRIORITY, COLOR_LABEL, HEADERS };

    private static final Map<String, MessagingField> FIELDS_MAP;

    static {
        final Map<String, MessagingField> m = new HashMap<String, MessagingField>(32);
        final MessagingField[] fields = MessagingField.values();
        for (final MessagingField field : fields) {
            m.put(field.name, field);
        }
        FIELDS_MAP = ImmutableMap.copyOf(m);
    }

    private static final MessagingField[] EMPTY_FIELDS = new MessagingField[0];

    /**
     * Creates an array of {@link MessagingField} corresponding to given names.
     * <p>
     * This is just a convenience method that invokes {@link #getField(String)} for every name.
     *
     * @see #getField(String)
     * @param names The names
     * @return The array of {@link MessagingField} corresponding to given names
     */
    public static final MessagingField[] getFields(final String[] names) {
        if ((names == null) || (names.length == 0)) {
            return EMPTY_FIELDS;
        }
        final MessagingField[] retval = new MessagingField[names.length];
        for (int i = 0; i < names.length; i++) {
            retval[i] = getField(names[i]);
        }
        return retval;
    }

    /**
     * Maps specified name to a field.
     *
     * @param name The field name
     * @return The mapped {@link MessagingField} or <code>null</code> if no corresponding mail field could be found
     */
    public static MessagingField getField(final String name) {
        return FIELDS_MAP.get(name);
    }

    public Object doSwitch(final MessagingMessageSwitcher switcher, final Object...args) throws OXException {
        switch (this) {
        case ID : return switcher.id(args);
        case FOLDER_ID : return switcher.folderId(args);
        case CONTENT_TYPE : return switcher.contentType(args);
        case FROM : return switcher.from(args);
        case TO : return switcher.to(args);
        case CC : return switcher.cc(args);
        case BCC : return switcher.bcc(args);
        case SUBJECT : return switcher.subject(args);
        case SIZE : return switcher.size(args);
        case SENT_DATE : return switcher.sentDate(args);
        case RECEIVED_DATE : return switcher.receivedDate(args);
        case FLAGS : return switcher.flags(args);
        case THREAD_LEVEL : return switcher.threadLevel(args);
        case DISPOSITION_NOTIFICATION_TO : return switcher.dispositionNotificationTo(args);
        case PRIORITY : return switcher.priority(args);
        case COLOR_LABEL : return switcher.colorLabel(args);
        case ACCOUNT_NAME : return switcher.accountName(args);
        case PICTURE: return switcher.picture(args);
        case BODY : return switcher.body(args);
        case HEADERS : return switcher.headers(args);
        case FULL : return switcher.full(args);
        case URL : return switcher.url(args);
        }
        throw new IllegalArgumentException("Don't know how to handle "+this);
    }

    private static final Map<MessagingField, KnownHeader> equivalentHeaders = new EnumMap<MessagingField, KnownHeader>(MessagingField.class);


    /**
     * Maps a MessagingField to a MessagingHeader
     * @return the MessagingHeader this field is associated with
     */
    public KnownHeader getEquivalentHeader() {
        return equivalentHeaders.get(this);
    }

    public static void initHeaders() {
        for (final KnownHeader header : KnownHeader.values()) {
            if (null != header.getEquivalentField()) {
                equivalentHeaders.put(header.getEquivalentField(), header);
            }
        }
    }

}
