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

package com.openexchange.config.internal;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.collect.Iterators;
import com.openexchange.annotation.NonNull;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.ConfigurationServices;
import com.openexchange.config.Filter;
import com.openexchange.config.ForcedReloadable;
import com.openexchange.config.Interests;
import com.openexchange.config.PropertyFilter;
import com.openexchange.config.Reloadable;
import com.openexchange.config.Reloadables;
import com.openexchange.config.WildcardFilter;
import com.openexchange.config.cascade.ReinitializableConfigProviderService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Reference;
import com.openexchange.java.Strings;
import com.openexchange.startup.StaticSignalStartedService;
import com.openexchange.startup.StaticSignalStartedService.State;

/**
 * {@link ConfigurationImpl}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class ConfigurationImpl implements ConfigurationService {

    /** The logger constant. */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ConfigurationImpl.class);

    private static final class PropertyFileFilter implements FileFilter {

        private final String ext;
        private final String mpasswd;

        PropertyFileFilter() {
            super();
            ext = ".properties";
            mpasswd = "mpasswd";
        }

        @Override
        public boolean accept(final File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }
            String name = pathname.getName();
            return name.toLowerCase(Locale.US).endsWith(ext) || mpasswd.equals(name);
        }

    }

    private static final String[] getDirectories() {
        // Collect "openexchange.propdir" system properties
        List<String> properties = new ArrayList<String>(4);
        properties.add(System.getProperty("openexchange.propdir"));
        boolean checkNext;
        int i = 2;
        do {
            checkNext = false;
            String sysProp = System.getProperty(new StringBuilder("openexchange.propdir").append(i++).toString());
            if (null != sysProp) {
                properties.add(sysProp);
                checkNext = true;
            }
        } while (checkNext);

        return properties.toArray(new String[properties.size()]);
    }

    private static interface FileNameMatcher {

        boolean matches(String filename, File file);
    }

    private static final FileNameMatcher PATH_MATCHER = new FileNameMatcher() {

        @Override
        public boolean matches(String filename, File file) {
            return file.getPath().endsWith(filename);
        }
    };

    private static final FileNameMatcher NAME_MATCHER = new FileNameMatcher() {

        @Override
        public boolean matches(String filename, File file) {
            return file.getName().equals(filename);
        }
    };

    private static final AtomicReference<ConfigurationImpl> CONFIG_REFERENCE = new AtomicReference<>(null);

    /**
     * Sets the config reference to the initialized instance to provide static access
     *
     * @param config The initialized config instance
     */
    public static void setConfigReference(ConfigurationImpl config) {
        CONFIG_REFERENCE.set(config);
    }

    /**
     * Gets the config reference for the initialized instance to provide static access
     *
     * @return The initialized config instance or <code>null</code>
     */
    public static ConfigurationImpl getConfigReference() {
        return CONFIG_REFERENCE.get();
    }

    private static final Object PRESENT = new Object();

    /*-
     * -------------------------------------------------- Member stuff ---------------------------------------------------------
     */

    /** The <code>ForcedReloadable</code> services. */
    private final List<ForcedReloadable> forcedReloadables;

    /** The <code>Reloadable</code> services in this list match all properties. */
    private final List<Reloadable> matchingAllProperties;

    /**
     * This is a map for exact property name matches. The key is the topic,
     * the value is a list of <code>Reloadable</code> services.
     */
    private final Map<String, List<Reloadable>> matchingProperty;

    /**
     * This is a map for wild-card property names. The key is the prefix of the property name,
     * the value is a list of <code>Reloadable</code> services
     */
    private final Map<String, List<Reloadable>> matchingPrefixProperty;

    /**
     * This is a map for file names. The key is the file name,
     * the value is a list of <code>Reloadable</code> services
     */
    private final Map<String, List<Reloadable>> matchingFile;

    private final Map<String, String> texts;

    private final File[] dirs;

    /** The set to track such properties that were replaced by a system environment variable. */
    private final ConcurrentMap<String, Object> sysEnvPropertyHits;

    /** The set to track such properties that were looked-up by a system environment variable, but there ain't one. */
    private final ConcurrentMap<String, Object> sysEnvPropertyMisses;

    /** Maps file paths of the .properties file to their properties. */
    private final Map<File, Properties> propertiesByFile;

    /** Maps property names to their values. */
    private final Map<String, String> properties;

    /** Maps property names to the file path of the .properties file containing the property. */
    private final Map<String, String> propertiesFiles;

    /** Maps objects to YAML file name, with a path */
    private final Map<File, YamlRef> yamlFiles;

    /** Maps filenames to whole file paths for yaml lookup */
    private final Map<String, File> yamlPaths;

    private final Map<File, byte[]> xmlFiles;

    /** The <code>ConfigProviderServiceImpl</code> reference. */
    private volatile ConfigProviderServiceImpl configProviderServiceImpl;

    private final Collection<ReinitializableConfigProviderService> reinitQueue;

    /**
     * Initializes a new configuration. The properties directory is determined by system property "<code>openexchange.propdir</code>"
     */
    public ConfigurationImpl(Collection<ReinitializableConfigProviderService> reinitQueue) {
        this(getDirectories(), reinitQueue);
    }

    /**
     * Initializes a new configuration
     *
     * @param directory The directory where property files are located
     */
    public ConfigurationImpl(String[] directories, Collection<ReinitializableConfigProviderService> reinitQueue) {
        super();
        this.reinitQueue = null == reinitQueue ? Collections.<ReinitializableConfigProviderService> emptyList() : reinitQueue;

        // Start with empty collections
        this.forcedReloadables = new CopyOnWriteArrayList<ForcedReloadable>();
        this.matchingAllProperties = new CopyOnWriteArrayList<Reloadable>();
        this.matchingProperty = new ConcurrentHashMap<String, List<Reloadable>>(128, 0.9f, 1);
        this.matchingPrefixProperty = new ConcurrentHashMap<String, List<Reloadable>>(128, 0.9f, 1);
        this.matchingFile = new ConcurrentHashMap<String, List<Reloadable>>(128, 0.9f, 1);

        sysEnvPropertyHits = new ConcurrentHashMap<>(16, 0.9f, 1);
        sysEnvPropertyMisses = new ConcurrentHashMap<>(16, 0.9f, 1);
        propertiesByFile = new HashMap<File, Properties>(256);
        texts = new ConcurrentHashMap<String, String>(1024, 0.9f, 1);
        properties = new ConcurrentHashMap<String, String>(2048, 0.9f, 1);
        propertiesFiles = new HashMap<String, String>(2048);
        yamlFiles = new HashMap<File, YamlRef>(64);
        yamlPaths = new HashMap<String, File>(64);
        dirs = new File[directories.length];
        xmlFiles = new HashMap<File, byte[]>(2048);
        loadConfiguration(directories);
    }

    private void loadConfiguration(String[] directories) {
        if (null == directories || directories.length == 0) {
            throw new IllegalArgumentException("Missing configuration directory path.");
        }

        // First filter+processor pair for .properties files & sub-directories
        FileFilter fileFilter = new PropertyFileFilter();
        FileProcessor processor = new FileProcessor() {

            private final boolean debug = LOG.isDebugEnabled();

            @Override
            public void processFile(final File file) {
                processPropertiesFile(file, debug);
            }

        };

        // Second filter+processor pair for YAML files & sub-directories
        FileFilter fileFilter2 = new FileFilter() {

            @Override
            public boolean accept(final File pathname) {
                if (pathname.isDirectory()) {
                    return true;
                }
                String name = pathname.getName();
                return name.endsWith(".yml") || name.endsWith(".yaml");
            }

        };

        final Map<String, File> yamlPaths = this.yamlPaths;
        final Map<File, YamlRef> yamlFiles = this.yamlFiles;
        FileProcessor processor2 = new FileProcessor() {

            @Override
            public void processFile(final File file) {
                yamlPaths.put(file.getName(), file);
                yamlFiles.put(file, new YamlRef(file));
            }

        };

        // Third filter+processor pair for XML files
        FileFilter fileFilter3 = new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".xml");
            }
        };

        final Map<File, byte[]> xmlFiles = this.xmlFiles;
        FileProcessor processor3 = new FileProcessor() {

            @Override
            public void processFile(File file) {
                try {
                    byte[] hash = ConfigurationServices.getHash(file);
                    xmlFiles.put(file, hash);
                } catch (IllegalStateException e) {
                    Throwable cause = e.getCause();
                    String message = "Failed to load XML file '" + file + "'. Reason: " + (null == cause ? e.getMessage() : cause.getMessage());
                    StaticSignalStartedService.getInstance().setState(State.INVALID_CONFIGURATION, e, message);
                    throw e;
                }
            }
        };

        for (int i = 0; i < directories.length; i++) {
            if (null == directories[i]) {
                throw new IllegalArgumentException("Given configuration directory path is null.");
            }
            final File dir = new File(directories[i]);
            dirs[i] = dir;
            if (!dir.exists()) {
                throw new IllegalArgumentException(MessageFormat.format("Not found: \"{0}\".", directories[i]));
            } else if (!dir.isDirectory()) {
                throw new IllegalArgumentException(MessageFormat.format("Not a directory: {0}", directories[i]));
            }
            // Process: First round
            processDirectory(dir, fileFilter, processor);
            // Process: Second round
            processDirectory(dir, fileFilter2, processor2);
            // Process: Third round
            processDirectory(dir, fileFilter3, processor3);
        }
    }

    private synchronized void processDirectory(final File dir, final FileFilter fileFilter, final FileProcessor processor) {
        File[] files = dir.listFiles(fileFilter);
        if (files == null) {
            LOG.info("Cannot read {}. Skipping.", dir);
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file, fileFilter, processor);
            } else {
                /**
                 * Preparations for US: 55795476 Change configuration values without restarting the systems final FileWatcher fileWatcher =
                 * FileWatcher.getFileWatcher(file); fileWatcher.addFileListener(new ProcessingFileListener(file, processor));
                 */
                processor.processFile(file);
            }
        }
    }

    void processPropertiesFile(File propFile, boolean debug) {
        try {
            if (!propFile.exists()) {
                return;
            }
            if (!propFile.canRead()) {
                throw new IOException("No read permission");
            }

            Reference<Set<String>> sysEnvPropertiesReference = new Reference<>(null);
            Properties tmp = ConfigurationServices.loadPropertiesFrom(propFile, true, sysEnvPropertiesReference);
            propertiesByFile.put(propFile, tmp);
            addToTrackedSysEnvVariables(sysEnvPropertiesReference);

            String propFilePath = propFile.getPath();
            for (Map.Entry<Object, Object> e : tmp.entrySet()) {
                String propName = e.getKey().toString().trim();
                if (debug) {
                    String otherValue = properties.get(propName);
                    if (otherValue != null && !otherValue.equals(e.getValue())) {
                        String otherFile = propertiesFiles.get(propName);
                        LOG.debug("Overwriting property {} from file ''{}'' with property from file ''{}'', overwriting value ''{}'' with value ''{}''", propName, otherFile, propFilePath, otherValue, e.getValue());
                    }
                }
                properties.put(propName, e.getValue().toString().trim());
                propertiesFiles.put(propName, propFilePath);
            }
        } catch (IOException e) {
            LOG.warn("An I/O error occurred while processing .properties file \"{}\".", propFile, e);
        } catch (IllegalArgumentException encodingError) {
            LOG.warn("A malformed Unicode escape sequence in .properties file \"{}\".", propFile, encodingError);
        } catch (RuntimeException e) {
            LOG.warn("An error occurred while processing .properties file \"{}\".", propFile, e);
        }
    }

    private void addToTrackedSysEnvVariables(Reference<Set<String>> sysEnvPropertiesReference) {
        Set<String> sysEnvProperties = sysEnvPropertiesReference.getValue();
        if (sysEnvProperties == null || sysEnvProperties.isEmpty()) {
            return;
        }

        for (String sysEnvProperty : sysEnvProperties) {
            this.sysEnvPropertyHits.putIfAbsent(sysEnvProperty, PRESENT);
        }
    }

    @Override
    public Filter getFilterFromProperty(final String name) {
        String value = getProperty(name);
        return null == value ? null : new WildcardFilter(value);
    }

    @Override
    public String getProperty(final String name) {
        return getProperty(name, null);
    }

    @Override
    public String getProperty(final String name, final String defaultValue) {
        String value = properties.get(name);
        if (value == null) {
            if (sysEnvPropertyMisses.containsKey(name) == false) {
                value = ConfigurationServices.checkForSysEnvVariable(name, null);
                if (value == null) {
                    // No such system environment variable
                    sysEnvPropertyMisses.put(name, PRESENT);
                } else {
                    // Found appropriate system environment variable. Store it.
                    properties.putIfAbsent(name, value);
                    sysEnvPropertyHits.put(name, PRESENT);
                }
            }
        }
        return null == value ? defaultValue : value;
    }

    @Override
    public List<String> getProperty(String name, String defaultValue, String separator) {
        String property = getProperty(name, defaultValue);
        return Strings.splitAndTrim(property, separator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Properties getFile(final String filename) {
        if (null == filename) {
            return new Properties();
        }

        boolean isPath = filename.indexOf(File.separatorChar) >= 0;
        FileNameMatcher matcher = isPath ? PATH_MATCHER : NAME_MATCHER;

        for (Map.Entry<File, Properties> entry : propertiesByFile.entrySet()) {
            if (matcher.matches(filename, entry.getKey())) {
                Properties retval = new Properties();
                retval.putAll(entry.getValue());
                return retval;
            }
        }

        return new Properties();
    }

    @Override
    public Set<String> getSysEnvProperties() {
        return Collections.unmodifiableSet(sysEnvPropertyHits.keySet());
    }

    @Override
    public Map<String, String> getProperties(final PropertyFilter filter) throws OXException {
        if (null == filter) {
            return new HashMap<String, String>(properties);
        }
        final Map<String, String> ret = new LinkedHashMap<String, String>(32);
        for (final Entry<String, String> entry : this.properties.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (filter.accept(key, value)) {
                ret.put(key, value);
            }
        }
        return ret;
    }

    @Override
    public Properties getPropertiesInFolder(final String folderName) {
        Properties retval = new Properties();
        for (File dir : dirs) {
            String fldName = dir.getAbsolutePath() + File.separatorChar + folderName + File.separatorChar;
            for (Iterator<Entry<String, String>> iter = propertiesFiles.entrySet().iterator(); iter.hasNext();) {
                Map.Entry<String, String> entry = iter.next();
                if (entry.getValue().startsWith(fldName)) {
                    String value = getProperty(entry.getKey());
                    retval.put(entry.getKey(), value);
                }
            }
        }
        return retval;
    }

    @Override
    public boolean getBoolProperty(final String name, final boolean defaultValue) {
        final String prop = getProperty(name);
        return null == prop ? defaultValue : Boolean.parseBoolean(prop.trim());
    }

    @Override
    public int getIntProperty(final String name, final int defaultValue) {
        final String prop = getProperty(name);
        if (prop != null) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException e) {
                LOG.trace("", e);
            }
        }
        return defaultValue;
    }

    @Override
    public Iterator<String> propertyNames() {
        return Iterators.unmodifiableIterator(properties.keySet().iterator());
    }

    @Override
    public int size() {
        return properties.size();
    }

    @Override
    public File getFileByName(final String fileName) {
        return doGetFileByName(fileName);
    }

    /**
     * Gets the file denoted by given file name.
     *
     * @param fileName The file name
     * @return The file or <code>null</code>
     */
    public static File doGetFileByName(final String fileName) {
        if (null == fileName) {
            return null;
        }
        for (final String dir : getDirectories()) {
            final File f = traverseForFile(new File(dir), fileName);
            if (f != null) {
                return f;
            }
        }
        /*
         * Try guessing the filename separator
         */
        String fn;
        int pos;
        if ((pos = fileName.lastIndexOf('/')) >= 0 || (pos = fileName.lastIndexOf('\\')) >= 0) {
            fn = fileName.substring(pos + 1);
        } else {
            LOG.warn("No such file: {}", fileName);
            return null;
        }
        for (final String dir : getDirectories()) {
            final File f = traverseForFile(new File(dir), fn);
            if (f != null) {
                return f;
            }
        }
        LOG.warn("No such file: {}", fileName);
        return null;
    }

    private static File traverseForFile(final File file, final String fileName) {
        if (null == file) {
            return null;
        }
        if (file.isFile()) {
            if (fileName.equals(file.getName())) {
                // Found
                return file;
            }
            return null;
        }
        final File[] subs = file.listFiles();
        if (subs != null) {
            for (final File sub : subs) {
                final File f = traverseForFile(sub, fileName);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    @Override
    public File getDirectory(final String directoryName) {
        if (null == directoryName) {
            return null;
        }
        for (final String dir : getDirectories()) {
            final File fdir = traverseForDir(new File(dir), directoryName);
            if (fdir != null) {
                return fdir;
            }
        }
        LOG.warn("No such directory: {}", directoryName);
        return null;
    }

    private File traverseForDir(final File file, final String directoryName) {
        if (null == file) {
            return null;
        }
        if (file.isDirectory() && directoryName.equals(file.getName())) {
            // Found
            return file;
        }
        final File[] subDirs = file.listFiles(new FileFilter() {

            @Override
            public boolean accept(final File file) {
                return file.isDirectory();
            }
        });
        if (subDirs != null) {
            // Check first-level sub-directories first
            for (final File subDir : subDirs) {
                if (subDir.isDirectory() && directoryName.equals(subDir.getName())) {
                    return subDir;
                }
            }
            // Then check recursively
            for (final File subDir : subDirs) {
                final File dir = traverseForDir(subDir, directoryName);
                if (dir != null) {
                    return dir;
                }
            }
        }
        return null;
    }

    @Override
    public String getText(final String fileName) {
        String text = texts.get(fileName);
        if (text != null) {
            return text;
        }

        for (String dir : getDirectories()) {
            String s = traverse(new File(dir), fileName);
            if (s != null) {
                texts.put(fileName, s);
                return s;
            }
        }
        return null;
    }

    private String traverse(final File file, final String filename) {
        if (null == file) {
            return null;
        }

        if (file.isFile()) {
            if (file.getName().equals(filename)) {
                try {
                    return ConfigurationServices.readFile(file);
                } catch (IOException e) {
                    LOG.error("Can't read file: {}", file, e);
                    return null;
                }
            }
            return null;
        }

        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                String s = traverse(f, filename);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public Object getYaml(final String filename) {
        if (null == filename) {
            return null;
        }

        boolean isPath = filename.indexOf(File.separatorChar) >= 0;
        if (isPath) {
            FileNameMatcher matcher = PATH_MATCHER;
            for (Map.Entry<File, YamlRef> entry : yamlFiles.entrySet()) {
                if (matcher.matches(filename, entry.getKey())) {
                    return entry.getValue().getValue();
                }
            }

            // No such YAML file
            return null;
        }

        // Look-up by file name
        File path = yamlPaths.get(filename);
        if (path == null) {
            path = yamlPaths.get(filename + ".yml");
            if (path == null) {
                path = yamlPaths.get(filename + ".yaml");
                if (path == null) {
                    return null;
                }
            }
        }

        return yamlFiles.get(path).getValue();
    }

    @Override
    public Map<String, Object> getYamlInFolder(final String folderName) {
        final Map<String, Object> retval = new HashMap<String, Object>();
        final Iterator<Entry<File, YamlRef>> iter = yamlFiles.entrySet().iterator();
        String fldName = folderName;
        for (final File dir : dirs) {
            fldName = dir.getAbsolutePath() + File.separatorChar + fldName + File.separatorChar;
            while (iter.hasNext()) {
                final Entry<File, YamlRef> entry = iter.next();
                String pathName = entry.getKey().getPath();
                if (pathName.startsWith(fldName)) {
                    retval.put(pathName, entry.getValue().getValue());
                }
            }
        }
        return retval;
    }

    /**
     * Propagates the reloaded configuration among registered listeners.
     */
    public void reloadConfiguration() {
        LOG.info("Reloading configuration...");

        // Copy current content to get associated files on check for expired PropertyWatchers
        final Map<File, Properties> oldPropertiesByFile = new HashMap<File, Properties>(propertiesByFile);
        final Map<File, byte[]> oldXml = new HashMap<File, byte[]>(xmlFiles);
        final Map<File, YamlRef> oldYaml = new HashMap<File, YamlRef>(yamlFiles);

        // Clear maps
        properties.clear();
        propertiesByFile.clear();
        propertiesFiles.clear();
        sysEnvPropertyHits.clear();
        sysEnvPropertyMisses.clear();
        texts.clear();
        yamlFiles.clear();
        yamlPaths.clear();
        xmlFiles.clear();
        com.openexchange.config.utils.FileVariablesProvider.clearInstances();

        // (Re-)load configuration
        loadConfiguration(getDirectories());

        // Re-initialize config-cascade
        reinitConfigCascade();

        // Check if properties have been changed, execute only forced ones if not
        Set<String> namesOfChangedProperties = new HashSet<>(512);
        Set<File> changes = getChanges(oldPropertiesByFile, oldXml, oldYaml, namesOfChangedProperties);

        Set<Reloadable> toTrigger = new LinkedHashSet<>(32);

        // Collect forced ones
        for (ForcedReloadable reloadable : forcedReloadables) {
            toTrigger.add(reloadable);
        }

        if (!changes.isEmpty()) {
            // Continue to reload
            LOG.info("Detected changes in the following configuration files: {}", changes);

            // Collect the ones interested in all
            for (Reloadable reloadable : matchingAllProperties) {
                toTrigger.add(reloadable);
            }

            // Collect the ones interested in properties
            for (String nameOfChangedProperties : namesOfChangedProperties) {
                // Now check for prefix matches
                if (!matchingPrefixProperty.isEmpty()) {
                    int pos = nameOfChangedProperties.lastIndexOf('.');
                    while (pos > 0) {
                        String prefix = nameOfChangedProperties.substring(0, pos);
                        List<Reloadable> interestedReloadables = matchingPrefixProperty.get(prefix);
                        if (null != interestedReloadables) {
                            toTrigger.addAll(interestedReloadables);
                        }
                        pos = prefix.lastIndexOf('.');
                    }
                }

                // Add the subscriptions for matching topic names
                {
                    List<Reloadable> interestedReloadables = matchingProperty.get(nameOfChangedProperties);
                    if (null != interestedReloadables) {
                        toTrigger.addAll(interestedReloadables);
                    }
                }
            }

            // Collect the ones interested in files
            for (File file : changes) {
                List<Reloadable> interestedReloadables = matchingFile.get(file.getName());
                if (null != interestedReloadables) {
                    toTrigger.addAll(interestedReloadables);
                }
            }
        } else {
            LOG.info("No changes in *.properties, *.xml, or *.yaml configuration files detected");
        }

        // Trigger collected ones
        for (Reloadable reloadable : toTrigger) {
            try {
                reloadable.reloadConfiguration(this);
            } catch (Exception e) {
                LOG.warn("Failed to let reloaded configuration be handled by: {}", reloadable.getClass().getName(), e);
            }
        }

        // Check for expired PropertyWatchers
        /*-
         *
        for (final PropertyWatcher watcher : watchers.values()) {
            final String propertyName = watcher.getName();
            if (!properties.containsKey(propertyName)) {
                final PropertyWatcher removedWatcher = PropertyWatcher.removePropertWatcher(propertyName);
                final FileWatcher fileWatcher = FileWatcher.optFileWatcher(new File(propertiesFilesCopy.get(propertyName)));
                if (null != fileWatcher) {
                    fileWatcher.removeFileListener(removedWatcher);
                }
            }
        }
         *
         */
    }

    /**
     * Propagates the reloaded configuration among registered listeners for certain properties.
     *
     * @param propertyNames The names of the properties that have been changed
     */
    public void reloadConfigurationFor(String... propertyNames) {
        if (null == propertyNames || propertyNames.length <= 0) {
            return;
        }

        LOG.info("Propagating change for the following configuration properties: {}", Arrays.toString(propertyNames));

        Set<Reloadable> toTrigger = new LinkedHashSet<>(32);

        // Collect forced ones
        for (ForcedReloadable reloadable : forcedReloadables) {
            toTrigger.add(reloadable);
        }

        // Collect the ones interested in all
        for (Reloadable reloadable : matchingAllProperties) {
            toTrigger.add(reloadable);
        }

        // Collect the ones interested in properties
        for (String propertyName : propertyNames) {
            // Now check for prefix matches
            if (!matchingPrefixProperty.isEmpty()) {
                int pos = propertyName.lastIndexOf('.');
                while (pos > 0) {
                    String prefix = propertyName.substring(0, pos);
                    List<Reloadable> interestedReloadables = matchingPrefixProperty.get(prefix);
                    if (null != interestedReloadables) {
                        toTrigger.addAll(interestedReloadables);
                    }
                    pos = prefix.lastIndexOf('.');
                }
            }

            // Add the subscriptions for matching topic names
            {
                List<Reloadable> interestedReloadables = matchingProperty.get(propertyName);
                if (null != interestedReloadables) {
                    toTrigger.addAll(interestedReloadables);
                }
            }
        }

        // Trigger collected ones
        for (Reloadable reloadable : toTrigger) {
            try {
                reloadable.reloadConfiguration(this);
            } catch (Exception e) {
                LOG.warn("Failed to let reloaded configuration be handled by: {}", reloadable.getClass().getName(), e);
            }
        }
    }

    private boolean isInterestedInAll(String[] propertiesOfInterest) {
        if (null == propertiesOfInterest || 0 == propertiesOfInterest.length) {
            // Reloadable does not indicate the properties of interest
            return true;
        }

        for (String poi : propertiesOfInterest) {
            if ("*".equals(poi)) {
                return true;
            }
        }

        return false;
    }

    private void reinitConfigCascade() {
        ConfigProviderServiceImpl configProvider = this.configProviderServiceImpl;
        boolean reinitMyProvider = true;

        for (ReinitializableConfigProviderService reinit : reinitQueue) {
            if (reinit == configProvider) {
                reinitMyProvider = false;
            }
            try {
                reinit.reinit();
                LOG.info("Re-initialized configuration provider for scope \"{}\"", reinit.getScope());
            } catch (Exception e) {
                LOG.warn("Failed to re-initialize configuration provider for scope \"{}\"", reinit.getScope(), e);
            }
        }

        if (reinitMyProvider && configProvider != null) {
            try {
                configProvider.reinit();
                LOG.info("Re-initialized configuration provider for scope \"server\"");
            } catch (Exception e) {
                LOG.warn("Failed to re-initialize configuration provider for scope \"server\"", e);
            }
        }
    }

    /**
     * Adds specified <code>Reloadable</code> instance.
     *
     * @param service The instance to add
     * @return <code>true</code> if successfully added; otherwise <code>false</code> if already present
     */
    @SuppressWarnings("null")
    public synchronized boolean addReloadable(Reloadable service) {
        if (ForcedReloadable.class.isInstance(service)) {
            forcedReloadables.add((ForcedReloadable) service);
            return true;
        }

        Interests interests = service.getInterests();
        String[] propertiesOfInterest = null == interests ? null : interests.getPropertiesOfInterest();
        String[] configFileNames = null == interests ? null : interests.getConfigFileNames();

        boolean hasInterestForProperties = (null != propertiesOfInterest && propertiesOfInterest.length > 0);
        boolean hasInterestForFiles = (null != configFileNames && configFileNames.length > 0);

        // No interests at all?
        if (!hasInterestForProperties && !hasInterestForFiles) {
            // A Reloadable w/o any interests... Assume all
            matchingAllProperties.add(service);
            return true;
        }

        // Check interest for concrete properties
        if (hasInterestForProperties) {
            if (isInterestedInAll(propertiesOfInterest)) {
                matchingAllProperties.add(service);
            } else {
                for (String propertyName : propertiesOfInterest) { // Guarded by 'hasInterestForProperties'
                    Reloadables.validatePropertyName(propertyName);
                    if (propertyName.endsWith(".*")) {
                        // Wild-card property name: we remove the .*
                        String prefix = propertyName.substring(0, propertyName.length() - 2);
                        List<Reloadable> list = matchingPrefixProperty.get(prefix);
                        if (null == list) {
                            List<Reloadable> newList = new CopyOnWriteArrayList<>();
                            matchingPrefixProperty.put(prefix, newList);
                            list = newList;
                        }
                        list.add(service);
                    } else {
                        // Exact match
                        List<Reloadable> list = matchingProperty.get(propertyName);
                        if (null == list) {
                            List<Reloadable> newList = new CopyOnWriteArrayList<>();
                            matchingProperty.put(propertyName, newList);
                            list = newList;
                        }
                        list.add(service);
                    }
                }
            }
        }

        // Check interest for files
        if (hasInterestForFiles) {
            for (String configFileName : configFileNames) { // Guarded by 'hasInterestForFiles'
                Reloadables.validateFileName(configFileName);
                List<Reloadable> list = matchingFile.get(configFileName);
                if (null == list) {
                    List<Reloadable> newList = new CopyOnWriteArrayList<>();
                    matchingFile.put(configFileName, newList);
                    list = newList;
                }
                list.add(service);
            }
        }

        return true;
    }

    /**
     * Removes specified <code>Reloadable</code> instance.
     *
     * @param service The instance to remove
     */
    public synchronized void removeReloadable(Reloadable service) {
        matchingAllProperties.remove(service);

        for (Iterator<List<Reloadable>> it = matchingPrefixProperty.values().iterator(); it.hasNext();) {
            List<Reloadable> reloadables = it.next();
            if (reloadables.remove(service)) {
                if (reloadables.isEmpty()) {
                    it.remove();
                }
            }
        }

        for (Iterator<List<Reloadable>> it = matchingProperty.values().iterator(); it.hasNext();) {
            List<Reloadable> reloadables = it.next();
            if (reloadables.remove(service)) {
                if (reloadables.isEmpty()) {
                    it.remove();
                }
            }
        }

        for (Iterator<List<Reloadable>> it = matchingFile.values().iterator(); it.hasNext();) {
            List<Reloadable> reloadables = it.next();
            if (reloadables.remove(service)) {
                if (reloadables.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Sets associated <code>ConfigProviderServiceImpl</code> instance
     *
     * @param configProviderServiceImpl The instance
     */
    public void setConfigProviderServiceImpl(ConfigProviderServiceImpl configProviderServiceImpl) {
        this.configProviderServiceImpl = configProviderServiceImpl;
    }

    @NonNull
    private Set<File> getChanges(Map<File, Properties> oldPropertiesByFile, Map<File, byte[]> oldXml, Map<File, YamlRef> oldYaml, Set<String> namesOfChangedProperties) {
        Set<File> result = new HashSet<File>(oldPropertiesByFile.size());

        // Check for changes in .properties files
        for (Map.Entry<File, Properties> newEntry : propertiesByFile.entrySet()) {
            File pathname = newEntry.getKey();
            Properties newProperties = newEntry.getValue();
            Properties oldProperties = oldPropertiesByFile.get(pathname);
            if (null == oldProperties) {
                // New .properties file
                result.add(pathname);
                for (Object propertyName : newProperties.keySet()) {
                    namesOfChangedProperties.add(propertyName.toString());
                }
            } else if (!newProperties.equals(oldProperties)) {
                // Changed .properties file
                result.add(pathname);

                // Removed ones
                {
                    Set<Object> removedKeys = new HashSet<>(oldProperties.keySet());
                    removedKeys.removeAll(newProperties.keySet());
                    for (Object removedKey : removedKeys) {
                        namesOfChangedProperties.add(removedKey.toString());
                    }
                }

                // New ones or changed value
                Iterator<Map.Entry<Object, Object>> i = newProperties.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<Object, Object> e = i.next();
                    Object key = e.getKey();
                    Object value = e.getValue();
                    if (value == null) {
                        if (oldProperties.get(key) != null || !oldProperties.containsKey(key)) {
                            namesOfChangedProperties.add(key.toString());
                        }
                    } else {
                        if (!value.equals(oldProperties.get(key))) {
                            namesOfChangedProperties.add(key.toString());
                        }
                    }
                }
            }
        }
        {
            Set<File> removedFiles = new HashSet<File>(oldPropertiesByFile.keySet());
            removedFiles.removeAll(propertiesByFile.keySet());
            result.addAll(removedFiles);
        }

        // Do the same for XML files
        for (Entry<File, byte[]> fileEntry : xmlFiles.entrySet()) {
            File file = fileEntry.getKey();
            byte[] newHash = fileEntry.getValue();
            byte[] oldHash = oldXml.get(file);
            if (null == oldHash || !Arrays.equals(oldHash, newHash)) {
                result.add(file);
            }
        }
        {
            Set<File> removedXml = new HashSet<File>(oldXml.keySet());
            removedXml.removeAll(xmlFiles.keySet());
            result.addAll(removedXml);
        }

        // ... and one more time for YAMLs
        for (Entry<File, YamlRef> filenameEntry : yamlFiles.entrySet()) {
            File filename = filenameEntry.getKey();
            YamlRef newYamlRef = filenameEntry.getValue();
            YamlRef oldYamlRef = oldYaml.get(filename);
            if (null == oldYamlRef || !oldYamlRef.equals(newYamlRef)) {
                result.add(filename);
            } else {
                // Still the same, take over YAML value if present
                newYamlRef.setValueIfAbsent(oldYamlRef.optValue());
            }
        }
        {
            Set<File> removedYaml = new HashSet<File>(oldYaml.keySet());
            removedYaml.removeAll(yamlFiles.keySet());
            result.addAll(removedYaml);
        }

        return result;
    }

    /**
     * Gets all currently tracked <code>Reloadable</code> instances.
     *
     * @return The <code>Reloadable</code> instances
     */
    public Collection<Reloadable> getReloadables() {
        Set<Reloadable> tracked = new LinkedHashSet<>(32);
        tracked.addAll(forcedReloadables);
        tracked.addAll(matchingAllProperties);
        for (List<Reloadable> reloadables : matchingPrefixProperty.values()) {
            tracked.addAll(reloadables);
        }
        for (List<Reloadable> reloadables : matchingProperty.values()) {
            tracked.addAll(reloadables);
        }
        for (List<Reloadable> reloadables : matchingFile.values()) {
            tracked.addAll(reloadables);
        }
        return tracked;
    }

}
