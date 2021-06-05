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

package com.openexchange.tools.iterator;

import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;

/**
 * The {@link SearchIterator} error code enumeration.
 */
public enum SearchIteratorExceptionCodes implements DisplayableOXExceptionCode {

    /**
     * A SQL error occurred: %1$s
     */
    SQL_ERROR("A SQL error occurred: %1$s", Category.CATEGORY_ERROR, 1),
    /**
     * A DBPool error occurred: %1$s
     */
    DBPOOLING_ERROR("A DBPool error occurred: 1$%s", Category.CATEGORY_ERROR, 2),
    /**
     * Operation not allowed on a closed SearchIterator
     */
    CLOSED("Operation not allowed on a closed SearchIterator", Category.CATEGORY_ERROR, 3),
    /**
     * Mapping for %1$d not implemented
     */
    NOT_IMPLEMENTED("Mapping for %1$d not implemented", Category.CATEGORY_ERROR, 4),

    /**
     * FreeBusyResults calculation problem with oid: %1$d
     */
    CALCULATION_ERROR("FreeBusyResults calculation problem with oid: %1$d", Category.CATEGORY_ERROR, 5),
    /**
     * Invalid constructor argument. Instance of %1$s not supported
     */
    INVALID_CONSTRUCTOR_ARG("Invalid constructor argument. Instance of %1$s not supported", Category.CATEGORY_ERROR, 6),
    /**
     * No such element.
     */
    NO_SUCH_ELEMENT("No such element.", Category.CATEGORY_ERROR, 7),
    /**
     * An unexpected error occurred: %1$s
     */
    UNEXPECTED_ERROR("An unexpected error occurred: %1$s", Category.CATEGORY_ERROR, 8);

    private final String message;

    private final String displayMessage;

    private final int detailNumber;

    private final Category category;

    private SearchIteratorExceptionCodes(final String message, final Category category, final int detailNumber) {
        this(message, category, detailNumber, null);
    }

    private SearchIteratorExceptionCodes(final String message, final Category category, final int detailNumber, final String displayMessage) {
        this.message = message;
        this.category = category;
        this.detailNumber = detailNumber;
        this.displayMessage = displayMessage == null ? OXExceptionStrings.MESSAGE : displayMessage;
    }

    private static final String PREFIX = "FLD";

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public int getNumber() {
        return detailNumber;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDisplayMessage() {
        return displayMessage;
    }

    @Override
    public boolean equals(final OXException e) {
        return OXExceptionFactory.getInstance().equals(this, e);
    }

    /**
     * Creates an {@link OXException} instance using this error code.
     *
     * @return The newly created {@link OXException} instance.
     */
    public OXException create() {
        return create(new Object[0]);
    }

    /**
     * Creates an {@link OXException} instance using this error code.
     *
     * @param logArguments The arguments for log message.
     * @return The newly created {@link OXException} instance.
     */
    public OXException create(final Object... logArguments) {
        return create(null, logArguments);
    }

    /**
     * Creates an {@link OXException} instance using this error code.
     *
     * @param cause The initial cause for {@link OXException}
     * @param arguments The arguments for message.
     * @return The newly created {@link OXException} instance.
     */
    public OXException create(final Throwable cause, final Object... arguments) {
        return OXExceptionFactory.getInstance().create(this, cause, arguments);
    }
}
