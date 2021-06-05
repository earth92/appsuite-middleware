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

package com.openexchange.ipcheck.countrycode;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InetAddresses;
import com.openexchange.ajax.ipcheck.IPCheckConfiguration;
import com.openexchange.ajax.ipcheck.IPCheckers;
import com.openexchange.ajax.ipcheck.spi.IPChecker;
import com.openexchange.exception.OXException;
import com.openexchange.geolocation.GeoInformation;
import com.openexchange.geolocation.GeoLocationService;
import com.openexchange.geolocation.exceptions.GeoLocationExceptionCodes;
import com.openexchange.ipcheck.countrycode.mbean.IPCheckMetricCollector;
import com.openexchange.management.MetricAware;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;

/**
 * {@link CountryCodeIpChecker}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.8.4
 */
public class CountryCodeIpChecker implements IPChecker, MetricAware<IPCheckMetricCollector> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CountryCodeIpChecker.class);

    private final IPCheckMetricCollector metricCollector;
    private final ServiceLookup services;

    /**
     * Initializes a new {@link CountryCodeIpChecker}.
     *
     * @param metricCollector The {@link IPCheckMetricCollector}
     */
    public CountryCodeIpChecker(ServiceLookup services, IPCheckMetricCollector metricCollector) {
        super();
        this.services = services;
        this.metricCollector = metricCollector;
    }

    @Override
    public void handleChangedIp(String current, String previous, Session session, IPCheckConfiguration configuration) throws OXException {
        boolean whiteListedClient = IPCheckers.isWhitelistedClient(session, configuration);
        // ACCEPT: If session-associated client is white-listed
        if (whiteListedClient) {
            accept(current, previous, session, true, AcceptReason.WHITE_LISTED);
            return;
        }

        // ACCEPT: If one of the given IP address lies with in the private range
        if (isPrivateV4Address(current) || isPrivateV4Address(previous)) {
            accept(current, previous, session, whiteListedClient, AcceptReason.PRIVATE_IPV4);
            return;
        }

        // ACCEPT: if the IP address is white-listed
        if (IPCheckers.isWhiteListedAddress(current, previous, configuration)) {
            accept(current, previous, session, whiteListedClient, AcceptReason.WHITE_LISTED);
            return;
        }
        try {
            GeoLocationService service = services.getServiceSafe(GeoLocationService.class);
            GeoInformation geoInformationCurrent = service.getGeoInformation(session.getContextId(), InetAddress.getByName(current));
            if (geoInformationCurrent == null) {
                LOGGER.warn("No geo information could be retrieved for the current IP '{}'.", current);
                return;
            }
            GeoInformation geoInformationPrevious = service.getGeoInformation(session.getContextId(), InetAddress.getByName(previous));
            if (geoInformationPrevious == null) {
                LOGGER.warn("No geo information could be retrieved for the previous IP '{}'.", previous);
                return;
            }

            boolean countryChanged = true;
            if (geoInformationPrevious.hasCountry() && geoInformationCurrent.hasCountry()) {
                countryChanged = !geoInformationPrevious.getCountry().equals(geoInformationCurrent.getCountry());
            }
            // DENY: if country code did change
            if (countryChanged) {
                deny(current, previous, session, DenyReason.COUNTRY_CHANGE);
            }
        } catch (OXException e) {
            if (!e.getPrefix().equals(GeoLocationExceptionCodes.PREFIX)) {
                throw e;
            }
            String message = e.getMessage();
            LOGGER.error("{}", message, e);
            deny(current, previous, session, DenyReason.EXCEPTION, e);
        } catch (UnknownHostException e) {
            LOGGER.debug("Invalid addresses were specified: {}, {}", previous, current, e);
            deny(current, previous, session, DenyReason.EXCEPTION, e);
        }

        // ACCEPT
        accept(current, previous, session, whiteListedClient, AcceptReason.ELIGIBLE);
    }

    @Override
    public String getId() {
        return "countrycode";
    }

    @Override
    public IPCheckMetricCollector getMetricsObject() {
        return metricCollector;
    }

    ///////////////////////////////////////////// HELPERS //////////////////////////////////////////////////

    /**
     * Accepts the IP change and applies it to the specified {@link Session}
     *
     * @param current The current IP address
     * @param session The {@link Session}
     * @param whiteListedClient Whether client is white-listed
     * @param acceptReason The accept reason
     */
    private void accept(String current, String previous, Session session, boolean whiteListedClient, AcceptReason acceptReason) {
        if (false == IPCheckers.updateIPAddress(current, session, whiteListedClient)) {
            return;
        }
        LOGGER.debug("The IP change from '{}' to '{}' was accepted. Reason: '{}'", previous, current, acceptReason.getMessage());
        metricCollector.incrementAccepted(acceptReason);
    }

    /**
     * Denies the IP change and kicks the specified {@link Session}
     *
     * @param current The current IP
     * @param session The {@link Session}
     * @throws OXException To actually kick the session
     */
    private void deny(String current, String previous, Session session, DenyReason reason) throws OXException {
        deny(current, previous, session, reason, null);
    }

    /**
     * Denies the IP change and kicks the specified {@link Session}
     *
     * @param current The current IP
     * @param session The {@link Session}
     * @param t The exception
     * @throws OXException To actually kick the session
     */
    private void deny(String current, String previous, Session session, DenyReason reason, Throwable t) throws OXException {
        if (null == t) {
            LOGGER.debug("The IP change from '{}' to '{}' was denied. Reason: '{}'", previous, current, reason.getMessage());
        } else {
            LOGGER.debug("The IP change from '{}' to '{}' was denied. Reason: '{}', '{}'", previous, current, reason.getMessage(), t.getMessage());
        }

        metricCollector.incrementDenied(reason);
        IPCheckers.kick(current, session);
    }

    private static final Cache<String, Boolean> CACHE_PRIVATE_IPS = CacheBuilder.newBuilder().maximumSize(65536).expireAfterAccess(90, TimeUnit.MINUTES).build();

    /**
     * Checks whether the specified IP address lies in the private range.
     *
     * @param ip The IP address to check
     * @return <code>true</code> if the IP address lies within the private range of class A, B, or C; <code>false</code> otherwise
     */
    private boolean isPrivateV4Address(final String ip) {
        Boolean isPrivate = CACHE_PRIVATE_IPS.getIfPresent(ip);
        if (null != isPrivate) {
            return isPrivate.booleanValue();
        }

        Callable<Boolean> loader = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                return Boolean.valueOf(doCheckForPrivateV4Address(ip));
            }
        };

        try {
            return CACHE_PRIVATE_IPS.get(ip, loader).booleanValue();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
        }
    }

    /**
     * <p>Checks whether the specified IP address lies in the private range.</p>
     *
     * <ul>
     * <li>Class A range: 10.0.0.0 - 10.255.255.255</li>
     * <li>Class B range: 172.16.0.0 - 172.31.255.255</li>
     * <li>Class C range: 192.168.0.0 - 192.168.255.255</li>
     * </ul>
     *
     * <p>
     * Based on {@link Inet4Address#isSiteLocalAddress()}, with addition of the check for the 172.16.0.0 - 172.31.255.255 block
     * </p>
     *
     * @param ip The IP address to check
     * @return <code>true</code> if the IP address lies within the private range of class A, B, or C;
     *         <code>false</code> otherwise
     */
    static boolean doCheckForPrivateV4Address(String ip) {
        int address = InetAddresses.coerceToInteger(InetAddresses.forString(ip));
        return (((address >>> 24) & 0xFF) == 10) // Class A
            || ((((address >>> 24) & 0xFF) == 172) && ((address >>> 16) & 0xFF) >= 16 && ((address >>> 16) & 0xFF) <= 31) // Class B
            || ((((address >>> 24) & 0xFF) == 192) && (((address >>> 16) & 0xFF) == 168)); // Class C
    }
}
