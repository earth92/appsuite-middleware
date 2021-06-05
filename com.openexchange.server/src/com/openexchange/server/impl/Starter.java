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

package com.openexchange.server.impl;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Stack;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.server.Initialization;
import com.openexchange.server.services.ServerServiceRegistry;
import com.openexchange.tools.exceptions.ExceptionUtils;
import com.openexchange.version.VersionService;

/**
 * {@link Starter} - Starter for <a href="www.open-xchange.com">Open-Xchange</a> server.
 * <p>
 * All necessary initializations for a proper system start-up take place.
 *
 * @author <a href="mailto:martin.kauss@open-xchange.org">Martin Kauss</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class Starter implements Initialization {

    /**
     * This contains the components to be started if a normal groupware startup is done.
     */
    private final Initialization[] inits = new Initialization[] {
        /**
         * Reads system.properties.
         */
        com.openexchange.configuration.SystemConfig.getInstance(),
        /**
         * Cache availability registry start-up
         */
        com.openexchange.cache.registry.CacheAvailabilityRegistryInit.getInstance(),
        /**
         * Initialization for custom charset provider
         */
        new com.openexchange.charset.CustomCharsetProviderInit(),
        /**
         * Setup of ContextStorage and LoginInfo.
         */
        com.openexchange.groupware.contexts.impl.ContextInit.getInstance(),
        /**
         * Folder initialization
         */
        com.openexchange.tools.oxfolder.OXFolderProperties.getInstance(), new com.openexchange.folder.internal.FolderInitialization(),
        /**
         * Mail initialization
         */
        com.openexchange.mail.MailInitialization.getInstance(),
        /**
         * Transport initialization
         */
        com.openexchange.mail.transport.TransportInitialization.getInstance(),
        /**
         * Infostore Configuration
         */
        com.openexchange.groupware.infostore.InfostoreConfig.getInstance(),
        /**
         * Attachment Configuration
         */
        com.openexchange.groupware.attach.AttachmentConfig.getInstance(),
        /**
         * User configuration init
         */
        com.openexchange.groupware.userconfiguration.UserConfigurationStorageInit.getInstance(),
        /**
         * Notification Configuration
         */
        com.openexchange.groupware.notify.NotificationConfig.getInstance(),
        /**
         * Sets up the configuration tree.
         */
        com.openexchange.groupware.settings.impl.ConfigTreeInit.getInstance(),
        /**
         * Responsible for starting and stopping the EventQueue
         */
        new com.openexchange.event.impl.EventInit(),
        /**
         * Responsible for registering all instances for deleting users and groups.
         */
        new com.openexchange.groupware.delete.DeleteRegistryInitialization(),
        /**
         * Responsible for registering all instances for evicting caches of users and groups.
         */
        new com.openexchange.groupware.delete.DeleteFinishedRegistryInitialization(),
        /**
         * Downgrade registry start-up
         */
        com.openexchange.groupware.downgrade.DowngradeRegistryInit.getInstance(),
        /**
         * Further inits
         */
        new com.openexchange.mailaccount.internal.MailAccountStorageInit(), new com.openexchange.multiple.internal.MultipleHandlerInit(), new com.openexchange.groupware.impl.id.IDGeneratorInit() };

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Starter.class);

    private final Stack<Initialization> started;

    /**
     * Default constructor.
     */
    public Starter() {
        super();
        started = new Stack<Initialization>();
    }

    @Override
    public void start() throws OXException {

        dumpServerInfos();

        for (final Initialization init : inits) {
            try {
                init.start();
                started.push(init);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                LOG.error("initialization of {} failed", init.getClass().getName(), t);
            }
        }

        if (started.size() == inits.length) {
            LOG.info("Groupware server successfully initialized.");
        } else {
            LOG.info("Groupware server initialized with errors.");
        }
    }

    /**
     * Dump server information.
     * @throws OXException 
     */
    private static final void dumpServerInfos() throws OXException {
        StringBuilder message = new StringBuilder(64);
        List<Object> args = new ArrayList<>();
        String sep = Strings.getLineSeparator();

        try {
            Properties p = System.getProperties();

            message.append("Server info{}");
            args.add(sep);

            message.append("Operating system : {} {} {}{}");
            args.add(p.getProperty("os.name"));
            args.add(p.getProperty("os.arch"));
            args.add(p.getProperty("os.version"));
            args.add(sep);

            message.append("Java             : {}{}");
            args.add(p.getProperty("java.runtime.version"));
            args.add(sep);

            message.append("VM Total Memory  : {} KB{}");
            long totalMemory = Runtime.getRuntime().totalMemory() >> 10;
            args.add(NumberFormat.getNumberInstance().format(totalMemory));
            args.add(sep);

            message.append("VM Free Memory   : {} KB{}");
            long freeMemory = Runtime.getRuntime().freeMemory() >> 10;
            args.add(NumberFormat.getNumberInstance().format(freeMemory));
            args.add(sep);

            message.append("VM Used Memory   : {} KB{}");
            long usedMemory = totalMemory - freeMemory;
            args.add(NumberFormat.getNumberInstance().format(usedMemory));
            args.add(sep);
        } catch (Exception gee) {
            LOG.error("", gee);
        }

        message.append("System version   : {} Server [{}] initializing ...{}");
        args.add(VersionService.NAME);
        args.add(ServerServiceRegistry.getServize(VersionService.class, true).getVersionString());
        args.add(sep);

        message.append("Server Footprint : {}{}");
        args.add(OXException.getServerId());
        args.add(sep);

        LOG.info(message.toString(), args.toArray(new Object[args.size()]));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        while (!started.isEmpty()) {
            try {
                started.pop().stop();
            } catch (OXException e) {
                LOG.error("Component shutdown failed.", e);
            }
        }
    }
}
