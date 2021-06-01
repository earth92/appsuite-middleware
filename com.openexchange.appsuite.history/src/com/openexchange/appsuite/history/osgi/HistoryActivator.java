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

package com.openexchange.appsuite.history.osgi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.apps.manifests.DefaultManifestBuilder;
import com.openexchange.apps.manifests.ManifestProvider;
import com.openexchange.appsuite.DefaultFileCache;
import com.openexchange.appsuite.FileCacheProvider;
import com.openexchange.appsuite.history.impl.HistoryFileCacheProvider;
import com.openexchange.appsuite.history.impl.HistoryManifestProvider;
import com.openexchange.appsuite.history.impl.HistoryUtil;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.ForcedReloadable;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.config.lean.DefaultProperty;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.config.lean.Property;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.osgi.HousekeepingActivator;
import com.openexchange.security.manager.SecurityManagerPropertyProvider;

/**
 * {@link HistoryActivator}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.10.4
 */
public class HistoryActivator extends HousekeepingActivator implements ForcedReloadable {

    private static final String COLON = ":";
    private static final String MANIFESTS = "/manifests";
    private static final Logger LOG = LoggerFactory.getLogger(HistoryActivator.class);
    private static final Property INSTALLED_APPSUITE_PROP = DefaultProperty.valueOf("com.openexchange.apps.path", null);
    private static final Property INSTALLED_MANIFEST_PROP = DefaultProperty.valueOf("com.openexchange.apps.manifestPath", null);
    private static final Property APPSUITE_PROP = DefaultProperty.valueOf("com.openexchange.apps.backupPath", "/var/opt/open-xchange/frontend/history/apps");
    private static final Property MANIFEST_PROP = DefaultProperty.valueOf("com.openexchange.apps.manifestBackupPath", "/var/opt/open-xchange/frontend/history/manifests");

    private HistoryFileCacheProvider appsuiteCacheProvider;
    private HistoryManifestProvider manifestProvider;

    private final boolean isWindows = Optional.ofNullable(System.getProperty("os.name")).orElse("").toLowerCase().startsWith("windows");

    @Override
    protected Class<?>[] getNeededServices() {
        return new Class[] { LeanConfigurationService.class };
    }

    @Override
    protected void startBundle() throws Exception {
        LeanConfigurationService configService = getServiceSafe(LeanConfigurationService.class);
        Dictionary<String, Object> dictionary = new Hashtable<String, Object>(1);
        dictionary.put(SecurityManagerPropertyProvider.PROPS_SERVICE_KEY, Strings.concat(",", new String[] { APPSUITE_PROP.getFQPropertyName(), MANIFEST_PROP.getFQPropertyName() }));
        registerService(SecurityManagerPropertyProvider.class, (property) -> {
            if (APPSUITE_PROP.getFQPropertyName().contentEquals(property)) {
                return Optional.of(configService.getProperty(APPSUITE_PROP));
            }
            if (MANIFEST_PROP.getFQPropertyName().contentEquals(property)) {
                return Optional.of(configService.getProperty(MANIFEST_PROP));
            }
            return Optional.empty();
        }, dictionary);
        init(configService);
        registerService(Reloadable.class, this);
    }

    /**
     * Initializes the histories as an async task
     *
     * @param configService The {@link LeanConfigurationService}
     * @throws OXException in case {@link LeanConfigurationService} couldn't be loaded
     */
    private void init(LeanConfigurationService configService) {
        new Thread(() -> {
            LOG.info("Checking history for {}", History.appsuite);
            try {
                checkHistory(configService, History.appsuite);
                LOG.info("Checking history for {}", History.manifest);
                checkHistory(configService, History.manifest);
                LOG.info("Created history successfully");
            } catch (IOException | SecurityException e) {
                LOG.info("Unable to create history: {}", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Checks the given history
     *
     * @param configService The {@link ConfigurationService}
     * @param history The {@link History} to check
     * @throws IOException
     */
    private synchronized void checkHistory(LeanConfigurationService configService, History history) throws IOException, SecurityException {
        String path;
        String installed;
        switch (history) {
            case appsuite:
                path = configService.getProperty(APPSUITE_PROP);
                installed = configService.getProperty(INSTALLED_APPSUITE_PROP);
                break;
            case manifest:
                path = configService.getProperty(MANIFEST_PROP);
                installed = configService.getProperty(INSTALLED_MANIFEST_PROP);
                if (installed == null) {
                    String tmp = configService.getProperty(INSTALLED_APPSUITE_PROP);
                    if (Strings.isEmpty(tmp)) {
                        break;
                    }
                    String[] split = Strings.splitByColon(tmp);
                    installed = isWindows && split.length >= 2 ? split[0] + COLON + split[1] + MANIFESTS : split[0] + MANIFESTS;
                }
                break;
            default:
                LOG.debug("Unknown history type: {}", history.name());
                // unknown
                return;
        }
        if (Strings.isEmpty(path) || Strings.isEmpty(installed)) {
            LOG.info("The path of either the installed or the history folder is not configured for history {}", history.name());
            return;
        }
        File historyFolder = new File(path);
        File installedFolder = getInstalledPath(installed, history);
        if (historyFolder.exists() == false || (installedFolder != null && installedFolder.exists() == false)) {
            LOG.info("Either history folder or installed folder doesn't exist for history {}", history.name());
            // History is deactivated
            return;
        }
        File currentVersionFile = new File(historyFolder, "/current/version.txt");
        File installedVersionFile = installedFolder == null ? null : new File(installedFolder, "version.txt");
        try {
            Optional<String> optCurrent = Optional.empty();
            if (currentVersionFile.exists()) {
                try (Stream<String> currentStream = Files.lines(currentVersionFile.toPath())) {
                    optCurrent = currentStream.findFirst();
                }
            }

            if (installedVersionFile != null && installedVersionFile.exists()) {
                Optional<String> installedVersion = HistoryUtil.readVersion(installedVersionFile.toPath());
                if (installedVersion.isPresent() == false) {
                    LOG.debug("Couldn't read version info from installation folder for history {}", history.name());
                    // Nothing to do
                    return;
                }
                // update history folder by copying/moving the required files
                HistoryUtil.handleVersions(historyFolder, installedFolder, installedVersion.get(), optCurrent);

                File previousVersionFile = new File(historyFolder, "/previous/version.txt");
                Optional<String> previous = previousVersionFile.exists() ? HistoryUtil.readVersion(previousVersionFile.toPath()) : Optional.empty();
                if (previous.isPresent()) {
                    File previousFolder = new File(historyFolder, "/previous");
                    // register provider
                    registerProvider(history, previousFolder, previous);
                }
            } else {
                // History is deactivated
                LOG.info("Can't find installation folder for history {}", history.name());
                return;
            }
        } catch (IOException e) {
            LOG.error("Error while copying history files", e);
            // History can't be activated
            throw e;
        }
    }

    /**
     * Registers the necessary providers
     *
     * @param history The current checked history
     * @param previousFolder The root folder containing the content of the previous version
     * @param previousVersion The optional version string of the previous version
     * @throws IOException in case of errors
     */
    private void registerProvider(History history, File previousFolder, Optional<String> previousVersion) throws IOException {
        switch (history) {
            case appsuite:
                if (appsuiteCacheProvider != null) {
                    unregisterService(FileCacheProvider.class);
                }
                appsuiteCacheProvider = new HistoryFileCacheProvider(previousVersion.get(), new DefaultFileCache(previousFolder));
                registerService(FileCacheProvider.class, appsuiteCacheProvider);
                break;
            case manifest:
                if (manifestProvider != null) {
                    unregisterService(ManifestProvider.class);
                }
                JSONArray manifests = readManifests(previousFolder);
                if (manifests != null) {
                    manifestProvider = new HistoryManifestProvider(previousVersion.get(), new DefaultManifestBuilder(manifests, null));
                    registerService(ManifestProvider.class, manifestProvider);
                }
                break;
            default:
                LOG.error("Unknown history type: {}", history.name());
                // Should never happen
                return;
        }
    }

    /**
     * Reads the manifest files
     *
     * @param root The manifest root folder
     * @return The manifests as an {@link JSONArray} or null
     */
    private JSONArray readManifests(File root) {
        if (root.exists() && root.isDirectory()) {
            File[] filesInDir = root.listFiles((file, name) -> name.contentEquals("version.txt") == false);
            if (null != filesInDir) {
                JSONArray manifests = new JSONArray();
                for (File f : filesInDir) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charsets.UTF_8))) {
                        JSONArray fileManifests = new JSONArray(r);
                        for (int i = 0, size = fileManifests.length(); i < size; i++) {
                            manifests.put(fileManifests.get(i));
                        }
                    } catch (Exception e) {
                        LOG.error("Unable to read history manifest files", e);
                    }
                }
                return manifests;
            }
        }
        return null;
    }

    /**
     * Parses the property and return the content folder of the first entry
     *
     * @param property The raw property value
     * @param history The history
     * @return The content folder or <code>null</code>
     */
    private File getInstalledPath(String property, History history) {
        String[] paths = Strings.splitByColon(property);
        return paths.length > 0 ? getContentFolder(new File(isWindows && paths.length >= 2 ? paths[0] + COLON + paths[1] : paths[0]), history) : null;
    }

    /**
     * Gets the content folder for the given history
     *
     * @param parent The parent folder
     * @param history The history
     * @return The folder containing the data for the given history
     */
    private File getContentFolder(File parent, History history) {
        switch (history) {
            default:
            case appsuite:
                return new File(parent, "apps");
            case manifest:
                if (parent.getName().equals("manifests")) {
                    return parent;
                }
                return new File(parent, "manifests");
        }
    }

    /**
     * {@link History} defines the different kinds of histories
     *
     * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
     * @since v7.10.4
     */
    private enum History {
        /**
         * The history of the AppSuite apps
         */
        appsuite,
        /**
         * The history of the manifest files
         */
        manifest;
    }

    @Override
    public Interests getInterests() {
        return Reloadables.getInterestsForAll();
    }

    @SuppressWarnings("unused")
    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        try {
            LeanConfigurationService leanConfigurationService = getServiceSafe(LeanConfigurationService.class);
            init(leanConfigurationService);
        } catch (OXException e) {
            LOG.error("Unable to reinit history", e);
        }
    }
}
