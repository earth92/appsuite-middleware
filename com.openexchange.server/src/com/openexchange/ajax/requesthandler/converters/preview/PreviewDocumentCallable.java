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

package com.openexchange.ajax.requesthandler.converters.preview;

import static com.openexchange.ajax.requesthandler.converters.preview.AbstractPreviewResultConverter.getContentType;
import static com.openexchange.ajax.requesthandler.converters.preview.AbstractPreviewResultConverter.getFileHolderFromResult;
import static com.openexchange.ajax.requesthandler.converters.preview.AbstractPreviewResultConverter.getUserLanguage;
import static com.openexchange.ajax.requesthandler.converters.preview.AbstractPreviewResultConverter.streamIsEof;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.AJAXUtility;
import com.openexchange.ajax.container.ModifyableFileHolder;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.conversion.DataProperties;
import com.openexchange.conversion.SimpleData;
import com.openexchange.exception.OXException;
import com.openexchange.java.InterruptibleInputStream;
import com.openexchange.java.Reference;
import com.openexchange.java.Streams;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.preview.ContentTypeChecker;
import com.openexchange.preview.Delegating;
import com.openexchange.preview.PreviewDocument;
import com.openexchange.preview.PreviewExceptionCodes;
import com.openexchange.preview.PreviewOutput;
import com.openexchange.preview.PreviewService;
import com.openexchange.preview.RemoteInternalPreviewService;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.tools.session.ServerSession;

/**
 *
 * {@link PreviewDocumentCallable} - {@link Callable} to generate a PreviewDocument via a given {@link PreviewService}
 *
 * @author <a href="mailto:marc.arens@open-xchange.com">Marc Arens</a>
 * @since 7.6.1
 */
final class PreviewDocumentCallable extends AbstractTask<PreviewDocument> {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewDocumentCallable.class);

    private final AJAXRequestData requestData;
    private final String previewLanguage;
    private final PreviewOutput previewOutput;
    private final ServerSession session;
    private volatile IFileHolder fileHolder;
    private volatile InterruptibleInputStream stream;
    private final PreviewService previewService;
    private final boolean respectLanguage;

    /**
     * Initializes a new {@link PreviewDocumentCallable}.
     *
     * @param result To get the FileHolder and associated metadata for the requested document
     * @param requestData To get requested parameters for document/preview action
     * @param previewOutput The requested output format of the preview
     * @param session The current {@link ServerSession}
     * @param previewService The {@link PreviewService to use}
     * @param respectLanguage Use true if the language can influence the preview (e.g. date formats in documents)
     * @throws OXException If we aren't able to gather all required infos to construct this callable
     */
    PreviewDocumentCallable(AJAXRequestResult result, AJAXRequestData requestData, PreviewOutput previewOutput, ServerSession session, PreviewService previewService, boolean respectLanguage) throws OXException {
        super();
        this.fileHolder = getFileHolderFromResult(result);
        this.requestData = requestData;
        this.previewLanguage = getUserLanguage(session);
        this.previewOutput = previewOutput;
        this.session = session;
        this.previewService = previewService;
        this.respectLanguage = respectLanguage;
    }

    @Override
    public PreviewDocument call() throws Exception {
        try {
            // Check file holder's content
            IFileHolder fileHolder = this.fileHolder;
            InterruptibleInputStream stream1;
            {
                InputStream in = fileHolder.getStream();
                if (0 == fileHolder.getLength()) {
                    Streams.close(in, fileHolder);
                    return PreviewConst.DEFAULT_PREVIEW_DOCUMENT;
                }
                final Reference<InputStream> ref = new Reference<InputStream>();
                if (streamIsEof(in, ref)) {
                    Streams.close(in, fileHolder);
                    return PreviewConst.DEFAULT_PREVIEW_DOCUMENT;
                }
                in = ref.getValue();
                stream1 = new InterruptibleInputStream(in);
                this.stream = stream1;
            }

            // Obtain preview either using running or separate thread
            PreviewService previewService = ServerServiceRegistry.getInstance().getService(PreviewService.class);

            // Name-wise MIME type detection
            {
                String mimeType = MimeType2ExtMap.getContentType(fileHolder.getName(), null);
                if (null == mimeType) {
                    // Unknown. Then detect MIME type by content.
                    ThresholdFileHolder tfh = new ThresholdFileHolder();
                    try {
                        tfh.write(stream);
                        tfh.setContentInfo(fileHolder);
                        fileHolder = tfh;
                        tfh = null;
                    } finally {
                        Streams.close(tfh);
                    }
                    mimeType = AJAXUtility.detectMimeType(fileHolder.getStream());
                    this.fileHolder = fileHolder;
                    stream1 = new InterruptibleInputStream(fileHolder.getStream());
                    this.stream = stream1;
                }
                ModifyableFileHolder modifyableFileHolder = new ModifyableFileHolder(fileHolder);
                modifyableFileHolder.setContentType(mimeType);
                fileHolder = modifyableFileHolder;
            }

            // Prepare properties for preview generation
            DataProperties dataProperties = new DataProperties(12);
            String mimeType = getContentType(fileHolder, previewService instanceof ContentTypeChecker ? (ContentTypeChecker) previewService : null);
            dataProperties.put("PreviewWidth", requestData.getParameter("width"));
            dataProperties.put("PreviewHeight", requestData.getParameter("height"));
            dataProperties.put("PreviewScaleType", requestData.getParameter("scaleType"));
            if (respectLanguage) {
                dataProperties.put("PreviewLanguage", previewLanguage);
            }
            dataProperties.put(DataProperties.PROPERTY_NAME, fileHolder.getName());
            dataProperties.put(DataProperties.PROPERTY_CONTENT_TYPE, mimeType);

            // Generate preview
            return previewService.getPreviewFor(new SimpleData<InputStream>(stream, dataProperties), previewOutput, session, 1);
        } catch (RuntimeException rte) {
            throw PreviewExceptionCodes.ERROR.create(rte, rte.getMessage());
        }
    }

    /**
     * Interrupt and cleanup internal resources
     *
     * @throws IOException if an I/O error occurs
     */
    public void interrupt() throws IOException {
        try {
            IFileHolder fileHolder = this.fileHolder;
            if (null != fileHolder) {
                fileHolder.close();
                this.fileHolder = null;
            }
        } finally {
            InterruptibleInputStream stream = this.stream;
            if (null != stream) {
                stream.interrupt();
                this.stream = null;
            }
        }
    }

    /**
     * Detect the recommended await threshold for the previewService or use the given default threshold.
     *
     * @param defaultThreshold The default threshold in milliseconds to use if the previewService doesn't specify any.
     * @return The recommended await threshold for the previewService or the given default threshold in milliseconds.
     * TODO: Check if {@link DelegationPreviewService} is completely obsolete and this code can be cleaned up!
     */
    public long getAwaitThreshold(long defaultThreshold) {
        long timeToWaitMillis = 0;

        final String mimeType = MimeType2ExtMap.getContentType(fileHolder.getName(), null);
        PreviewService candidate = null;
        if ((null != mimeType) && (previewService instanceof Delegating)) {
            // Determine candidate
            try {
                candidate = ((Delegating) previewService).getBestFitOrDelegate(mimeType, previewOutput, session);
            } catch (OXException e) {
                LOG.info("Failed to find delegate for mime-type {} and preview output {}", mimeType, previewOutput);
            }
            if (candidate instanceof RemoteInternalPreviewService) {
                timeToWaitMillis = ((RemoteInternalPreviewService) candidate).getTimeToWaitMillis();
            }
        }

        return timeToWaitMillis == 0 ? defaultThreshold : timeToWaitMillis;
    }

}