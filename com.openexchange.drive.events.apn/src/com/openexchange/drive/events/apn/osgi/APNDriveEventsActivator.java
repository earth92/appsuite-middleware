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

package com.openexchange.drive.events.apn.osgi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.osgi.framework.ServiceReference;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.config.lean.Property;
import com.openexchange.drive.events.DriveEventService;
import com.openexchange.drive.events.apn.APNAccess;
import com.openexchange.drive.events.apn.IOSAPNCertificateProvider;
import com.openexchange.drive.events.apn.MacOSAPNCertificateProvider;
import com.openexchange.drive.events.apn.internal.APNDriveEventPublisher;
import com.openexchange.drive.events.apn.internal.DriveEventsAPNProperty;
import com.openexchange.drive.events.apn.internal.OperationSystemType;
import com.openexchange.drive.events.subscribe.DriveSubscriptionStore;
import com.openexchange.exception.OXException;
import com.openexchange.fragment.properties.loader.FragmentPropertiesLoader;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.SimpleRegistryListener;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.timer.TimerService;

/**
 * {@link APNDriveEventsActivator}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class APNDriveEventsActivator extends HousekeepingActivator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(APNDriveEventsActivator.class);
    private static final String TOPIC_VANILLA_APP_IOS = "com.openexchange.drive";
    private static final String TOPIC_VANILLA_APP_MACOS = "com.openxchange.drive.macos.OXDrive";

    /**
     * Initializes a new {@link APNDriveEventsActivator}.
     */
    public APNDriveEventsActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { DriveEventService.class, DriveSubscriptionStore.class, LeanConfigurationService.class, TimerService.class, ThreadPoolService.class };
    }

    @Override
    protected Class<?>[] getOptionalServices() {
        return new Class<?>[] { IOSAPNCertificateProvider.class, MacOSAPNCertificateProvider.class };
    }

    @Override
    protected void startBundle() throws Exception {
        LOG.info("starting bundle: {}", context.getBundle().getSymbolicName());
        track(FragmentPropertiesLoader.class, new SimpleRegistryListener<FragmentPropertiesLoader>() {

            private IOSAPNCertificateProvider iosProvider;
            private MacOSAPNCertificateProvider macosProvider;

            @Override
            public synchronized void added(ServiceReference<FragmentPropertiesLoader> ref, FragmentPropertiesLoader service) {
                Properties properties = service.load(DriveEventsAPNProperty.FRAGMENT_FILE_NAME);
                if (properties != null) {
                    APNAccess access = createAccess(properties, OperationSystemType.IOS, service, TOPIC_VANILLA_APP_IOS);
                    if (access != null) {
                        iosProvider = () -> access;
                        registerService(IOSAPNCertificateProvider.class, iosProvider);
                    }

                    APNAccess macAccess = createAccess(properties, OperationSystemType.MACOS, service, TOPIC_VANILLA_APP_MACOS);
                    if (macAccess != null) {
                        macosProvider = () -> macAccess;
                        registerService(MacOSAPNCertificateProvider.class, macosProvider);
                    }
                }
            }

            @Override
            public synchronized void removed(ServiceReference<FragmentPropertiesLoader> ref, FragmentPropertiesLoader service) {
                if (iosProvider != null) {
                    unregisterService(iosProvider);
                }
                if (macosProvider != null) {
                    unregisterService(macosProvider);
                }
            }
        });
        openTrackers();
        /*
         * register publishers
         */
        DriveEventService eventService = getServiceSafe(DriveEventService.class);
        eventService.registerPublisher(new APNDriveEventPublisher(this, "apn", OperationSystemType.IOS, IOSAPNCertificateProvider.class));
        eventService.registerPublisher(new APNDriveEventPublisher(this, "apn.macos", OperationSystemType.MACOS, MacOSAPNCertificateProvider.class));
    }

    @Override
    public <S> void registerService(Class<S> clazz, S service) {
        super.registerService(clazz, service);
    }

    @Override
    public <S> void unregisterService(S service) {
        super.unregisterService(service);
    }

    /**
     * Creates an {@link APNAccess} from the given {@link Properties} object
     *
     * @param properties The {@link Properties} object
     * @param The OS identifier
     * @param The {@link FragmentPropertiesLoader}
     * @return The {@link APNAccess} or null
     */
    protected APNAccess createAccess(Properties properties, OperationSystemType type, FragmentPropertiesLoader loader, String topic) {
        try {
            Map<String, String> optionals = Collections.singletonMap(DriveEventsAPNProperty.OPTIONAL_FIELD, type.getName());

            // Try with PKCS#8 private key first
            String privateKey = optProperty(properties, DriveEventsAPNProperty.privatekey, optionals);
            if (Strings.isNotEmpty(privateKey)) {
                String keyId = getProperty(properties, DriveEventsAPNProperty.keyid, optionals);
                String teamId = getProperty(properties, DriveEventsAPNProperty.teamid, optionals);
                boolean production = Boolean.parseBoolean(getProperty(properties, DriveEventsAPNProperty.production, optionals));

                // Check file path validity
                if (new File(privateKey).exists()) {
                    try (FileInputStream keyIn = new FileInputStream(privateKey)) {
                        return new APNAccess(Streams.stream2bytes(keyIn), keyId, teamId, topic, production);
                    } catch (IOException e) {
                        LOG.warn("Could not read file: {}", privateKey, e);
                    }
                }

                // Assume file is given as resource identifier
                try {
                    byte[] privateKeyBytes = Streams.stream2bytes(loader.loadResource(privateKey));
                    if (privateKeyBytes.length == 0) {
                        return null;
                    }
                    return new APNAccess(privateKeyBytes, keyId, teamId, topic, production);
                } catch (IOException e) {
                    LOG.warn("Error instantiating APNS options from resource {}", privateKey, e);
                }
            }

            // Use PKCS#12 keystore as fall-back
            String keystore = optProperty(properties, DriveEventsAPNProperty.keystore, optionals);
            if (Strings.isNotEmpty(keystore)) {
                String password = getProperty(properties, DriveEventsAPNProperty.password, optionals);
                boolean production = Boolean.parseBoolean(getProperty(properties, DriveEventsAPNProperty.production, optionals));

                // Check file path validity
                if (new File(keystore).exists()) {
                    return new APNAccess(keystore, password, production, topic);
                }

                // Assume file is given as resource identifier
                try {
                    byte[] keystoreBytes = Streams.stream2bytes(loader.loadResource(keystore));
                    if (keystoreBytes.length == 0) {
                        return null;
                    }
                    return new APNAccess(keystoreBytes, password, production, topic);
                } catch (IOException e) {
                    LOG.warn("Error instantiating APNS options from resource {}", keystore, e);
                }
            }
        } catch (OXException e) {
            LOG.debug("Error while creating APN access", e);
        }
        return null;
    }

    /**
     * Get the given property from the {@link Properties} object
     *
     * @param properties The {@link Properties} object
     * @param prop The {@link Property} to return
     * @param optional The optional
     * @return The string value of the property
     * @throws OXException In case the property is missing
     */
    private String getProperty(Properties properties, Property prop, Map<String, String> optional) throws OXException {
        String result = properties.getProperty(prop.getFQPropertyName(optional));
        if (result == null) {
            // This should never happen as long as the shipped fragment contains a proper properties file
            LOG.error("Missing required property from fragment: {}", prop.getFQPropertyName());
            throw OXException.general("Missing property: " + prop.getFQPropertyName());
        }
        return result;
    }

    /**
     * Get the given property from the {@link Properties} object or <code>null</code>
     *
     * @param properties The {@link Properties} object
     * @param prop The {@link Property} to return
     * @param optional The optional
     * @return The string value of the property or <code>null</code> in case property is missing
     */
    private String optProperty(Properties properties, Property prop, Map<String, String> optional) {
        String result = properties.getProperty(prop.getFQPropertyName(optional));
        if (null == result) {
            LOG.debug("Missing required property from fragment: {}", prop.getFQPropertyName());
        }
        return result;
    }

    @Override
    protected void stopBundle() throws Exception {
        LOG.info("stopping bundle: {}", context.getBundle().getSymbolicName());
        super.stopBundle();
    }

}
