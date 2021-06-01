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

package com.openexchange.push.impl.credstorage.osgi;

import static com.openexchange.osgi.Tools.withRanking;
import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.openexchange.config.ConfigurationService;
import com.openexchange.crypto.CryptoService;
import com.openexchange.database.CreateTableService;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.delete.DeleteListener;
import com.openexchange.groupware.update.DefaultUpdateTaskProviderService;
import com.openexchange.groupware.update.UpdateTaskProviderService;
import com.openexchange.hazelcast.configuration.HazelcastConfigurationService;
import com.openexchange.hazelcast.serialization.CustomPortableFactory;
import com.openexchange.java.Strings;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.push.credstorage.CredentialStorage;
import com.openexchange.push.credstorage.CredentialStorageProvider;
import com.openexchange.push.impl.credstorage.OSGiCredentialStorageProvider;
import com.openexchange.push.impl.credstorage.Obfuscator;
import com.openexchange.push.impl.credstorage.inmemory.HazelcastCredentialStorage;
import com.openexchange.push.impl.credstorage.inmemory.portable.PortableCredentialsFactory;
import com.openexchange.push.impl.credstorage.rdb.RdbCredentialStorage;
import com.openexchange.push.impl.credstorage.rdb.groupware.CreateCredStorageTable;
import com.openexchange.push.impl.credstorage.rdb.groupware.CredConvertUtf8ToUtf8mb4Task;
import com.openexchange.push.impl.credstorage.rdb.groupware.CredStorageCreateTableTask;
import com.openexchange.push.impl.credstorage.rdb.groupware.CredStorageDeleteListener;
import com.openexchange.push.impl.portable.HazelcastInstanceNotActiveExceptionHandler;
import com.openexchange.push.impl.portable.PortablePushUserFactory;


/**
 * {@link CredStorageActivator}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public class CredStorageActivator extends HousekeepingActivator implements HazelcastInstanceNotActiveExceptionHandler {

    /**
     * Initializes a new {@link CredStorageActivator}.
     */
    public CredStorageActivator() {
        super();
    }

    @Override
    public <S> boolean removeService(Class<? extends S> clazz) {
        return super.removeService(clazz);
    }

    @Override
    public <S> boolean addService(Class<S> clazz, S service) {
        return super.addService(clazz, service);
    }

    @Override
    public void propagateNotActive(HazelcastInstanceNotActiveException notActiveException) {
        BundleContext context = this.context;
        if (null != context) {
            context.registerService(HazelcastInstanceNotActiveException.class, notActiveException, null);
        }
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { ConfigurationService.class, HazelcastConfigurationService.class, CryptoService.class, DatabaseService.class };
    }

    @Override
    protected void startBundle() throws Exception {
        final Logger log = org.slf4j.LoggerFactory.getLogger(CredStorageActivator.class);
        CredStorageServices.setServiceLookup(this);
        final BundleContext context = this.context;
        final HazelcastConfigurationService hazelcastConfig = getService(HazelcastConfigurationService.class);

        ConfigurationService configService = getService(ConfigurationService.class);
        boolean credStoreEnabled = configService.getBoolProperty("com.openexchange.push.credstorage.enabled", false);

        final HazelcastCredentialStorage hzCredStorage;
        final RdbCredentialStorage rdbCredStorage;
        if (credStoreEnabled) {
            String key = configService.getProperty("com.openexchange.push.credstorage.passcrypt");
            if (Strings.isEmpty(key)) {
                throw new BundleException("Property \"com.openexchange.push.credstorage.enabled\" set to \"true\", but missing value for \"com.openexchange.push.credstorage.passcrypt\" property.");
            }

            // Register portables
            registerService(CustomPortableFactory.class, new PortablePushUserFactory());
            registerService(CustomPortableFactory.class, new PortableCredentialsFactory());

            Obfuscator obfuscator = new Obfuscator(key.trim());

            hzCredStorage = new HazelcastCredentialStorage(obfuscator, this, this);
            rdbCredStorage = configService.getBoolProperty("com.openexchange.push.credstorage.rdb", false) ? new RdbCredentialStorage(obfuscator) : null;
            // Check Hazelcast stuff
            if (hazelcastConfig.isEnabled()) {
                // Track HazelcastInstance service
                ServiceTrackerCustomizer<HazelcastInstance, HazelcastInstance> customizer = new ServiceTrackerCustomizer<HazelcastInstance, HazelcastInstance>() {

                    @Override
                    public HazelcastInstance addingService(ServiceReference<HazelcastInstance> reference) {
                        HazelcastInstance hzInstance = context.getService(reference);
                        try {
                            String mapName = hazelcastConfig.discoverMapName("credentials");
                            if (null == mapName) {
                                context.ungetService(reference);
                                return null;
                            }
                            addService(HazelcastInstance.class, hzInstance);
                            hzCredStorage.setHzMapName(mapName);
                            hzCredStorage.changeBackingMapToHz();
                            return hzInstance;
                        } catch (OXException e) {
                            log.warn("Couldn't initialize remote credentials map.", e);
                        } catch (RuntimeException e) {
                            log.warn("Couldn't initialize remote credentials map.", e);
                        }
                        context.ungetService(reference);
                        return null;
                    }

                    @Override
                    public void modifiedService(ServiceReference<HazelcastInstance> reference, HazelcastInstance service) {
                        // Ignore
                    }

                    @Override
                    public void removedService(ServiceReference<HazelcastInstance> reference, HazelcastInstance service) {
                        removeService(HazelcastInstance.class);
                        hzCredStorage.changeBackingMapToLocalMap();
                        context.ungetService(reference);
                    }
                };
                track(HazelcastInstance.class, customizer);
            }
        } else {
            hzCredStorage = null;
            rdbCredStorage = null;
        }

        OSGiCredentialStorageProvider storageProvider = new OSGiCredentialStorageProvider(context);
        rememberTracker(storageProvider);

        openTrackers();

        {
            CredStoragePasswordChangeHandler handler = new CredStoragePasswordChangeHandler();
            Dictionary<String, Object> props = new Hashtable<String, Object>(2);
            props.put(EventConstants.EVENT_TOPIC, handler.getTopic());
            registerService(EventHandler.class, handler, props);
        }

        registerService(CreateTableService.class, new CreateCredStorageTable(), null);
        registerService(DeleteListener.class, new CredStorageDeleteListener(), null);
        registerService(UpdateTaskProviderService.class, new DefaultUpdateTaskProviderService(new CredStorageCreateTableTask(), new CredConvertUtf8ToUtf8mb4Task()));

        registerService(CredentialStorageProvider.class, storageProvider);
        addService(CredentialStorageProvider.class, storageProvider);
        if (null != hzCredStorage) {
            registerService(CredentialStorage.class, hzCredStorage, withRanking(0));
        }
        if (null != rdbCredStorage) {
            // Higher ranked
            registerService(CredentialStorage.class, rdbCredStorage, withRanking(10));
        }
    }

    @Override
    protected void stopBundle() throws Exception {
        CredStorageServices.setServiceLookup(null);
        super.stopBundle();
    }

}
