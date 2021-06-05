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

package com.openexchange.net.ssl.exception;

import java.util.Map;
import java.util.Map.Entry;
import com.openexchange.exception.Category;
import com.openexchange.exception.DisplayableOXExceptionCode;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionFactory;
import com.openexchange.exception.OXExceptionStrings;

/**
 * {@link SSLExceptionCode}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.8.3
 */
public enum SSLExceptionCode implements DisplayableOXExceptionCode {

    /**
     * <li>The certificate for domain '%2$s' is untrusted.</li>
     * <li>The certificate with fingerprint '%1$s' for domain '%2$s' is untrusted.</li>
     */
    UNTRUSTED_CERTIFICATE("The certificate with fingerprint '%1$s' for domain '%2$s' is untrusted.", CATEGORY_ERROR, 1, SSLExceptionMessages.UNTRUSTED_CERTIFICATE_MSG),
    /**
     * <li>The certificate for domain '%2$s' is untrusted. You can change your general trust level in the settings.</li>
     * <li>The certificate with fingerprint \"%1$s\" for domain \"%2$s\" is untrusted.</li>
     */
    UNTRUSTED_CERT_USER_CONFIG("The certificate with fingerprint '%1$s' for domain '%2$s' is untrusted.", CATEGORY_ERROR, 2, SSLExceptionMessages.UNTRUSTED_CERT_USER_CONFIG_MSG),
    /**
     * <li>The certificate is not trusted by the user.</li>
     * <li>The certificate with fingerprint '%1$s' is not trusted by the user '%2$s' in context '%3$s'</li>
     */
    USER_DOES_NOT_TRUST_CERTIFICATE("The certificate with fingerprint '%1$s' for domain '%2$s' is not trusted by the user '%3$s' in context '%4$s'", CATEGORY_ERROR, 3, SSLExceptionMessages.USER_DOES_NOT_TRUST_CERTIFICATE),
    /**
     * <li>The certificate is self-signed.</li>
     * <li>The certificate with fingerprint '%1$s' is self-signed.</li>
     */
    SELF_SIGNED_CERTIFICATE("The certificate with fingerprint '%1$s' for domain '%2$s' is self-signed", CATEGORY_ERROR, 4, SSLExceptionMessages.SELF_SIGNED_CERTIFICATE),
    /**
     * <li>The certificate is expired.</li>
     * <li>The certificate with fingerprint '%1$s' is expired.</li>
     */
    CERTIFICATE_IS_EXPIRED("The certificate with fingerprint '%1$s' for domain '%2$s' is expired", CATEGORY_ERROR, 5, SSLExceptionMessages.CERTIFICATE_IS_EXPIRED),
    /**
     * <li>The common name for the certificate is invalid.</li>
     * <li>The common name of the certificate with fingerprint '%1$s' does not match the requested endpoint's hostname '%2$s'.</li>
     */
    INVALID_HOSTNAME("The common name of the certificate with fingerprint '%1$s' does not match the requested endpoint's hostname '%2$s'", CATEGORY_ERROR, 6, SSLExceptionMessages.INVALID_COMMON_NAME),
    /**
     * <li>The root authority for the certificate is untrusted</li>
     * <li>The root authority of the certificate with fingerprint '%1$s' for domain '%2$s' is untrusted.</li>
     */
    UNTRUSTED_ROOT_AUTHORITY("The root authority of the certificate with fingerprint '%1$s' for domain '%2$s' is untrusted.", CATEGORY_ERROR, 7, SSLExceptionMessages.UNTRUSTED_ROOT_AUTHORITY),
    /**
     * <li>The certificate is using a weak algorithm</li>
     * <li>The certificate with fingerprint '%1$s' for domain '%2$s' is using a weak algorithm.</li>
     */
    WEAK_ALGORITHM("The certificate with fingerprint '%1$s' for domain '%2$s' is using a weak algorithm.", CATEGORY_ERROR, 8, SSLExceptionMessages.WEAK_ALGORITHM),
    /**
     * <li>The certificate was revoked.</li>
     * <li>The certificate with fingerprint '%1$s' for domain '%2$s' was revoked.</li>
     */
    CERTIFICATE_REVOKED("The certificate with fingerprint '%1$s' for domain '%2$s' was revoked.", CATEGORY_ERROR, 9, SSLExceptionMessages.CERTIFICATE_REVOKED),
    ;

    private static final String EMPTY_STRING = "";

    public static final String PREFIX = "SSL";

    private final Category category;
    private final int detailNumber;
    private final String message;
    private final String displayMessage;

    /**
     * Initializes a new {@link SSLExceptionCode}.
     * 
     * @param detailNumber
     */
    private SSLExceptionCode(final String message, final Category category, final int detailNumber) {
        this(message, category, detailNumber, null);
    }

    /**
     * Initializes a new {@link SSLExceptionCode}.
     */
    private SSLExceptionCode(final String message, final Category category, final int detailNumber, final String displayMessage) {
        this.message = message;
        this.detailNumber = detailNumber;
        this.category = category;
        this.displayMessage = displayMessage == null ? OXExceptionStrings.MESSAGE : displayMessage;
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getNumber() {
        return detailNumber;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public boolean equals(final OXException e) {
        return OXExceptionFactory.getInstance().equals(this, e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayMessage() {
        return this.displayMessage;
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

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param cause The optional initial cause
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(Map<String, Object> arguments, final Object... args) {
        OXException oxe = create(args);
        for (Entry<String, Object> entry : arguments.entrySet()) {
            oxe.setArgument(entry.getKey(), entry.getValue());
        }
        return oxe;
    }

    /**
     * Creates a new {@link OXException} instance pre-filled with this code's attributes.
     *
     * @param cause The optional initial cause
     * @param args The message arguments in case of printf-style message
     * @return The newly created {@link OXException} instance
     */
    public OXException create(final Throwable cause, Map<String, Object> arguments, final Object... args) {
        OXException oxe = OXExceptionFactory.getInstance().create(this, cause, args);
        for (Entry<String, Object> entry : arguments.entrySet()) {
            oxe.setArgument(entry.getKey(), entry.getValue());
        }
        return oxe;
    }

    /**
     * Extracts the argument with the specified name (if exists) from the specified {@link Throwable} chain
     * 
     * @param t The {@link Throwable} chain
     * @param argumentName The argument's name
     * @return The argument's value or an empty string if no argument with that name exists in the parameters of the specified {@link Throwable} chain
     */
    public static String extractArgument(Throwable t, String argumentName) {
        Throwable cause = t.getCause();
        while (cause != null) {
            if (OXException.class.isInstance(cause)) {
                OXException oxe = (OXException) cause;
                if (oxe.getPrefix().equals(SSLExceptionCode.PREFIX)) {
                    return (String) oxe.getArgument(argumentName);
                }
            }
            cause = cause.getCause();
        }
        return EMPTY_STRING;
    }
}
