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

package com.openexchange.groupware.infostore.media.image;

import static com.eaio.util.text.HumanTime.exactly;
import static com.openexchange.java.Autoboxing.I;
import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CasioType2MakernoteDirectory;
import com.drew.metadata.gif.GifImageDirectory;
import com.drew.metadata.heif.HeifDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.infostore.DocumentMetadata;
import com.openexchange.groupware.infostore.InfostoreExceptionCodes;
import com.openexchange.groupware.infostore.media.Effort;
import com.openexchange.groupware.infostore.media.ExtractorResult;
import com.openexchange.groupware.infostore.media.InputStreamProvider;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractor;
import com.openexchange.groupware.infostore.media.MediaMetadataExtractors;
import com.openexchange.groupware.infostore.media.metadata.KnownDirectory;
import com.openexchange.groupware.infostore.media.metadata.MetadataMapImpl;
import com.openexchange.groupware.infostore.media.metadata.MetadataUtility;
import com.openexchange.imagetransformation.ImageMetadataService;
import com.openexchange.java.GeoLocation;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link ImageMediaMetadataExtractor} - The extractor for image files.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.2
 */
public class ImageMediaMetadataExtractor implements MediaMetadataExtractor {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ImageMediaMetadataExtractor.class);

    private static enum KnownArgument {

        FILE_TYPE("image.fileType"),
        TIMEZONE("timezone")
        ;

        private final String id;

        private KnownArgument(String id) {
            this.id = id;
        }

        /**
         * Gets the argument's identifier
         *
         * @return The identifier
         */
        String getId() {
            return id;
        }
    }

    private static final ImageMediaMetadataExtractor INSTANCE = new ImageMediaMetadataExtractor();

    /**
     * Gets the instance
     *
     * @return The instance
     */
    public static ImageMediaMetadataExtractor getInstance() {
        return INSTANCE;
    }

    // --------------------------------------------------------------------------------------------------------------------------------

    private final Set<FileType> fastTypes;
    private final boolean writeMediaMetadata;

    /**
     * Initializes a new {@link ImageMediaMetadataExtractor}.
     */
    private ImageMediaMetadataExtractor() {
        super();
        writeMediaMetadata = false;
        this.fastTypes = EnumSet.of(FileType.Jpeg, FileType.Psd, FileType.Eps, FileType.Bmp, FileType.Ico, FileType.Pcx, FileType.Heif);
    }

    private boolean indicatesImage(DocumentMetadata document) {
        // Only read images
        return (mimeTypeIndicatesImage(document.getFileMIMEType()) || mimeTypeIndicatesImage(getMimeTypeByFileName(document.getFileName())));
    }

    private boolean mimeTypeIndicatesImage(String mimeType) {
        // Starts with "image/"
        return (null != mimeType && Strings.asciiLowerCase(mimeType).startsWith("image/"));
    }

    private String getMimeTypeByFileName(String fileName) {
        return MimeType2ExtMap.getContentType(fileName, null);
    }

    private boolean fileTypeIndicatesImage(FileType fileType) {
        String mimeType = fileType == null ? null : fileType.getMimeType();
        return (mimeType != null && Strings.asciiLowerCase(mimeType).startsWith("image/"));
    }

    @Override
    public boolean isApplicable(DocumentMetadata document) throws OXException {
        return indicatesImage(document);
    }

    @Override
    public Effort estimateEffort(Session session, InputStream in, DocumentMetadata document, Map<String, Object> optArguments) throws OXException {
        if (false == indicatesImage(document)) {
            return Effort.NOT_APPLICABLE;
        }

        BufferedInputStream bufferedInputStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 64);

        FileType detectedFileType;
        try {
            detectedFileType = FileTypeDetector.detectFileType(bufferedInputStream);
        } catch (IOException e) {
            throw InfostoreExceptionCodes.IO_ERROR.create(e, e.getMessage());
        } catch (@SuppressWarnings("unused") AssertionError e) {
            detectedFileType = FileType.Unknown;
        }

        if (FileType.Unknown == detectedFileType) {
            LOGGER.warn("Failed to estimate effort for extraction of image metadata from document {} with version {}. File type is unknown.", I(document.getId()), I(document.getVersion()));
            return Effort.NOT_APPLICABLE;
        }

        if (false == fileTypeIndicatesImage(detectedFileType)) {
            return Effort.NOT_APPLICABLE;
        }

        if (null != optArguments) {
            optArguments.put(KnownArgument.FILE_TYPE.getId(), detectedFileType);
            optArguments.put(KnownArgument.TIMEZONE.getId(), TimeZone.getTimeZone(ServerSessionAdapter.valueOf(session).getUser().getTimeZone()));
        }

        Effort effort = fastTypes.contains(detectedFileType) ? Effort.LOW_EFFORT : Effort.HIGH_EFFORT;
        LOGGER.debug("Estimated {} effort for extracting {} image metadata from document {} ({}) with version {}", effort.getName(), detectedFileType.getName(), I(document.getId()), document.getFileName(), I(document.getVersion()));
        return effort;
    }

    @Override
    public ExtractorResult extractAndApplyMediaMetadata(InputStream optStream, InputStreamProvider provider, DocumentMetadata document, Map<String, Object> arguments) throws OXException {
        if (null == provider) {
            throw OXException.general("Stream must not be null.");
        }
        if (null == document) {
            throw OXException.general("Document must not be null.");
        }

        if (false == indicatesImage(document)) {
            return ExtractorResult.NONE;
        }

        InputStream in = optStream;
        BufferedInputStream bufferedStream = null;
        try {
            boolean debugEnabled = LOGGER.isDebugEnabled();
            long startNanos = debugEnabled ? System.nanoTime() : 0L;

            FileType detectedFileType = null == arguments ? null : (FileType) arguments.get(KnownArgument.FILE_TYPE.getId());
            if (null == detectedFileType) {
                in = null == in ? provider.getInputStream() : in;
                bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);

                try {
                    bufferedStream.mark(65536);
                    detectedFileType = FileTypeDetector.detectFileType(bufferedStream);
                } catch (IOException e) {
                    LOGGER.warn("Failed to extract image metadata from document {} with version {}", I(document.getId()), I(document.getVersion()), e);
                    return ExtractorResult.NONE;
                } catch (@SuppressWarnings("unused") AssertionError e) {
                    detectedFileType = FileType.Unknown;
                }
            }

            if (FileType.Unknown == detectedFileType) {
                LOGGER.warn("Failed to extract image metadata from document {} with version {}. File type is unknown.", I(document.getId()), I(document.getVersion()));
                return ExtractorResult.NONE;
            }

            if (false == fileTypeIndicatesImage(detectedFileType)) {
                return ExtractorResult.NONE;
            }

            if (debugEnabled) {
                LOGGER.debug("Going to extract {} image metadata from document {} ({}) with version {}", detectedFileType.getName(), I(document.getId()), document.getFileName(), I(document.getVersion()));
            }

            try {
                if (null == bufferedStream) {
                    in = null == in ? provider.getInputStream() : in;
                    bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);
                } else {
                    try {
                        bufferedStream.reset();
                    } catch (Exception e) {
                        // Reset failed
                        if (debugEnabled) {
                            LOGGER.debug("Failed to reset stream for extracting image metadata from document {} with version {}", I(document.getId()), I(document.getVersion()), e);
                        }
                        Streams.close(bufferedStream, in);
                        in = provider.getInputStream();
                        bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);
                    }
                }

                Metadata metadata = ImageMetadataReader.readMetadata(bufferedStream, -1, detectedFileType);

                MetadataMapImpl.Builder mediaMeta = writeMediaMetadata ? MetadataMapImpl.builder(4) : null;

                Thread currentThread = Thread.currentThread();
                for (Directory directory : metadata.getDirectories()) {
                    if (currentThread.isInterrupted()) {
                        return ExtractorResult.INTERRUPTED;
                    }

                    if (directory.isEmpty()) {
                        continue;
                    }

                    // Check for a known directory
                    KnownDirectory knownDirectory = KnownDirectory.knownDirectoryFor(directory);

                    // Check if media meta-data are supposed to be written
                    if (writeMediaMetadata) {
                        // Get tag list & initialize appropriate map for current metadata directory
                        boolean discardDirectory = MetadataUtility.putMediaMeta(null == knownDirectory ? directory.getName() : knownDirectory.name(), directory, mediaMeta);
                        if (discardDirectory) {
                            // Nothing put into media metadata for current metadata directory
                            continue;
                        }
                    }

                    if (null != knownDirectory) {
                        switch (knownDirectory) {
                            case EXIF:
                                if (directory instanceof ExifSubIFDDirectory) {
                                    ExifSubIFDDirectory exifSubIFDDirectory = (ExifSubIFDDirectory) directory;
                                    TimeZone defTimezone = TimeZone.getTimeZone("UTC");
                                    Date dateOriginal = exifSubIFDDirectory.getDateOriginal(arguments == null ? defTimezone : (TimeZone) arguments.getOrDefault(KnownArgument.TIMEZONE.getId(), defTimezone));
                                    if (null != dateOriginal) {
                                        document.setCaptureDate(dateOriginal);
                                    }
                                }
                                if (document.getWidth() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH);
                                    Long longObject = MediaMetadataExtractors.getLongValue(value);
                                    if (null != longObject) {
                                        document.setWidth(longObject.longValue());
                                    }
                                }
                                if (document.getHeight() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT);
                                    Long longObject = MediaMetadataExtractors.getLongValue(value);
                                    if (null != longObject) {
                                        document.setHeight(longObject.longValue());
                                    }
                                }
                                if (document.getWidth() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_IMAGE_WIDTH);
                                    Long longObject = MediaMetadataExtractors.getLongValue(value);
                                    if (null != longObject) {
                                        document.setWidth(longObject.longValue());
                                    }
                                }
                                if (document.getHeight() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_IMAGE_HEIGHT);
                                    Long longObject = MediaMetadataExtractors.getLongValue(value);
                                    if (null != longObject) {
                                        document.setHeight(longObject.longValue());
                                    }
                                }
                                if (document.getCameraIsoSpeed() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_ISO_SPEED);
                                    Long longObject = MediaMetadataExtractors.getLongValue(value);
                                    if (null != longObject) {
                                        document.setCameraIsoSpeed(longObject.longValue());
                                    } else {
                                        value = directory.getObject(ExifDirectoryBase.TAG_ISO_EQUIVALENT);
                                        longObject = MediaMetadataExtractors.getLongValue(value);
                                        if (null != longObject) {
                                            document.setCameraIsoSpeed(longObject.longValue());
                                        }
                                    }
                                }
                                if (document.getCameraAperture() == null) {
                                    //Double aperture = directory.getDoubleObject(ExifDirectoryBase.TAG_APERTURE);
                                    Double aperture = directory.getDoubleObject(ExifDirectoryBase.TAG_FNUMBER);
                                    if (aperture != null) {
                                        document.setCameraAperture(aperture.doubleValue());
                                    }
                                }
                                if (document.getCameraExposureTime() == null) {
                                    Double exposureTime = directory.getDoubleObject(ExifDirectoryBase.TAG_EXPOSURE_TIME);
                                    if (exposureTime != null) {
                                        document.setCameraExposureTime(exposureTime.doubleValue());
                                    }
                                }
                                if (document.getCameraFocalLength() == null) {
                                    Double focalLength = directory.getDoubleObject(ExifDirectoryBase.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
                                    if (focalLength != null) {
                                        document.setCameraFocalLength(focalLength.doubleValue());
                                    }
                                }
                                if (document.getCameraMake() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_MAKE);
                                    if (null != value) {
                                        document.setCameraMake(value.toString().trim());
                                    }
                                }
                                if (document.getCameraModel() == null) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_MODEL);
                                    if (null != value) {
                                        document.setCameraModel(value.toString().trim());
                                    }
                                }
                                if (null == document.getCaptureDate()) {
                                    Object value = directory.getObject(ExifDirectoryBase.TAG_DATETIME_ORIGINAL);
                                    if (null != value) {
                                        document.setCaptureDate(MediaMetadataExtractors.parseDateStringToDate(value.toString(), null));
                                    }
                                }
                                break;
                            case GPS:
                                {
                                    Double latitude = null;
                                    Double longitude = null;

                                    // Latitude
                                    String dms = directory.getDescription(GpsDirectory.TAG_LATITUDE);
                                    if (null != dms) {
                                        latitude = GeoLocation.parseDMSStringToDouble(dms);
                                    }

                                    // Longitude
                                    dms = directory.getDescription(GpsDirectory.TAG_LONGITUDE);
                                    if (null != dms) {
                                        longitude = GeoLocation.parseDMSStringToDouble(dms);
                                    }

                                    if (null != latitude && null != longitude) {
                                        document.setGeoLocation(new GeoLocation(latitude.doubleValue(), longitude.doubleValue()));
                                    }
                                }

                                break;
                            case IPTC:
                                if (null == document.getCaptureDate()) {
                                    String dateString;
                                    {
                                        Object object = directory.getObject(IptcDirectory.TAG_DATE_CREATED);
                                        dateString = null == object ? null : object.toString();
                                    }
                                    String timeString;
                                    {
                                        Object object = directory.getObject(IptcDirectory.TAG_TIME_CREATED);
                                        timeString = null == object ? null : object.toString();
                                    }
                                    document.setCaptureDate(MediaMetadataExtractors.parseDateStringToDate(dateString, timeString));
                                }
                                break;
                            default:
                                break;
                        }
                    } else {
                        switch (detectedFileType) {
                            case Png:
                                if (directory instanceof PngDirectory) {
                                    PngDirectory pngDirectory = (PngDirectory) directory;

                                    if (document.getWidth() == null) {
                                        Long longObject = pngDirectory.getLongObject(PngDirectory.TAG_IMAGE_WIDTH);
                                        if (null != longObject) {
                                            document.setWidth(longObject.longValue());
                                        }
                                    }
                                    if (document.getHeight() == null) {
                                        Long longObject = pngDirectory.getLongObject(PngDirectory.TAG_IMAGE_HEIGHT);
                                        if (null != longObject) {
                                            document.setHeight(longObject.longValue());
                                        }
                                    }
                                    if (null == document.getCaptureDate()) {
                                        Object object = pngDirectory.getObject(PngDirectory.TAG_LAST_MODIFICATION_TIME);
                                        if (null != object) {
                                            document.setCaptureDate(MediaMetadataExtractors.parseDateStringToDate(object.toString(), null));
                                        }
                                    }
                                }
                                break;
                            case Jpeg:
                                if (directory instanceof JpegDirectory) {
                                    JpegDirectory jpegDirectory = (JpegDirectory) directory;

                                    if (document.getWidth() == null) {
                                        Long longObject = jpegDirectory.getLongObject(JpegDirectory.TAG_IMAGE_WIDTH);
                                        if (null != longObject) {
                                            document.setWidth(longObject.longValue());
                                        }
                                    }
                                    if (document.getHeight() == null) {
                                        Long longObject = jpegDirectory.getLongObject(JpegDirectory.TAG_IMAGE_HEIGHT);
                                        if (null != longObject) {
                                            document.setHeight(longObject.longValue());
                                        }
                                    }
                                }
                                if (directory instanceof CasioType2MakernoteDirectory) {
                                    CasioType2MakernoteDirectory casioMakernoteDirectory = (CasioType2MakernoteDirectory) directory;

                                    if (document.getCameraIsoSpeed() == null) {
                                        String desc = casioMakernoteDirectory.getDescription(CasioType2MakernoteDirectory.TAG_ISO_SENSITIVITY);
                                        if (null != desc) {
                                            try {
                                                document.setCameraIsoSpeed(Long.parseLong(desc));
                                            } catch (@SuppressWarnings("unused") NumberFormatException e) {
                                                // Ignore...
                                            }
                                        }
                                    }
                                }
                                break;
                            case Heif:
                                if (directory instanceof HeifDirectory) {
                                    HeifDirectory heifDirectory = (HeifDirectory) directory;

                                    if (document.getWidth() == null) {
                                        Long longObject = heifDirectory.getLongObject(HeifDirectory.TAG_IMAGE_WIDTH);
                                        if (null != longObject) {
                                            document.setWidth(longObject.longValue());
                                        }
                                    }
                                    if (document.getHeight() == null) {
                                        Long longObject = heifDirectory.getLongObject(HeifDirectory.TAG_IMAGE_HEIGHT);
                                        if (null != longObject) {
                                            document.setHeight(longObject.longValue());
                                        }
                                    }
                                }
                                break;
                            case Bmp:
                                if (directory instanceof BmpHeaderDirectory) {
                                    BmpHeaderDirectory bmpDirectory = (BmpHeaderDirectory) directory;

                                    if (document.getWidth() == null) {
                                        Long longObject = bmpDirectory.getLongObject(BmpHeaderDirectory.TAG_IMAGE_WIDTH);
                                        if (null != longObject) {
                                            document.setWidth(longObject.longValue());
                                        }
                                    }
                                    if (document.getHeight() == null) {
                                        Long longObject = bmpDirectory.getLongObject(BmpHeaderDirectory.TAG_IMAGE_HEIGHT);
                                        if (null != longObject) {
                                            document.setHeight(longObject.longValue());
                                        }
                                    }
                                }
                                break;
                            case Gif:
                                if (directory instanceof GifImageDirectory) {
                                    GifImageDirectory gifDirectory = (GifImageDirectory) directory;

                                    if (document.getWidth() == null) {
                                        Long longObject = gifDirectory.getLongObject(GifImageDirectory.TAG_WIDTH);
                                        if (null != longObject) {
                                            document.setWidth(longObject.longValue());
                                        }
                                    }
                                    if (document.getHeight() == null) {
                                        Long longObject = gifDirectory.getLongObject(GifImageDirectory.TAG_HEIGHT);
                                        if (null != longObject) {
                                            document.setHeight(longObject.longValue());
                                        }
                                    }
                                }
                                break;
                            default:
                                // nothing
                                break;
                        }
                    }
                }

                if (document.getWidth() == null || document.getHeight() == null) {
                    ImageMetadataService imageMetadataService = ServerServiceRegistry.getInstance().getService(ImageMetadataService.class);
                    if (null != imageMetadataService) {
                        // Reset buffered stream for re-use
                        try {
                            bufferedStream.reset();
                        } catch (Exception e) {
                            // Reset failed
                            if (debugEnabled) {
                                LOGGER.debug("Failed to reset stream for extracting image metadata from document {} with version {}", I(document.getId()), I(document.getVersion()), e);
                            }
                            Streams.close(bufferedStream, in);
                            in = provider.getInputStream();
                            bufferedStream = in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in, 65536);
                        }
                        try {
                            Dimension dimension = imageMetadataService.getDimensionFor(bufferedStream, null, null);
                            document.setWidth(dimension.width);
                            document.setHeight(dimension.height);
                        } catch (Exception e) {
                            // Failed to determine image height/width via ImageMetadataService
                            LOGGER.warn("Failed to determine image height/width via {} for document {} with version {}", ImageMetadataService.class.getName(), I(document.getId()), I(document.getVersion()), e);
                        }
                    }
                }

                document.setMediaMeta(null == mediaMeta ? null : mediaMeta.build().asMap());

                if (debugEnabled) {
                    long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                    String formattedDuration = exactly(durationMillis, true);
                    if (Strings.isEmpty(formattedDuration)) {
                        formattedDuration = durationMillis + "ms";
                    }
                    LOGGER.debug("Successfully extracted {} image metadata in {} from document {} ({}) with version {}", detectedFileType.getName(), formattedDuration, I(document.getId()), document.getFileName(), I(document.getVersion()));
                }
                return ExtractorResult.SUCCESSFUL;
            } catch (com.drew.imaging.ImageProcessingException e) {
                LOGGER.debug("Failed to extract {} image metadata from document {} ({}) with version {}", detectedFileType.getName(), I(document.getId()), document.getFileName(), I(document.getVersion()), e);
                return ExtractorResult.ACCEPTED_BUT_FAILED;
            } catch (Exception e) {
                LOGGER.debug("Failed to extract {} image metadata from document {} ({}) with version {}", detectedFileType.getName(), I(document.getId()), document.getFileName(), I(document.getVersion()), e);
                throw e instanceof OXException ? (OXException) e : OXException.general("Failed to extract image metadata from document " + document.getId() + " with version " + document.getVersion(), e);
            }
        } finally {
            Streams.close(bufferedStream, in);
        }
    }

}
