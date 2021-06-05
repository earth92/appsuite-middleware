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

package com.openexchange.report.console;

import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import com.openexchange.ajax.Client;
import com.openexchange.auth.rmi.RemoteAuthenticator;
import com.openexchange.cli.AbstractRmiCLI;
import com.openexchange.cli.AsciiTable;
import com.openexchange.report.internal.LoginCounterRMIService;

/**
 * {@link LastLoginTimeStampTool}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class LastLoginTimeStampTool extends AbstractRmiCLI<Void> {

    private static final String HEADER = "Prints the time stamp of the last login for a user using a certain client.";
    private static final String SYNTAX = "lastlogintimestamp -c <contextId> [-u <userId> | -i <userId>] -t <clientId> [-d <datePattern>] | --listclients " + BASIC_MASTER_ADMIN_USAGE;
    private static final String FOOTER = "Examples:\n./lastlogintimestamp -c 1 -u 6 -t open-xchange-appsuite\n./lastlogintimestamp -c 1 -u 6 -t open-xchange-appsuite -d \"yyyy.MM.dd G 'at' HH:mm:ss z\"";

    public static void main(final String[] args) {
        new LastLoginTimeStampTool().execute(args);
    }

    private int userId = -1;
    private int contextId = -1;
    private String client = null;
    private String pattern = null;

    /**
     * Initializes a new {@link LastLoginTimeStampTool}.
     */
    private LastLoginTimeStampTool() {
        super();
    }

    @Override
    protected void administrativeAuth(String login, String password, CommandLine cmd, RemoteAuthenticator authenticator) throws RemoteException {
        authenticator.doAuthentication(login, password);
    }

    @Override
    protected void addOptions(Options options) {
        options.addOption(createArgumentOption("c", "context", "contextId", "A valid (numeric) context identifier", false));
        options.addOption(createArgumentOption("u", "user", "userId", "A valid (numeric) user identifier", false));
        options.addOption(createArgumentOption("d", "datepattern", "datePattern", "The optional date pattern used for formatting retrieved time stamp; e.g \"EEE, d MMM yyyy HH:mm:ss Z\" would yield \"Wed, 4 Jul 2001 12:08:56 -0700\"", false));
        options.addOption(createSwitch("l", "list-clients", "Outputs a table of known client identifiers", false));

        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setRequired(false);
        optionGroup.addOption(createArgumentOption("i", null, "userId", "A valid (numeric) user identifier. As alternative for the \"-u, --user\" option.", false));
        optionGroup.addOption(createArgumentOption("t", "client", "clientId", "A client identifier; e.g \"open-xchange-appsuite\" for App Suite UI. Execute \"./lastlogintimestamp --listclients\" to get a listing of known identifiers.", false));
        options.addOptionGroup(optionGroup);
    }

    @Override
    protected Void invoke(Options options, CommandLine cmd, String optRmiHostName) throws Exception {
        boolean error = true;
        try {
            LoginCounterRMIService rmiService = getRmiStub(optRmiHostName, LoginCounterRMIService.RMI_NAME);
            List<Object[]> lastLoginTimeStamp = rmiService.getLastLoginTimeStamp(userId, contextId, client);
            if (null == lastLoginTimeStamp || lastLoginTimeStamp.isEmpty()) {
                System.out.println("No matching entry found.");
            } else if (1 == lastLoginTimeStamp.size()) {
                SimpleDateFormat sdf = new SimpleDateFormat(null == pattern ? "EEE, d MMM yyyy HH:mm:ss z" : pattern, Locale.US);
                final Object[] objs = lastLoginTimeStamp.get(0);
                System.out.println(sdf.format(objs[0]));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(null == pattern ? "EEE, d MMM yyyy HH:mm:ss z" : pattern, Locale.US);
                for (final Object[] objs : lastLoginTimeStamp) {
                    System.out.println(sdf.format(objs[0]) + " -- " + objs[1]);
                }
            }
            error = false;
        } catch (MalformedURLException e) {
            System.err.println("URL to connect to server is invalid: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Unable to communicate with the server: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Problem in runtime: " + e.getMessage());
            printHelp();
        } finally {
            if (error) {
                System.exit(1);
            }
        }
        return null;
    }

    @Override
    protected Boolean requiresAdministrativePermission() {
        return Boolean.TRUE;
    }

    @Override
    protected void checkOptions(CommandLine cmd) {
        if (cmd.hasOption("l")) {
            printClients();
            System.exit(0);
        }
        if (!cmd.hasOption('t')) {
            System.err.println("Missing client identifier.");
            printHelp();
            System.exit(1);
        }
        client = cmd.getOptionValue('t');

        if (cmd.hasOption('d')) {
            pattern = cmd.getOptionValue('d');
        }

        if (!cmd.hasOption('c')) {
            System.err.println("Missing context identifier.");
            printHelp();
            System.exit(1);
        }
        String optionValue = cmd.getOptionValue('c');
        try {
            contextId = Integer.parseInt(optionValue.trim());
        } catch (NumberFormatException e) {
            System.err.println("Context identifier parameter is not a number: " + optionValue);
            printHelp();
            System.exit(1);
        }

        if (cmd.hasOption('u')) {
            optionValue = cmd.getOptionValue('u');
        } else if (cmd.hasOption('i')) {
            optionValue = cmd.getOptionValue('i');
        } else {
            System.err.println("Missing user identifier.");
            printHelp();
            System.exit(1);
        }
        try {
            userId = Integer.parseInt(optionValue.trim());
        } catch (NumberFormatException e) {
            System.err.println("User identifier parameter is not a number: " + optionValue);
            printHelp();
            System.exit(1);
        }
    }

    @Override
    protected String getFooter() {
        return FOOTER;
    }

    @Override
    protected String getName() {
        return SYNTAX;
    }

    @Override
    protected String getHeader() {
        return HEADER;
    }

    //////////////////////////////// HELPERS ///////////////////////////////////

    /**
     * Prints the clients
     */
    private void printClients() {
        AsciiTable table = new AsciiTable();
        table.setMaxColumnWidth(45);

        table.addColumn(new AsciiTable.Column("Client ID"));
        table.addColumn(new AsciiTable.Column("Description"));
        for (Client client : Client.values()) {
            AsciiTable.Row row = new AsciiTable.Row();
            row.addValue(client.getClientId());
            row.addValue(client.getDescription());
            table.addData(row);
        }

        table.calculateColumnWidth();
        table.render();
    }

}
