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

package com.openexchange.drive.client.windows.osgi;

import java.rmi.Remote;
import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.service.http.HttpService;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.drive.BrandedDriveVersionService;
import com.openexchange.drive.client.windows.files.UpdateFilesProvider;
import com.openexchange.drive.client.windows.files.UpdateFilesProviderImpl;
import com.openexchange.drive.client.windows.service.Constants;
import com.openexchange.drive.client.windows.service.DriveUpdateService;
import com.openexchange.drive.client.windows.service.internal.BrandingConfigurationRemoteImpl;
import com.openexchange.drive.client.windows.service.internal.DriveUpdateServiceImpl;
import com.openexchange.drive.client.windows.service.internal.Services;
import com.openexchange.drive.client.windows.service.rmi.BrandingConfigurationRemote;
import com.openexchange.drive.client.windows.servlet.DownloadServlet;
import com.openexchange.drive.client.windows.servlet.InstallServlet;
import com.openexchange.drive.client.windows.servlet.UpdatesXMLServlet;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.osgi.service.http.HttpServices;
import com.openexchange.templating.TemplateService;
import com.openexchange.user.UserService;
import com.openexchange.userconf.UserConfigurationService;

/**
 *
 * {@link Activator}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.8.1
 */
public class Activator extends HousekeepingActivator {

    private String downloadServletAlias;
    private String updateServletAlias;
    private String installServletAlias;

    /**
     * Initializes a new {@link Activator}.
     */
    public Activator() {
        super();
    }

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class<?>[] { TemplateService.class, ConfigurationService.class, ContextService.class, UserService.class,
            UserConfigurationService.class, HttpService.class, CapabilityService.class, DispatcherPrefixService.class,
            ConfigViewFactory.class, BrandedDriveVersionService.class };
    }

    @Override
    protected synchronized void startBundle() throws Exception {
        Services.setServiceLookup(this);
        DriveUpdateService updateService = new DriveUpdateServiceImpl();

        //register files provider
        final ConfigurationService config = getService(ConfigurationService.class);
        String path = config.getProperty(Constants.BRANDINGS_PATH);
        UpdateFilesProvider fileProvider = UpdateFilesProviderImpl.getInstance().init(path);
        updateService.init(fileProvider);

        registerService(DriveUpdateService.class, updateService, null);

        //register download servlet
        DownloadServlet downloadServlet = new DownloadServlet(updateService, fileProvider);
        String prefix = getService(DispatcherPrefixService.class).getPrefix();
        downloadServletAlias = prefix + Constants.DOWNLOAD_SERVLET;
        getService(HttpService.class).registerServlet(downloadServletAlias, downloadServlet, null, null);

        //register update servlet
        updateServletAlias = prefix + Constants.UPDATE_SERVLET;
        final TemplateService templateService = getService(TemplateService.class);
        getService(HttpService.class).registerServlet(updateServletAlias, new UpdatesXMLServlet(templateService, updateService), null, null);

        //register install servlet
        installServletAlias = prefix + Constants.INSTALL_SERVLET;
        getService(HttpService.class).registerServlet(installServletAlias, new InstallServlet(updateService), null, null);

        //register rmi interface
        Dictionary<String, Object> props = new Hashtable<String, Object>(2);
        props.put("RMIName", BrandingConfigurationRemote.RMI_NAME);
        registerService(Remote.class, new BrandingConfigurationRemoteImpl(), props);
    }

    @Override
    protected synchronized void stopBundle() throws Exception {
        HttpService httpService = getService(HttpService.class);
        if (httpService != null) {
            if (downloadServletAlias != null) {
                HttpServices.unregister(downloadServletAlias, httpService);
                downloadServletAlias = null;
            }
            if (updateServletAlias != null) {
                HttpServices.unregister(updateServletAlias, httpService);
                updateServletAlias = null;
            }
            if (installServletAlias != null) {
                HttpServices.unregister(installServletAlias, httpService);
                installServletAlias = null;
            }
        }
        super.stopBundle();
        Services.setServiceLookup(null);
    }
}
