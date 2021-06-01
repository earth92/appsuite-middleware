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

package com.openexchange.groupware.contact.datasource;

import static com.openexchange.ajax.AJAXServlet.PARAMETER_FOLDERID;
import static com.openexchange.ajax.AJAXServlet.PARAMETER_ID;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.contact.ContactService;
import com.openexchange.contact.vcard.VCardUtil;
import com.openexchange.conversion.Data;
import com.openexchange.conversion.DataArguments;
import com.openexchange.conversion.DataExceptionCodes;
import com.openexchange.conversion.DataProperties;
import com.openexchange.conversion.DataSource;
import com.openexchange.conversion.SimpleData;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.tools.stream.UnsynchronizedByteArrayInputStream;
import com.openexchange.tools.stream.UnsynchronizedByteArrayOutputStream;

/**
 * {@link ContactDataSource} - A data source for contacts.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ContactDataSource implements DataSource {

    private static final Class<?>[] TYPES = { InputStream.class, byte[].class };
    private static final String[] ARGS = { "com.openexchange.groupware.contact.pairs" };
    private final ServiceLookup services;

    /**
     * Initializes a new {@link ContactDataSource}
     */
    public ContactDataSource(ServiceLookup services) {
        this.services = services;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D> Data<D> getData(final Class<? extends D> type, final DataArguments dataArguments, final Session session) throws OXException {
        if (!InputStream.class.equals(type) && !byte[].class.equals(type)) {
            throw DataExceptionCodes.TYPE_NOT_SUPPORTED.create(type.getName());
        }
        /*
         * Check arguments
         */
        final int[] folderIds;
        final int[] objectIds;
        final int len;
        {
            final JSONArray pairsArray;
            try {
                pairsArray = new JSONArray(dataArguments.get(ARGS[0]));
                len = pairsArray.length();
                if (len == 0) {
                    throw DataExceptionCodes.MISSING_ARGUMENT.create(ARGS[0]);
                }
                folderIds = new int[len];
                objectIds = new int[len];
                for (int i = 0; i < len; i++) {
                    final JSONObject pairObject = pairsArray.getJSONObject(i);
                    folderIds[i] = pairObject.getInt(PARAMETER_FOLDERID);
                    objectIds[i] = pairObject.getInt(PARAMETER_ID);
                }
            } catch (JSONException e) {
                throw DataExceptionCodes.INVALID_ARGUMENT.create(e, ARGS[0], dataArguments.get(ARGS[0]));
            }
        }
        /*
         * Get contact
         */
        final Contact[] contacts = new Contact[len];
        {
            final ContactService contactService = services.getServiceSafe(ContactService.class);
            for (int i = 0; i < len; i++) {
            	contacts[i] = contactService.getContact(session, Integer.toString(folderIds[i]), Integer.toString(objectIds[i]));
            }
        }
        /*
         * Create necessary objects
         */
        final ByteArrayOutputStream sink = new UnsynchronizedByteArrayOutputStream(len << 12);
        for (final Contact contact : contacts) {
            VCardUtil.exportContact(contact, session, sink);
        }
        /*
         * Return data
         */
        final DataProperties properties = new DataProperties();
        properties.put(DataProperties.PROPERTY_CHARSET, "UTF-8");
        properties.put(DataProperties.PROPERTY_VERSION, "3.0");
        properties.put(DataProperties.PROPERTY_CONTENT_TYPE, "text/vcard");
        final String displayName = contacts.length == 1 ? contacts[0].getDisplayName() : null;
        properties.put(DataProperties.PROPERTY_NAME, displayName == null ? "vcard.vcf" : new StringBuilder(
            displayName.replaceAll(" +", "_")).append(".vcf").toString());
        final byte[] vcardBytes = sink.toByteArray();
        properties.put(DataProperties.PROPERTY_SIZE, String.valueOf(vcardBytes.length));
        return new SimpleData<D>(
            (D) (InputStream.class.equals(type) ? new UnsynchronizedByteArrayInputStream(vcardBytes) : vcardBytes),
            properties);
    }

    @Override
    public String[] getRequiredArguments() {
        return ARGS;
    }

    @Override
    public Class<?>[] getTypes() {
        return TYPES;
    }

}
