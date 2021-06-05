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

package com.openexchange.dav;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.openexchange.ajax.Client;
import com.openexchange.clientinfo.ClientInfo;
import com.openexchange.clientinfo.ClientInfoProvider;
import com.openexchange.clientinfo.ClientInfoType;
import com.openexchange.java.Strings;
import com.openexchange.session.Origin;
import com.openexchange.session.Session;
import com.openexchange.uadetector.UserAgentParser;
import net.sf.uadetector.ReadableUserAgent;

/**
 * {@link DAVClientInfoProvider}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 */
public class DAVClientInfoProvider implements ClientInfoProvider {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DAVClientInfoProvider.class);

    private final LoadingCache<String, DAVClientInfo> clientInfoCache;

    private static final String MAC_CALENDAR = "macos_calendar";
    private static final String MAC_ADDRESSBOOK = "macos_addressbook";
    private static final String IOS_DAV = "ios_calendar/addressbook";
    private static final String THUNDERBIRD_LIGHTNING = "thunderbird_lightning";
    private static final String EMCLIENT = "emclient";
    private static final String EMCLIENT_APPSUITE = "emclient_appsuite";
    private static final String OX_SYNC = "oxsyncapp";
    private static final String CALDAV_SYNC = "caldav_sync";
    private static final String CARDDAV_SYNC = "carddav_sync";
    private static final String DAVDROID = "davdroid";
    private static final String DAVX5 = "davx5";
    private static final String WINDOWS_PHONE = "windows_phone";
    private static final String WINDOWS = "windows";
    private static final String KONQUEROR = "konqueror";
    private static final String OUTLOOK_CALDAV_SYNCHRONIZER = "outlook_caldav";
    private static final String GENERIC_CALDAV = "generic_caldav";
    private static final String GENERIC_CARDDAV = "generic_carddav";
    private static final String UNKNOWN = "unknown";

    /**
     * Initializes a new {@link DAVClientInfoProvider}.
     */
    public DAVClientInfoProvider(final UserAgentParser userAgentParser) {
        super();
        CacheLoader<String, DAVClientInfo> loader = new CacheLoader<String, DAVClientInfo>() {

            @Override
            public DAVClientInfo load(String sUserAgent) throws Exception {
                DAVUserAgent userAgent = DAVUserAgent.parse(sUserAgent);
                String clientFamily = getClientFamily(userAgent);
                ReadableUserAgent readableUserAgent = userAgentParser.parse(sUserAgent);
                if (UNKNOWN.equals(clientFamily) && (null == readableUserAgent || UNKNOWN.equalsIgnoreCase(readableUserAgent.getName()))) {
                    if (Strings.isNotEmpty(sUserAgent)) {

                        // Maybe iOS accountsd?
                        if (sUserAgent.contains("iOS") && sUserAgent.contains("accountsd")) {
                            return new DAVClientInfo(DAVUserAgent.IOS.getReadableName(), "ios", null, IOS_DAV, null, IOS_DAV, ClientInfoType.DAV);
                        }

                    }

                    // Unknown User-Agent
                    return new DAVClientInfo(userAgent.getReadableName(), clientFamily);
                }

                String osVersion = null;
                String osFamily = null;
                if (null != readableUserAgent.getOperatingSystem()) {
                    osFamily = readableUserAgent.getOperatingSystem().getFamilyName();
                    String osVersionMajor = readableUserAgent.getOperatingSystem().getVersionNumber().getMajor();
                    String osVersionMinor = readableUserAgent.getOperatingSystem().getVersionNumber().getMinor();
                    if (Strings.isNotEmpty(osVersionMajor)) {
                        if (Strings.isNotEmpty(osVersionMinor)) {
                            osVersion = new StringBuilder(osVersionMajor).append(".").append(osVersionMinor).toString();
                        } else {
                            osVersion = osVersionMajor;
                        }
                    }
                }
                if (Strings.isEmpty(osFamily) || UNKNOWN.equals(osFamily)) {
                    osFamily = getOSFamily(userAgent);
                }

                String clientVersion = null;
                String client = readableUserAgent.getName();
                if (EMCLIENT.equals(clientFamily)) {
                    client = "eM Client";
                }
                if (EMCLIENT_APPSUITE.equals(clientFamily)) {
                    client = "eM Client for OX App Suite";
                }
                String clientVersionMajor = readableUserAgent.getVersionNumber().getMajor();
                String clientVersionMinor = readableUserAgent.getVersionNumber().getMinor();
                if (Strings.isNotEmpty(clientVersionMajor)) {
                    if (Strings.isNotEmpty(clientVersionMinor)) {
                        clientVersion = new StringBuilder(clientVersionMajor).append('.').append(clientVersionMinor).toString();
                    } else {
                        clientVersion = clientVersionMajor;
                    }
                }
                if (DAVUserAgent.OX_SYNC == userAgent || DAVUserAgent.SMOOTH_SYNC == userAgent) {
                    return new DAVClientInfo(userAgent.getReadableName(), osFamily, osVersion, client, clientVersion, clientFamily, ClientInfoType.OXAPP);
                }
                if (UNKNOWN.equals(clientFamily)) {
                    //Maybe akonadi
                    if (Strings.isNotEmpty(sUserAgent) && sUserAgent.contains("akonadi")) {
                        return new DAVClientInfo("KDE/Plasma DAV Client", "linux", null, "akonadi", null, "akonadi", ClientInfoType.DAV);
                    }

                    // Maybe konqueror?
                    if (Strings.isNotEmpty(sUserAgent) && sUserAgent.contains("Linux") && sUserAgent.contains("Konqueror")) {
                        return new DAVClientInfo(KONQUEROR, "linux", null, KONQUEROR, null, KONQUEROR, ClientInfoType.DAV);
                    }
                }
                return new DAVClientInfo(userAgent.getReadableName(), osFamily, osVersion, UNKNOWN.equals(client) ? userAgent.getReadableName() : client, clientVersion, clientFamily);
            }
        };
        clientInfoCache = CacheBuilder.newBuilder().initialCapacity(128).maximumSize(65536).expireAfterAccess(2, TimeUnit.HOURS).build(loader);
    }

    @Override
    public ClientInfo getClientInfo(Session session) {
        if (null == session) {
            return null;
        }

        if (!isDAV(session)) {
            return null;
        }

        String sUserAgent = (String) session.getParameter(Session.PARAM_USER_AGENT);
        if (Strings.isEmpty(sUserAgent)) {
            // Unknown User-Agent
            return new DAVClientInfo(DAVUserAgent.UNKNOWN.getReadableName(), getClientFamily(DAVUserAgent.UNKNOWN));
        }

        try {
            DAVClientInfo davClientInfo = clientInfoCache.get(sUserAgent);
            return davClientInfo;
        } catch (ExecutionException e) {
            LOG.error("Failed to determine client info for User-Agent {}", sUserAgent, e.getCause());
            return new DAVClientInfo(DAVUserAgent.UNKNOWN.getReadableName(), getClientFamily(DAVUserAgent.UNKNOWN));
        }
    }

    @Override
    public ClientInfo getClientInfo(String clientId) {
        if (Strings.isEmpty(clientId)) {
            return null;
        }
        Client client = Client.getClientByID(clientId);
        if (Client.CALDAV.equals(client)) {
            return new DAVClientInfo(DAVUserAgent.GENERIC_CALDAV.getReadableName(), getClientFamily(DAVUserAgent.GENERIC_CALDAV));
        }
        if (Client.CARDDAV.equals(client)) {
            return new DAVClientInfo(DAVUserAgent.GENERIC_CARDDAV.getReadableName(), getClientFamily(DAVUserAgent.GENERIC_CARDDAV));
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Determines the client family from specified DAV user agent
     *
     * @param userAgent The DAV user agent
     * @return The client family; e.g. <code>"macos_calendar"</code> or <code>"thunderbird_lightning"</code>
     */
    static String getClientFamily(DAVUserAgent userAgent) {
        switch (userAgent) {
            case MAC_CALENDAR:
                return MAC_CALENDAR;
            case MAC_CONTACTS:
                return MAC_ADDRESSBOOK;
            case IOS:
                return IOS_DAV;
            case THUNDERBIRD_LIGHTNING:
                return THUNDERBIRD_LIGHTNING;
            case EM_CLIENT:
                return EMCLIENT;
            case EM_CLIENT_FOR_APPSUITE:
                return EMCLIENT_APPSUITE;
            case OX_SYNC:
            case SMOOTH_SYNC:
                return OX_SYNC;
            case CALDAV_SYNC:
                return CALDAV_SYNC;
            case CARDDAV_SYNC:
                return CARDDAV_SYNC;
            case DAVDROID:
                return DAVDROID;
            case DAVX5:
                return DAVX5;
            case OUTLOOK_CALDAV_SYNCHRONIZER:
                return OUTLOOK_CALDAV_SYNCHRONIZER;
            case WINDOWS_PHONE:
                return WINDOWS_PHONE;
            case WINDOWS:
                return WINDOWS;
            case GENERIC_CALDAV:
                return GENERIC_CALDAV;
            case GENERIC_CARDDAV:
                return GENERIC_CARDDAV;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Determines the OS family from specified DAV user agent
     *
     * @param userAgent The DAV user agent
     * @return The OS family; e.g. <code>"macos"</code> or <code>"windows"</code>
     */
    static String getOSFamily(DAVUserAgent userAgent) {
        switch (userAgent) {
            case MAC_CALENDAR:
            case MAC_CONTACTS:
                return "macos";
            case IOS:
                return "ios";
            case EM_CLIENT:
            case EM_CLIENT_FOR_APPSUITE:
            case WINDOWS:
                return "windows";
            case DAVDROID:
            case OX_SYNC:
            case CALDAV_SYNC:
            case CARDDAV_SYNC:
            case SMOOTH_SYNC:
                return "android";
            default:
                return "unknown";
        }
    }

    private boolean isDAV(Session session) {
        return Origin.CALDAV.equals(session.getOrigin()) || Origin.CARDDAV.equals(session.getOrigin());
    }

}
