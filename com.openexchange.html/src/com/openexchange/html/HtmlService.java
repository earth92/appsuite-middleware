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

package com.openexchange.html;

import java.io.Reader;
import com.openexchange.exception.OXException;
import com.openexchange.html.whitelist.Whitelist;
import com.openexchange.osgi.annotation.SingletonService;




/**
 * {@link HtmlService} - The HTML service provides several methods concerning processing of HTML content.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@SingletonService
public interface HtmlService {

    /**
     * Replaces image URLs with a proxy URI to ensure safe loading of image content.
     *
     * @param content The HTML content
     * @param sessionId The identifier of the session needed to register appropriate proxy URI
     * @return The HTML content with image URLs replaced
     */
    String replaceImages(String content, String sessionId);

    /**
     * Converts found URLs inside specified content to valid links.
     *
     * @param content The content
     * @param commentId The identifier wrapped in a comment prepended to each formatted URL:<br>
     *            <code>"&lt;!--" + &lt;<i>comment</i>&gt; + " " + <i>&lt;anchor-tag&gt;</i> + "--&gt;"</code>
     * @return The content with URLs turned to links
     */
    String formatURLs(String content, String commentId);

    /**
     * Searches for non-HTML links and convert them to valid HTML links.
     * <p>
     * Example: <code>http://www.somewhere.com</code> is converted to
     * <code>&lt;a&nbsp;href=&quot;http://www.somewhere.com&quot;&gt;http://www.somewhere.com&lt;/a&gt;</code>.
     *
     * @param content The content to search in
     * @return The given content with all non-HTML links converted to valid HTML links
     */
    String formatHrefLinks(final String content);

    /**
     * Filters specified HTML content according to white-list filter.
     * <p>
     * <b>Note</b>: Specified HTML content needs to be validated as per {@link #getConformHTML(String, String)}
     *
     * @param htmlContent The <b>validated</b> HTML content
     * @return The filtered HTML content
     * @see #getConformHTML(String, String)
     */
    String filterWhitelist(String htmlContent);

    /**
     * Filters specified HTML content according to white-list filter.
     * <p>
     * <b>Note</b>: Specified HTML content needs to be validated as per {@link #getConformHTML(String, String)}
     *
     * @param htmlContent The <b>validated</b> HTML content
     * @param configName The name of the whitelist to use.
     * @return The filtered HTML content
     * @see #getConformHTML(String, String)
     */
    String filterWhitelist(String htmlContent, String configName);

    /**
     * Filters externally loaded images out of specified HTML content.
     * <p>
     * <b>Note</b>: Specified HTML content needs to be validated as per {@link #getConformHTML(String, String)}
     *
     * @param htmlContent The <b>validated</b> HTML content
     * @param modified A <code>boolean</code> array with length <code>1</code> to store modified status
     * @return The HTML content stripped by external images
     * @see #getConformHTML(String, String)
     */
    String filterExternalImages(String htmlContent, boolean[] modified);

    /**
     * Sanitizes specified HTML content by limiting the content size to the character count provided with maxContentSize.
     *
     * @param htmlContent The HTML content to sanitize
     * @param optConfigName The optional configuration name to read whitelist from
     * @param dropExternalImages Whether to drop image URLs
     * @param modified A <code>boolean</code> array with length <code>1</code> to store modified status
     * @param cssPrefix The optional CSS prefix
     * @param maxContentSize maximum number of bytes that is will be returned for content. '<=0' means unlimited. Below 10000 will be ignor
     * @return {@link HtmlSanitizeResult} with the content and additional information, e.g. if the content was truncated
     */
    HtmlSanitizeResult sanitize(String htmlContent, String optConfigName, boolean dropExternalImages, boolean[] modified, String cssPrefix, int maxContentSize) throws OXException;

    /**
     * Sanitizes specified HTML content by limiting the content size to the character count provided with maxContentSize.
     *
     * @param htmlContent The HTML content to sanitize
     * @param options The options for performing the sanitizing
     * @return {@link HtmlSanitizeResult} with the content and additional information, e.g. if the content was truncated
     */
    HtmlSanitizeResult sanitize(String htmlContent, HtmlSanitizeOptions options) throws OXException;

    /**
     * Sanitizes specified HTML content.
     *
     * @param htmlContent The HTML content to sanitize
     * @param optConfigName The optional configuration name to read whitelist from
     * @param dropExternalImages Whether to drop image URLs
     * @param modified A <code>boolean</code> array with length <code>1</code> to store modified status
     * @param cssPrefix The optional CSS prefix
     * @return The sanitized HTML content
     */
    String sanitize(String htmlContent, String optConfigName, boolean dropExternalImages, boolean[] modified, String cssPrefix) throws OXException;

    /**
     * Extracts the plain text from specified HTML content.
     *
     * @param htmlContent The HTML content to extract from
     * @return The extracted plain text
     * @throws OXException If text extraction fails
     */
    String extractText(String htmlContent) throws OXException;

    /**
     * Extracts the plain text from specified HTML input.
     *
     * @param htmlContent The HTML input to extract from
     * @return The extracted plain text
     * @throws OXException If text extraction fails
     */
    String extractText(Reader htmlInput) throws OXException;

    /**
     * Converts specified HTML content to plain text.
     *
     * @param htmlContent The <b>validated</b> HTML content
     * @param appendHref <code>true</code> to append URLs contained in <i>href</i>s and <i>src</i>s; otherwise <code>false</code>.<br>
     *            Example: <code>&lt;a&nbsp;href=\"www.somewhere.com\"&gt;Link&lt;a&gt;</code> would be
     *            <code>Link&nbsp;[www.somewhere.com]</code>
     * @return The plain text representation of specified HTML content
     * @see #getConformHTML(String, String)
     */
    String html2text(String htmlContent, boolean appendHref);

    /**
     * Formats plain text to HTML by escaping HTML special characters e.g. <code>&quot;&lt;&quot;</code> is converted to
     * <code>&quot;&amp;lt;&quot;</code>. Additionally limiting the content size to the character count provided with maxContentSize.
     *
     * @param plainText The plain text
     * @param withQuote Whether to escape quotes (<code>&quot;</code>) or not
     * @param commentId The identifier wrapped in a comment prepended to each formatted URL:<br>
     *            <code>"&lt;!--" + &lt;<i>comment</i>&gt; + " " + <i>&lt;anchor-tag&gt;</i> + "--&gt;"</code>
     * @param maxContentSize maximum number of bytes that is will be returned for content. '<=0' means unlimited.
     * @return {@link HtmlSanitizeResult} with the content and additional information, e. g. if the content was truncated
     */
    HtmlSanitizeResult htmlFormat(String plainText, boolean withQuote, String commentId, int maxContentSize);

    /**
     * Formats plain text to HTML by escaping HTML special characters e.g. <code>&quot;&lt;&quot;</code> is converted to
     * <code>&quot;&amp;lt;&quot;</code>.
     *
     * @param plainText The plain text
     * @param withQuote Whether to escape quotes (<code>&quot;</code>) or not
     * @param commentId The identifier wrapped in a comment prepended to each formatted URL:<br>
     *            <code>"&lt;!--" + &lt;<i>comment</i>&gt; + " " + <i>&lt;anchor-tag&gt;</i> + "--&gt;"</code>
     * @return properly escaped HTML content
     */
    String htmlFormat(String plainText, boolean withQuote, String commentId);

    /**
     * Formats plain text to HTML by escaping HTML special characters e.g. <code>&quot;&lt;&quot;</code> is converted to
     * <code>&quot;&amp;lt;&quot;</code>.
     *
     * @param plainText The plain text
     * @param withQuote Whether to escape quotes (<code>&quot;</code>) or not
     * @return properly escaped HTML content
     */
    String htmlFormat(String plainText, boolean withQuote);

    /**
     * Formats plain text to HTML by escaping HTML special characters e.g. <code>&quot;&lt;&quot;</code> is converted to
     * <code>&quot;&amp;lt;&quot;</code>.
     * <p>
     * This is just a convenience method which invokes <code>{@link #htmlFormat(String, boolean)}</code> with latter parameter set to
     * <code>true</code>.
     *
     * @param plainText The plain text
     * @return properly escaped HTML content
     * @see #htmlFormat(String, boolean)
     */
    String htmlFormat(String plainText);

    /**
     * Surrounds given HTML content with a valid HTML document provided that &lt;body&gt; tag is missing. Otherwise content is returned
     * as-is.
     *
     * @param htmlContent The HTML content
     * @param charset The charset parameter
     * @return The HTML document or the content as-is
     */
    String documentizeContent(String htmlContent, String charset);

    /**
     * Attempts to generate well-formed HTML document for specified HTML fragment.
     *
     * @param htmlContent The HTML fragment
     * @return The well-formed HTML document
     * @throws OXException If operation fails
     */
    String getWellFormedHTMLDocument(String htmlContent) throws OXException;

    /**
     * Creates valid HTML from specified HTML content conform to W3C standards. Non-ascii-URLs will be replaced with puny-code-encoded URLs.
     *
     * @param htmlContent The HTML content
     * @param charset The charset parameter
     * @return The HTML content conform to W3C standards
     * @throws OXException
     */
    String getConformHTML(String htmlContent, String charset) throws OXException;

    /**
     * Creates valid HTML from specified HTML content conform to W3C standards.
     *
     * @param htmlContent The HTML content
     * @param charset The charset parameter
     * @param checkUrls Define if non-ascii-URLs shell be replaced with puny-code-encoded URLs
     * @return The HTML content conform to W3C standards
     * @throws OXException
     */
    String getConformHTML(String htmlContent, String charset, boolean replaceUrls) throws OXException;

    /**
     * Drops <code>&lt;script&gt;</code> tags in HTML content's header.
     *
     * @param htmlContent The HTML content
     * @return The HTML content with <code>&lt;script&gt;</code> tags removed
     */
    String dropScriptTagsInHeader(String htmlContent);

    /**
     * Extracts CSS-stylesheets from HTML header
     * @param htmlContent The HTML content
     * @return The stylesheet from HTML header
     */
    String getCSSFromHTMLHeader(String htmlContent);

    /**
     * Checks for existence of a <code>&lt;base&gt;</code> tag. Allowing it if an absolute URL is specified in <code><i>href</i></code>
     * attribute <b><small>AND</small></b> <code>externalImagesAllowed</code> is set to <code>true</code>; otherwise the
     * <code>&lt;base&gt;</code> tag is removed.
     *
     * @param htmlContent The HTML content
     * @param externalImagesAllowed <code>true</code> if external images are allowed; otherwise <code>false</code>
     * @return The HTML content with a proper <code>&lt;base&gt;</code> tag
     */
    String checkBaseTag(String htmlContent, boolean externalImagesAllowed);

    /**
     * Pretty prints specified HTML content.
     *
     * @param htmlContent The HTML content
     * @return Pretty printed HTML content
     */
    String prettyPrint(final String htmlContent);

    /**
     * Replaces all HTML entities occurring in specified HTML content with corresponding unicode character.
     *
     * @param content The content
     * @return The content with HTML entities replaced with corresponding unicode character
     */
    String replaceHTMLEntities(String content);

    /**
     * Maps specified HTML entity - e.g. <code>&amp;uuml;</code> - to corresponding ASCII character.
     *
     * @param entity The HTML entity
     * @return The corresponding ASCII character or <code>null</code>
     */
    Character getHTMLEntity(String entity);

    /**
     * Encode data for use in HTML using HTML entity encoding
     * <p>
     * Note that the following characters: 00-08, 0B-0C, 0E-1F, and 7F-9F
     * <p>
     * cannot be used in HTML.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Character_encodings_in_HTML">HTML Encodings [wikipedia.org]</a>
     * @see <a href="http://www.w3.org/TR/html4/sgml/sgmldecl.html">SGML Specification [w3.org]</a>
     * @see <a href="http://www.w3.org/TR/REC-xml/#charsets">XML Specification [w3.org]</a>
     * @param input the text to encode for HTML
     * @return input encoded for HTML
     */
    String encodeForHTML(String input);

    /**
     * Encode data for use in HTML using HTML entity encoding
     * <p>
     * Note that the following characters: 00-08, 0B-0C, 0E-1F, and 7F-9F
     * <p>
     * cannot be used in HTML.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Character_encodings_in_HTML">HTML Encodings [wikipedia.org]</a>
     * @see <a href="http://www.w3.org/TR/html4/sgml/sgmldecl.html">SGML Specification [w3.org]</a>
     * @see <a href="http://www.w3.org/TR/REC-xml/#charsets">XML Specification [w3.org]</a>
     * @param candidates The characters to escape
     * @param input the text to encode for HTML
     * @return input encoded for HTML
     */
    String encodeForHTML(char[] candidates, String input);

    /**
     * Encode data for use in HTML attributes.
     *
     * @param input the text to encode for an HTML attribute
     * @param input
     *      the text to encode for an HTML attribute
     *
     * @return input encoded for use as an HTML attribute
     */
    String encodeForHTMLAttribute(String input);

    /**
     * Gets the currently applicable white-lists for HTML and CSS
     *
     * @param withCss Whether returned white-list should also contain the CSS stuff (if <code>false</code> an empty map is returned for {@link Whitelist#getStyleWhitelist()})
     * @return The HTML/CSS white-lists
     */
    Whitelist getWhitelist(boolean withCss);
}
