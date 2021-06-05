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

package com.openexchange.admin.console.user;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import com.openexchange.cli.AbstractCLI;
import com.openexchange.exception.OXException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.password.mechanism.PasswordMech;
import com.openexchange.password.mechanism.PasswordDetails;
import com.openexchange.password.mechanism.stock.StockPasswordMechs;

/**
 * {@link GenerateMasterPasswordCLT}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class GenerateMasterPasswordCLT extends AbstractCLI<Void, Map<GenerateMasterPasswordCLT.Parameter, String>> {

    /**
     * @param args
     */
    public static void main(String[] args) {
        new GenerateMasterPasswordCLT().execute(args);
    }

    private static final String SYNTAX = "generatempasswd [-A <adminuser>] [-P <adminpassword>] [-e <encryption>] [-f </path/for/mpasswdfile>]";
    private static final String FOOTER = "Command-line tool to generate the master password file.";

    enum Parameter {
        adminuser, adminpass, encryption, mpasswdfile, salt
    }

    private final Map<Parameter, String> parameters = new EnumMap<>(GenerateMasterPasswordCLT.Parameter.class);

    /**
     * Initializes a new {@link GenerateMasterPasswordCLT}.
     */
    public GenerateMasterPasswordCLT() {
        super();
    }

    @Override
    protected Void invoke(Options option, CommandLine cmd, Map<Parameter, String> context) throws Exception {
        File file = new File(context.get(Parameter.mpasswdfile));
        System.out.println((file.createNewFile() ? "Created a new file in '" : "Using already existing file '") + file.getAbsolutePath() + "'");
        StringBuilder builder = new StringBuilder();
        boolean printUsage = false;
        String exceptionMessage = "";
        try {
            List<String> lines = readPasswordFile(file, context);
            writePasswordFile(file, lines);
            builder.append("Saved password for user '").append(context.get(Parameter.adminuser)).append("' and encryption '").append(context.get(Parameter.encryption)).append("' in '").append(context.get(Parameter.mpasswdfile)).append("'.");
            System.out.println(builder.toString());
            return null;
        } catch (IOException e) {
            exceptionMessage = e.getMessage();
        }
        builder.append("Unable to save password for user '").append(context.get(Parameter.adminuser)).append("' and encryption '").append(context.get(Parameter.encryption)).append("' in '").append(context.get(Parameter.mpasswdfile)).append("'.");
        builder.append("\n").append(exceptionMessage);
        System.err.println(builder.toString());
        if (printUsage) {
            printUsage(-1);
        }

        return null;
    }

    @Override
    protected void addOptions(Options options) {
        options.addOption(createOption("A", "adminuser", true, "master Admin user name (Default: oxadminmaster)", false));
        options.addOption(createOption("P", "adminpass", true, "master Admin password", false));
        options.addOption(createOption("f", "mpasswdfile", true, "Path to mpasswd (Default: /opt/open-xchange/etc/mpasswd)", false));
        options.addOption(createOption("e", "encryption", true, "Encryption algorithm to use for the password Possible values: CRYPT, SHA, SHA-256, SHA-512, BCRYPT (default)", false));
        options.addOption(createOption("h", "help", false, "Prints this help text", false));
    }

    @Override
    protected void checkOptions(CommandLine cmd) {
        initParameters();
        StringBuilder builder = new StringBuilder();

        boolean printUsage = false;
        String exceptionMessage = "";
        try {
            if (cmd.hasOption("A")) {
                parameters.put(Parameter.adminuser, cmd.getOptionValue("A"));
            }
            if (cmd.hasOption("e")) {
                String mechId = cmd.getOptionValue("e");
                getPasswordMechFor(mechId);
                parameters.put(Parameter.encryption, mechId);
            }
            String clearPassword;
            if (cmd.hasOption("P")) {
                clearPassword = cmd.getOptionValue("P");
            } else {
                builder.append("Enter password for user ").append(parameters.get(Parameter.adminuser)).append(": ");
                Console console = System.console();
                char[] passwd;
                if (console != null && (passwd = console.readPassword("[%s]", builder.toString())) != null) {
                    clearPassword = new String(passwd);
                } else {
                    BufferedWriter bufferWrite = new BufferedWriter(new OutputStreamWriter(System.out, Charset.forName("UTF-8")));
                    bufferWrite.write(builder.toString());
                    bufferWrite.flush();

                    BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in, Charset.forName("UTF-8")));
                    clearPassword = bufferRead.readLine();
                }
            }
            PasswordDetails passwordDetails = encryptPassword(parameters.get(Parameter.encryption), clearPassword);
            clearPassword = null;
            parameters.put(Parameter.adminpass, passwordDetails.getEncodedPassword());
            byte[] salt = passwordDetails.getSalt();
            if (salt != null) {
                String saltString = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
                parameters.put(Parameter.salt, saltString);
            }
            if (cmd.hasOption("f")) {
                parameters.put(Parameter.mpasswdfile, cmd.getOptionValue("f"));
            }
            return;
        } catch (IllegalArgumentException | OXException e) {
            exceptionMessage = e.getMessage();
            printUsage = true;
        } catch (IOException e) {
            exceptionMessage = e.getMessage();
        }

        builder.append("\n").append(exceptionMessage);
        System.err.println(builder.toString());
        if (printUsage) {
            printUsage(-1);
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
    protected Map<Parameter, String> getContext() {
        return parameters;
    }

    ///////////////////////////////////// HELPERS ////////////////////////////////

    /**
     * Reads the specified password file
     *
     * @param file The file to read
     * @param context a map with the command line values for adminuser, encryption and adminpass
     * @return A {@link List} with the lines of the file
     * @throws FileNotFoundException If the file is not found
     * @throws IOException if an I/O error is occurred
     */
    private List<String> readPasswordFile(File file, Map<Parameter, String> context) throws FileNotFoundException, IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8))) {
            List<String> lines = new LinkedList<String>();
            boolean updated = false;
            StringBuilder builder = new StringBuilder(96);
            for (String line; (line = br.readLine()) != null;) {
                if (!line.startsWith("#") && Strings.isNotEmpty(line)) {
                    builder.append(context.get(Parameter.adminuser)).append(":").append(context.get(Parameter.encryption)).append(":").append(context.get(Parameter.adminpass));
                    if (Strings.isNotEmpty(context.get(Parameter.salt))) {
                        builder.append(":").append(context.get(Parameter.salt));
                    }
                    lines.add(builder.toString());
                    updated = true;
                } else {
                    lines.add(line);
                }
                builder.setLength(0);
            }

            if (!updated) {
                builder.append(context.get(Parameter.adminuser)).append(":").append(context.get(Parameter.encryption)).append(":").append(context.get(Parameter.adminpass));
                if (Strings.isNotEmpty(context.get(Parameter.salt))) {
                    builder.append(":").append(context.get(Parameter.salt));
                }
                lines.add(builder.toString());
            }
            return lines;
        }
    }

    /**
     * Writes the specified lines to the specified file
     *
     * @param file The file to write to
     * @param lines The lines to write to the file
     */
    private void writePasswordFile(File file, List<String> lines) {
        boolean error = true;
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8));) {
            for (String line : lines) {
                writer.append(line).append(Strings.getLineSeparator());
            }
            writer.flush();
            error = false;
        } catch (UnsupportedEncodingException e) {
            System.err.println("Unsupported encoding: 'UTF-8'");
        } catch (FileNotFoundException e) {
            System.err.println("File not found: '" + file.getAbsolutePath() + "'");
        } catch (IOException e) {
            System.err.println("I/O error occurred: '" + e.getMessage() + "'");
        } finally {
            if (error) {
                System.exit(-1);
            }
        }
    }

    /**
     * Encrypt the specified password
     *
     * @param encryption The encryption algorithm
     * @param password The plain-text password to encrypt
     * @return {@link PasswordDetails} containing the password details
     * @throws OXException
     * @throws IllegalArgumentException if the request encryption algorithm string is either <code>null</code>, or empty, or unknown
     */
    private PasswordDetails encryptPassword(final String encryption, final String password) throws OXException {
        PasswordMech pm = getPasswordMechFor(encryption);
        return pm.encode(password);
    }

    /**
     * Initialise defaults
     */
    private void initParameters() {
        parameters.put(Parameter.adminuser, "oxadminmaster");
        parameters.put(Parameter.adminpass, null);
        parameters.put(Parameter.encryption, "bcrypt");
        parameters.put(Parameter.mpasswdfile, "/opt/open-xchange/etc/mpasswd");
    }

    /**
     * Print usage
     *
     * @param exitCode
     */
    private final void printUsage(int exitCode) {
        printHelp();
        System.exit(exitCode);
    }

    /**
     * Gets the password mechanism for given identifier
     *
     * @param identifier The identifier
     * @return The password mechanism
     * @throws IllegalArgumentException if the identifier is either <code>null</code>, or empty, or unknown
     */
    private PasswordMech getPasswordMechFor(String identifier) {
        if (Strings.isEmpty(identifier)) {
            throw new IllegalArgumentException("The identifier for the password mechanism can neither be 'null' nor empty.");
        }
        String id = Strings.toUpperCase(identifier);
        if (false == id.startsWith("{")) {
            id = new StringBuilder(id.length() + 1).append('{').append(id).toString();
        }
        if (false == id.endsWith("}")) {
            id = new StringBuilder(id.length() + 1).append(id).append('}').toString();
        }

        for (StockPasswordMechs value : StockPasswordMechs.values()) {
            if (id.equals(value.getIdentifier())) {
                return value.getPasswordMech();
            }
        }
        throw new IllegalArgumentException("The identifier '" + identifier + "' for the password mechanism is unknown.");
    }
}
