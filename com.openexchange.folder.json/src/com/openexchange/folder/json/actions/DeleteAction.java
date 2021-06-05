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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.EnqueuableAJAXActionService;
import com.openexchange.ajax.requesthandler.jobqueue.JobKey;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.folder.json.services.ServiceRegistry;
import com.openexchange.folderstorage.FolderExceptionErrorMessage;
import com.openexchange.folderstorage.FolderResponse;
import com.openexchange.folderstorage.FolderService;
import com.openexchange.folderstorage.FolderServiceDecorator;
import com.openexchange.folderstorage.TrashAwareFolderService;
import com.openexchange.folderstorage.TrashResult;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.oauth.provider.resourceserver.OAuthAccess;
import com.openexchange.oauth.provider.resourceserver.annotations.OAuthScopeCheck;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link DeleteAction} - Maps the action to a DELETE action.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractFolderAction.MODULE, type = RestrictedAction.Type.WRITE, hasCustomOAuthScopeCheck = true)
public final class DeleteAction extends AbstractFolderAction implements EnqueuableAJAXActionService {

    private static final String PARAM_HARD_DELETE = "hardDelete";
    private static final String PARAM_FAIL_ON_ERROR = "failOnError";
    private static final String PARAM_TREE = "tree";
    public static final String ACTION = AJAXServlet.ACTION_DELETE;
    private static final String NEW_PATH = "new_path";
    private static final String PATH = "path";
    private static final String HAS_FAILED = "hasFailed";
    private static final String IS_TRASHED = "isTrashed";
    private static final String SUPPORTED = "isSuppoprted";
    private static final String EXTENDED_RESPONSE = "extendedResponse";

    /**
     * Initializes a new {@link DeleteAction}.
     */
    public DeleteAction() {
        super();
    }

    @Override
    protected AJAXRequestResult doPerform(final AJAXRequestData request, final ServerSession session) throws OXException, JSONException {
        /*
         * Parse parameters
         */
        String treeId = request.getParameter(PARAM_TREE);
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
                timestamp = null;
            } else {
                try {
                    timestamp = new Date(Long.parseLong(timestampStr));
                } catch (NumberFormatException e) {
                    throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create("timestamp", timestampStr);
                }
            }
        }
        boolean extendedResponse;
        {
            Boolean bExtendedResponse = request.getParameter(EXTENDED_RESPONSE, boolean.class, true);
            extendedResponse = bExtendedResponse != null ? bExtendedResponse.booleanValue() : false;
        }
        /*
         * Compose JSON array with id
         */
        final JSONArray jsonArray = (JSONArray) request.requireData();
        final int len = jsonArray.length();
        /*
         * Delete
         */
        final boolean failOnError = AJAXRequestDataTools.parseBoolParameter(PARAM_FAIL_ON_ERROR, request, false);
        final FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
        FolderServiceDecorator decorator = getDecorator(request).put(PARAM_HARD_DELETE, request.getParameter(PARAM_HARD_DELETE));
        final AJAXRequestResult result;
        if (failOnError) {
            final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeleteAction.class);
            Map<String, OXException> foldersWithError = new HashMap<>(len);
            List<TrashResult> trashResults = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                final String folderId = jsonArray.getString(i);
                try {
                    FolderResponse<?> response = null;
                    if (extendedResponse && folderService instanceof TrashAwareFolderService) {
                        try {
                            response = ((TrashAwareFolderService) folderService).trashFolder(treeId, folderId, timestamp, session, decorator);
                        } catch (OXException e) {
                            if (!e.equalsCode(1041, "FLD")) {
                                throw e;
                            }
                            // else continue with normal operation
                        }
                    }

                    if (response == null) {
                        response = folderService.deleteFolder(treeId, folderId, timestamp, session, decorator);
                        if (extendedResponse) {
                            trashResults.add(TrashResult.createUnsupportedTrashResult());
                        }
                    } else {
                        trashResults.add((TrashResult) response.getResponse());
                    }
                    final Collection<OXException> warnings = response.getWarnings();
                    if (null != warnings && !warnings.isEmpty()) {
                        throw warnings.iterator().next();
                    }
                } catch (OXException e) {
                    e.setCategory(Category.CATEGORY_ERROR);
                    log.error("Failed to delete folder {} in tree {}.", folderId, treeId, e);
                    foldersWithError.put(folderId, e);
                }
            }
            final int size = foldersWithError.size();
            if (size > 0) {
                if (1 == size) {
                    throw foldersWithError.values().iterator().next();
                }
                final StringBuilder sb = new StringBuilder(64);
                Iterator<String> iterator = foldersWithError.keySet().iterator();
                sb.append(getFolderNameSafe(folderService, iterator.next(), treeId, session));
                while (iterator.hasNext()) {
                    sb.append(", ").append(getFolderNameSafe(folderService, iterator.next(), treeId, session));
                }
                throw FolderExceptionErrorMessage.FOLDER_DELETION_FAILED.create(sb.toString());
            }
            if (extendedResponse) {
                result = createExtendedResponse(trashResults, true);
            } else {
                result = new AJAXRequestResult(new JSONArray(0));
            }
        } else {
            final JSONArray responseArray = new JSONArray();
            final List<OXException> warnings = new LinkedList<>();
            List<TrashResult> trashResults = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                final String folderId = jsonArray.getString(i);
                try {
                    FolderResponse<?> response = null;
                    if (extendedResponse && folderService instanceof TrashAwareFolderService) {
                        try {
                            response = ((TrashAwareFolderService) folderService).trashFolder(treeId, folderId, timestamp, session, decorator);
                        } catch (OXException e) {
                            if (!e.equalsCode(1041, "FLD")) {
                                throw e;
                            }
                            // else continue with normal operation
                        }
                    }
                    if (response == null) {
                        response = folderService.deleteFolder(treeId, folderId, timestamp, session, decorator);
                        if (extendedResponse) {
                            trashResults.add(TrashResult.createUnsupportedTrashResult());
                        }
                    } else {
                        trashResults.add((TrashResult) response.getResponse());
                    }
                } catch (OXException e) {
                    final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeleteAction.class);
                    log.error("Failed to delete folder {} in tree {}.", folderId, treeId, e);
                    e.setCategory(Category.CATEGORY_WARNING);
                    warnings.add(e);
                    responseArray.put(folderId);
                    if (extendedResponse) {
                        trashResults.add(new TrashResult(folderId, true));
                    }
                }
            }
            if (extendedResponse) {
                result = createExtendedResponse(trashResults, false).addWarnings(warnings);
            } else {
                result = new AJAXRequestResult(responseArray).addWarnings(warnings);
            }
        }
        /*
         * Return appropriate result
         */
        return result;
    }

    private AJAXRequestResult createExtendedResponse(List<TrashResult> results, boolean failOnError) throws JSONException {
        JSONArray resultArray = new JSONArray(results.size());
        if (failOnError) {

            for (TrashResult trashResult : results) {
                if (trashResult.isSupported()) {
                    JSONObject obj = new JSONObject(3);
                    obj.put(SUPPORTED, true);
                    if (trashResult.isTrashed()) {
                        obj.put(IS_TRASHED, true);
                        obj.put(NEW_PATH, trashResult.getNewPath());
                        obj.put(PATH, trashResult.getOldPath());
                    } else {
                        obj.put(IS_TRASHED, false);
                        obj.put(PATH, trashResult.getOldPath());
                    }
                    resultArray.put(obj);
                } else {
                    JSONObject obj = new JSONObject(1);
                    obj.put(SUPPORTED, false);
                    resultArray.put(obj);
                }
            }
        } else {
            for (TrashResult trashResult : results) {
                if (trashResult.isSupported()) {
                    JSONObject obj = new JSONObject(3);
                    obj.put(SUPPORTED, true);
                    if (trashResult.hasFailed()) {
                        obj.put(HAS_FAILED, true);
                        obj.put(PATH, trashResult.getOldPath());
                    } else {
                        if (trashResult.isTrashed()) {
                            obj.put(IS_TRASHED, true);
                            obj.put(PATH, trashResult.getOldPath());
                            obj.put(NEW_PATH, trashResult.getNewPath());
                        } else {
                            obj.put(IS_TRASHED, false);
                            obj.put(PATH, trashResult.getOldPath());
                        }
                    }
                    resultArray.put(obj);
                } else {
                    JSONObject obj = new JSONObject(1);
                    obj.put(SUPPORTED, false);
                    resultArray.put(obj);
                }

            }
        }
        return new AJAXRequestResult(resultArray);
    }

    /**
     * Tries to get the name of a folder, not throwing an exception in case retrieval fails, but falling back to the folder identifier.
     *
     * @param folderService The folder service
     * @param folderId The ID of the folder to get the name for
     * @param treeId The folder tree
     * @param session the session
     * @return The folder name, or the passed folder ID as fallback
     */
    private static String getFolderNameSafe(FolderService folderService, String folderId, String treeId, ServerSession session) {
        try {
            UserizedFolder folder = folderService.getFolder(treeId, folderId, session, null);
            if (null != folder) {
                return folder.getLocalizedName(session.getUser().getLocale());
            }
        } catch (OXException e) {
            org.slf4j.LoggerFactory.getLogger(DeleteAction.class).debug("Error getting folder name for {}", folderId, e);
        }
        return folderId;
    }

    @OAuthScopeCheck
    public boolean accessAllowed(final AJAXRequestData request, final ServerSession session, final OAuthAccess access) throws OXException {
        final JSONArray jsonArray = (JSONArray) request.requireData();
        final int len = jsonArray.length();
        String treeId = request.getParameter(PARAM_TREE);
        if (null == treeId) {
            treeId = getDefaultTreeIdentifier();
        }

        final FolderService folderService = ServiceRegistry.getInstance().getService(FolderService.class, true);
        try {
            for (int i = 0; i < len; i++) {
                final String folderId = jsonArray.getString(i);
                UserizedFolder folder = folderService.getFolder(treeId, folderId, session, new FolderServiceDecorator());
                if (!mayWriteViaOAuthRequest(folder.getContentType(), access)) {
                    return false;
                }
            }

            return true;
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public EnqueuableAJAXActionService.Result isEnqueueable(AJAXRequestData request, ServerSession session) throws OXException {

        String treeId = request.getParameter(PARAM_TREE);
        if (null == treeId) {
            treeId = getDefaultTreeIdentifier();
        }

        Boolean bExtendedResponse = request.getParameter(EXTENDED_RESPONSE, boolean.class, true);
        boolean extendedResponse = bExtendedResponse != null ? bExtendedResponse.booleanValue() : false;

        boolean failOnError = AJAXRequestDataTools.parseBoolParameter(PARAM_FAIL_ON_ERROR, request, false);
        String hardDelete = request.getParameter(PARAM_HARD_DELETE);

        final JSONArray jsonArray = (JSONArray) request.requireData();
        int hash = jsonArray.toString().hashCode();

        try {
            JSONObject jKeyDesc = new JSONObject(4);
            jKeyDesc.put("module", "folder");
            jKeyDesc.put("action", "delete");
            jKeyDesc.put(PARAM_TREE, treeId);
            jKeyDesc.put(PARAM_FAIL_ON_ERROR, failOnError);
            jKeyDesc.put(EXTENDED_RESPONSE, extendedResponse);
            jKeyDesc.put(PARAM_HARD_DELETE, hardDelete);
            jKeyDesc.put("body", hash);

            return EnqueuableAJAXActionService.resultFor(true, new JobKey(session.getUserId(), session.getContextId(), jKeyDesc.toString()), this);
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }
    }
}
