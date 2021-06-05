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

package com.openexchange.groupware.infostore.osgi;

import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.CreateTableService;
import com.openexchange.database.provider.DBPoolProvider;
import com.openexchange.file.storage.FileStorageEventConstants;
import com.openexchange.file.storage.registry.FileStorageServiceRegistry;
import com.openexchange.filestore.QuotaFileStorageService;
import com.openexchange.groupware.filestore.FileLocationHandler;
import com.openexchange.groupware.impl.FolderLockManagerImpl;
import com.openexchange.groupware.infostore.InfostoreAvailable;
import com.openexchange.groupware.infostore.InfostoreFacades;
import com.openexchange.groupware.infostore.database.InfostoreFilestoreLocationUpdater;
import com.openexchange.groupware.infostore.database.impl.InfostoreDocumentAddFulltextIndexUpdateTask;
import com.openexchange.groupware.infostore.database.impl.InfostoreFilenameReservationsCreateTableTask;
import com.openexchange.groupware.infostore.database.impl.InfostoreReservedPathsConvertUtf8ToUtf8mb4UpdateTask;
import com.openexchange.groupware.infostore.facade.impl.InfostoreFacadeImpl;
import com.openexchange.groupware.infostore.rmi.FileChecksumsRMIServiceImpl;
import com.openexchange.groupware.infostore.webdav.EntityLockManagerImpl;
import com.openexchange.groupware.infostore.webdav.LockCleaner;
import com.openexchange.groupware.infostore.webdav.PropertyCleaner;
import com.openexchange.groupware.infostore.webdav.PropertyStoreImpl;
import com.openexchange.groupware.settings.tree.modules.infostore.autodelete.AutodeleteEditable;
import com.openexchange.groupware.settings.tree.modules.infostore.autodelete.MaxVersionCount;
import com.openexchange.groupware.settings.tree.modules.infostore.autodelete.RetentionDays;
import com.openexchange.groupware.update.DefaultUpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskV2;
import com.openexchange.jslob.ConfigTreeEquivalent;
import com.openexchange.jslob.shared.SharedJSlobService;
import com.openexchange.server.services.SharedInfostoreJSlob;

/**
 * {@link InfostoreActivator}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class InfostoreActivator implements BundleActivator {
    /**
     * A flag that indicates whether InfoStore file storage bundle is available or not.
     *
     * @see InfostoreFacades#isInfoStoreAvailable()
     */
    public static final AtomicReference<InfostoreAvailable> INFOSTORE_FILE_STORAGE_AVAILABLE = new AtomicReference<InfostoreAvailable>();

    private Queue<ServiceRegistration<?>> registrations;
    private ServiceTracker<FileStorageServiceRegistry, FileStorageServiceRegistry> tracker;
    private ServiceTracker<ConfigurationService, ConfigurationService> configTracker;
    private ServiceTracker<QuotaFileStorageService, QuotaFileStorageService> qfsTracker;
    private ServiceTracker<ConfigurationService, ConfigurationService> fulltextIndexUpdateTaskTracker;
    private List<ServiceRegistration<ConfigTreeEquivalent>> registeredSettings;

    @Override
    public synchronized void start(final BundleContext context) throws Exception {
        try {

            LockCleaner lockCleaner = new LockCleaner(new FolderLockManagerImpl(new DBPoolProvider()), new EntityLockManagerImpl(new DBPoolProvider(), "infostore_lock"));
            PropertyCleaner propertyCleaner = new PropertyCleaner(new PropertyStoreImpl(new DBPoolProvider(), "oxfolder_property"), new PropertyStoreImpl(new DBPoolProvider(), "infostore_property"));
            Dictionary<String, Object> serviceProperties = new Hashtable<String, Object>(1);
            serviceProperties.put(EventConstants.EVENT_TOPIC, FileStorageEventConstants.ALL_TOPICS);
            /*
             * Service registrations
             */
            Queue<ServiceRegistration<?>> registrations = new LinkedList<ServiceRegistration<?>>();
            //            registrations.offer(context.registerService(CreateTableService.class.getName(), task, null));
            //            registrations.offer(
            registrations.offer(context.registerService(EventHandler.class, lockCleaner, serviceProperties));
            registrations.offer(context.registerService(EventHandler.class, propertyCleaner, serviceProperties));

            /*
             * Register infostore filestore location updater for move context filestore
             */
            registrations.offer(context.registerService(FileLocationHandler.class, new InfostoreFilestoreLocationUpdater(), null));

            this.registrations = registrations;
            /*
             * Service trackers
             */
            class AvailableTracker extends ServiceTracker<FileStorageServiceRegistry, FileStorageServiceRegistry> {

                AvailableTracker(final BundleContext context) {
                    super(context, FileStorageServiceRegistry.class, null);
                }

                @Override
                public FileStorageServiceRegistry addingService(final ServiceReference<FileStorageServiceRegistry> reference) {
                    final FileStorageServiceRegistry registry = super.addingService(reference);
                    INFOSTORE_FILE_STORAGE_AVAILABLE.set(new InfostoreAvailable() {

                        @Override
                        public boolean available() {
                            return registry.containsFileStorageService("com.openexchange.infostore");
                        }
                    });
                    return registry;
                }

                @Override
                public void removedService(final ServiceReference<FileStorageServiceRegistry> reference, final FileStorageServiceRegistry service) {
                    INFOSTORE_FILE_STORAGE_AVAILABLE.set(null);
                    super.removedService(reference, service);
                }
            }
            AvailableTracker tracker = new AvailableTracker(context);
            tracker.open();
            this.tracker = tracker;

            final InfostoreFilenameReservationsCreateTableTask task = new InfostoreFilenameReservationsCreateTableTask();
            context.registerService(CreateTableService.class, task, null);
            context.registerService(UpdateTaskProviderService.class.getName(), (UpdateTaskProviderService) () -> Arrays.asList(((UpdateTaskV2) task), new InfostoreReservedPathsConvertUtf8ToUtf8mb4UpdateTask()), null);

            ServiceTracker<ConfigurationService, ConfigurationService> configTracker = new ServiceTracker<ConfigurationService, ConfigurationService>(context, ConfigurationService.class, new ServiceTrackerCustomizer<ConfigurationService, ConfigurationService>() {

                /*
                 * Register quotas as SharedJSLob
                 */
                ServiceRegistration<SharedJSlobService> registration;

                @Override
                public ConfigurationService addingService(ServiceReference<ConfigurationService> arg0) {

                    ConfigurationService configService = context.getService(arg0);
                    SharedJSlobService infostoreJSlob = new SharedInfostoreJSlob();
                    registration = context.registerService(SharedJSlobService.class, infostoreJSlob, null);
                    return configService;
                }

                @Override
                public void modifiedService(ServiceReference<ConfigurationService> arg0, ConfigurationService arg1) {
                    //nothing to do
                }

                @Override
                public void removedService(ServiceReference<ConfigurationService> arg0, ConfigurationService arg1) {
                    registration.unregister();
                    context.ungetService(arg0);
                }
            });
            this.configTracker = configTracker;
            configTracker.open();

            ServiceTracker<QuotaFileStorageService, QuotaFileStorageService> qfsTracker = new ServiceTracker<QuotaFileStorageService, QuotaFileStorageService>(context, QuotaFileStorageService.class, null) {

                @Override
                public QuotaFileStorageService addingService(ServiceReference<QuotaFileStorageService> reference) {
                    QuotaFileStorageService service = super.addingService(reference);
                    InfostoreFacadeImpl.setQuotaFileStorageService(service);
                    return service;
                }

                @Override
                public void removedService(ServiceReference<QuotaFileStorageService> reference, QuotaFileStorageService service) {
                    InfostoreFacadeImpl.setQuotaFileStorageService(null);
                    super.removedService(reference, service);
                }
            };
            this.qfsTracker = qfsTracker;
            qfsTracker.open();

            ServiceTracker<ConfigurationService, ConfigurationService> fulltextIndexUpdateTaskTracker = new ServiceTracker<ConfigurationService, ConfigurationService>(context, ConfigurationService.class, null) {

                @Override
                public ConfigurationService addingService(ServiceReference<ConfigurationService> reference) {
                    ConfigurationService service = context.getService(reference);
                    boolean fulltextSearch = service.getBoolProperty("com.openexchange.infostore.fulltextSearch", false);
                    if (fulltextSearch) {
                        context.registerService(UpdateTaskProviderService.class, new DefaultUpdateTaskProviderService(new InfostoreDocumentAddFulltextIndexUpdateTask()), null);
                    }
                    return service;
                }

            };
            this.fulltextIndexUpdateTaskTracker = fulltextIndexUpdateTaskTracker;
            fulltextIndexUpdateTaskTracker.open();

            serviceProperties = new Hashtable<String, Object>(1);
            serviceProperties.put("RMI_NAME", FileChecksumsRMIServiceImpl.RMI_NAME);
            context.registerService(Remote.class, new FileChecksumsRMIServiceImpl(), serviceProperties);

            // Register settings
            List<ServiceRegistration<ConfigTreeEquivalent>> registeredSettings = new ArrayList<ServiceRegistration<ConfigTreeEquivalent>>(4);
            AutodeleteEditable autodeleteEditable = new AutodeleteEditable(); // --> Statically registered via ConfigTree class
            registeredSettings.add(context.registerService(ConfigTreeEquivalent.class, autodeleteEditable, null));

            MaxVersionCount maxVersionCount = new MaxVersionCount(); // --> Statically registered via ConfigTree class
            registeredSettings.add(context.registerService(ConfigTreeEquivalent.class, maxVersionCount, null));

            RetentionDays retentionDays = new RetentionDays(); // --> Statically registered via ConfigTree class
            registeredSettings.add(context.registerService(ConfigTreeEquivalent.class, retentionDays, null));

            this.registeredSettings = registeredSettings;
        } catch (Exception e) {
            final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InfostoreActivator.class);
            logger.error("Starting InfostoreActivator failed.", e);
            throw e;
        }
    }

    @Override
    public synchronized void stop(final BundleContext context) throws Exception {
        try {
            List<ServiceRegistration<ConfigTreeEquivalent>> registeredSettings = this.registeredSettings;
            if (null != registeredSettings) {
                this.registeredSettings = null;
                for (ServiceRegistration<ConfigTreeEquivalent> serviceRegistration : registeredSettings) {
                    serviceRegistration.unregister();
                }
            }

            ServiceTracker<FileStorageServiceRegistry, FileStorageServiceRegistry> tracker = this.tracker;
            if (null != tracker) {
                tracker.close();
                this.tracker = null;
            }

            ServiceTracker<QuotaFileStorageService, QuotaFileStorageService> qfsTracker = this.qfsTracker;
            if (null != qfsTracker) {
                qfsTracker.close();
                this.qfsTracker = null;
            }

            ServiceTracker<ConfigurationService, ConfigurationService> configTracker = this.configTracker;
            if (null != configTracker) {
                configTracker.close();
                this.configTracker = null;
            }

            ServiceTracker<ConfigurationService, ConfigurationService> fulltextIndexUpdateTaskTracker = this.fulltextIndexUpdateTaskTracker;
            if (null != fulltextIndexUpdateTaskTracker) {
                fulltextIndexUpdateTaskTracker.close();
                this.fulltextIndexUpdateTaskTracker = null;
            }

            Queue<ServiceRegistration<?>> registrations = this.registrations;
            if (null != registrations) {
                ServiceRegistration<?> polled;
                while ((polled = registrations.poll()) != null) {
                    polled.unregister();
                }
                this.registrations = null;
            }
        } catch (Exception e) {
            final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InfostoreActivator.class);
            logger.error("Stopping InfostoreActivator failed.", e);
            throw e;
        }
    }

}
