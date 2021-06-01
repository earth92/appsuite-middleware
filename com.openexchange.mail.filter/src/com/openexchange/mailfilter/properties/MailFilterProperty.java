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

package com.openexchange.mailfilter.properties;

import static com.openexchange.java.Autoboxing.I;
import com.openexchange.config.lean.Property;
import com.openexchange.mailfilter.MailFilterService;

/**
 * {@link MailFilterProperty} - Defines all available properties for the {@link MailFilterService}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public enum MailFilterProperty implements Property {

    /**
     * Specify which sieve server should be used. Two options are allowed here:
     * <li>{@link LoginType#USER}</li>
     * <li>{@link LoginType#GLOBAL}</li>
     */
    loginType(LoginType.USER.name),

    /**
     * Specify which sieve credentials should be use. Following options are allowed here:
     *
     * <li>{@link CredentialSource#SESSION}: login name and password are used from the current session</li>
     * <li>{@link CredentialSource#SESSION_FULL_LOGIN}: full login (incl. context part) name and password
     * are used from the current session</i>
     * <li>{@link CredentialSource#IMAP_LOGIN}: the login name is taken from the field imapLogin of the current
     * user the password is taken from the current session</li>
     * <li>{@link CredentialSource#MAIL}: use the primary mail address of the user and the password from the
     * session</li>
     */
    credentialSource(CredentialSource.SESSION.name),

    /**
     * To override the sieve server defaults specify a value for the sieve server here
     */
    server("localhost"),

    /**
     * <p>Specify the SIEVE port</p>
     *
     * <p><b>NOTE</b>: <code>2000</code> is the deprecated port number for SIEVE (now assigned to
     * some Cisco SCCP protocol by the IANA).
     * <code>4190</code> is the new one used with most recent Linux and IMAP implementations.
     * Please check your system's default port defined at <code>/etc/services</code>.</p>
     */
    port(I(4190)),

    /**
     * If you want the script to be generated with another script name change it here.
     * Note that the mail filter bundle will then leave the old script with the old
     * script name behind, and doesn't delete it
     */
    scriptName("Open-Xchange"),

    /**
     * Define the charset encoding to use for authentication to sieve server
     */
    authenticationEncoding("UTF-8"),

    /**
     * Define the regex which recognizes servers with incorrect sieve TLS implementation
     */
    nonRFCCompliantTLSRegex("^Cyrus.*v([0-1]\\.[0-9].*|2\\.[0-2].*|2\\.3\\.[0-9]|2\\.3\\.[0-9][^0-9].*)$"),

    /**
     * Whether to use TLS if available
     */
    tls(Boolean.TRUE),

    /**
     * What protocols to use if TLS is enabled
     */
    protocols("SSLv3, TLSv1, TLSv1.1, TLSv1.2", true, "com.openexchange.mail.filter.tls."),

    /**
     * Specify here if vacation messages should only be sent to specific domains
     * If multiple domains are given, they should be separated by ","
     * e.g. VACATION_DOMAINS=testdomain.com,example.com
     */
    vacationDomains(MailFilterProperty.EMPTY, true),

    /**
     * Specifies when the connection should time out (value in milliseconds)
     */
    connectionTimeout(I(30000)),

    /**
     * Specifies when the connection should time out (value in milliseconds) when performing
     * SASL authentication against Sieve end-point. Default is 6000
     */
    authTimeout(I(6000), true),

    /**
     * <p>Set the password source; meaning which source is taken to determine a user's
     * password to login into mail filter system. If 'session' is set, then user's individual
     * system's password is taken. If 'global' is set, then the value specified through
     * property 'com.openexchange.mail.filter.masterPassword' is taken.</p>
     *
     * <p>Currently known values: {@link PasswordSource#SESSION} and {@link PasswordSource#GLOBAL}</p>
     */
    passwordSource("session"),

    /**
     * The master password for mail/transport server. Only takes effect when property
     * "com.openexchange.mail.filter.passwordSource" is set to "global"
     */
    masterPassword,

    /**
     * <p>This property defines if mailbox names shall be UTF-7 encoded as specified in
     * RFC2060; section 5.1.3. "Mailbox International Naming Convention".
     * Default is "false"; meaning no UTF-7 encoding is performed.</p>
     *
     * <p>Set to "true" for those Cyrus IMAP server versions that do NOT support
     * "sieve_utf8fileinto" property (e.g. lower than v2.3.11) Set to "true" for those
     * Cyrus IMAP server versions that support "sieve_utf8fileinto" property having that
     * property set to "0". Thus moving mails with the 'fileinto' command will properly
     * work for mailbox names that contain non-ascii characters</p>
     */
    useUTF7FolderEncoding(Boolean.FALSE),

    /**
     * Enable punycode encoding for the username used in authentication
     * against the managesieve server
     */
    punycode(Boolean.FALSE),

    /**
     * Interpret SIEVE Response Codes, see https://tools.ietf.org/html/rfc5804#section-1.3
     * in most cases, this option must be kept to false
     */
    useSIEVEResponseCodes(Boolean.FALSE),

    /**
     * Specify a comma-separated list of domains (wild-card syntax supported) that are allowed
     * for redirect rules
     */
    redirectWhitelist,

    /**
     * <p>Specifies the preferred SASL authentication mechanism.
     * An empty value falls-back to "PLAIN"</p>
     *
     * <p>Known values: GSSAPI, XOAUTH2, OAUTHBEARER</p>
     *
     * <p>Default is empty (which results in "PLAIN" being used).</p>
     */
    preferredSaslMech,

    /**
     * Specifies the time out (value in milliseconds) how long a Sieve end-point is supposed to be considered as down
     * once a connect timeout occurred
     */
    tempDownTimeout(I(10000), true),

    /**
     * The enabled flag for mail filter circuit breaker
     */
    enabled(Boolean.FALSE, true, "com.openexchange.mail.filter.breaker."),

    /**
     * The failure threshold for mail filter circuit breaker, which is the number of successive failures that must occur in order to open
     * the circuit
     */
    failureThreshold("5", true, "com.openexchange.mail.filter.breaker."),

    /**
     * The number of executions to measure the failures against
     */
    failureExecutions("", true, "com.openexchange.mail.filter.breaker."),

    /**
     * The success threshold for mail filter circuit breaker, which is the number of successive successful executions that must occur when
     * in a half-open state in order to close the circuit
     */
    successThreshold("2", true, "com.openexchange.mail.filter.breaker."),

    /**
     * The number of executions to measure the successes against
     */
    successExecutions("", true, "com.openexchange.mail.filter.breaker."),

    /**
     * The delay in milliseconds for mail filter circuit breaker, which is the number of milliseconds to wait in open state before
     * transitioning to half-open
     */
    delayMillis("60000", true, "com.openexchange.mail.filter.breaker.");

    private static final String EMPTY = "";
    private static final String PREFIX = "com.openexchange.mail.filter.";

    private final String fqPrefix;
    private final Object defaultValue;
    private final boolean optional;

    /**
     * Initializes a new {@link MailFilterProperty}.
     */
    private MailFilterProperty() {
        this(EMPTY);
    }

    /**
     * Initializes a new {@link MailFilterProperty}.
     *
     * @param defaultValue The default value of the property
     */
    private MailFilterProperty(Object defaultValue) {
        this(defaultValue, false);
    }

    /**
     * Initializes a new {@link MailFilterProperty}.
     *
     * @param defaultValue The default value of the property
     * @param optional Whether the property is optional
     */
    private MailFilterProperty(Object defaultValue, boolean optional) {
        this(defaultValue, optional, PREFIX);
    }

    /**
     * Initializes a new {@link MailFilterProperty}.
     *
     * @param defaultValue The default value of the property
     * @param optional Whether the property is optional
     */
    private MailFilterProperty(Object defaultValue, boolean optional, String prefix) {
        this.optional = optional;
        this.defaultValue = defaultValue;
        this.fqPrefix = prefix;
    }

    /**
     * Returns whether the property is optional
     *
     * @return <code>true</code> if the property is optional; <code>false</code> otherwise
     */
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String getFQPropertyName() {
        return fqPrefix + name();
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }
}
