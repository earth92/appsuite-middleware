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

package com.openexchange.icap.test.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import com.openexchange.icap.ICAPCommons;
import com.openexchange.icap.ICAPCommunicationStrings;
import com.openexchange.icap.ICAPResponse;
import com.openexchange.icap.ICAPStatusCode;
import com.openexchange.icap.header.ICAPResponseHeader;
import com.openexchange.icap.header.OptionsICAPResponseHeader;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;

/**
 * {@link ICAPResponseFactory}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.2
 */
public final class ICAPResponseFactory {

    /**
     * Builds an {@link ICAPResponse} with the specified {@link ICAPStatusCode}
     * 
     * @param code The {@link ICAPStatusCode}
     * @return The {@link ICAPResponse}
     */
    public static ICAPResponse buildICAPResponse(ICAPStatusCode code) {
        return createBuilder(code).build();
    }

    /**
     * Builds an ICAP Options response with RESPMOD and REQMOD as viable options.
     * <ul><li>Response code: <code>200</code></li></ul>
     * 
     * @return The {@link ICAPResponse}
     */
    public static ICAPResponse buildICAPOptionsResponse() {
        ICAPResponse.Builder responseBuilder = createBuilder(ICAPStatusCode.OK);
        responseBuilder.addHeader(OptionsICAPResponseHeader.METHODS, "RESPMOD, REQMOD");
        responseBuilder.addHeader(OptionsICAPResponseHeader.SERVICE, "OX Dummy ICAP Server");
        responseBuilder.addHeader(OptionsICAPResponseHeader.ISTAG, UUID.randomUUID().toString());
        responseBuilder.addHeader(OptionsICAPResponseHeader.TRANSFER_PREVIEW, "*");
        responseBuilder.addHeader(OptionsICAPResponseHeader.OPTIONS_TTL, "3600");
        responseBuilder.addHeader(OptionsICAPResponseHeader.DATE, new Date().toString());
        responseBuilder.addHeader(OptionsICAPResponseHeader.PREVIEW, "1024");
        responseBuilder.addHeader(OptionsICAPResponseHeader.ALLOW, "204");
        responseBuilder.addHeader(OptionsICAPResponseHeader.ENCAPSULATED, "null-body=0");
        return responseBuilder.build();
    }

    /**
     * Builds a simple Continue response
     * <ul><li><li>Response code: <code>100</code></li></ul>
     * 
     * @return The continue {@link ICAPResponse}
     */
    public static ICAPResponse buildContinueResponse() {
        return createBuilder(ICAPStatusCode.CONTINUE).build();
    }

    /**
     * Builds a 'Not-Modified' response
     * <ul><li><li>Response code: <code>204</code></li></ul>
     * 
     * @return The continue {@link ICAPResponse}
     */
    public static ICAPResponse buildUnmodifiedResponse() {
        ICAPResponse.Builder builder = createBuilder(ICAPStatusCode.NO_CONTENT);
        builder.addHeader(ICAPResponseHeader.ISTAG, UUID.randomUUID().toString());
        return builder.build();
    }

    /**
     * Builds an ICAP infected found response
     * <ul>
     * <li>Response code: <code>200</code></li>
     * <li>Encapsulated code: <code>403</code></li>
     * </ul>
     * 
     * @return The {@link ICAPResponse}
     */
    public static ICAPResponse buildInfectedFoundResponse() {
        ICAPResponse.Builder builder = createBuilder(ICAPStatusCode.OK);
        builder.addHeader(ICAPResponseHeader.ISTAG, UUID.randomUUID().toString());
        builder.addHeader("X-Infection-Found", "Type=0; Resolution=2; Threat=Eicar-Test-Signature;");
        builder.addHeader("X-Violations-Found", "1");
        builder.addHeader("Encapsulated", "res-hdr=0, res-body=123");

        // Encapsulated Headers
        builder.withEncapsulatedStatusCode(ICAPStatusCode.FORBIDDEN.getCode());
        builder.withEncapsulatedStatusLine(compileStatusLine(ICAPStatusCode.FORBIDDEN));
        builder.addEncapsulatedHeader(ICAPResponseHeader.SERVER, "C-ICAP");
        builder.addEncapsulatedHeader("Content-Type", "html/text");

        // Encapsulated body
        builder.withEncapsulatedBody("<html><body><h1>VIRUS FOUND</h1></body></html>");
        return builder.build();
    }

    /**
     * Creates an {@link InputStream} from the specified {@link ICAPResponse}
     * 
     * @param response The {@link ICAPResponse} from which to create an {@link InputStream}
     * @return The {@link InputStream}
     */
    public static InputStream buildICAPResponseInputStream(ICAPResponse response) {
        StringBuilder responseBuilder = new StringBuilder(512);
        // Status line
        if (Strings.isNotEmpty(response.getStatusLine())) {
            responseBuilder.append(response.getStatusLine()).append(ICAPCommunicationStrings.CRLF);
        }
        // ICAP headers
        appendICAPHeaders(response, responseBuilder);
        responseBuilder.append(ICAPCommunicationStrings.CRLF);

        if (Strings.isNotEmpty(response.getEncapsulatedStatusLine())) {
            responseBuilder.append(response.getEncapsulatedStatusLine()).append(ICAPCommunicationStrings.CRLF);
        }
        // Encapsulated headers
        appendHeaders(response.getEncapsulatedHeaders(), responseBuilder);
        responseBuilder.append(ICAPCommunicationStrings.CRLF);
        // Encapsulated body (if available)
        if (Strings.isNotEmpty(response.getEncapsulatedBody())) {
            responseBuilder.append(response.getEncapsulatedBody());
            responseBuilder.append(ICAPCommunicationStrings.HTTP_TERMINATOR);
        }
        return new ByteArrayInputStream(responseBuilder.toString().getBytes(Charsets.UTF_8));
    }

    //////////////////////////// HELPERS /////////////////////////////

    /**
     * Appends the ICAP headers to the specified response builder. If the 'Encapsulated' header
     * is present, then it is appended at the end of the headers.
     * 
     * @param response The {@link ICAPResponse}
     * @param builder The response builder
     */
    private static void appendICAPHeaders(ICAPResponse response, StringBuilder builder) {
        if (response.getHeader(ICAPResponseHeader.ENCAPSULATED) == null) {
            appendHeaders(response.getHeaders(), builder);
            return;
        }

        Map<String, String> encapsulated = new HashMap<>(1);
        Map<String, String> headers = new HashMap<>(response.getHeaders().size());
        for (Entry<String, String> entry : response.getHeaders().entrySet()) {
            if (entry.getKey().equals(ICAPResponseHeader.ENCAPSULATED.toLowerCase())) {
                encapsulated.put(entry.getKey(), entry.getValue());
                continue;
            }
            headers.put(entry.getKey(), entry.getValue());
        }
        appendHeaders(headers, builder);
        appendHeaders(encapsulated, builder);
    }

    /**
     * Appends the specified headers to the specified response builder
     * 
     * @param headers The headers to append
     * @param builder The response builder to append them to
     */
    private static void appendHeaders(Map<String, String> headers, StringBuilder builder) {
        if (headers.isEmpty()) {
            return;
        }
        for (Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(ICAPCommunicationStrings.CRLF);
        }
    }

    /**
     * Creates an {@link ICAPResponse.Builder} with the specified status code and the
     * {@link ICAPResponseHeader#SERVER} header
     * 
     * @param code The code of the response builder
     * @return The {@link ICAPResponse.Builder}
     */
    private static ICAPResponse.Builder createBuilder(ICAPStatusCode code) {
        ICAPResponse.Builder builder = new ICAPResponse.Builder();
        builder.withStatusCode(code.getCode());
        builder.withStatusLine(compileStatusLine(code));
        builder.addHeader(ICAPResponseHeader.SERVER, ICAPTestProperties.ICAP_SERVER_ID);
        return builder;
    }

    /**
     * Compiles a status line out of the specified {@link ICAPStatusCode}
     * 
     * @param code The status code of the status line
     * @return The compiled status line
     */
    private static String compileStatusLine(ICAPStatusCode code) {
        StringBuilder builder = new StringBuilder(64);
        builder.append("ICAP/").append(ICAPCommons.ICAP_VERSION).append(' ');
        builder.append(code.getCode()).append(' ').append(code.getMessage());
        return builder.toString();
    }
}
