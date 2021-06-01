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

package com.openexchange.smtp.config;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.List;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Strings;
import com.openexchange.mail.api.AbstractProtocolProperties;
import com.openexchange.mail.transport.config.ITransportProperties;
import com.openexchange.mail.transport.config.TransportProperties;
import com.openexchange.smtp.services.Services;

/**
 * {@link SMTPProperties}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class SMTPProperties extends AbstractProtocolProperties implements ISMTPProperties {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SMTPProperties.class);

    private static final SMTPProperties instance = new SMTPProperties();

    /**
     * Gets the singleton instance of {@link SMTPProperties}
     *
     * @return The singleton instance of {@link SMTPProperties}
     */
    public static SMTPProperties getInstance() {
        return instance;
    }

    /*
     * Fields for global properties
     */

    private final ITransportProperties transportProperties;

    private String smtpLocalhost;

    private boolean smtpAuth;

    private boolean smtpEnvelopeFrom;

    private boolean sendPartial;

    private String smtpAuthEnc;

    private int smtpTimeout;

    private int smtpConnectionTimeout;

    private boolean logTransport;

    private String sslProtocols;

    private String cipherSuites;

    private String primaryAdressHeader;

    /**
     * Initializes a new {@link SMTPProperties}
     */
    private SMTPProperties() {
        super();
        transportProperties = TransportProperties.getInstance();
    }

    @Override
    protected void loadProperties0() throws OXException {
        StringBuilder logBuilder = new StringBuilder(1024);
        List<Object> args = new ArrayList<Object>(32);
        String lineSeparator = Strings.getLineSeparator();

        logBuilder.append("{}Loading global SMTP properties...{}");
        args.add(lineSeparator);
        args.add(lineSeparator);

        final ConfigurationService configuration = Services.getService(ConfigurationService.class);
        {
            final String smtpLocalhostStr = configuration.getProperty("com.openexchange.smtp.smtpLocalhost").trim();
            smtpLocalhost = (smtpLocalhostStr == null) || (smtpLocalhostStr.length() == 0) || "null".equalsIgnoreCase(smtpLocalhostStr) ? null : smtpLocalhostStr;
            logBuilder.append("    SMTP Localhost: {}{}");
            args.add(smtpLocalhost);
            args.add(lineSeparator);
        }

        {
            final String smtpAuthStr = configuration.getProperty("com.openexchange.smtp.smtpAuthentication", "false").trim();
            smtpAuth = Boolean.parseBoolean(smtpAuthStr);
            logBuilder.append("    SMTP Authentication: {}{}");
            args.add(B(smtpAuth));
            args.add(lineSeparator);
        }

        {
            final String sendPartialStr = configuration.getProperty("com.openexchange.smtp.sendPartial", "false").trim();
            sendPartial = Boolean.parseBoolean(sendPartialStr);
            logBuilder.append("    Send Partial: {}{}");
            args.add(B(sendPartial));
            args.add(lineSeparator);
        }

        {
            final String smtpEnvFromStr = configuration.getProperty("com.openexchange.smtp.setSMTPEnvelopeFrom", "false").trim();
            smtpEnvelopeFrom = Boolean.parseBoolean(smtpEnvFromStr);
            logBuilder.append("    Set SMTP ENVELOPE-FROM: {}{}");
            args.add(B(smtpEnvelopeFrom));
            args.add(lineSeparator);
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.smtp.logTransport", "false").trim();
            logTransport = Boolean.parseBoolean(tmp);
            logBuilder.append("    Log transport: {}{}");
            args.add(B(logTransport));
            args.add(lineSeparator);
        }

        {
            final String smtpAuthEncStr = configuration.getProperty("com.openexchange.smtp.smtpAuthEnc", "UTF-8").trim();
            if (CharsetDetector.isValid(smtpAuthEncStr)) {
                smtpAuthEnc = smtpAuthEncStr;
                logBuilder.append("    SMTP Auth Encoding: ").append(smtpAuthEnc).append('\n');
                args.add(smtpAuthEnc);
                args.add(lineSeparator);
            } else {
                smtpAuthEnc = "UTF-8";
                logBuilder.append("    SMTP Auth Encoding: Unsupported charset \"{}\". Setting to fallback {}{}");
                args.add(smtpAuthEncStr);
                args.add(smtpAuthEnc);
                args.add(lineSeparator);
            }
        }

        {
            final String smtpTimeoutStr = configuration.getProperty("com.openexchange.smtp.smtpTimeout", "5000").trim();
            try {
                smtpTimeout = Integer.parseInt(smtpTimeoutStr);
                logBuilder.append("    SMTP Timeout: {}{}");
                args.add(I(smtpTimeout));
                args.add(lineSeparator);
            } catch (NumberFormatException e) {
                smtpTimeout = 5000;
                logBuilder.append("    SMTP Timeout: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(smtpTimeoutStr);
                args.add(I(smtpTimeout));
                args.add(lineSeparator);

            }
        }

        {
            final String smtpConTimeoutStr = configuration.getProperty("com.openexchange.smtp.smtpConnectionTimeout", "10000").trim();
            try {
                smtpConnectionTimeout = Integer.parseInt(smtpConTimeoutStr);
                logBuilder.append("    SMTP Connection Timeout: {}{}");
                args.add(I(smtpConnectionTimeout));
                args.add(lineSeparator);
            } catch (NumberFormatException e) {
                smtpConnectionTimeout = 10000;
                logBuilder.append("    SMTP Connection Timeout: Invalid value \"{}\". Setting to fallback {}{}");
                args.add(smtpConTimeoutStr);
                args.add(I(smtpConnectionTimeout));
                args.add(lineSeparator);

            }
        }

        sslProtocols = configuration.getProperty("com.openexchange.smtp.ssl.protocols", "SSLv3 TLSv1").trim();
        logBuilder.append("    Supported SSL protocols: {}{}");
        args.add(sslProtocols);
        args.add(lineSeparator);

        {
            final String tmp = configuration.getProperty("com.openexchange.smtp.ssl.ciphersuites", "").trim();
            this.cipherSuites = Strings.isEmpty(tmp) ? null : tmp;
            logBuilder.append("    Supported SSL cipher suites: {}{}");
            args.add(null == this.cipherSuites ? "<default>" : cipherSuites);
            args.add(lineSeparator);
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.smtp.setPrimaryAddressHeader", "").trim();
            primaryAdressHeader = (tmp == null) || (tmp.length() == 0) || "null".equalsIgnoreCase(tmp) ? null : tmp;
            logBuilder.append("    Primary address header: {}{}");
            args.add(primaryAdressHeader);
            args.add(lineSeparator);
        }

        logBuilder.append("Global SMTP properties successfully loaded!");
        LOG.info(logBuilder.toString(), args.toArray(new Object[args.size()]));
    }

    @Override
    protected void resetFields() {
        smtpLocalhost = null;
        sendPartial = false;
        smtpAuth = false;
        smtpEnvelopeFrom = false;
        smtpAuthEnc = null;
        smtpTimeout = 0;
        smtpConnectionTimeout = 0;
        logTransport = false;
        sslProtocols = "SSLv3 TLSv1";
        cipherSuites = null;
        primaryAdressHeader = null;
    }

    @Override
    public String getSmtpLocalhost() {
        return smtpLocalhost;
    }

    @Override
    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    @Override
    public boolean isSendPartial() {
        return sendPartial;
    }

    @Override
    public boolean isSmtpEnvelopeFrom() {
        return smtpEnvelopeFrom;
    }

    @Override
    public boolean isLogTransport() {
        return logTransport;
    }

    @Override
    public String getSmtpAuthEnc() {
        return smtpAuthEnc;
    }

    @Override
    public int getSmtpTimeout() {
        return smtpTimeout;
    }

    @Override
    public int getSmtpConnectionTimeout() {
        return smtpConnectionTimeout;
    }

    @Override
    public int getReferencedPartLimit() {
        return transportProperties.getReferencedPartLimit();
    }

    @Override
    public String getSSLProtocols() {
        return sslProtocols;
    }

    @Override
    public String getSSLCipherSuites() {
        return cipherSuites;
    }

    @Override
    public String getPrimaryAddressHeader() {
        return primaryAdressHeader;
    }
}
