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

package com.openexchange.file.storage.search;

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
     * @param term The visited AND term
     * @throws OXException If visit attempt fails
     */
    void visit(AndTerm term) throws OXException;

    /**
     * The visitation for OR term.
     *
     * @param term The visited OR term
     * @throws OXException If visit attempt fails
     */
    void visit(OrTerm term) throws OXException;

    /**
     * The visitation for not term.
     *
     * @param term The visited not term
     * @throws OXException If visit attempt fails
     */
    void visit(NotTerm term) throws OXException;

    /**
     * The visitation for meta term.
     *
     * @param term The visited meta term
     * @throws OXException If visit attempt fails
     */
    void visit(MetaTerm term) throws OXException;

    /**
     * The visitation for number-of-versions term.
     *
     * @param term The visited number-of-versions term
     * @throws OXException If visit attempt fails
     */
    void visit(NumberOfVersionsTerm term) throws OXException;

    /**
     * The visitation for last-modified UTC term.
     *
     * @param term The visited last-modified UTC term
     * @throws OXException If visit attempt fails
     */
    void visit(LastModifiedUtcTerm term) throws OXException;

    /**
     * The visitation for color label term.
     *
     * @param term The visited color label term
     * @throws OXException If visit attempt fails
     */
    void visit(ColorLabelTerm term) throws OXException;

    /**
     * The visitation for current version term.
     *
     * @param term The current version term
     * @throws OXException If visit attempt fails
     */
    void visit(CurrentVersionTerm term) throws OXException;

    /**
     * The visitation for version comment term.
     *
     * @param term The version comment term
     * @throws OXException If visit attempt fails
     */
    void visit(VersionCommentTerm term) throws OXException;

    /**
     * The visitation for file MD5 sum term.
     *
     * @param term The file MD5 sum term
     * @throws OXException If visit attempt fails
     */
    void visit(FileMd5SumTerm term) throws OXException;

    /**
     * The visitation for locked-until term.
     *
     * @param term The locked-until term
     * @throws OXException If visit attempt fails
     */
    void visit(LockedUntilTerm term) throws OXException;

    /**
     * The visitation for categories term.
     *
     * @param term The categories term
     * @throws OXException If visit attempt fails
     */
    void visit(CategoriesTerm term) throws OXException;

    /**
     * The visitation for sequence number term.
     *
     * @param term The sequence number term
     * @throws OXException If visit attempt fails
     */
    void visit(SequenceNumberTerm term) throws OXException;

    /**
     * The visitation for file MIME type term.
     *
     * @param term The file MIME type term
     * @throws OXException If visit attempt fails
     */
    void visit(FileMimeTypeTerm term) throws OXException;

    /**
     * The visitation for file name term.
     *
     * @param term The file name term
     * @throws OXException If visit attempt fails
     */
    void visit(FileNameTerm term) throws OXException;

    /**
     * The visitation for last modified term.
     *
     * @param term The visited last modified term
     * @throws OXException If visit attempt fails
     */
    void visit(LastModifiedTerm term) throws OXException;

    /**
     * The visitation for created term.
     *
     * @param term The visited created term
     * @throws OXException If visit attempt fails
     */
    void visit(CreatedTerm term) throws OXException;

    /**
     * The visitation for modified by term.
     *
     * @param term The visited modified by term
     * @throws OXException If visit attempt fails
     */
    void visit(ModifiedByTerm term) throws OXException;

    /**
     * The visitation for title term.
     *
     * @param term The visited title term
     * @throws OXException If visit attempt fails
     */
    void visit(TitleTerm term) throws OXException;

    /**
     * The visitation for version term.
     *
     * @param term The visited version term
     * @throws OXException If visit attempt fails
     */
    void visit(VersionTerm term) throws OXException;

    /**
     * The visitation for content term.
     *
     * @param term The visited content term
     * @throws OXException If visit attempt fails
     */
    void visit(ContentTerm term) throws OXException;

    /**
     * The visitation for file size term.
     *
     * @param term The visited file size term
     * @throws OXException If visit attempt fails
     */
    void visit(FileSizeTerm term) throws OXException;

    /**
     * The visitation for description term.
     *
     * @param term The visited description term
     * @throws OXException If visit attempt fails
     */
    void visit(DescriptionTerm term) throws OXException;

    /**
     * The visitation for url term.
     *
     * @param term The visited url term
     * @throws OXException If visit attempt fails
     */
    void visit(UrlTerm term) throws OXException;

    /**
     * The visitation for created by term.
     *
     * @param term The visited created by term
     * @throws OXException If visit attempt fails
     */
    void visit(CreatedByTerm term) throws OXException;

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
   void visit(CameraMakeTerm cameraMakeTerm) throws OXException;

}
