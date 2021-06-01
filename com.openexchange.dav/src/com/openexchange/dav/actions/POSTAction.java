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

package com.openexchange.dav.actions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.http.HttpServletResponse;
import org.jdom2.Element;
import com.openexchange.dav.AttachmentUtils;
import com.openexchange.dav.DAVProtocol;
import com.openexchange.dav.internal.ShareHelper;
import com.openexchange.dav.resources.CommonResource;
import com.openexchange.dav.resources.DAVObjectResource;
import com.openexchange.dav.resources.FolderCollection;
import com.openexchange.exception.OXException;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.webdav.action.ReplayWebdavRequest;
import com.openexchange.webdav.action.WebdavRequest;
import com.openexchange.webdav.action.WebdavResponse;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;

/**
 * {@link POSTAction}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.1
 */
public class POSTAction extends DAVAction {

    /**
     * Initializes a new {@link POSTAction}.
     *
     * @param protocol The underlying protocol
     */
    public POSTAction(Protocol protocol) {
        super(protocol);
    }

    @Override
    public void perform(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        /*
         * default handling
         */
        handle(request, response);
    }

    /**
     * Tries to handle a common <code>POST</code> request. This includes:
     * <ul>
     * <li>attachment-related actions on {@link CommonResource}s</li>
     * <li>sharing-related actions on {@link FolderCollection}s</li>
     * </ul>
     *
     * @param request The WebDAV request
     * @param response The response
     * @return <code>true</code> if a suitable request was detected and handled, <code>false</code>, otherwise
     */
    protected boolean handle(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        WebdavResource resource = request.getResource();
        if (null != resource) {
            String action = request.getParameter("action");
            if (Strings.isNotEmpty(action) && DAVObjectResource.class.isInstance(resource)) {
                /*
                 * handle special attachment action
                 */
                return handleAction(request, response);
            }
            String contentType = getContentType(request);
            if (("application/davsharing+xml".equals(contentType)) && FolderCollection.class.isInstance(resource)) {
                request = new ReplayWebdavRequest(request);
                Element rootElement = optRootElement(request, DAVProtocol.DAV_NS, "share-resource");
                if (null != rootElement) {
                    /*
                     * handle WebDAV share request
                     */
                    ShareHelper.shareResource((FolderCollection<?>) resource, rootElement);
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return true;
                }
            }
            if ("text/xml".equals(contentType) && FolderCollection.class.isInstance(resource)) {
                request = new ReplayWebdavRequest(request);
                Element rootElement = optRootElement(request, DAVProtocol.CALENDARSERVER_NS, "share");
                if (null != rootElement) {
                    /*
                     * handle calendarserver share request
                     */
                    ShareHelper.share((FolderCollection<?>) resource, rootElement);
                    response.setStatus(HttpServletResponse.SC_OK);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tries to handle a special action as indicated by the <code>action</code> query parameter.
     *
     * @param request The WebDAV request
     * @param response The response
     * @return <code>true</code> if a special action was detected and handled, <code>false</code>, otherwise
     */
    private boolean handleAction(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        String action = request.getParameter("action");
        if (Strings.isEmpty(action)) {
            return false;
        }
        switch (action) {
            case "attachment-add":
                addAttachment(request, response);
                return true;
            case "attachment-remove":
                removeAttachment(request, response);
                return true;
            case "attachment-update":
                updateAttachment(request, response);
                return true;
            default:
                return false;
        }
    }

    protected void writeResource(WebdavResource resource, WebdavResponse response) throws WebdavProtocolException {
        /*
         * write back response
         */
        response.setContentType(resource.getContentType());
        OutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            inputStream = resource.getBody();
            outputStream = response.getOutputStream();
            if (inputStream != null) {
                int buflen = 65536;
                byte[] buffer = new byte[buflen];
                for (int length; (length = inputStream.read(buffer, 0, buflen)) > 0;) {
                    outputStream.write(buffer, 0, length);
                }
            }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(POSTAction.class).debug("Client gone?", e);
        } finally {
            Streams.close(inputStream, outputStream);
        }
    }

    private void addAttachment(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        /*
         * get targeted resource & attachment related parameters
         */
        DAVObjectResource<?> resource = requireResource(request, DAVObjectResource.class);
        String contentType = getContentType(request);
        String fileName = AttachmentUtils.parseFileName(request);
        String[] recurrenceIDs = Strings.splitByComma(request.getParameter("rid"));
        long size = getContentLength(request);
        /*
         * save attachment
         */
        int attachmenId;
        InputStream inputStream = null;
        try {
            inputStream = request.getBody();
            attachmenId = resource.addAttachment(inputStream, contentType, fileName, size, recurrenceIDs)[0];
        } catch (IOException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (OXException e) {
            throw AttachmentUtils.protocolException(e, request.getUrl());
        } finally {
            Streams.close(inputStream);
        }
        /*
         * apply response headers
         */
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType(resource.getContentType());
        response.setHeader("Content-Location", resource.getUrl().toString());
        setHeaderOpt("ETag", resource.getETag(), true, response);
        response.setHeader("Cal-Managed-ID", String.valueOf(attachmenId));
    }

    private void removeAttachment(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        /*
         * get targeted resource & attachment related parameters
         */
        DAVObjectResource<?> resource = requireResource(request, DAVObjectResource.class);
        String managedId = request.getParameter("managed-id");
        if (Strings.isEmpty(managedId)) {
            throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        int attachmentId;
        try {
            attachmentId = Integer.parseInt(managedId);
        } catch (NumberFormatException e) {
            throw WebdavProtocolException.generalError(e, request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        String[] recurrenceIDs = Strings.splitByComma(request.getParameter("rid"));
        /*
         * remove attachment & apply response headers for successful removal
         */
        try {
            resource.removeAttachment(attachmentId, recurrenceIDs);
        } catch (OXException e) {
            throw AttachmentUtils.protocolException(e, request.getUrl());
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void updateAttachment(WebdavRequest request, WebdavResponse response) throws WebdavProtocolException {
        /*
         * get targeted resource & attachment related parameters
         */
        DAVObjectResource<?> resource = requireResource(request, DAVObjectResource.class);
        String contentType = getContentType(request);
        String fileName = AttachmentUtils.parseFileName(request);
        long size = getContentLength(request);
        String managedId = request.getParameter("managed-id");
        if (Strings.isEmpty(managedId)) {
            throw WebdavProtocolException.generalError(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        int attachmentId;
        try {
            attachmentId = Integer.parseInt(managedId);
        } catch (NumberFormatException e) {
            throw WebdavProtocolException.generalError(e, request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        }
        /*
         * update attachment
         */
        InputStream inputStream = null;
        try {
            inputStream = request.getBody();
            attachmentId = resource.updateAttachment(attachmentId, inputStream, contentType, fileName, size);
        } catch (IOException e) {
            throw WebdavProtocolException.Code.GENERAL_ERROR.create(request.getUrl(), HttpServletResponse.SC_BAD_REQUEST);
        } catch (OXException e) {
            throw AttachmentUtils.protocolException(e, request.getUrl());
        } finally {
            Streams.close(inputStream);
        }
        /*
         * apply response headers
         */
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType(resource.getContentType());
        response.setHeader("Content-Location", resource.getUrl().toString());
        setHeaderOpt("ETag", resource.getETag(), true, response);
        response.setHeader("Cal-Managed-ID", String.valueOf(attachmentId));
    }

}
