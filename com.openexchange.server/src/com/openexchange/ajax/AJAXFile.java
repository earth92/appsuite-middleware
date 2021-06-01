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

package com.openexchange.ajax;

import static com.openexchange.ajax.Mail.getSaveAsFileName;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.helper.ParamContainer;
import com.openexchange.ajax.writer.ResponseWriter;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.exception.OXException;
import com.openexchange.filemanagement.ManagedFile;
import com.openexchange.filemanagement.ManagedFileManagement;
import com.openexchange.groupware.upload.impl.UploadException;
import com.openexchange.groupware.upload.impl.UploadQuotaChecker;
import com.openexchange.java.Streams;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.servlet.OXJSONExceptionCodes;
import com.openexchange.tools.servlet.UploadServletException;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.session.ServerSession;

/**
 * AJAXFile
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class AJAXFile extends PermissionServlet {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 1L;

    private static final transient org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AJAXFile.class);

    private static final String MIME_TEXT_HTML_CHARSET_UTF_8 = "text/html; charset=UTF-8";

    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final String STR_NULL = "null";

    public AJAXFile() {
        super();
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType(CONTENTTYPE_JSON);
        /*
         * The magic spell to disable caching
         */
        Tools.disableCaching(resp);
        final ServerSession session = getSessionObject(req);
        final String action = req.getParameter(PARAMETER_ACTION);
        if (ACTION_KEEPALIVE.equalsIgnoreCase(action)) {
            actionKeepAlive(req, resp);
        } else if (ACTION_GET.equalsIgnoreCase(action)) {
            actionGet(req, resp);
        } else {
            final Response response = new Response(session);
            response.setException(UploadException.UploadCode.UNKNOWN_ACTION_VALUE.create(
                action == null ? STR_NULL : action).setAction(null));
            try {
                ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
            } catch (JSONException e) {
                LOG.error("", e);
                final ServletException se = new ServletException(e.getMessage(), e);
                se.initCause(e);
                throw se;
            }
        }
    }

    private void actionKeepAlive(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        ServerSession session = getSessionObject(req);
        try {
            ResponseWriter.write(actionKeepAlive(session, ParamContainer.getInstance(req, resp)), resp.getWriter(), localeFrom(session));
        } catch (JSONException e) {
            final Response response = new Response(session);
            response.setException(OXJSONExceptionCodes.JSON_WRITE_ERROR.create(e, new Object[0]));
            try {
                ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
            } catch (JSONException e1) {
                LOG.error("", e1);
                final ServletException se = new ServletException(e1.getMessage(), e1);
                se.initCause(e1);
                throw se;
            }
        }
    }

    private Response actionKeepAlive(final ServerSession session, final ParamContainer paramContainer) {
        /*
         * Some variables
         */
        final Response response = new Response(session);
        /*
         * Start response
         */
        try {
            final String id = paramContainer.checkStringParam(PARAMETER_ID);
            final ManagedFileManagement management = ServerServiceRegistry.getInstance().getService(ManagedFileManagement.class);
            management.getByID(id);
        } catch (OXException e) {
            response.setException(e);
        }
        /*
         * Close response and flush print writer
         */
        response.setData(JSONObject.NULL);
        response.setTimestamp(null);
        return response;
    }

    private void actionGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final ServerSession session = getSessionObject(req);
        try {
            final String id = req.getParameter(PARAMETER_ID);
            if (id == null || id.length() == 0) {
                throw UploadException.UploadCode.MISSING_PARAM.create(PARAMETER_ID).setAction(ACTION_GET);
            }
            /*
             * Check if user agent is internet explorer
             */
            final String userAgent = req.getHeader("user-agent") == null ? null : req.getHeader("user-agent").toLowerCase(Locale.ENGLISH);
            final boolean internetExplorer = (userAgent != null && userAgent.indexOf("msie") > -1 && userAgent.indexOf("windows") > -1);
            final ManagedFileManagement management = ServerServiceRegistry.getInstance().getService(ManagedFileManagement.class);
            final ManagedFile file = management.getByID(id);
            /*
             * Set proper headers
             */
            final String fileName = getSaveAsFileName(file.getFileName(), internetExplorer, file.getContentType());
            final ContentType contentType = new ContentType(file.getContentType());
            if (contentType.getBaseType().equalsIgnoreCase(MIME_APPLICATION_OCTET_STREAM)) {
                /*
                 * Try to determine MIME type
                 */
                final String ct = MimeType2ExtMap.getContentType(fileName);
                final int pos = ct.indexOf('/');
                contentType.setPrimaryType(ct.substring(0, pos));
                contentType.setSubType(ct.substring(pos + 1));
            }
            contentType.setParameter("name", fileName);
            resp.setContentType(contentType.toString());
            resp.setHeader(
                "Content-disposition",
                new StringBuilder(50).append("inline; filename=\"").append(fileName).append('"').toString());
            /*
             * Write from content's input stream to response output stream
             */
            InputStream contentInputStream = null;
            /*
             * Handle caching headers
             */
            Tools.updateCachingHeaders(req, resp);
            final OutputStream out = resp.getOutputStream();
            try {
                contentInputStream = new FileInputStream(file.getFile());
                final byte[] buffer = new byte[0xFFFF];
                for (int len; (len = contentInputStream.read(buffer)) > 0;) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            } finally {
                Streams.close(contentInputStream);
            }
        } catch (UploadException e) {
            LOG.error("", e);
            resp.setContentType(MIME_TEXT_HTML_CHARSET_UTF_8);
            Tools.disableCaching(resp);
            JSONObject responseObj = null;
            try {
                final Response response = new Response(session);
                response.setException(e);
                responseObj = ResponseWriter.getJSON(response);
            } catch (JSONException e1) {
                LOG.error("", e1);
            }
			throw new UploadServletException(resp, substituteJS(
					responseObj == null ? STR_NULL : responseObj.toString(),
					e.getAction() == null ? STR_NULL : e.getAction()),
					e.getMessage(), e);
        } catch (OXException e) {
            LOG.error("", e);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType(CONTENTTYPE_JSON);
            Tools.disableCaching(resp);
            final Response response = new Response();
            response.setException(e);
            try {
                ResponseWriter.write(response, resp.getWriter(), localeFrom(session));
            } catch (JSONException e1) {
                LOG.error("", e1);
                final ServletException se = new ServletException(e1.getMessage(), e1);
                se.initCause(e1);
                throw se;
            }
        }
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        /*
         * The magic spell to disable caching
         */
        final ServerSession session = getSessionObject(req);
        Tools.disableCaching(resp);
        resp.setContentType(MIME_TEXT_HTML_CHARSET_UTF_8);
        String action = null;
        try {
            if (FileUploadBase.isMultipartContent(new ServletRequestContext(req))) {
                final DiskFileItemFactory factory = new DiskFileItemFactory();
                /*
                 * Set factory constraints
                 */
                factory.setSizeThreshold(0);
                factory.setRepository(ServerConfig.getTmpDir());
                /*
                 * Create a new file upload handler
                 */
                final ServletFileUpload upload = new ServletFileUpload(factory);
                /*
                 * Set overall request size constraint
                 */
                final String moduleParam = req.getParameter(PARAMETER_MODULE);
                if (moduleParam == null) {
                    throw UploadException.UploadCode.MISSING_PARAM.create(PARAMETER_MODULE);
                }
                final String fileTypeFilter = req.getParameter(PARAMETER_TYPE);
                if (fileTypeFilter == null) {
                    throw UploadException.UploadCode.MISSING_PARAM.create(PARAMETER_TYPE);
                }
                final ServerSession sessionObj = getSessionObject(req);
                final UploadQuotaChecker checker =
                    UploadQuotaChecker.getUploadQuotaChecker(getModuleInteger(moduleParam), sessionObj, sessionObj.getContext());
                upload.setSizeMax(checker.getQuotaMax());
                upload.setFileSizeMax(checker.getFileQuotaMax());
                /*
                 * Check action parameter
                 */
                try {
                    action = getAction(req);
                } catch (OXException e) {
                    throw UploadException.UploadCode.UPLOAD_FAILED.create(e, e.getMessage()).setAction(action);
                }
                if (!ACTION_NEW.equalsIgnoreCase(action)) {
                    throw UploadException.UploadCode.INVALID_ACTION_VALUE.create(action).setAction(action);
                }
                /*
                 * Process upload
                 */
                final List<FileItem> items;
                try {
                    final @SuppressWarnings("unchecked") List<FileItem> tmp = upload.parseRequest(req);
                    items = tmp;
                } catch (FileUploadException e) {
                    throw UploadException.UploadCode.UPLOAD_FAILED.create(e).setAction(action);
                }
                final int size = items.size();
                final Iterator<FileItem> iter = items.iterator();
                final JSONArray jArray = new JSONArray();
                try {
                    final ManagedFileManagement management = ServerServiceRegistry.getInstance().getService(ManagedFileManagement.class);
                    for (int i = 0; i < size; i++) {
                        final FileItem fileItem = iter.next();
                        // Check for a valid file item
                        if (isValidFile(fileItem)) {
                            // Check file item's content type
                            final ContentType ct = new ContentType(fileItem.getContentType());
                            if (!checkFileType(fileTypeFilter, ct)) {
                                throw UploadException.UploadCode.INVALID_FILE_TYPE.create(
                                    action == null ? STR_NULL : action,
                                    fileItem.getContentType(),
                                    fileTypeFilter);
                            }
                            jArray.put(processFileItem(fileItem, management));
                        }
                    }
                } catch (UploadException e) {
                    throw e;
                } catch (Exception e) {
                    throw UploadException.UploadCode.UPLOAD_FAILED.create(e).setAction(action);
                }
                /*
                 * Return IDs of upload files in response
                 */
                final Response response = new Response(session);
                response.setData(jArray);
				final String jsResponse = substituteJS(
						ResponseWriter.getJSON(response).toString(), action);
                final Writer writer = resp.getWriter();
                writer.write(jsResponse);
                writer.flush();

            }
        } catch (OXException e) {
            JSONObject responseObj = null;
            try {
                final Response response = new Response(session);
                response.setException(e);
                responseObj = ResponseWriter.getJSON(response);
            } catch (JSONException e1) {
                LOG.error("", e1);
            }
			throw new UploadServletException(resp, substituteJS(
					responseObj == null ? STR_NULL : responseObj.toString(),
					STR_NULL),
					e.getMessage(), e);
        } catch (JSONException e) {
            final OXException oje = OXJSONExceptionCodes.JSON_WRITE_ERROR.create(e, new Object[0]);
            JSONObject responseObj = null;
            try {
                final Response response = new Response(session);
                response.setException(oje);
                responseObj = ResponseWriter.getJSON(response);
            } catch (JSONException e1) {
                LOG.error("", e1);
            }
			throw new UploadServletException(resp, substituteJS(
					responseObj == null ? STR_NULL : responseObj.toString(),
					action == null ? STR_NULL : action), e.getMessage(), e);
        }
    }

    /**
     * Checks if specified {@link FileItem file item} denotes a valid file.
     *
     * @param fileItem The file item to check
     * @return <code>true</code> if file item denotes a valid file; otherwise <code>false</code>
     */
    private static boolean isValidFile(final FileItem fileItem) {
        if (fileItem.isFormField() || fileItem.getSize() <= 0) {
            return false;
        }
        final String name = fileItem.getName();
        return (name != null && name.length() > 0);
    }

    private static final String FILE_TYPE_ALL = "file";

    private static final String FILE_TYPE_TEXT = "text";

    private static final String FILE_TYPE_MEDIA = "media";

    private static final String FILE_TYPE_IMAGE = "image";

    private static final String FILE_TYPE_AUDIO = "audio";

    private static final String FILE_TYPE_VIDEO = "video";

    private static final String FILE_TYPE_APPLICATION = "application";

    private static boolean checkFileType(final String filter, final ContentType fileContentType) {
        if (FILE_TYPE_ALL.equalsIgnoreCase(filter)) {
            return true;
        } else if (FILE_TYPE_TEXT.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("text/");
        } else if (FILE_TYPE_MEDIA.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("image/") || fileContentType.startsWith("audio/") || fileContentType.startsWith("video/");
        } else if (FILE_TYPE_IMAGE.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("image/");
        } else if (FILE_TYPE_AUDIO.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("audio/");
        } else if (FILE_TYPE_VIDEO.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("video/");
        } else if (FILE_TYPE_APPLICATION.equalsIgnoreCase(filter)) {
            return fileContentType.startsWith("application/");
        }
        return false;
    }

    private static String processFileItem(final FileItem fileItem, final ManagedFileManagement management) throws Exception {
        final ManagedFile managedFile = management.createManagedFile(fileItem.getInputStream());
        managedFile.setFileName(fileItem.getName());
        managedFile.setContentType(fileItem.getContentType());
        managedFile.setSize(fileItem.getSize());
        return managedFile.getID();
    }

    @Override
    protected boolean hasModulePermission(final ServerSession session) {
        return true;
    }

}
