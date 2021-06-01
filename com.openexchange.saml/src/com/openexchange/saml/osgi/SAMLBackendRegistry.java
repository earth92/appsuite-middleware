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

package com.openexchange.saml.osgi;

import java.security.Provider;
import java.security.Security;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilderFactory;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.xmlsec.config.impl.JavaCryptoValidationInitializer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hazelcast.core.HazelcastInstance;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.login.LoginRequestHandler;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.exception.OXException;
import com.openexchange.java.ConcurrentList;
import com.openexchange.java.Strings;
import com.openexchange.osgi.service.http.HttpServices;
import com.openexchange.saml.OpenSAML;
import com.openexchange.saml.SAMLConfig;
import com.openexchange.saml.SAMLSessionParameters;
import com.openexchange.saml.http.AssertionConsumerService;
import com.openexchange.saml.http.InitService;
import com.openexchange.saml.http.MetadataService;
import com.openexchange.saml.http.SingleLogoutService;
import com.openexchange.saml.impl.DefaultLoginConfigurationLookup;
import com.openexchange.saml.impl.LoginConfigurationLookup;
import com.openexchange.saml.impl.SAMLLoginRequestHandler;
import com.openexchange.saml.impl.SAMLLogoutRequestHandler;
import com.openexchange.saml.impl.WebSSOProviderImpl;
import com.openexchange.saml.impl.hz.HzStateManagement;
import com.openexchange.saml.spi.ExceptionHandler;
import com.openexchange.saml.spi.SAMLBackend;
import com.openexchange.saml.tools.SAMLLoginTools;
import com.openexchange.server.ServiceLookup;
import com.openexchange.serverconfig.ComputedServerConfigValueService;
import com.openexchange.session.Session;

/**
 * {@link SAMLBackendRegistry} - A registry for {@link SAMLBackend} instances.
 *
 * @author <a href="mailto:felix.marx@open-xchange.com">Felix Marx</a>
 * @since 7.8.4
 */
public final class SAMLBackendRegistry extends ServiceTracker<SAMLBackend, SAMLBackend> {

    private static final Logger LOG = LoggerFactory.getLogger(SAMLBackendRegistry.class);

    private final ConcurrentList<SAMLBackend> backends;
    private final LoginConfigurationLookup loginConfigurationLookup;
    private final ServiceLookup services;
    private final HzStateManagement hzStateManagement;
    private final OpenSAML openSAML;
    private final ConcurrentHashMap<SAMLBackend, Stack<String>> backendServlets;
    private final ConcurrentHashMap<SAMLBackend, Stack<ServiceRegistration<?>>> backendServiceRegistrations;

    /**
     * Initializes a new {@link SAMLBackendRegistry}.
     *
     * @param context The bundle context
     * @param services The serviceLookup
     * @param loginConfigurationLookup
     * @throws BundleException
     */
    public SAMLBackendRegistry(BundleContext context, ServiceLookup services) throws BundleException {
        super(context, SAMLBackend.class, null);
        this.openSAML = initOpenSAML();
        this.loginConfigurationLookup = new DefaultLoginConfigurationLookup();
        this.services = services;
        this.backends = new ConcurrentList<SAMLBackend>();
        this.backendServlets = new ConcurrentHashMap<SAMLBackend, Stack<String>>();
        this.backendServiceRegistrations = new ConcurrentHashMap<SAMLBackend, Stack<ServiceRegistration<?>>>();
        this.hzStateManagement = new HzStateManagement(services.getService(HazelcastInstance.class));
    }

    @Override
    public SAMLBackend addingService(ServiceReference<SAMLBackend> reference) {
        final SAMLBackend samlBackend = context.getService(reference);
        final Stack<String> servlets = new Stack<String>();
        final Stack<ServiceRegistration<?>> serviceRegistrations = new Stack<ServiceRegistration<?>>();
        HttpService httpService = services.getService(HttpService.class);
        if (backends.addIfAbsent(samlBackend)) {
            try {
                // register new endpoints
                final SAMLConfig config = samlBackend.getConfig();
                if (null == config) {
                    throw new IllegalArgumentException("no saml config found, therefore not adding SAMLBackend");
                }
                String prefix = getPrefix(samlBackend);
                String path = samlBackend.getPath();
                if (Strings.isNotEmpty(path)) {
                    // path must be validated if it is not empty
                    validatePath(path);
                    serviceRegistrations.push(context.registerService(ComputedServerConfigValueService.class, getSamlPathComputedValue(samlBackend, config),null));
                }
                WebSSOProviderImpl serviceProvider = new WebSSOProviderImpl(config, openSAML, hzStateManagement, services, samlBackend);
                ExceptionHandler exceptionHandler = samlBackend.getExceptionHandler();

                registerServlet(servlets, httpService, prefix, new AssertionConsumerService(serviceProvider, exceptionHandler), "acs");
                registerServlet(servlets, httpService, prefix, new InitService(config, serviceProvider, exceptionHandler, loginConfigurationLookup, services), "init");

                registerRequestHandler(samlBackend, serviceRegistrations, SAMLLoginTools.ACTION_SAML_LOGIN, new SAMLLoginRequestHandler(samlBackend, loginConfigurationLookup, services));

                if (config.singleLogoutEnabled()) {
                    registerServlet(servlets, httpService, prefix, new SingleLogoutService(serviceProvider, exceptionHandler), "sls");
                    registerRequestHandler(samlBackend, serviceRegistrations, SAMLLoginTools.ACTION_SAML_LOGOUT, new SAMLLogoutRequestHandler(samlBackend, loginConfigurationLookup));
                    serviceRegistrations.push(context.registerService(ComputedServerConfigValueService.class, getSingleLogoutComputedValue(samlBackend, config),null));
                }
                if (config.enableMetadataService()) {
                    registerServlet(servlets, httpService, prefix, new MetadataService(serviceProvider, exceptionHandler), "metadata");
                }
                return samlBackend;
            } catch (NamespaceException | ServletException | IllegalArgumentException e) {
                LOG.error("Exception while registering SAML Backend", e);
                while (!servlets.isEmpty()) {
                    HttpServices.unregister(servlets.pop(), httpService);
                }
                while (!serviceRegistrations.isEmpty()) {
                    ServiceRegistration<?> pop = serviceRegistrations.pop();
                    if (null != pop) {
                        pop.unregister();
                    }
                }
                backends.remove(samlBackend);
                context.ungetService(reference);
            } finally {
                if (!servlets.isEmpty()) {
                    backendServlets.putIfAbsent(samlBackend, servlets);
                }
                if (!serviceRegistrations.isEmpty()) {
                    backendServiceRegistrations.putIfAbsent(samlBackend, serviceRegistrations);
                }

            }
        }
        return null;
    }

    /**
     * Helper method that validates the path to only contain allowed characters
     * @param path The path to be checked.
     * @return
     */
    private void validatePath(String path) {
        if (null == path) {
            throw new IllegalArgumentException("path is null");
        }
        if (path.matches(".*[^a-zA-Z0-9].*")) {
            throw new IllegalArgumentException("path contains not allowed parameters");
        }
    }

    /**
     * Helper method to register a RequestHandler
     * @param samlBackend the SAMLBackend
     * @param serviceRegistrations the registrations of that samlBackend
     * @param samlAction the action to be used as a parameter
     * @param requestHandler the RequestHandler to be registered
     */
    private void registerRequestHandler(final SAMLBackend samlBackend, final Stack<ServiceRegistration<?>> serviceRegistrations, String samlAction, LoginRequestHandler requestHandler) {
        Dictionary<String, Object> requestHandlerProps = new Hashtable<String, Object>();
        requestHandlerProps.put(AJAXServlet.PARAMETER_ACTION, samlAction + getPathString(samlBackend.getPath()));
        serviceRegistrations.push(context.registerService(LoginRequestHandler.class, requestHandler, requestHandlerProps));
    }

    /**
     * Helper method to register a servlet
     * @param servlets the servlets stack of a SAMLBackend
     * @param httpService the HttpService where to register the servlet
     * @param prefix prefix of this SAMLBackend
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

    /**
     * Helper method to get the prefix
     * @param samlBackend the SAMLBackend to check
     * @return path of the samlBackend
     */
    private String getPrefix(final SAMLBackend samlBackend) {
        StringBuilder prefixBuilder = new StringBuilder();
        prefixBuilder.append(services.getService(DispatcherPrefixService.class).getPrefix());
        prefixBuilder.append("saml/");
        String path = samlBackend.getPath();
        if (Strings.isNotEmpty(path)) {
            prefixBuilder.append(path).append("/");
        }
        return prefixBuilder.toString();
    }

    /**
     * Helper method to provide either the path or empty String
     * @param path The path to check
     * @return <code>empty_string</code> if path is Empty or <code>path</code>
     */
    private String getPathString(String path) {
        if (Strings.isEmpty(path)) {
            return "";
        }
        return path;
    }

    private ComputedServerConfigValueService getSingleLogoutComputedValue(final SAMLBackend samlBackend, final SAMLConfig config) {
        return new ComputedServerConfigValueService() {

            @Override
            public void addValue(Map<String, Object> serverConfig, String hostName, int userID, int contextID, Session optSession) throws OXException {
                if (serverConfig.containsKey("samlSingleLogout")) {
                    return;
                }
                // check session value first and fall back to samlBackend
                if (null != optSession && null != optSession.getParameter(SAMLSessionParameters.SINGLE_LOGOUT)) {
                    serverConfig.put("samlSingleLogout", optSession.getParameter(SAMLSessionParameters.SINGLE_LOGOUT));
                    return;
                }
                if (Strings.isEmpty(samlBackend.getPath())) {
                    serverConfig.put("samlSingleLogout", Boolean.TRUE);
                    return;
                }
                Set<String> hosts = config.getHosts();
                if (hosts.contains("all")) {
                    serverConfig.put("samlSingleLogout", Boolean.TRUE);
                    return;
                }
                for (String hostIdentifer: hosts) {
                    if (Strings.isNotEmpty(hostIdentifer) && hostIdentifer.equalsIgnoreCase(hostName)) {
                        serverConfig.put("samlSingleLogout", Boolean.TRUE);
                        return;
                    }
                }
            }
        };
    }

    private ComputedServerConfigValueService getSamlPathComputedValue(final SAMLBackend samlBackend, final SAMLConfig config) {
        return new ComputedServerConfigValueService() {

            @Override
            public void addValue(Map<String, Object> serverConfig, String hostName, int userID, int contextID, Session optSession) throws OXException {
                if (serverConfig.containsKey("samlPath")) {
                    return;
                }
                // check session value first and fall back to samlBackend
                if (null != optSession && null != optSession.getParameter(SAMLSessionParameters.SAML_PATH)) {
                    serverConfig.put("samlPath", optSession.getParameter(SAMLSessionParameters.SAML_PATH));
                    return;
                }
                if (Strings.isEmpty(samlBackend.getPath())) {
                    return;
                }
                Set<String> hosts = config.getHosts();
                if (hosts.contains("all")) {
                    serverConfig.put("samlPath", samlBackend.getPath());
                    return;
                }
                for (String hostIdentifer: hosts) {
                    if (Strings.isNotEmpty(hostIdentifer) && hostIdentifer.equalsIgnoreCase(hostName)) {
                        serverConfig.put("samlPath", samlBackend.getPath());
                        return;
                    }
                }
            }
        };
    }

    @Override
    public void removedService(ServiceReference<SAMLBackend> reference, SAMLBackend samlBackend) {
        backends.remove(samlBackend);
        Stack<String> servlets = backendServlets.remove(samlBackend);
        try {
            if (null != servlets) {
                HttpService httpService = services.getService(HttpService.class);
                while (!servlets.isEmpty()) {
                    HttpServices.unregister(servlets.pop(), httpService);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while removing path for SAML Backend", e);
        }
        Stack<ServiceRegistration<?>> registrations = backendServiceRegistrations.remove(samlBackend);
        try {
            if (null != registrations) {
                while (!registrations.isEmpty()) {
                    registrations.pop().unregister();
                }
            }
        } catch (Exception e) {
            LOG.error("Error while removing path for SAML Backend", e);
        }
        context.ungetService(reference);
    }

    public void stop() {
        for (Iterator<SAMLBackend> iterator = backends.iterator(); iterator.hasNext();) {
            SAMLBackend samlBackend = iterator.next();
            Stack<String> servlets = backendServlets.remove(samlBackend);
            try {
                if (null != servlets) {
                    HttpService httpService = services.getService(HttpService.class);
                    while (!servlets.isEmpty()) {
                        String pop = servlets.pop();
                        HttpServices.unregister(pop, httpService);
                    }
                }
            } catch (Exception e) {
                LOG.error("Error while removing path for SAML Backend", e);
            }
            Stack<ServiceRegistration<?>> registrations = backendServiceRegistrations.remove(samlBackend);
            try {
                if (null != registrations) {
                    while (!registrations.isEmpty()) {
                        registrations.pop().unregister();
                    }
                }
            } catch (Exception e) {
                LOG.error("Error while removing path for SAML Backend", e);
            }
        }
    }

    private OpenSAML initOpenSAML() throws BundleException {
        try {
            new JavaCryptoValidationInitializer().init();
        } catch (InitializationException e1) {
            LOG.error("The necessary JCE providers for OpenSAML could not be found. SAML 2.0 integration will be disabled!");
            throw new BundleException("The necessary JCE providers for OpenSAML could not be found.", BundleException.ACTIVATOR_ERROR);
        }

        LOG.info("OpenSAML will use {} as API for XML processing", DocumentBuilderFactory.newInstance().getClass().getName());
        for (Provider jceProvider : Security.getProviders()) {
            LOG.info("OpenSAML found {} as potential JCE provider", jceProvider.getInfo());
        }

        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            LOG.error("Error while bootstrapping OpenSAML library", e);
            throw new BundleException("Error while bootstrapping OpenSAML library", BundleException.ACTIVATOR_ERROR, e);
        }
        return new OpenSAML();
    }
}
