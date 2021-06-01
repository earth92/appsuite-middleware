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

package com.openexchange.file.storage.xctx;

import static com.openexchange.file.storage.infostore.folder.AbstractInfostoreFolderAccess.PUBLIC_INFOSTORE_FOLDER_ID;
import static com.openexchange.file.storage.infostore.folder.AbstractInfostoreFolderAccess.USER_INFOSTORE_FOLDER_ID;
import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.b;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.capabilities.CapabilitySet;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.config.lean.Property;
import com.openexchange.conversion.ConversionService;
import com.openexchange.conversion.DataHandler;
import com.openexchange.conversion.datahandler.DataHandlers;
import com.openexchange.dispatcher.DispatcherPrefixService;
import com.openexchange.exception.OXException;
import com.openexchange.file.storage.CapabilityAware;
import com.openexchange.file.storage.ErrorStateFileAccess;
import com.openexchange.file.storage.ErrorStateFolderAccess;
import com.openexchange.file.storage.FileStorageAccount;
import com.openexchange.file.storage.FileStorageAccountAccess;
import com.openexchange.file.storage.FileStorageAccountErrorHandler;
import com.openexchange.file.storage.FileStorageAccountErrorHandler.Result;
import com.openexchange.file.storage.FileStorageCapability;
import com.openexchange.file.storage.FileStorageCapabilityTools;
import com.openexchange.file.storage.FileStorageExceptionCodes;
import com.openexchange.file.storage.FileStorageFileAccess;
import com.openexchange.file.storage.FileStorageFolder;
import com.openexchange.file.storage.FileStorageFolderAccess;
import com.openexchange.file.storage.FileStorageResult;
import com.openexchange.file.storage.FileStorageService;
import com.openexchange.file.storage.SetterAwareFileStorageFolder;
import com.openexchange.groupware.notify.hostname.HostData;
import com.openexchange.java.Strings;
import com.openexchange.osgi.ShutDownRuntimeException;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.share.ShareExceptionCodes;
import com.openexchange.share.core.subscription.AccountMetadataHelper;
import com.openexchange.share.core.subscription.SubscribedHelper;
import com.openexchange.share.core.tools.ShareLinks;
import com.openexchange.share.core.tools.ShareToken;
import com.openexchange.share.subscription.ShareSubscriptionExceptions;
import com.openexchange.share.subscription.XctxHostData;
import com.openexchange.share.subscription.XctxSessionManager;
import com.openexchange.tools.arrays.Collections;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.tools.session.ServerSessionAdapter;

/**
 * {@link XctxAccountAccess}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since 7.10.5
 */
public class XctxAccountAccess implements FileStorageAccountAccess, CapabilityAware {

    /** The identifiers of the parent folders where adjusting the subscribed flag is supported, which mark the entry points for shared and public files */
    static final Set<String> XCTX_PARENT_FOLDER_IDS = Collections.unmodifiableSet(USER_INFOSTORE_FOLDER_ID, PUBLIC_INFOSTORE_FOLDER_ID);

    private static final Logger LOG = LoggerFactory.getLogger(XctxFileStorageService.class);

    private final ServiceLookup services;
    private final FileStorageAccountErrorHandler errorHandler;

    private ServerSession guestSession;
    private boolean isConnected;

    protected final FileStorageAccount account;
    protected final ServerSession session;


    /**
     * Initializes a new {@link XctxAccountAccess}.
     *
     * @param services A service lookup reference
     * @param account The account
     * @param session The user's session
     * @param retryAfterError The amount of seconds after which accessing an error afflicted account should be retried.
     */
    protected XctxAccountAccess(ServiceLookup services, FileStorageAccount account, Session session, int retryAfterError) throws OXException {
        super();
        this.services = services;
        this.account = account;
        this.session = ServerSessionAdapter.valueOf(session);

        ConversionService conversionService = getServiceSafe(ConversionService.class);
        DataHandler ox2jsonDataHandler = conversionService.getDataHandler(DataHandlers.OXEXCEPTION2JSON);
        DataHandler json2oxDataHandler = conversionService.getDataHandler(DataHandlers.JSON2OXEXCEPTION);
        //@formatter:off
        this.errorHandler = new FileStorageAccountErrorHandler(ox2jsonDataHandler,
            json2oxDataHandler,
            this,
            session,
            retryAfterError,
            new FileStorageAccountErrorHandler.CompositingFilter()
                .add(new FileStorageAccountErrorHandler.IgnoreExceptionPrefixes("SES"))
                .add((e) -> {
                    if (ShareExceptionCodes.UNKNOWN_SHARE.equals(e) && isAutoRemoveUnknownShares(session)) {
                        LOG.info("Guest account for cross-context share subscription no longer exists, removing file storage account {}.", account.getId(), e);
                        try {
                            account.getFileStorageService().getAccountManager().deleteAccount(account, session);
                            return Boolean.FALSE; // handled by removing the account, abort upstream processing
                        } catch (OXException x) {
                            LOG.error("Unexpected error removing file storage account {}.", account.getId(), x);
                        }
                    }
                    return Boolean.TRUE; // not handled, continue with upstream processing (persist error in account)
                })
            );
        //@formatter:on
    }

    /**
     * Gets the underlying filestorage account.
     *
     * @return The file storage account
     */
    public FileStorageAccount getAccount() {
        return account;
    }

    /**
     * Gets a {@link SubscribedHelper} suitable for the connected file storage account.
     *
     * @return The subscribed helper
     */
    public SubscribedHelper getSubscribedHelper() {
        return new SubscribedHelper(account, XCTX_PARENT_FOLDER_IDS);
    }

    /**
     * Gets the service of specified type. Throws error if service is absent.
     *
     * @param <S> The class type
     * @param clazz The service's class
     * @return The service instance
     * @throws ShutDownRuntimeException If system is currently shutting down
     * @throws OXException In case of missing service
     */
    public <S extends Object> S getServiceSafe(Class<? extends S> clazz) throws OXException {
        return services.getServiceSafe(clazz);
    }

    /**
     * Gets a {@link HostData} implementation under the perspective of the guest user.
     *
     * @return The host data for the guest
     * @throws OXException In case URL is missing or invalid
     */
    public HostData getGuestHostData() throws OXException {
        String shareUrl = (String) account.getConfiguration().get("url");
        if (Strings.isEmpty(shareUrl)) {
            throw FileStorageExceptionCodes.MISSING_CONFIG.create("url", account.getId());
        }
        URI uri;
        try {
            uri = new URI(shareUrl);
        } catch (URISyntaxException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e.getMessage(), e);
        }
        return new XctxHostData(uri, guestSession) {

            @Override
            protected DispatcherPrefixService getDispatcherPrefixService() throws OXException {
                return getServiceSafe(DispatcherPrefixService.class);
            }
        };
    }

    /**
     * Gets a {@link JSONObject} providing additional arbitrary metadata of the account for clients.
     *
     * @return The metadata
     */
    public JSONObject getMetadata() throws OXException {
        try {
            JSONObject metadata = new JSONObject();
            /*
             * add identifiers of guest user/context based on share token
             */
            ShareToken shareToken = new ShareToken(getBaseToken(account));
            metadata.put("guestContextId", shareToken.getContextID());
            metadata.put("guestUserId", shareToken.getUserID());
            metadata.put("guestUserIdentifier", new EntityHelper(this).mangleRemoteEntity(shareToken.getUserID()));
            /*
             * add capabilities of guest user
             */
            CapabilityService capabilityService = getServiceSafe(CapabilityService.class);
            CapabilitySet capabilities = capabilityService.getCapabilities(shareToken.getUserID(), shareToken.getContextID());
            metadata.put("guestCapabilities", capabilities.asSet());
            return metadata;
        } catch (JSONException e) {
            throw FileStorageExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Resets the last known, recent, error for this account
     *
     * @throws OXException
     */
    public void resetRecentError() throws OXException {
        errorHandler.removeRecentException();
    }

    @Override
    public Boolean supports(FileStorageCapability capability) {
        if (FileStorageCapability.RESTORE.equals(capability)) {
            return Boolean.FALSE;
        }
        if (capability.isFileAccessCapability()) {
            return FileStorageCapabilityTools.supportsByClass(XctxFileAccess.class, capability);
        }
        return FileStorageCapabilityTools.supportsFolderCapabilityByClass(XctxFolderAccess.class, capability);
    }

    @Override
    public void connect() throws OXException {
        String baseToken = getBaseToken(account);
        String password = (String) account.getConfiguration().get("password");

        boolean hasKnownError = errorHandler.hasRecentException();
        if (hasKnownError) {
            //We do not really connect because we have known errors,
            //but dummy folders should still be returned
            isConnected = true;
            return;
        }

        try {
            this.guestSession = ServerSessionAdapter.valueOf(services.getServiceSafe(XctxSessionManager.class).getGuestSession(session, baseToken, password));
            isConnected = true;
        } catch (OXException e) {
            Result handlingResult = errorHandler.handleException(e);
            isConnected = handlingResult.isHandled();
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void close() {
        this.guestSession = null; // guest session still kept in cache
        isConnected = false;
    }

    @Override
    public boolean ping() throws OXException {
        return true;
    }

    @Override
    public boolean cacheable() {
        return true;
    }

    @Override
    public String getAccountId() {
        return account.getId();
    }

    @Override
    public FileStorageFileAccess getFileAccess() throws OXException {
        if (false == isConnected()) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        OXException recentException = this.errorHandler.getRecentException();
        if (recentException != null) {
            //In case of an error state: we return an implementation which at least return empty objects on read access
            return new ErrorStateFileAccess(recentException, this);
        }
        return new XctxFileAccess(this, session, guestSession);
    }

    @Override
    public FileStorageFolderAccess getFolderAccess() throws OXException {
        if (false == isConnected()) {
            throw FileStorageExceptionCodes.NOT_CONNECTED.create();
        }
        OXException recentException = this.errorHandler.getRecentException();

        //In case of an error state: we return an implementation which will only allow to get the last known folders
        if (recentException != null) {

            return new ErrorStateFolderAccess(account, recentException) {

                @Override
                public FileStorageFolderStub[] getLastKnownSubFolders(String folderId) throws OXException {
                    return new AccountMetadataHelper(account, session).getLastKnownFolders(folderId);
                }

                @Override
                public FileStorageFolderStub getLastKnownFolder(String folderId) throws OXException {
                    return new AccountMetadataHelper(account, session).getLastKnownFolder(folderId);
                }

                @Override
                public FileStorageResult<String> updateLastKnownFolder(FileStorageFolder folder, boolean ignoreWarnings, FileStorageFolder toUpdate) throws OXException {
                    //Only able update the subscription flag in case of an error
                    if (SetterAwareFileStorageFolder.class.isInstance(toUpdate) && ((SetterAwareFileStorageFolder) toUpdate).containsSubscribed()) {

                        //Check for un-subscription and check if the last folder is going to be unscubscribed
                        if (false == toUpdate.isSubscribed()) {
                            List<FileStorageFolder> subscribedFolders = getVisibleRootFolders();
                            Optional<FileStorageFolder> folderToUnsubscibe = subscribedFolders.stream().filter(f -> f.getId().equals(folder.getId())).findFirst();
                            if (folderToUnsubscibe.isPresent()) {
                                subscribedFolders.removeIf(f -> f == folderToUnsubscibe.get());
                                if (subscribedFolders.isEmpty()) {
                                    //The last folder is going to be unsubscribed
                                    if (ignoreWarnings) {
                                        //Delete
                                        getService().getAccountManager().deleteAccount(getAccount(), session);
                                    } else {
                                        //Throw a warning
                                        String folderName = folderToUnsubscibe.get().getName();
                                        String accountName = getAccount().getDisplayName();
                                        return FileStorageResult.newFileStorageResult(null, Arrays.asList(ShareSubscriptionExceptions.ACCOUNT_WILL_BE_REMOVED.create(folderName, accountName)));
                                    }
                                    return FileStorageResult.newFileStorageResult(null, null);
                                }
                            }
                        }

                        //Do the update
                        getSubscribedHelper().setSubscribed(session, folder, B(toUpdate.isSubscribed()));
                        return FileStorageResult.newFileStorageResult(folder.getId(), null);
                    }
                    return FileStorageResult.newFileStorageResult(null, null);
                }
            };
        }

        return new XctxFolderAccess(this, session, guestSession);
    }

    @Override
    public FileStorageFolder getRootFolder() throws OXException {
        throw FileStorageExceptionCodes.NO_SUCH_FOLDER.create();
    }

    @Override
    public FileStorageService getService() {
        return account.getFileStorageService();
    }

    /**
     * Gets a value indicating whether the automatic removal of accounts in the <i>cross-context</i> file storage provider that refer to a no
     * longer existing guest user in the remote context is enabled or not.
     *
     * @param session The session to check the configuration for
     * @return <code>true</code> if unknown shares should be removed automatically, <code>false</code>, otherwise
     */
    private boolean isAutoRemoveUnknownShares(Session session) {
        Property property = XctxFileStorageProperties.AUTO_REMOVE_UNKNOWN_SHARES;
        try {
            return services.getServiceSafe(LeanConfigurationService.class).getBooleanProperty(session.getUserId(), session.getContextId(), property);
        } catch (OXException e) {
            LOG.error("Error getting {}, falling back to defaults.", property, e);
            return b(property.getDefaultValue(Boolean.class));
        }
    }

    private static String getBaseToken(FileStorageAccount account) throws OXException {
        String shareUrl = (String) account.getConfiguration().get("url");
        if (Strings.isEmpty(shareUrl)) {
            throw FileStorageExceptionCodes.MISSING_CONFIG.create("url", account.getId());
        }
        String baseToken = ShareLinks.extractBaseToken(shareUrl);
        if (null == baseToken) {
            throw ShareExceptionCodes.INVALID_LINK.create(shareUrl);
        }
        return baseToken;
    }

}
