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

package com.openexchange.client.onboarding.plist.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.container.ThresholdFileHolder;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.client.onboarding.BuiltInProvider;
import com.openexchange.client.onboarding.ClientDevice;
import com.openexchange.client.onboarding.Device;
import com.openexchange.client.onboarding.OnboardingExceptionCodes;
import com.openexchange.client.onboarding.OnboardingProvider;
import com.openexchange.client.onboarding.Scenario;
import com.openexchange.client.onboarding.download.DownloadLinkProvider;
import com.openexchange.client.onboarding.download.DownloadParameters;
import com.openexchange.client.onboarding.plist.OnboardingPlistProvider;
import com.openexchange.client.onboarding.plist.PListSigner;
import com.openexchange.client.onboarding.plist.PlistScenario;
import com.openexchange.client.onboarding.plist.PlistScenarioType;
import com.openexchange.client.onboarding.plist.PlistUtility;
import com.openexchange.client.onboarding.plist.osgi.Services;
import com.openexchange.client.onboarding.service.OnboardingService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.notify.hostname.HostnameService;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.plist.PListDict;
import com.openexchange.plist.PListWriter;
import com.openexchange.server.ServiceLookup;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.webdav.WebDavServlet;

/**
 * {@link PListDownloadServlet}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.1
 */
public class PListDownloadServlet extends WebDavServlet {

    private static final long serialVersionUID = -175037413514512006L;

    private static final Logger LOG = LoggerFactory.getLogger(PListDownloadServlet.class);

    /** The servlet path */
    public static final String SERVLET_PATH = "plist";

    private final transient ServiceLookup lookup;

    /**
     * Initializes a new {@link PListDownloadServlet}.
     */
    public PListDownloadServlet(ServiceLookup lookup) {
        super();
        this.lookup = lookup;

    }

    @SuppressWarnings("resource")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        InputStream filestream = null;
        ThresholdFileHolder fileHolder = null;
        try {
            int fileSize = -1;

            String fileName = req.getPathInfo();
            if (fileName == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("text/plain");
                resp.getWriter().println("No file name was given.");
                return;
            }

            if (fileName.startsWith("/")) {
                fileName = fileName.substring(1, fileName.length());
            }

            DownloadLinkProvider downloadLinkProvider = lookup.getService(DownloadLinkProvider.class);
            DownloadParameters parameters;
            try {
                parameters = downloadLinkProvider.getParameter(req.getPathInfo());
            } catch (OXException e) {
                LOG.error("", e);
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            String scenarioId = parameters.getScenarioId();
            Device device = Device.deviceFor(parameters.getDeviceId());
            int userId = parameters.getUserId();
            int contextId = parameters.getContextId();

            try {
                if (false == downloadLinkProvider.validateChallenge(userId, contextId, scenarioId, device.getId(), parameters.getChallenge())) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            } catch (OXException e) {
                LOG.error("", e);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            Scenario scenario = null;
            PListDict plist = null;
            try {
                OnboardingService onboardingService = lookup.getService(OnboardingService.class);
                try {
                    scenario = onboardingService.getScenario(scenarioId, ClientDevice.IMPLIES_ALL, device, userId, contextId);
                }
                catch (OXException e) {
                    if (OnboardingExceptionCodes.NO_SUCH_SCENARIO.equals(e)) {
                        scenario = getDirectDownloadScenario(scenarioId, onboardingService);
                    } else {
                        throw e;
                    }
                }

                String hostName = determineHostName(req, userId, contextId);

                for (OnboardingProvider provider : scenario.getProviders(userId, contextId)) {
                    if (provider instanceof OnboardingPlistProvider) {
                        plist = ((OnboardingPlistProvider) provider).getPlist(plist, scenario, hostName, userId, contextId);
                    }
                }
            } catch (OXException e) {
                LOG.error("", e);
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (plist == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            fileHolder = new ThresholdFileHolder();
            fileHolder.setDisposition("attachment");
            fileHolder.setName(scenario.getId() + ".mobileconfig");
            fileHolder.setContentType("application/x-apple-aspen-config");// Or application/x-plist ?
            fileHolder.setDelivery("download");
            new PListWriter().write(plist, fileHolder.asOutputStream());

            // Sign it
            try {
                fileHolder = sign(fileHolder, userId, contextId);
                filestream = fileHolder.getClosingStream();
            } catch (OXException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                LOG.error(e.getMessage());
                return;
            }
            fileSize = (int) fileHolder.getLength();
            if (filestream == null || fileSize == 0) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/octet-stream");
            resp.setContentLength(fileSize);
            resp.setHeader("Content-disposition", "attachment; filename=\"" + scenarioId + ".mobileconfig\"");
            Tools.removeCachingHeader(resp);

            OutputStream out = resp.getOutputStream();
            byte[] buf = new byte[4096];
            for (int read; (read = filestream.read(buf)) > 0;) {
                out.write(buf, 0, read);
            }
            out.flush();
            filestream.close();
        } finally {
            Streams.close(filestream, fileHolder);
        }
    }

    private Scenario getDirectDownloadScenario(String sTypes, OnboardingService onboardingService) throws OXException {
        String[] types = Strings.splitByComma(sTypes);

        // Determine suitable providers
        Map<String, OnboardingPlistProvider> onboardingProviders = new LinkedHashMap<>(types.length);
        for (String type : types) {
            Optional<PlistScenarioType> optionalScenarioType = PlistScenarioType.plistScenarioTypeFor(type);
            if (optionalScenarioType.isPresent()) {
                switch (optionalScenarioType.get()) {
                    case CALDAV:
                        PlistUtility.putPlistProviderById(BuiltInProvider.CALDAV, onboardingProviders, onboardingService);
                        break;
                    case CARDDAV:
                        PlistUtility.putPlistProviderById(BuiltInProvider.CARDDAV, onboardingProviders, onboardingService);
                        break;
                    case DAV:
                        PlistUtility.putPlistProviderById(BuiltInProvider.CALDAV, onboardingProviders, onboardingService);
                        PlistUtility.putPlistProviderById(BuiltInProvider.CARDDAV, onboardingProviders, onboardingService);
                        break;
                    case MAIL:
                        PlistUtility.putPlistProviderById(BuiltInProvider.MAIL, onboardingProviders, onboardingService);
                        break;
                    default:
                        throw OnboardingExceptionCodes.NO_SUCH_SCENARIO.create(type);

                }
            } else {
                Optional<OnboardingPlistProvider> optionalProvider = PlistUtility.lookUpPlistProviderById(type, onboardingService);
                if (!optionalScenarioType.isPresent()) {
                    throw OnboardingExceptionCodes.NOT_FOUND.create(type);
                }

                onboardingProviders.put(type, optionalProvider.get());
            }
        }

        return PlistScenario.newInstance(sTypes, new ArrayList<>(onboardingProviders.values()));
    }

    private String determineHostName(final HttpServletRequest req, int userId, int contextId) {
        String hostName = null;

        {
            HostnameService hostnameService = Services.optService(HostnameService.class);
            if (null != hostnameService) {
                hostName = hostnameService.getHostname(userId, contextId);
            }
        }

        // Get from request
        if (Strings.isEmpty(hostName)) {
            hostName = req.getServerName();
        }

        // Get from java
        if (Strings.isEmpty(hostName)) {
            try {
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (@SuppressWarnings("unused") UnknownHostException e) {
                // ignore
            }
        }

        // Fall back to localhost as last resort
        if (Strings.isEmpty(hostName)) {
            hostName = "localhost";
        }

        return hostName;
    }

    private ThresholdFileHolder sign(ThresholdFileHolder fileHolder, int userId, int contextId) throws OXException, IOException {
        PListSigner signer = lookup.getService(PListSigner.class);
        IFileHolder signed = signer.signPList(fileHolder, userId, contextId);

        if (signed instanceof ThresholdFileHolder) {
            return (ThresholdFileHolder) signed;
        }

        ThresholdFileHolder tfh = new ThresholdFileHolder(signed);
        signed.close();
        return tfh;
    }
}
