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

package com.openexchange.mail.autoconfig.tools;

import static com.openexchange.java.Autoboxing.I;
import java.net.URI;
import java.net.URISyntaxException;
import com.openexchange.config.cascade.ComposedConfigProperty;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.mailaccount.MailAccountStorageService;
import com.openexchange.mailaccount.MailAccounts;

/**
 * {@link Utils}
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.4
 */
public class Utils {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Utils.class);
    }

    public static final String PROPERTY_ISPDB_PROXY = "com.openexchange.mail.autoconfig.http.proxy";
    public static final String PROPERTY_ISPDB_PROXY_LOGIN = "com.openexchange.mail.autoconfig.http.proxy.login";
    public static final String PROPERTY_ISPDB_PROXY_PASSWORD = "com.openexchange.mail.autoconfig.http.proxy.password";

    public static final String OX_CONTEXT_ID = "OX-Context-Object";
    public static final String OX_USER_ID = "OX-User-Object";
    public static final String OX_TARGET_ID = "OX-Target-Object";

    /**
     * Initializes a new {@link Utils}.
     */
    private Utils() {
        super();
    }

    public static ProxyInfo getHttpProxyIfEnabled(ConfigView view) throws OXException {
        ComposedConfigProperty<String> property = view.property(PROPERTY_ISPDB_PROXY, String.class);
        if (!property.isDefined()) {
            return null;
        }

        // Get & check proxy setting
        String proxy = property.get();
        if (false != Strings.isEmpty(proxy)) {
            return null;
        }

        // Parse & apply proxy settings
        try {
            URI proxyUrl;
            {
                String sProxyUrl = Strings.asciiLowerCase(proxy.trim());
                if (sProxyUrl.startsWith("://")) {
                    sProxyUrl = new StringBuilder(sProxyUrl.length() + 4).append("http").append(sProxyUrl).toString();
                } else if (false == sProxyUrl.startsWith("http://") && false == sProxyUrl.startsWith("https://")) {
                    sProxyUrl = new StringBuilder(sProxyUrl.length() + 7).append("http://").append(sProxyUrl).toString();
                }
                proxyUrl = new URI(sProxyUrl);
            }

            String proxyLogin = null;
            String proxyPassword = null;

            ComposedConfigProperty<String> propLogin = view.property(PROPERTY_ISPDB_PROXY_LOGIN, String.class);
            if (propLogin.isDefined()) {
                ComposedConfigProperty<String> propPassword = view.property(PROPERTY_ISPDB_PROXY_PASSWORD, String.class);
                if (propPassword.isDefined()) {
                    proxyLogin = propLogin.get();
                    proxyPassword = propPassword.get();
                    if (Strings.isNotEmpty(proxyLogin) && Strings.isNotEmpty(proxyPassword)) {
                        proxyLogin = proxyLogin.trim();
                        proxyPassword = proxyPassword.trim();
                    }
                }
            }

            return new ProxyInfo(proxyUrl, proxyLogin, proxyPassword);
        } catch (URISyntaxException e) {
            LoggerHolder.LOGGER.warn("Unable to parse proxy URL: {}", proxy, e);
            return null;
        } catch (NumberFormatException e) {
            LoggerHolder.LOGGER.warn("Invalid proxy setting: {}", proxy, e);
            return null;
        } catch (RuntimeException e) {
            LoggerHolder.LOGGER.warn("Could not apply proxy: {}", proxy, e);
            return null;
        }
    }

    /**
     * Checks if given host/port denote the primary IMAP account of specified user.
     *
     * @param host The host
     * @param port The port
     * @param userId The user identifier
     * @param contextId The context identifier
     * @return <code>true</code> if given host/port denote the primary IMAP account; otherwise <code>false</code>
     */
    public static boolean isPrimaryImapAccount(String host, int port, int userId, int contextId) {
        try {
            MailAccountStorageService storageService = Services.optService(MailAccountStorageService.class);
            if (storageService == null) {
                return false;
            }

            MailAccount defaultMailAccount = storageService.getDefaultMailAccount(userId, contextId);
            return MailAccounts.isEqualImapAccount(defaultMailAccount, host, port);
        } catch (Exception e) {
           LoggerHolder.LOGGER.warn("Failed to check for primary IMAP account of user {} in context {}", I(userId), I(contextId), e);
           return false;
        }
    }

}
