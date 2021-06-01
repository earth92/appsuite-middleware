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

package com.openexchange.groupware.update.tools.console;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import com.openexchange.groupware.update.UpdateTaskService;

/**
 * {@link ListUpdateTaskNamespaces}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.0
 */
public final class ListUpdateTaskNamespaces extends AbstractUpdateTasksCLT<Void> {

    //@formatter:off
    private static final String FOOTER = "This tools lists all namespaces for any update tasks and/or update task sets. The outcome of this tool can be used to " +
        " populate the property 'com.openexchange.groupware.update.excludedUpdateTasks'. Entries in that property will result in excluding all update tasks that are part " + 
        " of that particular namespace.";
    //@formatter:on

    private boolean printNamespacesOnly;

    /**
     * Entry point
     * 
     * @param args The command line arguments to pass
     */
    public static void main(String[] args) {
        new ListUpdateTaskNamespaces().execute(args);
    }

    /**
     * Initialises a new {@link ListUpdateTaskNamespaces}.
     */
    private ListUpdateTaskNamespaces() {
        super("listUpdateTaskNamespaces -n " + BASIC_MASTER_ADMIN_USAGE, FOOTER);
    }

    @Override
    protected void addOptions(Options options) {
        options.addOption(createSwitch("n", "namespaces-only", "Prints only the available namespaces without their update tasks", false));
    }

    @Override
    protected Void invoke(Options options, CommandLine cmd, String optRmiHostName) throws Exception {
        UpdateTaskService updateTaskService = getRmiStub(UpdateTaskService.RMI_NAME);
        Map<String, Set<String>> namespaceAware = updateTaskService.getNamespaceAware();
        if (printNamespacesOnly) {
            printNamespaceOnly(namespaceAware);
        } else {
            printEverything(namespaceAware);
        }
        return null;
    }

    @Override
    protected void checkOptions(CommandLine cmd) {
        printNamespacesOnly = cmd.hasOption('n');
    }

    /**
     * Prints everything (namespace + underlying update tasks)
     * 
     * @param map The {@link Map} containing the namespace aware update tasks
     */
    private void printEverything(Map<String, Set<String>> map) {
        for (Entry<String, Set<String>> entry : map.entrySet()) {
            printKey(entry.getKey());
            printValue(entry.getValue());
        }
    }

    /**
     * Prints only the namespace
     * 
     * @param map The {@link Map} containing the namespace aware update tasks
     */
    private void printNamespaceOnly(Map<String, Set<String>> map) {
        for (Entry<String, Set<String>> entry : map.entrySet()) {
            printKey(entry.getKey());
        }
    }

    /**
     * Prints the key
     * 
     * @param key the key to print
     */
    private void printKey(String key) {
        System.out.println("+- " + key);
    }

    /**
     * Prints the values
     * 
     * @param values The values to print
     */
    private void printValue(Set<String> values) {
        for (String c : values) {
            System.out.println("|--- " + c);
        }
    }
}
