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

package com.openexchange.pop3.config;

import static com.openexchange.pop3.services.POP3ServiceRegistry.getServiceRegistry;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Strings;
import com.openexchange.mail.api.AbstractProtocolProperties;
import com.openexchange.mail.api.IMailProperties;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.spamhandler.SpamHandler;

/**
 * {@link POP3Properties} - POP3 properties loaded from properties file.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class POP3Properties extends AbstractProtocolProperties implements IPOP3Properties {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(POP3Properties.class);

    private static final POP3Properties instance = new POP3Properties();

    /**
     * Gets the singleton instance of {@link POP3Properties}
     *
     * @return The singleton instance of {@link POP3Properties}
     */
    public static POP3Properties getInstance() {
        return instance;
    }

    /*
     * Fields for global properties
     */

    private final IMailProperties mailProperties;

    private int pop3Timeout;

    private int pop3ConnectionTimeout;

    private int pop3ConnectionIdleTime;

    private int pop3TemporaryDown;

    private int pop3BlockSize;

    private String pop3AuthEnc;

    private String spamHandlerName;

    private String sslProtocols;

    private String cipherSuites;

    /**
     * Initializes a new {@link POP3Properties}
     */
    private POP3Properties() {
        super();
        mailProperties = MailProperties.getInstance();
    }

    @Override
    protected void loadProperties0() throws OXException {
        final StringBuilder logBuilder = new StringBuilder(1024);
        logBuilder.append("\nLoading global POP3 properties...\n");

        final ConfigurationService configuration = getServiceRegistry().getService(ConfigurationService.class);

        {
            final String pop3TimeoutStr = configuration.getProperty("com.openexchange.pop3.pop3Timeout", "0").trim();
            try {
                pop3Timeout = Integer.parseInt(pop3TimeoutStr);
                logBuilder.append("\tPOP3 Timeout: ").append(pop3Timeout).append('\n');
            } catch (NumberFormatException e) {
                pop3Timeout = 0;
                logBuilder.append("\tPOP3 Timeout: Invalid value \"").append(pop3TimeoutStr).append("\". Setting to fallback: ").append(
                    pop3Timeout).append('\n');
            }
        }

        {
            final String pop3ConTimeoutStr = configuration.getProperty("com.openexchange.pop3.pop3ConnectionTimeout", "0").trim();
            try {
                pop3ConnectionTimeout = Integer.parseInt(pop3ConTimeoutStr);
                logBuilder.append("\tPOP3 Connection Timeout: ").append(pop3ConnectionTimeout).append('\n');
            } catch (NumberFormatException e) {
                pop3ConnectionTimeout = 0;
                logBuilder.append("\tPOP3 Connection Timeout: Invalid value \"").append(pop3ConTimeoutStr).append(
                    "\". Setting to fallback: ").append(pop3ConnectionTimeout).append('\n');
            }
        }

        {
            final String pop3TempDownStr = configuration.getProperty("com.openexchange.pop3.pop3TemporaryDown", "0").trim();
            try {
                pop3TemporaryDown = Integer.parseInt(pop3TempDownStr);
                logBuilder.append("\tPOP3 Temporary Down: ").append(pop3TemporaryDown).append('\n');
            } catch (NumberFormatException e) {
                pop3TemporaryDown = 0;
                logBuilder.append("\tPOP3 Temporary Down: Invalid value \"").append(pop3TempDownStr).append("\". Setting to fallback: ").append(
                    pop3TemporaryDown).append('\n');
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.pop3.pop3BlockSize", "100").trim();
            try {
                pop3BlockSize = Integer.parseInt(tmp);
                if (pop3BlockSize <= 0) {
                    pop3BlockSize = 100;
                    logBuilder.append("\tPOP3 Block Size: Invalid value \"").append(tmp).append("\". Setting to fallback: ").append(
                        pop3BlockSize).append('\n');
                } else {
                    logBuilder.append("\tPOP3 Block Size: ").append(pop3BlockSize).append('\n');
                }
            } catch (NumberFormatException e) {
                pop3BlockSize = 100;
                logBuilder.append("\tPOP3 Block Size: Invalid value \"").append(tmp).append("\". Setting to fallback: ").append(
                    pop3BlockSize).append('\n');
            }
        }

        {
            final String tmp = configuration.getProperty("com.openexchange.pop3.pop3ConnectionIdleTime", "300000").trim();
            try {
                pop3ConnectionIdleTime = Integer.parseInt(tmp);
                logBuilder.append("\tPOP3 Connection Idle Time: ").append(pop3ConnectionIdleTime).append('\n');
            } catch (NumberFormatException e) {
                pop3ConnectionIdleTime = 300000;
                logBuilder.append("\tPOP3 Connection Idle Time: Invalid value \"").append(tmp).append("\". Setting to fallback: ").append(
                    pop3ConnectionIdleTime).append('\n');
            }
        }

        {
            final String pop3AuthEncStr = configuration.getProperty("com.openexchange.pop3.pop3AuthEnc", "UTF-8").trim();
            if (CharsetDetector.isValid(pop3AuthEncStr)) {
                pop3AuthEnc = pop3AuthEncStr;
                logBuilder.append("\tAuthentication Encoding: ").append(pop3AuthEnc).append('\n');
            } else {
                pop3AuthEnc = "UTF-8";
                logBuilder.append("\tAuthentication Encoding: Unsupported charset \"").append(pop3AuthEncStr).append(
                    "\". Setting to fallback: ").append(pop3AuthEnc).append('\n');
            }
        }
        spamHandlerName = configuration.getProperty("com.openexchange.pop3.spamHandler", SpamHandler.SPAM_HANDLER_FALLBACK).trim();
        logBuilder.append("\tSpam Handler: ").append(spamHandlerName).append('\n');

        sslProtocols = configuration.getProperty("com.openexchange.pop3.ssl.protocols", "SSLv3 TLSv1").trim();
        logBuilder.append("\tSupported SSL protocols: ").append(sslProtocols).append("\n");

        {
            final String tmp = configuration.getProperty("com.openexchange.pop3.ssl.ciphersuites", "").trim();
            this.cipherSuites = Strings.isEmpty(tmp) ? null : tmp;
            logBuilder.append("\tSupported SSL cipher suites: ").append(null == this.cipherSuites ? "<default>" : cipherSuites).append("\n");
        }

        logBuilder.append("Global POP3 properties successfully loaded!");
        LOG.info(logBuilder.toString());
    }

    @Override
    protected void resetFields() {
        pop3Timeout = 0;
        pop3ConnectionTimeout = 0;
        pop3ConnectionIdleTime = 0;
        pop3TemporaryDown = 0;
        pop3AuthEnc = null;
        spamHandlerName = null;
        pop3BlockSize = 100;
        sslProtocols = "SSLv3 TLSv1";
        cipherSuites = null;
    }

    /**
     * Gets the spam handler name.
     *
     * @return The spam handler name
     */
    public String getSpamHandlerName() {
        return spamHandlerName;
    }

    @Override
    public String getPOP3AuthEnc() {
        return pop3AuthEnc;
    }

    @Override
    public int getPOP3ConnectionIdleTime() {
        return pop3ConnectionIdleTime;
    }

    @Override
    public int getPOP3ConnectionTimeout() {
        return pop3ConnectionTimeout;
    }

    @Override
    public int getPOP3TemporaryDown() {
        return pop3TemporaryDown;
    }

    @Override
    public int getPOP3Timeout() {
        return pop3Timeout;
    }

    @Override
    public int getPOP3BlockSize() {
        return pop3BlockSize;
    }

    @Override
    public int getMailFetchLimit() {
        return mailProperties.getMailFetchLimit();
    }

    @Override
    public boolean hideInlineImages() {
        return mailProperties.hideInlineImages();
    }

    @Override
    public boolean isAllowNestedDefaultFolderOnAltNamespace() {
        return mailProperties.isAllowNestedDefaultFolderOnAltNamespace();
    }

    @Override
    public boolean isIgnoreSubscription() {
        return mailProperties.isIgnoreSubscription();
    }

    @Override
    public boolean isSupportSubscription() {
        return mailProperties.isSupportSubscription();
    }

    @Override
    public boolean isUserFlagsEnabled() {
        return mailProperties.isUserFlagsEnabled();
    }

    @Override
    public String getSSLProtocols() {
        return sslProtocols;
    }

    @Override
    public String getSSLCipherSuites() {
        return cipherSuites;
    }

}
