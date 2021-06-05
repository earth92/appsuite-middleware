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

package com.openexchange.templating.impl;

import static com.openexchange.templating.TemplateErrorMessage.IOException;
import static com.openexchange.templating.TemplateErrorMessage.TemplateNotFound;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.java.util.Pair;
import com.openexchange.mail.mime.MimeType2ExtMap;
import com.openexchange.session.Session;
import com.openexchange.templating.OXTemplate;
import com.openexchange.templating.OXTemplate.TemplateLevel;
import com.openexchange.templating.OXTemplateExceptionHandler;
import com.openexchange.templating.TemplateErrorMessage;
import com.openexchange.templating.TemplateService;
import com.openexchange.templating.TemplatingHelper;
import com.openexchange.tools.encoding.Base64;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/**
 * {@link TemplateServiceImpl} - The default implementation of {@link TemplateService}.
 *
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class TemplateServiceImpl implements TemplateService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TemplateServiceImpl.class);

    /** The property for file path to templates */
    public static final String PATH_PROPERTY = "com.openexchange.templating.path";

    /** The property for whether user templates are enabled */
    public static final String USER_TEMPLATING_PROPERTY = "com.openexchange.templating.usertemplating";
    public static final String TRUSTED_PROPERTY = "com.openexchange.templating.trusted";

    /** The map for cached tags */
    private static final Map<String, Map<String, Set<String>>> CACHED_TAGS = new ConcurrentHashMap<String, Map<String, Set<String>>>();

    private final Object lock;
    private final ConfigurationService config;
    private OXFolderHelper folders;
    private OXInfostoreHelper infostore;
    private final String defaultTemplatePath;

    /**
     * Initializes a new {@link TemplateServiceImpl}.
     *
     * @param config The configuration service
     */
    public TemplateServiceImpl(final ConfigurationService config) {
        super();
        lock = new Object();
        this.config = config;
        defaultTemplatePath = config.getProperty(PATH_PROPERTY);
    }

    @Override
    public OXTemplate loadTemplate(final String templateName) throws OXException {
        return loadTemplate(templateName, null);
    }

    @Override
    public OXTemplate loadTemplate(final String templateName, final OXTemplateExceptionHandler exceptionHandler) throws OXException {
        final String templatePath = defaultTemplatePath;
        if (templatePath == null) {
            return null;
        }

        final OXTemplateImpl retval = exceptionHandler == null ? new OXTemplateImpl() : new OXTemplateImpl(exceptionHandler);

        retval.setLevel(TemplateLevel.SERVER);
        Properties properties = new Properties();

        // Load template
        {
            Template template = loadTemplate(templatePath, templateName, properties);
            if (null == template) {
                throw TemplateErrorMessage.TemplateNotFound.create(templatePath + File.separator + templateName);
            }
            retval.setTemplate(template);
        }

        checkTrustLevel(retval);
        retval.setProperties(properties);
        return retval;
    }

    protected Template loadTemplate(final String templatePath, final String templateName, final Properties properties) throws OXException {
        final File path = new File(templatePath);
        if (!path.exists() || !path.isDirectory() || !path.canRead()) {
            return null;
        }
        checkTemplatePath(templatePath);

        synchronized (lock) {
            Template retval = null;
            try {
                if (existsInFilesystem(templateName)) {
                    final String userDir = System.getProperty("user.dir");
                    System.setProperty("user.dir", templatePath);
                    final Configuration config = new Configuration();
                    System.setProperty("user.dir", userDir);
                    config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
                    String templateText = loadFromFileSystem(templateName);
                    templateText = extractProperties(templateText, properties);

                    retval = new Template(templateName, new StringReader(templateText), config);
                }
            } catch (IOException e) {
                throw IOException.create(e);
            }
            if (retval == null) {
                throw TemplateNotFound.create(templateName);
            }
            return retval;
        }
    }

    @Override
    public TemplatingHelper createHelper(final Object rootObject, final Session session, final boolean createCopy) {
        return new TemplatingHelperImpl(rootObject, session, this, createCopy);
    }

    @Override
    public OXTemplate loadTemplate(final String templateName, final String defaultTemplateName, final Session session, final OXTemplateExceptionHandler exceptionHandler) throws OXException {
        return loadTemplate(templateName, defaultTemplateName, session);
    }

    @Override
    public OXTemplate loadTemplate(final String templateName, final String defaultTemplateName, final Session session) throws OXException {
        return loadTemplate(templateName, defaultTemplateName, session, true);
    }

    @Override
    public OXTemplate loadTemplate(final String templateName, final String defaultTemplateName, final Session sess, final boolean createCopy) throws OXException {
        final ServerSession session = ServerSessionAdapter.valueOf(sess);

        if (Strings.isEmpty(templateName) || !isUserTemplatingEnabled(session)) {
            if (!isAdminTemplate(templateName)) {
                return loadTemplate(defaultTemplateName);
            }
        }

        synchronized (lock) {
            try {
                FolderObject folder = folders.getPrivateTemplateFolder(session);
                FolderObject privateFolder = folder;
                boolean global = false;

                if (null == folder) {
                    folder = folders.getGlobalTemplateFolder(session);
                    global = true;
                }
                String templateText = (folder == null) ? null : infostore.findTemplateInFolder(session, folder, templateName);

                final String userDir = System.getProperty("user.dir");
                final String templatePath = config.getProperty(PATH_PROPERTY);
                System.setProperty("user.dir", templatePath);
                final Configuration config = new Configuration();
                System.setProperty("user.dir", userDir);
                config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
                if (templateText != null) {
                    final OXTemplateImpl template = new OXTemplateImpl();
                    Properties properties = new Properties();
                    templateText = extractProperties(templateText, properties);
                    template.setTemplate(new Template(templateName, new StringReader(templateText), config));
                    template.setLevel(TemplateLevel.USER);
                    checkTrustLevel(template);
                    template.setProperties(properties);
                    return template;
                }

                if (!global) {
                    folder = folders.getGlobalTemplateFolder(session);
                    global = true;

                    templateText = (folder == null) ? null : infostore.findTemplateInFolder(session, folder, templateName);
                }

                if (templateText == null) {

                    if (existsInFilesystem(templateName)) {
                        templateText = loadFromFileSystem(templateName);
                        final OXTemplateImpl template = new OXTemplateImpl();
                        Properties properties = new Properties();
                        templateText = extractProperties(templateText, properties);
                        template.setTemplate(new Template(templateName, new StringReader(templateText), config));
                        template.setLevel(TemplateLevel.SERVER);
                        template.setProperties(properties);
                        checkTrustLevel(template);
                        return template;
                    }

                    templateText = loadFromFileSystem(defaultTemplateName);
                    if (privateFolder == null) {
                        folder = folders.createPrivateTemplateFolder(session);
                        privateFolder = folder;
                    }
                    infostore.storeTemplateInFolder(session, privateFolder, templateName, templateText);
                }
                final OXTemplateImpl template = new OXTemplateImpl();
                Properties properties = new Properties();
                templateText = extractProperties(templateText, properties);
                template.setTemplate(new Template(templateName, new StringReader(templateText), config));
                template.setLevel(TemplateLevel.USER);
                template.setProperties(properties);
                checkTrustLevel(template);
                return template;
            } catch (IOException e) {
                throw IOException.create(e);
            }
        }
    }

    /**
     * Indicates if a given template is an admin template which means that it is defined from the administrator and stored within the
     * backend.
     *
     * @param templateName - the name of the template to check.
     * @return true - if the template is indicated to be from the admin. false - if it is configured from the user.
     */
    protected boolean isAdminTemplate(String templateName) {
        List<String> basicTemplateNames = getBasicTemplateNames(new String[0]);
        if (basicTemplateNames.contains(templateName) && existsInFilesystem(templateName)) {
            return true;
        }
        return false;
    }

    private void checkTemplatePath(final String templatePath) throws OXException {
        try {
            if (Strings.isEmpty(templatePath)) {
                return;
            }
            final String defaultTemplatePath = this.defaultTemplatePath;
            if (defaultTemplatePath == null) {
                return;
            }
            if (Strings.toLowerCase(defaultTemplatePath).equals(Strings.toLowerCase(templatePath))) {
                // Equal directory
                return;
            }
            if (isSubDirectory(new File(defaultTemplatePath), new File(templatePath))) {
                return;
            }
            // A file is accessed in a foreign directory
            final OXException e = TemplateErrorMessage.AccessDenied.create();
            LOG.error("{}: Acces to file denied: \"{}\" exceptionID={}", e.getErrorCode(), templatePath, e.getExceptionId());
            throw e;
        } catch (IOException e) {
            throw TemplateErrorMessage.IOException.create(e, e.getMessage());
        }
    }

    /**
     * Checks, whether the child directory is a sub-directory of the base directory.
     *
     * @param base The base directory.
     * @param child The suspected child directory.
     * @return <code>true</code> if the child is a sub-directory of the base directory.
     * @throws IOException If an I/O error occurred during the test.
     */
    private boolean isSubDirectory(final File base, final File child) throws IOException {
        final File b = base.getCanonicalFile();
        final File c = child.getCanonicalFile();

        File parentFile = c;
        while (parentFile != null) {
            if (b.equals(parentFile)) {
                return true;
            }
            parentFile = parentFile.getParentFile();
        }
        return false;
    }

    private void checkTrustLevel(OXTemplateImpl template) {
        String trustExpression = config.getProperty(TRUSTED_PROPERTY);
        if (trustExpression == null) {
            template.setTrusted(template.getLevel() == TemplateLevel.SERVER);
            return;
        }
        if (trustExpression.indexOf("true") != -1) {
            template.setTrusted(true);
            return;
        }

        if (trustExpression.indexOf("server") != -1 && template.getLevel() == TemplateLevel.SERVER) {
            template.setTrusted(true);
            return;
        }

        if (trustExpression.indexOf("user") != -1 && template.getLevel() == TemplateLevel.USER) {
            template.setTrusted(true);
            return;
        }

        template.setTrusted(false);
    }

    private String extractProperties(String text, Properties properties) throws OXException {
        StringBuilder keep = new StringBuilder();
        StringBuilder props = new StringBuilder();
        int state = 0;
        for (String line : text.split("\n")) {
            switch (state) {
                case 0:
                    if (line.startsWith("BEGIN")) {
                        state = 1;
                    } else {
                        keep.append(line).append('\n');
                    }
                    break;
                case 1:
                    if (line.startsWith("END")) {
                        state = 2;
                    } else {
                        props.append(line).append('\n');
                    }
                    break;
                case 2:
                    keep.append(line).append('\n');
            }
        }

        try {
            if (state > 0) {
                properties.load(new StringReader(props.toString()));
            }
        } catch (IOException e) {
            throw TemplateErrorMessage.IOException.create();
        }

        return keep.toString();
    }

    private boolean isUserTemplatingEnabled(final ServerSession session) {
        return "true".equalsIgnoreCase(config.getProperty(USER_TEMPLATING_PROPERTY, "true")) && session.getUserPermissionBits().hasInfostore();
    }

    protected boolean existsInFilesystem(final String templateName) {
        final File templateFile = getTemplateFile(templateName);
        if (!templateFile.exists() || !templateFile.exists() || !templateFile.canRead()) {
            return false;
        }
        return true;
    }

    protected String loadFromFileSystem(final String defaultTemplateName) throws OXException {
        final File templateFile = getTemplateFile(defaultTemplateName);
        if (!templateFile.exists() || !templateFile.exists() || !templateFile.canRead()) {
            throw TemplateErrorMessage.TemplateNotFound.create(defaultTemplateName);
        }
        checkTemplatePath(templateFile.getPath());
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(templateFile), com.openexchange.java.Charsets.UTF_8), 2048);
            final StringBuilder builder = new StringBuilder(2048);
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            throw IOException.create(e);
        } finally {
            Streams.close(reader);
        }
    }

    private File getTemplateFile(String defaultTemplateName) {
        defaultTemplateName = new File(defaultTemplateName).getName();
        return new File(defaultTemplatePath, defaultTemplateName);
    }

    /**
     * Sets the folder helper
     *
     * @param helper The helper
     */
    public synchronized void setOXFolderHelper(final OXFolderHelper helper) {
        this.folders = helper;
    }

    /**
     * Sets the InfoStore helper
     *
     * @param helper The helper
     */
    public synchronized void setInfostoreHelper(final OXInfostoreHelper helper) {
        this.infostore = helper;
    }

    @Override
    public List<String> getBasicTemplateNames(final String... filter) {
        final String templatePath = defaultTemplatePath;
        final File templateDir = new File(templatePath);
        if (!templateDir.isDirectory() || !templateDir.exists()) {
            return new ArrayList<String>(0);
        }

        final Set<String> sieve = new HashSet<String>(Arrays.asList(filter));

        final Map<String, Set<String>> tagMap = getTagMap(templateDir);

        final File[] files = templateDir.listFiles();
        if (files == null) {
            return new ArrayList<String>(0);
        }
        final Set<String> names = new HashSet<String>();
        final Set<String> defaults = new HashSet<String>();
        for (final File file : files) {
            Set<String> tags = tagMap.get(file.getName());
            if (tags == null) {
                tags = Collections.emptySet();
            }
            if (file.isFile() && file.canRead() && file.getName().endsWith(".tmpl") && (tags.containsAll(sieve))) {
                if (tags.contains("default")) {
                    defaults.add(file.getName());
                } else {
                    names.add(file.getName());
                }

            }
        }
        final List<String> a = new ArrayList<String>(defaults);
        final List<String> b = new ArrayList<String>(names);
        Collections.sort(a);
        Collections.sort(b);
        a.addAll(b);

        return a;
    }

    private Map<String, Set<String>> getTagMap(final File templateDir) {
        final String absolutePath = templateDir.getAbsolutePath();

        {
            final Map<String, Set<String>> map = CACHED_TAGS.get(absolutePath);
            if (null != map) {
                return map;
            }
        }

        final File[] files = templateDir.listFiles(new FileFilter() {

            @Override
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(".properties") && pathname.canRead() && pathname.isFile();
            }
        });
        if (files == null) {
            final Map<String, Set<String>> emptyMap = Collections.emptyMap();
            CACHED_TAGS.put(absolutePath, emptyMap);
            return emptyMap;
        }

        final Map<String, Set<String>> tagMap = new HashMap<String, Set<String>>(files.length);
        for (final File file : files) {
            InputStream inStream = null;
            try {
                inStream = new BufferedInputStream(new FileInputStream(file), 2048);
                final Properties index = new Properties();
                index.load(inStream);
                for (final Entry<Object, Object> entry : index.entrySet()) {
                    final String filename = entry.getKey().toString();
                    final String[] categoriesArr = Strings.splitByComma(entry.getValue().toString());
                    final Set<String> categories = new HashSet<String>(Arrays.asList(categoriesArr));
                    tagMap.put(filename, categories);
                }
            } catch (IOException e) {
                LOG.error("", e);
            } finally {
                Streams.close(inStream);
            }
        }
        CACHED_TAGS.put(absolutePath, tagMap);
        return tagMap;

    }

    @Override
    public List<String> getTemplateNames(final Session sess, String... filter) throws OXException {
        final ServerSession session = ServerSessionAdapter.valueOf(sess);
        if (filter == null) {
            filter = new String[0];
        }
        final Set<String> names = new HashSet<String>();
        if (!isUserTemplatingEnabled(session)) {
            return getBasicTemplateNames(filter);
        }

        final FolderObject globalTemplateFolder = folders.getGlobalTemplateFolder(session);
        final FolderObject privateTemplateFolder = folders.getPrivateTemplateFolder(session);

        if (globalTemplateFolder != null) {
            names.addAll(infostore.getNames(session, globalTemplateFolder, filter));
        }
        if (privateTemplateFolder != null) {
            names.addAll(infostore.getNames(session, privateTemplateFolder, filter));
        }

        final List<String> basicTemplateNames = getBasicTemplateNames(filter);
        if (!names.isEmpty()) {
            final ArrayList<String> userTemplates = new ArrayList<String>(names);
            Collections.sort(userTemplates);
            basicTemplateNames.addAll(userTemplates);
        }
        return basicTemplateNames;
    }

    @Override
    public Pair<String, String> encodeTemplateImage(String imageName) throws OXException {
        try {
            File imageFile = new File(defaultTemplatePath, imageName);
            String contentType = MimeType2ExtMap.getContentType(imageFile);
            byte[] imageBytes = FileUtils.readFileToByteArray(imageFile);
            String imageBase64 = Base64.encode(imageBytes);
            return new Pair<String, String>(contentType, imageBase64);
        } catch (IOException e) {
            throw TemplateErrorMessage.IOException.create(e, e.getMessage());
        }
    }
}
