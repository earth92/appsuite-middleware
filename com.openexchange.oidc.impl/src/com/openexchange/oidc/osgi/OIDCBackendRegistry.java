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
package com.openexchange.oidc.osgi;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hazelcast.core.HazelcastInstance;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.LoginServlet;
import com.openexchange.ajax.login.LoginConfiguration;
import com.openexchange.ajax.login.LoginRequestHandler;
import com.openexchange.exception.OXException;
import com.openexchange.java.ConcurrentList;
import com.openexchange.java.Strings;
import com.openexchange.oidc.OIDCBackend;
import com.openexchange.oidc.OIDCConfig;
import com.openexchange.oidc.OIDCExceptionCode;
import com.openexchange.oidc.OIDCExceptionHandler;
import com.openexchange.oidc.OIDCWebSSOProvider;
import com.openexchange.oidc.http.AuthenticationService;
import com.openexchange.oidc.http.InitService;
import com.openexchange.oidc.http.LogoutService;
import com.openexchange.oidc.impl.OIDCLoginRequestHandler;
import com.openexchange.oidc.impl.OIDCLogoutRequestHandler;
import com.openexchange.oidc.impl.OIDCWebSSOProviderImpl;
import com.openexchange.oidc.state.impl.CoreStateManagement;
import com.openexchange.oidc.tools.OIDCTools;
import com.openexchange.osgi.service.http.HttpServices;
import com.openexchange.server.ServiceLookup;
import com.openexchange.serverconfig.ComputedServerConfigValueService;
import com.openexchange.session.Session;

/**
 * Registers and stores all OpenID backends and their servlets to handle future requests.
 *
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 * @since v7.10.0
 */
public class OIDCBackendRegistry extends ServiceTracker<OIDCBackend, OIDCBackend>{

    private static final Logger LOG = LoggerFactory.getLogger(OIDCBackendRegistry.class);

    private static final String ERROR_WHILE_REMOVING_PATH_FOR_OIDC_BACKEND = "Error while removing path for OIDC Backend";

    /**
     * Server config key for oidc path
     */
    public static final String OIDC_PATH = "oidcPath";

    private final ServiceLookup services;
    private final ConcurrentList<OIDCBackend> backends;
    private final ConcurrentHashMap<OIDCBackend, Stack<String>> backendServlets;
    private final ConcurrentHashMap<OIDCBackend, Stack<ServiceRegistration<?>>> backendServiceRegistrations;
    private final LoginConfiguration loginConfiguration;
    private final CoreStateManagement stateManagement;

    public OIDCBackendRegistry(BundleContext context, ServiceLookup services) {
        super(context, OIDCBackend.class, null);
        this.services = services;
        this.backends = new ConcurrentList<>();
        this.backendServlets = new ConcurrentHashMap<>();
        this.backendServiceRegistrations = new ConcurrentHashMap<>();
        this.loginConfiguration = LoginServlet.getLoginConfiguration();
        this.stateManagement = new CoreStateManagement(services.getService(HazelcastInstance.class));
    }

    @Override
    public OIDCBackend addingService(ServiceReference<OIDCBackend> reference) {
        final OIDCBackend oidcBackend = this.context.getService(reference);

        final Stack<String> servlets = new Stack<>();
        HttpService httpService = this.services.getService(HttpService.class);
        final Stack<ServiceRegistration<?>> serviceRegistrations = new Stack<>();

        if (!backends.addIfAbsent(oidcBackend)) {
            // Such a back-end already exists. Release obtained service and return
            this.context.ungetService(reference);
            return null;
        }

        String path = oidcBackend.getPath();
        LOG.info("Adding OIDCBackend: {}", Strings.isEmpty(path) ? oidcBackend.getClass().getSimpleName() : path);

        boolean error = true; // pessimistic
        try {
            OIDCConfig config = oidcBackend.getOIDCConfig();
            if (config == null) {
                throw OIDCExceptionCode.MISSING_BACKEND_CONFIGURATION.create(Strings.isEmpty(path) ? "No path available" : path);
            }

            if (!Strings.isEmpty(path)) {
                OIDCTools.validatePath(path);
            }
            serviceRegistrations.push(context.registerService(ComputedServerConfigValueService.class, getOidcPathComputedValue(oidcBackend),null));

            oidcBackend.setLoginConfiguration(this.loginConfiguration);
            OIDCWebSSOProvider ssoProvider = new OIDCWebSSOProviderImpl(oidcBackend, stateManagement, this.services, this.loginConfiguration);
            OIDCExceptionHandler exceptionHandler = oidcBackend.getExceptionHandler();

            String prefix = OIDCTools.getPrefix(oidcBackend);
            this.registerServlet(servlets, httpService, prefix, new InitService(ssoProvider, exceptionHandler), "init");
            this.registerServlet(servlets, httpService, prefix, new AuthenticationService(ssoProvider, exceptionHandler), "auth");
            this.registerServlet(servlets, httpService, prefix, new LogoutService(ssoProvider, exceptionHandler), "logout");
            this.registerRequestHandler(oidcBackend, serviceRegistrations, OIDCTools.OIDC_LOGIN, new OIDCLoginRequestHandler(oidcBackend));
            this.registerRequestHandler(oidcBackend, serviceRegistrations, OIDCTools.OIDC_LOGOUT, new OIDCLogoutRequestHandler(oidcBackend));

            if (!servlets.isEmpty()) {
                this.backendServlets.putIfAbsent(oidcBackend, servlets);
            }
            if (!serviceRegistrations.isEmpty()) {
                this.backendServiceRegistrations.putIfAbsent(oidcBackend, serviceRegistrations);
            }

            error = false;
            return oidcBackend;
        } catch (Exception e) {
            LOG.error("Failed to add OIDCBackend {} to registry", Strings.isEmpty(path) ? oidcBackend.getClass().getSimpleName() : path, e);
        } finally {
            if (error) {
                while (!servlets.isEmpty()) {
                    HttpServices.unregister(servlets.pop(), httpService);
                }
                while (!serviceRegistrations.isEmpty()) {
                    ServiceRegistration<?> pop = serviceRegistrations.pop();
                    if (null != pop) {
                        pop.unregister();
                    }
                }
                this.backends.remove(oidcBackend);
                this.context.ungetService(reference);
            }
        }

        // Return nothing...
        this.context.ungetService(reference);
        return null;
    }

    private ComputedServerConfigValueService getOidcPathComputedValue(final OIDCBackend oidcBackend) {
        return new ComputedServerConfigValueService() {

            @Override
            public void addValue(Map<String, Object> serverConfig, String hostName, int userID, int contextID, Session optSession) throws OXException {

                if (serverConfig.containsKey(OIDC_PATH)) {
                    return;
                }

                String oidcPath = "/" + OIDCTools.DEFAULT_BACKEND_PATH;
                String backendPath = oidcBackend.getPath();
                if (!Strings.isEmpty(backendPath)) {
                    oidcPath += "/" + backendPath;
                }

                List<String> hosts = oidcBackend.getBackendConfig().getHosts();
                if (hosts.contains("all")) {
                    serverConfig.put(OIDC_PATH, oidcPath);
                    return;
                }

                for (String hostIdentifer: hosts) {
                    if (!Strings.isEmpty(hostIdentifer) && hostIdentifer.equalsIgnoreCase(hostName)) {
                        serverConfig.put(OIDC_PATH, oidcPath);
                        return;
                    }
                }
            }
        };
    }

    /**
     * Helper method to register a servlet
     * @param servlets the servlets stack of a OIDCBackend
     * @param httpService the HttpService where to register the servlet
     * @param prefix prefix of this OIDCBackend
     * @param servlet the servlet to be registered
     * @param part additional servlet path information
     * @throws ServletException if the servlet's init method throws an exception, or the given servlet object has already been registered at a different alias.
     * @throws NamespaceException if the registration fails because the alias is already in use.
     */
    private void registerServlet(final Stack<String> servlets, HttpService httpService, String prefix, Servlet servlet, String part) throws ServletException, NamespaceException {
        String servletName = prefix + part;
        httpService.registerServlet(servletName, servlet, null, null);
        servlets.push(servletName);
    }

    private void registerRequestHandler(final OIDCBackend backend, final Stack<ServiceRegistration<?>> serviceRegistrations, String oidcAction, LoginRequestHandler requestHandler) {
        Dictionary<String, Object> requestHandlerProps = new Hashtable<String, Object>();
        requestHandlerProps.put(AJAXServlet.PARAMETER_ACTION, oidcAction + OIDCTools.getPathString(backend.getPath()));
        serviceRegistrations.push(context.registerService(LoginRequestHandler.class, requestHandler, requestHandlerProps));
    }

    /**
     * Removes all registered OIDC backends from backend container. Unregisters all
     * servlets for each backend and also all Handlers.
     */
    public void stop() {
        for (OIDCBackend oidcBackend : backends) {
            Stack<String> servlets = backendServlets.remove(oidcBackend);
            try {
                if (null != servlets) {
                    HttpService httpService = services.getService(HttpService.class);
                    while (!servlets.isEmpty()) {
                        String pop = servlets.pop();
                        HttpServices.unregister(pop, httpService);
                    }
                }
            } catch (Exception e) {
                LOG.error(ERROR_WHILE_REMOVING_PATH_FOR_OIDC_BACKEND, e);
            }
            Stack<ServiceRegistration<?>> registrations = backendServiceRegistrations.remove(oidcBackend);
            try {
                if (null != registrations) {
                    while (!registrations.isEmpty()) {
                        registrations.pop().unregister();
                    }
                }
            } catch (Exception e) {
                LOG.error(ERROR_WHILE_REMOVING_PATH_FOR_OIDC_BACKEND, e);
            }
        }
    }

    @Override
    public void removedService(ServiceReference<OIDCBackend> reference, OIDCBackend oidcBackend) {
        boolean removed = backends.remove(oidcBackend);
        if (!removed) {
            // Was not contained
            return;
        }

        Stack<String> servlets = backendServlets.remove(oidcBackend);
        try {
            if (null != servlets) {
                HttpService httpService = services.getService(HttpService.class);
                while (!servlets.isEmpty()) {
                    HttpServices.unregister(servlets.pop(), httpService);
                }
            }
        } catch (Exception e) {
            LOG.error(ERROR_WHILE_REMOVING_PATH_FOR_OIDC_BACKEND, e);
        }
        Stack<ServiceRegistration<?>> registrations = backendServiceRegistrations.remove(oidcBackend);
        try {
            if (null != registrations) {
                while (!registrations.isEmpty()) {
                    registrations.pop().unregister();
                }
            }
        } catch (Exception e) {
            LOG.error(ERROR_WHILE_REMOVING_PATH_FOR_OIDC_BACKEND, e);
        }
        context.ungetService(reference);
    }

    /**
     * Gets the unmodifiable list reference to the tracked back-ends.
     *
     * @return The unmodifiable list
     */
    public List<OIDCBackend> getAllRegisteredBackends() {
        return Collections.unmodifiableList(this.backends);
    }
}
