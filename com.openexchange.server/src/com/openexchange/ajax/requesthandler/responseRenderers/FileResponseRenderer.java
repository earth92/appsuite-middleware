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

package com.openexchange.ajax.requesthandler.responseRenderers;

import static com.openexchange.java.Streams.close;
import static com.openexchange.java.Strings.isEmpty;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import com.google.common.collect.ImmutableList;
import com.openexchange.ajax.AJAXUtility;
import com.openexchange.ajax.container.DelegateFileHolder;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.ajax.fileholder.Readable;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.CheckParametersAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.CopyHeaderAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.IDataWrapper;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.IFileResponseRendererAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.ModifyCachingHeaderHeaderAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.OutputBinaryContentAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.PrepareResponseHeaderAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.SetBinaryInputStreamAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.TransformImageClientAction;
import com.openexchange.ajax.requesthandler.responseRenderers.actions.UpdateETagHeaderAction;
import com.openexchange.exception.OXException;
import com.openexchange.imageconverter.api.IImageClient;
import com.openexchange.imagetransformation.ImageTransformationDeniedIOException;
import com.openexchange.imagetransformation.ImageTransformationService;
import com.openexchange.imagetransformation.ScaleType;
import com.openexchange.java.Streams;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.http.Tools;

/**
 * {@link FileResponseRenderer}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class FileResponseRenderer extends AbstractListenerCollectingResponseRenderer {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FileResponseRenderer.class);

    private static final String SAVE_AS_TYPE = "application/octet-stream";

    /** The default width when requesting a thumbnail image */
    public static final int THUMBNAIL_WIDTH = 160;

    /** The default height when requesting a thumbnail image */
    public static final int THUMBNAIL_HEIGHT = 160;

    /** The default scale type when requesting a thumbnail image */
    public static final String THUMBNAIL_SCALE_TYPE = ScaleType.COVER.getKeyword();

    /** The default delivery value when requesting a thumbnail image */
    public static final String THUMBNAIL_DELIVERY = IDataWrapper.VIEW;

    // -------------------------------------------------------------------------------------------------------------------------------

    private final TransformImageClientAction imageClientAction = new TransformImageClientAction();
    private final List<IFileResponseRendererAction> registeredActions;

    /**
     * Initializes a new {@link FileResponseRenderer}.
     */
    public FileResponseRenderer() {
        super();

        // Initialize renderer actions
        ImmutableList.Builder<IFileResponseRendererAction> registeredActions = ImmutableList.builder();
        registeredActions.add(new CheckParametersAction());
        registeredActions.add(imageClientAction);
        registeredActions.add(new CopyHeaderAction());
        registeredActions.add(new SetBinaryInputStreamAction());
        registeredActions.add(new PrepareResponseHeaderAction());
        registeredActions.add(new ModifyCachingHeaderHeaderAction());
        registeredActions.add(new UpdateETagHeaderAction());
        registeredActions.add(new OutputBinaryContentAction());
        this.registeredActions = registeredActions.build();
    }

    @Override
    public int getRanking() {
        return 0;
    }

    /**
     * Sets the image scaler.
     *
     * @param scaler The image scaler
     */
    public void setScaler(ImageTransformationService scaler) {
        imageClientAction.setScaler(scaler);
    }

    /**
     * Sets the client reference to the image service.
     *
     * @param imageClient The client reference to the image service
     */
    public void setImageClient(IImageClient imageClient) {
        imageClientAction.setImageClient(imageClient);
    }

    @Override
    public boolean handles(AJAXRequestData request, AJAXRequestResult result) {
        return (result.getResultObject() instanceof IFileHolder);
    }

    private static final String MSG_OUTPUT_EXCEPTION = "Exception while trying to output file";

    @Override
    public void actualWrite(AJAXRequestData request, AJAXRequestResult result, HttpServletRequest req, HttpServletResponse resp) {
        IFileHolder file = (IFileHolder) result.getResultObject();

        // Check if file is actually supplied by the request URL.
        if (null == file) {
            try {
                // Do your thing if the file is not supplied to the request URL or if there is no file item associated with specified file
                // Throw an exception, or send 404, or show default/warning page, or just ignore it.
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException e) {
                LOG.error("Exception while trying to write HTTP response.", e);
            }
        } else {
            // Check if file is actually supplied by the request URL.
            try {
                file = returnInstanceIfHoldsData(file);
            } catch (IOException e) {
                LOG.error(MSG_OUTPUT_EXCEPTION, e);
                sendErrorSafe(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, MSG_OUTPUT_EXCEPTION, resp);
            }

            // Output...
            if (file == null) {
                try {
                    // Do your thing if the file is not supplied to the request URL or if there is no file item associated with specified file
                    // Throw an exception, or send 404, or show default/warning page, or just ignore it.
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                } catch (IOException e) {
                    LOG.error("Exception while trying to write HTTP response.", e);
                }
            } else {
                try {
                    writeFileHolder(file, request, result, req, resp);
                } finally {
                    postProcessingTasks(file);
                }
            }
        }
    }

    /**
     * @param file
     */
    private static void postProcessingTasks(IFileHolder file) {
        List<Runnable> tasks = file.getPostProcessingTasks();
        if (null != tasks && !tasks.isEmpty()) {
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    /**
     * Writes specified file holder.
     *
     * @param fileHolder The file holder
     * @param requestData The AJAX request data
     * @param result The AJAX response
     * @param req The HTTP request
     * @param resp The HTTP response
     */
    public void writeFileHolder(IFileHolder fileHolder, AJAXRequestData requestData, AJAXRequestResult result, HttpServletRequest req, HttpServletResponse resp) {
        final String fileName = fileHolder.getName();
        final long length = fileHolder.getLength();
        final List<Closeable> closeables = new LinkedList<>();
        final String fileContentType = fileHolder.getContentType();
        IDataWrapper data = new DataWrapper().setContentTypeByParameter(Boolean.FALSE).setLength(length).setFile(fileHolder).setRequest(req).setFileContentType(fileContentType).setFileName(fileName).setRequestData(requestData).setResponse(resp).setCloseAbles(closeables).setResult(result);

        try {
            data.setUserAgent(AJAXUtility.sanitizeParam(req.getHeader("user-agent")));
            for (IFileResponseRendererAction action : registeredActions) {
                action.call(data);
            }
        } catch (FileResponseRendererActionException ex) {
            // Respond with an error
            try {
                resp.sendError(ex.statusCode, ex.message == null ? EnglishReasonPhraseCatalog.INSTANCE.getReason(ex.statusCode, null) : ex.message);
            } catch (IOException e) {
                LOG.error("", e);
            }
            return;
        } catch (OXException e) {
            String message = MSG_OUTPUT_EXCEPTION;
            LOG.error(message, e);
            if (AjaxExceptionCodes.BAD_REQUEST.equals(e)) {
                Throwable cause = e;
                for (Throwable xc; (xc = cause.getCause()) != null;) {
                    cause = xc;
                }
                final String causeMsg = cause.getMessage();
                sendErrorSafe(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null == causeMsg ? message : causeMsg, resp);
            } else if (AjaxExceptionCodes.HTTP_ERROR.equals(e)) {
                Object[] logArgs = e.getLogArgs();
                Object statusMsg = logArgs.length > 1 ? logArgs[1] : null;
                int sc = ((Integer) logArgs[0]).intValue();
                sendErrorSafe(sc, null == statusMsg ? null : statusMsg.toString(), resp);
                return;
            } else {
                sendErrorSafe(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, resp);
            }
        } catch (ImageTransformationDeniedIOException e) {
            // Quit with 406
            LOG.error("Exception while trying to output image", e);
            sendErrorSafe(HttpServletResponse.SC_NOT_ACCEPTABLE, e.getMessage(), resp);
        } catch (Exception e) {
            String message = MSG_OUTPUT_EXCEPTION;
            LOG.error(message, e);
            sendErrorSafe(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, resp);
        } finally {
            close(data.getDocumentData(), data.getFile());
            close(closeables);
        }
    }

    /**
     * @param fileName
     * @return
     */
    public static String getContentTypeByFileName(final String fileName) {
        return null == fileName ? null : MimeType2ExtMap.getContentType(fileName, null);
    }

    /**
     * @param sc
     * @param msg
     * @param resp
     */
    private static void sendErrorSafe(int sc, String msg, final HttpServletResponse resp) {
        try {
            Tools.sendErrorPage(resp, sc, msg);
        } catch (@SuppressWarnings("unused") final Exception e) {
            // Ignore
        }
    }

    /**
     * Checks if given file holder holds data.
     *
     * @param file The file holder to check
     * @return The passed file holder instance in case it is considered to hold data; otherwise <code>null</code> for no data
     * @throws IOException If an I/O error occurs
     */
    private static IFileHolder returnInstanceIfHoldsData(final IFileHolder file) throws IOException {
        final String fileMIMEType = file.getContentType();
        if ((!isEmpty(fileMIMEType) && !SAVE_AS_TYPE.equals(fileMIMEType)) || !isEmpty(file.getName())) {
            return file;
        }

        // First, check advertised length
        if (file.getLength() > 0L) {
            // File holder signals to hold data
            return file;
        }
        if (file.getLength() == 0L) {
            // Apparently no data
            return null;
        }

        // Unknown length. Need to check stream...
        if (file.repetitive()) {
            InputStream in = null;
            try {
                in = file.getStream();
                return in.read() < 0 ? null : file;
            } catch (OXException e) {
                Throwable cause = e.getCause();
                throw cause instanceof IOException ? ((IOException) cause) : new IOException(null == cause ? e : cause);
            } finally {
                close(in);
            }
        }

        InputStream in = null;
        try {
            in = file.getStream();
            in = Streams.getNonEmpty(in);
            if (in == null) {
                return null;
            }
            DelegateFileHolder newFile = new DelegateFileHolder(file);
            newFile.setStream(in, -1);
            in = null; // Avoid premature closing
            return newFile;
        } catch (OXException e) {
            Throwable cause = e.getCause();
            throw cause instanceof IOException ? ((IOException) cause) : new IOException(null == cause ? e : cause);
        } finally {
            close(in);
        }
    }

    /**
     * {@link DataWrapper}
     *
     * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
     */
    private static class DataWrapper implements IDataWrapper {

        private String delivery = null;
        private String contentType = null;
        private String contentDisposition = null;
        private String userAgent;
        private String fileContentType;
        private String fileName;
        private long length = -1;
        private Boolean contentTypeByParameter = Boolean.FALSE;
        private Readable documentData = null;
        private IFileHolder file;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private AJAXRequestData requestData;
        private AJAXRequestResult result;
        private List<Closeable> closeables;

        /**
         * Initializes a new {@link DataWrapper}.
         */
        DataWrapper() {
            super();
        }

        @Override
        public String getDelivery() {
            return delivery;
        }

        @Override
        public IDataWrapper setDelivery(String delivery) {
            this.delivery = delivery;
            return this;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public IDataWrapper setContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        @Override
        public String getContentDisposition() {
            return contentDisposition;
        }

        @Override
        public IDataWrapper setContentDisposition(String contentDisposition) {
            this.contentDisposition = contentDisposition;
            return this;
        }

        @Override
        public Boolean getContentTypeByParameter() {
            return contentTypeByParameter;
        }

        @Override
        public IDataWrapper setContentTypeByParameter(Boolean contentTypeByParameter) {
            this.contentTypeByParameter = contentTypeByParameter;
            return this;
        }

        @Override
        public Readable getDocumentData() {
            return documentData;
        }

        @Override
        public IDataWrapper setDocumentData(Readable documentData) {
            this.documentData = documentData;
            return this;
        }

        @Override
        public long getLength() {
            return length;
        }

        @Override
        public IDataWrapper setLength(long length) {
            this.length = length;
            return this;
        }

        @Override
        public IFileHolder getFile() {
            return file;
        }

        @Override
        public IDataWrapper setFile(IFileHolder file) {
            this.file = file;
            return this;
        }

        @Override
        public HttpServletRequest getRequest() {
            return request;
        }

        @Override
        public IDataWrapper setRequest(HttpServletRequest req) {
            this.request = req;
            return this;
        }

        @Override
        public String getFileContentType() {
            return fileContentType;
        }

        @Override
        public IDataWrapper setFileContentType(String fileContentType) {
            this.fileContentType = fileContentType;
            return this;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public IDataWrapper setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        @Override
        public AJAXRequestData getRequestData() {
            return requestData;
        }

        @Override
        public IDataWrapper setRequestData(AJAXRequestData requestData) {
            this.requestData = requestData;
            return this;
        }

        @Override
        public HttpServletResponse getResponse() {
            return response;
        }

        @Override
        public IDataWrapper setResponse(HttpServletResponse response) {
            this.response = response;
            return this;
        }

        @Override
        public String getUserAgent() {
            return userAgent;
        }

        @Override
        public IDataWrapper setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        @Override
        public void addCloseable(Closeable closeable) {
            if (this.closeables == null) {
                this.closeables = new ArrayList<>();
            }

            this.closeables.add(closeable);
        }

        @Override
        public IDataWrapper setCloseAbles(java.util.List<Closeable> closeables) {
            if (closeables != null) {
                this.closeables = closeables;
            }
            return this;
        }

        @Override
        public List<Closeable> getCloseables() {
            return closeables;
        }

        @Override
        public AJAXRequestResult getResult() {
            return result;
        }

        @Override
        public IDataWrapper setResult(AJAXRequestResult result) {
            this.result = result;
            return this;
        }

    } // End of class DataWrapper

    /**
     * {@link FileResponseRendererActionException} - The special exception to signal that an appropriate HTTP status has already been
     * applied to {@link HttpServletResponse} instance and control flow is supposed to return.
     */
    public static class FileResponseRendererActionException extends Exception {

        private static final long serialVersionUID = 1654135178706909163L;

        /** The status code to respond with */
        public final int statusCode;

        /** The optional accompanying message */
        public final String message;

        /**
         * Initializes a new {@link FileResponseRendererActionException}.
         *
         * @param statusCode The HTTP status code
         * @param message The optional accompanying message
         */
        public FileResponseRendererActionException(int statusCode, String message) {
            super();
            this.statusCode = statusCode;
            this.message = message;
        }
    } // End of class FileResponseRendererActionException

}
