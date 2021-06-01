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

package com.openexchange.share.impl;

import static com.openexchange.java.Autoboxing.I;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.mail.internet.AddressException;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.ldap.UserImpl;
import com.openexchange.groupware.modules.Module;
import com.openexchange.groupware.userconfiguration.Permission;
import com.openexchange.groupware.userconfiguration.UserConfigurationCodes;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.guest.GuestService;
import com.openexchange.java.Strings;
import com.openexchange.mail.mime.QuotedInternetAddress;
import com.openexchange.password.mechanism.PasswordDetails;
import com.openexchange.password.mechanism.PasswordMech;
import com.openexchange.password.mechanism.PasswordMechRegistry;
import com.openexchange.password.mechanism.stock.StockPasswordMechs;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.share.AuthenticationMode;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.core.ShareConstants;
import com.openexchange.share.core.tools.ShareToken;
import com.openexchange.share.core.tools.ShareTool;
import com.openexchange.share.recipient.AnonymousRecipient;
import com.openexchange.share.recipient.GuestRecipient;
import com.openexchange.share.recipient.ShareRecipient;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.user.UserService;
import com.openexchange.userconf.UserConfigurationService;
import com.openexchange.userconf.UserPermissionService;

/**
 * {@link ShareUtils}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.0
 */
public class ShareUtils {

    private final ServiceLookup services;

    /**
     * Initializes a new {@link ShareUtils}.
     *
     * @param services The service lookup reference
     */
    public ShareUtils(ServiceLookup services) {
        super();
        this.services = services;
    }

    /**
     * Gets the service of specified type, throwing an appropriate excpetion if it's missing.
     *
     * @param clazz The service's class
     * @return The service
     */
    public <S extends Object> S requireService(Class<? extends S> clazz) throws OXException {
        S service = services.getService(clazz);
        if (null == service) {
            throw ServiceExceptionCode.absentService(clazz);
        }
        return service;
    }

    /**
     * Gets the context where the session's user is located in.
     *
     * @param session The session
     * @return The context
     */
    public Context getContext(Session session) throws OXException {
        if (ServerSession.class.isInstance(session)) {
            return ((ServerSession) session).getContext();
        }
        return requireService(ContextService.class).getContext(session.getContextId());
    }

    /**
     * Gets the session's user.
     *
     * @param session The session
     * @return The user
     */
    public User getUser(Session session) throws OXException {
        if (ServerSession.class.isInstance(session)) {
            return ((ServerSession) session).getUser();
        }
        return requireService(UserService.class).getUser(session.getUserId(), session.getContextId());
    }

    /**
     * Prepares a guest user instance based on the supplied share recipient.
     * 
     * @param contextId The context identifier 
     * @param sharingUser The sharing user
     * @param recipient The recipient description
     * @param target The share target
     * @return The guest user
     * @throws OXException In case of error
     */
    public UserImpl prepareGuestUser(int contextId, User sharingUser, ShareRecipient recipient, ShareTarget target) throws OXException {
        if (AnonymousRecipient.class.isInstance(recipient)) {
            return prepareGuestUser(sharingUser, (AnonymousRecipient) recipient, target);
        } else if (GuestRecipient.class.isInstance(recipient)) {
            return prepareGuestUser(contextId, sharingUser, (GuestRecipient) recipient);
        } else {
            throw ShareExceptionCodes.UNEXPECTED_ERROR.create("unsupported share recipient: " + recipient);
        }
    }

    /**
     * Prepares a (named) guest user instance. If no password is defined in the supplied guest recipient, an auto-generated one is used.
     *
     * @param sharingUser The sharing user
     * @param recipient The recipient description
     * @return The guest user
     */
    private UserImpl prepareGuestUser(int contextId, User sharingUser, GuestRecipient recipient) throws OXException {
        /*
         * extract and validate the recipient's e-mail address
         */
        String emailAddress;
        try {
            QuotedInternetAddress address = new QuotedInternetAddress(recipient.getEmailAddress(), true);
            emailAddress = address.getAddress();
        } catch (AddressException e) {
            throw ShareExceptionCodes.INVALID_MAIL_ADDRESS.create(recipient.getEmailAddress());
        }
        /*
         * try to lookup & reuse data from existing guest in other context via guest service
         */
        String groupId = requireService(ConfigViewFactory.class).getView(sharingUser.getId(), contextId).opt("com.openexchange.context.group", String.class, "default");
        UserImpl copiedUser = requireService(GuestService.class).createUserCopy(emailAddress, groupId, contextId);
        if (copiedUser != null) {
            return prepareGuestUser(sharingUser, copiedUser);
        }
        /*
         * prepare new guest user for recipient & set "was created" marker
         */
        UserImpl guestUser = prepareGuestUser(sharingUser);
        guestUser.setDisplayName(recipient.getDisplayName());
        guestUser.setMail(emailAddress);
        guestUser.setLoginInfo(emailAddress);
        if (recipient.getPreferredLanguage() != null) {  // If recipient language specified, use rather than use sharingUser's language
            guestUser.setPreferredLanguage(recipient.getPreferredLanguage());
        }
        if (Strings.isNotEmpty(recipient.getPassword())) {
            PasswordMechRegistry passwordMechRegistry = services.getService(PasswordMechRegistry.class);
            PasswordMech passwordMech = passwordMechRegistry.get(guestUser.getPasswordMech());
            if (passwordMech == null) {
                passwordMech = StockPasswordMechs.BCRYPT.getPasswordMech();
            }
            PasswordDetails passwordDetails = passwordMech.encode(recipient.getPassword());
            guestUser.setPasswordMech(passwordDetails.getPasswordMech());
            guestUser.setUserPassword(passwordDetails.getEncodedPassword());
            guestUser.setSalt(passwordDetails.getSalt());
        }
        return guestUser;
    }

    /**
     * Prepares an anonymous guest user instance.
     *
     * @param sharingUser The sharing user
     * @param recipient The recipient description
     * @param target The link target
     * @return The guest user
     */
    private UserImpl prepareGuestUser(User sharingUser, AnonymousRecipient recipient, ShareTarget target) throws OXException {
        UserImpl guestUser = prepareGuestUser(sharingUser);
        guestUser.setDisplayName("Guest");
        guestUser.setMail("");
        if (null != recipient.getPassword()) {
            PasswordDetails passwordDetails = services.getService(PasswordMechRegistry.class).get(ShareConstants.PASSWORD_MECH_ID).encode(recipient.getPassword());
            guestUser.setUserPassword(passwordDetails.getEncodedPassword());
            guestUser.setPasswordMech(ShareConstants.PASSWORD_MECH_ID);
            guestUser.setSalt(passwordDetails.getSalt());
        } else {
            guestUser.setPasswordMech("");
        }
        if (null != recipient.getExpiryDate()) {
            String expiryDateValue = String.valueOf(recipient.getExpiryDate().getTime());
            ShareTool.assignUserAttribute(guestUser, ShareTool.EXPIRY_DATE_USER_ATTRIBUTE, expiryDateValue);
        }
        ShareTool.assignUserAttribute(guestUser, ShareTool.LINK_TARGET_USER_ATTRIBUTE, ShareTool.targetToJSON(target).toString());
        return guestUser;
    }

    /**
     * Prepares a guest user instance based on a "parent" sharing user.
     *
     * @param sharingUser The sharing user
     * @return The guest user
     */
    private UserImpl prepareGuestUser(User sharingUser) {
        UserImpl guestUser = new UserImpl();
        guestUser.setCreatedBy(sharingUser.getId());
        guestUser.setPreferredLanguage(sharingUser.getPreferredLanguage());
        guestUser.setTimeZone(sharingUser.getTimeZone());
        guestUser.setMailEnabled(true);
        ShareToken.assignBaseToken(guestUser);
        return guestUser;
    }

    /**
     * Prepares a guest user instance based on a "parent" sharing user.
     *
     * @param sharingUser The sharing user
     * @param guestUser The existing guest user to prepare
     * @return The guest user
     */
    private UserImpl prepareGuestUser(User sharingUser, UserImpl guestUser) {
        if (guestUser == null) {
            return prepareGuestUser(sharingUser);
        }
        guestUser.setCreatedBy(sharingUser.getId());
        guestUser.setPreferredLanguage(sharingUser.getPreferredLanguage());
        guestUser.setTimeZone(sharingUser.getTimeZone());
        guestUser.setMailEnabled(true);
        ShareToken.assignBaseToken(guestUser);
        return guestUser;
    }

    /**
     * Prepares a user contact for a guest user.
     *
     * @param contextId The context identifier
     * @param sharingUser The sharing user
     * @param guestUser The guest user
     * @return The guest contact
     */
    public Contact prepareGuestContact(int contextId, User sharingUser, User guestUser) throws OXException {
        String groupId = requireService(ConfigViewFactory.class).getView(sharingUser.getId(), contextId).opt("com.openexchange.context.group", String.class, "default");
        /*
         * try to lookup & reuse data from existing guest in other context via guest service
         */
        Contact copiedContact = requireService(GuestService.class).createContactCopy(guestUser.getMail(), groupId, contextId, guestUser.getId());
        if (null != copiedContact) {
            return copiedContact;
        }
        /*
         * prepare new contact for recipient
         */
        Contact contact = new Contact();
        contact.setParentFolderID(FolderObject.VIRTUAL_GUEST_CONTACT_FOLDER_ID);
        contact.setCreatedBy(sharingUser.getId());
        contact.setDisplayName(guestUser.getDisplayName());
        contact.setEmail1(guestUser.getMail());
        return contact;
    }

    /**
     * Sets a user's permission bits. This includes assigning initial permission bits, as well as updating already existing permissions.
     *
     * @param connection The database connection to use
     * @param context The context
     * @param userID The identifier of the user to set the permission bits for
     * @param permissionBits The permission bits to set
     * @param merge <code>true</code> to merge with the previously assigned permissions, <code>false</code> to overwrite
     * @return The updated permission bits
     */
    public UserPermissionBits setPermissionBits(Connection connection, Context context, int userID, int permissionBits, boolean merge) throws OXException {
        UserPermissionService userPermissionService = requireService(UserPermissionService.class);
        UserPermissionBits userPermissionBits = null;
        try {
            userPermissionBits = userPermissionService.getUserPermissionBits(connection, userID, context);
        } catch (OXException e) {
            if (false == UserConfigurationCodes.NOT_FOUND.equals(e)) {
                throw e;
            }
        }
        if (null == userPermissionBits) {
            /*
             * save permission bits
             */
            userPermissionBits = new UserPermissionBits(permissionBits, userID, context);
            userPermissionService.saveUserPermissionBits(connection, userPermissionBits);
        } else if (userPermissionBits.getPermissionBits() != permissionBits) {
            /*
             * update permission bits
             */
            userPermissionBits.setPermissionBits(merge ? permissionBits | userPermissionBits.getPermissionBits() : permissionBits);
            userPermissionService.saveUserPermissionBits(connection, userPermissionBits);
            /*
             * invalidate affected user configuration
             */
            requireService(UserConfigurationService.class).removeUserConfiguration(userID, context);
        }
        return userPermissionBits;
    }

    /**
     * Gets permission bits suitable for a guest user being allowed to access the supplied share target. Besides the concrete module
     * permission(s), this includes the permission bits to access shared and public folders, as well as the bit to turn off portal
     * access.
     *
     * @param recipient The share recipient
     * @param target The share target
     * @return The permission bits
     */
    public int getRequiredPermissionBits(ShareRecipient recipient, ShareTarget target) throws OXException {
        return getRequiredPermissionBits(ShareTool.getAuthenticationMode(recipient), Collections.singleton(I(target.getModule())));
    }

    /**
     * Gets permission bits suitable for a guest user being allowed to access all supplied modules. Besides the concrete module
     * permission(s), this includes the permission bits to access shared and public folders, as well as the bit to turn off portal
     * access.
     *
     * @param guest The guest user
     * @param modules The module identifiers
     * @return The permission bits
     */
    public int getRequiredPermissionBits(User guest, Collection<Integer> modules) throws OXException {
        return getRequiredPermissionBits(ShareTool.getAuthenticationMode(guest), modules);
    }

    /**
     * Gets permission bits suitable for a guest user being allowed to access all supplied modules. Besides the concrete module
     * permission(s), this includes the permission bits to access shared and public folders, as well as the bit to turn off portal
     * access.
     *
     * @param guest The guest user
     * @param modules The module identifiers
     * @return The permission bits
     */
    private int getRequiredPermissionBits(AuthenticationMode authentication, Collection<Integer> modules) throws OXException {
        Set<Permission> perms = new HashSet<Permission>(8);
        perms.add(Permission.DENIED_PORTAL);
        perms.add(Permission.EDIT_PUBLIC_FOLDERS);
        perms.add(Permission.READ_CREATE_SHARED_FOLDERS);
        if (AuthenticationMode.GUEST == authentication || AuthenticationMode.GUEST_PASSWORD == authentication) {
            perms.add(Permission.EDIT_PASSWORD);
        }
        for (Integer module : modules) {
            addModulePermissions(perms, module.intValue());
        }
        return Permission.toBits(perms);
    }

    /**
     * Adds a module permission to the supplied permission set.
     *
     * @param perms The permission set
     * @param module The module to add the permissions for
     * @return The adjusted permission set
     */
    private Set<Permission> addModulePermissions(Set<Permission> perms, int module) throws OXException {
        Module matchingModule = Module.getForFolderConstant(module);
        if (null != matchingModule) {
            Permission modulePermission = matchingModule.getPermission();
            if (null == modulePermission) {
                throw ShareExceptionCodes.UNEXPECTED_ERROR.create("No module permission for module " + matchingModule);
            }
            perms.add(modulePermission);
        }
        return perms;
    }
}
