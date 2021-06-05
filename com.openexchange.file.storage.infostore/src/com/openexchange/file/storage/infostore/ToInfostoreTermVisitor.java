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

package com.openexchange.file.storage.infostore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.infostore.internal.Utils;
import com.openexchange.file.storage.search.AndTerm;
import com.openexchange.file.storage.search.CameraApertureTerm;
import com.openexchange.file.storage.search.CameraExposureTimeTerm;
import com.openexchange.file.storage.search.CameraFocalLengthTerm;
import com.openexchange.file.storage.search.CameraIsoSpeedTerm;
import com.openexchange.file.storage.search.CameraMakeTerm;
import com.openexchange.file.storage.search.CameraModelTerm;
import com.openexchange.file.storage.search.CaptureDateTerm;
import com.openexchange.file.storage.search.CategoriesTerm;
import com.openexchange.file.storage.search.ColorLabelTerm;
import com.openexchange.file.storage.search.ContentTerm;
import com.openexchange.file.storage.search.CreatedByTerm;
import com.openexchange.file.storage.search.CreatedTerm;
import com.openexchange.file.storage.search.CurrentVersionTerm;
import com.openexchange.file.storage.search.DescriptionTerm;
import com.openexchange.file.storage.search.FileMd5SumTerm;
import com.openexchange.file.storage.search.FileMimeTypeTerm;
import com.openexchange.file.storage.search.FileNameTerm;
import com.openexchange.file.storage.search.FileSizeTerm;
import com.openexchange.file.storage.search.HeightTerm;
import com.openexchange.file.storage.search.LastModifiedTerm;
import com.openexchange.file.storage.search.LastModifiedUtcTerm;
import com.openexchange.file.storage.search.LockedUntilTerm;
import com.openexchange.file.storage.search.MediaDateTerm;
import com.openexchange.file.storage.search.MetaTerm;
import com.openexchange.file.storage.search.ModifiedByTerm;
import com.openexchange.file.storage.search.NotTerm;
import com.openexchange.file.storage.search.NumberOfVersionsTerm;
import com.openexchange.file.storage.search.OrTerm;
import com.openexchange.file.storage.search.SearchTerm;
import com.openexchange.file.storage.search.SearchTermVisitor;
import com.openexchange.file.storage.search.SequenceNumberTerm;
import com.openexchange.file.storage.search.TitleTerm;
import com.openexchange.file.storage.search.UrlTerm;
import com.openexchange.file.storage.search.VersionCommentTerm;
import com.openexchange.file.storage.search.VersionTerm;
import com.openexchange.file.storage.search.WidthTerm;
import com.openexchange.groupware.infostore.InfostoreFacade;
import com.openexchange.groupware.infostore.search.ComparisonType;

/**
 * {@link ToInfostoreTermVisitor}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ToInfostoreTermVisitor implements SearchTermVisitor {

    private com.openexchange.groupware.infostore.search.SearchTerm<?> infstoreTerm;

    /**
     * Initializes a new {@link ToInfostoreTermVisitor}.
     */
    public ToInfostoreTermVisitor() {
        super();
    }

    /**
     * Gets the infostore search term.
     *
     * @return The infostore search term
     */
    public com.openexchange.groupware.infostore.search.SearchTerm<?> getInfostoreTerm() {
        return infstoreTerm;
    }

    @Override
    public void visit(final AndTerm term) throws OXException {
        final List<SearchTerm<?>> terms = term.getPattern();
        final int size = terms.size();
        final List<com.openexchange.groupware.infostore.search.SearchTerm<?>> infostoreTerms = new ArrayList<com.openexchange.groupware.infostore.search.SearchTerm<?>>(size);
        for (int i = 0; i < size; i++) {
            final SearchTerm<?> searchTerm = terms.get(i);
            final ToInfostoreTermVisitor newVisitor = new ToInfostoreTermVisitor();
            searchTerm.visit(newVisitor);
            infostoreTerms.add(newVisitor.getInfostoreTerm());
        }
        infstoreTerm = new com.openexchange.groupware.infostore.search.AndTerm(infostoreTerms);
    }

    @Override
    public void visit(final OrTerm term) throws OXException {
        final List<SearchTerm<?>> terms = term.getPattern();
        final int size = terms.size();
        final List<com.openexchange.groupware.infostore.search.SearchTerm<?>> infostoreTerms = new ArrayList<com.openexchange.groupware.infostore.search.SearchTerm<?>>(size);
        for (int i = 0; i < size; i++) {
            final SearchTerm<?> searchTerm = terms.get(i);
            final ToInfostoreTermVisitor newVisitor = new ToInfostoreTermVisitor();
            searchTerm.visit(newVisitor);
            infostoreTerms.add(newVisitor.getInfostoreTerm());
        }
        infstoreTerm = new com.openexchange.groupware.infostore.search.OrTerm(infostoreTerms);
    }

    @Override
    public void visit(final NotTerm term) throws OXException {
        final ToInfostoreTermVisitor newVisitor = new ToInfostoreTermVisitor();
        term.getPattern().visit(newVisitor);
        infstoreTerm = new com.openexchange.groupware.infostore.search.NotTerm(newVisitor.getInfostoreTerm());
    }

    @Override
    public void visit(final MetaTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.MetaTerm(term.getPattern());
    }

    @Override
    public void visit(final NumberOfVersionsTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.NumberOfVersionsTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(final LastModifiedUtcTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.LastModifiedUtcTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(final ColorLabelTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.ColorLabelTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(final CurrentVersionTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CurrentVersionTerm(term.getPattern().booleanValue());
    }

    @Override
    public void visit(final VersionCommentTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.VersionCommentTerm(term.getPattern(), term.isIgnoreCase());
    }

    @Override
    public void visit(final FileMd5SumTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.FileMd5SumTerm(term.getPattern());
    }

    @Override
    public void visit(final LockedUntilTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.LockedUntilTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(final CategoriesTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CategoriesTerm(term.getPattern());
    }

    @Override
    public void visit(final SequenceNumberTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.SequenceNumberTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(final FileMimeTypeTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.FileMimeTypeTerm(term.getPattern());
    }

    @Override
    public void visit(final FileNameTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.FileNameTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(final LastModifiedTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.LastModifiedTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(final CreatedTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CreatedTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(final ModifiedByTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.ModifiedByTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(final TitleTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.TitleTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(final VersionTerm term) throws OXException {
        String sVersion = term.getPattern();
        infstoreTerm = new com.openexchange.groupware.infostore.search.VersionTerm(FileStorageFileAccess.CURRENT_VERSION == sVersion ? InfostoreFacade.CURRENT_VERSION : Utils.parseUnsignedInt(sVersion.trim()));
    }

    @Override
    public void visit(final ContentTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.ContentTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(final FileSizeTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.FileSizeTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(final DescriptionTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.DescriptionTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(final UrlTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.UrlTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(final CreatedByTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CreatedByTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(MediaDateTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.MediaDateTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(CameraModelTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraModelTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(CameraMakeTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraMakeTerm(term.getPattern(), term.isIgnoreCase(), term.isSubstringSearch());
    }

    @Override
    public void visit(CaptureDateTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CaptureDateTerm(new ComparablePatternImpl<Date>(term.getPattern()));
    }

    @Override
    public void visit(HeightTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.HeightTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(CameraIsoSpeedTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraIsoSpeedTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(CameraApertureTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraApertureTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(CameraExposureTimeTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraExposureTimeTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(CameraFocalLengthTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.CameraFocalLengthTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    @Override
    public void visit(WidthTerm term) throws OXException {
        infstoreTerm = new com.openexchange.groupware.infostore.search.WidthTerm(new ComparablePatternImpl<Number>(term.getPattern()));
    }

    private static final class ComparablePatternImpl<T> implements com.openexchange.groupware.infostore.search.ComparablePattern<T> {

        private final com.openexchange.file.storage.search.ComparablePattern<T> cp;

        /**
         * Initializes a new {@link ToInfostoreTermVisitor.ComparablePatternImpl}.
         */
        ComparablePatternImpl(final com.openexchange.file.storage.search.ComparablePattern<T> cp) {
            super();
            this.cp = cp;
        }

        @Override
        public ComparisonType getComparisonType() {
            switch (cp.getComparisonType()) {
            case EQUALS:
                return com.openexchange.groupware.infostore.search.ComparisonType.EQUALS;
            case LESS_THAN:
                return com.openexchange.groupware.infostore.search.ComparisonType.LESS_THAN;
            case GREATER_THAN:
                return com.openexchange.groupware.infostore.search.ComparisonType.GREATER_THAN;
            default:
                return null;
            }
        }

        @Override
        public T getPattern() {
            return cp.getPattern();
        }

    }

}
