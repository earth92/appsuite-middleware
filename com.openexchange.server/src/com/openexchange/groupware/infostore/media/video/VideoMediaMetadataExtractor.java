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

package com.openexchange.groupware.infostore.media.video;

import static com.openexchange.java.Autoboxing.I;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.containers.mkv.MKVParser;
import org.jcodec.containers.mkv.MKVType;
import org.jcodec.containers.mkv.boxes.EbmlBase;
import org.jcodec.containers.mkv.boxes.EbmlFloat;
import org.jcodec.containers.mkv.boxes.EbmlMaster;
import org.jcodec.containers.mkv.boxes.EbmlUint;
import org.slf4j.Logger;
import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import com.openexchange.ajax.container.TmpFileFileHolder;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.media.Effort;
import com.openexchange.groupware.infostore.media.ExtractorResult;
import com.openexchange.groupware.infostore.media.InputStreamProvider;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractor;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractors;
import com.openexchange.groupware.infostore.media.video.mkv.MkvUtility;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.session.Session;

/**
 * {@link VideoMediaMetadataExtractor} - The extractor for video files.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class VideoMediaMetadataExtractor implements MediaMetadataExtractor {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(VideoMediaMetadataExtractor.class);
    }

    private static final VideoMediaMetadataExtractor INSTANCE = new VideoMediaMetadataExtractor();

    /**
     * Gets the instance
     *
     * @return The instance
     */
    public static VideoMediaMetadataExtractor getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    private final Tika tika;

    /**
     * Initializes a new {@link VideoMediaMetadataExtractor}.
     */
    public VideoMediaMetadataExtractor() {
        super();
        tika = new Tika(TikaConfig.getDefaultConfig());
    }

    private String indicatesVideo(DocumentMetadata document) {
        // Only read videos
        String mimeType = mimeTypeIndicatesVideo(document.getFileMIMEType());
        if (null != mimeType) {
            return mimeType;
        }

        mimeType = mimeTypeIndicatesVideo(getMimeTypeByFileName(document.getFileName()));
        return mimeType;
    }

    private String mimeTypeIndicatesVideo(String mimeType) {
        // Starts with "video/"
        return (null != mimeType && Strings.asciiLowerCase(mimeType).startsWith("video/")) ? mimeType : null;
    }

    private String getMimeTypeByFileName(String fileName) {
        return MimeType2ExtMap.getContentType(fileName, null);
    }

    @Override
    public boolean isApplicable(DocumentMetadata document) throws OXException {
        return null != indicatesVideo(document);
    }

    @Override
    public Effort estimateEffort(Session session, InputStream in, DocumentMetadata document, Map<String, Object> optArguments) throws OXException {
        if (null == indicatesVideo(document)) {
            return Effort.NOT_APPLICABLE;
        }

        return Effort.HIGH_EFFORT;
    }

    // mp4
    private static final int MP4_CREATION_TIME = 256;    // E.g. "Thu Mar 16 13:48:44 CET 2017"
    private static final int MP4_DURATION_SECONDS = 259; // E.g. "7"

    // mp4 video
    private static final int MP4_VIDEO_WIDTH_PIXELS = 104;    // E.g. "640"
    private static final int MP4_VIDEO_HEIGHT_PIXELS = 105;   // E.g. "368"

    // iptc
    private static final int IPTC_DATE_CREATED = 0x0237;
    private static final int IPTC_TIME_CREATED = 0X023C;

    @Override
    public ExtractorResult extractAndApplyMediaMetadata(InputStream optStream, InputStreamProvider provider, DocumentMetadata document, Map<String, Object> arguments) throws OXException {
        if (null == provider) {
            throw OXException.general("Stream must not be null.");
        }
        if (null == document) {
            throw OXException.general("Document must not be null.");
        }

        String mimeType = indicatesVideo(document);
        if (null == mimeType) {
            return ExtractorResult.NONE;
        }

        InputStream in = optStream;
        BufferedInputStream bufferedStream = null;
        try {
            in = null == in ? provider.getInputStream() : in;

            Date captureDate = null;
            Long width = null;
            Long height = null;
            Long durationMillis = null;
            Map<String, Object> mediaMeta = new LinkedHashMap<String, Object>(4);

            boolean processFile = true;
            if (mimeType.indexOf("matroska") >= 0) {
                processFile = false;
                FileInputStream fileInputStream = null;
                File tempFile = TmpFileFileHolder.newTempFile("open-xchange-mme-", false);
                try {
                    FileUtils.copyInputStreamToFile(in, tempFile);
                    Streams.close(in);
                    in = null;

                    fileInputStream = new FileInputStream(tempFile);
                    MKVParser reader = new MKVParser(new FileChannelWrapper(fileInputStream.getChannel()));
                    List<EbmlMaster> ebmlTree = reader.parse();

                    // com.openexchange.groupware.infostore.media.video.mkv.MkvUtility.printParsedTree(System.out, ebmlTree);

                    MKVType[] path = new MKVType[] { MKVType.Segment, MKVType.Tracks, MKVType.TrackEntry, MKVType.Video, MKVType.PixelWidth };
                    EbmlBase[] allPixelWidth = MKVType.findAllTree(ebmlTree, EbmlBase.class, path);
                    if (allPixelWidth.length > 0) {
                        EbmlUint pixelWidth = (EbmlUint) allPixelWidth[0];
                        width = Long.valueOf(pixelWidth.getUint());
                    }

                    path = new MKVType[] { MKVType.Segment, MKVType.Tracks, MKVType.TrackEntry, MKVType.Video, MKVType.PixelHeight };
                    EbmlBase[] allPixelHeight = MKVType.findAllTree(ebmlTree, EbmlBase.class, path);
                    if (allPixelHeight.length > 0) {
                        EbmlUint pixelHeight = (EbmlUint) allPixelHeight[0];
                        height = Long.valueOf(pixelHeight.getUint());
                    }

                    path = new MKVType[] { MKVType.Segment, MKVType.Info, MKVType.Duration };
                    EbmlBase[] allDuration = MKVType.findAllTree(ebmlTree, EbmlBase.class, path);
                    if (allDuration.length > 0) {
                        EbmlFloat duration = (EbmlFloat) allDuration[0];
                        double d = duration.getDouble();
                        durationMillis = Long.valueOf((long) d);
                    }

                    mediaMeta.put("mkv", MkvUtility.treeToMap(ebmlTree));

                    // https://www.matroska.org/technical/specs/index.html#Track
                    // https://www.matroska.org/technical/diagram/index.html

                    // https://github.com/jcodec/jcodec/blob/master/src/test/java/org/jcodec/containers/mkv/MKVParserTest.java
                } finally {
                    Streams.close(fileInputStream);
                    FileUtils.deleteQuietly(tempFile);
                }
            }

            if (processFile) {
                // Try to detect file type with metadata-extractor
                bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);
                FileType detectedFileType;
                try {
                    bufferedStream.mark(65536);
                    detectedFileType = FileTypeDetector.detectFileType(bufferedStream);
                } catch (IOException e) {
                    LoggerHolder.LOG.debug("", e);
                    return ExtractorResult.NONE;
                }

                // Try to reset stream
                try {
                    bufferedStream.reset();
                } catch (Exception e) {
                    // Reset failed
                    LoggerHolder.LOG.debug("Failed to reset stream for extracting video metadata from document {} with version {}", I(document.getId()), I(document.getVersion()), e);
                    Streams.close(bufferedStream, in);
                    in = provider.getInputStream();
                    bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);
                }
                bufferedStream.mark(65536);

                com.drew.metadata.Metadata metadata;
                if (FileType.Unknown == detectedFileType || (metadata = ImageMetadataReader.readMetadata(bufferedStream, -1, detectedFileType)).getDirectoryCount() <= 0) {
                    // Use Apache Tika
                    org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
                    tika.parse(bufferedStream, tikaMetadata);

                    // Examine metadata
                    String[] names = tikaMetadata.names();
                    if (names.length <= 2) {
                        return ExtractorResult.NONE;
                    }

                    Map<String, Map<String, Object>> videoMetadataMap = new LinkedHashMap<>(names.length);
                    for (String name : names) {
                        String value = tikaMetadata.get(name);
                        Map<String, Object> entry = new LinkedHashMap<>(4);
                        String id = Strings.asciiLowerCase(name);
                        entry.put("id", id);
                        entry.put("value", value);
                        entry.put("name", name);
                        videoMetadataMap.put(name, entry);

                        if ("date".equals(id)) {
                            captureDate = MediaMetadataExtractors.parseDateStringToDate(value, null);
                        } else if (id.indexOf("duration") >= 0) {
                            double d = Double.parseDouble(value);
                            durationMillis = Long.valueOf((long) (d * 1000L));
                        } else if (id.indexOf("width") >= 0) {
                            width = MediaMetadataExtractors.getLongValue(value);
                        } else if (id.indexOf("height") >= 0) {
                            height = MediaMetadataExtractors.getLongValue(value);
                        }
                    }
                    mediaMeta.put("tika", videoMetadataMap);
                } else {
                    // Iterate directories
                    for (Directory directory : metadata.getDirectories()) {
                        Collection<Tag> tags = directory.getTags();
                        Map<String, Map<String, Object>> directoryMap = new LinkedHashMap<>(tags.size());
                        for (Tag tag : tags) {
                            Map<String, Object> entry = new LinkedHashMap<>(4);

                            Integer tagType = Integer.valueOf(tag.getTagType());
                            entry.put("id", tagType);

                            Object value = directory.getObject(tagType.intValue());
                            entry.put("value", value);

                            String name = tag.getTagName();
                            if (null != name) {
                                entry.put("name", name);
                            }

                            String description = tag.getDescription();
                            if (null != description) {
                                entry.put("description", description);
                            }

                            directoryMap.put(name == null ? tagType.toString() : name, entry);
                        }

                        String directoryName = Strings.asciiLowerCase(directory.getName());
                        if (directoryName.indexOf("iptc") >= 0) {
                            if (null == captureDate) {
                                String dateString = (String) directory.getObject(IPTC_DATE_CREATED);
                                String timeString = (String) directory.getObject(IPTC_TIME_CREATED);
                                captureDate = MediaMetadataExtractors.parseDateStringToDate(dateString, timeString);
                            }

                            mediaMeta.put("iptc", directoryMap);
                        } else if (directoryName.equals("mp4 video")) {
                            {
                                Object obj = directory.getObject(MP4_VIDEO_WIDTH_PIXELS);
                                width = MediaMetadataExtractors.getLongValue(obj);
                            }
                            {
                                Object obj = directory.getObject(MP4_VIDEO_HEIGHT_PIXELS);
                                height = MediaMetadataExtractors.getLongValue(obj);
                            }

                            mediaMeta.put("mp4_video", directoryMap);
                        } else if (directoryName.equals("mp4")) {
                            {
                                Object obj = directory.getObject(MP4_CREATION_TIME);
                                captureDate = MediaMetadataExtractors.getDateValue(obj);
                            }
                            {
                                Object obj = directory.getObject(MP4_DURATION_SECONDS);
                                durationMillis = MediaMetadataExtractors.getLongValue(obj);
                            }

                            mediaMeta.put("mp4", directoryMap);
                        } else {
                            mediaMeta.put(directoryName, directoryMap);
                        }
                    }
                }
            }

            // -----------------------------------------------------------------------------------------------------------

            if (null != width) {
                document.setWidth(width.longValue());
            }
            if (null != height) {
                document.setHeight(height.longValue());
            }
            if (null != durationMillis) {
                //document.setDuration(durationMillis.longValue());
            }
            if (null != captureDate) {
                document.setCaptureDate(captureDate);
            }
            document.setMediaMeta(mediaMeta);

            return ExtractorResult.SUCCESSFUL;
        } catch (Exception e) {
            LoggerHolder.LOG.warn("Failed to extract video metadata from document {} with version {}", I(document.getId()), I(document.getVersion()), e);
            return ExtractorResult.ACCEPTED_BUT_FAILED;
        } finally {
            Streams.close(bufferedStream, in);
        }
    }

}
