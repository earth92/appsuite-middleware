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

package com.openexchange.importexport.helpers;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.openexchange.ajax.helper.DownloadUtility;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.api2.TasksSQLInterface;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.provider.composition.IDBasedCalendarAccess;
import com.openexchange.chronos.service.CalendarService;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.chronos.service.EventID;
import com.openexchange.contact.ContactService;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.composition.FilenameValidationUtils;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.folderstorage.database.contentType.ContactsContentType;
import com.openexchange.folderstorage.database.contentType.TaskContentType;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.tasks.Task;
import com.openexchange.groupware.tasks.TasksSQLImpl;
import com.openexchange.i18n.tools.StringHelper;
import com.openexchange.importexport.osgi.ImportExportServices;
import com.openexchange.java.Strings;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link ExportFileNameCreator}
 *
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a>
 * @since v7.10
 */
public class ExportFileNameCreator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExportFileNameCreator.class);

    /**
     * Prevent instantiation.
     */
    private ExportFileNameCreator() {
        super();
    }

    /**
     * Creates a file name based on the folder
     *
     * @param session The session object
     * @param folder The folder to create the file name with
     * @param extension The extension of the file name
     * @return String The file name
     */
    public static String createFolderExportFileName(ServerSession session, String folder, String extension) {
        FolderService folderService = ImportExportServices.getFolderService();
        String prefix;
        try {
            UserizedFolder folderObj = folderService.getFolder(FolderStorage.REAL_TREE_ID, folder, session, null);
            prefix = folderObj.getLocalizedName(session.getUser().getLocale());
        } catch (OXException e) {
            LOG.debug("", e);
            prefix = getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME);
        }
        return validateFileName(prefix, extension, session);
    }

    /**
     * Creates a file name based on the a batch of folders and objects
     *
     * @param session The session object
     * @param batchIds The batchIds to create the file name with
     * @return String The file name
     */
    public static String createBatchExportFileName(ServerSession session, Map<String, List<String>> batchIds, String extension) {
        String prefix;
        Entry<String, List<String>> entry = batchIds.entrySet().iterator().next();
        if (batchIds.size() == 1) {
            //check for contacts of the same folder
            if (entry.getValue().size() > 1) {
                prefix = createBatchExportFileName(session, entry.getKey(), null);
            } else {
                //exactly one contact to export, file name equals contact name
                prefix = createBatchExportFileName(session, entry.getKey(), entry.getValue().get(0));
            }
        } else {
            //batch of contact ids from different folders, file name is set to a default
            FolderService folderService = ImportExportServices.getFolderService();
            try {
                UserizedFolder userizedFolder = folderService.getFolder(FolderStorage.REAL_TREE_ID, entry.getKey(), session, null);
                if (TaskContentType.getInstance().equals(userizedFolder.getContentType())) {
                    prefix = getLocalizedFileName(session, ExportDefaultFileNames.ICAL_TASKS_NAME);
                } else if (com.openexchange.folderstorage.database.contentType.CalendarContentType.getInstance().equals(userizedFolder.getContentType())) {
                    prefix = getLocalizedFileName(session, ExportDefaultFileNames.ICAL_APPOINTMENT_NAME);
                } else if (com.openexchange.folderstorage.calendar.contentType.CalendarContentType.getInstance().equals(userizedFolder.getContentType())) {
                    prefix = getLocalizedFileName(session, ExportDefaultFileNames.ICAL_EVENT_NAME);
                } else if (ContactsContentType.getInstance().equals(userizedFolder.getContentType())) {
                    prefix = getLocalizedFileName(session, ExportDefaultFileNames.CONTACTS_NAME);
                }else {
                    prefix = getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME);
                }
            } catch (OXException e) {
                LOG.debug("", e);
                prefix = getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME);
            }
        }
        return validateFileName(prefix, extension, session);
    }

    /**
     * Helper method for creating a file name based on the folder and the object
     *
     * @param session The session object
     * @param folder The folderId to create the file name with
     * @param batchId The batchId to create the file name with
     * @return String The file name
     */
    private static String createBatchExportFileName(ServerSession session, String folder, String batchId) {
        final StringBuilder sb = new StringBuilder();
        FolderService folderService = ImportExportServices.getFolderService();
        try {
            UserizedFolder folderObj = folderService.getFolder(FolderStorage.REAL_TREE_ID, folder, session, null);
            if (null == batchId || batchId.equals("")) {
                sb.append(getLocalizedFileName(session, folderObj.getLocalizedName(session.getUser().getLocale())));
            } else {
                if (ContactsContentType.getInstance().equals(folderObj.getContentType())) {
                    sb.append(createSingleContactName(session, folder, batchId));
                } else if (com.openexchange.folderstorage.database.contentType.CalendarContentType.getInstance().equals(folderObj.getContentType())) {
                    CalendarService calendarService = ImportExportServices.getCalendarService();
                    CalendarSession calendarSession = calendarService.init(session);
                    Event event = calendarService.getEvent(calendarSession, folder, new EventID(folder, batchId));
                    String title = event.getSummary();
                    if (Strings.isEmpty(title)) {
                        sb.append(getLocalizedFileName(session, ExportDefaultFileNames.ICAL_APPOINTMENT_NAME));
                    } else {
                        sb.append(title);
                    }
                } else if (com.openexchange.folderstorage.calendar.contentType.CalendarContentType.getInstance().equals(folderObj.getContentType())) {
                    IDBasedCalendarAccess calAccess = ImportExportServices.getIDBasedCalendarAccessFactory().createAccess(session);
                    Event event = calAccess.getEvent(new EventID(folder, batchId));
                    String title = event.getSummary();
                    if (Strings.isEmpty(title)) {
                        sb.append(getLocalizedFileName(session, ExportDefaultFileNames.ICAL_EVENT_NAME));
                    } else {
                        sb.append(title);
                    }
                } else if (TaskContentType.getInstance().equals(folderObj.getContentType())) {
                    TasksSQLInterface tasksSql = new TasksSQLImpl(session);
                    Task taskObj = tasksSql.getTaskById(Integer.parseInt(batchId), Integer.parseInt(folder));
                    String title = taskObj.getTitle();
                    if (Strings.isEmpty(title)) {
                        sb.append(getLocalizedFileName(session, ExportDefaultFileNames.ICAL_TASKS_NAME));
                    } else {
                        sb.append(title);
                    }
                } else {
                    sb.append(getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME));
                }
            }
        } catch (OXException e) {
            LOG.debug("", e);
            sb.append(getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME));
        }
        return sb.toString();
    }

    /**
     * Creates a localized string based on the file name
     *
     * @param session The session object
     * @param fileName The file name of the module
     * @return String The localized file name
     */
    private static String getLocalizedFileName(ServerSession session, String fileName) {
        return StringHelper.valueOf(session.getUser().getLocale()).getString(fileName);
    }

    /**
     * Creates a file name for a single contact
     *
     * @param session The session object
     * @param folder The folderId
     * @param batchId The objectId
     * @return String A file name for a single contact export
     * @throws OXException if contact is unavailable
     */
    private static String createSingleContactName(ServerSession session, String folder, String batchId) throws OXException {
        StringBuilder sb = new StringBuilder();
        ContactService contactService = ImportExportServices.getContactService();
        Contact contactObj = contactService.getContact(session, folder, batchId, null);
        if (contactObj.getMarkAsDistribtuionlist()) {
            String displayName = contactObj.getDisplayName();
            if (Strings.isEmpty(displayName)) {
                sb.append(getLocalizedFileName(session, ExportDefaultFileNames.CONTACTS_NAME));
            } else {
                sb.append(displayName);
            }
        } else {
            if (Strings.isEmpty(contactObj.getGivenName()) && Strings.isEmpty(contactObj.getSurName())) {
                sb.append(getLocalizedFileName(session, ExportDefaultFileNames.CONTACTS_NAME));
            } else if (Strings.isNotEmpty(contactObj.getGivenName()) && Strings.isNotEmpty(contactObj.getSurName())) {
                sb.append(contactObj.getGivenName() + " " + contactObj.getSurName());
            } else {
                if (Strings.isNotEmpty(contactObj.getGivenName()) && Strings.isEmpty(contactObj.getSurName())) {
                    sb.append(contactObj.getGivenName());
                } else if (Strings.isEmpty(contactObj.getGivenName()) && Strings.isNotEmpty(contactObj.getSurName())) {
                    sb.append(contactObj.getSurName());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Validates the file name
     *
     * @param prefix The prefix to use for file name
     * @param suffix The suffix to use for file name; e.g. <code>".vcf"</code>
     * @param session The session object
     * @param sb The {@link StringBuilder} instance which contains the file name
     * @param extension The file extension
     * @return String The validated file name
     */
    private static String validateFileName(String prefix, String suffix, ServerSession session) {
        try {
            String fileName = new StringBuilder(prefix).append('.').append(suffix).toString();
            FilenameValidationUtils.checkCharacters(fileName);
            FilenameValidationUtils.checkName(fileName);
            return fileName;
        } catch (OXException e) {
            LOG.debug("", e);
            return new StringBuilder(getLocalizedFileName(session, ExportDefaultFileNames.DEFAULT_NAME)).append('.').append(suffix).toString();
        }
    }

    /**
     * Appends the file name parameter
     *
     * @param requestData The ajax request data
     * @param fileName The file name to encode
     * @return String The validated and encoded file name parameter
     */
    public static String appendFileNameParameter(AJAXRequestData requestData, String fileName) {
        final StringBuilder sb = new StringBuilder();
        DownloadUtility.appendFilenameParameter(fileName, null == requestData ? "unknown" : requestData.getUserAgent(), sb);
        return sb.toString();
    }

}
