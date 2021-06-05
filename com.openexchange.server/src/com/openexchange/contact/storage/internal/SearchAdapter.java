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

package com.openexchange.contact.storage.internal;

import static com.openexchange.java.Autoboxing.I;
import org.json.JSONException;
import com.openexchange.contact.ContactFieldOperand;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.ContactConfig;
import com.openexchange.groupware.contact.Search;
import com.openexchange.groupware.contact.helpers.ContactField;
import com.openexchange.groupware.search.ContactSearchObject;
import com.openexchange.search.CompositeSearchTerm;
import com.openexchange.search.CompositeSearchTerm.CompositeOperation;
import com.openexchange.search.SearchTerm;
import com.openexchange.search.SingleSearchTerm;
import com.openexchange.search.SingleSearchTerm.SingleOperation;
import com.openexchange.search.internal.operands.ColumnOperand;
import com.openexchange.search.internal.operands.ConstantOperand;

/**
 * {@link SearchAdapter}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class SearchAdapter {

    private final SearchTerm<?> term;

    public SearchAdapter(ContactSearchObject contactSearch) throws OXException {
        this.term = null != contactSearch.getPattern() ? parseSearchTerm(contactSearch) : parseSearchTermAlternative(contactSearch);
    }

    /**
     * @return the term
     */
    public SearchTerm<?> getSearchTerm() {
        return term;
    }

    /**
     * Parses a search term from JSON.
     *
     * @param json
     * @return
     * @throws JSONException
     * @throws OXException
     */
    private SearchTerm<?> parseSearchTerm(ContactSearchObject contactSearch) throws OXException {
        String pattern = contactSearch.getPattern();
        /*
         * use start letter term when set
         */
        SearchTerm<?> searchTerm = contactSearch.isStartLetter() ? getStartLetterTerm(pattern) : null;
        if (null == searchTerm || null == searchTerm.getOperands() || 0 == searchTerm.getOperands().length) {
            /*
             * fallback to display name search
             */
            searchTerm = getSearchTerm(ContactField.DISPLAY_NAME, pattern, true, true);
        }
        /*
         * combine with folders term when set
         */
        SearchTerm<?> foldersTerm = contactSearch.hasFolders() ? parseFoldersTerm(contactSearch.getFolders()) : null;
        if (null != foldersTerm && null != foldersTerm.getOperands() && 0 < foldersTerm.getOperands().length) {
            searchTerm = null == searchTerm ? foldersTerm : getCompositeTerm(foldersTerm, searchTerm);
        }
        return searchTerm;
    }

    /**
     * Parses an alternative search term from JSON.
     *
     * @param json
     * @return
     * @throws JSONException
     * @throws OXException
     */
    private SearchTerm<?> parseSearchTermAlternative(ContactSearchObject contactSearch) throws OXException {
        /*
         * create composite term
         */
        boolean emailAutoComplete = contactSearch.isEmailAutoComplete();
        boolean orSearch = emailAutoComplete || contactSearch.isOrSearch();
        CompositeSearchTerm searchCriteriaTerm = new CompositeSearchTerm(orSearch ? CompositeOperation.OR : CompositeOperation.AND);
        /*
         * add search criteria
         */
        if (null != contactSearch.getSurname()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.SUR_NAME, contactSearch.getSurname(), false == orSearch, true));
        }
        if (null != contactSearch.getGivenName()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.GIVEN_NAME, contactSearch.getGivenName(), false == orSearch, true));
        }
        if (null != contactSearch.getDisplayName()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.DISPLAY_NAME, contactSearch.getDisplayName(), false == orSearch, true));
        }
        if (null != contactSearch.getEmail1()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.EMAIL1, contactSearch.getEmail1(), false == orSearch, true));
        }
        if (null != contactSearch.getEmail2()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.EMAIL2, contactSearch.getEmail2(), false == orSearch, true));
        }
        if (null != contactSearch.getEmail3()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.EMAIL3, contactSearch.getEmail3(), false == orSearch, true));
        }
        if (null != contactSearch.getCompany()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.COMPANY, contactSearch.getCompany(), false == orSearch, true));
        }
        if (null != contactSearch.getStreetBusiness()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.STREET_BUSINESS, contactSearch.getStreetBusiness(), false == orSearch, true));
        }
        if (null != contactSearch.getCityBusiness()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.CITY_BUSINESS, contactSearch.getCityBusiness(), false == orSearch, true));
        }
        if (null != contactSearch.getDepartment()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.DEPARTMENT, contactSearch.getDepartment(), false == orSearch, true));
        }
        if (null != contactSearch.getCatgories()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.CATEGORIES, contactSearch.getCatgories(), false == orSearch, true));
        }
        if (null != contactSearch.getYomiLastName()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.YOMI_LAST_NAME, contactSearch.getYomiLastName(), false == orSearch, true));
        }
        if (null != contactSearch.getYomiFirstName()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.YOMI_FIRST_NAME, contactSearch.getYomiFirstName(), false == orSearch, true));
        }
        if (null != contactSearch.getYomiCompany()) {
            searchCriteriaTerm.addSearchTerm(getSearchTerm(ContactField.YOMI_COMPANY, contactSearch.getYomiCompany(), false == orSearch, true));
        }

        SearchTerm<?> searchTerm = searchCriteriaTerm;

        if (contactSearch.hasImage()) {
            searchTerm = getCompositeTerm(searchTerm, HAS_IMG_TERM);
        }

        /*
         * combine with email auto complete
         */
        if (emailAutoComplete) {
            searchTerm = getCompositeTerm(searchTerm, HAS_EMAIL_TERM);
        }

        /*
         * combine with folders term when set
         */
        SearchTerm<?> foldersTerm = contactSearch.hasFolders() ? parseFoldersTerm(contactSearch.getFolders()) : null;
        if (null != foldersTerm && null != foldersTerm.getOperands() && 0 < foldersTerm.getOperands().length) {
            searchTerm = getCompositeTerm(foldersTerm, searchTerm);
        }
        return searchTerm;
    }

    /**
     * Creates a new 'AND' composite search term using the supplied terms as
     * operands.
     *
     * @param term1 the first term
     * @param term2 the second term
     * @return the composite search term
     */
    private static SearchTerm<?> getCompositeTerm(SearchTerm<?> term1, SearchTerm<?> term2) {
        if (null == term1 || null == term1.getOperands() || 0 == term1.getOperands().length) {
            return term2;
        }
        if (null == term2 || null == term2.getOperands() || 0 == term2.getOperands().length) {
            return term1;
        }
        CompositeSearchTerm andTerm = new CompositeSearchTerm(CompositeOperation.AND);
        andTerm.addSearchTerm(term1);
        andTerm.addSearchTerm(term2);
        return andTerm;
    }

    /**
     * Parses the "folder" information from the supplied json object and puts
     * the folder IDs into a suitable search term.
     *
     * @param json
     * @return
     * @throws JSONException
     * @throws OXException
     */
    private static SearchTerm<?> parseFoldersTerm(int[] folders) throws OXException {
        return getFoldersTerm(folders);
    }

    /**
     * Creates a search term to find contacts based on their parent folder.
     *
     * @param folderIDs the IDs of the folders
     * @return the search term
     */
    private static SearchTerm<?> getFoldersTerm(int[] folderIDs) {
        if (null == folderIDs || 0 == folderIDs.length) {
            return null;
        } else if (1 == folderIDs.length) {
            return getFolderTerm(folderIDs[0]);
        } else {
            CompositeSearchTerm orTerm = new CompositeSearchTerm(CompositeOperation.OR);
            for (int folderID : folderIDs) {
                orTerm.addSearchTerm(getFolderTerm(folderID));
            }
            return orTerm;
        }
    }

    /**
     * Creates a search term to find contacts based on their parent folder.
     *
     * @param folderID the ID of the folder
     * @return the search term
     */
    private static SingleSearchTerm getFolderTerm(int folderID) {
        SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
        term.addOperand(new ContactFieldOperand(ContactField.FOLDER_ID));
        term.addOperand(new ConstantOperand<String>(Integer.toString(folderID)));
        return term;
    }

    /**
     * Creates a search term to find contacts based on their start letter.
     *
     * @param pattern the start letter pattern
     * @return the search term
     */
    private static SearchTerm<?> getStartLetterTerm(String pattern) {
        String field = ContactConfig.getInstance().getString(ContactConfig.Property.LETTER_FIELD);
        if (".".equals(pattern) || "#".equals(pattern)) {
            CompositeSearchTerm andTerm = new CompositeSearchTerm(CompositeOperation.AND);
            CompositeSearchTerm orTerm = new CompositeSearchTerm(CompositeOperation.OR);
            SingleSearchTerm lessThanTerm = new SingleSearchTerm(SingleOperation.LESS_THAN);
            lessThanTerm.addOperand(new ColumnOperand(field));
            lessThanTerm.addOperand(new ConstantOperand<String>("0*"));
            orTerm.addSearchTerm(lessThanTerm);
            SingleSearchTerm greaterThanTerm = new SingleSearchTerm(SingleOperation.GREATER_THAN);
            greaterThanTerm.addOperand(new ColumnOperand(field));
            greaterThanTerm.addOperand(new ConstantOperand<String>("z*"));
            orTerm.addSearchTerm(greaterThanTerm);
            andTerm.addSearchTerm(orTerm);
            SingleSearchTerm notEqualsTerm = new SingleSearchTerm(SingleOperation.NOT_EQUALS);
            notEqualsTerm.addOperand(new ColumnOperand(field));
            notEqualsTerm.addOperand(new ConstantOperand<String>("z*"));
            andTerm.addSearchTerm(notEqualsTerm);
            return andTerm;
        } else if (pattern.matches("\\d")) {
            CompositeSearchTerm andTerm = new CompositeSearchTerm(CompositeOperation.AND);
            SingleSearchTerm greaterThanTerm = new SingleSearchTerm(SingleOperation.GREATER_THAN);
            greaterThanTerm.addOperand(new ColumnOperand(field));
            greaterThanTerm.addOperand(new ConstantOperand<String>("0*"));
            andTerm.addSearchTerm(greaterThanTerm);
            SingleSearchTerm lessThanTerm = new SingleSearchTerm(SingleOperation.LESS_THAN);
            lessThanTerm.addOperand(new ColumnOperand(field));
            lessThanTerm.addOperand(new ConstantOperand<String>("a*"));
            andTerm.addSearchTerm(lessThanTerm);
            return andTerm;
        } else if (false == "all".equals(pattern)) {
            /*
             * ( ! ( <field> IS NULL ) AND <field> LIKE '<pattern>%' ) OR ( <field> IS NULL AND <fallbackField> LIKE '<pattern>%' )
             */
            ContactField fallbackField = ContactField.DISPLAY_NAME;
            CompositeSearchTerm orTerm = new CompositeSearchTerm(CompositeOperation.OR);
            CompositeSearchTerm andTerm = new CompositeSearchTerm(CompositeOperation.AND);
            CompositeSearchTerm andTerm2 = new CompositeSearchTerm(CompositeOperation.AND);
            /*
             * ! ( <field> IS NULL )
             */
            CompositeSearchTerm notTerm = new CompositeSearchTerm(CompositeOperation.NOT);
            SingleSearchTerm isNullTerm = new SingleSearchTerm(SingleOperation.ISNULL);
            isNullTerm.addOperand(new ColumnOperand(field));
            notTerm.addSearchTerm(isNullTerm);
            andTerm.addSearchTerm(notTerm);
            /*
             * <field> LIKE '<pattern>%'
             */
            String preparedPattern = prepareForSearch(pattern, false, true);
            SingleSearchTerm equalsTerm = new SingleSearchTerm(SingleOperation.EQUALS);
            equalsTerm.addOperand(new ColumnOperand(field));
            equalsTerm.addOperand(new ConstantOperand<String>(preparedPattern));
            andTerm.addSearchTerm(equalsTerm);
            /*
             * <field> IS NULL
             */
            andTerm2.addSearchTerm(isNullTerm);
            /*
             * <fallbackField> LIKE '<pattern>%'
             */
            SingleSearchTerm equalsTerm2 = new SingleSearchTerm(SingleOperation.EQUALS);
            equalsTerm2.addOperand(new ContactFieldOperand(fallbackField));
            equalsTerm2.addOperand(new ConstantOperand<String>(preparedPattern));
            andTerm2.addSearchTerm(equalsTerm2);

            orTerm.addSearchTerm(andTerm);
            orTerm.addSearchTerm(andTerm2);
            return orTerm;
        } else {
            /*
             * no valid start letter pattern
             */
            return null;
        }
    }

    private static final CompositeSearchTerm HAS_EMAIL_TERM;
    static {
        ContactField[] emailFields = { ContactField.EMAIL1, ContactField.EMAIL2, ContactField.EMAIL3 };
        CompositeSearchTerm andTerm = new CompositeSearchTerm(CompositeOperation.AND);
        for (ContactField field : emailFields) {
            SingleSearchTerm term = new SingleSearchTerm(SingleOperation.ISNULL);
            term.addOperand(new ContactFieldOperand(field));
            andTerm.addSearchTerm(term);
        }
        CompositeSearchTerm notTerm = new CompositeSearchTerm(CompositeOperation.NOT);
        notTerm.addSearchTerm(andTerm);
        SingleSearchTerm distributionListTerm = new SingleSearchTerm(SingleOperation.GREATER_THAN);
        distributionListTerm.addOperand(new ContactFieldOperand(ContactField.NUMBER_OF_DISTRIBUTIONLIST));
        distributionListTerm.addOperand(new ConstantOperand<Integer>(0));
        final CompositeSearchTerm hasEmailTerm = new CompositeSearchTerm(CompositeOperation.OR);
        hasEmailTerm.addSearchTerm(notTerm);
        hasEmailTerm.addSearchTerm(distributionListTerm);
        HAS_EMAIL_TERM = hasEmailTerm;
    }

    private static final SingleSearchTerm HAS_IMG_TERM;

    static {
        SingleSearchTerm term = new SingleSearchTerm(SingleOperation.GREATER_THAN);
        term.addOperand(new ContactFieldOperand(ContactField.NUMBER_OF_IMAGES));
        term.addOperand(new ConstantOperand<Integer>(I(0)));
        HAS_IMG_TERM = term;
    }

    private static SingleSearchTerm getSearchTerm(ContactField field, String pattern, boolean prependWildcard,
            boolean appendWildcard) throws OXException {
        SingleSearchTerm term = new SingleSearchTerm(SingleOperation.EQUALS);
        term.addOperand(new ContactFieldOperand(field));
        Search.checkPatternLength(pattern);
        term.addOperand(new ConstantOperand<String>(prepareForSearch(pattern, prependWildcard, appendWildcard)));
        return term;
    }

    private static String prepareForSearch(String pattern, boolean prependWildcard, boolean appendWildcard) {
        if (prependWildcard && false == pattern.startsWith("*")) {
            pattern = "*" + pattern;
        }
        if (prependWildcard && false == pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        return pattern;
    }

}
