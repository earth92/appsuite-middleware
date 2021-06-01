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

package com.openexchange.folder.json.actions;

import java.util.Date;
import org.json.JSONArray;
import org.json.JSONException;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.exception.OXException;
import com.openexchange.folder.json.Constants;
import com.openexchange.folder.json.FolderField;
import com.openexchange.folder.json.services.ServiceRegistry;
import com.openexchange.folder.json.writer.FolderWriter;
import com.openexchange.folderstorage.ContentType;
import com.openexchange.folderstorage.ContentTypeDiscoveryService;
import com.openexchange.folderstorage.FolderResponse;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link UpdatesAction} - Maps the action to a UPDATES action.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction()
public final class UpdatesAction extends AbstractFolderAction {

    public static final String ACTION = AJAXServlet.ACTION_UPDATES;

    /**
     * Initializes a new {@link UpdatesAction}.
     */
    public UpdatesAction() {
        super();
    }

    @Override
    protected AJAXRequestResult doPerform(final AJAXRequestData request, final ServerSession session) throws OXException {
        /*
         * Parse parameters
         */
        String treeId = request.getParameter("tree");
        if (null == treeId) {
            /*
             * Fallback to default tree identifier
             */
            treeId = getDefaultTreeIdentifier();
        }
        final Date timestamp;
        {
            final String timestampStr = request.getParameter("timestamp");
            if (null == timestampStr) {
                throw AjaxExceptionCodes.MISSING_PARAMETER.create("timestamp");
            }
            try {
                timestamp = new Date(Long.parseLong(timestampStr));
            } catch (NumberFormatException e) {
                throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create("timestamp", timestampStr);
            }
        }
        final int[] columns = parseIntArrayParameter(AJAXServlet.PARAMETER_COLUMNS, request);
        final boolean ignoreDeleted = "deleted".equalsIgnoreCase(request.getParameter(AJAXServlet.PARAMETER_IGNORE));
        final boolean includeMail;
        {
            final String parameter = request.getParameter("mail");
            includeMail = "1".equals(parameter) || Boolean.parseBoolean(parameter);
        }
        /*
         * Request subfolders from folder service
         */
        final FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
        final FolderResponse<UserizedFolder[][]> resultObject =
            folderService.getUpdates(
                treeId,
                timestamp,
                ignoreDeleted,
                includeMail ? new ContentType[] { ServiceRegistry.getInstance().getService(ContentTypeDiscoveryService.class).getByString(
                    "mail") } : null,
                    session,
                getDecorator(request));
        /*
         * Determine last-modified time stamp
         */
        final UserizedFolder[][] result = resultObject.getResponse();
        long lastModified = timestamp.getTime();
        for (final UserizedFolder userizedFolder : result[0]) {
            final Date modified = userizedFolder.getLastModifiedUTC();
            if (modified != null) {
                final long time = modified.getTime();
                lastModified = ((lastModified >= time) ? lastModified : time);
            }
        }
        for (final UserizedFolder userizedFolder : result[1]) {
            final Date modified = userizedFolder.getLastModifiedUTC();
            if (modified != null) {
                final long time = modified.getTime();
                lastModified = ((lastModified >= time) ? lastModified : time);
            }
        }

        /*
         * Write subfolders as JSON arrays to JSON array
         */
        final JSONArray resultArray = FolderWriter.writeMultiple2Array(request, columns, result[0], Constants.ADDITIONAL_FOLDER_FIELD_LIST);

        try {
            final JSONArray jsonArray2 =
                FolderWriter.writeMultiple2Array(
                    request,
                    new int[] { FolderField.ID.getColumn() },
                    result[1],
                    Constants.ADDITIONAL_FOLDER_FIELD_LIST);
            final int len = jsonArray2.length();
            for (int i = 0; i < len; i++) {
                resultArray.put(jsonArray2.getJSONArray(i).get(0));
            }
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
        /*
         * Return appropriate result
         */
        return new AJAXRequestResult(resultArray, 0 == lastModified ? null : new Date(lastModified)).addWarnings(resultObject.getWarnings());
    }
}
