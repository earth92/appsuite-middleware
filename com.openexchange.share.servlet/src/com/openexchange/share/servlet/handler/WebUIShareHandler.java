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

package com.openexchange.share.servlet.handler;

import java.io.IOException;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.i18n.Translator;
import com.openexchange.notification.FullNameBuilder;
import com.openexchange.share.AuthenticationMode;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.ShareTargetPath;
import com.openexchange.share.core.tools.ShareTool;
import com.openexchange.share.recipient.RecipientType;
import com.openexchange.share.servlet.ShareServletStrings;
import com.openexchange.share.servlet.auth.ShareLoginMethod;
import com.openexchange.share.servlet.internal.ShareServiceLookup;
import com.openexchange.share.servlet.utils.LoginLocation;
import com.openexchange.share.servlet.utils.LoginLocationRegistry;
import com.openexchange.share.servlet.utils.LoginType;
import com.openexchange.share.servlet.utils.MessageType;
import com.openexchange.share.servlet.utils.ShareServletUtils;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * This handler either logs in the guest user (if no password/PIN is set) and redirects him into
 * a web session or redirects him to the login page.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public class WebUIShareHandler extends AbstractShareHandler {

    /**
     * Initializes a new {@link WebUIShareHandler}.
     *
     * @param shareLoginConfiguration The login configuration for shares
     */
    public WebUIShareHandler() {
        super();
    }

    @Override
    public int getRanking() {
        return 0;
    }

    @Override
    public ShareHandlerReply handle(AccessShareRequest shareRequest, HttpServletRequest request, HttpServletResponse response) throws OXException {
        GuestInfo guest = shareRequest.getGuest();
        AuthenticationMode authMode = guest.getAuthentication();
        switch (authMode) {
            case ANONYMOUS:
                if (shareRequest.isInvalidTarget()) {
                    // Deny handling for invalid targets in case the AuthenticationMode is ANONYMOUS. See bug #49464
                    return ShareHandlerReply.DENY;
                }
                //$FALL-THROUGH$
            case GUEST:
            {
                if (shareRequest.isInvalidTarget()) {
                    return redirectToLoginPage(shareRequest, response);
                }

                ShareLoginMethod shareLoginMethod = getShareLoginMethod(shareRequest);
                if (ShareServletUtils.createSessionAndRedirect(guest, shareRequest.getTarget(), request, response, shareLoginMethod)) {
                    return ShareHandlerReply.ACCEPT;
                }

                return ShareHandlerReply.DENY;
            }
            case ANONYMOUS_PASSWORD:
            case GUEST_PASSWORD:
                return redirectToLoginPage(shareRequest, response);
            default:
                return ShareHandlerReply.NEUTRAL;
        }
    }

    /**
     * Get translation function for the share request
     *
     * @param shareRequest The Share request
     * @param sharingUser  The sharing user
     * @return  Translator function
     */
    private static Function<Translator, String> getMessage(AccessShareRequest shareRequest, User sharingUser) {
        ShareTargetPath targetPath = shareRequest.getTargetPath();
        switch (targetPath.getModule()) {
            // Mail
            case FolderObject.MAIL:
                return  t -> String.format(t.translate(ShareServletStrings.SHARE_PASSWORD));
            // Other share
            default:
                if (null != sharingUser) {
                    String toTranslate;
                    if (RecipientType.ANONYMOUS.equals(shareRequest.getGuest().getRecipientType())) {
                        toTranslate = targetPath.isFolder() ? ShareServletStrings.SHARE_FOLDER_WITH_TARGET : ShareServletStrings.SHARE_FILE_WITH_TARGET;
                    } else {
                        toTranslate = targetPath.isFolder() ? ShareServletStrings.SHARE_FOLDER_WITH_TARGET_AND_GUEST_PASSWORD : ShareServletStrings.SHARE_FILE_WITH_TARGET_AND_GUEST_PASSWORD;
                    }
                    return t -> String.format(
                        t.translate(toTranslate),
                        FullNameBuilder.buildFullName(sharingUser, t),
                        shareRequest.getTargetProxy().getLocalizedTitle(t));
                }

                String toTranslate;
                if (RecipientType.ANONYMOUS.equals(shareRequest.getGuest().getRecipientType())) {
                    toTranslate = targetPath.isFolder() ? ShareServletStrings.SHARE_FOLDER_WITH_TARGET_UNKNOWN_SHARING_USER : ShareServletStrings.SHARE_FILE_WITH_TARGET_UNKNOWN_SHARING_USER;
                } else {
                    toTranslate = targetPath.isFolder() ? ShareServletStrings.SHARE_FOLDER_WITH_TARGET_AND_GUEST_PASSWORD_UNKNOWN_SHARING_USER : ShareServletStrings.SHARE_FILE_WITH_TARGET_AND_GUEST_PASSWORD_UNKNOWN_SHARING_USER;
                }
                return t -> String.format(
                    t.translate(toTranslate),
                    shareRequest.getTargetProxy().getLocalizedTitle(t));
        }
    }

    private ShareHandlerReply redirectToLoginPage(AccessShareRequest shareRequest, HttpServletResponse response) throws OXException {
        try {
            GuestInfo guestInfo = shareRequest.getGuest();
            ShareTargetPath targetPath = shareRequest.getTargetPath();
            if (shareRequest.isInvalidTarget()) {
                LoginLocation location;
                if (guestInfo.getAuthentication() == AuthenticationMode.GUEST) {
                    location = new LoginLocation()
                        .status("not_found_continue")
                        .loginType(LoginType.MESSAGE_CONTINUE)
                        .share(guestInfo.getBaseToken())
                        .target(targetPath)
                        .message(MessageType.INFO, t -> t.translate(ShareServletStrings.NO_ACCESS_TO_SHARE_CONTACT_OWNER_CONTINUE))
                        .loginName(guestInfo.getGuestID(), guestInfo.getContextID());
                } else {
                    location = new LoginLocation()
                        .status("not_found_continue")
                        .share(guestInfo.getBaseToken())
                        .loginType(guestInfo.getAuthentication())
                        .target(targetPath)
                        .message(MessageType.INFO, t -> t.translate(ShareServletStrings.NO_ACCESS_TO_SHARE_CONTACT_OWNER_LOG_IN));
                    if (guestInfo.getAuthentication() == AuthenticationMode.GUEST_PASSWORD) {
                        location.loginName(guestInfo.getGuestID(), guestInfo.getContextID());
                    }
                }

                LoginLocationRegistry.getInstance().putAndRedirect(location, response);
                return ShareHandlerReply.ACCEPT;
            }

            User sharingUser = null;
            int sharingUserId = ShareTool.extractShareCreator(targetPath);
            if (0 == sharingUserId) {
                if (ShareTool.checkShareAndGuestCreator(shareRequest.getTargetProxy(), guestInfo)) {
                    sharingUser = ShareServiceLookup.getService(UserService.class, true).getUser(guestInfo.getCreatedBy(), guestInfo.getContextID());
                }
            } else {
                sharingUser = ShareServiceLookup.getService(UserService.class, true).getUser(sharingUserId, guestInfo.getContextID());
            }
            LoginLocation location = new LoginLocation()
                .share(guestInfo.getBaseToken())
                .loginType(guestInfo.getAuthentication())
                .message(MessageType.INFO, getMessage(shareRequest, sharingUser)
            );
            if (guestInfo.getAuthentication() == AuthenticationMode.GUEST_PASSWORD) {
                location.loginName(guestInfo.getGuestID(), guestInfo.getContextID());
            }
            location.target(targetPath);
            LoginLocationRegistry.getInstance().putAndRedirect(location, response);
            return ShareHandlerReply.ACCEPT;
        } catch (IOException e) {
            throw ShareExceptionCodes.IO_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw ShareExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

}
