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

package com.openexchange.mail.text;

import java.io.IOException;
import javax.mail.MessageRemovedException;
import com.openexchange.exception.OXException;
import com.openexchange.html.HtmlService;
import com.openexchange.java.CharsetDetector;
import com.openexchange.java.Strings;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.config.MailProperties;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.dataobjects.MailPart;
import com.openexchange.mail.mime.ContentType;
import com.openexchange.mail.mime.MessageHeaders;
import com.openexchange.mail.utils.MessageUtility;
import com.openexchange.server.services.ServerServiceRegistry;

/**
 * {@link TextProcessing} - Various methods for text processing
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class TextProcessing {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(TextProcessing.class);

    private static final String SPLIT_LINES = "\r?\n";

    private static final char CHAR_BREAK = '\n';

    private static String foldLine(String line, int linewrap, String prefix) {
        int end;
        char c;
        /*
         * Strip trailing spaces and newlines
         */
        String s;
        final int used;
        {
            final String foldMe;
            if (null == prefix) {
                used = 0;
                foldMe = line;
            } else {
                used = prefix.length();
                foldMe = line.substring(used);
            }
            final int mlen = foldMe.length() - 1;
            for (end = mlen; end >= 0; end--) {
                c = foldMe.charAt(end);
                if (c != '\r' && c != '\n') {
                    break;
                }
            }
            if (end != mlen) {
                s = foldMe.substring(0, end + 1);
            } else {
                s = foldMe;
            }
        }
        /*
         * Check if the string fits now
         */
        int total = used + s.length();
        if (total <= linewrap) {
            return used > 0 ? new StringBuilder(total).append(prefix).append(s).toString() : s;
        }
        /*
         * Fold the string
         */
        final StringBuilder sb = new StringBuilder(total);
        char lastc = 0;
        if (used > 0) {
            while (total > linewrap) {
                int lastspace = -1;
                for (int i = 0; i < s.length(); i++) {
                    if (lastspace != -1 && used + i > linewrap) {
                        break;
                    }
                    c = s.charAt(i);
                    if ((c == ' ' || c == '\t') && !(lastc == ' ' || lastc == '\t')) {
                        lastspace = i;
                    }
                    lastc = c;
                }
                if (lastspace < 0) {
                    /*
                     * No space, use the whole thing
                     */
                    sb.append(prefix).append(s);
                    return sb.toString();
                }
                sb.append(prefix);
                sb.append(s.substring(0, lastspace));
                sb.append(CHAR_BREAK);
                lastc = s.charAt(lastspace);
                // sb.append(lastc);
                s = s.substring(lastspace + 1);
                total = used + s.length();
            }
            sb.append(prefix).append(s);
            return sb.toString();
        }
        /*
         * No prefix given
         */
        while (s.length() > linewrap) {
            int lastspace = -1;
            for (int i = 0; i < s.length(); i++) {
                if (lastspace != -1 && i > linewrap) {
                    break;
                }
                c = s.charAt(i);
                if ((c == ' ' || c == '\t') && !(lastc == ' ' || lastc == '\t')) {
                    lastspace = i;
                }
                lastc = c;
            }
            if (lastspace < 0) {
                /*
                 * No space, use the whole thing
                 */
                sb.append(s);
                return sb.toString();
            }
            sb.append(s.substring(0, lastspace));
            sb.append(CHAR_BREAK);
            lastc = s.charAt(lastspace);
            // sb.append(lastc);
            s = s.substring(lastspace + 1);
        }
        sb.append(s);
        return sb.toString();
    }

    /**
     * Performs the line folding after specified number of characters through parameter <code>linewrap</code>. Occurring HTML links are
     * excluded.
     * <p>
     * If parameter <code>isHtml</code> is set to <code>true</code> the content is returned unchanged.
     *
     * @param content The plain text content to fold
     * @param linewrap The number of characters which may fit into a line
     * @return The line-folded content
     */
    public static String performLineFolding(String content, int linewrap) {
        if (linewrap <= 0) {
            return content;
        }
        final String[] lines = content.split(SPLIT_LINES);
        if (lines.length > 0) {
            final StringBuilder sb = new StringBuilder(content.length() + 128);
            {
                final String foldMe = lines[0];
                sb.append(foldLine(foldMe, linewrap, getQuotePrefix(foldMe)));
            }
            for (int i = 1; i < lines.length; i++) {
                final String foldMe = lines[i];
                sb.append(CHAR_BREAK).append(foldLine(foldMe, linewrap, getQuotePrefix(foldMe)));
            }
            return sb.toString();
        }
        return content;
    }

    private static String getQuotePrefix(String line) {
        if (line.length() == 0) {
            return null;
        }
        final int length = line.length();
        final StringBuilder sb = new StringBuilder(8);
        int lastGT = -1;
        for (int i = 0; i < length; i++) {
            final char c = line.charAt(i);
            if (c == '>') {
                sb.append(c);
                lastGT = i;
            } else if (Strings.isWhitespace(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        if (lastGT == -1) {
            /*
             * No '>' character found
             */
            return null;
        }
        /*
         * Allow only 1 whitespace character after last '>' character
         */
        final int len = sb.length();
        if (lastGT + 2 < len) {
            sb.delete(lastGT + 2, len);
        }
        return sb.toString();
        // final Matcher m = PATTERN_QP.matcher(line);
        // return m.matches() ? new StringBuilder(m.group(1)).append(m.group(2)).toString() : null;
    }

    /**
     * Extracts plain-text content from specified mail.
     *
     * @param mail The mail
     * @return The extracted plain-text content
     * @throws OXException If text extraction fails
     */
    public static String extractTextFrom(MailMessage mail) throws OXException {
        final MailPart mailPart = extractTextFrom(mail, 0);
        if (null == mailPart) {
            return "";
        }
        try {
            final ContentType contentType = mailPart.getContentType();
            if (!contentType.startsWith("text/htm")) {
                return readContent(mailPart, contentType);
            }
            /*
             * Handle HTML content
             */
            final String html = readContent(mailPart, contentType);
            final HtmlService htmlService = ServerServiceRegistry.getInstance().getService(HtmlService.class);
            return htmlService.html2text(html, true);
        } catch (IOException e) {
            if ("com.sun.mail.util.MessageRemovedIOException".equals(e.getClass().getName()) || (e.getCause() instanceof MessageRemovedException)) {
                throw MailExceptionCode.MAIL_NOT_FOUND_SIMPLE.create(e);
            }
            throw MailExceptionCode.IO_ERROR.create(e, e.getMessage());
        }
    }

    private static MailPart extractTextFrom(MailPart mailPart, int altLevel) throws OXException {
        if (!mailPart.containsContentType()) {
            return null;
        }
        final ContentType contentType = mailPart.getContentType();
        if (contentType.startsWith("text/")) {
            return (altLevel <= 0) || contentType.startsWith("text/htm") ? mailPart : null;
        }
        if (contentType.startsWith("multipart/")) {
            final boolean isAlternative = contentType.startsWith("multipart/alternative");
            int alternative = altLevel;
            if (isAlternative) {
                alternative++;
            }
            final int count = mailPart.getEnclosedCount();
            MailPart textPart = null;
            for (int i = 0; i < count; i++) {
                final MailPart enclosedPart = mailPart.getEnclosedMailPart(i);
                final MailPart ret = extractTextFrom(enclosedPart, alternative);
                if (null != ret) {
                    return ret;
                }
                if (isAlternative && null == textPart && enclosedPart.getContentType().startsWith("text/")) {
                    textPart = enclosedPart;
                }
            }
            if (isAlternative) {
                alternative--;
                if (null != textPart) {
                    return textPart;
                }
            }
        }
        return null;
    }

    private static String readContent(MailPart mailPart, ContentType contentType) throws OXException, IOException {
        final String charset = getCharset(mailPart, contentType);
        try {
            return MessageUtility.readMailPart(mailPart, charset);
        } catch (java.io.CharConversionException e) {
            // Obviously charset was wrong or bogus implementation of character conversion
            final String fallback = "US-ASCII";
            LOG.warn("Character conversion exception while reading content with charset \"{}\". Using fallback charset \"{}\" instead.", charset, fallback, e);
            return MessageUtility.readMailPart(mailPart, fallback);
        }
    }

    private static String getCharset(MailPart mailPart, ContentType contentType) throws OXException {
        final String charset;
        if (mailPart.containsHeader(MessageHeaders.HDR_CONTENT_TYPE)) {
            String cs = contentType.getCharsetParameter();
            if (!CharsetDetector.isValid(cs)) {
                final String prev = cs;
                if (contentType.startsWith("text/")) {
                    cs = CharsetDetector.detectCharset(mailPart.getInputStream());
                    LOG.warn("Illegal or unsupported encoding \"{}\". Using auto-detected encoding: \"{}\"", prev, cs);
                } else {
                    cs = MailProperties.getInstance().getDefaultMimeCharset();
                    LOG.warn("Illegal or unsupported encoding \"{}\". Using fallback encoding: \"{}\"", prev, cs);
                }
            }
            charset = cs;
        } else {
            if (contentType.startsWith("text/")) {
                charset = CharsetDetector.detectCharset(mailPart.getInputStream());
            } else {
                charset = MailProperties.getInstance().getDefaultMimeCharset();
            }
        }
        return charset;
    }

    /**
     * Initializes a new {@link TextProcessing}
     */
    private TextProcessing() {
        super();
    }
}
