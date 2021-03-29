/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.database.tombstone.cleanup.osgi;

import org.slf4j.Logger;
import com.openexchange.config.ConfigTools;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.context.ContextService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.cleanup.CleanUpInfo;
import com.openexchange.database.cleanup.CleanUpJob;
import com.openexchange.database.cleanup.DatabaseCleanUpService;
import com.openexchange.database.tombstone.cleanup.SchemaTombstoneCleaner;
import com.openexchange.database.tombstone.cleanup.config.TombstoneCleanupConfig;
import com.openexchange.database.tombstone.cleanup.update.InitialTombstoneCleanupUpdateTask;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.update.DefaultUpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskProviderService;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.Tools;

/**
 *
 * {@link TombstoneCleanupActivator}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since v7.10.2
 */
public class TombstoneCleanupActivator extends HousekeepingActivator implements Reloadable {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TombstoneCleanupActivator.class);

    private CleanUpInfo cleanupTask;

    /**
     * Initializes a new {@link TombstoneCleanupActivator}.
     */
    public TombstoneCleanupActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { LeanConfigurationService.class, DatabaseService.class, ContextService.class, DatabaseCleanUpService.class };
    }

    @Override
    protected boolean stopOnServiceUnavailability() {
        return true;
    }

    @Override
    protected synchronized void startBundle() throws Exception {
        LOGGER.info("Starting bundle: {}", context.getBundle().getSymbolicName());
        Services.setServiceLookup(this);

        LeanConfigurationService leanConfig = Tools.requireService(LeanConfigurationService.class, this);
        registerService(Reloadable.class, this);

        initCleanupTask(leanConfig, true);
    }

    private void initCleanupTask(LeanConfigurationService leanConfig, boolean registerUpdateTask) throws OXException {
        String timespanStr = leanConfig.getProperty(TombstoneCleanupConfig.TIMESPAN).trim();
        long timespan = ConfigTools.parseTimespan(timespanStr);
        if (timespan < 1) {
            LOGGER.warn("Cleanup enabled but no meaningful value defined: \"{}\" Will use the default of 12 weeks (~3 months).", timespanStr);
            timespan = ConfigTools.parseTimespan(TombstoneCleanupConfig.TIMESPAN.getDefaultValue(String.class));
        }
        if (!leanConfig.getBooleanProperty(TombstoneCleanupConfig.ENABLED)) {
            LOGGER.info("Skipped starting database cleanup task based on configuration.");
            return;
        }

        DatabaseCleanUpService cleanUpService = Tools.requireService(DatabaseCleanUpService.class, this);
        CleanUpJob cleanUpJob = SchemaTombstoneCleaner.getCleanUpJob(timespan);
        this.cleanupTask = cleanUpService.scheduleCleanUpJob(cleanUpJob);

        if (registerUpdateTask) {
            registerService(UpdateTaskProviderService.class, new DefaultUpdateTaskProviderService(new InitialTombstoneCleanupUpdateTask(timespan)));
        }
    }

    @Override
    protected synchronized void stopBundle() throws Exception {
        CleanUpInfo cleanupTask = this.cleanupTask;
        if (cleanupTask != null) {
            this.cleanupTask = null;
            cleanupTask.cancel(true);
        }
        Services.setServiceLookup(null);

        super.stopBundle();
        LOGGER.info("Successfully stopped bundle {}", this.context.getBundle().getSymbolicName());
    }

    @Override
    public synchronized void reloadConfiguration(ConfigurationService configService) {
        LeanConfigurationService leanConfig;
        try {
            leanConfig = Tools.requireService(LeanConfigurationService.class, this);

            if (this.cleanupTask != null) {
                cleanupTask.cancel(true);
            }

            initCleanupTask(leanConfig, false);
        } catch (Exception e) {
            LOGGER.error("Encountered an error while restarting database cleanup task", e);
        }
    }

    @Override
    public Interests getInterests() {
        return DefaultInterests.builder().propertiesOfInterest(TombstoneCleanupConfig.TIMESPAN.getFQPropertyName()).build();
    }
}
