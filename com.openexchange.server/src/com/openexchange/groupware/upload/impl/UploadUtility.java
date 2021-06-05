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

package com.openexchange.groupware.upload.impl;

import static com.openexchange.java.Strings.isEmpty;
import static java.lang.String.format;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.FileItemHeaders;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadBase.FileUploadIOException;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.james.mime4j.field.contenttype.parser.ContentTypeParser;
import org.slf4j.Logger;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.configuration.ServerConfig.Property;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.upload.StreamedUpload;
import com.openexchange.groupware.upload.StreamedUploadFileListener;
import com.openexchange.groupware.upload.UploadFile;
import com.openexchange.groupware.upload.UploadFileListener;
import com.openexchange.java.Streams;
import com.openexchange.java.util.UUIDs;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.osgi.ServiceListing;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.systemproperties.SystemPropertiesUtils;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.servlet.http.Tools;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * {@link UploadUtility} - Utility class for uploads.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UploadUtility {

    /** The logger */
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UploadUtility.class);

    private static final TIntObjectMap<String> M = new TIntObjectHashMap<String>(13);

    static {
        int pos = 0;
        M.put(pos++, "");
        M.put(pos++, "Kilo");
        M.put(pos++, "Mega");
        M.put(pos++, "Giga");
        M.put(pos++, "Tera");
        M.put(pos++, "Peta");
        M.put(pos++, "Exa");
        M.put(pos++, "Zetta");
        M.put(pos++, "Yotta");
        M.put(pos++, "Xenna");
        M.put(pos++, "W-");
        M.put(pos++, "Vendeka");
        M.put(pos++, "U-");
    }

    private static final AtomicReference<ScheduledTimerTask> TIMER_TASK_REFERENCE = new AtomicReference<ScheduledTimerTask>();

    /**
     * Initializes a new {@link UploadUtility}
     */
    private UploadUtility() {
        super();
    }

    /**
     * Performs shut-down operations.
     */
    public static void shutDown() {
        ScheduledTimerTask timerTask;
        do {
            timerTask = TIMER_TASK_REFERENCE.get();
            if (null == timerTask) {
                return;
            }
        } while (!TIMER_TASK_REFERENCE.compareAndSet(timerTask, null));
        timerTask.cancel(true);
    }

    /**
     * Converts given number of bytes to a human readable format.
     *
     * @param size The number of bytes
     * @return The number of bytes in a human readable format
     */
    public static String getSize(long size) {
        return getSize(size, 2, false, true);
    }

    /**
     * Converts given number of bytes to a human readable format.
     *
     * @param size The number of bytes
     * @param precision The number of digits allowed after dot
     * @param longName <code>true</code> to use unit's long name (e.g. <code>Megabytes</code>) or short name (e.g. <code>MB</code>)
     * @param realSize <code>true</code> to bytes' real size of <code>1024</code> used for detecting proper unit; otherwise
     *            <code>false</code> to narrow unit with <code>1000</code>.
     * @return The number of bytes in a human readable format
     */
    public static String getSize(long size, int precision, boolean longName, boolean realSize) {
        return getSize(size, precision, longName, realSize, Locale.US);
    }

    /**
     * Converts given number of bytes to a human readable format.
     *
     * @param size The number of bytes
     * @param precision The number of digits allowed after dot
     * @param longName <code>true</code> to use unit's long name (e.g. <code>Megabytes</code>) or short name (e.g. <code>MB</code>)
     * @param realSize <code>true</code> to bytes' real size of <code>1024</code> used for detecting proper unit; otherwise
     *            <code>false</code> to narrow unit with <code>1000</code>.
     * @param locale The locale to use to format number
     * @return The number of bytes in a human readable format
     */
    public static String getSize(long size, int precision, boolean longName, boolean realSize, Locale locale) {
        int pos = 0;
        double decSize = size;
        final int base = realSize ? 1024 : 1000;
        while (decSize >= base) {
            decSize /= base;
            pos++;
        }

        StringBuilder sb = new StringBuilder(8);
        int num = (int) Math.pow(10, precision);
        double value = (Math.round(decSize * num)) / (double) num;
        if (precision <= 0) {
            sb.append((int) value);
        } else {
            NumberFormat numberFormat = NumberFormat.getInstance(locale);
            sb.append(numberFormat.format(value));
        }
        sb.append(' ');

        if (longName) {
            sb.append(getSizePrefix(pos)).append("bytes");
        } else {
            final String prefix = getSizePrefix(pos);
            if (0 == prefix.length()) {
                sb.append("bytes");
            } else {
                sb.append(String.valueOf(prefix.charAt(0))).append('B');
            }
        }
        return sb.toString();
    }

    private static String getSizePrefix(int pos) {
        final String prefix = M.get(pos);
        if (prefix != null) {
            return prefix;
        }
        return "?-";
    }

    private static final AtomicReference<ServiceListing<UploadFileListener>> LISTENERS = new AtomicReference<>();

    /**
     * Sets the specified listing of upload file listeners
     *
     * @param listing The listing to set
     */
    public static void setUploadFileListenerLsting(ServiceListing<UploadFileListener> listing) {
        LISTENERS.set(listing);
    }

    private static final AtomicReference<ServiceListing<StreamedUploadFileListener>> STREAMED_LISTENERS = new AtomicReference<>();

    /**
     * Sets the specified listing of upload file listeners
     *
     * @param listing The listing to set
     */
    public static void setStreamedUploadFileListenerLsting(ServiceListing<StreamedUploadFileListener> listing) {
        STREAMED_LISTENERS.set(listing);
    }

    // ----------------------------------------------- Parse/process an upload request -----------------------------------------------------

    /**
     * 1MB threshold.
     */
    private static final int SIZE_THRESHOLD = 1048576;

    /**
     * Creates a new {@code ServletFileUpload} instance using {@link ServerConfig.Property#UploadDirectory} for
     * uploaded files that exceed the threshold of 1MB.
     * <p>
     * The returned <code>ServletFileUpload</code> instance is suitable to for
     * {@link FileUploadBase#parseRequest(org.apache.commons.fileupload.RequestContext) parseRequest()} invocation.
     *
     * @param maxFileSize The maximum allowed size of a single uploaded file
     * @param maxOverallSize The maximum allowed size of a complete request
     * @return A new {@code ServletFileUpload} instance
     */
    public static ServletFileUpload newThresholdFileUploadBase(long maxFileSize, long maxOverallSize) {
        // Create the upload event
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // Set factory constraints; threshold for single files
        factory.setSizeThreshold(SIZE_THRESHOLD);
        factory.setRepository(ServerConfig.getTmpDir());
        // Create a new file upload handler
        ServletFileUpload sfu = new ServletFileUpload(factory);
        // Set the maximum allowed size of a single uploaded file
        sfu.setFileSizeMax(maxFileSize);
        // Set overall request size constraint
        sfu.setSizeMax(maxOverallSize);
        return sfu;
    }

    /**
     * Creates a new {@code ServletFileUpload} instance.
     * <p>
     * The returned <code>ServletFileUpload</code> instance is <b>only</b> suitable to for
     * {@link FileUploadBase#getItemIterator(org.apache.commons.fileupload.RequestContext) getItemIterator()} invocation.
     * <p>
     * <div style="background-color:#FFDDDD; padding:6px; margin:0px;">
     * <b>NOTE</b>:<br>
     * An attempt calling {@link FileUploadBase#parseRequest(org.apache.commons.fileupload.RequestContext) parseRequest()} with the returned
     * <code>ServletFileUpload</code> instance will throw a {@code FileUploadException}.
     * </div>
     *
     * @param maxFileSize The maximum allowed size of a single uploaded file
     * @param maxOverallSize The maximum allowed size of a complete request
     * @return A new {@code ServletFileUpload} instance
     */
    public static ServletFileUpload newFileUploadBase(long maxFileSize, long maxOverallSize) {
        // Create a new file upload handler
        ServletFileUpload sfu = new ServletFileUpload();
        // Set the maximum allowed size of a single uploaded file
        sfu.setFileSizeMax(maxFileSize);
        // Set overall request size constraint
        sfu.setSizeMax(maxOverallSize);
        return new IteratorOnlyServletFileUpload(sfu);
    }

    /**
     * Creates the <code>FileItemIterator</code> from specified arguments.
     *
     * @param req The HTTP reqauest providing the upload
     * @param maxFileSize The max. file size
     * @param maxOverallSize The max. total size
     * @param action The associated action identifier
     * @return The <code>FileItemIterator</code> instance
     * @throws UploadException If <code>FileItemIterator</code> instance cannot be returned
     */
    private static FileItemIterator createFileItemIteratorFor(HttpServletRequest req, long maxFileSize, long maxOverallSize, String action) throws UploadException {
        // Parse the upload request
        try {
            // Get file upload...
            // ... and add some "extra space" as Apache Fileupload considers the maximum allowed size of a complete request (incl. form fields)
            ServletFileUpload upload = newFileUploadBase(maxFileSize, maxOverallSize > 0 ? maxOverallSize + 1024 : maxOverallSize);
            // Check request's character encoding
            if (null == req.getCharacterEncoding()) {
                String defaultEnc = ServerConfig.getProperty(Property.DefaultEncoding);
                try {
                    // Might be ineffective if already fully parsed
                    req.setCharacterEncoding(defaultEnc);
                } catch (@SuppressWarnings("unused") Exception e) { /* Ignore */
                }
                upload.setHeaderEncoding(defaultEnc);
            }
            // Parse multipart request
            ServletRequestContext requestContext = new ServletRequestContext(req);
            return upload.getItemIterator(requestContext);
        } catch (FileSizeLimitExceededException e) {
            throw UploadFileSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true);
        } catch (SizeLimitExceededException e) {
            throw UploadSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true);
        } catch (FileUploadException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                String message = cause.getMessage();
                if (null != message && message.startsWith("Max. byte count of ")) {
                    // E.g. Max. byte count of 10240 exceeded.
                    int pos = message.indexOf(" exceeded", 19 + 1);
                    String limit = message.substring(19, pos);
                    throw UploadException.UploadCode.MAX_UPLOAD_SIZE_EXCEEDED_UNKNOWN.create(cause, getSize(Long.parseLong(limit), 2, false, true));
                }
            } else if (cause instanceof EOFException) {
                // Stream closed/ended unexpectedly
                throw UploadException.UploadCode.UNEXPECTED_EOF.create(cause, cause.getMessage());
            }
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e, null == cause ? e.getMessage() : (null == cause.getMessage() ? e.getMessage() : cause.getMessage()));
        } catch (FileUploadIOException e) {
            // Might wrap a size-limit-exceeded error
            Throwable cause = e.getCause();
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException exc = (FileSizeLimitExceededException) cause;
                throw UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            if (cause instanceof SizeLimitExceededException) {
                SizeLimitExceededException exc = (SizeLimitExceededException) cause;
                throw UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true);
            }
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e, action);
        } catch (IOException e) {
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e, action);
        }
    }

    /**
     * Stream-wise processes specified request's upload provided that request is of content type <code>multipart/*</code>.
     *
     * @param requireStartingFormField <code>true</code> to require that multipart upload request starts with at least one simple form field; otherwise <code>false</code>
     * @param req The HTTP request
     * @param maxFileSize The maximum allowed size of a single uploaded file or <code>-1</code>
     * @param maxOverallSize The maximum allowed size of a complete request or <code>-1</code>
     * @param session The associated session or <code>null</code>
     * @return The streamed upload
     * @throws OXException If streamed upload cannot be returned
     */
    public static StreamedUpload processStreamedUpload(boolean requireStartingFormField, HttpServletRequest req, long maxFileSize, long maxOverallSize, Session session) throws OXException {
        if (!Tools.isMultipartContent(req)) {
            // No multipart content
            throw UploadException.UploadCode.NO_MULTIPART_CONTENT.create();
        }

        // Check action parameter existence
        String action;
        try {
            action = AJAXServlet.getAction(req);
        } catch (OXException e) {
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e.getMessage(), e);
        }

        // Parse the upload request
        FileItemIterator iter = createFileItemIteratorFor(req, maxFileSize, maxOverallSize, action);

        // Get the currently available listeners
        List<StreamedUploadFileListener> listeners = Collections.emptyList();
        {
            ServiceListing<StreamedUploadFileListener> listing = STREAMED_LISTENERS.get();
            if (null != listing) {
                listeners = new ArrayList<>(listing.getServiceList());
            }
        }

        // Yield an ID
        String uuid = UUIDs.getUnformattedStringFromRandom();

        // Create the upload instance
        String fileName = req.getParameter("filename");
        try {
            return new MultipartStreamedUpload(iter, uuid, listeners, action, fileName, req.getCharacterEncoding(), req.getContentLengthLong(), requireStartingFormField, session);
        } catch (MultipartStreamedUpload.MissingStartingFormField e) {
            // Get the currently available listeners
            List<UploadFileListener> uploadListeners = Collections.emptyList();
            {
                ServiceListing<UploadFileListener> listing = LISTENERS.get();
                if (null != listing) {
                    uploadListeners = new ArrayList<>(listing.getServiceList());
                }
            }

            UploadEvent uploadEvent = doProcessUpload(req, maxFileSize, maxOverallSize, e.getItem(), iter, uuid, action, uploadListeners, session);
            UploadException uploadException = UploadException.UploadCode.FAILED_STREAMED_UPLOAD.create(e);
            uploadException.setArgument("__uploadEvent", uploadEvent);
            throw uploadException;
        }
    }

    /**
     * (Statically) Processes specified request's upload provided that request is of content type <code>multipart/*</code>.
     *
     * @param req The request whose upload shall be processed
     * @param maxFileSize The maximum allowed size of a single uploaded file or <code>-1</code>
     * @param maxOverallSize The maximum allowed size of a complete request or <code>-1</code>
     * @param session The associated session or <code>null</code>
     * @return The processed instance of {@link UploadEvent}
     * @throws OXException Id processing the upload fails
     */
    public static UploadEvent processUpload(HttpServletRequest req, long maxFileSize, long maxOverallSize, Session session) throws OXException {
        if (!Tools.isMultipartContent(req)) {
            // No multipart content
            throw UploadException.UploadCode.NO_MULTIPART_CONTENT.create();
        }

        // Check action parameter existence
        String action;
        try {
            action = AJAXServlet.getAction(req);
        } catch (OXException e) {
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e);
        }

        // Parse the upload request
        FileItemIterator iter = createFileItemIteratorFor(req, maxFileSize, maxOverallSize, action);

        // Get the currently available listeners
        List<UploadFileListener> listeners = Collections.emptyList();
        {
            ServiceListing<UploadFileListener> listing = LISTENERS.get();
            if (null != listing) {
                listeners = new ArrayList<>(listing.getServiceList());
            }
        }

        // Yield an ID
        String uuid = UUIDs.getUnformattedStringFromRandom();

        return doProcessUpload(req, maxFileSize, maxOverallSize, null, iter, uuid, action, listeners, session);
    }

    private static UploadEvent doProcessUpload(HttpServletRequest req, long maxFileSize, long maxOverallSize, FileItemStream optFirstItem, FileItemIterator iter, String uuid, String action, List<UploadFileListener> listeners, Session session) throws OXException {
        // Create the upload event
        UploadEvent uploadEvent = new UploadEvent();
        uploadEvent.setAction(action);

        boolean error = true;
        try {
            // Fill upload event instance
            String charEnc;
            {
                String rce = req.getCharacterEncoding();
                charEnc = null == rce ? ServerConfig.getProperty(Property.DefaultEncoding) : rce;
            }

            File uploadDir = ServerConfig.getTmpDir();
            String fileName = req.getParameter("filename");
            long current = 0L;

            FileItemStream ofi = optFirstItem;
            while (null != ofi || iter.hasNext()) {
                FileItemStream item;
                if (null != ofi) {
                    item = ofi;
                    ofi = null;
                } else {
                    item = iter.next();
                }
                if (item.isFormField()) {
                    uploadEvent.addFormField(item.getFieldName(), Streams.stream2string(item.openStream(), charEnc));
                } else {
                    String name = item.getName();
                    if (!isEmpty(name)) {
                        try {
                            UploadFile uf = processUploadedFile(item, uploadDir, isEmpty(fileName) ? name : fileName, current, maxFileSize, maxOverallSize, uuid, session, listeners);
                            current += uf.getSize();
                            uploadEvent.addUploadFile(uf);
                        } catch (OXException e) {
                            // Do not signal this OXException to listeners as it was created by one of the listeners itself
                            throw e;
                        }
                    }
                }
            }
            if (maxOverallSize > 0 && current > maxOverallSize) {
                throwException(uuid, UploadSizeExceededException.create(current, maxOverallSize, true), session, listeners);
            }

            // Signal success
            for (UploadFileListener listener : listeners) {
                listener.onUploadSuceeded(uuid, uploadEvent, session);
            }

            // Everything went well
            error = false;

            return uploadEvent;
        } catch (FileSizeLimitExceededException e) {
            throwException(uuid, UploadFileSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true), session, listeners);
        } catch (SizeLimitExceededException e) {
            throwException(uuid, UploadSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true), session, listeners);
        } catch (FileUploadException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                String message = cause.getMessage();
                if (null != message && message.startsWith("Max. byte count of ")) {
                    // E.g. Max. byte count of 10240 exceeded.
                    int pos = message.indexOf(" exceeded", 19 + 1);
                    String limit = message.substring(19, pos);
                    throwException(uuid, UploadException.UploadCode.MAX_UPLOAD_SIZE_EXCEEDED_UNKNOWN.create(cause, getSize(Long.parseLong(limit), 2, false, true)), session, listeners);
                }
            } else if (cause instanceof EOFException) {
                // Stream closed/ended unexpectedly
                throwException(uuid, UploadException.UploadCode.UNEXPECTED_EOF.create(cause, cause.getMessage()), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, null == cause ? e.getMessage() : (null == cause.getMessage() ? e.getMessage() : cause.getMessage())), session, listeners);
        } catch (FileUploadIOException e) {
            // Might wrap a size-limit-exceeded error
            Throwable cause = e.getCause();
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException exc = (FileSizeLimitExceededException) cause;
                throwException(uuid, UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true), session, listeners);
            }
            if (cause instanceof SizeLimitExceededException) {
                SizeLimitExceededException exc = (SizeLimitExceededException) cause;
                throwException(uuid, UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, action), session, listeners);
        } catch (EOFException e) {
            // Stream closed/ended unexpectedly
            throwException(uuid, UploadException.UploadCode.UNEXPECTED_EOF.create(e, e.getMessage()), session, listeners);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                throwException(uuid, UploadException.UploadCode.UNEXPECTED_TIMEOUT.create(e, new Object[0]), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, action), session, listeners);
        } catch (RuntimeException e) {
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, action), session, listeners);
        } finally {
            if (error) {
                uploadEvent.cleanUp();
            }
        }

        return null; // To keep compiler happy
    }

    /**
     * (Statically) Processes specified request's upload for a simple binary upload.
     *
     * @param requireStartingFormField <code>true</code> to require that multipart upload request starts with at least one simple form field; otherwise <code>false</code>
     * @param req The request whose upload shall be processed
     * @param maxFileSize The maximum allowed size of a single uploaded file or <code>-1</code>
     * @param maxOverallSize The maximum allowed size of a complete request or <code>-1</code>
     * @param session The associated session or <code>null</code>
     * @return The processed instance of {@link UploadEvent}
     * @throws OXException Id processing the upload fails
     */
    public static StreamedUpload processStreamedPutUpload(HttpServletRequest req, long maxFileSize, long maxOverallSize, Session session) throws OXException {
        if (!"PUT".equals(req.getMethod())) {
            throw AjaxExceptionCodes.MISSING_REQUEST_BODY.create();
        }

        // Check action parameter existence
        String action;
        try {
            action = AJAXServlet.getAction(req);
        } catch (OXException e) {
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e);
        }

        // Get the currently available listeners
        List<StreamedUploadFileListener> listeners = Collections.emptyList();
        ServiceListing<StreamedUploadFileListener> listing = STREAMED_LISTENERS.get();
        if (null != listing) {
            listeners = new ArrayList<>(listing.getServiceList());
        }

        // Yield an ID
        String uuid = UUIDs.getUnformattedStringFromRandom();

        String contentType = req.getHeader("Content-type");
        String fileName = req.getParameter("filename");

        InputStream in;
        try {
            long limit;
            if (maxFileSize >= 0) {
                limit = maxFileSize;
                if (maxOverallSize >= 0 && maxOverallSize < limit) {
                    limit = maxOverallSize;
                }
            } else {
                limit = maxOverallSize;
            }
            if (maxOverallSize >= 0) {
                final InputStream requestStream = req.getInputStream();
                in = new org.apache.commons.fileupload.util.LimitedInputStream(requestStream, maxOverallSize) {

                    @Override
                    protected void raiseError(long pSizeMax, long pCount) throws IOException {
                        requestStream.close();
                        FileSizeLimitExceededException e = new FileSizeLimitExceededException(format("The file exceeds its maximum permitted size of %s bytes.", Long.valueOf(pSizeMax)), pCount, pSizeMax);
                        e.setFileName(fileName);
                        throw new FileUploadIOException(e);
                    }
                };
            } else {
                in = req.getInputStream();
            }
        } catch (IOException e) {
            UploadException exception = UploadException.UploadCode.UPLOAD_FAILED.create(e, action);
            for (StreamedUploadFileListener listener : listeners) {
                listener.onUploadFailed(uuid, exception, session);
            }
            throw exception;
        }

        return new SingleStreamedUpload(in, contentType, fileName, uuid, listeners, action, req.getContentLengthLong(), session);
    }

    /**
     * (Statically) Processes specified request's upload for a simple binary upload.
     *
     * @param req The request whose upload shall be processed
     * @param maxFileSize The maximum allowed size of a single uploaded file or <code>-1</code>
     * @param maxOverallSize The maximum allowed size of a complete request or <code>-1</code>
     * @param session The associated session or <code>null</code>
     * @return The processed instance of {@link UploadEvent}
     * @throws OXException Id processing the upload fails
     */
    public static UploadEvent processPutUpload(HttpServletRequest req, long maxFileSize, long maxOverallSize, Session session) throws OXException {
        if (!"PUT".equals(req.getMethod())) {
            throw AjaxExceptionCodes.MISSING_REQUEST_BODY.create();
        }

        // Check action parameter existence
        String action;
        try {
            action = AJAXServlet.getAction(req);
        } catch (OXException e) {
            throw UploadException.UploadCode.UPLOAD_FAILED.create(e);
        }

        // Get the currently available listeners
        List<UploadFileListener> listeners = Collections.emptyList();
        ServiceListing<UploadFileListener> listing = LISTENERS.get();
        if (null != listing) {
            listeners = new ArrayList<>(listing.getServiceList());
        }

        // Yield an ID
        String uuid = UUIDs.getUnformattedStringFromRandom();

        // Create the upload event
        UploadEvent uploadEvent = new UploadEvent();
        uploadEvent.setAction(action);

        String contentType = req.getHeader("Content-type");
        File uploadDir = ServerConfig.getTmpDir();
        String fileName = req.getParameter("filename");
        boolean error = true;
        try {
            uploadEvent.addUploadFile(processUploadedFile(req.getInputStream(), contentType, uploadDir, fileName, maxFileSize, maxOverallSize, uuid, session, listeners));
            error = false;
        } catch (FileSizeLimitExceededException e) {
            throwException(uuid, UploadFileSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true), session, listeners);
        } catch (SizeLimitExceededException e) {
            throwException(uuid, UploadSizeExceededException.create(e.getActualSize(), e.getPermittedSize(), true), session, listeners);
        } catch (FileUploadException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                String message = cause.getMessage();
                if (null != message && message.startsWith("Max. byte count of ")) {
                    // E.g. Max. byte count of 10240 exceeded.
                    int pos = message.indexOf(" exceeded", 19 + 1);
                    String limit = message.substring(19, pos);
                    throwException(uuid, UploadException.UploadCode.MAX_UPLOAD_SIZE_EXCEEDED_UNKNOWN.create(cause, getSize(Long.parseLong(limit), 2, false, true)), session, listeners);
                }
            } else if (cause instanceof EOFException) {
                // Stream closed/ended unexpectedly
                throwException(uuid, UploadException.UploadCode.UNEXPECTED_EOF.create(cause, cause.getMessage()), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, null == cause ? e.getMessage() : (null == cause.getMessage() ? e.getMessage() : cause.getMessage())), session, listeners);
        } catch (FileUploadIOException e) {
            // Might wrap a size-limit-exceeded error
            Throwable cause = e.getCause();
            if (cause instanceof FileSizeLimitExceededException) {
                FileSizeLimitExceededException exc = (FileSizeLimitExceededException) cause;
                throwException(uuid, UploadFileSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true), session, listeners);
            }
            if (cause instanceof SizeLimitExceededException) {
                SizeLimitExceededException exc = (SizeLimitExceededException) cause;
                throwException(uuid, UploadSizeExceededException.create(exc.getActualSize(), exc.getPermittedSize(), true), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, action), session, listeners);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                throwException(uuid, UploadException.UploadCode.UNEXPECTED_TIMEOUT.create(e, new Object[0]), session, listeners);
            }
            throwException(uuid, UploadException.UploadCode.UPLOAD_FAILED.create(e, action), session, listeners);
        } finally {
            if (error) {
                uploadEvent.cleanUp();
            }
        }

        // Signal success
        for (UploadFileListener listener : listeners) {
            listener.onUploadSuceeded(uuid, uploadEvent, session);
        }

        return uploadEvent;
    }

    private static UploadFile processUploadedFile(InputStream stream, String contentType, File uploadDir, String fileName, long maxFileSize, long maxOverallSize, String uuid, Session session, List<UploadFileListener> listeners) throws IOException, FileUploadException, OXException {
        UploadFile retval = new UploadFileImpl();
        retval.setFileName(fileName);

        // Deduce MIME type from passed file name
        String mimeType = MimeType2ExtMap.getContentType(fileName, null);

        // Set associated MIME type
        {
            // Check if we are forced to select the MIME type as signaled by file item
            ContentType safeContentType = getContentTypeSafe(contentType);

            if (null == safeContentType) {
                retval.setContentType(null == mimeType ? contentType : mimeType);
            } else {
                retval.setContentType(safeContentType.getBaseType());
            }
        }

        // Signal basic info prior to processing
        for (UploadFileListener listener : listeners) {
            listener.onBeforeUploadProcessed(uuid, fileName, retval.getFieldName(), retval.getContentType(), session);
        }

        // Track size
        long size = 0;
        // Create temporary file
        File tmpFile = File.createTempFile(PREFIX, null, uploadDir);
        tmpFile.deleteOnExit();

        // Start upload evicter (if not yet done)
        startUploadEvicterIfNotYetDone();

        // Write to temporary file
        InputStream in = null;
        OutputStream out = null;
        boolean error = true;
        try {
            in = Streams.getNonEmpty(stream);
            // Check if readable...
            if (null == in) {
                // Empty file item...
                LOG.warn("Detected empty upload file {}.", retval.getFileName());
            } else {
                out = new FileOutputStream(tmpFile, false);
                int buflen = 65536;
                byte[] buf = new byte[buflen];
                for (int read; (read = in.read(buf, 0, buflen)) > 0;) {
                    out.write(buf, 0, read);
                    size += read;

                    if (maxFileSize > 0) {
                        if (size > maxFileSize) {
                            // Close resources and count remaining bytes
                            Streams.close(out);
                            if (!tmpFile.delete()) {
                                LOG.warn("Temporary file could not be deleted: {}", tmpFile.getPath());
                            }
                            size += Streams.countInputStream(in);
                            throw new FileSizeLimitExceededException("File size exceeded", size, maxFileSize);
                        }
                    }
                    if (maxOverallSize > 0) {
                        if (size > maxOverallSize) {
                            // Close resources and count remaining bytes
                            Streams.close(out);
                            if (!tmpFile.delete()) {
                                LOG.warn("Temporary file could not be deleted: {}", tmpFile.getPath());
                            }
                            size += Streams.countInputStream(in);
                            retval.setSize(size);
                            return retval;
                        }
                    }
                }
                out.flush();
            }

            // Signal success after processing
            for (UploadFileListener listener : listeners) {
                listener.onAfterUploadProcessed(uuid, retval, session);
            }

            error = false;
        } finally {
            Streams.close(in, out);
            if (error) {
                if (!tmpFile.delete()) {
                    LOG.warn("Temporary file could not be deleted: {}", tmpFile.getPath());
                }
            }
        }

        // Apply temporary file and its size
        retval.setSize(size);
        retval.setTmpFile(tmpFile);
        return retval;
    }

    /**
     * Advertises specified exception to listeners and re-throws it
     *
     * @param uuid The upload's UUID
     * @param exception The exception to advertise
     * @param session The associated session
     * @param listeners The listeners to notify
     * @throws OXException The re-thrown exception
     */
    private static void throwException(String uuid, OXException exception, Session session, List<UploadFileListener> listeners) throws OXException {
        for (UploadFileListener listener : listeners) {
            listener.onUploadFailed(uuid, exception, session);
        }
        throw exception;
    }

    private static final String PREFIX = "openexchange-upload-" + com.openexchange.exception.OXException.getServerId() + "-";

    private static UploadFile processUploadedFile(FileItemStream item, File uploadDir, String fileName, long current, long maxFileSize, long maxOverallSize, String uuid, Session session, List<UploadFileListener> listeners) throws IOException, FileUploadException, OXException {
        UploadFile retval = new UploadFileImpl();
        retval.setFieldName(item.getFieldName());
        retval.setFileName(fileName);

        FileItemHeaders headers = item.getHeaders();
        String contentId = headers.getHeader("Content-Id");
        retval.setContentId(contentId);

        // Deduce MIME type from passed file name
        String mimeType = MimeType2ExtMap.getContentType(fileName, null);

        // Set associated MIME type
        {
            // Check if we are forced to select the MIME type as signaled by file item
            String forcedMimeType = headers.getHeader("X-Forced-MIME-Type");
            if (null == forcedMimeType) {
                String itemContentType = item.getContentType();
                ContentType contentType = getContentTypeSafe(itemContentType);
                if (null == contentId && contentType != null) {
                    contentId = contentType.getParameter("cid");
                    retval.setContentId(contentId);
                }

                if (null == contentType) {
                    retval.setContentType(null == mimeType ? itemContentType : mimeType);
                } else {
                    retval.setContentType(contentType.getBaseType());
                }
            } else if (AJAXRequestDataTools.parseBoolParameter(forcedMimeType)) {
                retval.setContentType(item.getContentType());
            } else {
                // Valid MIME type specified?
                try {
                    ContentTypeParser parser = new ContentTypeParser(new StringReader(forcedMimeType));
                    parser.parseAll();
                    retval.setContentType(new StringBuilder(parser.getType()).append('/').append(parser.getSubType()).toString());
                } catch (@SuppressWarnings("unused") Exception e) {
                    // Assume invalid value
                    retval.setContentType(null == mimeType ? item.getContentType() : mimeType);
                }
            }
        }

        // Signal basic info prior to processing
        for (UploadFileListener listener : listeners) {
            listener.onBeforeUploadProcessed(uuid, fileName, retval.getFieldName(), retval.getContentType(), session);
        }

        // Track size
        long size = 0;

        // Check if overall size is already exceeded
        if (maxOverallSize > 0 && current > maxOverallSize) {
            // Count current bytes
            size = Streams.countInputStream(item.openStream());
            retval.setSize(size);
            return retval;
        }

        // Create temporary file
        File tmpFile = File.createTempFile(PREFIX, null, uploadDir);
        tmpFile.deleteOnExit();

        // Start upload evicter (if not yet done)
        startUploadEvicterIfNotYetDone();

        // Write to temporary file
        InputStream in = null;
        OutputStream out = null;
        boolean error = true;
        try {
            in = Streams.getNonEmpty(item.openStream());
            // Check if readable...
            if (null == in) {
                // Empty file item...
                LOG.warn("Detected empty upload file {}.", retval.getFileName());
            } else {
                out = new FileOutputStream(tmpFile, false);
                int buflen = 65536;
                byte[] buf = new byte[buflen];
                for (int read; (read = in.read(buf, 0, buflen)) > 0;) {
                    out.write(buf, 0, read);
                    size += read;

                    if (maxFileSize > 0) {
                        if (size > maxFileSize) {
                            // Close resources and count remaining bytes
                            Streams.close(out);
                            tmpFile.delete();
                            size += Streams.countInputStream(in);
                            throw new FileSizeLimitExceededException("File size exceeded", size, maxFileSize);
                        }
                    }
                    if (maxOverallSize > 0) {
                        if ((current + size) > maxOverallSize) {
                            // Close resources and count remaining bytes
                            Streams.close(out);
                            tmpFile.delete();
                            size += Streams.countInputStream(in);
                            retval.setSize(size);
                            return retval;
                        }
                    }
                }
                out.flush();
            }

            // Signal success after processing
            for (UploadFileListener listener : listeners) {
                listener.onAfterUploadProcessed(uuid, retval, session);
            }

            error = false;
        } finally {
            Streams.close(in, out);
            if (error) {
                tmpFile.delete();
            }
        }

        // Apply temporary file and its size
        retval.setSize(size);
        retval.setTmpFile(tmpFile);
        return retval;
    }

    static ContentType getContentTypeSafe(String contentType) {
        if (null == contentType) {
            return null;
        }

        try {
            return new ContentType(contentType);
        } catch (Exception e) {
            LOG.debug("MIME type could not be parsed", e);
            return null;
        }
    }

    private static void startUploadEvicterIfNotYetDone() {
        ScheduledTimerTask timerTask = TIMER_TASK_REFERENCE.get();
        if (null == timerTask) {
            synchronized (UploadUtility.class) {
                timerTask = TIMER_TASK_REFERENCE.get();
                if (null == timerTask) {
                    try {
                        UploadEvicter evicter = new UploadEvicter(PREFIX, LOG);
                        TimerService timerService = ServerServiceRegistry.getInstance().getService(TimerService.class);
                        long delay = 300000; // 5 minutes
                        timerTask = timerService.scheduleWithFixedDelay(evicter, 3000, delay);
                        TIMER_TASK_REFERENCE.set(timerTask);
                    } catch (Exception e) {
                        LOG.warn("Failed to initialze {}", UploadEvicter.class.getSimpleName(), e);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks for expired upload files.
     * <p>
     * Derived from <a href="http://www.jroller.com/javabean/entry/solving_an_outofmemoryerror_java_6">Java 6 DeleteOnExitHook memory leak</a> from Cedrik LIME.
     */
    private static final class UploadEvicter implements Runnable {

        private final org.slf4j.Logger logger;
        private final String prefix;
        private final Object mutex;
        private final LinkedHashSet<String> files;
        private int filesLastSize = 0; // contains files.size() from last iteration

        /**
         * Initializes a new {@link UploadUtility.UploadEvicter}.
         *
         * @throws IllegalStateException If initialization fails
         */
        UploadEvicter(String prefix, org.slf4j.Logger logger) {
            super();
            this.logger = logger;
            this.prefix = prefix;
            try {
                Class<?> clazz = Class.forName("java.io.DeleteOnExitHook");
                Field[] declaredFields = clazz.getDeclaredFields();
                Field filesField = getFieldFrom("files", declaredFields);
                if (filesField == null) {
                    throw new IllegalStateException("Can't initialize. Are you running an incompatible java version?");
                }
                filesField.setAccessible(true);
                @SuppressWarnings("unchecked") LinkedHashSet<String> files = (LinkedHashSet<String>) filesField.get(null);
                if (null == files) {
                    throw new IllegalStateException("Can't initialize. Are you running Java 6+ or within a restricted SecurityManager?");
                }
                this.files = files;
                mutex = isJavaVersionGreaterThan160_20() ? clazz : files;
                synchronized (mutex) {
                    filesLastSize = files.size();
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void run() {
            try {
                synchronized (mutex) {
                    // Remove non-existing files from DeleteOnExitHook.files
                    logger.debug("DeleteOnExitHook went from {} to {} file entries.", Integer.valueOf(filesLastSize), Integer.valueOf(files.size()));
                    int existent = 0, removed = 0;
                    int total = removed + existent;
                    for (Iterator<String> iterator = files.iterator(); iterator.hasNext() && total < filesLastSize;) {
                        String fileName = iterator.next();
                        if (fileName == null || !new File(fileName).exists()) {
                            // No file by given filename exists: remove (old), useless entry
                            logger.trace("Removing file entry {}", fileName);
                            iterator.remove();
                            ++removed;
                        } else {
                            ++existent;
                        }
                        ++total;
                    }
                    if (removed > 0) {
                        logger.debug("removed {}/{} entries", Integer.valueOf(removed), Integer.valueOf(total));
                    }
                    filesLastSize = files.size();

                    // Check orphaned files
                    if (filesLastSize > 0) {
                        long stamp = System.currentTimeMillis() - 1800000; // Older than 30 minutes
                        List<String> toBeDeleted = new ArrayList<String>(files);
                        Collections.reverse(toBeDeleted);
                        for (String filename : toBeDeleted) {
                            File file = new File(filename);
                            if (file.getName().startsWith(prefix) && file.lastModified() < stamp) {
                                logger.debug("Found expired file entry {}. Deleting...", filename);
                                if (!file.delete() && file.exists()) {
                                    logger.warn("Temporary file could not be deleted: {}", file.getPath());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("{}", e);
            }
        }

        private Field getFieldFrom(String name, Field[] declaredFields) {
            for (Field field : declaredFields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
            return null;
        }

        private boolean isJavaVersionGreaterThan160_20() {
            try {
                float javaClassVersion = Float.parseFloat(getSystemProperty("java.class.version"));
                // Java <= 5
                if (javaClassVersion <= 49.0f) {
                    return false;
                }
                // Java >= 7
                if (javaClassVersion > 50.0f) {
                    return true;
                }
                // This is Java 6. Get patch level version.
                // Assume JAVA_VERSION is 1.6.0_x
                String javaVersion = getJavaVersionTrimmed();
                if (javaVersion == null) {
                    // Assume "yes"
                    return true;
                }
                assert javaVersion.startsWith("1.6") : javaVersion;
                String patchLevelStr = javaVersion.substring(javaVersion.indexOf('_') + 1);
                return Integer.parseInt(patchLevelStr) > 20;
            } catch (@SuppressWarnings("unused") RuntimeException e) {
                // Assume "yes"
                return true;
            }
        }

        /**
         * Trims the text of the java version to start with numbers.
         *
         * @return the trimmed java version
         */
        private String getJavaVersionTrimmed() {
            String javaVersion = getSystemProperty("java.version");
            if (javaVersion != null) {
                for (int i = 0; i < javaVersion.length(); i++) {
                    char ch = javaVersion.charAt(i);
                    if (ch >= '0' && ch <= '9') {
                        return javaVersion.substring(i);
                    }
                }
            }
            return null;
        }

        /**
         * Gets a System property, defaulting to <code>null</code> if the property cannot be read.
         *
         * @param property The system property name
         * @return the system property value or <code>null</code> if a security problem occurs
         */
        private String getSystemProperty(String property) {
            try {
                return SystemPropertiesUtils.getProperty(property);
            } catch (SecurityException ex) {
                logger.warn("Encountered a SecurityException reading the system property '{}'; returning null instead.", property, ex);
                return null;
            }
        }
    }

}
