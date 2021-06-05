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

package com.openexchange.groupware.infostore.search;

import com.openexchange.exception.OXException;

/**
 * {@link SearchTermVisitor}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since 7.6.0
 */
public interface SearchTermVisitor {

    /**
     * The visitation for AND term.
     *
     * @param andTerm The visited AND term
     * @throws OXException If visit attempt fails
     */
    void visit(AndTerm andTerm) throws OXException;

    /**
     * The visitation for OR term.
     *
     * @param orTerm The visited OR term
     * @throws OXException If visit attempt fails
     */
    void visit(OrTerm orTerm) throws OXException;

    /**
     * The visitation for not term.
     *
     * @param notTerm The visited not term
     * @throws OXException If visit attempt fails
     */
    void visit(NotTerm notTerm) throws OXException;

    /**
     * The visitation for meta term.
     *
     * @param metaTerm The visited meta term
     * @throws OXException If visit attempt fails
     */
    void visit(MetaTerm metaTerm) throws OXException;

    /**
     * The visitation for number-of-versions term.
     *
     * @param numberOfVersionsTerm The visited number-of-versions term
     * @throws OXException If visit attempt fails
     */
    void visit(NumberOfVersionsTerm numberOfVersionsTerm) throws OXException;

    /**
     * The visitation for last-modified UTC term.
     *
     * @param lastModifiedUtcTerm The visited last-modified UTC term
     * @throws OXException If visit attempt fails
     */
    void visit(LastModifiedUtcTerm lastModifiedUtcTerm) throws OXException;

    /**
     * The visitation for color label term.
     *
     * @param colorLabelTerm The visited color label term
     * @throws OXException If visit attempt fails
     */
    void visit(ColorLabelTerm colorLabelTerm) throws OXException;

    /**
     * The visitation for current version term.
     *
     * @param currentVersionTerm The current version term
     * @throws OXException If visit attempt fails
     */
    void visit(CurrentVersionTerm currentVersionTerm) throws OXException;

    /**
     * The visitation for version comment term.
     *
     * @param versionCommentTerm The version comment term
     * @throws OXException If visit attempt fails
     */
    void visit(VersionCommentTerm versionCommentTerm) throws OXException;

    /**
     * The visitation for file MD5 sum term.
     *
     * @param fileMd5SumTerm The file MD5 sum term
     * @throws OXException If visit attempt fails
     */
    void visit(FileMd5SumTerm fileMd5SumTerm) throws OXException;

    /**
     * The visitation for locked-until term.
     *
     * @param lockedUntilTerm The locked-until term
     * @throws OXException If visit attempt fails
     */
    void visit(LockedUntilTerm lockedUntilTerm) throws OXException;

    /**
     * The visitation for categories term.
     *
     * @param categoriesTerm The categories term
     * @throws OXException If visit attempt fails
     */
    void visit(CategoriesTerm categoriesTerm) throws OXException;

    /**
     * The visitation for sequence number term.
     *
     * @param sequenceNumberTerm The sequence number term
     * @throws OXException If visit attempt fails
     */
    void visit(SequenceNumberTerm sequenceNumberTerm) throws OXException;

    /**
     * The visitation for file MIME type term.
     *
     * @param fileMimeTypeTerm The file MIME type term
     * @throws OXException If visit attempt fails
     */
    void visit(FileMimeTypeTerm fileMimeTypeTerm) throws OXException;

    /**
     * The visitation for file name term.
     *
     * @param fileNameTerm The file name term
     * @throws OXException If visit attempt fails
     */
    void visit(FileNameTerm fileNameTerm) throws OXException;

    /**
     * The visitation for last modified term.
     *
     * @param lastModifiedTerm The visited last modified term
     * @throws OXException If visit attempt fails
     */
    void visit(LastModifiedTerm lastModifiedTerm) throws OXException;

    /**
     * The visitation for created term.
     *
     * @param createdTerm The visited created term
     * @throws OXException If visit attempt fails
     */
    void visit(CreatedTerm createdTerm) throws OXException;

    /**
     * The visitation for modified by term.
     *
     * @param modifiedByTerm The visited modified by term
     * @throws OXException If visit attempt fails
     */
    void visit(ModifiedByTerm modifiedByTerm) throws OXException;

    /**
     * The visitation for title term.
     *
     * @param titleTerm The visited title term
     * @throws OXException If visit attempt fails
     */
    void visit(TitleTerm titleTerm) throws OXException;

    /**
     * The visitation for version term.
     *
     * @param versionTerm The visited version term
     * @throws OXException If visit attempt fails
     */
    void visit(VersionTerm versionTerm) throws OXException;

    /**
     * The visitation for content term.
     *
     * @param contentTerm The visited content term
     * @throws OXException If visit attempt fails
     */
    void visit(ContentTerm contentTerm) throws OXException;

    /**
     * The visitation for file size term.
     *
     * @param fileSizeTerm The visited file size term
     * @throws OXException If visit attempt fails
     */
    void visit(FileSizeTerm fileSizeTerm) throws OXException;

    /**
     * The visitation for description term.
     *
     * @param descriptionTerm The visited description term
     * @throws OXException If visit attempt fails
     */
    void visit(DescriptionTerm descriptionTerm) throws OXException;

    /**
     * The visitation for url term.
     *
     * @param urlTerm The visited url term
     * @throws OXException If visit attempt fails
     */
    void visit(UrlTerm urlTerm) throws OXException;

    /**
     * The visitation for created by term.
     *
     * @param createdByTerm The visited created by term
     * @throws OXException If visit attempt fails
     */
    void visit(CreatedByTerm createdByTerm) throws OXException;

    /**
     * The visitation for media date term.
     *
     * @param mediaDateTerm The visited media date term
     * @throws OXException If visit attempt fails
     */
    void visit(MediaDateTerm mediaDateTerm) throws OXException;

    /**
     * The visitation for capture date term.
     *
     * @param captureDateTerm The visited capture date term
     * @throws OXException If visit attempt fails
     */
    void visit(CaptureDateTerm captureDateTerm) throws OXException;

    /**
     * The visitation for ISO speed term.
     *
     * @param cameraIsoSpeedTerm The visited ISO speed term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraIsoSpeedTerm cameraIsoSpeedTerm) throws OXException;

    /**
     * The visitation for aperture term.
     *
     * @param cameraApertureTerm The visited term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraApertureTerm cameraApertureTerm) throws OXException;

    /**
     * The visitation for exposure time term.
     *
     * @param cameraExposureTimeTerm The visited term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraExposureTimeTerm cameraExposureTimeTerm) throws OXException;

    /**
     * The visitation for focal length term.
     *
     * @param cameraFocalLengthTerm The visited term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraFocalLengthTerm cameraFocalLengthTerm) throws OXException;

    /**
     * The visitation for width term.
     *
     * @param widthTerm The visited width term
     * @throws OXException If visit attempt fails
     */
    void visit(WidthTerm widthTerm) throws OXException;

    /**
     * The visitation for height term.
     *
     * @param heightTerm The visited height term
     * @throws OXException If visit attempt fails
     */
    void visit(HeightTerm heightTerm) throws OXException;

    /**
     * The visitation for camera model term.
     *
     * @param cameraModelTerm The visited camera model term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraModelTerm cameraModelTerm) throws OXException;

    /**
     * The visitation for camera make term.
     *
     * @param cameraMakeTerm The visited camera make term
     * @throws OXException If visit attempt fails
     */
    void visit(CameraMakeTerm cameraMakeTerm);

}
