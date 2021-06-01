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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.slf4j.Logger;
import com.openexchange.imagetransformation.ImageInformation;
import com.openexchange.imagetransformation.ImageTransformations;
import com.openexchange.imagetransformation.TransformationContext;

/**
 * {@link RotateTransformation}
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.com">Martin Herfurth</a>
 */
public class RotateTransformation implements ImageTransformation {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOG = org.slf4j.LoggerFactory.getLogger(RotateTransformation.class);
    }

    private static final RotateTransformation INSTANCE = new RotateTransformation();

    /**
     * Gets the instance
     *
     * @return The instance
     */
    public static RotateTransformation getInstance() {
        return INSTANCE;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    private RotateTransformation() {
        super();
    }

    /**
     * Checks if specified image information imply that rotation is needed
     *
     * @param imageInformation The image information to examine
     * @return <code>true</code> if rotation is needed; otherwise <code>false</code>
     */
    public boolean needsRotation(ImageInformation imageInformation) {
        if (null == imageInformation) {
            // No image information available, unable to rotate image
            return false;
        }

        AffineTransform exifTransformation = getExifTransformation(imageInformation);
        if (null == exifTransformation) {
            // No EXIF transformation available, unable to rotate image
            return false;
        }

        // Rotation required
        return true;
    }

    @Override
    public BufferedImage perform(BufferedImage sourceImage, TransformationContext transformationContext, ImageInformation imageInformation) throws IOException {
        if (null == imageInformation) {
            LoggerHolder.LOG.debug("No image information available, unable to rotate image");
            return sourceImage;
        }
        AffineTransform exifTransformation = getExifTransformation(new ImageInformation(imageInformation.orientation, sourceImage.getWidth(), sourceImage.getHeight()));
        if (null == exifTransformation) {
            LoggerHolder.LOG.debug("No EXIF transformation available, unable to rotate image");
            return sourceImage;
        }
        // Draw rotated picture if its orientation is greater than 4
        boolean rotate = imageInformation.orientation > 4;
        int newWidth = rotate ? sourceImage.getHeight() : sourceImage.getWidth();
        int newHeight = rotate ? sourceImage.getWidth() : sourceImage.getHeight();
        BufferedImage destinationImage = new BufferedImage(newWidth, newHeight, sourceImage.getType());
        Graphics2D g = destinationImage.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, destinationImage.getWidth(), destinationImage.getHeight());
        g.drawImage(sourceImage, exifTransformation, null);
        transformationContext.addExpense(ImageTransformations.LOW_EXPENSE);
        return destinationImage;
    }

    private AffineTransform getExifTransformation(ImageInformation info) {
        AffineTransform t = new AffineTransform();

        switch (info.orientation) {
        default:
        case 1:
            return null;
        case 2:
            t.scale(-1.0, 1.0);
            t.translate(-info.width, 0);
            break;
        case 3:
            t.translate(info.width, info.height);
            t.rotate(Math.PI);
            break;
        case 4:
            t.scale(1.0, -1.0);
            t.translate(0, -info.height);
            break;
        case 5:
            t.rotate(-Math.PI / 2);
            t.scale(-1.0, 1.0);
            break;
        case 6:
            t.translate(info.height, 0);
            t.rotate(Math.PI / 2);
            break;
        case 7:
            t.scale(-1.0, 1.0);
            t.translate(-info.height, 0);
            t.translate(0, info.width);
            t.rotate(3 * Math.PI / 2);
            break;
        case 8:
            t.translate(0, info.width);
            t.rotate(3 * Math.PI / 2);
            break;
        }
        return t;
    }

    @Override
    public boolean needsImageInformation() {
        return true;
    }

    @Override
    public boolean supports(String formatName) {
        return null != formatName && "jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName) ||
            "tiff".equalsIgnoreCase(formatName) || "psd".equalsIgnoreCase(formatName);
    }

    @Override
    public Dimension getRequiredResolution(Dimension originalResolution) {
        return null;
    }

}