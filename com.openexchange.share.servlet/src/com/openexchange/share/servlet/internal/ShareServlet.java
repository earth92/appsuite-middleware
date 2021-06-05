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

package com.openexchange.share.servlet.internal;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.openexchange.configuration.ServerProperty;
import com.openexchange.exception.OXException;
import com.openexchange.exception.OXExceptionStrings;
import com.openexchange.groupware.contexts.impl.ContextExceptionCodes;
import com.openexchange.groupware.upgrade.SegmentedUpdateService;
import com.openexchange.java.Strings;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.sessiond.SessionExceptionCodes;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.ShareExceptionMessages;
import com.openexchange.share.ShareService;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.ShareTargetPath;
import com.openexchange.share.groupware.ModuleSupport;
import com.openexchange.share.groupware.TargetProxy;
import com.openexchange.share.servlet.ShareServletStrings;
import com.openexchange.share.servlet.handler.AccessShareRequest;
import com.openexchange.share.servlet.handler.ShareHandler;
import com.openexchange.share.servlet.handler.ShareHandlerReply;
import com.openexchange.share.servlet.utils.LoginLocation;
import com.openexchange.share.servlet.utils.LoginLocationRegistry;
import com.openexchange.share.servlet.utils.LoginType;
import com.openexchange.share.servlet.utils.MessageType;
import com.openexchange.share.servlet.utils.ShareServletUtils;
import com.openexchange.tools.servlet.http.Tools;
import com.openexchange.tools.servlet.ratelimit.RateLimitedException;

/**
 * {@link ShareServlet}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.0
 */
public class ShareServlet extends AbstractShareServlet {

    private static final long serialVersionUID = -598653369873570676L;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ShareServlet.class);

    private final RankingAwareNearRegistryServiceTracker<ShareHandler> shareHandlerRegistry;
    private final AtomicReference<UserAgentBlacklist> userAgentBlacklistRef;

    /**
     * Initializes a new {@link ShareServlet}.
     *
     * @param userAgentBlacklist The User-Agent black-list to use
     * @param shareHandlerRegistry The handler registry
     */
    public ShareServlet(UserAgentBlacklist userAgentBlacklist, RankingAwareNearRegistryServiceTracker<ShareHandler> shareHandlerRegistry) {
        super();
        this.userAgentBlacklistRef = new AtomicReference<UserAgentBlacklist>(userAgentBlacklist);
        this.shareHandlerRegistry = shareHandlerRegistry;
    }

    /**
     * Sets a new User-Agent black-list that shall be used by this servlet.
     *
     * @param newBlacklist The new black-list to apply
     */
    public void setUserAgentBlacklist(UserAgentBlacklist newBlacklist) {
        this.userAgentBlacklistRef.set(newBlacklist);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Tools.disableCaching(response);
        try {
            request.getSession(true);

            // Extract share from path info
            String pathInfo = request.getPathInfo();
            if (pathInfo == null) {
                sendNotFound(request, response);
                return;
            }

            List<String> paths = ShareServletUtils.splitPath(pathInfo);
            if (paths.isEmpty()) {
                LOG.debug("No share found at '{}'", pathInfo);
                sendNotFound(request, response);
                return;
            }

            {
                UserAgentBlacklist userAgentBlacklist = userAgentBlacklistRef.get();
                if (null != userAgentBlacklist) {
                    String userAgent = request.getHeader("User-Agent");
                    if (userAgentBlacklist.isBlacklisted(userAgent)) {
                        LOG.info("User-Agent black-listed: '{}'", userAgent);
                        sendNotFound(request, response, ShareServletStrings.SHARE_NOT_ACCESSIBLE, "client_blacklisted");
                        return;
                    }
                }
            }

            GuestInfo guest = ShareServiceLookup.getService(ShareService.class, true).resolveGuest(paths.get(0));
            if (guest == null) {
                LOG.debug("No guest with token '{}' found at '{}'", paths.get(0), pathInfo);
                sendNotFound(request, response);
                return;
            }
            LOG.debug("Successfully resolved guest at '{}' to {}", pathInfo, guest);

            TargetProxy targetProxy = null;
            ShareTargetPath targetPath = null;
            boolean invalidTarget = false;
            int contextId = guest.getContextID();
            int guestId = guest.getGuestID();
            if (paths.size() > 1) {
                ModuleSupport moduleSupport = ShareServiceLookup.getService(ModuleSupport.class, true);
                targetPath = ShareTargetPath.parse(paths.subList(1, paths.size()));
                if (targetPath == null) {
                    invalidTarget = true;
                } else {
                    int m = targetPath.getModule();
                    String f = targetPath.getFolder();
                    String i = targetPath.getItem();
                    if (moduleSupport.exists(m, f, i, contextId, guestId) && moduleSupport.isVisible(m, f, i, contextId, guestId)) {
                        targetProxy = moduleSupport.resolveTarget(targetPath, contextId, guestId);
                    } else {
                        invalidTarget = true;
                    }
                }

                if (invalidTarget) {
                    List<TargetProxy> otherTargets = moduleSupport.listTargets(contextId, guestId);
                    if (otherTargets.isEmpty()) {
                        sendNotFound(request, response);
                        return;
                    }

                    targetProxy = applyFallbackPathMatching(otherTargets, paths.get(1));
                    if (targetProxy == null) {
                        targetProxy = otherTargets.iterator().next();
                    } else {
                        invalidTarget = false;
                    }
                    targetPath = targetProxy.getTargetPath();
                }
            }

            /*
             * Determine appropriate ShareHandler and handle the share
             */
            if (false == handle(new AccessShareRequest(guest, targetPath, targetProxy, invalidTarget), request, response)) {
                // No appropriate ShareHandler available
                throw ShareExceptionCodes.UNEXPECTED_ERROR.create("No share handler found");
            }
        } catch (RateLimitedException e) {
            // Mark optional HTTP session as rate-limited
            HttpSession optionalHttpSession = request.getSession(false);
            if (optionalHttpSession != null) {
                optionalHttpSession.setAttribute(com.openexchange.servlet.Constants.HTTP_SESSION_ATTR_RATE_LIMITED, Boolean.TRUE);
            }
            // Send error response
            e.send(response);
        } catch (OXException e) {
            handleException(request, response, e);
        }
    }

    /**
     * Passes the resolved share to the most appropriate handler and lets him serve the request.
     *
     * @param shareRequest The share request
     *            isn't existing or accessible.
     * @param request The associated HTTP request
     * @param response The associated HTTP response
     * @return <code>true</code> if the share request was handled, <code>false</code>, otherwise
     */
    private boolean handle(AccessShareRequest shareRequest, HttpServletRequest request, HttpServletResponse response) throws OXException {
        for (ShareHandler handler : shareHandlerRegistry.getServiceList()) {
            ShareHandlerReply reply = handler.handle(shareRequest, request, response);
            if (ShareHandlerReply.NEUTRAL != reply) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the specified OXException
     *
     * @param request The {@link HttpServletRequest}
     * @param response The {@link HttpServletResponse}
     * @param e The {@link OXException} to handle
     * @throws IOException if an I/O error is occurred
     */
    private void handleException(HttpServletRequest request, HttpServletResponse response, OXException e) throws IOException {
        if (ShareExceptionCodes.INVALID_TOKEN.equals(e) || ShareExceptionCodes.UNKNOWN_SHARE.equals(e)) {
            sendNotFound(request, response);
            return;
        }
        if (ContextExceptionCodes.LOCATED_IN_ANOTHER_SERVER.equals(e)) {
            LOG.debug("Could not process share '{}': {}", request.getPathInfo(), e.getMessage(), e);
            try {
                SegmentedUpdateService segmentedUpdateService = ShareServiceLookup.getService(SegmentedUpdateService.class, true);
                String migrationRedirectURL = segmentedUpdateService.getSharingMigrationRedirectURL(request.getServerName());
                if (Strings.isEmpty(migrationRedirectURL)) {
                    LOG.error("Cannot redirect. The property '{}' is not set.", ServerProperty.migrationRedirectURL.getFQPropertyName());
                    LoginLocation location = new LoginLocation().status("internal_error").loginType(LoginType.MESSAGE).message(MessageType.ERROR, t -> t.translate(OXExceptionStrings.MESSAGE_RETRY));
                    LoginLocationRegistry.getInstance().putAndRedirect(location, response);
                    return;
                }
                response.sendRedirect(migrationRedirectURL + request.getServletPath() + request.getPathInfo());
                return;
            } catch (OXException ex) {
                LOG.error("Cannot redirect. An error was encountered while getting the migration URL property.", ex);
                LoginLocation location = new LoginLocation().status("internal_error").loginType(LoginType.MESSAGE).message(MessageType.ERROR, t -> t.translate(OXExceptionStrings.MESSAGE_RETRY));
                LoginLocationRegistry.getInstance().putAndRedirect(location, response);
                return;
            }
        }
        if (SessionExceptionCodes.MAX_SESSION_PER_USER_EXCEPTION.equals(e)) {
            LOG.error("Error processing share '{}': {}", request.getPathInfo(), e.getMessage(), e);
            LoginLocation location = new LoginLocation().status("internal_error").loginType(LoginType.MESSAGE).message(MessageType.ERROR, t -> t.translate(ShareExceptionMessages.SHARE_NOT_AVAILABLE_MSG));
            LoginLocationRegistry.getInstance().putAndRedirect(location, response);
            return;
        }
        LOG.error("Error processing share '{}': {}", request.getPathInfo(), e.getMessage(), e);
        LoginLocation location = new LoginLocation().status("internal_error").loginType(LoginType.MESSAGE).message(MessageType.ERROR, t -> t.translate(OXExceptionStrings.MESSAGE_RETRY));
        LoginLocationRegistry.getInstance().putAndRedirect(location, response);
    }

    /**
     * Sends a redirect with an {@link ShareServletStrings#SHARE_NOT_FOUND appropriate error message} for a not found share.
     *
     * @param request The HTTP servlet request
     * @param response The HTTP servlet response to redirect
     */
    private void sendNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        sendNotFound(request, response, ShareServletStrings.SHARE_NOT_FOUND, "not_found");
    }

    /**
     * Sends a redirect with an appropriate error message for a not found share.
     *
     * @param request The HTTP servlet request
     * @param response The HTTP servlet response to redirect
     * @param displayMessage The message displayed to the user
     * @param status The status to signal
     */
    private void sendNotFound(HttpServletRequest request, HttpServletResponse response, String displayMessage, String status) throws IOException {
        /*
         * send handler-specific "not found" if appropriate
         */
        for (ShareHandler handler : shareHandlerRegistry.getServiceList()) {
            if (ShareHandlerReply.ACCEPT.equals(handler.handleNotFound(request, response, status))) {
                return;
            }
        }
        /*
         * send generic "not found" (via web interface) by default
         */
        LoginLocation location = new LoginLocation().status(status).parameter("status", status).loginType(LoginType.MESSAGE).message(MessageType.ERROR, t -> t.translate(displayMessage));
        LoginLocationRegistry.getInstance().putAndRedirect(location, response);
    }

    private static TargetProxy applyFallbackPathMatching(List<TargetProxy> targets, String path) {
        final int prime = 31;
        for (TargetProxy proxy : targets) {
            ShareTarget target = proxy.getTarget();
            int hashCode = 1;
            hashCode = prime * hashCode + ((target.getFolder() == null) ? 0 : target.getFolder().hashCode());
            hashCode = prime * hashCode + ((target.getItem() == null) ? 0 : target.getItem().hashCode());
            hashCode = prime * hashCode + target.getModule();
            if (String.format("%08x", Integer.valueOf(hashCode)).equals(path)) {
                return proxy;
            }
        }

        return null;
    }

}
