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

package com.openexchange.report.appsuite.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.config.ConfigurationService;
import com.openexchange.database.DatabaseService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;

/**
 * Contains static methods that collect information for the report client.
 *
 * @author <a href="mailto:anna.ottersbach@open-xchange.com">Anna Ottersbach</a>
 * @since v7.10.4
 */
public class ReportInformation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportInformation.class);

    private ReportInformation() {}

    /**
     * 
     * Gets the database version from connection meta data.
     *
     * @return The database version. Returns <code>null</code> if an error occurs.
     */
    public static String getDatabaseVersion() {
        Connection con = null;
        String version = null;
        DatabaseService service = Services.getService(DatabaseService.class);
        try {
            con = service.getReadOnly();
            DatabaseMetaData metaData = con.getMetaData();
            version = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (SQLException | OXException e) {
            LOGGER.warn("Unable to get database version: {}", e.getMessage());
        } finally {
            if (con != null) {
                service.backReadOnly(con);
            }
        }
        return version;
    }

    /**
     * 
     * Gets the name of the Linux distribution if possible.
     * Information is the PRETTY_NAME from /etc/os-release file.
     * Returns an empty String, if the operating system is not Linux or if PRETTY_NAME information cannot be found.
     *
     * @return The Linux distribution.
     */
    public static String getDistributionName() {
        if (isLinux() == false) {
            return "";
        }
        String distributionName = "";
        List<String> commandOutput = executeCommand("cat", "/etc/os-release");
        for (String line : commandOutput) {
            if (line.startsWith("PRETTY_NAME=")) {
                distributionName = line.replace("PRETTY_NAME=", "").replace("\"", "");
                break;
            }
        }
        return distributionName;
    }

    /**
     * 
     * Gets the installed open-xchange packages depending on the installed Linux distribution.
     * Returns an empty list, if no packages can be found.
     *
     * @return A list with the installed open-xchange packages.
     */
    public static List<String> getInstalledPackages() {
        List<String> commandResult = new ArrayList<>();
        String filterPattern = "'open-xchange|readerengine'";
        Distribution distribution = getDistribution();
        switch (distribution) {
            case DEBIAN:
                commandResult = executeCommand("/bin/sh", "-c", "apt --installed list | egrep " + filterPattern);
                break;
            case RHEL:
            case CENTOS:
            case AMAZONLINUX:
                commandResult = executeCommand("/bin/sh", "-c", "yum list installed | egrep " + filterPattern);
                break;
            case SLES:
                commandResult = executeCommand("/bin/sh", "-c", "zypper se -i | egrep " + filterPattern);
                break;
            case NOLINUX:
                LOGGER.warn("Installed packages not available; Operating system is not Linux");
                break;
            case UNKNOWN:
            default:
                LOGGER.warn("Installed packages not available; Linux distribution is unknown");
        }
        return formatPackageList(commandResult, distribution);
    }

    /**
     * 
     * Gets the via OAuth configured 3rd party APIs i.e. Dropbox, Twitter, XING.
     * Activation criteria are the OAuth properties for the particular API.
     * Returns an empty list, if no APIs can be found.
     *
     * @return The via OAuth configured 3rd party APIs in an list with {@link ThirdPartyAPI} enumeration.
     */
    public static List<ThirdPartyAPI> getConfiguredThirdPartyAPIsViaOAuth() {
        List<ThirdPartyAPI> configuredAPIs = new ArrayList<>();
        /*
         * Boxcom properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.BOXCOM.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.BOXCOM);
        }
        /*
         * Dropbox properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.DROPBOX.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.DROPBOX);
        }
        /*
         * Google properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.GOOGLE.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.GOOGLE);
        }
        /*
         * Microsoft properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.MICROSOFT.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.MICROSOFT);
        }
        /*
         * Twitter properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.TWITTER.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.TWITTER);
        }
        /*
         * Xing properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.XING.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.XING);
        }
        /*
         * Yahoo properties
         */
        if (isOAUthAPIConfigured(ThirdPartyAPI.YAHOO.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.YAHOO);
        }
        /*
         * Flickr properties
         */
        if (isSettingConfigured(ThirdPartyAPI.FLICKR.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.FLICKR);
        }
        /*
         * Tumblr properties
         */
        if (isSettingConfigured(ThirdPartyAPI.TUMBLR.getPropertyName())) {
            configuredAPIs.add(ThirdPartyAPI.TUMBLR);
        }

        return configuredAPIs;
    }

    /**
     * 
     * Gets the configured 3rd party APIs (not configured with OAuth) i.e. SchedJoules.
     * Returns an empty list, if no APIs can be found.
     *
     * @return The configured 3rd party APIs (not configured with OAuth) in an list with {@link ThirdPartyAPI} enumeration.
     */
    public static List<ThirdPartyAPI> getConfiguredThirdPartyAPIsNotOAuth() {
        ConfigurationService service = Services.getService(ConfigurationService.class);
        List<ThirdPartyAPI> activatedIntegrations = new ArrayList<>();
        /*
         * SchedJoules properties
         */
        String apiKey = service.getProperty(ThirdPartyAPI.SCHEDJOULES.getPropertyName() + ".apiKey");
        if (apiKey != null && !apiKey.isEmpty()) {
            activatedIntegrations.add(ThirdPartyAPI.SCHEDJOULES);
        }

        return activatedIntegrations;
    }

    //---------------------------------------------- Private helper methods -------------------------------------------------------

    private static List<String> executeCommand(String... commands) {
        List<String> results = null;
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        /*
         * Execute command
         */
        try {
            process = runtime.exec(commands);
        } catch (IOException e) {
            LOGGER.error("The command \"{}\" cannot be executed: {}", concatenateCommandParts(commands), e.getMessage());
            return results;
        }
        /*
         * Read output
         */
        // @formatter:off
        try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charsets.UTF_8));) {
            results = readCommandOutput(stdInput);
            logWarnings(stdError, commands);
        } catch (IOException | SecurityException e) {
            LOGGER.error("The command \"{}\" cannot be executed: {}", concatenateCommandParts(commands), e.getMessage());
        }
        // @formatter:on
        return results;
    }

    private static String concatenateCommandParts(String... commands) {
        StringBuilder command = new StringBuilder();
        for (String s : commands) {
            command.append(s + " ");
        }
        command.deleteCharAt(command.length() - 1);
        return command.toString();
    }

    private static List<String> readCommandOutput(BufferedReader stdInput) throws IOException {
        List<String> results;
        String str;
        results = new ArrayList<>();
        while ((str = stdInput.readLine()) != null) {
            results.add(str);
        }
        return results;
    }

    private static void logWarnings(BufferedReader stdError, String... commands) throws IOException {
        String str;
        StringBuilder errors = new StringBuilder();
        while ((str = stdError.readLine()) != null) {
            if (!str.isEmpty()) {
                errors.append(str).append("\n");
            }
        }
        if (errors.length() > 0) {
            LOGGER.warn("Error/Warning while executing command \"{}\": {}", concatenateCommandParts(commands), errors.toString());
        }
    }

    protected static boolean isLinux() {
        /*
         * Checks if the operation system is Linux
         */
        try {
            String propertyValue = System.getProperty("os.name");
            return propertyValue != null && propertyValue.contains("Linux");
        } catch (SecurityException e) {
            LOGGER.warn("Operating system name unavailable: {}", e.getMessage());
            return false;
        }
    }

    protected static Distribution getDistribution() {
        Distribution distribution;
        if (isLinux()) {
            /*
             * Get distribution name from os-release file
             */
            String distributionName = getDistributionName().toLowerCase();
            /*
             * Analyze the distribution name
             */
            if (distributionName.contains("debian")) {
                distribution = Distribution.DEBIAN;
            } else if (distributionName.contains("rhel") || distributionName.contains("red hat")) {
                distribution = Distribution.RHEL;
            } else if (distributionName.contains("sles") || distributionName.contains("suse")) {
                distribution = Distribution.SLES;
            } else if (distributionName.contains("amazon")) {
                distribution = Distribution.AMAZONLINUX;
            } else if (distributionName.contains("centos")) {
                distribution = Distribution.CENTOS;
            } else {
                distribution = Distribution.UNKNOWN;
            }
        } else {
            distribution = Distribution.NOLINUX;
        }
        return distribution;
    }

    protected static List<String> formatPackageList(List<String> commandOutput, Distribution distribution) {
        if (commandOutput != null && !commandOutput.isEmpty()) {
            switch (distribution) {
                /*
                 * Format output of yum command
                 */
                case RHEL:
                case AMAZONLINUX:
                case CENTOS:
                    return extractPackageNames(commandOutput, "\\.", 0);
                /*
                 * Format output of apt command
                 */
                case DEBIAN:
                    return extractPackageNames(commandOutput, "/", 0);
                /*
                 * Format output of zypper command
                 */
                case SLES:
                    return extractPackageNames(commandOutput, "\\s*\\|\\s*", 1);
                case NOLINUX:
                case UNKNOWN:
                default:
                    return commandOutput;
            }
        }
        return commandOutput;
    }

    private static List<String> extractPackageNames(List<String> commandOutput, String splitter, int position) {
        List<String> formattedOutput = new ArrayList<>();
        for (String line : commandOutput) {
            if (line.isEmpty() == false) {
                String[] pkg = line.split(splitter);
                formattedOutput.add((pkg != null && pkg.length > position) ? pkg[position] : line);
            }
        }
        return formattedOutput;
    }

    private static boolean isOAUthAPIConfigured(String apiProperty) {
        ConfigurationService service = Services.getService(ConfigurationService.class);
        String enabledValue = service.getProperty(apiProperty);
        String apiKey = service.getProperty(apiProperty + ".apiKey");
        String apiSecret = service.getProperty(apiProperty + ".apiSecret");
        // @formatter:off
        if (enabledValue != null && enabledValue.equals("true") &&
            apiKey != null && !apiKey.startsWith("REPLACE") && !apiKey.startsWith("INSERT") &&
            apiSecret != null && !apiSecret.startsWith("REPLACE") && !apiSecret.startsWith("INSERT")) {
            return true;
        }
        // @formatter:on
        return false;
    }

    private static boolean isSettingConfigured(String apiProperty) {
        ConfigurationService service = Services.getService(ConfigurationService.class);
        String propertyKey = service.getProperty(apiProperty);
        if (propertyKey != null && !propertyKey.isEmpty() && !propertyKey.startsWith("REPLACE") && !propertyKey.startsWith("INSERT")) {
            return true;
        }
        return false;
    }
}

/**
 * 
 * Represents the Linux distribution of the operating system.
 *
 * @author <a href="mailto:anna.ottersbach@open-xchange.com">Anna Ottersbach</a>
 * @since v7.10.4
 */
enum Distribution {
    DEBIAN,
    RHEL,
    SLES,
    AMAZONLINUX,
    CENTOS,
    UNKNOWN,
    NOLINUX
}

/**
 * 
 * Represents the possible 3rd party APIs that are configured via OAuth (see
 * <a href="https://documentation.open-xchange.com/7.10.3/middleware/3rd_party_integrations.html">Documentation 3rd Party Integrations</a>)
 * and via other ways (e.g. SchedJoules).
 *
 * @author <a href="mailto:anna.ottersbach@open-xchange.com">Anna Ottersbach</a>
 * @since v7.10.4
 */
enum ThirdPartyAPI {

    BOXCOM("Box.com", "com.openexchange.oauth.boxcom"),
    DROPBOX("Dropbox", "com.openexchange.oauth.dropbox"),
    GOOGLE("Google", "com.openexchange.oauth.google"),
    MICROSOFT("Microsoft", "com.openexchange.oauth.microsoft.graph"),
    TWITTER("Twitter", "com.openexchange.oauth.twitter"),
    XING("XING", "com.openexchange.oauth.xing"),
    YAHOO("yahoo", "com.openexchange.oauth.yahoo"),
    SCHEDJOULES("SchedJoules", "com.openexchange.calendar.schedjoules"),
    FLICKR("Flickr", "io.ox/portal//apiKeys/flickr"),
    TUMBLR("Tumblr", "io.ox/portal//apiKeys/tumblr");

    private String displayName;
    private String propertyName;

    private ThirdPartyAPI(String displayName, String propertyName) {
        this.displayName = displayName;
        this.propertyName = propertyName;
    }

    /**
     * 
     * Gets the display name of the 3rd party API.
     *
     * @return The display name of the 3rd party API.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the property name of the 3rd party API.
     *
     * @return The property name of the 3rd party API.
     */
    public String getPropertyName() {
        return propertyName;
    }
}
