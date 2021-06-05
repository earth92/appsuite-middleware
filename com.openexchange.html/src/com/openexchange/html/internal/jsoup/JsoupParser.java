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

package com.openexchange.html.internal.jsoup;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.XmlDeclaration;
import org.jsoup.parser.InterruptedParsingException;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;
import com.google.common.collect.ImmutableMap;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.html.HtmlExceptionCodes;
import com.openexchange.html.HtmlServices;
import com.openexchange.html.internal.jsoup.control.JsoupParseControl;
import com.openexchange.html.internal.jsoup.control.JsoupParseControlTask;
import com.openexchange.html.internal.jsoup.control.JsoupParseTask;
import com.openexchange.html.internal.parser.HtmlHandler;
import com.openexchange.html.services.ServiceRegistry;

/**
 * {@link JsoupParser}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.4
 */
public class JsoupParser {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JsoupParser.class);

    private static final JsoupParser INSTANCE = new JsoupParser();

    /**
     * Gets the instance
     *
     * @return The instance
     */
    public static JsoupParser getInstance() {
        return INSTANCE;
    }

    /**
     * Shuts-down the parser.
     */
    public static void shutDown() {
        INSTANCE.stop();
    }

    // -----------------------------------------------------------------------------------------------------------------------

    private final int htmlParseTimeoutSec;
    private final Thread controlRunner;

    /**
     * Initializes a new {@link JsoupParser}.
     */
    private JsoupParser() {
        super();
        ConfigurationService service = ServiceRegistry.getInstance().getService(ConfigurationService.class);
        int defaultValue = 10;
        htmlParseTimeoutSec = null == service ? defaultValue : service.getIntProperty("com.openexchange.html.parse.timeout", defaultValue);
        if (htmlParseTimeoutSec > 0) {
            controlRunner = new Thread(new JsoupParseControlTask(), "JsoupControl");
            controlRunner.start();
        } else {
            controlRunner = null;
        }
    }

    /**
     * Stops this parser.
     */
    private void stop() {
        if (htmlParseTimeoutSec > 0) {
            JsoupParseControl.getInstance().add(JsoupParseTask.POISON);
        }
        Thread controlRunner = this.controlRunner;
        if (null != controlRunner) {
            controlRunner.interrupt();
        }
    }

    /**
     * Parses specified real-life HTML document and delegates events to given instance of {@link HtmlHandler}
     *
     * @param html The real-life HTML document
     * @param handler The handler
     * @param checkSize Whether length of specified HTML content should be checked against {@link HtmlServices#htmlThreshold()}
     * @param prettyPrint Whether resulting HTML content is supposed to be pretty-printed
     * @throws OXException If specified HTML content cannot be parsed
     */
    public void parse(String html, JsoupHandler handler, boolean checkSize, boolean prettyPrint) throws OXException {
        // Check size
        int maxLength = checkSize ? HtmlServices.htmlThreshold() : 0;
        if (maxLength > 0 && html.length() > maxLength) {
            Integer iMaxLength = I(maxLength); Integer iLength = I(html.length());
            LOG.info("HTML content is too big: max. '{}', but is '{}'.", iMaxLength, iLength);
            throw HtmlExceptionCodes.TOO_BIG.create(iMaxLength, iLength);
        }

        int timeout = htmlParseTimeoutSec;
        if (timeout <= 0) {
            doParse(html, handler, prettyPrint);
            return;
        }

        // Run as a monitored task
        new JsoupParseTask(html, handler, timeout, prettyPrint, this).call();
    }

    /**
     * Parses specified real-life HTML document and delegates events to given instance of {@link HtmlHandler}
     *
     * @param html The real-life HTML document
     * @param handler The handler
     * @param prettyPrint Whether resulting HTML content is supposed to be pretty-printed
     * @throws OXException If specified HTML content cannot be parsed
     */
    public void doParse(String html, JsoupHandler handler, boolean prettyPrint) throws OXException {
        try {
            // Parse HTML input to a Jsoup document
            Document document = Parser.htmlParser().parseInput(new InterruptibleStringReader(html), "");

            if (!prettyPrint) {
                // Disable pretty-print: If disabled, the HTML output methods will not re-format the output, and the output will generally look like the input.
                document.outputSettings().prettyPrint(false);
            }

            // Check <style> tag sizes against threshold
            {
                int cssThreshold = HtmlServices.cssThreshold();
                if (cssThreshold > 0) {
                    Elements styleTags = document.getElementsByTag("style");
                    for (Element styleTag : styleTags) {
                        if (styleTag.html().length() > cssThreshold) {
                            styleTag.remove();
                        }
                    }
                }
            }

            // Traverse document, giving call-backs to specified handler
            document.traverse(new InterruptibleJsoupNodeVisitor(handler));
            handler.finished(document);
        } catch (InterruptedParsingException e) {
            throw HtmlExceptionCodes.PARSING_FAILED.create(e, "Parser timeout.");
        } catch (StackOverflowError parserOverflow) {
            throw HtmlExceptionCodes.PARSING_FAILED.create("Parser overflow detected.", parserOverflow);
        }
    }

    private static final class InterruptibleJsoupNodeVisitor implements NodeVisitor {

        private static interface Invoker {

            void invoke(Node node, JsoupHandler handler);
        }

        private static final Map<Class<?>, Invoker> CALLS = ImmutableMap.<Class<?>, Invoker> builder()
            .put(Element.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleElementStart((Element) node);
                }
            })
            .put(FormElement.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleElementStart((Element) node);
                }
            })
            .put(TextNode.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleTextNode((TextNode) node);
                }
            })
            .put(Comment.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleComment((Comment) node);
                }
            })
            .put(DataNode.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleDataNode((DataNode) node);
                }
            })
            .put(DocumentType.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleDocumentType((DocumentType) node);
                }
            })
            .put(Document.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    // Nothing
                }
            })
            .put(XmlDeclaration.class, new Invoker() {

                @Override
                public void invoke(Node node, JsoupHandler handler) {
                    handler.handleXmlDeclaration((XmlDeclaration) node);
                }
            })
            .build();

        private final JsoupHandler handler;

        InterruptibleJsoupNodeVisitor(JsoupHandler handler) {
            super();
            this.handler = handler;
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void head(Node node, int depth) {
            if (Thread.interrupted()) { // clears flag if set
                throw new InterruptedParsingException();
            }

            Invoker invoker = CALLS.get(node.getClass());
            if (null != invoker) {
                invoker.invoke(node, handler);
            } else {
                LOG.warn("Unexpected node: {}", node.getClass().getName());
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (Thread.interrupted()) { // clears flag if set
                throw new InterruptedParsingException();
            }
            if (node instanceof Element) {
                Element element = (Element) node;
                handler.handleElementEnd(element);
            }
        }
    }

    private static class InterruptibleStringReader extends Reader {

        private String str;
        private final int length;
        private int next = 0;
        private int mark = 0;

        /**
         * Creates a new string reader.
         *
         * @param s  String providing the character stream.
         */
        public InterruptibleStringReader(String s) {
            this.str = s;
            this.length = s.length();
        }

        /** Check to make sure that the stream has not been closed */
        private void ensureOpenAndNotInterrupted() throws IOException {
            if (str == null) {
                throw new IOException("Stream closed");
            }
            if (Thread.interrupted()) { // clears flag if set
                throw new InterruptedParsingException();
            }
        }

        /**
         * Reads a single character.
         *
         * @return     The character read, or -1 if the end of the stream has been
         *             reached
         *
         * @exception  IOException  If an I/O error occurs
         */
        @Override
        public int read() throws IOException {
            ensureOpenAndNotInterrupted();
            if (next >= length) {
                return -1;
            }
            return str.charAt(next++);
        }

        /**
         * Reads characters into a portion of an array.
         *
         * @param      cbuf  Destination buffer
         * @param      off   Offset at which to start writing characters
         * @param      len   Maximum number of characters to read
         *
         * @return     The number of characters read, or -1 if the end of the
         *             stream has been reached
         *
         * @exception  IOException  If an I/O error occurs
         */
        @Override
        public int read(char cbuf[], int off, int len) throws IOException {
            ensureOpenAndNotInterrupted();
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            if (next >= length) {
                return -1;
            }
            int n = Math.min(length - next, len);
            str.getChars(next, next + n, cbuf, off);
            next += n;
            return n;
        }

        /**
         * Skips the specified number of characters in the stream. Returns
         * the number of characters that were skipped.
         *
         * <p>The <code>ns</code> parameter may be negative, even though the
         * <code>skip</code> method of the {@link Reader} superclass throws
         * an exception in this case. Negative values of <code>ns</code> cause the
         * stream to skip backwards. Negative return values indicate a skip
         * backwards. It is not possible to skip backwards past the beginning of
         * the string.
         *
         * <p>If the entire string has been read or skipped, then this method has
         * no effect and always returns 0.
         *
         * @exception  IOException  If an I/O error occurs
         */
        @Override
        public long skip(long ns) throws IOException {
            ensureOpenAndNotInterrupted();
            if (next >= length) {
                return 0;
            }
            // Bound skip by beginning and end of the source
            long n = Math.min(length - next, ns);
            n = Math.max(-next, n);
            next += n;
            return n;
        }

        /**
         * Tells whether this stream is ready to be read.
         *
         * @return True if the next read() is guaranteed not to block for input
         *
         * @exception  IOException  If the stream is closed
         */
        @Override
        public boolean ready() throws IOException {
            ensureOpenAndNotInterrupted();
            return true;
        }

        /**
         * Tells whether this stream supports the mark() operation, which it does.
         */
        @Override
        public boolean markSupported() {
            return true;
        }

        /**
         * Marks the present position in the stream.  Subsequent calls to reset()
         * will reposition the stream to this point.
         *
         * @param  readAheadLimit  Limit on the number of characters that may be
         *                         read while still preserving the mark.  Because
         *                         the stream's input comes from a string, there
         *                         is no actual limit, so this argument must not
         *                         be negative, but is otherwise ignored.
         *
         * @exception  IllegalArgumentException  If {@code readAheadLimit < 0}
         * @exception  IOException  If an I/O error occurs
         */
        @Override
        public void mark(int readAheadLimit) throws IOException {
            if (readAheadLimit < 0){
                throw new IllegalArgumentException("Read-ahead limit < 0");
            }
            ensureOpenAndNotInterrupted();
            mark = next;
        }

        /**
         * Resets the stream to the most recent mark, or to the beginning of the
         * string if it has never been marked.
         *
         * @exception  IOException  If an I/O error occurs
         */
        @Override
        public void reset() throws IOException {
            ensureOpenAndNotInterrupted();
            next = mark;
        }

        /**
         * Closes the stream and releases any system resources associated with
         * it. Once the stream has been closed, further read(),
         * ready(), mark(), or reset() invocations will throw an IOException.
         * Closing a previously closed stream has no effect.
         */
        @Override
        public void close() {
            str = null;
        }
    }
}
