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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * {@link AbstractCLI} - The basic super class for command-line tools.
 *
 * @param <R> - The return type
 * @param <C> - The execution context type
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class AbstractCLI<R, C> {

    /** Defines the extended width of the help screen */
    public static final int EXTENDED_WIDTH = 120;

    /** The associated options */
    protected Options options;

    /**
     * Initializes a new {@link AbstractCLI}.
     */
    protected AbstractCLI() {
        super();
    }

    /**
     * Executes the command-line tool.
     *
     * @param args The arguments
     * @return The return value
     */
    public R execute(String[] args) {
        Options options = newOptions();
        boolean error = true;
        try {
            // Option for help
            options.addOption(createSwitch("h", "help", "Prints this help text", false));

            // Add other options
            addOptions(options);

            // Check if help output is requested
            if (helpRequested(args)) {
                printHelp(options);
                System.exit(0);
            }

            // Initialize command-line parser & parse arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            // Check other mandatory options
            checkOptions(cmd, options);

            R retval = null;
            try {
                retval = invoke(options, cmd, getContext());
            } catch (Exception e) {
                Throwable t = e.getCause();
                throw new ExecutionFault(null == t ? e : t);
            }

            error = false;
            return retval;
        } catch (ExecutionFault e) {
            Throwable t = e.getCause();
            String message = t.getMessage();
            System.err.println(null == message ? "An error occurred." : message);
        } catch (MissingOptionException e) {
            System.err.println(e.getMessage());
            printHelp(options);
        } catch (ParseException e) {
            System.err.println("Unable to parse command line: " + e.getMessage());
            printHelp(options);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            String clazzName = e.getClass().getName();
            System.err.println("A runtime error occurred: " + (null == message ? clazzName : new StringBuilder(clazzName).append(": ").append(message).toString()));
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

    /**
     * Checks if help output is requested.
     *
     * @param args The command line arguments to examine
     * @return <code>true</code> if help output is requested; otherwise <code>false</code>
     */
    protected boolean helpRequested(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        for (String s : args) {
            if ("-h".equals(s) || "--help".equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Invokes the CLI method.
     *
     * @param option The options
     * @param cmd The command line providing parameters/options
     * @param executionContext The execution context
     * @return The return value
     * @throws Exception If invocation fails
     */
    protected abstract R invoke(Options option, CommandLine cmd, C executionContext) throws Exception;

    /**
     * Creates an initially empty {@link ReservedOptions} instance.
     *
     * @return The new options
     */
    protected ReservedOptions newOptions() {
        ReservedOptions options = new ReservedOptions();
        this.options = options;
        return options;
    }

    /**
     * Adds this command-line tool's options.
     * <p>
     * Note following options are reserved:
     * <ul>
     * <li>-h / --help
     * </ul>
     *
     * @param options The options
     */
    protected abstract void addOptions(Options options);

    /**
     * Checks other mandatory options.
     *
     * @param cmd The command line
     * @param options The associated options
     * @throws ParseException If check fails
     */
    @SuppressWarnings("unused")
    protected void checkOptions(CommandLine cmd, Options options) throws ParseException {
        checkOptions(cmd);
    }

    /**
     * Checks other mandatory options.
     *
     * @param cmd The command line
     * @throws ParseException If check fails
     */
    protected abstract void checkOptions(CommandLine cmd) throws ParseException;

    /**
     * Prints the <code>--help</code> text.
     */
    protected void printHelp() {
        Options options = this.options;
        if (null != options) {
            printHelp(options);
        }
    }

    /**
     * Prints the <code>--help</code> text.
     *
     * @param options The help output
     */
    protected void printHelp(Options options) {
        printHelp(options, EXTENDED_WIDTH);
    }

    /**
     * Prints the <code>--help</code> text.
     *
     * @param options The help output
     * @param width The width of the help screen
     */
    protected void printHelp(Options options, int width) {
        printHelp(options, width, false);
    }

    /**
     * Prints the <code>--help</code> text.
     *
     * @param options The help output
     * @param width The width of the help screen
     * @param usage Whether to automatically generate the usage line
     */
    protected void printHelp(Options options, int width, boolean usage) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(width, getName(), getHeader(), options, formatFooter(), usage);
    }

    /**
     * Gets the banner to display at the end of the help
     *
     * @return The banner to display at the end of the help
     */
    protected abstract String getFooter();

    /**
     * Gets the syntax for this application.
     *
     * @return The syntax for this application
     */
    protected abstract String getName();

    /**
     * Returns the execution context {@link C}
     *
     * @return the execution context {@link C} C
     */
    protected abstract C getContext();

    /**
     * Returns the command line tool's header
     *
     * @return the command line tool's header
     */
    protected String getHeader() {
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------------

    /**
     * Prepends two new lines for the footer
     *
     * @return The footer with two new lines prepended
     */
    private String formatFooter() {
        return "\n\n" + getFooter();
    }

    /**
     * Parses & validates the port value for given option.
     * <p>
     * Exits gracefully if port value is invalid.
     *
     * @param opt The option name
     * @param defaultValue The default value
     * @param cmd The command line
     * @param options The options
     * @return The port value
     */
    protected int parsePort(char opt, int defaultValue, CommandLine cmd, Options options) {
        int port = defaultValue;
        // Check option & parse if present
        String sPort = cmd.getOptionValue(opt);
        if (null == sPort) {
            return port;
        }

        try {
            port = Integer.parseInt(sPort.trim());
        } catch (NumberFormatException e) {
            System.err.println("Port parameter is not a number: " + sPort);
            printHelp(options);
            System.exit(1);
        }
        if (port < 1 || port > 65535) {
            System.err.println("Port argument '-" + opt + "' is out of range: " + sPort + ". Valid range is from 1 to 65535.");
            printHelp(options);
            System.exit(1);
        }
        return port;
    }

    /**
     * Parses & validates the <code>int</code> value for given option.
     * <p>
     * Exits gracefully if <code>int</code> value is invalid.
     *
     * @param opt The option name
     * @param defaultValue The default value
     * @param cmd The command line
     * @param options The options
     * @return The <code>int</code> value
     */
    protected int parseInt(char opt, int defaultValue, CommandLine cmd, Options options) {
        int i = defaultValue;
        // Check option & parse if present
        String sInt = cmd.getOptionValue(opt);
        if (null != sInt) {
            try {
                i = Integer.parseInt(sInt.trim());
            } catch (NumberFormatException e) {
                System.err.println("Integer option '-" + opt + "' is not a number: " + sInt);
                printHelp(options);
                System.exit(1);
            }
        }
        return i;
    }

    /**
     * Parses & validates the <code>int</code> value for given option.
     * <p>
     * Exits gracefully if <code>int</code> value is invalid.
     *
     * @param longOpt The long option name
     * @param defaultValue The default value
     * @param cmd The command line
     * @param options The options
     * @return The <code>int</code> value
     */
    protected int parseInt(String longOpt, int defaultValue, CommandLine cmd, Options options) {
        int i = defaultValue;
        // Check option & parse if present
        String sInt = cmd.getOptionValue(longOpt);
        if (null == sInt) {
            return i;
        }

        try {
            i = Integer.parseInt(sInt.trim());
        } catch (NumberFormatException e) {
            System.err.println("Integer option '--" + longOpt + "' is not a number: " + sInt);
            printHelp(options);
            System.exit(1);
        }
        return i;
    }

    /**
     * Gets the mandatory integer value for the specified option
     *
     * <p>Exits gracefully if <code>int</code> value is invalid or missing
     * (i.e. equals with the default value which should be dictated by the invoker).</p>
     *
     * @param opt The option name
     * @param defaultValue The default value
     * @param cmd The command line
     * @param options The options
     * @return The <code>int</code> value
     */
    protected int getMandatoryInt(char opt, int defaultValue, CommandLine cmd, Options options) {
        int candidate = parseInt(opt, defaultValue, cmd, options);
        if (candidate == defaultValue) {
            System.err.println("The '-" + opt + "' is mandatory but not set.");
            printHelp();
            System.exit(1);
        }
        return candidate;
    }

    /**
     * Creates a switch {@link Option} with no arguments
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     * @return the new {@link Option}
     */
    protected Option createSwitch(String shortName, String longName, String description, boolean mandatory) {
        return createOption(shortName, longName, false, description, mandatory);
    }

    /**
     * Creates an {@link Option} with arguments
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param argumentName The argument's name
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     * @return the new {@link Option}
     */
    protected Option createArgumentOption(String shortName, String longName, String argumentName, String description, boolean mandatory) {
        return createOption(shortName, longName, argumentName, true, description, mandatory);
    }

    /**
     * Creates an {@link Option} with arguments
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param argumentName The argument's name
     * @param argumentType The argument's type
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     * @return the new {@link Option}
     */
    protected Option createArgumentOption(String shortName, String longName, String argumentName, Class<?> argumentType, String description, boolean mandatory) {
        return createOption(shortName, longName, argumentName, true, argumentType, description, mandatory);
    }

    /**
     * Create an {@link Option}
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param hasArgs boolean flag to indicate whether or not the option has arguments
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     */
    protected Option createOption(String shortName, String longName, boolean hasArgs, String description, boolean mandatory) {
        return createOption(shortName, longName, "arg", hasArgs, description, mandatory);
    }

    /**
     * Create an {@link Option}
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param argName The argument's name
     * @param hasArgs boolean flag to indicate whether or not the option has arguments
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     */
    protected Option createOption(String shortName, String longName, String argName, boolean hasArgs, String description, boolean mandatory) {
        return Option.builder(shortName).longOpt(longName).hasArg(hasArgs).argName(argName).desc(description).required(mandatory).build();
    }

    /**
     * Create an {@link Option}
     *
     * @param shortName The short name of the {@link Option}
     * @param longName The long name of the {@link Option}
     * @param argName The argument's name
     * @param hasArgs boolean flag to indicate whether or not the option has arguments
     * @param type The argument's type
     * @param description The description of the {@link Option}
     * @param mandatory boolean flag to indicate whether the {@link Option} is mandatory
     */
    protected Option createOption(String shortName, String longName, String argName, boolean hasArgs, Class<?> type, String description, boolean mandatory) {
        return Option.builder(shortName).longOpt(longName).hasArg(hasArgs).argName(argName).desc(description).type(type).required(mandatory).build();
    }
}
