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

package com.openexchange.secret.osgi;

import static com.openexchange.osgi.Tools.withRanking;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import org.slf4j.Logger;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.crypto.CryptoService;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.secret.SecretEncryptionFactoryService;
import com.openexchange.secret.SecretService;
import com.openexchange.secret.SecretUsesPasswordChecker;
import com.openexchange.secret.impl.CryptoSecretEncryptionFactoryService;
import com.openexchange.secret.impl.DefaultSecretUsesPasswordChecker;
import com.openexchange.secret.impl.LiteralToken;
import com.openexchange.secret.impl.ReservedToken;
import com.openexchange.secret.impl.Token;
import com.openexchange.secret.impl.TokenBasedSecretService;
import com.openexchange.secret.impl.TokenList;
import com.openexchange.secret.impl.TokenRow;
import com.openexchange.secret.osgi.tools.WhiteboardSecretService;

/**
 * {@link SecretActivator}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class SecretActivator extends HousekeepingActivator implements Reloadable {

    private WhiteboardSecretService whiteboardSecretService;

    /**
     * Initializes a new {@link SecretActivator}.
     */
    public SecretActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { ConfigurationService.class, CryptoService.class };
    }

    @Override
    protected synchronized void startBundle() throws Exception {
        final ConfigurationService configurationService = getService(ConfigurationService.class);
        reinit(configurationService, false);
    }

    @Override
    protected synchronized void stopBundle() throws Exception {
        TokenBasedSecretService.RANDOM.set("unknown");
        final WhiteboardSecretService whiteboardSecretService = this.whiteboardSecretService;
        if (null != whiteboardSecretService) {
            this.whiteboardSecretService = null;
            whiteboardSecretService.close();
        }
        super.stopBundle();
    }

    @Override
    public synchronized void reloadConfiguration(final ConfigurationService configurationService) {
        reinit(configurationService, true);
    }

    private void reinit(final ConfigurationService configurationService, final boolean performShutDown) {
        final Logger logger = org.slf4j.LoggerFactory.getLogger(SecretActivator.class);
        if (performShutDown) {
            try {
                stopBundle();
            } catch (Exception e) {
                logger.warn("Secret module could not be shut down.", e);
            }
        }
        /*
         * Initialize plain SessionSecretService
         */
        final TokenList tokenList;
        {
            TokenBasedSecretService.RANDOM.set(configurationService.getProperty("com.openexchange.secret.secretRandom", "unknown"));
            /*
             * Get pattern from configuration
             */
            String pattern = configurationService.getProperty("com.openexchange.secret.secretSource", "\"<password>\"");
            if (pattern.charAt(0) == '"') {
                pattern = pattern.substring(1);
            }
            if (pattern.charAt(pattern.length() - 1) == '"') {
                pattern = pattern.substring(0, pattern.length() - 1);
            }
            /*
             * Check for "list"
             */
            final TokenBasedSecretService tokenBasedSecretService;
            if ("<list>".equals(pattern)) {
                String text = configurationService.getText("secrets");
                if (null == text) {
                    text = "\"<user-id> + '-' +  <random> + '-' + <context-id>\"";
                }
                tokenList = TokenList.parseText(text);
                // SecretService based on last token in token list
                tokenBasedSecretService = new TokenBasedSecretService(tokenList);
            } else {
                final String[] tokens = pattern.split(" *\\+ *");
                final List<Token> tl = new ArrayList<Token>(tokens.length);
                for (String token : tokens) {
                    token = token.trim();
                    final boolean isReservedToken = ('<' == token.charAt(0));
                    if (isReservedToken || ('\'' == token.charAt(0))) {
                        token = token.substring(1);
                        token = token.substring(0, token.length() - 1);
                    }
                    final ReservedToken rt = ReservedToken.reservedTokenFor(token);
                    if (null == rt) {
                        if (isReservedToken) {
                            throw new IllegalStateException("Unknown reserved token: " + token);
                        }
                        tl.add(new LiteralToken(token));
                    } else {
                        tl.add(rt);
                    }
                }
                tokenBasedSecretService = new TokenBasedSecretService(new TokenRow(tl));
                tokenList = TokenList.newInstance(Collections.singleton(tl));
            }
            // Checks if SecretService is configured to use a password
            final boolean usesPassword = tokenList.isUsesPassword();
            registerService(SecretUsesPasswordChecker.class, new DefaultSecretUsesPasswordChecker(tokenBasedSecretService, usesPassword));

            Dictionary<String, Object> properties = withRanking(tokenBasedSecretService.getRanking());
            registerService(SecretService.class, tokenBasedSecretService, properties);
        }
        /*
         * Create & open whiteboard service
         */
        final WhiteboardSecretService whiteboardSecretService = new WhiteboardSecretService(context);
        this.whiteboardSecretService = whiteboardSecretService;
        whiteboardSecretService.open();
        /*
         * Register CryptoSecretEncryptionFactoryService
         */
        final CryptoService crypto = getService(CryptoService.class);
        final CryptoSecretEncryptionFactoryService service = new CryptoSecretEncryptionFactoryService(crypto, whiteboardSecretService, tokenList);
        registerService(SecretEncryptionFactoryService.class, service);
        /*
         * Register Reloadable (again)
         */
        registerService(Reloadable.class, this);

        logger.info("(Re-)Initialized 'com.openexchange.secret' bundle.");
    }

    @Override
    public Interests getInterests() {
        return Reloadables.interestsForFiles("secret.properties");
    }

}
