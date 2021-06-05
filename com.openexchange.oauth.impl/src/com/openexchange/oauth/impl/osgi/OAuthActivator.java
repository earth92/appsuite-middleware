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

package com.openexchange.oauth.impl.osgi;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import com.hazelcast.core.HazelcastInstance;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.crypto.CryptoService;
import com.openexchange.database.DatabaseService;
import com.openexchange.database.provider.DBProvider;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.external.account.ExternalAccountProvider;
import com.openexchange.hazelcast.serialization.CustomPortableFactory;
import com.openexchange.html.HtmlService;
import com.openexchange.http.client.builder.HTTPResponseProcessor;
import com.openexchange.http.deferrer.CustomRedirectURLDetermination;
import com.openexchange.http.deferrer.DeferringURLService;
import com.openexchange.id.IDGeneratorService;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.oauth.AdministrativeOAuthAccountStorage;
import com.openexchange.oauth.CallbackRegistry;
import com.openexchange.oauth.OAuthAPIRegistry;
import com.openexchange.oauth.OAuthAccountDeleteListener;
import com.openexchange.oauth.OAuthAccountInvalidationListener;
import com.openexchange.oauth.OAuthAccountReauthorizedListener;
import com.openexchange.oauth.OAuthAccountStorage;
import com.openexchange.oauth.OAuthService;
import com.openexchange.oauth.OAuthServiceMetaDataRegistry;
import com.openexchange.oauth.access.OAuthAccessRegistryService;
import com.openexchange.oauth.association.OAuthAccountAssociationService;
import com.openexchange.oauth.http.OAuthHTTPClientFactory;
import com.openexchange.oauth.impl.OAuthAPIRegistryImpl;
import com.openexchange.oauth.impl.access.impl.OAuthAccessRegistryServiceImpl;
import com.openexchange.oauth.impl.association.OAuthAccountAssociationServiceImpl;
import com.openexchange.oauth.impl.httpclient.impl.scribe.ScribeHTTPClientFactoryImpl;
import com.openexchange.oauth.impl.internal.AdministrativeOAuthAccountStorageSQLImpl;
import com.openexchange.oauth.impl.internal.CallbackRegistryImpl;
import com.openexchange.oauth.impl.internal.DeleteListenerRegistry;
import com.openexchange.oauth.impl.internal.InvalidationListenerRegistry;
import com.openexchange.oauth.impl.internal.OAuthAccountStorageSQLImpl;
import com.openexchange.oauth.impl.internal.OAuthExternalAccountProvider;
import com.openexchange.oauth.impl.internal.OAuthServiceImpl;
import com.openexchange.oauth.impl.internal.ReauthorizeListenerRegistry;
import com.openexchange.oauth.impl.internal.hazelcast.PortableCallbackRegistryFetch;
import com.openexchange.oauth.impl.internal.hazelcast.PortableCallbackRegistryFetchFactory;
import com.openexchange.oauth.impl.scope.impl.OAuthScopeRegistryImpl;
import com.openexchange.oauth.impl.services.Services;
import com.openexchange.oauth.scope.OAuthScopeRegistry;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.SimpleRegistryListener;
import com.openexchange.secret.SecretEncryptionFactoryService;
import com.openexchange.secret.recovery.EncryptedItemCleanUpService;
import com.openexchange.secret.recovery.EncryptedItemDetectorService;
import com.openexchange.secret.recovery.SecretMigrator;
import com.openexchange.session.SessionHolder;
import com.openexchange.sessiond.SessiondEventConstants;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.timer.ScheduledTimerTask;
import com.openexchange.timer.TimerService;
import com.openexchange.user.UserService;

/**
 * {@link OAuthActivator} - The activator for OAuth bundle.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class OAuthActivator extends HousekeepingActivator {

    private OSGiDelegateServiceMap delegateServices;
    private ScheduledTimerTask timerTask;
    private OAuthAccessRegistryServiceImpl accessRegistryService;
    private org.scribe.model.RequestListener scribeRequestListener;

    /**
     * Initializes a new {@link OAuthActivator}.
     */
    public OAuthActivator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        //@formatter:off
        return new Class<?>[] { DatabaseService.class, SessiondService.class, EventAdmin.class, SecretEncryptionFactoryService.class,
            SessionHolder.class, CryptoService.class, ConfigViewFactory.class, TimerService.class, DispatcherPrefixService.class,
            UserService.class, SSLSocketFactoryProvider.class, ThreadPoolService.class };
        //@formatter:on
    }

    @Override
    public synchronized void startBundle() throws Exception {
        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OAuthActivator.class);
        try {
            log.info("starting bundle: com.openexchange.oauth");

            Services.setServiceLookup(this);

            DeleteListenerRegistry.initInstance();
            InvalidationListenerRegistry.initInstance();
            ReauthorizeListenerRegistry.initInstance();
            /*
             * Collect OAuth services
             */
            OSGiMetaDataRegistry.initialize();
            final OSGiMetaDataRegistry registry = OSGiMetaDataRegistry.getInstance();
            final BundleContext context = this.context;
            registry.start(context);

            final OAuthAccessRegistryServiceImpl accessRegistryService = new OAuthAccessRegistryServiceImpl();
            this.accessRegistryService = accessRegistryService;
            registerService(OAuthAccessRegistryService.class, accessRegistryService);
            trackService(OAuthAccessRegistryService.class);

            OAuthScopeRegistry oauthScopeRegistry = new OAuthScopeRegistryImpl();
            registerService(OAuthScopeRegistry.class, oauthScopeRegistry);
            trackService(OAuthScopeRegistry.class);

            OAuthAccountAssociationServiceImpl associationService = new OAuthAccountAssociationServiceImpl(context);
            rememberTracker(associationService);

            /*
             * Start other trackers
             */
            track(OAuthAccountDeleteListener.class, new DeleteListenerServiceTracker(context));
            track(OAuthAccountReauthorizedListener.class, new ReauthorizeListenerServiceTracker(context));
            track(OAuthAccountInvalidationListener.class, new InvalidationListenerServiceTracker(context));
            trackService(HtmlService.class);
            trackService(DeferringURLService.class);
            trackService(HazelcastInstance.class);
            openTrackers();
            /*
             * Register
             */
            final CallbackRegistryImpl cbRegistry = new CallbackRegistryImpl();
            {
                final TimerService timerService = getService(TimerService.class);
                final ScheduledTimerTask timerTask = timerService.scheduleAtFixedRate(cbRegistry, 600000, 600000);
                this.timerTask = new ScheduledTimerTask() {

                    @Override
                    public boolean cancel() {
                        cbRegistry.clear();
                        boolean retval = timerTask.cancel();
                        timerService.purge();
                        return retval;
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        cbRegistry.clear();
                        boolean retval = timerTask.cancel(mayInterruptIfRunning);
                        timerService.purge();
                        return retval;
                    }
                };
            }
            PortableCallbackRegistryFetch.setCallbackRegistry(cbRegistry);
            registerService(CustomPortableFactory.class, new PortableCallbackRegistryFetchFactory(), null);
            final OSGiDelegateServiceMap delegateServices = new OSGiDelegateServiceMap();
            this.delegateServices = delegateServices;
            delegateServices.put(DBProvider.class, new OSGiDatabaseServiceDBProvider().start(context));
            delegateServices.put(ContextService.class, new OSGiContextService().start(context));
            delegateServices.put(IDGeneratorService.class, new OSGiIDGeneratorService().start(context));
            delegateServices.startAll(context);

            final OAuthAccountStorageSQLImpl oauthAccountStorage = new OAuthAccountStorageSQLImpl(delegateServices.get(DBProvider.class), delegateServices.get(IDGeneratorService.class), registry, delegateServices.get(ContextService.class));
            final OAuthServiceImpl oauthService = new OAuthServiceImpl(registry, oauthAccountStorage, cbRegistry);

            registerService(CallbackRegistry.class, cbRegistry);
            registerService(CustomRedirectURLDetermination.class, cbRegistry);
            registerService(OAuthService.class, oauthService);
            registerService(OAuthAccountStorage.class, oauthAccountStorage);
            registerService(AdministrativeOAuthAccountStorage.class, new AdministrativeOAuthAccountStorageSQLImpl(delegateServices.get(DBProvider.class), delegateServices.get(IDGeneratorService.class), registry, delegateServices.get(ContextService.class)));
            registerService(ExternalAccountProvider.class, new OAuthExternalAccountProvider(this));
            registerService(OAuthServiceMetaDataRegistry.class, registry);
            registerService(OAuthAccountAssociationService.class, associationService);
            registerService(EncryptedItemDetectorService.class, oauthAccountStorage);
            registerService(SecretMigrator.class, oauthAccountStorage);
            registerService(EncryptedItemCleanUpService.class, oauthAccountStorage);
            registerService(OAuthAPIRegistry.class, OAuthAPIRegistryImpl.getInstance());

            trackService(AdministrativeOAuthAccountStorage.class);

            org.scribe.model.RequestListener scribeRequestListener = new org.scribe.model.RequestListener() {

                @Override
                public void onBeforeSend(org.scribe.model.Request request) throws IOException {
                    request.setConnectTimeout(5, TimeUnit.SECONDS);
                    request.setReadTimeout(30, TimeUnit.SECONDS);

                    SSLSocketFactoryProvider factoryProvider = Services.optService(SSLSocketFactoryProvider.class);
                    if (null != factoryProvider) {
                        request.setSSLSocketFactory(factoryProvider.getDefault());
                    }
                }

                @Override
                public void onAfterSend(org.scribe.model.Request request, org.scribe.model.Response response) {
                    // Don't care
                }
            };
            org.scribe.model.Request.addRequestListener(scribeRequestListener);
            this.scribeRequestListener = scribeRequestListener;

            final ScribeHTTPClientFactoryImpl oauthFactory = new ScribeHTTPClientFactoryImpl();
            registerService(OAuthHTTPClientFactory.class, oauthFactory);

            {
                EventHandler eventHandler = new EventHandler() {

                    @Override
                    public void handleEvent(Event event) {
                        String topic = event.getTopic();
                        if (SessiondEventConstants.TOPIC_LAST_SESSION.equals(topic)) {
                            Integer contextId = (Integer) event.getProperty(SessiondEventConstants.PROP_CONTEXT_ID);
                            if (null != contextId) {
                                Integer userId = (Integer) event.getProperty(SessiondEventConstants.PROP_USER_ID);
                                if (null != userId) {
                                    accessRegistryService.userInactive(userId.intValue(), contextId.intValue());

                                }
                            }
                        }
                    }
                };
                Dictionary<String, Object> dict = new Hashtable<String, Object>(1);
                dict.put(EventConstants.EVENT_TOPIC, SessiondEventConstants.TOPIC_LAST_SESSION);
                registerService(EventHandler.class, eventHandler, dict);
            }

            SimpleRegistryListener<HTTPResponseProcessor> listener = new SimpleRegistryListener<HTTPResponseProcessor>() {

                @Override
                public void added(ServiceReference<HTTPResponseProcessor> ref, HTTPResponseProcessor service) {
                    oauthFactory.registerProcessor(service);
                }

                @Override
                public void removed(ServiceReference<HTTPResponseProcessor> ref, HTTPResponseProcessor service) {
                    oauthFactory.forgetProcessor(service);
                }

            };
            ServiceTracker<HTTPResponseProcessor, HTTPResponseProcessor> tracker = track(HTTPResponseProcessor.class, listener);
            tracker.open();
        } catch (Exception e) {
            log.error("Starting bundle \"com.openexchange.oauth\" failed.", e);
            throw e;
        }
    }

    @Override
    public synchronized void stopBundle() throws Exception {
        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OAuthActivator.class);
        try {
            log.info("stopping bundle: com.openexchange.oauth");
            super.stopBundle();

            {
                final OSGiDelegateServiceMap delegateServices = this.delegateServices;
                if (null != delegateServices) {
                    delegateServices.clear();
                    this.delegateServices = null;
                }
            }

            {
                ScheduledTimerTask timerTask = this.timerTask;
                if (null != timerTask) {
                    this.timerTask = null;
                    timerTask.cancel();
                }
            }

            OAuthAccessRegistryServiceImpl accessRegistryService = this.accessRegistryService;
            if (null != accessRegistryService) {
                this.accessRegistryService = null;
                accessRegistryService.clear();
            }

            org.scribe.model.RequestListener scribeRequestListener = this.scribeRequestListener;
            if (null != scribeRequestListener) {
                this.scribeRequestListener = null;
                org.scribe.model.Request.removeRequestListener(scribeRequestListener);
            }

            ReauthorizeListenerRegistry.releaseInstance();
            DeleteListenerRegistry.releaseInstance();
            OSGiMetaDataRegistry.releaseInstance();
            Services.setServiceLookup(null);
        } catch (Exception e) {
            log.error("Stopping bundle \"com.openexchange.oauth\" failed.", e);
            throw e;
        }
    }

}
