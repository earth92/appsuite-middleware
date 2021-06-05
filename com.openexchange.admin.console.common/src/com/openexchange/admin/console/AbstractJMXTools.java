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

package com.openexchange.admin.console;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import com.openexchange.admin.console.AdminParser.NeededQuadState;
import com.openexchange.admin.rmi.exceptions.InvalidDataException;
import com.openexchange.admin.rmi.exceptions.MissingOptionException;

/**
 * {@link AbstractJMXTools} can be extended to write a command line tool that is a JMX client.
 *
 * @author <a href="mailto:marcus.klein@open-xchange.com">Marcus Klein</a>
 */
public abstract class AbstractJMXTools extends BasicCommandlineOptions {

    private static final int DEFAULT_JMX_SERVER_PORT = 9999;

    /** The default JMX port (<code>9999</code>) */
    protected static final String JMX_SERVER_PORT = Integer.toString(DEFAULT_JMX_SERVER_PORT);

    protected static final char OPT_HOST_SHORT = 'H';
    protected static final String OPT_HOST_LONG = "host";

    protected static final char OPT_PORT_SHORT = 'p';
    protected static final String OPT_PORT_LONG = "port";

    protected static final char OPT_TIMEOUT_SHORT = 'T';
    protected static final String OPT_TIMEOUT_LONG = "timeout";

    protected static final char OPT_JMX_AUTH_PASSWORD_SHORT = 'P';
    protected static final String OPT_JMX_AUTH_PASSWORD_LONG = "jmxauthpassword";

    protected static final char OPT_JMX_AUTH_USER_SHORT = 'J';
    protected static final String OPT_JMX_AUTH_USER_LONG = "jmxauthuser";

    protected static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private CLIOption hostOption = null;
    private CLIOption portOption = null;
    private CLIOption timeoutOption = null;
    protected CLIOption jmxpass = null;
    protected CLIOption jmxuser = null;
    protected String hostname = "localhost";
    protected int port = DEFAULT_JMX_SERVER_PORT;
    protected int timeout = 15000;
    JMXConnector c = null;


    protected AbstractJMXTools() {
        super();
    }

    protected void closeConnection() {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    protected static StringBuffer getStats(MBeanServerConnection con, String objectName) throws IOException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, ReflectionException, IntrospectionException, MalformedObjectNameException {
        StringBuffer retval = new StringBuffer();
        for (ObjectInstance instance : con.queryMBeans(new ObjectName(objectName), null)) {
            ObjectName obj = instance.getObjectName();
            if (null == obj) {
                continue;
            }
            MBeanInfo info;
            try {
                info = con.getMBeanInfo(obj);
            } catch (InstanceNotFoundException e) {
                return retval;
            }
            String ocname = obj.getCanonicalName();
            MBeanAttributeInfo[] attrs = info.getAttributes();
            if (attrs.length == 0) {
                continue;
            }
            processAttributes(con, retval, obj, ocname, attrs);
        }
        return retval;
    }

    private static void processAttributes(MBeanServerConnection con, StringBuffer retval, ObjectName obj, String ocname, MBeanAttributeInfo[] attrs) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
        for (MBeanAttributeInfo element : attrs) {
            processElement(con, retval, obj, ocname, element);
        }
    }

    private static void processElement(MBeanServerConnection con, StringBuffer retval, ObjectName obj, String ocname, MBeanAttributeInfo element) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
        try {
            Object o = con.getAttribute(obj, element.getName());
            if (o == null) {
                return;
            }
            StringBuilder sb = new StringBuilder(ocname).append(",").append(element.getName()).append(" = ");
            if (o instanceof CompositeDataSupport) {
                CompositeDataSupport c = (CompositeDataSupport) o;
                sb.append("[init=").append(c.get("init")).append(",max=").append(c.get("max")).append(",committed=").append(c.get("committed")).append(",used=").append(c.get("used")).append("]");
                retval.append(sb.append(LINE_SEPARATOR));
                return;
            }

            if (o instanceof String[]) {
                String[] c = (String[]) o;
                retval.append(sb.append(Arrays.toString(c)).append(LINE_SEPARATOR));
            } else if (o instanceof long[]) {
                long[] l = (long[]) o;
                retval.append(sb.append(Arrays.toString(l)).append(LINE_SEPARATOR));
            } else {
                retval.append(sb.append(o.toString()).append(LINE_SEPARATOR));
            }
        } catch (RuntimeMBeanException e) {
            // If there was an error getting the attribute we just omit that attribute
        }
    }

    protected static String getStats(MBeanServerConnection mbc, String domain, String key, String name) throws JMException, NullPointerException, IOException, IllegalStateException {
        ObjectName objectName = new ObjectName(domain, key, name);
        MBeanInfo info;
        try {
            info = mbc.getMBeanInfo(objectName);
        } catch (Exception e) {
            return "";
        }
        MBeanAttributeInfo[] attrs = info.getAttributes();
        StringBuilder retval = new StringBuilder();
        if (attrs.length > 0) {
            for (MBeanAttributeInfo element : attrs) {
                try {
                    Object o = mbc.getAttribute(objectName, element.getName());
                    if (o != null) {
                        retval.append(objectName.getCanonicalName()).append(',').append(element.getName()).append(" = ");
                        if (o instanceof int[]) {
                            int[] i = (int[]) o;
                            retval.append(Arrays.toString(i)).append(LINE_SEPARATOR);
                        } else {
                            retval.append(o.toString()).append(LINE_SEPARATOR);
                        }
                    }
                } catch (RuntimeMBeanException e) {
                    // If there was an error getting the attribute we just omit that attribute
                }
            }
        }
        return retval.toString();
    }

    protected MBeanServerConnection initConnection(Map<String, String[]> env) throws InterruptedException, IOException {
        // Set timeout here, it is given in ms
        JMXServiceURL serviceurl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + hostname + ':' + port + "/server");
        IOException[] exc = new IOException[1];
        RuntimeException[] excr = new RuntimeException[1];
        Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    c = JMXConnectorFactory.connect(serviceurl, env);
                } catch (IOException e) {
                    exc[0] = e;
                } catch (RuntimeException e) {
                    excr[0] = e;
                }
            }
        };
        t.start();
        t.join(timeout);
        if (t.isAlive()) {
            t.interrupt();
            throw new InterruptedIOException("Connection timed out");
        }
        if (exc[0] != null) {
            throw exc[0];
        }
        if (excr[0] != null) {
            throw excr[0];
        }
        return c.getMBeanServerConnection();
    }

    protected void setOptions(AdminParser parser) {
        this.hostOption = setShortLongOpt(parser, OPT_HOST_SHORT, OPT_HOST_LONG, "host", "specifies the host", false);
        this.portOption = setShortLongOpt(parser, OPT_PORT_SHORT, OPT_PORT_LONG, "port", "specifies the port", false);
        this.timeoutOption = setShortLongOpt(parser, OPT_TIMEOUT_SHORT, OPT_TIMEOUT_LONG, "timeout in seconds for the connection creation to the backend (default 15s)", true, NeededQuadState.notneeded);
        this.jmxuser = setShortLongOpt(parser, OPT_JMX_AUTH_USER_SHORT, OPT_JMX_AUTH_USER_LONG, "jmx username (required when jmx authentication enabled)", true, NeededQuadState.notneeded);
        this.jmxpass = setShortLongOpt(parser, OPT_JMX_AUTH_PASSWORD_SHORT, OPT_JMX_AUTH_PASSWORD_LONG, "jmx username (required when jmx authentication enabled)", true, NeededQuadState.notneeded);
        setFurtherOptions(parser);
    }

    protected abstract void setFurtherOptions(AdminParser parser);

    protected static String doOperationReturnString(MBeanServerConnection mbc, String fullqualifiedoperationname) throws MalformedObjectNameException, NullPointerException, InstanceNotFoundException, MBeanException, ReflectionException, IOException, InvalidDataException {
        Object opObject = doOperation(mbc, fullqualifiedoperationname);
        if (null == opObject) {
            return "";
        }

        StringBuilder retval = new StringBuilder();
        retval.append(fullqualifiedoperationname).append(" = ");
        return retval.append(opObject).append(LINE_SEPARATOR).toString();
    }

    protected static Object doOperation(MBeanServerConnection mbc, String fullqualifiedoperationname) throws MalformedObjectNameException, NullPointerException, IOException, InvalidDataException, InstanceNotFoundException, MBeanException, ReflectionException {
        String[] split = fullqualifiedoperationname.split("!");
        if (2 == split.length) {
            ObjectName objectName = new ObjectName(split[0]);
            Object result = mbc.invoke(objectName, split[1], null, null);
            if (result instanceof Object[]) {
                return Arrays.toString((Object[]) result);
            }
            return result;
        } else if (2 <= split.length) {
            ObjectName objectName = new ObjectName(split[0]);
            String[] param = new String[split.length - 2];
            System.arraycopy(split, 2, param, 0, split.length - 2);
            String[] signature = new String[split.length - 2];
            for (int i = 0; i < signature.length; i++) {
                signature[i] = "java.lang.String";
            }
            Object result = mbc.invoke(objectName, split[1], param, signature);
            if (result instanceof Object[]) {
                return Arrays.toString((Object[]) result);
            }
            return result;
        } else {
            throw new InvalidDataException("The given operationname is not valid. It couldn't be split at \"!\"");
        }
    }

    protected Map<String, String[]> setCreds(final AdminParser parser) throws CLIIllegalOptionValueException {
        final String userValue = (String) parser.getOptionValue(this.jmxuser);
        final String passValue = (String) parser.getOptionValue(this.jmxpass);

        if (userValue != null && userValue.trim().length() > 0) {
            if (passValue == null) {
                throw new CLIIllegalOptionValueException(this.jmxpass, null);
            }
            Map<String, String[]> env = new HashMap<String, String[]>();
            final String[] creds = new String[] { userValue, passValue };
            env.put(JMXConnector.CREDENTIALS, creds);
            return env;
        }
        return null;
    }

    protected void readAndApplyOptions(AdminParser parser) throws CLIIllegalOptionValueException {
        String value = (String) parser.getOptionValue(this.hostOption);
        if (null != value) {
            hostname = value;
        }
        value = (String) parser.getOptionValue(this.portOption);
        if (null != value) {
            try {
                port = Integer.parseInt(value.trim());
                if (port < 1 || port > 65535) {
                    throw new CLIIllegalOptionValueException(portOption, value, new Throwable("Invalid port range. Valid range is 1 through 65535."));
                }
            } catch (NumberFormatException e) {
                throw new CLIIllegalOptionValueException(portOption, value, e);
            }
        }
        value = (String) parser.getOptionValue(this.timeoutOption);
        if (null != value) {
            try {
                timeout = Integer.parseInt(value) * 1000;
            } catch (NumberFormatException e) {
                throw new CLIIllegalOptionValueException(timeoutOption, value, e);
            }
        }
    }

    public void start(String args[], String commandlinetoolname) {
        AdminParser parser = new AdminParser(commandlinetoolname);

        setOptions(parser);

        try {
            parser.ownparse(args);

            Map<String, String[]> env = setCreds(parser);

            readAndApplyOptions(parser);

            furtherOptionsHandling(parser, env);
        } catch (CLIParseException e) {
            printError("Parsing command-line failed : " + e.getMessage(), parser);
            parser.printUsage();
            sysexit(SYSEXIT_ILLEGAL_OPTION_VALUE);
        } catch (CLIIllegalOptionValueException e) {
            printError("Illegal option value : " + e.getMessage(), parser);
            parser.printUsage();
            sysexit(SYSEXIT_ILLEGAL_OPTION_VALUE);
        } catch (CLIUnknownOptionException e) {
            printError("Unrecognized options on the command line: " + e.getMessage(), parser);
            parser.printUsage();
            sysexit(SYSEXIT_UNKNOWN_OPTION);
        } catch (MissingOptionException e) {
            printError(e.getMessage(), parser);
            parser.printUsage();
            sysexit(SYSEXIT_MISSING_OPTION);
        } catch (MalformedURLException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (IOException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (InstanceNotFoundException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (AttributeNotFoundException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (IntrospectionException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (MBeanException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (ReflectionException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (InterruptedException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (MalformedObjectNameException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (NullPointerException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (InvalidDataException e) {
            printServerException(e, parser);
            sysexit(1);
        } catch (JMException e) {
            printServerException(e, parser);
            sysexit(1);
        } finally {
            closeConnection();
        }
    }

    protected abstract void furtherOptionsHandling(AdminParser parser, Map<String, String[]> env) throws InterruptedException, IOException, MalformedURLException, InstanceNotFoundException, AttributeNotFoundException, IntrospectionException, MBeanException, ReflectionException, MalformedObjectNameException, InvalidDataException, JMException;

}
