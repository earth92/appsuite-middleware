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

package com.openexchange.utils.propertyhandling.internal;

import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;


/**
 * {@link ConfigurationExceptionCodes}
 *
 * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
 */
public enum ConfigurationExceptionCodes implements DisplayableOXExceptionCode {

    NO_CONFIGURATION_SERVICE_FOUND("No configuration service found.", CATEGORY_CONFIGURATION, 1, null),

    NO_INTEGER_VALUE("The value given in the property %1$s is no integer value.", CATEGORY_CONFIGURATION, 2, null),

    REQUIRED_PROPERTY_NOT_SET("Property %1$s not set but required.", CATEGORY_CONFIGURATION, 3, null),

    CONDITION_NOT_SET("Property %1$s claims to have condition but condition not set.", CATEGORY_CONFIGURATION, 4, null),

    MUST_BE_SET_TO("Property %1$s must be set if %2$s is set to %3$s", CATEGORY_CONFIGURATION, 5, null),

    UNKNOWN_TYPE_CLASS("The %1$s cannot be used as a property type in the property %2$s", CATEGORY_CONFIGURATION, 6, null);

    private final Category category;

    private final int detailNumber;

    private final String message;
    
    private String displayMessage;

    private ConfigurationExceptionCodes(final String message, final Category category, final int detailNumber, String displayMessage) {
        this.message = message;
        this.detailNumber = detailNumber;
        this.category = category;
        this.displayMessage = displayMessage != null ? displayMessage : OXExceptionStrings.MESSAGE;
    }

    @Override
    public boolean equals(OXException e) {
        return OXExceptionFactory.getInstance().equals(this, e);
    }

    @Override
    public int getNumber() {
        return detailNumber;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    private static final String PREFIX = "CONFIGURATION";
    
    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getMessage() {
        return message;
    }
    
    @Override
    public String getDisplayMessage() {
        return displayMessage;
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
