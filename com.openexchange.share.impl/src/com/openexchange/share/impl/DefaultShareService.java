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

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.I2i;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.capabilities.CapabilitySet;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.contact.storage.ContactUserStorage;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.folderstorage.FolderPermissionType;
import com.openexchange.folderstorage.Permission;
import com.openexchange.folderstorage.Permissions;
import com.openexchange.group.Group;
import com.openexchange.group.GroupService;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.ldap.LdapExceptionCode;
import com.openexchange.groupware.ldap.UserImpl;
import com.openexchange.groupware.userconfiguration.UserPermissionBits;
import com.openexchange.guest.GuestService;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Strings;
import com.openexchange.java.util.Pair;
import com.openexchange.password.mechanism.PasswordDetails;
import com.openexchange.password.mechanism.PasswordMechRegistry;
import com.openexchange.quota.QuotaService;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.sessiond.SessiondService;
import com.openexchange.share.CreatedShares;
import com.openexchange.share.GuestInfo;
import com.openexchange.share.LinkUpdate;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.ShareInfo;
import com.openexchange.share.ShareLink;
import com.openexchange.share.ShareService;
import com.openexchange.share.ShareTarget;
import com.openexchange.share.ShareTargetPath;
import com.openexchange.share.core.CreatedSharesImpl;
import com.openexchange.share.core.ShareConstants;
import com.openexchange.share.core.tools.ShareToken;
import com.openexchange.share.core.tools.ShareTool;
import com.openexchange.share.groupware.ModuleSupport;
import com.openexchange.share.groupware.SubfolderAwareTargetPermission;
import com.openexchange.share.groupware.TargetPermission;
import com.openexchange.share.groupware.TargetProxy;
import com.openexchange.share.groupware.TargetUpdate;
import com.openexchange.share.impl.cleanup.GuestCleaner;
import com.openexchange.share.impl.cleanup.GuestLastModifiedMarker;
import com.openexchange.share.impl.quota.ShareQuotas;
import com.openexchange.share.recipient.AnonymousRecipient;
import com.openexchange.share.recipient.GuestRecipient;
import com.openexchange.share.recipient.InternalRecipient;
import com.openexchange.share.recipient.RecipientType;
import com.openexchange.share.recipient.ShareRecipient;
import com.openexchange.user.User;
import com.openexchange.user.UserExceptionCode;
import com.openexchange.user.UserService;
import com.openexchange.userconf.UserPermissionService;

/**
 * {@link DefaultShareService}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.8.0
 */
public class DefaultShareService implements ShareService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultShareService.class);

    /** The default permission bits to use for anonymous link shares */
    private static final int LINK_PERMISSION_BITS = Permissions.createPermissionBits(
        Permission.READ_FOLDER, Permission.READ_ALL_OBJECTS, Permission.NO_PERMISSIONS, Permission.NO_PERMISSIONS, false);

    private final ServiceLookup services;
    private final GuestCleaner guestCleaner;
    private final ShareUtils utils;

    /**
     * Initializes a new {@link DefaultShareService}.
     *
     * @param services The service lookup reference
     * @param guestCleaner An initialized guest cleaner to work with
     */
    public DefaultShareService(ServiceLookup services, GuestCleaner guestCleaner) {
        super();
        this.services = services;
        this.guestCleaner = guestCleaner;
        this.utils = new ShareUtils(services);
    }

    private <S> S requireService(Class<? extends S> clazz) throws OXException {
        S service = services.getOptionalService(clazz);
        if (null == service) {
            throw ServiceExceptionCode.absentService(clazz);
        }
        return service;
    }

    private <S> S requireService(Class<? extends S> clazz, S alreadyAcquired) throws OXException {
        if (alreadyAcquired != null ) {
            return alreadyAcquired;
        }

        return requireService(clazz);
    }

    @Override
    public DefaultGuestInfo resolveGuest(String token) throws OXException {
        ShareToken shareToken = new ShareToken(token);
        int contextID = shareToken.getContextID();
        User guestUser;
        try {
            guestUser = requireService(UserService.class).getUser(shareToken.getUserID(), contextID);
            shareToken.verifyGuest(contextID, guestUser);

            GuestService guestService = requireService(GuestService.class);
            if (guestService != null) {
                guestUser = guestService.alignUserWithGuest(guestUser, contextID);
            }
        } catch (OXException e) {
            if (UserExceptionCode.USER_NOT_FOUND.equals(e) || e.equalsCode(2, "CTX")) {
                LOG.debug("Guest user for share token {} not found, unable to resolve token.", shareToken, e);
                return null;
            }
            throw e;
        }
        return removeExpired(new DefaultGuestInfo(services, guestUser, shareToken, getLinkTarget(contextID, guestUser)));
    }

    @Override
    public GuestInfo getGuestInfo(Session session, int guestID) throws OXException {
        User user = null;
        ConnectionHelper connectionHelper = new ConnectionHelper(session, services, false);
        try {
            user = requireService(UserService.class).getUser(connectionHelper.getConnection(), guestID, utils.getContext(session));
            connectionHelper.commit();
        } catch (OXException e) {
            if (false == UserExceptionCode.USER_NOT_FOUND.equals(e)) {
                throw e;
            }
        } finally {
            connectionHelper.finish();
        }
        if (null != user && user.isGuest()) {
            DefaultGuestInfo guestInfo = new DefaultGuestInfo(services, session.getContextId(), user, getLinkTarget(session.getContextId(), user));
            if (Boolean.TRUE.equals(session.getParameter("com.openexchange.share.administrativeUpdate"))) {
                return guestInfo; // don't remove expired shares during administrative updates to avoid recursions
            }
            return removeExpired(guestInfo);
        }
        return null;
    }

    @Override
    public Set<Integer> getSharingUsersFor(int contextID, int guestID) throws OXException {
        User guestUser = requireService(UserService.class).getUser(guestID, contextID);
        if (false == guestUser.isGuest()) {
            throw ShareExceptionCodes.UNKNOWN_GUEST.create(I(guestID));
        }
        /*
         * always add the user who created this guest
         */
        Set<Integer> userIDs = new HashSet<Integer>();
        userIDs.add(I(guestUser.getCreatedBy()));
        /*
         * for invited guests, also add the user permission entities found in accessible targets
         */
        if (false == ShareTool.isAnonymousGuest(guestUser)) {
            List<TargetProxy> targets = requireService(ModuleSupport.class).listTargets(contextID, guestID);
            for (TargetProxy target : targets) {
                List<TargetPermission> permissions = target.getPermissions();
                if (null != permissions && 0 < permissions.size()) {
                    for (TargetPermission permission : permissions) {
                        if (guestID != permission.getEntity() && false == permission.isGroup()) {
                            userIDs.add(I(permission.getEntity()));
                        }
                    }
                }
            }
        }
        return userIDs;
    }

    @Override
    public boolean isGuestVisibleTo(int guestID, Session session) throws OXException {
        Context context = requireService(ContextService.class).getContext(session.getContextId());
        User guestUser = requireService(UserService.class).getUser(guestID, context);
        if (false == guestUser.isGuest()) {
            throw ShareExceptionCodes.UNKNOWN_GUEST.create(I(guestID));
        }
        /*
         * check if guest was created by session's user
         */
        if (guestID == session.getUserId() || guestUser.getCreatedBy() == session.getUserId() || ShareTool.isAnonymousGuest(guestUser)) {
            return true;
        }
        /*
         * check if any share target is visible to session's user, using the share target path to have the global view on the target
         */
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        for (TargetProxy target : moduleSupport.listTargets(session.getContextId(), guestID)) {
            ShareTargetPath targetPath = target.getTargetPath();
            if (moduleSupport.isVisible(targetPath.getModule(), targetPath.getFolder(), targetPath.getItem(), session.getContextId(), session.getUserId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CreatedShares addTarget(Session session, ShareTarget target, List<ShareRecipient> recipients) throws OXException {
        ShareTool.validateTarget(target);
        LOG.info("Configuring accounts for {} at {} in context {}...", recipients, target, I(session.getContextId()));
        Map<ShareRecipient, ShareInfo> sharesByRecipient = new HashMap<ShareRecipient, ShareInfo>(recipients.size());
        ConnectionHelper connectionHelper = new ConnectionHelper(session, services, true);
        List<ShareInfo> sharesInfos = new ArrayList<ShareInfo>(recipients.size());
        List<GuestInfo> createdGuests = new ArrayList<>(recipients.size());

        try {
            connectionHelper.start();
            /*
             * perform initial checks & get current quota usages
             */
            ShareQuotas shareQuotas = new ShareQuotas(requireService(QuotaService.class), session);
            checkRecipients(recipients, session);
            /*
             * prepare guest users and resulting shares
             */
            for (ShareRecipient recipient : recipients) {
                /*
                 * prepare shares for this recipient
                 */
                Pair<Boolean, ShareInfo> shareInfo = prepareShare(connectionHelper, session, recipient, target);
                sharesInfos.add(shareInfo.getSecond());
                sharesByRecipient.put(recipient, shareInfo.getSecond());
                if (Boolean.TRUE.equals(shareInfo.getFirst())) {
                    createdGuests.add(shareInfo.getSecond().getGuest());
                }
            }
            /*
             * check quota, commit transaction & return result
             */
            shareQuotas.checkAllowsNewShares(createdGuests);
            connectionHelper.commit();
            LOG.info("Accounts at {} in context {} configured: {}", target, I(session.getContextId()), sharesByRecipient.values());
            return new CreatedSharesImpl(sharesByRecipient);
        } finally {
            if (false == connectionHelper.isCommitted()) {
                removeGuestReferences(createdGuests);
            }
            connectionHelper.finish();
        }
    }

    @Override
    public ShareLink optLink(Session session, ShareTarget target) throws OXException {
        Context context = utils.getContext(session);
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        TargetProxy proxy = moduleSupport.load(target, session);
        if (false == proxy.mayAdjust()) {
            return null; // don't expose share link info if no admin access to target
        }
        DefaultShareInfo shareInfo = optLinkShare(session, context, proxy, null);
        return null != shareInfo ? new DefaultShareLink(shareInfo, proxy.getTimestamp(), false) : null;
    }

    @Override
    public ShareLink getLink(Session session, ShareTarget target) throws OXException {
        int count = 0;
        final int MAX_RETRIES = 5;
        do {
            try {
                return getOrCreateLink(session, target);
            } catch (OXException e) {
                if (++count < MAX_RETRIES) {
                    /*
                     * try again in case of concurrent modifications
                     */
                    if ("IFO-1302".equals(e.getErrorCode()) || "FLD-1022".equals(e.getErrorCode())) {
                        LOG.info("Detected concurrent modification during link creation: \"{}\" - trying again ({}/{})...", e.getMessage(), I(count), I(MAX_RETRIES));
                        continue;
                    }
                    /*
                     * try again in case of a concurrent guest deletion
                     */
                    if ("USR-0010".equals(e.getErrorCode())) {
                        LOG.info("Detected stale reference to no longer existing user during link creation: \"{}\" - trying again ({}/{})...", e.getMessage(), I(count), I(MAX_RETRIES));
                        continue;
                    }
                }
                throw e;
            }
        } while (true);
    }

    @Override
    public ShareLink updateLink(Session session, ShareTarget target, LinkUpdate linkUpdate, Date clientTimestamp) throws OXException {
        /*
         * update password of anonymous user for this share link
         */
        UserService userService = requireService(UserService.class);
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        Context context = utils.getContext(session);
        ConnectionHelper connectionHelper = new ConnectionHelper(session, services, true);
        try {
            connectionHelper.start();
            TargetUpdate targetUpdate = moduleSupport.prepareUpdate(session, connectionHelper.getConnection());
            targetUpdate.fetch(Collections.singletonList(target));
            TargetProxy targetProxy = targetUpdate.get(target);
            if (false == targetProxy.mayAdjust()) {
                throw ShareExceptionCodes.NO_EDIT_PERMISSIONS.create(I(session.getUserId()), target, I(session.getContextId()));
            }
            if (clientTimestamp.before(targetProxy.getTimestamp())) {
                throw ShareExceptionCodes.CONCURRENT_MODIFICATION.create(target);
            }
            DefaultShareInfo shareInfo = optLinkShare(session, context, targetProxy, connectionHelper);
            if (null == shareInfo) {
                throw ShareExceptionCodes.INVALID_LINK_TARGET.create(I(target.getModule()), target.getFolder(), target.getItem());
            }
            User guest = userService.getUser(shareInfo.getGuest().getGuestID(), session.getContextId());
            if (false == guest.isGuest() || false == ShareTool.isAnonymousGuest(guest)) {
                throw ShareExceptionCodes.UNKNOWN_GUEST.create(I(guest.getId()));
            }

            boolean guestUserUpdated = false;
            boolean passwordChanged = false;
            boolean folderPermissionUpdate = false;
            if (linkUpdate.containsExpiryDate()) {
                String expiryDateValue = null != linkUpdate.getExpiryDate() ? String.valueOf(linkUpdate.getExpiryDate().getTime()) : null;
                userService.setAttribute(connectionHelper.getConnection(), ShareTool.EXPIRY_DATE_USER_ATTRIBUTE, expiryDateValue, guest.getId(), context);
                guestUserUpdated = true;
            }
            if (linkUpdate.containsPassword()) {
                if (updatePassword(connectionHelper, context, guest, linkUpdate.getPassword())){
                    guestUserUpdated = true;
                    passwordChanged = true;
                }
            }
            if (linkUpdate.containsIncludeSubfolders() && shareInfo.isIncludeSubfolders() != linkUpdate.isIncludeSubfolders() && shareInfo.getTarget().getModule() == FolderObject.INFOSTORE) {
                TargetPermission targetPermission = new SubfolderAwareTargetPermission(shareInfo.getGuest().getGuestID(), false, LINK_PERMISSION_BITS, FolderPermissionType.LEGATOR.getTypeNumber(), null, 0);
                if (shareInfo.isIncludeSubfolders()) {
                    // Remove permission in case includeSubfolders is changed from 'true' to 'false' so handed down permissions get removed as well
                    targetProxy.removePermissions(Collections.singletonList(targetPermission));
                    targetPermission = new SubfolderAwareTargetPermission(shareInfo.getGuest().getGuestID(), false, LINK_PERMISSION_BITS, FolderPermissionType.NORMAL.getTypeNumber(), null, 0);
                }
                // Re-apply the link permission
                targetProxy.applyPermissions(Collections.singletonList(targetPermission));
                targetProxy.touch();
                targetUpdate.run();
                folderPermissionUpdate = true;
            } else if (guestUserUpdated) {
                targetProxy.touch();
                targetUpdate.run();
            }
            connectionHelper.commit();
            if (guestUserUpdated || folderPermissionUpdate) {
                userService.invalidateUser(context, guest.getId());
                if (passwordChanged) {
                    // kill guest user sessions
                    SessiondService service = services.getService(SessiondService.class);
                    if (service == null) {
                        LOG.error("Unable to remove guest sessions, because SessiondService is not available.");
                    } else {
                        service.removeUserSessionsGlobally(guest.getId(), session.getContextId());
                    }
                }
                targetProxy = moduleSupport.load(target, session);
                shareInfo = optLinkShare(session, context, targetProxy, null);
                return new DefaultShareLink(shareInfo, targetProxy.getTimestamp(), false);
            }
            return new DefaultShareLink(shareInfo, targetProxy.getTimestamp(), false);
        } finally {
            connectionHelper.finish();
        }
    }

    @Override
    public void deleteLink(Session session, ShareTarget target, Date clientTimestamp) throws OXException {
        /*
         * delete anonymous guest user permission for this share link
         */
        int guestID;
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        Context context = utils.getContext(session);
        ConnectionHelper connectionHelper = new ConnectionHelper(session, services, true);
        try {
            connectionHelper.start();
            TargetUpdate targetUpdate = moduleSupport.prepareUpdate(session, connectionHelper.getConnection());
            targetUpdate.fetch(Collections.singletonList(target));
            TargetProxy targetProxy = targetUpdate.get(target);
            if (false == targetProxy.mayAdjust()) {
                throw ShareExceptionCodes.NO_EDIT_PERMISSIONS.create(I(session.getUserId()), target, I(session.getContextId()));
            }
            if (clientTimestamp.before(targetProxy.getTimestamp())) {
                throw ShareExceptionCodes.CONCURRENT_MODIFICATION.create(target);
            }
            DefaultShareInfo shareInfo = optLinkShare(session, context, targetProxy, connectionHelper);
            if (null == shareInfo) {
                throw ShareExceptionCodes.INVALID_LINK_TARGET.create(I(target.getModule()), target.getFolder(), target.getItem());
            }
            guestID = shareInfo.getGuest().getGuestID();
            targetProxy.removePermissions(Collections.singletonList(new TargetPermission(guestID, false, 0)));
            targetUpdate.run();
            connectionHelper.commit();
        } finally {
            connectionHelper.finish();
        }
        scheduleGuestCleanup(session.getContextId(), new int[] { guestID });
    }

    @Override
    public void scheduleGuestCleanup(int contextID, int...guestIDs) throws OXException {
        if (null == guestIDs) {
            guestCleaner.scheduleContextCleanup(contextID);
        } else {
            guestCleaner.scheduleGuestCleanup(contextID, guestIDs);
        }
    }

    /**
     * Optionally gets an existing share link for a specific share target, i.e. a share to an anonymous guest user.
     *
     * @param session The user session
     * @param context The context
     * @param proxy The target proxy
     * @param connectionHelper A (started) connection helper, or <code>null</code> if not available
     * @return The share link, if one exists for the target, or <code>null</code>, otherwise
     */
    private DefaultShareInfo optLinkShare(Session session, Context context, TargetProxy proxy, ConnectionHelper connectionHelper) throws OXException {
        List<TargetPermission> permissions = proxy.getPermissions();
        if (null != permissions && 0 < permissions.size()) {
            Map<Integer, Boolean> entities = new HashMap<Integer, Boolean>(permissions.size());
            for (TargetPermission permission : permissions) {
                if (false == permission.isGroup() && LINK_PERMISSION_BITS == permission.getBits()) {
                    boolean includeSubfolders = false;
                    if (permission instanceof SubfolderAwareTargetPermission) {
                        if (((SubfolderAwareTargetPermission) permission).getSystem() != 0) {
                            continue;
                        }
                        includeSubfolders = ((SubfolderAwareTargetPermission) permission).getType() == FolderPermissionType.LEGATOR.getTypeNumber();
                    }
                    entities.put(I(permission.getEntity()), B(includeSubfolders));
                }
            }
            if (0 < entities.size()) {
                UserService userService = requireService(UserService.class);
                ModuleSupport moduleSupport = null;
                for (Entry<Integer, Boolean> entry : entities.entrySet()) {
                    User user;
                    if (null != connectionHelper) {
                        user = userService.getUser(connectionHelper.getConnection(), entry.getKey().intValue(), context);
                    } else {
                        user = userService.getUser(entry.getKey().intValue(), context);
                    }
                    if (ShareTool.isAnonymousGuest(user)) {
                        moduleSupport = requireService(ModuleSupport.class, moduleSupport);
                        ShareTarget dstTarget = moduleSupport.adjustTarget(proxy.getTarget(), session, user.getId(), null != connectionHelper ? connectionHelper.getConnection() : null);
                        ShareTargetPath path = moduleSupport.getPath(dstTarget, session);
                        return new DefaultShareInfo(services, context.getContextId(), user, proxy.getTarget(), dstTarget, path, entry.getValue().booleanValue());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves the a share token to the referenced shares.
     *
     * @param token The token to resolve
     * @param path The path to a specific share target, or <code>null</code> to resolve all accessible shares
     * @return The shares
     */
    List<ShareInfo> getShares(String token, String path) throws OXException {
        DefaultGuestInfo guest = resolveGuest(token);
        if (null == guest) {
            return null;
        }
        int contextID = guest.getContextID();
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        List<ShareInfo> shareInfos;
        if (path == null) {
            List<TargetProxy> proxies = moduleSupport.listTargets(contextID, guest.getGuestID());
            shareInfos = new ArrayList<ShareInfo>(proxies.size());
            for (TargetProxy proxy : proxies) {
                ShareTargetPath targetPath = proxy.getTargetPath();
                ShareTarget srcTarget = new ShareTarget(targetPath.getModule(), targetPath.getFolder(), targetPath.getItem());
                shareInfos.add(new DefaultShareInfo(services, contextID, guest.getUser(), srcTarget, proxy.getTarget(), targetPath, checkForLegatorPermission(proxy.getPermissions(), guest.getGuestID())));
            }
        } else {
            ShareTargetPath targetPath = ShareTargetPath.parse(path);
            if (targetPath == null) {
                return Collections.emptyList();
            }

            shareInfos = new ArrayList<ShareInfo>(1);
            TargetProxy proxy = moduleSupport.resolveTarget(targetPath, contextID, guest.getGuestID());
            ShareTarget srcTarget = new ShareTarget(targetPath.getModule(), targetPath.getFolder(), targetPath.getItem());
            shareInfos.add(new DefaultShareInfo(services, contextID, guest.getUser(), srcTarget, proxy.getTarget(), proxy.getTargetPath(), checkForLegatorPermission(proxy.getPermissions(), guest.getGuestID())));
        }

        return removeExpired(contextID, shareInfos);
    }

    private boolean checkForLegatorPermission(List<TargetPermission> permissions, int guestId) {
        for (TargetPermission perm : permissions) {
            if (perm.isGroup() == false && perm.getBits() == LINK_PERMISSION_BITS && perm.getEntity() == guestId) {
                if (perm instanceof SubfolderAwareTargetPermission) {
                    return ((SubfolderAwareTargetPermission) perm).getType() == FolderPermissionType.LEGATOR.getTypeNumber();
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Gets all shares created in a specific context.
     *
     * @param contextID The context identifier
     * @return The shares, or an empty list if there are none
     */
    List<ShareInfo> getAllShares(int contextID) throws OXException {
        List<ShareInfo> shareInfos = new ArrayList<ShareInfo>();
        UserService userService = requireService(UserService.class);
        int[] guestIDs = userService.listAllUser(contextID, true, true);
        ModuleSupport moduleSupport = null;
        if (null != guestIDs && 0 < guestIDs.length) {
            Set<Integer> guestsWithoutShares = new HashSet<>(guestIDs.length);
            for (int guestID : guestIDs) {
                User guest = userService.getUser(guestID, contextID);
                if (guest.isGuest()) { // double check
                    moduleSupport = requireService(ModuleSupport.class, moduleSupport);
                    List<TargetProxy> targets = moduleSupport.listTargets(contextID, guestID);
                    if (targets.isEmpty()) {
                        guestsWithoutShares.add(I(guestID));
                    } else {
                        for (TargetProxy proxy : targets) {
                            ShareTargetPath targetPath = proxy.getTargetPath();
                            ShareTarget srcTarget = new ShareTarget(targetPath.getModule(), targetPath.getFolder(), targetPath.getItem());
                            shareInfos.add(new DefaultShareInfo(services, contextID, guest, srcTarget, proxy.getTarget(), targetPath, checkForLegatorPermission(proxy.getPermissions(), guestID)));
                        }
                    }
                }
            }

            if (!guestsWithoutShares.isEmpty()) {
                scheduleGuestCleanup(contextID, Autoboxing.Coll2i(guestsWithoutShares));
            }
        }
        return shareInfos;
    }

    /**
     * Gets all shares created in a specific context that were created for a specific user.
     *
     * @param contextID The context identifier
     * @param guestID The guest user identifier
     * @return The shares, or an empty list if there are none
     */
    List<ShareInfo> getAllShares(int contextID, int guestID) throws OXException {
        List<ShareInfo> shareInfos = new ArrayList<ShareInfo>();
        Context context = requireService(ContextService.class).getContext(contextID);
        User guest = requireService(UserService.class).getUser(guestID, context);
        if (false == guest.isGuest()) {
            throw ShareExceptionCodes.UNKNOWN_GUEST.create(I(guestID));
        }
        List<TargetProxy> targets = requireService(ModuleSupport.class).listTargets(contextID, guest.getId());
        for (TargetProxy proxy : targets) {
            ShareTargetPath targetPath = proxy.getTargetPath();
            ShareTarget srcTarget = new ShareTarget(targetPath.getModule(), targetPath.getFolder(), targetPath.getItem());
            shareInfos.add(new DefaultShareInfo(services, contextID, guest, srcTarget, proxy.getTarget(), targetPath, checkForLegatorPermission(proxy.getPermissions(), guestID)));
        }
        return shareInfos;
    }

    /**
     * Removes all shares in a context.
     * <P/>
     * Associated guest permission entities from the referenced share targets are removed implicitly, guest cleanup tasks are scheduled as
     * needed.
     * <p/>
     * This method ought to be called in an administrative context, hence no session is required and no permission checks are performed.
     *
     * @param contextID The context identifier
     * @return The number of affected shares
     */
    int removeShares(int contextID) throws OXException {
        /*
         * load & delete all shares in the context, removing associated target permissions
         */
        List<ShareInfo> shares = getAllShares(contextID);
        ConnectionHelper connectionHelper = new ConnectionHelper(contextID, services, true);
        try {
            connectionHelper.start();
            if (0 < shares.size()) {
                removeTargetPermissions(null, connectionHelper, shares);
            }
            connectionHelper.commit();
        } finally {
            connectionHelper.finish();
        }
        /*
         * schedule cleanup tasks as needed
         */
        if (0 < shares.size()) {
            scheduleGuestCleanup(contextID, I2i(ShareTool.getGuestIDs(shares)));
        }
        return shares.size();
    }

    /**
     * Removes all shares in a context that were created for a specific user.
     * <P/>
     * Associated guest permission entities from the referenced share targets are removed implicitly, guest cleanup tasks are scheduled as
     * needed.
     * <p/>
     * This method ought to be called in an administrative context, hence no session is required and no permission checks are performed.
     *
     * @param contextID The context identifier
     * @param guestID The identifier of the guest user to delete the shares for
     * @return The number of affected shares
     */
    int removeShares(int contextID, int guestID) throws OXException {
        /*
         * load & delete all shares in the context, removing associated target permissions
         */
        List<ShareInfo> shares = getAllShares(contextID, guestID);
        ConnectionHelper connectionHelper = new ConnectionHelper(contextID, services, true);
        try {
            connectionHelper.start();
            if (0 < shares.size()) {
                removeTargetPermissions(null, connectionHelper, shares);
            }
            connectionHelper.commit();
        } finally {
            connectionHelper.finish();
        }
        /*
         * schedule cleanup tasks as needed
         */
        if (0 < shares.size()) {
            scheduleGuestCleanup(contextID, I2i(ShareTool.getGuestIDs(shares)));
        }
        return shares.size();
    }

    /**
     * Removes all shares identified by the supplied tokens. The tokens might either be in their absolute format (i.e. base token plus
     * path), as well as in their base format only, which in turn leads to all share targets associated with the base token being removed.
     * <P/>
     * Associated guest permission entities from the referenced share targets are removed implicitly, guest cleanup tasks are scheduled as
     * needed.
     * <p/>
     * This method ought to be called in an administrative context, hence no session is required and no permission checks are performed.
     *
     * @param tokens The tokens to delete the shares for
     * @return The number of affected shares
     */
    int removeShares(List<String> tokens) throws OXException {
        /*
         * order tokens by context
         */
        Map<Integer, List<String>> tokensByContextID = new HashMap<Integer, List<String>>();
        for (String token : tokens) {
            Integer contextID = I(new ShareToken(token).getContextID());
            List<String> tokensInContext = tokensByContextID.get(contextID);
            if (null == tokensInContext) {
                tokensInContext = new ArrayList<String>();
                tokensByContextID.put(contextID, tokensInContext);
            }
            tokensInContext.add(token);
        }
        /*
         * delete shares per context
         */
        int affectedShares = 0;
        for (Map.Entry<Integer, List<String>> entry : tokensByContextID.entrySet()) {
            affectedShares += removeShares(null, entry.getKey().intValue(), entry.getValue());
        }
        return affectedShares;
    }

    int removeShares(List<String> tokens, int contextID) throws OXException {
        for (String token : tokens) {
            if (contextID != new ShareToken(token).getContextID()) {
                throw ShareExceptionCodes.UNKNOWN_SHARE.create(token);
            }
        }
        return removeShares(tokens);
    }

    /**
     * Checks that the sharing user doesn't try to share targets to himself and has sufficient permissions
     * to create links or invite guests.
     *
     * @param recipients The recipients
     * @param userId the sharing users ID
     * @throws OXException If user is missing appropriate permissions
     */
    private void checkRecipients(List<ShareRecipient> recipients, Session session) throws OXException {
        boolean shareLinks = false;
        boolean inviteGuests = false;
        CapabilityService capabilityService = requireService(CapabilityService.class);
        if (null == capabilityService) {
            throw ServiceExceptionCode.absentService(CapabilityService.class);
        }
        CapabilitySet capabilities = capabilityService.getCapabilities(session);
        if (null != capabilities && capabilities.contains("share_links")) {
            shareLinks = true;
        }
        if (null != capabilities && capabilities.contains("invite_guests")) {
            inviteGuests = true;
        }

        int userId = session.getUserId();
        for (ShareRecipient recipient : recipients) {
            if (recipient.isInternal()) {
                InternalRecipient internal = recipient.toInternal();
                if (!internal.isGroup() && internal.getEntity() == userId) {
                    throw ShareExceptionCodes.NO_SHARING_WITH_YOURSELF.create();
                }
            }
            if (RecipientType.ANONYMOUS.equals(recipient.getType())) {
                if (!shareLinks) {
                    throw ShareExceptionCodes.NO_SHARE_LINK_PERMISSION.create();
                }
            }
            if (RecipientType.GUEST.equals(recipient.getType())) {
                if (!inviteGuests) {
                    throw ShareExceptionCodes.NO_INVITE_GUEST_PERMISSION.create();
                }
            }
        }
    }

    /**
     * Removes all shares identified by the supplied tokens. The tokens might either be in their absolute format (i.e. base token plus
     * path), as well as in their base format only, which in turn leads to all share targets associated with the base token being removed.
     * <P/>
     * Associated guest permission entities from the referenced share targets are removed implicitly, guest cleanup tasks are scheduled as
     * needed.
     * <p/>
     * Depending on the session, the removal is done in terms of an administrative update with no further permission checks, or regular
     * update as performed by the session's user, checking permissions on the share targets implicitly.
     *
     * @param session The session, or <code>null</code> to perform an administrative update
     * @param contextID The context ID
     * @param tokens The tokens to delete the shares for
     * @return The number of affected shares
     */
    private int removeShares(Session session, int contextID, List<String> tokens) throws OXException {
        /*
         * prepare a token collection to distinguish between base tokens only or base token with specific paths
         */
        TokenCollection tokenCollection = new TokenCollection(services, contextID, tokens);
        List<ShareInfo> shares;
        ConnectionHelper connectionHelper = null != session ? new ConnectionHelper(session, services, true) : new ConnectionHelper(contextID, services, true);
        try {
            connectionHelper.start();
            /*
             * load all shares referenced by the supplied tokens
             */
            shares = tokenCollection.loadShares();
            /*
             * delete the shares by removing the associated target permissions
             */
            if (0 < shares.size()) {
                removeTargetPermissions(session, connectionHelper, shares);
            }
            connectionHelper.commit();
        } finally {
            connectionHelper.finish();
        }
        /*
         * schedule cleanup tasks as needed
         */
        if (0 < shares.size()) {
            scheduleGuestCleanup(contextID, tokenCollection.getGuestUserIDs());
        }
        return shares.size();
    }

    /**
     * Filters expired shares from the supplied list of shares and triggers their final deletion, adjusting target permissions as well as
     * cleaning up guest users as needed.
     *
     * @param contextID The context identifier
     * @param shares The shares
     * @return The filtered shares, which may be an empty list if all shares were expired
     * @throws OXException
     */
    private List<ShareInfo> removeExpired(int contextID, List<ShareInfo> shares) throws OXException {
        List<ShareInfo> expiredShares = ShareTool.filterExpiredShares(shares);
        if (null != expiredShares && 0 < expiredShares.size()) {
            ConnectionHelper connectionHelper = new ConnectionHelper(contextID, services, true);
            try {
                connectionHelper.start();
                removeTargetPermissions(null, connectionHelper, expiredShares);
                connectionHelper.commit();
            } finally {
                connectionHelper.finish();
            }
            /*
             * schedule cleanup tasks
             */
            scheduleGuestCleanup(contextID, I2i(ShareTool.getGuestIDs(expiredShares)));
        }
        return shares;
    }

    /**
     * Removes any permissions that are directly associated with the supplied shares, i.e. the permissions in the share targets for the
     * guest entities. Depending on the session, the removal is done in an administrative or regular update.
     *
     * @param session The session, or <code>null</code> to perform an administrative update
     * @param connectionHelper A (started) connection helper
     * @param shares The shares to remove the associated permissions for
     * @throws OXException
     */
    private void removeTargetPermissions(Session session, ConnectionHelper connectionHelper, List<ShareInfo> shares) throws OXException {
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        TargetUpdate targetUpdate;
        if (null == session) {
            targetUpdate = moduleSupport.prepareAdministrativeUpdate(connectionHelper.getContextID(), connectionHelper.getConnection());
        } else {
            targetUpdate = moduleSupport.prepareUpdate(session, connectionHelper.getConnection());
        }
        try {
            Map<ShareTarget, Set<Integer>> guestsByTarget = ShareTool.mapGuestsByTarget(shares);
            targetUpdate.fetch(guestsByTarget.keySet());
            for (Entry<ShareTarget, Set<Integer>> entry : guestsByTarget.entrySet()) {
                Set<Integer> guestIDs = entry.getValue();
                List<TargetPermission> permissions = new ArrayList<TargetPermission>(guestIDs.size());
                for (Integer guestID : guestIDs) {
                    permissions.add(new TargetPermission(guestID.intValue(), false, 0));
                }
                targetUpdate.get(entry.getKey()).removePermissions(permissions);
            }
            targetUpdate.run();
        } finally {
            targetUpdate.close();
        }
    }

    /**
     * Removes a guests permission from a share target.
     *
     * @param guestID The guests ID
     * @param target The share target; must ontain globally valid IDs
     * @param connectionHelper A (started) connection helper
     * @throws OXException
     */
    private void removeTargetPermission(int guestID, ShareTarget target, ConnectionHelper connectionHelper) throws OXException {
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        TargetUpdate targetUpdate = moduleSupport.prepareAdministrativeUpdate(connectionHelper.getContextID(), connectionHelper.getConnection());
        try {
            targetUpdate.fetch(Collections.singletonList(target));
            targetUpdate.get(target).removePermissions(Collections.singletonList(new TargetPermission(guestID, false, 0)));
            targetUpdate.run();
        } finally {
            targetUpdate.close();
        }
    }

    /**
     * Prepares a new individual share for a specific target based on the supplied recipient. This includes resolving the share recipient
     * to an internal permission entity, with new guest entities being provisioned as needed.
     *
     * @param connectionHelper A (started) connection helper
     * @param session The sharing users session
     * @param recipient The share recipient
     * @param shareTarget The share target from the sharing users point of view
     * @return The prepared share, with information if a user was created for the share or not
     */
    private Pair<Boolean, ShareInfo> prepareShare(ConnectionHelper connectionHelper, Session session, ShareRecipient recipient, ShareTarget shareTarget) throws OXException {
        User sharingUser = utils.getUser(session);
        Context context = requireService(ContextService.class).getContext(connectionHelper.getContextID());
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        /*
         * pre-adjust share target to ensure having the current session user's point of view
         */
        ShareTarget target = moduleSupport.adjustTarget(shareTarget, session, session.getUserId(), connectionHelper.getConnection());
        if (RecipientType.GROUP.equals(recipient.getType())) {
            /*
             * prepare pseudo share infos for group recipient
             */
            Group group = requireService(GroupService.class).getGroup(context, ((InternalRecipient) recipient).getEntity());
            int targetUserId = null != group.getMember() && 0 < group.getMember().length ? group.getMember()[0] : context.getMailadmin();
            ShareTarget dstTarget = moduleSupport.adjustTarget(target, session, targetUserId, connectionHelper.getConnection());
            return new Pair<>(Boolean.FALSE, new InternalGroupShareInfo(context.getContextId(), group, target, dstTarget, true));
        }
        /*
         * prepare guest or internal user shares for other recipient types
         */
        int permissionBits = utils.getRequiredPermissionBits(recipient, target);
        Pair<Boolean, User> userPair = getGuestUser(connectionHelper.getConnection(), context, sharingUser, permissionBits, recipient, target);
        User targetUser = userPair.getSecond();

        ShareTarget dstTarget = moduleSupport.adjustTarget(target, session, targetUser.getId(), connectionHelper.getConnection());
        if (false == targetUser.isGuest()) {
            return new Pair<>(Boolean.FALSE, new InternalUserShareInfo(context.getContextId(), targetUser, target, dstTarget, true));
        }
        /*
         * (re-)adjust persisted link permission association for anonymous guests if required
         */
        if (AnonymousRecipient.class.isInstance(recipient) && false == dstTarget.getFolderToLoad().equals(dstTarget.getFolder())) {
            ShareTarget realTarget = new ShareTarget(dstTarget);
            realTarget.setFolder(dstTarget.getFolderToLoad());
            services.getService(UserService.class).setAttribute(connectionHelper.getConnection(), ShareTool.LINK_TARGET_USER_ATTRIBUTE,
                ShareTool.targetToJSON(realTarget).toString(), targetUser.getId(), context, false);
        }
        ShareTargetPath targetPath = moduleSupport.getPath(target, session);
        return new Pair<>(userPair.getFirst(), new DefaultShareInfo(services, context.getContextId(), targetUser, target, dstTarget, targetPath, true));
    }

    /**
     * Updates the guest user behind the anonymous recipient as needed, i.e. adjusts the defined password mechanism and the password
     * itself in case it differs from the updated recipient.
     *
     * @param connection A (writable) connection to the database
     * @param context The context
     * @param guestUser The guest user to update the password for
     * @param password The password to set for the anonymous guest user, or <code>null</code> to remove the password protection
     * @return <code>true</code> if the user was updated, <code>false</code>, otherwise
     */
    private boolean updatePassword(ConnectionHelper connectionHelper, Context context, User guestUser, String password) throws OXException {
        String originalPassword = guestUser.getUserPassword();
        if (null == password && null != originalPassword || null != password && null == originalPassword ||
            null != password && null != originalPassword && false == password.equals(originalPassword)) {
            if (false == ShareTool.isAnonymousGuest(guestUser)) {
                throw ShareExceptionCodes.UNEXPECTED_ERROR.create("Can't change password for non-anonymous guest");
            }
            UserImpl updatedGuest = new UserImpl();
            updatedGuest.setId(guestUser.getId());
            updatedGuest.setCreatedBy(guestUser.getCreatedBy());
            if (Strings.isEmpty(password)) {
                updatedGuest.setPasswordMech("");
                updatedGuest.setUserPassword(null);
                updatedGuest.setSalt(null);
            } else {
                PasswordDetails passwordDetails = requireService(PasswordMechRegistry.class).get(ShareConstants.PASSWORD_MECH_ID).encode(password);
                updatedGuest.setUserPassword(passwordDetails.getEncodedPassword());
                updatedGuest.setSalt(passwordDetails.getSalt());
                updatedGuest.setPasswordMech(ShareConstants.PASSWORD_MECH_ID);
            }
            requireService(UserService.class).updatePassword(connectionHelper.getConnection(), updatedGuest, context);
            return true;
        }
        return false;
    }

    /**
     * Gets a guest user for a new share. A new guest user is created if no matching one exists, the permission bits are applied as needed.
     * In case the guest recipient denotes an already existing, internal user, this user is returned.
     *
     * @param connection A (writable) connection to the database
     * @param context The context
     * @param sharingUser The sharing user
     * @param permissionBits The permission bits to apply to the guest user
     * @param recipient The recipient description
     * @param target The share target
     * @return The guest user with the information if the guest user and its corresponding entries in the configDB was created or not
     */
    private Pair<Boolean, User> getGuestUser(Connection connection, Context context, User sharingUser, int permissionBits, ShareRecipient recipient, ShareTarget target) throws OXException {
        UserService userService = requireService(UserService.class);
        if (GuestRecipient.class.isInstance(recipient)) {
            /*
             * re-use existing, non-anonymous guest user from this context if possible
             */
            GuestRecipient guestRecipient = (GuestRecipient) recipient;
            User existingUser = null;
            try {
                existingUser = userService.searchUser(guestRecipient.getEmailAddress(), context, true, true, false);
            } catch (OXException e) {
                if (false == LdapExceptionCode.NO_USER_BY_MAIL.equals(e)) {
                    throw e;
                }
            }
            if (null != existingUser) {
                if (existingUser.isGuest()) {
                    /*
                     * combine permission bits with existing ones, reset any last modified marker if present
                     */
                    UserPermissionBits userPermissionBits = utils.setPermissionBits(connection, context, existingUser.getId(), permissionBits, true);
                    GuestLastModifiedMarker.clearLastModified(services, context, existingUser);
                    LOG.debug("Using existing guest user {} with permissions {} in context {}: {}", existingUser.getMail(), I(userPermissionBits.getPermissionBits()), I(context.getContextId()), I(existingUser.getId()));
                    /*
                     * As the recipient already belongs to an existing user, its password must be set to null, to avoid wrong notification
                     * messages
                     */
                    guestRecipient.setPassword(null);
                } else {
                    /*
                     * guest recipient points to internal user
                     */
                    LOG.debug("Guest recipient {} points to internal user {} in context {}: {}",
                        guestRecipient.getEmailAddress(), existingUser.getLoginInfo(), I(context.getContextId()), I(existingUser.getId()));
                }
                return new Pair<>(Boolean.FALSE, existingUser);
            }
        } else if (InternalRecipient.class.isInstance(recipient)) {
            InternalRecipient internalRecipient = (InternalRecipient) recipient;
            User user = userService.getUser(internalRecipient.getEntity(), context);
            return new Pair<>(Boolean.FALSE, user);
        }
        /*
         * create new guest user & contact in this context
         */
        ContactUserStorage contactUserStorage = requireService(ContactUserStorage.class);
        UserImpl guestUser = utils.prepareGuestUser(context.getContextId(), sharingUser, recipient, target);
        Contact contact = utils.prepareGuestContact(context.getContextId(), sharingUser, guestUser);
        int contactId = contactUserStorage.createGuestContact(context.getContextId(), contact, connection);
        guestUser.setContactId(contactId);
        int guestID = userService.createUser(connection, context, guestUser);
        guestUser.setId(guestID);
        contact.setCreatedBy(guestID);
        contact.setModifiedBy(guestID);
        contact.setInternalUserId(guestID);
        contactUserStorage.updateGuestContact(context.getContextId(), contactId, contact, connection);
        /*
         * store permission bits
         */
        requireService(UserPermissionService.class).saveUserPermissionBits(connection, new UserPermissionBits(permissionBits, guestID, context));
        if (AnonymousRecipient.class.isInstance(recipient)) {
            LOG.info("Created anonymous guest user with permissions {} in context {}: {}", I(permissionBits), I(context.getContextId()), I(guestID));
        } else {
            GuestService guestService = requireService(GuestService.class);
            String groupId = requireService(ConfigViewFactory.class).getView(sharingUser.getId(), context.getContextId()).opt("com.openexchange.context.group", String.class, "default");
            guestService.addGuest(guestUser.getMail(), groupId, context.getContextId(), guestID, guestUser.getUserPassword(), guestUser.getPasswordMech(), guestUser.getSalt());
            LOG.info("Created guest user {} with permissions {} in context {}: {}", guestUser.getMail(), I(permissionBits), I(context.getContextId()), I(guestID));
        }

        return new Pair<>(Boolean.TRUE, guestUser);
    }

    /**
     * Gets the link target for the given anonymous guest user. If no target is set (via an user attribute)
     * this method tries to restore the consistency by setting the attribute.
     *
     * @param contextId The context ID
     * @param guestUser The guest user
     * @return The target or <code>null</code> if the user is no guest at all or not anonymous
     * @throws OXException
     */
    private ShareTarget getLinkTarget(int contextId, User guestUser) throws OXException {
        if (guestUser.isGuest() && ShareTool.isAnonymousGuest(guestUser)) {
            try {
                String targetAttr = ShareTool.getUserAttribute(guestUser, ShareTool.LINK_TARGET_USER_ATTRIBUTE);
                if (targetAttr == null) {
                    scheduleGuestCleanup(contextId, guestUser.getId());
                    OXException e = ShareExceptionCodes.UNEXPECTED_ERROR.create("Anonymous guest " + guestUser.getId() + " in context " + contextId + " is in inconsistent state - no share target exists.");
                    LOG.warn("Scheduled clean up of broken guest user entity", e);
                    return null;
                }
                return ShareTool.jsonToTarget(new JSONObject(targetAttr));
            } catch (JSONException e) {
                throw ShareExceptionCodes.UNEXPECTED_ERROR.create(e, "Could not compile or resolve share target");
            }
        }
        return null;
    }

    private DefaultShareLink getOrCreateLink(Session session, ShareTarget target) throws OXException {
        Context context = utils.getContext(session);
        ModuleSupport moduleSupport = requireService(ModuleSupport.class);
        ConnectionHelper connectionHelper = new ConnectionHelper(session, services, true);
        try {
            connectionHelper.start();
            TargetUpdate targetUpdate = moduleSupport.prepareUpdate(session, connectionHelper.getConnection());
            targetUpdate.fetch(Collections.singletonList(target));
            TargetProxy targetProxy = targetUpdate.get(target);
            if (false == targetProxy.mayAdjust()) {
                throw ShareExceptionCodes.NO_EDIT_PERMISSIONS.create(I(session.getUserId()), target, I(session.getContextId()));
            }
            DefaultShareInfo existingLink = optLinkShare(session, context, targetProxy, connectionHelper);
            if (null != existingLink) {
                return new DefaultShareLink(existingLink, targetProxy.getTimestamp(), false);
            }
            /*
             * check quota & create new anonymous recipient for this target
             */
            new ShareQuotas(requireService(QuotaService.class), session).checkAllowsNewLinks(1);
            AnonymousRecipient recipient = new AnonymousRecipient(LINK_PERMISSION_BITS, null, null);
            LOG.info("Adding new share link to {} for {} in context {}...", target, recipient, I(session.getContextId()));
            /*
             * perform initial checks
             */
            checkRecipients(Collections.singletonList(recipient), session);
            /*
             * prepare link share & apply new permission entity for this target
             */
            ShareInfo shareInfo = prepareShare(connectionHelper, session, recipient, target).getSecond();
            TargetPermission targetPermission = new SubfolderAwareTargetPermission(shareInfo.getGuest().getGuestID(), false, recipient.getBits(), FolderPermissionType.LEGATOR.getTypeNumber(), null, 0);

            targetProxy.applyPermissions(Collections.singletonList(targetPermission));
            Date oldSeq = targetProxy.getTimestamp();
            /*
             * run target update, commit transaction & return created share link info
             */
            targetUpdate.run();
            connectionHelper.commit();
            LOG.info("Share link to {} for {} in context {} added successfully.", target, recipient, I(session.getContextId()));
            Date timestamp = targetProxy.getTimestamp();
            if (oldSeq.getTime() == timestamp.getTime()) {
                // Timestamp not updated. Try a reload
                timestamp = moduleSupport.load(target, session).getTimestamp();
            }
            return new DefaultShareLink(shareInfo, timestamp, true);
        } finally {
            connectionHelper.finish();
        }
    }

    /**
     * Evaluates an optionally set expiry date for the guest user prior returning it.
     *
     * @param guestInfo The guest to check the expiry date for
     * @return The passed guest, or <code>null</code> if the guest was expired
     */
    private DefaultGuestInfo removeExpired(DefaultGuestInfo guestInfo) throws OXException {
        Date expiryDate = guestInfo.getExpiryDate();
        if (null != expiryDate && expiryDate.before(new Date())) {
            LOG.info("Guest user {} in context {} expired, scheduling guest cleanup.", I(guestInfo.getGuestID()), I(guestInfo.getContextID()));
            ConnectionHelper connectionHelper = new ConnectionHelper(guestInfo.getContextID(), services, true);
            try {
                connectionHelper.start();
                removeTargetPermission(guestInfo.getGuestID(), guestInfo.getLinkTarget(), connectionHelper);
                connectionHelper.commit();
            } finally {
                connectionHelper.finish();
            }
            scheduleGuestCleanup(guestInfo.getContextID(), guestInfo.getGuestID());
            return null;
        }
        return guestInfo;
    }

    /**
     * Removes any references stored in the globalDB that has been created during user creation
     *
     * @param createdGuests The list of guest users that were created but not committed to the DB
     * @throws OXException
     */
    private void removeGuestReferences(List<GuestInfo> createdGuests) {
        /*
         * Transaction rolled back, no users have been created. Therefore
         * deleted references in globalDB guest tables
         */
        try {
            GuestService guestService = services.getService(GuestService.class);
            if (null == guestService) {
                LOG.error(GuestService.class.getSimpleName() + " is missing. Can't remove orphaned entries in globalDB for guest users: [{}]", createdGuests);
                return;
            }
            for (GuestInfo guestInfo : createdGuests) {
                guestService.removeGuest(guestInfo.getContextID(), guestInfo.getGuestID());
            }
        } catch (OXException e) {
            LOG.error("Error while removing orphaned entries in the globalDB for guest users [{}]", createdGuests, e);
        }
    }

}
