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

package com.openexchange.html.internal.parser;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import com.openexchange.java.Charsets;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;

/**
 * {@link HtmlParser} - Parses a well-formed HTML document based on {@link XmlPullParser}. The corresponding events are delegated to a given
 * instance of {@link HtmlHandler}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class HtmlParser {

    private static final String PROPERTY_XMLDECL_STANDALONE = "http://xmlpull.org/v1/doc/features.html#xmldecl-standalone";

    private static final String PROPERTY_XMLDECL_VERSION = "http://xmlpull.org/v1/doc/properties.html#xmldecl-version";

    private static final String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HtmlParser.class);

    private static final int INT_IS_EMPTY_TAG = 1;

    private HtmlParser() {
        super();
    }

    /**
     * Parses specified well-formed HTML document and delegates events to given instance of {@link HtmlHandler}
     *
     * @param html The well-formed HTML document
     * @param handler The HTML handler
     */
    public static void parse(final String html, final HtmlHandler handler) {
        final XmlPullParser parser = new KXmlParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            // parser.setFeature(FEATURE_PRESERVE_TEXT, true);
            parser.setFeature(FEATURE_RELAXED, true);
            parser.setInput(new StringReader(html));
            int event = XmlPullParser.END_DOCUMENT;
            int[] holderForStartAndLength = null;
            final StringBuilder textBuilder = new StringBuilder(512);
            final StringBuilder cdataBuilder = new StringBuilder(512);
            int prevEvent = XmlPullParser.END_DOCUMENT;
            int[] depthInfo = new int[8];
            boolean ignoreWhitespace = false;
            boolean obtainXMLDecl = true;
            while ((event = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
                if (obtainXMLDecl) {
                    final Object version = parser.getProperty(PROPERTY_XMLDECL_VERSION);
                    if (null != version) {
                        final Object standalone = parser.getProperty(PROPERTY_XMLDECL_STANDALONE);
                        handler.handleXMLDeclaration(
                            version.toString(),
                            standalone == null ? null : (Boolean) standalone,
                            parser.getInputEncoding());
                    }
                    obtainXMLDecl = false;
                }
                if (event == XmlPullParser.CDSECT) {
                    /*
                     * Gather subsequent text inside CDATA (ex. 'fo<o' from <!CDATA[fo<o]]>)
                     */
                    if (prevEvent != XmlPullParser.CDSECT && textBuilder.length() > 0) {
                        handler.handleText(textBuilder.toString(), ignoreWhitespace);
                        textBuilder.setLength(0);
                    }
                    prevEvent = XmlPullParser.CDSECT;
                    cdataBuilder.append(parser.getText());
                } else if (event == XmlPullParser.TEXT) {
                    if (prevEvent != XmlPullParser.TEXT && cdataBuilder.length() > 0) {
                        handler.handleCDATA(cdataBuilder.toString());
                        cdataBuilder.setLength(0);
                    }
                    /*
                     * Gather subsequent text inside element's content
                     */
                    prevEvent = XmlPullParser.TEXT;
                    if (!parser.isWhitespace()) {
                        ignoreWhitespace = false;
                    }
                    textBuilder.append(parser.getText());
                } else if (event == XmlPullParser.ENTITY_REF) {
                    /*
                     * Entity reference, such as "&amp;"
                     */
                    if (null == holderForStartAndLength) {
                        holderForStartAndLength = new int[2];
                    }
                    ignoreWhitespace = false;
                    if (prevEvent == XmlPullParser.TEXT) {
                        final char[] textCharacters = parser.getTextCharacters(holderForStartAndLength);
                        if (null != textCharacters) {
                            textBuilder.append('&').append(textCharacters).append(';');
                        }
                    } else if (prevEvent == XmlPullParser.CDSECT) {
                        cdataBuilder.append('&').append(parser.getTextCharacters(holderForStartAndLength)).append(';');
                    } else {
                        LOG.warn("Unexpected entity occurring inside non-text and non-CDATA area");
                    }
                } else {
                    /*
                     * Check for text/CDATA
                     */
                    if (textBuilder.length() > 0) {
                        handler.handleText(textBuilder.toString(), ignoreWhitespace);
                        textBuilder.setLength(0);
                    }
                    if (cdataBuilder.length() > 0) {
                        handler.handleCDATA(cdataBuilder.toString());
                        cdataBuilder.setLength(0);
                    }
                    /*
                     * Handle non-text event
                     */
                    if (event == XmlPullParser.COMMENT) {
                        handler.handleComment(parser.getText());
                    } else if (event == XmlPullParser.DOCDECL) {
                        handler.handleDocDeclaration(parser.getText());
                    } else if (event == XmlPullParser.END_TAG) {
                        if ((depthInfo[parser.getDepth()] & INT_IS_EMPTY_TAG) == 0) {
                            handler.handleEndTag(parser.getName());
                        }
                        ignoreWhitespace = true;
                    } else if (event == XmlPullParser.IGNORABLE_WHITESPACE) {
                        LOG.trace("IGNORABLE_WHITESPACE: {}", parser.getText());
                    } else if (event == XmlPullParser.PROCESSING_INSTRUCTION) {
                        LOG.trace("PROCESSING_INSTRUCTION: {}", parser.getText());
                    } else if (event == XmlPullParser.START_DOCUMENT) {
                        LOG.trace("START_DOCUMENT: {}", parser.getText());
                    } else if (event == XmlPullParser.START_TAG) {
                        final Map<String, String> attributes = new HashMap<String, String>();
                        final int count = parser.getAttributeCount();
                        for (int i = 0; i < count; i++) {
                            attributes.put(parser.getAttributeName(i), parser.getAttributeValue(i));
                        }
                        final int depth = parser.getDepth();
                        if (depth >= depthInfo.length) {
                            /*
                             * Increase array
                             */
                            final int[] tmp = depthInfo;
                            depthInfo = new int[tmp.length << 1];
                            System.arraycopy(tmp, 0, depthInfo, 0, tmp.length);
                        }
                        if (parser.isEmptyElementTag()) {
                            depthInfo[depth] = (depthInfo[depth] | INT_IS_EMPTY_TAG);
                            handler.handleSimpleTag(parser.getName(), attributes);
                        } else {
                            depthInfo[depth] = (depthInfo[depth] & ~INT_IS_EMPTY_TAG);
                            handler.handleStartTag(parser.getName(), attributes);
                        }
                        ignoreWhitespace = true;
                    } else {
                        handler.handleError(new StringBuilder(32).append("Unknown event type: ").append(event).toString());
                    }
                }
            }
        } catch (XmlPullParserException e) {
            LOG.error(composeErrorMessage(e, html), e);
            handler.handleError(e.getMessage());
        } catch (IOException e) {
            LOG.error(composeErrorMessage(e, html), e);
            handler.handleError(e.getMessage());
        } catch (RuntimeException e) {
            LOG.error(composeErrorMessage(e, html), e);
            handler.handleError(e.getMessage());
        } finally {
            if (parser instanceof Closeable) {                
                Streams.close((Closeable) parser);
            }
        }
    }

    private static String composeErrorMessage(final Exception e, final String html) {
        final StringBuilder sb = new StringBuilder(html.length() + 256);
        sb.append("Parsing of HTML content failed: ");
        sb.append(e.getMessage());
        if (LOG.isDebugEnabled()) {
            sb.append(". Affected HTML content:").append('\n');
            dumpHtml(html, sb);
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static void dumpHtml(final String html, final StringBuilder sb) {
        final String[] lines = Strings.splitByCRLF(html);
        final DecimalFormat df = new DecimalFormat("0000");
        int count = 1;
        for (final String line : lines) {
            sb.append(df.format(count++)).append(' ').append(line).append('\n');
        }
        OutputStreamWriter writer = null;
        try {
            File file = File.createTempFile("parsefailed", ".html", new File(System.getProperty("java.io.tmpdir")));
            writer = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            writer.write(html);
        } catch (IOException e) {
            LOG.error("Problem writing not parsable HTML to tmp directory.", e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }
}
