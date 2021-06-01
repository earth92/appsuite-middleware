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

package com.openexchange.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.java.AsciiReader;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;

/**
 *
 * {@link AbstractRestCLI} - A basic abstract REST Cli implementation that is based
 * on BasicAuth
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @param <R> - The return type
 * @since v7.8.4
 */
public abstract class AbstractRestCLI<R> extends AbstractAdministrativeCLI<R, Builder, Void> {

    protected static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    protected static final String USER_LONG = "api-user";
    protected static final String USER_SHORT = "U";

    private Builder executionContext;

    /**
     * Initializes a new {@link AbstractRestCLI}.
     */
    protected AbstractRestCLI() {
        super();
    }

    @Override
    protected void addAdministrativeOptions(Options options, boolean mandatory) {
        options.addOption(createArgumentOption(USER_SHORT, USER_LONG, "user:password", "Username and password to use for REST API authentication (user:password).", true));
    }

    /**
     * Gets the raw value of authorization header added to the HTTP request.
     *
     * @param cmd The {@link CommandLine}
     * @return The uncoded value of the authorization header to add to the HTTP request.
     */
    protected String getAuthorizationHeader(CommandLine cmd) {
        return cmd.getOptionValue(USER_SHORT);
    }

    /**
     * Executes the command-line tool.
     *
     * @param args The arguments
     * @return The return value
     */
    @Override
    public R execute(String[] args) {
        Options options = newOptions();
        boolean error = true;
        try {
            // Option for help
            options.addOption(createSwitch("h", "help", "Prints this help text", false));
            boolean requiresAdministrativePermission = optAdministrativeOptions(args);

            // Add other options
            addOptions(options);

            // Check if help output is requested
            if (helpRequested(args)) {
                printHelp(options);
                System.exit(0);
                return null;
            }

            // Initialize command-line parser & parse arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            checkArguments(cmd);

            // Check other mandatory options
            checkOptions(cmd, options);

            WebTarget endpoint = getEndpoint(cmd);
            if (null == endpoint) {
                return null;
            }

            executionContext = endpoint.request();
            if (requiresAdministrativePermission) {
                optAuthenticate(cmd);
            }
            R retval = invoke(options, cmd, executionContext);
            error = false;
            return retval;
        } catch (MissingOptionException e) {
            System.err.println(e.getMessage());
            printHelp(options);
        } catch (ParseException e) {
            System.err.println("Unable to parse command line: " + e.getMessage());
            printHelp(options);
        } catch (MalformedURLException e) {
            System.err.println("URL to connect to server is invalid: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Unable to communicate with the server: " + e.getMessage());
        } catch (javax.ws.rs.NotAuthorizedException e) {
            System.err.println("Authorization not possible. Please check the provided credentials.");
        } catch (javax.ws.rs.ProcessingException e) {
            System.err.println("Unable to reach provided endpoint: " + e.getMessage());
        } catch (javax.ws.rs.InternalServerErrorException e) {
            System.err.println("An error occurred on endpoint side. Please check the server logs.");
        } catch (javax.ws.rs.BadRequestException e) {
            System.err.println(printClientException(e, "The provided request parameters seem to be invalid. Please check them and additionally the server logs for further information."));
        } catch (javax.ws.rs.NotFoundException e) {
            System.err.println(printClientException(e, "The requested resource cannot be found. Please check the provided parameters and additionally the server logs for further information."));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            String clazzName = e.getClass().getName();
            System.err.println("A runtime error occurred: " + (null == message ? clazzName : new StringBuilder(clazzName).append(": ").append(message).toString()));
        } catch (Error e) {
            String message = e.getMessage();
            String clazzName = e.getClass().getName();
            System.err.println("A JVM problem occurred: " + (null == message ? clazzName : new StringBuilder(clazzName).append(": ").append(message).toString()));
        } catch (Throwable t) {
            String message = t.getMessage();
            String clazzName = t.getClass().getName();
            System.err.println("A JVM problem occurred: " + (null == message ? clazzName : new StringBuilder(clazzName).append(": ").append(message).toString()));
        } finally {
            if (error) {
                System.exit(1);
            }
        }
        return null;
    }

    private String printClientException(ClientErrorException exception, String defaultMessage) {
        String result = defaultMessage;
        InputStream response = (InputStream) exception.getResponse().getEntity();
        if (response != null) {
            try {
                String parsedResponse = new String(IOUtils.toCharArray(new AsciiReader(response)));
                JSONObject errorJson = new JSONObject(parsedResponse);
                String errorMessage = (String) errorJson.get("error_desc");
                if (errorJson.hasAndNotNull("error_id")) {
                    StringBuilder sb = new StringBuilder(errorMessage);
                    result = sb.append(" Server log exception ID: ").append(errorJson.getString("error_id")).toString();
                } else {
                    result = Strings.isEmpty(errorMessage) ? defaultMessage : errorMessage;
                }
            } catch (IOException | JSONException e) {
                //do nothing
            }
        }

        return result;
    }

    @Override
    protected Builder getContext() {
        return executionContext;
    }

    protected abstract void checkArguments(CommandLine cmd);

    /**
     * Adds this command-line tool's options.
     * <p>
     * Note following options are reserved:
     * <ul>
     * <li>-h / --help
     * <li>-t / --host
     * <li>-p / --port
     * <li>-l / --login
     * <li>-s / --password
     * <li>-A / --adminuser
     * <li>-P / --adminpass
     * </ul>
     *
     * @param options The options
     */
    @Override
    protected abstract void addOptions(Options options);

    @Override
    protected void optAuthenticate(CommandLine cmd) throws Exception {
        if (useBasicAuth()) {
            administrativeAuth(null, null, cmd, null);
            return;
        }
        super.optAuthenticate(cmd);
    }

    @Override
    protected void administrativeAuth(String login, String password, CommandLine cmd, Void authenticator) throws Exception {
        String authString = getAuthorizationHeader(cmd);
        String authorizationHeaderValue = "Basic " + Base64.encodeBase64String(authString.getBytes(Charsets.UTF_8));
        executionContext.header(AUTHORIZATION_HEADER_NAME, authorizationHeaderValue);
    }

    @Override
    protected Void getAuthenticator() {
        return null;
    }

    @Override
    protected int getAuthFailedExitCode() {
        return 403;
    }

    @Override
    protected Boolean requiresAdministrativePermission() {
        return null;
    }

    /**
     * Returns <code>true</code> if basic auth shall be used.
     *
     * <p>
     * Properties <code>"com.openexchange.rest.services.basic-auth.login"</code> and
     * <code>"com.openexchange.rest.services.basic-auth.password"</code> are required to be set.
     *
     * @return <code>true</code> if basic auth shall be used; <code>false</code> otherwise
     */
    protected boolean useBasicAuth() {
        return true;
    }

    @Override
    protected boolean isAuthEnabled(Void authenticator) {
        Boolean b = requiresAdministrativePermission();
        return b != null && b.booleanValue();
    }

    protected abstract WebTarget getEndpoint(CommandLine cmd);
}
