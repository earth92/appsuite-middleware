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

package com.openexchange.rss.actions;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.net.ssl.SSLHandshakeException;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.ajax.requesthandler.AJAXActionService;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.Category;
import com.openexchange.exception.ExceptionUtils;
import com.openexchange.exception.OXException;
import com.openexchange.html.HtmlService;
import com.openexchange.java.Strings;
import com.openexchange.rss.RssExceptionCodes;
import com.openexchange.rss.RssResult;
import com.openexchange.rss.osgi.Services;
import com.openexchange.rss.preprocessors.RssPreprocessor;
import com.openexchange.rss.preprocessors.SanitizingPreprocessor;
import com.openexchange.rss.util.TimeoutHttpURLFeedFetcher;
import com.openexchange.rss.utils.RssProperties;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndImage;
import com.sun.syndication.fetcher.FetcherException;
import com.sun.syndication.fetcher.impl.HashMapFeedInfoCache;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.ParsingFeedException;

/**
 * {@link RssAction} - The RSS action.
 */
public class RssAction implements AJAXActionService {

    private static final String COULD_NOT_LOAD_RSS_FEED_FROM = "Could not load RSS feed from: {}";

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RssAction.class);

    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;

    private static final Comparator<RssResult> ASC = new Comparator<RssResult>() {

        @Override
        public int compare(RssResult r1, RssResult r2) {
            return r1.getDate().compareTo(r2.getDate());
        }
    };

    private static final Comparator<RssResult> DESC = new Comparator<RssResult>() {

        @Override
        public int compare(RssResult r1, RssResult r2) {
            return r2.getDate().compareTo(r1.getDate());
        }
    };

    // ------------------------------------------------------------------------------------------------------------------------------

    private final TimeoutHttpURLFeedFetcher fetcher;
    private final HashMapFeedInfoCache feedCache;

    /**
     * Initializes a new {@link RssAction}.
     */
    public RssAction() {
        feedCache = new HashMapFeedInfoCache();
        fetcher = new TimeoutHttpURLFeedFetcher(10000, 30000, feedCache);
    }

    @Override
    public AJAXRequestResult perform(AJAXRequestData request, ServerSession session) throws OXException {
        List<OXException> warnings = new LinkedList<>();

        List<SyndFeed> feeds;
        try {
            feeds = getAcceptedFeeds(getUrls(request), warnings);
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw AjaxExceptionCodes.INVALID_PARAMETER.create(e, e.getMessage());
        } catch (JSONException e) {
            throw AjaxExceptionCodes.JSON_ERROR.create(e, e.getMessage());
        }

        if (feeds == null) {
            return new AJAXRequestResult(new ArrayList<RssResult>(1), "rss").addWarnings(warnings);
        }

        List<RssResult> results = new ArrayList<>(feeds.size());
        boolean dropExternalImages = AJAXRequestDataTools.parseBoolParameter("drop_images", request, true);
        RssPreprocessor preprocessor = new SanitizingPreprocessor(dropExternalImages);
        Date now = new Date();

        // Iterate feeds
        for (SyndFeed feed : feeds) {
            if (feed == null) {
                continue;
            }

            // Iterate feed's entries

            String feedTitle = extractTextFrom(feed.getTitleEx());
            String imageUrl = null;
            String feedUrl;
            try {
                feedUrl = checkUrl(feed.getLink());
                SyndImage image = feed.getImage();
                if (image != null) {
                    imageUrl = checkUrl(image.getUrl());
                }
            } catch (MalformedURLException e) {
                throw RssExceptionCodes.INVALID_RSS.create(e, feed.getLink());
            }
            for (Object obj : feed.getEntries()) {
                SyndEntry entry = (SyndEntry) obj;

                // Create appropriate RssResult instance
                RssResult result;
                try {
                    result = new RssResult().setAuthor(entry.getAuthor()).setUrl(checkUrl(entry.getLink()));
                    result.setFeedUrl(feedUrl).setFeedTitle(feedTitle).setDate(entry.getUpdatedDate(), entry.getPublishedDate(), now);

                    // Title
                    result.setSubject(extractTextFrom(entry.getTitleEx()));

                    // Check possible image
                    if (imageUrl != null) {
                        result.setImageUrl(imageUrl);
                    }

                    // Add to results list
                    results.add(result);

                    @SuppressWarnings("unchecked") List<SyndContent> contents = entry.getContents();
                    handleContents(preprocessor, entry, result, contents);
                } catch (MalformedURLException e) {
                    LOG.debug("Unable to process entry with invalid link {}", entry.getLink(), e);
                }
            }
        }

        String sort = request.getParameter("sort"); // DATE or SOURCE
        if (sort == null) {
            sort = "DATE";
        }
        String order = request.getParameter("order"); // ASC or DESC
        if (order == null) {
            order = "DESC";
        }
        if (sort.equalsIgnoreCase("DATE")) {
            Collections.sort(results, "DESC".equalsIgnoreCase(order) ? DESC : ASC);
        }
        int limit = request.getIntParameter("limit");
        if (limit > 0 && limit < results.size()) {
            results = results.subList(0, limit);
        }

        return new AJAXRequestResult(results, "rss").addWarnings(warnings);
    }

    /**
     * Extracts plain text from given content.
     *
     * @param content The content to extract from
     * @return The extracted plain text
     */
    private String extractTextFrom(SyndContent content) {
        String type = content.getType();
        if (null != type && (type.startsWith("htm") || type.startsWith("xhtm"))) {
            HtmlService htmlService = Services.optService(HtmlService.class);
            if (htmlService != null) {
                return htmlService.html2text(content.getValue(), false);
            }
        }
        return content.getValue();
    }

    private void handleContents(RssPreprocessor preprocessor, SyndEntry entry, RssResult result, List<SyndContent> contents) throws OXException {
        if (contents.isEmpty()) {
            /* Change for bug 52689: If no content is available at least display description */
            SyndContent description = entry.getDescription();
            if (null != description) {
                result.setBody(sanitiseString(description.getValue())).setFormat("text/html");
            }
        } else {
            for (SyndContent content : contents) {
                String type = content.getType();
                if (null != type && (type.startsWith("htm") || type.startsWith("xhtm"))) {
                    String htmlContent = preprocessor.process(content.getValue(), result);
                    result.setBody(htmlContent).setFormat("text/html");
                    break;
                }
                result.setBody(content.getValue()).setFormat(type);
            }
        }
    }

    /**
     * Retrieves all RSS URLs wrapped in the given request.
     *
     * @param request - the {@link AJAXRequestData} containing all feed URLs
     * @return {@link List} with desired feed URLs for further processing
     * @throws OXException
     * @throws MalformedURLException
     * @throws JSONException
     */
    protected List<URL> getUrls(AJAXRequestData request) throws OXException, MalformedURLException, JSONException {
        List<URL> urls = new ArrayList<>();

        String urlString = "";
        JSONObject data = (JSONObject) request.requireData();
        JSONArray test = data.optJSONArray("feedUrl");
        if (test == null) {
            urlString = request.checkParameter("feedUrl");
            urlString = urlDecodeSafe(urlString);
            urls = Collections.singletonList(new URL(prepareUrlString(urlString)));
        } else {
            final int length = test.length();
            urls = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                urlString = test.getString(i);
                urls.add(new URL(prepareUrlString(urlString)));
            }
        }
        return urls;
    }

    /**
     * Checks given URLs for validity (esp. host name black-listing and port white-listing) and adds not accepted feeds to the provided warnings list.
     * <p>
     * Returns a list of accepted feeds for further processing.
     *
     * @param urls - List of {@link URL}s to check
     * @param warnings - List of {@link OXException} that might be enhanced by possible errors
     * @return {@link List} of {@link SyndFeed}s that have been accepted for further processing
     * @throws OXException
     */
    protected List<SyndFeed> getAcceptedFeeds(List<URL> urls, List<OXException> warnings) throws OXException {
        RssProperties rssProperties = Services.getService(RssProperties.class);
        if (rssProperties == null) {
            throw ServiceExceptionCode.absentService(RssProperties.class);
        }

        int numberOfUrls = urls.size();
        List<SyndFeed> feeds = new ArrayList<>(numberOfUrls);

        if (numberOfUrls == 1) {
            // Only one feed URL
            URL url = urls.get(0);
            if (rssProperties.isDenied(url.toString())) {
                throw RssExceptionCodes.RSS_CONNECTION_ERROR.create(url.toString());
            }
            retrieveAndAddFeed(url, feeds, Optional.empty());
        } else {
            // More than one feed URL
            for (URL url : urls) {
                if (rssProperties.isDenied(url.toString())) {
                    OXException oxe = RssExceptionCodes.RSS_CONNECTION_ERROR.create(url.toString());
                    oxe.setCategory(Category.CATEGORY_WARNING);
                    warnings.add(oxe);
                } else {
                    retrieveAndAddFeed(url, feeds, Optional.of(warnings));
                }
            }
        }

        return feeds;
    }

    private void retrieveAndAddFeed(URL url, List<SyndFeed> feeds, Optional<List<OXException>> optionalWarnings) throws OXException {
        try {
            feeds.add(fetcher.retrieveFeed(url));
        } catch (java.net.SocketTimeoutException e) {
            OXException oxe = RssExceptionCodes.TIMEOUT_ERROR.create(e, url.toString());
            if (!optionalWarnings.isPresent()) {
                throw oxe;
            }
            optionalWarnings.get().add(oxe);
        } catch (UnsupportedEncodingException e) {
            /* yeah, right... not happening for UTF-8 */
            LOG.trace("", e);
        } catch (IOException e) {
            OXException oxe;
            if (ExceptionUtils.isEitherOf(e, SSLHandshakeException.class)) {
                oxe = RssExceptionCodes.SSL_HANDSHAKE_ERROR.create(e, url.toString());
            } else {
                oxe = RssExceptionCodes.IO_ERROR.create(e, e.getMessage(), url.toString());
            }
            if (!optionalWarnings.isPresent()) {
                throw oxe;
            }
            optionalWarnings.get().add(oxe);
        } catch (ParsingFeedException parsingException) {
            Throwable t = parsingException.getCause();
            if (t != null && t instanceof IOException) {
                String exceptionMessage = t.getMessage();
                if (Strings.isNotEmpty(exceptionMessage) && exceptionMessage.contains("exceeded")) {
                    ConfigurationService configService = Services.getService(ConfigurationService.class);
                    int maximumAllowedSize = configService.getIntProperty("com.openexchange.messaging.rss.feed.size", 4194304);
                    OXException oxe = RssExceptionCodes.RSS_SIZE_EXCEEDED.create(FileUtils.byteCountToDisplaySize(maximumAllowedSize), I(maximumAllowedSize));
                    if (!optionalWarnings.isPresent()) {
                        throw oxe;
                    }
                    optionalWarnings.get().add(oxe);
                }
            }
            final OXException oxe = RssExceptionCodes.INVALID_RSS.create(parsingException, url.toString());
            if (!optionalWarnings.isPresent()) {
                throw oxe;
            }
            oxe.setCategory(Category.CATEGORY_WARNING);
            optionalWarnings.get().add(oxe);
        } catch (FeedException e) {
            LOG.warn(COULD_NOT_LOAD_RSS_FEED_FROM, url, e);
        } catch (FetcherException e) {
            int responseCode = e.getResponseCode();
            if (responseCode <= 0) {
                // No response code available
                LOG.warn(COULD_NOT_LOAD_RSS_FEED_FROM, url, e);
            }
            if (NOT_FOUND == responseCode) {
                LOG.debug("Resource could not be found: {}", url, e);
            } else if (FORBIDDEN == responseCode) {
                LOG.debug("Authentication required for resource: {}", url, e);
            } else if (responseCode >= 500 && responseCode < 600) {
                OXException oxe = RssExceptionCodes.RSS_HTTP_ERROR.create(e, Integer.valueOf(responseCode), url);
                if (!optionalWarnings.isPresent()) {
                    throw oxe;
                }
                oxe.setCategory(Category.CATEGORY_WARNING);
                optionalWarnings.get().add(oxe);
            } else {
                LOG.warn(COULD_NOT_LOAD_RSS_FEED_FROM, url, e);
            }
        } catch (IllegalArgumentException e) {
            String exceptionMessage = e.getMessage();
            if (!"Invalid document".equals(exceptionMessage)) {
                throw AjaxExceptionCodes.INVALID_PARAMETER.create(e, exceptionMessage);
            }
            // There is no parser for current document
            LOG.warn(COULD_NOT_LOAD_RSS_FEED_FROM, url);
        }
    }

    /**
     * Sanitizes the specified string via the {@link HtmlService}
     *
     * @param string The string to sanitize
     * @return The sanitized string as HTML content if the {@link HtmlService} is available
     */
    private static String sanitiseString(String string) throws OXException {
        final HtmlService htmlService = Services.getService(HtmlService.class);
        if (htmlService == null) {
            LOG.warn("The HTMLService is unavailable at the moment, thus the RSS string '{}' might not be sanitised", string);
            return string;
        }
        return htmlService.sanitize(string, null, true, null, null);
    }

    private static String urlDecodeSafe(final String urlString) throws MalformedURLException {
        if (com.openexchange.java.Strings.isEmpty(urlString)) {
            return urlString;
        }
        try {
            final String ret = URLDecoder.decode(urlString, "ISO-8859-1");
            if (isAscii(ret)) {
                return checkUrl(ret);
            }
            return checkUrl(URLDecoder.decode(urlString, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            LOG.trace("", e);
            return urlString;
        }
    }

    /**
     * Checks given URL string for syntactical correctness.
     *
     * @param sUrl The URL string
     * @throws MalformedURLException If URL string is invalid
     */
    private static String checkUrl(final String sUrl) throws MalformedURLException {
        if (com.openexchange.java.Strings.isEmpty(sUrl)) {
            // Nothing to check
            return sUrl;
        }
        final java.net.URL url = new java.net.URL(sUrl);
        final String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new MalformedURLException("Only http & https protocols supported.");
        }
        return sUrl;
    }

    /**
     * Checks whether the specified string's characters are ASCII 7 bit
     *
     * @param s The string to check
     * @return <code>true</code> if string's characters are ASCII 7 bit; otherwise <code>false</code>
     */
    private static boolean isAscii(final String s) {
        final int length = s.length();
        boolean isAscci = true;
        for (int i = 0; (i < length) && isAscci; i++) {
            isAscci = (s.charAt(i) < 128);
        }
        return isAscci;
    }

    private static String prepareUrlString(final String urlString) {
        if (com.openexchange.java.Strings.isEmpty(urlString)) {
            return urlString;
        }
        final String tmp = urlString.toLowerCase(Locale.US);
        int pos = tmp.indexOf("http://");
        if (pos < 0) {
            pos = tmp.indexOf("https://");
        }
        return pos > 0 ? urlString.substring(pos) : urlString;
    }
}
