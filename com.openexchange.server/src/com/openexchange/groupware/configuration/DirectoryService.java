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



package com.openexchange.groupware.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.openexchange.configuration.SystemConfig;
import com.openexchange.java.Streams;

/**
 * This class contains the methods for reading all configurations for
 * directory services. Use host or uri for defining the
 * directory service. port can only be used in combination with host.
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public final class DirectoryService {

   /**
    * Name of the entry defining the port of the directory service.
    */
   private static final String PORT = "port";

   /**
    * Name of the entry defining the universal resource indicator of the
    * directory service.
    */
   private static final String URI = "uri";

   /**
    * Name of the entry defining the name of the host running the directory
    * service.
    */
   private static final String HOST = "host";

   /**
    * Name of the entry defining the baseDN of the directory service.
    */
   private static final String BASE = "base";

   /**
    * Name of the entry defining the distinguished name that should be used
    * instead of anonymous binds.
    */
   private static final String BINDDN = "binddn";

   /**
    * Name of the entry defining the password that should be used if using non
    * anonymous binds.
    */
   private static final String BINDPW = "bindpw";

   /**
    * Name of the file containing the configuration for the directory service.
    * The value of this constant is ldap.conf.
    */
   private static final String LDAP_CONF_FILENAME = "ldap.conf";

   /**
    * Name of the file containing the configuration for the writable directory
    * service. The value of this constant is ldapwrite.conf.
    */
   private static final String WRITABLE_LDAP_CONF_FILENAME = "ldapwrite.conf";

   /**
    * Logger.
    */
   private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DirectoryService.class);

   /**
    * The URI for the directory service.
    */
   private static String uri = null;

   /**
    * The baseDN for the directory service.
    */
   private static String baseDN = null;

   /**
    * The URI for the writable directory service.
    */
   private static String writableURI = null;

   /**
    * Distinguished name for a bind that should be used instead of anonymous
    * binds.
    */
   private static String bindDN = null;

   /**
    * Password for the bind if a non anonymous bind is used.
    */
   private static String bindPW = null;

   /**
    * This boolean stores if we don't have an extra directory service for
    * writing.
    */
   private static boolean writeSameAsRead = false;

   /**
    * Private constructor prevents instantiation.
    */
   private DirectoryService() {
       super();
   }

   /**
    * This method returns the base distinguished name of the directory service.
    * @return the base distinguished name of the directory service.
    */
   public static String getBaseDN() {
      if (null == baseDN) {
         loadLdapConf();
      }
      return baseDN;
   }

   /**
    * This method returns the URI for the directory service.
    * @return the URI for the directory service.
    */
   public static String getURI() {
      if (null == uri) {
         loadLdapConf();
      }
      return uri;
   }

   /**
    * This method returns the URI for the writable directory service.
    * @return the URI for the writable directory service.
    */
   public static String getWritableURI() {
      if (null == writableURI) {
         loadWritableLdapConf();
      }
      return writableURI;
   }

   /**
    * This method returns the distinguished name for anonymous access to the
    * directory service.
    * @return the full distinguished name or <code>null</code> if anonymous
    *         binds should be used.
    */
   public static String getBindDN() {
      if (null == bindDN && null == uri) {
         loadLdapConf();
      }
      return bindDN;
   }

   /**
    * This method returns the password for anonymous access to the directory
    * service.
    * @return the password or <code>null</code> if anonymous binds should be
    *         used.
    */
   public static String getBindPW() {
      if (null == bindPW && null == uri) {
         loadLdapConf();
      }
      return bindPW;
   }

   /**
    * This method returns <code>true</code> if no extra directory service is
    * used for writing.
    * @return <code>true</code> if the same directory service is used for
    *         reading and writing.
    */
   public static boolean isWriteSameAsRead() {
      if (null == writableURI) {
         loadWritableLdapConf();
      }
      return writeSameAsRead;
   }

   /**
    * This method reads the configuration parameters for the directory service.
    */
   private static void loadLdapConf() {
      final String configPath = SystemConfig.getProperty("openexchange.propdir");
      final File ldapConfFile = new File(configPath, LDAP_CONF_FILENAME);
      if (ldapConfFile.exists()) {
         try {
            final String[] confValues = loadLdapConf(ldapConfFile);
            if (null != confValues[3]) {
               uri = confValues[3];
            } else {
               if (null == confValues[1]) {
                   LOG.error("Missing HOST and URI in directory service configuration.");
               } else {
                  uri = "ldap://" + confValues[1];
                  if (null != confValues[2]) {
                     uri += ':' + confValues[2];
                  }
               }
            }
            if (null != confValues[0]) {
               baseDN = confValues[0];
            } else {
               LOG.error("Missing BASE in directory service configuration.");
            }
            if (null != confValues[4] && null != confValues[5]) {
               bindDN = confValues[4];
               bindPW = confValues[5];
            }
         } catch (IOException e) {
            LOG.error("Error while reading writable directory service configuration.", e);
         }
      } else {
         LOG.error("Cannot read directory service configuration file \"{}\"", ldapConfFile.getAbsolutePath());
      }
   }

   /**
    * This method reads the configuration parameters for the writable directory
    * service. If there is not configuration the one for the readable directory
    * service will be used.
    */
   private static void loadWritableLdapConf() {
      final String configPath = SystemConfig.getProperty("openexchange.propdir");
      final File writableLdapConfFile = new File(configPath,
               WRITABLE_LDAP_CONF_FILENAME);
      if (writableLdapConfFile.exists()) {
         try {
            final String[] confValues = loadLdapConf(writableLdapConfFile);
            if (null != confValues[3]) {
               writableURI = confValues[3];
            } else {
               if (null == confValues[1]) {
                  LOG.error("Missing HOST and URI in directory service configuration.");
               } else {
                  writableURI = "ldap://" + confValues[1];
                  if (null != confValues[2]) {
                     writableURI += ":" + confValues[2];
                  }
               }
            }
         } catch (IOException e) {
            LOG.error("Error while reading writable directory service configuration.", e);
         }
      } else {
         loadLdapConf();
         writableURI = uri;
         writeSameAsRead = true;
      }
   }

   /**
    * This method parses ldap configuration files according to their defined
    * format. It reads the entries HOST, PORT, BASE and URI. All other entries
    * are ignored.
    * @param ldapConfFile file contains a ldap configuration file.
    * @return a String array containing the read values or <code>null</code> if
    * an entry doesn't exist. The order is BASE, HOST, PORT, URI.
    * @throws IOException if an error occurs while reading the file.
    */
    private static String[] loadLdapConf(final File ldapConfFile) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(ldapConfFile), StandardCharsets.ISO_8859_1));
        final String[] retval;
        try {
            String line = null;
            retval = new String[6];
            while ((line = br.readLine()) != null) {
                final String detectable = line.trim().toLowerCase();
                if (detectable.startsWith(BASE)) {
                    retval[0] = line.substring(BASE.length()).trim();
                }
                if (detectable.startsWith(HOST)) {
                    retval[1] = line.substring(HOST.length()).trim();
                    if (retval[1].indexOf(' ') != -1) {
                        retval[1] = retval[1].substring(0, retval[1].indexOf(' '));
                    }
                }
                if (detectable.startsWith(URI)) {
                    retval[3] = line.substring(URI.length()).trim();
                }
                if (detectable.startsWith(PORT)) {
                    retval[2] = line.substring(PORT.length()).trim();
                }
                if (detectable.startsWith(BINDDN)) {
                    retval[4] = line.substring(BINDDN.length()).trim();
                }
                if (detectable.startsWith(BINDPW)) {
                    retval[5] = line.substring(BINDPW.length()).trim();
                }
            }
        } finally {
            Streams.close(br);
        }
        return retval;
    }

}
