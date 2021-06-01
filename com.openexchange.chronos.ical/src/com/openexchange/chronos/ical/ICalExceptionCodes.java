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

package com.openexchange.chronos.ical;

import static com.openexchange.chronos.ical.ICalExceptionMessages.CONVERSION_FAILED_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.INVALID_CALENDAR_CONTENT_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.NO_CALENDAR_FOUND_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.PARSER_ERROR_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.TOO_MANY_IMPORTS_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.TRUNCATED_RESULTS_MSG;
import static com.openexchange.chronos.ical.ICalExceptionMessages.VALIDATION_FAILED_MSG;
import static com.openexchange.exception.OXExceptionStrings.MESSAGE;
import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;

/**
 * {@link ICalExceptionCodes}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public enum ICalExceptionCodes implements DisplayableOXExceptionCode {

    /**
     * <li>An error occurred inside the server which prevented it from fulfilling the request.</li>
     * <li>An I/O error occurred: %1$s</li>
     */
    IO_ERROR("An I/O error occurred: %1$s", MESSAGE, Category.CATEGORY_ERROR, 1),
    /**
     * Conversion failed for property \"%1$s\": %2$s
     */
    CONVERSION_FAILED(CONVERSION_FAILED_MSG, CONVERSION_FAILED_MSG, Category.CATEGORY_WARNING, 2),
    /**
     * <li>No calendar data could be found in the supplied file. Please use a valid iCalendar file and try again.</li>
     * <li>No calendar data found</li>
     */
    NO_CALENDAR("No calendar data found", NO_CALENDAR_FOUND_MSG, Category.CATEGORY_USER_INPUT, 3),
    /**
     * <li>Error reading calendar data at line %1$d: %2$s</li>
     * <li>Parser error at line %1$d: %2$s</li>
     */
    PARSER_ERROR("Parser error at line %1$d: %2$s", PARSER_ERROR_MSG, Category.CATEGORY_WARNING, 4),
    /**
     * <li>Validation failed: %1$s"</li>
     */
    VALIDATION_FAILED(VALIDATION_FAILED_MSG, VALIDATION_FAILED_MSG, Category.CATEGORY_WARNING, 5),
    /**
     * <li>Not all of the objects could be imported due to a configured limitation.</li>
     * <li>Import truncated after %1$d objects (%2$d available)</li>
     */
    TRUNCATED_RESULTS("Import truncated after %1$d objects (%2$d available)", TRUNCATED_RESULTS_MSG, Category.CATEGORY_TRUNCATED, 6),
    /**
     * <li>The given iCalendar source does not contain valid content and cannot be processed.</li>
     * <li>The calendar source does not contain valid content</li>
     */
    INVALID_CALENDAR_CONTENT("The calendar source does not contain valid content", INVALID_CALENDAR_CONTENT_MSG, Category.CATEGORY_USER_INPUT, 7),

    /**
     * <li>The given iCal file contains too many entities. A max of %1$d entities are allowed per file.</li>
     */
    TOO_MANY_IMPORTS(TOO_MANY_IMPORTS_MSG, TOO_MANY_IMPORTS_MSG, Category.CATEGORY_USER_INPUT, 8),
    ;

    public static final String PREFIX = "ICAL".intern();

    private String message;
    private String displayMessage;
    private Category category;
    private int number;

    private ICalExceptionCodes(String message, String displayMessage, Category category, int number) {
        this.message = message;
        this.displayMessage = displayMessage != null ? displayMessage : OXExceptionStrings.MESSAGE;
        this.category = category;
        this.number = number;
    }

    @Override
    public int getNumber() {
        return number;
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
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public boolean equals(OXException e) {
        return OXExceptionFactory.getInstance().equals(this, e);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @return The newly created {@link OXException} instance
     */
    public OXException create() {
        return OXExceptionFactory.getInstance().create(this, new Object[0]);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Object... args) {
        return OXExceptionFactory.getInstance().create(this, (Throwable) null, args);
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param cause The optional initial cause
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Throwable cause, final Object... args) {
        return OXExceptionFactory.getInstance().create(this, cause, args);
    }

}
