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

package com.openexchange.pop3.connect;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.pop3.util.POP3StorageUtil.parseLoginDelaySeconds;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import javax.mail.internet.idn.IDNA;
import com.openexchange.exception.OXException;
import com.openexchange.mail.api.IMailFolderStorage;
import com.openexchange.pop3.POP3Access;
import com.openexchange.pop3.config.POP3Config;
import com.openexchange.pop3.storage.AlreadyLockedException;
import com.openexchange.pop3.storage.POP3Storage;
import com.openexchange.pop3.storage.POP3StorageProperties;
import com.openexchange.pop3.storage.POP3StoragePropertyNames;
import com.openexchange.pop3.util.POP3CapabilityCache;
import com.openexchange.session.Session;

/**
 * {@link POP3SyncMessagesCallable} - {@link Callable} to connect to POP3 account and synchronize its messages with POP3 storage.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class POP3SyncMessagesCallable implements Callable<Object> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(POP3SyncMessagesCallable.class);

    private final POP3Access pop3Access;

    private final POP3Storage pop3Storage;

    private final POP3StorageProperties pop3StorageProperties;

    private final IMailFolderStorage folderStorage;

    /**
     * Initializes a new {@link POP3SyncMessagesCallable}.
     *
     * @param pop3Access The POP3 access
     * @param pop3Storage The POP3 storage
     * @param pop3StorageProperties The POP3 storage properties
     * @param folderStorage The POP3 storage's folder storage instance
     * @param server Either the host name or textual representation of the IP address of the POP3 server
     */
    public POP3SyncMessagesCallable(final POP3Access pop3Access, final POP3Storage pop3Storage, final POP3StorageProperties pop3StorageProperties, final IMailFolderStorage folderStorage) {
        super();
        this.pop3Access = pop3Access;
        this.pop3Storage = pop3Storage;
        this.pop3StorageProperties = pop3StorageProperties;
        this.folderStorage = folderStorage;
    }

    @Override
    public Object call() throws Exception {
        /*
         * Is it allowed to connect to real POP3 account to synchronize messages?
         */
        final long refreshRate = getRefreshRateMillis();
        final Long lastAccessed = getLastAccessed();
        if (!isConnectable(refreshRate, lastAccessed)) {
            // Refresh not yet possible
            return null;
        }
        /*-
         * Refresh possible according to configured refresh rate
         *
         * Check refresh rate setting
         */
        final String server;
        {
            final POP3Config pop3Config = pop3Access.getPOP3Config();
            server = pop3Config.getServer();
            final int port = pop3Config.getPort();
            String capabilities;
            try {
                capabilities =
                    POP3CapabilityCache.getCapability(
                        InetAddress.getByName(IDNA.toASCII(server)),
                        port,
                        pop3Config.isSecure(),
                        pop3Config.getPOP3Properties(),
                        pop3Config.getLogin());
            } catch (Exception e) {
                final Session ses = pop3Access.getSession();
                final StringBuilder sb = new StringBuilder("Couldn't detect capabilities from POP3 server \"");
                sb.append(server).append("\" with login \"");
                sb.append(pop3Config.getLogin()).append("\" (user=");
                sb.append(ses.getUserId()).append(", context=");
                sb.append(ses.getContextId()).append("):\n");
                sb.append(e.getMessage());
                LOG.warn(sb.toString(), e);
                capabilities = POP3CapabilityCache.getDeaultCapabilities();
            }
            /*
             * Check refresh rate against minimum allowed seconds between logins provided that "LOGIN-DELAY" is contained in
             * capabilities
             */
            final int min = parseLoginDelaySeconds(capabilities);
            if (min >= 0 && (min * 1000l) > refreshRate) {
                LOG.warn("Refresh rate of {}sec is lower than minimum allowed seconds between logins ({}sec)", L(refreshRate / 1000), I(min));
            }
        }
        LOG.debug("\n\tSynchronizing messages with POP3 account: {}", server, new Throwable());
        /*
         * Check default folders since INBOX folder must be present prior to appending to it
         */
        folderStorage.checkDefaultFolders();
        /*-
         * Sync messages
         *
         * Access POP3 account and synchronize
         */
        try {
            pop3Storage.syncMessages(isExpungeOnQuit(), lastAccessed);
            /*
             * Update last-accessed time stamp
             */
            final long stamp = System.currentTimeMillis();
            pop3StorageProperties.addProperty(POP3StoragePropertyNames.PROPERTY_LAST_ACCESSED, Long.toString(stamp));
        } catch (AlreadyLockedException e) {
            LOG.debug("\n\tPOP3 account {} locked.", server, e);
        }
        return null;
    }

    private boolean isConnectable(final long refreshRateMillis, final Long lastAccessed) {
        return ((null == lastAccessed) || ((System.currentTimeMillis() - lastAccessed.longValue()) >= refreshRateMillis));
    }

    private Long getLastAccessed() throws OXException {
        final String lastAccessedStr = pop3StorageProperties.getProperty(POP3StoragePropertyNames.PROPERTY_LAST_ACCESSED);
        if (null == lastAccessedStr) {
            return null;
        }
        try {
            return Long.valueOf(lastAccessedStr);
        } catch (NumberFormatException e) {
            LOG.warn("", e);
            return null;
        }
    }

    private static final int FALLBACK_MINUTES = 10;

    private long getRefreshRateMillis() throws OXException {
        final String frequencyStr = pop3StorageProperties.getProperty(POP3StoragePropertyNames.PROPERTY_REFRESH_RATE);
        if (null == frequencyStr) {
            // Fallback to 10 minutes
            // LOG.warn("Missing POP3 property \"{}\". Using fallback of {} minutes.", POP3StoragePropertyNames.PROPERTY_REFRESH_RATE, FALLBACK_MINUTES, new Throwable());
            return FALLBACK_MINUTES * 60L * 1000L;
        }
        int minutes = 0;
        try {
            minutes = Integer.parseInt(frequencyStr);
        } catch (NumberFormatException e) {
            LOG.warn("POP3 property \"{}\" is not a number: ``{}''. Using fallback of {} minutes.", POP3StoragePropertyNames.PROPERTY_REFRESH_RATE, frequencyStr, I(FALLBACK_MINUTES),
                e);
            minutes = FALLBACK_MINUTES;
        }
        return minutes * 60L * 1000L;
    }

    private boolean isExpungeOnQuit() throws OXException {
        final String expungeStr = pop3StorageProperties.getProperty(POP3StoragePropertyNames.PROPERTY_EXPUNGE);
        return (null == expungeStr) ? false : Boolean.parseBoolean(expungeStr);
    }

}
