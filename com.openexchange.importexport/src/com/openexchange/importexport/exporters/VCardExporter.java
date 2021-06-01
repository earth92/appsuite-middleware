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

package com.openexchange.importexport.exporters;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.contact.ContactService;
import com.openexchange.contact.vcard.VCardExport;
import com.openexchange.contact.vcard.VCardService;
import com.openexchange.contact.vcard.storage.VCardStorageService;
import com.openexchange.contacts.json.mapping.ContactMapper;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.ContactExceptionCodes;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.container.CommonObject;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.DataObject;
import com.openexchange.groupware.container.FolderChildObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.userconfiguration.UserConfigurationStorage;
import com.openexchange.importexport.Format;
import com.openexchange.importexport.actions.exporter.ContactExportAction;
import com.openexchange.importexport.exceptions.ImportExportExceptionCodes;
import com.openexchange.importexport.helpers.DelayInitServletOutputStream;
import com.openexchange.importexport.helpers.SizedInputStream;
import com.openexchange.importexport.osgi.ImportExportServices;
import com.openexchange.java.Streams;
import com.openexchange.server.impl.EffectivePermission;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.session.ServerSession;

/**
 * @author <a href="mailto:sebastian.kauss@open-xchange.com">Sebastian Kauss</a>
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias 'Tierlieb' Prinz</a> (minor: changes to new interface)
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a> batch export
 */
public class VCardExporter extends AbstractExporter {

    protected final static int[] _contactFields = {
        DataObject.OBJECT_ID,
        DataObject.CREATED_BY,
        DataObject.CREATION_DATE,
        DataObject.LAST_MODIFIED,
        DataObject.MODIFIED_BY,
        FolderChildObject.FOLDER_ID,
        CommonObject.CATEGORIES,
        Contact.GIVEN_NAME,
        Contact.SUR_NAME,
        Contact.ANNIVERSARY,
        Contact.ASSISTANT_NAME,
        Contact.BIRTHDAY,
        Contact.BRANCHES,
        Contact.BUSINESS_CATEGORY,
        Contact.CELLULAR_TELEPHONE1,
        Contact.CELLULAR_TELEPHONE2,
        Contact.CITY_BUSINESS,
        Contact.CITY_HOME,
        Contact.CITY_OTHER,
        Contact.COLOR_LABEL,
        Contact.COMMERCIAL_REGISTER,
        Contact.COMPANY,
        Contact.COUNTRY_BUSINESS,
        Contact.COUNTRY_HOME,
        Contact.COUNTRY_OTHER,
        Contact.DEPARTMENT,
        Contact.DISPLAY_NAME,
        Contact.DISTRIBUTIONLIST,
        Contact.EMAIL1,
        Contact.EMAIL2,
        Contact.EMAIL3,
        Contact.EMPLOYEE_TYPE,
        Contact.FAX_BUSINESS,
        Contact.FAX_HOME,
        Contact.FAX_OTHER,
        Contact.INFO,
        Contact.INSTANT_MESSENGER1,
        Contact.INSTANT_MESSENGER2,
        Contact.IMAGE1,
        Contact.IMAGE1_CONTENT_TYPE,
        Contact.MANAGER_NAME,
        Contact.MARITAL_STATUS,
        Contact.MARK_AS_DISTRIBUTIONLIST,
        Contact.MIDDLE_NAME,
        Contact.NICKNAME,
        Contact.NOTE,
        Contact.NUMBER_OF_CHILDREN,
        Contact.NUMBER_OF_EMPLOYEE,
        Contact.POSITION,
        Contact.POSTAL_CODE_BUSINESS,
        Contact.POSTAL_CODE_HOME,
        Contact.POSTAL_CODE_OTHER,
        Contact.PRIVATE_FLAG,
        Contact.PROFESSION,
        Contact.ROOM_NUMBER,
        Contact.SALES_VOLUME,
        Contact.SPOUSE_NAME,
        Contact.STATE_BUSINESS,
        Contact.STATE_HOME,
        Contact.STATE_OTHER,
        Contact.STREET_BUSINESS,
        Contact.STREET_HOME,
        Contact.STREET_OTHER,
        Contact.SUFFIX,
        Contact.TAX_ID,
        Contact.TELEPHONE_ASSISTANT,
        Contact.TELEPHONE_BUSINESS1,
        Contact.TELEPHONE_BUSINESS2,
        Contact.TELEPHONE_CALLBACK,
        Contact.TELEPHONE_CAR,
        Contact.TELEPHONE_COMPANY,
        Contact.TELEPHONE_HOME1,
        Contact.TELEPHONE_HOME2,
        Contact.TELEPHONE_IP,
        Contact.TELEPHONE_ISDN,
        Contact.TELEPHONE_OTHER,
        Contact.TELEPHONE_PAGER,
        Contact.TELEPHONE_PRIMARY,
        Contact.TELEPHONE_RADIO,
        Contact.TELEPHONE_TELEX,
        Contact.TELEPHONE_TTYTDD,
        Contact.TITLE,
        Contact.URL,
        Contact.USERFIELD01,
        Contact.USERFIELD02,
        Contact.USERFIELD03,
        Contact.USERFIELD04,
        Contact.USERFIELD05,
        Contact.USERFIELD06,
        Contact.USERFIELD07,
        Contact.USERFIELD08,
        Contact.USERFIELD09,
        Contact.USERFIELD10,
        Contact.USERFIELD11,
        Contact.USERFIELD12,
        Contact.USERFIELD13,
        Contact.USERFIELD14,
        Contact.USERFIELD15,
        Contact.USERFIELD16,
        Contact.USERFIELD17,
        Contact.USERFIELD18,
        Contact.USERFIELD19,
        Contact.USERFIELD20,
        Contact.DEFAULT_ADDRESS,
        Contact.YOMI_FIRST_NAME,
        Contact.YOMI_LAST_NAME
    };

    /**
     * Initializes a new {@link VCardExporter}.
     */
    public VCardExporter() {
        super();
    }

    @Override
    public boolean canExport(final ServerSession session, final Format format, final String folder, final Map<String, Object> optionalParams) throws OXException {
        if (!format.equals(Format.VCARD)) {
            return false;
        }

        final int folderId = Integer.parseInt(folder);
        final FolderObject fo;
        try {
            fo = new OXFolderAccess(session.getContext()).getFolderObject(folderId);
        } catch (OXException e) {
            return false;
        }
        //check format of folder
        if (fo.getModule() == FolderObject.CONTACT) {
            if (!UserConfigurationStorage.getInstance().getUserConfigurationSafe(session.getUserId(), session.getContext()).hasContact()) {
                return false;
            }
        } else {
            return false;
        }
        //check read access to folder
        final EffectivePermission perm;
        try {
            perm = fo.getEffectiveUserPermission(session.getUserId(), UserConfigurationStorage.getInstance().getUserConfigurationSafe(session.getUserId(), session.getContext()));
        } catch (OXException e) {
            throw ImportExportExceptionCodes.NO_DATABASE_CONNECTION.create(e);
        } catch (RuntimeException e) {
            throw ImportExportExceptionCodes.SQL_PROBLEM.create(e, e.getMessage());
        }
        return perm.canReadAllObjects();
    }

    @Override
    public boolean canExportBatch(ServerSession session, Format format, Map.Entry<String, List<String>> batchIds, Map<String, Object> optionalParams) throws OXException {
        if (!canExport(session, format, batchIds.getKey(), optionalParams)) {
            return false;
        }
        for (String objectId : batchIds.getValue()) {
            try {
                Integer.parseInt(objectId);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public SizedInputStream exportFolderData(final ServerSession session, final Format format, final String folder, int[] fieldsToBeExported, final Map<String, Object> optionalParams) throws OXException {
        if (!canExport(session, format, folder, optionalParams)) {
            throw ImportExportExceptionCodes.CANNOT_EXPORT.create(folder, format);
        }

        boolean exportDistributionLists = null == optionalParams ? false : Boolean.parseBoolean(String.valueOf(optionalParams.get(ContactExportAction.PARAMETER_EXPORT_DLISTS)));

        AJAXRequestData requestData = (AJAXRequestData) (optionalParams == null ? null : optionalParams.get("__requestData"));
        if (null != requestData) {
            // Try to stream
            HttpServletResponse response = requestData.optHttpServletResponse();
            if (null != response) {
                Writer writer = null;
                try {
                    String fileName = getFolderExportFileName(session, folder, Format.VCARD.getExtension());
                    writer = setResponseHeadersAndInitWriter(response, fileName, optionalParams, requestData);
                    exportByFolder(session, folder, exportDistributionLists, fieldsToBeExported, writer);
                    return null;
                } finally {
                    Streams.close(writer);
                }
            }
        }

        OutputStream out = (OutputStream) (optionalParams == null ? null : optionalParams.get("__outputStream"));
        if (null != out) {
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(out, DEFAULT_CHARSET);
                exportByFolder(session, folder, exportDistributionLists, fieldsToBeExported, writer);
                return null;
            } finally {
                Streams.close(writer, out);
            }
        }

        // No streaming support possible
        ThresholdFileHolder sink = new ThresholdFileHolder();
        boolean error = true;
        try {
            exportByFolder(session, folder, exportDistributionLists, fieldsToBeExported, new OutputStreamWriter(sink.asOutputStream(), DEFAULT_CHARSET));
            SizedInputStream sizedIn = new SizedInputStream(sink.getClosingStream(), sink.getLength(), Format.VCARD);
            error = false;
            return sizedIn;
        } finally {
            if (error) {
                Streams.close(sink);
            }
        }
    }

    @Override
    public SizedInputStream exportBatchData(final ServerSession session, final Format format, final Map<String, List<String>> batchIds, final int[] fieldsToBeExported, final Map<String, Object> optionalParams) throws OXException {
        for (Map.Entry<String, List<String>> batchEntry : batchIds.entrySet()) {
            if (!canExportBatch(session, format, batchEntry, optionalParams)) {
                throw ImportExportExceptionCodes.CANNOT_EXPORT.create(batchEntry.getKey(), format);
            }
        }

        boolean exportDistributionLists = null == optionalParams ? false : Boolean.parseBoolean(String.valueOf(optionalParams.get(ContactExportAction.PARAMETER_EXPORT_DLISTS)));
        AJAXRequestData requestData = (AJAXRequestData) (optionalParams == null ? null : optionalParams.get("__requestData"));
        if (null != requestData) {
            // Try to stream
            HttpServletResponse response = requestData.optHttpServletResponse();
            if (null != response) {
                Writer writer = null;
                try {
                    String fileName = getBatchExportFileName(session, batchIds, Format.VCARD.getExtension());
                    writer = setResponseHeadersAndInitWriter(response, fileName, optionalParams, requestData);
                    exportByBatchIds(session, exportDistributionLists, fieldsToBeExported, writer, batchIds);
                    return null;
                } finally {
                    Streams.close(writer);
                }
            }
        }

        OutputStream out = (OutputStream) (optionalParams == null ? null : optionalParams.get("__outputStream"));
        if (null != out) {
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(out, DEFAULT_CHARSET);
                exportByBatchIds(session, exportDistributionLists, fieldsToBeExported, writer, batchIds);
                return null;
            } finally {
                Streams.close(writer, out);
            }
        }

        // No streaming support possible
        ThresholdFileHolder sink = new ThresholdFileHolder();
        boolean error = true;
        try {
            exportByBatchIds(session, exportDistributionLists, fieldsToBeExported, new OutputStreamWriter(sink.asOutputStream(), DEFAULT_CHARSET), batchIds);
            SizedInputStream sizedIn = new SizedInputStream(sink.getClosingStream(), sink.getLength(), Format.VCARD);
            error = false;
            return sizedIn;
        } finally {
            if (error) {
                Streams.close(sink);
            }
        }
    }

    /**
     * Sets the "Content-Type" as well as "Content-Disposition" response headers and returns initialized <code>Writer</code> instance.
     *
     * @param response The HTTP response to write headers to
     * @param fileName The file name to use
     * @param optionalParams The optional parameters
     * @param requestData The AJAX request data
     * @return The <code>Writer</code> instance
     */
    private Writer setResponseHeadersAndInitWriter(HttpServletResponse response, String fileName, Map<String, Object> optionalParams, AJAXRequestData requestData) {
        response.setHeader("Content-Type", isSaveToDisk(optionalParams) ? "application/octet-stream" : Format.VCARD.getMimeType() + "; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment" + appendFileNameParameter(requestData, fileName));
        Tools.removeCachingHeader(response);
        return new OutputStreamWriter(new DelayInitServletOutputStream(response), DEFAULT_CHARSET);
    }

    private void exportByBatchIds(ServerSession session, boolean exportDistributionLists, int[] fieldsToBeExported, Writer writer, Map<String, List<String>> batchIds) throws OXException {
        ContactField[] fields;
        if (fieldsToBeExported == null || fieldsToBeExported.length == 0) {
            fields = ContactMapper.getInstance().getFields(_contactFields);
            List<ContactField> tmp = new ArrayList<>();
            tmp.addAll(Arrays.asList(fields));
            tmp.add(ContactField.VCARD_ID);
            fields = tmp.toArray(new ContactField[tmp.size()]);
        } else {
            // In this case the original vCard must not be merged. Since the ContactMapper does not even map the VCARD_ID column it will not be considered when exporting.
            fields = ContactMapper.getInstance().getFields(fieldsToBeExported);
            fields = ensureContained(fields, ContactField.MARK_AS_DISTRIBUTIONLIST);
        }

        // Get required contact service
        ContactService contactService = ImportExportServices.getContactService();

        VCardStorageService vCardStorage = ImportExportServices.getVCardStorageService(session.getContextId());
        VCardService vCardService = ImportExportServices.getVCardService();

        // Export a single contact or a batch of contacts...
        for (Map.Entry<String, List<String>> batchEntry : batchIds.entrySet()) {
            String folder = batchEntry.getKey();
            List<String> contacts = batchEntry.getValue();
            String[] contactsId = new String[contacts.size()];
            contactsId = contacts.toArray(contactsId);
            SearchIterator<Contact> contactBatchIterator = contactService.getContacts(session, folder, contacts.toArray(contactsId));
            try {
                while (contactBatchIterator.hasNext()) {
                    Contact contact = contactBatchIterator.next();
                    try {
                        Contact fullContact = contactService.getContact(session, folder, Integer.toString(contact.getObjectID()), fields);
                        if (exportDistributionLists || (!fullContact.containsDistributionLists() && !fullContact.getMarkAsDistribtuionlist())) {
                            exportContact(session, fullContact, vCardService, vCardStorage, writer);
                        }
                    } catch (OXException e) {
                        if (!ContactExceptionCodes.CONTACT_NOT_FOUND.equals(e)) {
                            throw e;
                        }
                    }
                }
            } finally {
                SearchIterators.close(contactBatchIterator);
            }
        }
        doFlush(writer);
    }

    private static final ContactField[] FIELDS_ID = new ContactField[] { ContactField.OBJECT_ID };

    private void exportByFolder(ServerSession session, String folderId, boolean exportDistributionLists, int[] fieldsToBeExported, Writer writer) throws OXException {
        ContactField[] fields;
        if (fieldsToBeExported == null || fieldsToBeExported.length == 0) {
            fields = ContactMapper.getInstance().getFields(_contactFields);
            List<ContactField> tmp = new ArrayList<>();
            tmp.addAll(Arrays.asList(fields));
            tmp.add(ContactField.VCARD_ID);
            fields = tmp.toArray(new ContactField[tmp.size()]);
        } else {
            // In this case the original vCard must not be merged. Since the ContactMapper does not even map the VCARD_ID column it will not be considered when exporting.
            fields = ContactMapper.getInstance().getFields(fieldsToBeExported);
            fields = ensureContained(fields, ContactField.MARK_AS_DISTRIBUTIONLIST);
        }

        // Get required contact service
        ContactService contactService = ImportExportServices.getContactService();

        VCardStorageService vCardStorage = ImportExportServices.getVCardStorageService(session.getContextId());
        VCardService vCardService = ImportExportServices.getVCardService();

        // Export all contacts residing in given folder
        SearchIterator<Contact> searchIterator = contactService.getAllContacts(session, folderId, FIELDS_ID);
        try {
            while (searchIterator.hasNext()) {
                Contact contact = searchIterator.next();
                try {
                    Contact fullContact = contactService.getContact(session, folderId, Integer.toString(contact.getObjectID()), fields);
                    if (exportDistributionLists || (!fullContact.containsDistributionLists() && !fullContact.getMarkAsDistribtuionlist())) {
                        exportContact(session, fullContact, vCardService, vCardStorage, writer);
                    }
                } catch (OXException e) {
                    if (!ContactExceptionCodes.CONTACT_NOT_FOUND.equals(e)) {
                        throw e;
                    }
                }
            }
            doFlush(writer);
        } finally {
            SearchIterators.close(searchIterator);
        }
    }

    private void doFlush(Writer writer) throws OXException {
        if (null != writer) {
            try {
                writer.flush();
            } catch (IOException e) {
                throw ImportExportExceptionCodes.VCARD_CONVERSION_FAILED.create(e);
            }
        }
    }

    protected void exportContact(ServerSession session, Contact contactObj, VCardService optVCardService, VCardStorageService optVCardStorageService, Writer writer) throws OXException {
        InputStream originalVCard = null;
        Reader vcardReader = null;
        try {
            VCardStorageService vCardStorage = null == optVCardStorageService ? ImportExportServices.getVCardStorageService(session.getContextId()) : optVCardStorageService;
            if (vCardStorage != null && contactObj.getVCardId() != null) {
                originalVCard = vCardStorage.getVCard(contactObj.getVCardId(), session.getContextId());
            }

            VCardExport vCardExport = (null == optVCardService ? ImportExportServices.getVCardService() : optVCardService).exportContact(contactObj, originalVCard, null);
            Streams.close(originalVCard);
            originalVCard = null;

            vcardReader = new InputStreamReader(vCardExport.getClosingStream(), DEFAULT_CHARSET);
            int buflen = 65536;
            char[] cbuf = new char[buflen];
            for (int read; (read = vcardReader.read(cbuf, 0, buflen)) > 0;) {
                writer.write(cbuf, 0, read);
            }
        } catch (IOException e) {
            throw ImportExportExceptionCodes.VCARD_CONVERSION_FAILED.create(e);
        } finally {
            Streams.close(originalVCard, vcardReader);
        }
    }

    private ContactField[] ensureContained(ContactField[] fields, ContactField fieldToAdd) {
        for (ContactField field : fields) {
            if (field == fieldToAdd) {
                return fields;
            }
        }

        ContactField[] retval = new ContactField[fields.length + 1];
        System.arraycopy(fields, 0, retval, 0, fields.length);
        retval[fields.length] = fieldToAdd;
        return retval;
    }

}
