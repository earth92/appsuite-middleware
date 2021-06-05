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

package com.openexchange.userfeedback.clt;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.java.AsciiReader;
import com.openexchange.java.Strings;

/**
 * {@link SendUserFeedback}
 *
 * @author <a href="mailto:jan.bauerdick@open-xchange.com">Jan Bauerdick</a>
 * @since v7.8.4
 */
public class SendUserFeedback extends AbstractUserFeedback {

    private static final String SUBJECT_LONG = "subject";
    private static final String SUBJECT_SHORT = "S";

    private static final String BODY_LONG = "body";
    private static final String BODY_SHORT = "b";

    private static final String COMPRESS_LONG = "compress";
    private static final String COMPRESS_SHORT = "c";

    private static final String RECIPIENTS_SHORT = "r";
    private static final String RECIPIENT_LONG = "recipients";

    private static final String SEND_PATH = "send";

    public static void main(String[] args) {
        new SendUserFeedback().execute(args);
    }

    @Override
    protected void checkArguments(CommandLine cmd) {
        // nothing to do
    }

    @Override
    protected void addOptions(Options options) {
        addGenericOptions(options);

        options.addOption(null, ENDPOINT_LONG, true, " URL to an alternative HTTP API endpoint. Example: 'https://192.168.0.1:8443/userfeedback/v1/send/'");
        options.addOption(SUBJECT_SHORT, SUBJECT_LONG, true, " The mail subject. Default: \"User Feedback Report: [time range]\".");
        options.addOption(BODY_SHORT, BODY_LONG, true, "The mail body (plain text).");
        options.addOption(COMPRESS_SHORT, COMPRESS_LONG, false, "Use to gzip-compress exported feedback.");
        Option recipients = new Option(RECIPIENTS_SHORT, RECIPIENT_LONG, true, "Single Recipient's mail address like \"Displayname <email@example.com>\" or the local path to a CSV file containing all the recipients, starting with an '@' (@/tmp/file.csv). Where the address is followed by the display name, seperated by a comma.");
        recipients.setRequired(true);
        options.addOption(recipients);
    }

    @Override
    protected WebTarget getEndpoint(CommandLine cmd) {
        String endpoint = cmd.getOptionValue(ENDPOINT_LONG, ENDPOINT_DEFAULT);
        endpoint = addPathIfRequired(endpoint, SEND_PATH);
        try {
            URI uri = new URI(endpoint);

            ClientConfig config = new ClientConfig();
            Client client = ClientBuilder.newClient(config);
            WebTarget baseTarget = client.target(uri);

            String contextGroup = cmd.getOptionValue(CONTEXT_GROUP_SHORT, CONTEXT_GROUP_DEFAULT);
            String type = cmd.getOptionValue(TYPE_SHORT, TYPE_DEFAULT);
            WebTarget target = baseTarget.path(contextGroup).path(type);

            if (cmd.hasOption(START_SHORT)) {
                target = target.queryParam("start", cmd.getOptionValue(START_SHORT).concat("000")); // convert seconds to ms
            }
            if (cmd.hasOption(END_SHORT)) {
                target = target.queryParam("end", cmd.getOptionValue(END_SHORT).concat("000")); // convert seconds to ms
            }
            return target;
        } catch (URISyntaxException e) {
            System.err.print("Unable to return endpoint: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    @Override
    protected Void invoke(Options option, CommandLine cmd, Builder context) throws Exception {
        JSONObject requestBody = new JSONObject();
        if (cmd.hasOption(SUBJECT_SHORT)) {
            requestBody.put("subject", cmd.getOptionValue(SUBJECT_SHORT));
        }
        if (cmd.hasOption(BODY_SHORT)) {
            requestBody.put("body", cmd.getOptionValue(BODY_SHORT));
        }
        requestBody.put("compress", cmd.hasOption(COMPRESS_SHORT));
        String recipients = cmd.getOptionValue(RECIPIENTS_SHORT);
        JSONArray array = new JSONArray();
        if (recipients.startsWith("@")) {
            recipients = recipients.substring(1, recipients.length());
            array = extractRecipientsFromFile(recipients);
        } else {
            array.add(0, extractSingleRecipient(recipients));
        }
        requestBody.put("recipients", array);
        InputStream response = null;
        response = context.post(Entity.json(requestBody.toString()), InputStream.class);
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject json = new JSONObject(new String(IOUtils.toCharArray(new AsciiReader(response))));
            if (json.hasAndNotNull("pgp")) {
                sb.append("A PGP-signed/encrypted email with user feedback was send to\n");
                JSONArray a = json.getJSONArray("pgp");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
            if (json.hasAndNotNull("sign")) {
                sb.append("A PGP-signed email with user feedback was send to\n");
                JSONArray a = json.getJSONArray("sign");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
            if (json.hasAndNotNull("encrypt")) {
                sb.append("A PGP-encrypted email with user feedback was send to\n");
                JSONArray a = json.getJSONArray("encrypt");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
            if (json.hasAndNotNull("normal")) {
                sb.append("An email with user feedback was send to\n");
                JSONArray a = json.getJSONArray("normal");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
            if (json.hasAndNotNull("fail")) {
                sb.append("The following addresses are invalid and therefore ignored\n");
                JSONArray a = json.getJSONArray("fail");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
            if (json.hasAndNotNull("pgpFail")) {
                sb.append("The following addresses are linked with an invalid PGP key and therefore ignored\n");
                JSONArray a = json.getJSONArray("pgpFail");
                for (int i = 0; i < a.length(); i++) {
                    sb.append(a.get(i)).append("\n");
                }
            }
        } catch (@SuppressWarnings("unused") JSONException e) {
            // will not happen
        }
        System.out.println(sb.toString());
        System.exit(0);
        return null;
    }

    private JSONArray extractRecipientsFromFile(String filename) throws IOException, JSONException {
        JSONArray array = new JSONArray();
        CSVParser parser = null;
        FileReader reader = null;
        try {
            reader = new FileReader(filename);
            parser = new CSVParser(reader, CSVFormat.DEFAULT);
            Iterator<CSVRecord> it = parser.iterator();
            while (it.hasNext()) {
                CSVRecord record = it.next();
                String address = record.get(0);
                String displayName = "";
                String pgp = "";
                if (record.size() >= 2) {
                    displayName = record.get(1);
                }
                if (record.size() == 3) {
                    pgp = record.get(2);
                }
                JSONObject json = getAddressJSON(address, displayName);
                if (null != pgp && Strings.isNotEmpty(pgp)) {
                    try {
                        pgp = new String(Files.readAllBytes(Paths.get(pgp)));
                        json.put("pgp_key", pgp);
                    } catch (@SuppressWarnings("unused") IOException e) {
                        exitWithError("Could not load PGP key " + pgp);
                    }
                }
                array.add(0, json);
            }
        } catch (@SuppressWarnings("unused") IOException e) {
            exitWithError("Could not load CSV file " + filename);
        } finally {
            if (null != parser) {
                parser.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
        return array;
    }

    private JSONObject extractSingleRecipient(String recipients) throws JSONException {
        String publicKeyFile = null;
        JSONObject json = null;
        try {
            boolean hasPublicKeyFile = recipients.contains(":");
            int startOfAddress = recipients.lastIndexOf("<");
            String address = "";
            String displayName = "";
            if (startOfAddress >= 0) {
                address = recipients.substring(startOfAddress + 1, recipients.lastIndexOf(">"));
                displayName = startOfAddress != 0 ? recipients.substring(0, startOfAddress - 1) : "";
            } else {
                address = recipients;
            }
            json = getAddressJSON(address, displayName);
            if (hasPublicKeyFile) {
                int startOfPublicKeyFile = recipients.lastIndexOf(":");
                if (displayName.contains(":")) {
                    if (startOfAddress > startOfPublicKeyFile) {
                        return json;
                    }
                }
                publicKeyFile = recipients.substring(startOfPublicKeyFile + 1);
                String pgp = new String(Files.readAllBytes(Paths.get(publicKeyFile)));
                json.put("pgp_key", pgp);
            }
        } catch (@SuppressWarnings("unused") IOException e) {
            exitWithError("Could not load PGP public key file " + publicKeyFile);
        }
        return json;
    }

    private JSONObject getAddressJSON(String address, String displayName) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("address", address);
        json.put("displayName", displayName);
        return json;
    }

    @Override
    protected void checkOptions(CommandLine cmd) {
        // nothing to do
    }

    @Override
    protected String getFooter() {
        return "Send user feedback via mail.";
    }

    @Override
    protected String getName() {
        return "senduserfeedback -U <user:password> [OPTIONS]";
    }

    @Override
    protected String getHeader() {
        return "senduserfeedback -U <user:password> -s 1487348317 -r \"Displayname <email@example.com>\"";
    }

    private void exitWithError(String message) {
        System.err.println(message);
        System.exit(1);
    }

}
