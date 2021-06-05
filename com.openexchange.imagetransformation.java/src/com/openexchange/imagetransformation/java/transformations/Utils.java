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

package com.openexchange.imagetransformation.java.transformations;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import com.google.common.collect.ImmutableMap;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.exception.OXException;
import com.openexchange.imagetransformation.Utility;
import com.openexchange.imagetransformation.java.exif.ExifTool;
import com.openexchange.imagetransformation.java.exif.Orientation;
import com.openexchange.java.Strings;
import com.openexchange.tools.images.ImageTransformationUtility;
import com.twelvemonkeys.imageio.plugins.bmp.BMPImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.bmp.CURImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.bmp.ICOImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.dcx.DCXImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.hdr.HDRImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.icns.ICNSImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.iff.IFFImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.pcx.PCXImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.pict.PICTImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.psd.PSDImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.sgi.SGIImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.tga.TGAImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.thumbsdb.ThumbsDBImageReaderSpi;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi;
import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;

/**
 * {@link Utils}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.2
 */
public class Utils {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Utils.class);
    }

    private static final Map<String, ImageReaderSpi> READER_SPI_BY_EXTENSION;
    private static final Map<String, ImageReaderSpi> READER_SPI_BY_FORMAT_NAME;
    static {
        /*
         * prepare custom reader SPIs & remember by supported format name
         */
        List<ImageReaderSpi> readerSpis = Arrays.<ImageReaderSpi>asList(
            new JPEGImageReaderSpi(), new TIFFImageReaderSpi(), new BMPImageReaderSpi(), new PSDImageReaderSpi(),
            new ICOImageReaderSpi(), new CURImageReaderSpi(), new DCXImageReaderSpi(), new HDRImageReaderSpi(),
            new ICNSImageReaderSpi(), new IFFImageReaderSpi(), new PCXImageReaderSpi(), new PICTImageReaderSpi(),
            new SGIImageReaderSpi(), new TGAImageReaderSpi(), new ThumbsDBImageReaderSpi()
        );
        Map<String, ImageReaderSpi> readerSpiByExtension = new TreeMap<String, ImageReaderSpi>(String.CASE_INSENSITIVE_ORDER);
        Map<String, ImageReaderSpi> readerSpiByFormatName = new TreeMap<String, ImageReaderSpi>(String.CASE_INSENSITIVE_ORDER);
        IIORegistry registry = IIORegistry.getDefaultInstance();
        for (ImageReaderSpi readerSpi : readerSpis) {
            readerSpi.onRegistration(registry, ImageReaderSpi.class);
            for (String formatName : readerSpi.getFormatNames()) {
                readerSpiByFormatName.put(Strings.toLowerCase(formatName), readerSpi);
            }
            String[] fileSuffixes = readerSpi.getFileSuffixes();
            if (null != fileSuffixes) {
                for (String extension : readerSpi.getFileSuffixes()) {
                    readerSpiByExtension.put(Strings.toLowerCase(extension), readerSpi);
                }
            }
        }
        READER_SPI_BY_EXTENSION = ImmutableMap.copyOf(readerSpiByExtension);
        READER_SPI_BY_FORMAT_NAME = ImmutableMap.copyOf(readerSpiByFormatName);
    }

    /**
     * Gets an image reader suitable for the supplied image input stream.
     *
     * @param inputStream The image input stream to create the reader for
     * @param optContentType The indicated content type, or <code>null</code> if not available
     * @param optFileName The indicated file name, or <code>null</code> if not available
     * @return The image reader
     */
    public static ImageReader getImageReader(ImageInputStream inputStream, String optContentType, String optFileName) throws IOException {
        ImageReader reader = null;
        try {
            // Prefer alternative image reader by MIME type if possible
            if (null != optContentType) {
                ImageReaderSpi readerSpi = null;
                String extension = getFileExtension(optFileName);
                if (null != extension) {
                    readerSpi = READER_SPI_BY_EXTENSION.get(Strings.toLowerCase(extension));
                }
                if (null == readerSpi) {
                    readerSpi = READER_SPI_BY_FORMAT_NAME.get(Strings.toLowerCase(Utility.getImageFormat(optContentType)));
                }
                if (null != readerSpi) {
                    // Unlike a standard InputStream, all ImageInputStreams support marking.
                    inputStream.mark();
                    try {
                        if (readerSpi.canDecodeInput(inputStream)) {
                            reader = readerSpi.createReaderInstance();
                            LoggerHolder.LOG.trace("Using {} for indicated format \"{}\".", reader.getClass().getName(), optContentType);
                        }
                    } catch (IOException e) {
                        LoggerHolder.LOG.debug("Error probing for suitable image reader", e);
                    } finally {
                        inputStream.reset();
                    }
                }
            }

            // Fall back to regularly registered readers in case no alternative image reader was acquired
            if (null == reader) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
                if (false == readers.hasNext()) {
                    throw new IOException("No image reader available for format " + optContentType);
                }
                reader = readers.next();
                LoggerHolder.LOG.trace("Using {} for indicated format \"{}\".", reader.getClass().getName(), optContentType);
            }

            // Successfully obtained ImageReader instance. Return it.
            ImageReader retval = reader;
            reader = null; // Avoid premature disposal of ImageReader instance
            return retval;
        } finally {
            if (null != reader) {
                try {
                    reader.dispose();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Removes the transparency from the given image if necessary, i.e. the color model has an alpha channel and the supplied image
     * format is supposed to not support transparency.
     *
     * @param image The image
     * @param formatName The image format name, e.g. "jpeg" or "tiff"
     * @return The processed buffered image, or the previous image if no processing was necessary
     */
    public static BufferedImage removeTransparencyIfNeeded(BufferedImage image, String formatName) {
        if (null != image && null != formatName && false == ImageTransformationUtility.supportsTransparency(formatName)) {
            ColorModel colorModel = image.getColorModel();
            if (null != colorModel && colorModel.hasAlpha()) {
                BufferedImage targetImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = targetImage.createGraphics();
                graphics.drawImage(image, 0, 0, Color.WHITE, null);
                graphics.dispose();
                return targetImage;
            }
        }
        return image;
    }

    /**
     * Gets the {@code InputStream} from specified image file.
     *
     * @param imageFile The image file
     * @return The input stream
     * @throws IOException If input stream cannot be returned
     */
    public static InputStream getFileStream(IFileHolder imageFile) throws IOException {
        if (null == imageFile) {
            return null;
        }
        try {
            return imageFile.getStream();
        } catch (OXException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw null == cause ? new IOException(e.getMessage(), e) : new IOException(cause.getMessage(), cause);
        }
    }

    /**
     * Gets the {@code ImageInputStream} from specified image file.
     *
     * @param imageFile The image file
     * @return The image input stream
     * @throws IOException If input stream cannot be returned
     */
    public static ImageInputStream getImageInputStream(IFileHolder imageFile) throws IOException {
        try {
            /*
             * prefer 'optimized' image input streams for threshold file holders
             */
            if (ThresholdFileHolder.class.isInstance(imageFile)) {
                ThresholdFileHolder fileHolder = (ThresholdFileHolder) imageFile;
                if (fileHolder.isInMemory()) {
                    return new ByteArrayImageInputStream(fileHolder.toByteArray());
                }
                return new FileImageInputStream(fileHolder.getTempFile());
            }
            /*
             * fallback to default spi-based image input stream instantiation
             */
            return ImageIO.createImageInputStream(imageFile.getStream());
        } catch (OXException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw null == cause ? new IOException(e.getMessage(), e) : new IOException(cause.getMessage(), cause);
        }
    }

    /**
     * Gets the {@code ImageInputStream} from specified input stream.
     *
     * @param imageStream The stream
     * @return The image input stream
     * @throws IOException If input stream cannot be returned
     */
    public static ImageInputStream getImageInputStream(InputStream imageStream) throws IOException {
        return ImageIO.createImageInputStream(imageStream);
    }

    /**
     * Tries to read out the Exif orientation of an image using the supplied image reader.
     *
     * @param reader The image reader to use
     * @param imageIndex The image index
     * @return The Exif orientation, or <code>0</code> if not available
     */
    public static int readExifOrientation(ImageReader reader, int imageIndex) {
        try {
            String formatName = reader.getFormatName();
            if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
                Orientation orientatation = ExifTool.readOrientation(reader, imageIndex);
                if (null != orientatation) {
                    return orientatation.getValue();
                }
            }
        } catch (Exception e) {
            LoggerHolder.LOG.debug("error reading Exif orientation", e);
        }
        return 0;
    }

    /**
     * Determines the required source resolution to fulfill one or more image transformation operations.
     *
     * @param transformations The transformations to inspect
     * @param originalWidth The original image's width
     * @param originalHeight The original image's height
     * @return The required resolution, or <code>null</code> if not relevant
     */
    public static Dimension getRequiredResolution(List<ImageTransformation> transformations, int originalWidth, int originalHeight) {
        Dimension originalResolution = new Dimension(originalWidth, originalHeight);
        Dimension requiredResolution = null;
        for (ImageTransformation transformation : transformations) {
            Dimension resolution = transformation.getRequiredResolution(originalResolution);
            if (null == requiredResolution) {
                requiredResolution = resolution;
            } else if (null != resolution) {
                if (requiredResolution.height < resolution.height) {
                    requiredResolution.height = resolution.height;
                }
                if (requiredResolution.width < resolution.width) {
                    requiredResolution.width = resolution.width;
                }
            }
        }
        return requiredResolution;
    }

    /**
     * Selects the most appropriate image index for the required target image resolution from the images available in the supplied image reader.
     *
     * @param reader The image reader to get the most appropriate image index for
     * @param requiredResolution The required resolution for the target image
     * @param maxResolution The max. resolution for an image or less than/equal to 0 (zero) for no resolution limitation
     * @return The image index, or <code>0</code> for the default image
     */
    public static int selectImage(ImageReader reader, Dimension requiredResolution, long maxResolution) {
        try {
            int numImages = reader.getNumImages(false);
            if (1 < numImages) {
                Dimension selectedResolution = new Dimension(reader.getWidth(0), reader.getHeight(0));
                int selectedIndex = 0;
                for (int i = 1; i < numImages; i++) {
                    Dimension resolution = new Dimension(reader.getWidth(i), reader.getHeight(i));
                    if (1 == selectResolution(selectedResolution, resolution, requiredResolution, maxResolution)) {
                        selectedIndex = i;
                        selectedResolution = resolution;
                    }
                }
                return selectedIndex;
            }
        } catch (IOException e) {
            LoggerHolder.LOG.debug("Error determining most appropriate image index", e);
        }
        return 0;
    }

    /**
     * Selects the most appropriate resolution to match a the target resolution.
     *
     * @param resolution1 The first candidate
     * @param resolution2 The second candidate
     * @param requiredResolution The required resolution
     * @param maxResolution The max. resolution for an image or less than/equal to 0 (zero) for no resolution limitation
     * @return <code>-1</code> if the first candidate was selected, <code>1</code> for the second one
     */
    private static int selectResolution(Dimension resolution1, Dimension resolution2, Dimension requiredResolution, long maxResolution) {
        long resolutionWidth1 = resolution1.width;
        long resolutionHeight1 = resolution1.height;
        long resolutionWidth2 = resolution2.width;
        long resolutionHeight2 = resolution2.height;

        if (0 < maxResolution) {
            if (resolutionWidth1 * resolutionHeight1 <= maxResolution) {
                if (resolutionWidth2 * resolutionHeight2 > maxResolution) {
                    /*
                     * only first resolution fulfills max. resolution constraint
                     */
                    return -1;
                }
            } else if (resolutionWidth2 * resolutionHeight2 <= maxResolution) {
                /*
                 * only second resolution fulfills max. resolution constraint
                 */
                return 1;
            }
        }
        if (resolutionWidth1 >= requiredResolution.width && resolutionHeight1 >= requiredResolution.height) {
            if (resolutionWidth2 >= requiredResolution.width && resolutionHeight2 >= requiredResolution.height) {
                /*
                 * both resolutions fulfill required resolution, choose closest one
                 */
                return resolutionWidth1 * resolutionHeight1 > resolutionWidth2 * resolutionHeight2 ? 1 : -1;
            }
            /*
             * only first resolution fulfills required resolution
             */
            return -1;
        } else if (resolutionWidth2 >= requiredResolution.width && resolutionHeight2 >= requiredResolution.height) {
            /*
             * only second resolution fulfills required resolution
             */
            return 1;
        } else {
            /*
             * no resolution fulfills required resolution, choose closest one
             */
            return resolutionWidth1 * resolutionHeight1 >= resolutionWidth2 * resolutionHeight2 ? -1 : 1;
        }
    }

    private static String getFileExtension(String fileName) {
        if (null != fileName) {
            int index = fileName.lastIndexOf('.');
            if (0 < index && index < fileName.length() - 1) {
                return fileName.substring(1 + index);
            }
        }
        return null;
    }

}
