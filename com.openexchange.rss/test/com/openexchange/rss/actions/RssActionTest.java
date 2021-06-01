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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.Category;
import com.openexchange.exception.OXException;
import com.openexchange.rss.RssExceptionCodes;
import com.openexchange.rss.osgi.Services;
import com.openexchange.rss.util.TimeoutHttpURLFeedFetcher;
import com.openexchange.rss.utils.RssProperties;
import com.openexchange.test.mock.MockUtils;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.fetcher.FetcherException;
import com.sun.syndication.io.FeedException;

/**
 * {@link RssActionTest}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin
 *         Schneider</a>
 * @since v7.8.2
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Services.class, RssProperties.class, InetAddress.class })
public class RssActionTest {

	private RssAction action;

	@Mock
	private ConfigurationService configurationService;

	@Mock
	private TimeoutHttpURLFeedFetcher fetcher;

	private RssProperties rssProperties;

	List<URL> urls = new ArrayList<>();

	List<OXException> warnings = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		PowerMockito.mockStatic(Services.class);
		Mockito.when(Services.optService(ConfigurationService.class)).thenReturn(configurationService);
		Mockito.when(Services.getService(ConfigurationService.class)).thenReturn(configurationService);
		PowerMockito.mockStatic(InetAddress.class);
		InetAddress inetAddress = Mockito.mock(InetAddress.class);
		Mockito.when(InetAddress.getByName(ArgumentMatchers.anyString())).thenReturn(inetAddress);
		Mockito.when(configurationService.getProperty("com.openexchange.messaging.rss.feed.blacklist",
				RssProperties.DEFAULT_HOST_BLACKLIST)).thenReturn(RssProperties.DEFAULT_HOST_BLACKLIST);
		Mockito.when(configurationService.getProperty("com.openexchange.messaging.rss.feed.whitelist.ports",
				RssProperties.DEFAULT_PORT_WHITELIST)).thenReturn(RssProperties.DEFAULT_PORT_WHITELIST);
		Mockito.when(configurationService.getProperty(RssProperties.PROP_SCHEMES_WHITELIST,
				RssProperties.DEFAULT_SCHEMES_WHITELIST)).thenReturn(RssProperties.DEFAULT_SCHEMES_WHITELIST);
		Mockito.when(fetcher.retrieveFeed(ArgumentMatchers.any())).thenReturn(Mockito.mock(SyndFeed.class));

		rssProperties = new RssProperties() {

			@Override
			public boolean isDenied(String uriString) {
				if (uriString.indexOf("localhost") >= 0) {
					return true;
				}
				if (uriString.indexOf("127.0.0.1") >= 0) {
					return true;
				}
				if (uriString.indexOf(":77") >= 0) {
					return true;
				}
				if (uriString.indexOf(":88") >= 0) {
					return true;
				}
				if (uriString.indexOf("netdoc://") >= 0) {
					return true;
				}
				if (uriString.indexOf("file://") >= 0) {
					return true;
				}
				if (uriString.indexOf("mailto://") >= 0) {
					return true;
				}
				return false;
			}

			@Override
			public boolean isBlacklisted(String hostName) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean isAllowedScheme(String scheme) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean isAllowed(int port) {
				// TODO Auto-generated method stub
				return false;
			}
		};
		Mockito.when(Services.optService(RssProperties.class)).thenReturn(rssProperties);
		Mockito.when(Services.getService(RssProperties.class)).thenReturn(rssProperties);

		action = new RssAction();
		MockitoAnnotations.initMocks(this);

		MockUtils.injectValueIntoPrivateField(action, "fetcher", fetcher);
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_emptyURLs_returnNoWarningAndNoFeed() throws OXException {
		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(0, acceptedFeedsFromUrls.size());
		assertEquals(0, warnings.size());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_localhostURL_returnWarning() throws MalformedURLException {
		urls.add(new URL("http://localhost:80"));
		try {
			action.getAcceptedFeeds(urls, warnings);
			fail();
		} catch (OXException e) {
			assertEquals(0, warnings.size());
			assertEquals(Category.CATEGORY_USER_INPUT, e.getCategory());
			assertEquals(RssExceptionCodes.RSS_CONNECTION_ERROR.getNumber(), e.getCode());
		}
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_localhostOnly_returnWarning() throws MalformedURLException {
		urls.add(new URL("http://localhost"));
		try {
			action.getAcceptedFeeds(urls, warnings);
			fail();
		} catch (OXException e) {
			assertEquals(0, warnings.size());
			assertEquals(Category.CATEGORY_USER_INPUT, e.getCategory());
			assertEquals(RssExceptionCodes.RSS_CONNECTION_ERROR.getNumber(), e.getCode());
		}
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_LocalhostNotAllowedAndAllowedUrl_returnWarningAndFeed()
			throws OXException, MalformedURLException {
		urls.add(new URL("http://localhost:80"));
		urls.add(new URL("http://guteStube.com"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(1, acceptedFeedsFromUrls.size());
		assertEquals(1, warnings.size());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_LocalhostNotAllowedAndAllowedUrl_checkCorrectAdded()
			throws OXException, IllegalArgumentException, IOException, FeedException, FetcherException {
		urls.add(new URL("http://localhost:80"));
		URL guteStube = new URL("http://guteStube.com");
		urls.add(guteStube);
		SyndFeed syndFeedImpl = new SyndFeedImpl();
		syndFeedImpl.setUri(guteStube.toString());
		Mockito.when(fetcher.retrieveFeed(guteStube)).thenReturn(syndFeedImpl);

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(guteStube.toString(), acceptedFeedsFromUrls.get(0).getUri());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_LocalhostNotAllowedAndAllowedUrl_checkCorrectWarning()
			throws OXException, MalformedURLException {
		URL localhost = new URL("http://localhost:80");
		urls.add(localhost);
		urls.add(new URL("http://guteStube.com"));

		action.getAcceptedFeeds(urls, warnings);

		OXException warning = warnings.get(0);
		assertEquals(Category.CATEGORY_WARNING, warning.getCategory());
		assertEquals(RssExceptionCodes.RSS_CONNECTION_ERROR.getNumber(), warning.getCode());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_TwoNotAllowed_returnWarningAndFeed() throws OXException, MalformedURLException {
		urls.add(new URL("http://localhost:80"));
		urls.add(new URL("http://guteStube.com:77"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(0, acceptedFeedsFromUrls.size());
		assertEquals(2, warnings.size());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_TwoNotAllowedWithPathAndFile_returnWarningAndFeed()
			throws OXException, MalformedURLException {
		urls.add(new URL("http://localhost:80/this/is/nice"));
		urls.add(new URL("http://guteStube.com:77/tritt/mich.rss"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(0, acceptedFeedsFromUrls.size());
		assertEquals(2, warnings.size());
	}

	// tests bug 45402: SSRF at RSS feeds
	@Test
	public void testGetAcceptedFeeds_multipleCorrectAndInvalid_returnCorrectData()
			throws OXException, MalformedURLException {
		urls.add(new URL("http://tollerLaden.de:80/this/is/nice"));
		urls.add(new URL("http://tollerLaden.de:88/this/is/not/nice"));
		urls.add(new URL("http://tollerLaden2.de/this/is/nice/too"));
		urls.add(new URL("http://tollerLaden.de/this/is/nice/too/asFile.xml"));
		urls.add(new URL("https://tollerLaden.de:80/this/is/secured/nice"));
		urls.add(new URL("https://tollerLaden.de:88/this/is/secured/not/nice"));
		urls.add(new URL("https://tollerLaden.de/this/is/secured/nice/too"));
		urls.add(new URL("https://tollerLaden2.de/this/is/secured/nice/too/asFile.xml"));
		urls.add(new URL("http://127.0.0.1:80/this/is/never/nice"));
		urls.add(new URL("http://127.0.0.1:88/this/is/never/not/nice"));
		urls.add(new URL("http://127.0.0.1/this/is/never/nice/too"));
		urls.add(new URL("http://127.0.0.1/this/is/never/nice/too/asFile.xml"));
		urls.add(new URL("https://127.0.0.1/this/is/secured/never/nice"));
		urls.add(new URL("https://127.0.0.1:88/this/is/secured/never/d/nice"));
		urls.add(new URL("https://127.0.0.1/this/is/secured/never/nice/too"));
		urls.add(new URL("https://127.0.0.1/this/is/secured/never/nice/too/asFile.xml"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(6, acceptedFeedsFromUrls.size());
		assertEquals(10, warnings.size());
	}

	// tests bug 47891: RSS reader allows to detect local files
	@Test
	public void testGetAcceptedFeeds_onlyInvalidSchemes_returnNoCorrectData()
			throws OXException, MalformedURLException {
		urls.add(new URL("netdoc://tollerLaden.de/this/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("file://tollerLaden.de/this/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("mailto://tollerLaden.de/this/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("netdoc://tollerLaden.de/this/is/secured/never/nice"));
		urls.add(new URL("file://tollerLaden.de/"));
		urls.add(new URL("mailto://tollerLaden.de/this/is/"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(0, acceptedFeedsFromUrls.size());
		assertEquals(6, warnings.size());
	}

	// tests bug 47891: RSS reader allows to detect local files
	@Test
	public void testGetAcceptedFeeds_mixedValidAndInvalidSchemes_returnData()
			throws OXException, MalformedURLException {
		urls.add(new URL("netdoc://tollerLaden.de:80/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("file://tollerLaden.de/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("mailto://tollerLaden.de/this/is/secured/never/nice/too/asFile.xml"));
		urls.add(new URL("netdoc://tollerLaden.de/this/is/secured/never/nice"));
		urls.add(new URL("file://tollerLaden.de/"));
		urls.add(new URL("mailto://tollerLaden.de/this/is/"));
		urls.add(new URL("https://tollerLaden.de:80/this/is/secured/not/nice"));
		urls.add(new URL("https://tollerLaden2.de/this/is/secured/nice/too/asFile.xml"));
		urls.add(new URL("http://tollerLaden.de/this/is/never/nice"));
		urls.add(new URL("ftp://tollerLaden.de/this/is/never/nice/too/asFile.xml"));
		// should be used later when URI is supported
		// urls.add(new URL("news://tollerLaden.de/this/is/never/nice/too/asFile.xml"));
		// urls.add(new URL("feed://tollerLaden.de/this/is/never/nice/too/asFile.xml"));

		List<SyndFeed> acceptedFeedsFromUrls = action.getAcceptedFeeds(urls, warnings);

		assertEquals(4, acceptedFeedsFromUrls.size());
		assertEquals(6, warnings.size());
	}
}
