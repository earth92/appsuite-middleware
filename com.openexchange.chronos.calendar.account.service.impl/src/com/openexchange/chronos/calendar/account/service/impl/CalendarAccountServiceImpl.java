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

package com.openexchange.chronos.calendar.account.service.impl;

import static com.openexchange.chronos.service.CalendarParameters.PARAMETER_CONNECTION;
import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.osgi.Tools.requireService;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.capabilities.CapabilitySet;
import com.openexchange.chronos.exception.CalendarExceptionCodes;
import com.openexchange.chronos.provider.AutoProvisioningCalendarProvider;
import com.openexchange.chronos.provider.CalendarAccount;
import com.openexchange.chronos.provider.CalendarProvider;
import com.openexchange.chronos.provider.CalendarProviderRegistry;
import com.openexchange.chronos.provider.CalendarProviders;
import com.openexchange.chronos.provider.DefaultCalendarAccount;
import com.openexchange.chronos.provider.account.AdministrativeCalendarAccountService;
import com.openexchange.chronos.provider.account.CalendarAccountService;
import com.openexchange.chronos.provider.basic.BasicCalendarProvider;
import com.openexchange.chronos.provider.basic.CalendarSettings;
import com.openexchange.chronos.provider.folder.FolderCalendarProvider;
import com.openexchange.chronos.service.CalendarParameters;
import com.openexchange.chronos.storage.CalendarAccountStorage;
import com.openexchange.chronos.storage.CalendarStorage;
import com.openexchange.chronos.storage.CalendarStorageFactory;
import com.openexchange.chronos.storage.operation.CalendarStorageOperation;
import com.openexchange.chronos.storage.operation.OSGiCalendarStorageOperation;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViews;
import com.openexchange.context.ContextService;
import com.openexchange.crypto.CryptoService;
import com.openexchange.database.provider.DBTransactionPolicy;
import com.openexchange.database.provider.SimpleDBProvider;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.java.Strings;
import com.openexchange.secret.SecretEncryptionFactoryService;
import com.openexchange.secret.SecretEncryptionStrategy;
import com.openexchange.secret.recovery.EncryptedItemCleanUpService;
import com.openexchange.secret.recovery.EncryptedItemDetectorService;
import com.openexchange.secret.recovery.SecretMigrator;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSession;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

/**
 * {@link CalendarAccountServiceImpl}
 *
 * @author <a href="mailto:Jan-Oliver.Huhn@open-xchange.com">Jan-Oliver Huhn</a>
 * @since v7.10.0
 */
public class CalendarAccountServiceImpl implements CalendarAccountService, AdministrativeCalendarAccountService, SecretMigrator, EncryptedItemDetectorService, EncryptedItemCleanUpService {

    /** Simple comparator for calendar accounts to deliver accounts in a deterministic order */
    private static final Comparator<CalendarAccount> ACCOUNT_COMPARATOR = new Comparator<CalendarAccount>() {

        @Override
        public int compare(CalendarAccount account1, CalendarAccount account2) {
            if (null == account1) {
                return null == account2 ? 0 : 1;
            }
            if (null == account2) {
                return -1;
            }
            return Integer.compare(account1.getAccountId(), account2.getAccountId());
        }
    };

    private final ServiceLookup services;

    /**
     * Initializes a new {@link CalendarAccountServiceImpl}.
     *
     * @param serviceLookup A service lookup reference
     */
    public CalendarAccountServiceImpl(ServiceLookup serviceLookup) {
        super();
        this.services = serviceLookup;
    }

    @Override
    public List<CalendarProvider> getProviders() throws OXException {
        return getProviderRegistry().getCalendarProviders();
    }

    @Override
    public CalendarSettings probeAccountSettings(Session session, String providerId, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * get associated calendar provider, check permissions & perform the probe based on the supplied settings
         */
        CalendarProvider calendarProvider = requireCapability(getProvider(providerId), session);
        if (isGuest(session) || false == BasicCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(providerId);
        }
        return ((BasicCalendarProvider) calendarProvider).probe(session, settings, parameters);
    }

    @Override
    public CalendarAccount createAccount(Session session, String providerId, CalendarSettings settings, CalendarParameters parameters) throws OXException {
        /*
         * get associated calendar provider, check permissions & initialize account config
         */
        CalendarProvider calendarProvider = requireCapability(getProvider(providerId), session);
        if (isGuest(session) || false == BasicCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(providerId);
        }
        JSONObject internalConfig = ((BasicCalendarProvider) calendarProvider).configureAccount(session, settings, parameters);
        JSONObject userConfig = encryptSecretProperties(session, -1, settings.getConfig(), calendarProvider.getSecretProperties());
        /*
         * insert calendar account in storage within transaction
         */
        CalendarAccount account = insertAccount(session.getContextId(), calendarProvider, session.getUserId(), internalConfig, userConfig, parameters);
        /*
         * let provider perform any additional initialization
         */
        calendarProvider.onAccountCreated(session, account, parameters);
        return account;
    }

    @Override
    public CalendarAccount updateAccount(Session session, int id, CalendarSettings settings, long clientTimestamp, CalendarParameters parameters) throws OXException {
        /*
         * get & check stored calendar account
         */
        CalendarAccount storedAccount = getAccount(session, id, parameters);
        if (null != storedAccount.getLastModified() && storedAccount.getLastModified().getTime() > clientTimestamp) {
            throw CalendarExceptionCodes.CONCURRENT_MODIFICATION.create(String.valueOf(id), L(clientTimestamp), L(storedAccount.getLastModified().getTime()));
        }
        /*
         * get associated calendar provider & initialize account config
         */
        CalendarProvider calendarProvider = requireCapability(getProvider(storedAccount.getProviderId()), session);
        if (isGuest(session) || false == BasicCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(storedAccount.getProviderId());
        }
        JSONObject internalConfig = ((BasicCalendarProvider) calendarProvider).reconfigureAccount(session, storedAccount, settings, parameters);
        JSONObject userConfig = encryptSecretProperties(session, id, settings.getConfig(), calendarProvider.getSecretProperties());
        /*
         * update calendar account in storage within transaction
         */
        CalendarAccount account = updateAccount(session.getContextId(), session.getUserId(), id, internalConfig, userConfig, clientTimestamp, parameters);
        /*
         * let provider perform any additional initialization
         */
        calendarProvider.onAccountUpdated(session, account, parameters);
        return account;
    }

    @Override
    public CalendarAccount createAccount(Session session, String providerId, JSONObject userConfig, CalendarParameters parameters) throws OXException {
        /*
         * get associated calendar provider, check permissions & initialize account config
         */
        CalendarProvider calendarProvider = requireCapability(getProvider(providerId), session);
        if (isGuest(session) || false == FolderCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(calendarProvider.getId());
        }
        JSONObject internalConfig = ((FolderCalendarProvider) calendarProvider).configureAccount(session, userConfig, parameters);
        userConfig = encryptSecretProperties(session, -1, userConfig, calendarProvider.getSecretProperties());
        /*
         * insert calendar account in storage within transaction
         */
        CalendarAccount account = insertAccount(session.getContextId(), calendarProvider, session.getUserId(), internalConfig, userConfig, parameters);
        /*
         * let provider perform any additional initialization
         */
        calendarProvider.onAccountCreated(session, account, parameters);
        return account;
    }

    @Override
    public CalendarAccount updateAccount(Session session, int id, JSONObject userConfig, long clientTimestamp, CalendarParameters parameters) throws OXException {
        /*
         * get & check stored calendar account
         */
        CalendarAccount storedAccount = getAccount(session, id, parameters);
        if (null != storedAccount.getLastModified() && storedAccount.getLastModified().getTime() > clientTimestamp) {
            throw CalendarExceptionCodes.CONCURRENT_MODIFICATION.create(String.valueOf(id), L(clientTimestamp), L(storedAccount.getLastModified().getTime()));
        }
        /*
         * get associated calendar provider & initialize account config
         */
        CalendarProvider calendarProvider = requireCapability(getProvider(storedAccount.getProviderId()), session);
        if (isGuest(session) || false == FolderCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(storedAccount.getProviderId());
        }
        JSONObject internalConfig = ((FolderCalendarProvider) calendarProvider).reconfigureAccount(session, storedAccount, userConfig, parameters);
        userConfig = encryptSecretProperties(session, id, userConfig, calendarProvider.getSecretProperties());
        /*
         * update calendar account in storage within transaction
         */
        CalendarAccount account = updateAccount(session.getContextId(), session.getUserId(), id, internalConfig, userConfig, clientTimestamp, parameters);
        /*
         * let provider perform any additional initialization
         */
        calendarProvider.onAccountUpdated(session, account, parameters);
        return account;
    }

    @Override
    public void deleteAccount(Session session, int id, long clientTimestamp, CalendarParameters parameters) throws OXException {
        /*
         * get & check stored calendar account (directly from storage to circumvent access restrictions and still allow removal)
         */
        CalendarAccount storedAccount = initAccountStorage(session.getContextId(), parameters).loadAccount(session.getUserId(), id);
        if (null == storedAccount) {
            throw CalendarExceptionCodes.ACCOUNT_NOT_FOUND.create(I(id));
        }
        if (null != storedAccount.getLastModified() && storedAccount.getLastModified().getTime() > clientTimestamp) {
            throw CalendarExceptionCodes.CONCURRENT_MODIFICATION.create(String.valueOf(id), L(clientTimestamp), L(storedAccount.getLastModified().getTime()));
        }
        CalendarProvider calendarProvider = optProvider(storedAccount.getProviderId());
        if (null != calendarProvider && AutoProvisioningCalendarProvider.class.isInstance(calendarProvider)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(calendarProvider.getId());
        }
        if (isGuest(session)) {
            throw CalendarExceptionCodes.UNSUPPORTED_OPERATION_FOR_PROVIDER.create(storedAccount.getProviderId());
        }
        /*
         * delete calendar account in storage within transaction
         */
        try {
            new OSGiCalendarStorageOperation<Void>(services, session.getContextId(), -1, parameters) {

                @Override
                protected Void call(CalendarStorage storage) throws OXException {
                    CalendarAccount account = storage.getAccountStorage().loadAccount(session.getUserId(), id);
                    if (null == account) {
                        throw CalendarExceptionCodes.ACCOUNT_NOT_FOUND.create(I(id));
                    }
                    if (null != account.getLastModified() && account.getLastModified().getTime() > clientTimestamp) {
                        throw CalendarExceptionCodes.CONCURRENT_MODIFICATION.create(String.valueOf(id), L(clientTimestamp), L(account.getLastModified().getTime()));
                    }
                    storage.getAccountStorage().deleteAccount(session.getUserId(), id, clientTimestamp);
                    return null;
                }
            }.executeUpdate();
        } finally {
            invalidateStorage(session.getContextId(), session.getUserId(), id);
        }
        /*
         * finally let provider perform any additional initialization
         */
        if (null == calendarProvider) {
            LoggerFactory.getLogger(CalendarAccountServiceImpl.class).warn("Provider '{}' not available, skipping additional cleanup tasks for deleted account {}.",
                storedAccount.getProviderId(), storedAccount, CalendarExceptionCodes.PROVIDER_NOT_AVAILABLE.create(storedAccount.getProviderId()));
        } else {
            calendarProvider.onAccountDeleted(session, storedAccount, parameters);
        }
    }

    @Override
    public void deleteAccounts(int contextId, int userId, List<CalendarAccount> accounts) throws OXException {
        CalendarAccountStorage accountStorage = initAccountStorage(contextId, null);
        for (CalendarAccount acc : accounts) {
            try {
                accountStorage.deleteAccount(userId, acc.getAccountId(), Long.MAX_VALUE);
            } finally {
                invalidateStorage(contextId, userId, acc.getAccountId());
            }
        }
    }

    @Override
    public CalendarAccount getAccount(Session session, int id, CalendarParameters parameters) throws OXException {
        return getAccounts(session, new int[] { id }, parameters).get(0);
    }

    @Override
    public List<CalendarAccount> getAccounts(Session session, int[] ids, CalendarParameters parameters) throws OXException {
        CalendarAccount[] storedAccounts = initAccountStorage(session.getContextId(), parameters).loadAccounts(session.getUserId(), ids);
        for (int i = 0; i < storedAccounts.length; i++) {
            if (null == storedAccounts[i] && CalendarAccount.DEFAULT_ACCOUNT.getAccountId() == ids[i]) {
                if (isGuest(session)) {
                    /*
                     * return a virtual default calendar account for guest users
                     */
                    storedAccounts[i] = getVirtualDefaultAccount(session);
                } else {
                    /*
                     * get default account from list to implicitly trigger pending auto-provisioning tasks of the default account
                     */
                    storedAccounts[i] = find(getAccounts(session, parameters), CalendarAccount.DEFAULT_ACCOUNT.getProviderId());
                }
            }
            if (null == storedAccounts[i]) {
                throw CalendarExceptionCodes.ACCOUNT_NOT_FOUND.create(I(ids[i]));
            }
        }
        return decryptSecretProperties(session, Arrays.asList(storedAccounts));
    }

    @Override
    public List<CalendarAccount> getAccounts(Session session, CalendarParameters parameters) throws OXException {
        /*
         * get accounts from storage & check list for pending provisioning tasks
         */
        List<CalendarAccount> accounts = getAccounts(session.getContextId(), session.getUserId());
        if (false == getProvidersRequiringProvisioning(session, accounts).isEmpty() && false == isGuest(session)) {
            /*
             * assume there are pending auto-provisioning tasks, so re-check account list for pending auto-provisioning within transaction & auto-provision as needed
             */
            CalendarStorageOperation<List<CalendarAccount>> storageOperation = new OSGiCalendarStorageOperation<List<CalendarAccount>>(services, session.getContextId(), -1, parameters) {

                @Override
                protected List<CalendarAccount> call(CalendarStorage storage) throws OXException {
                    List<CalendarAccount> accounts = storage.getAccountStorage().loadAccounts(session.getUserId());
                    for (AutoProvisioningCalendarProvider calendarProvider : getProvidersRequiringProvisioning(session, accounts)) {
                        int maxAccounts = getMaxAccounts(calendarProvider, session.getContextId(), session.getUserId());
                        JSONObject userConfig = new JSONObject();
                        JSONObject internalConfig = calendarProvider.autoConfigureAccount(session, userConfig, parameters);
                        CalendarAccount account = insertAccount(storage.getAccountStorage(), calendarProvider.getId(), session.getUserId(), internalConfig, userConfig, maxAccounts);
                        if (account == null) {
                            LOG.warn("Failed to auto-provision account for user '{}' in context '{}'", I(session.getUserId()), I(session.getContextId()));
                            continue;
                        }
                        calendarProvider.onAccountCreated(session, account, parameters);
                        accounts.add(account);
                    }
                    return accounts;
                }
            };
            /*
             * ... trying again in case of a concurrently running auto-provisioning operation
             */
            try {
                accounts = Failsafe.with(new RetryPolicy()
                    .withMaxRetries(5).withBackoff(100, 1000, TimeUnit.MILLISECONDS).withJitter(0.25f)
                    .retryOn(f -> OXException.class.isInstance(f) && CalendarExceptionCodes.ACCOUNT_NOT_WRITTEN.equals((OXException) f)))
                    .onRetry(f -> LoggerFactory.getLogger(CalendarAccountServiceImpl.class).debug("New calendar account not stored, re-checking pending auto-provisioning tasks", f))
                .get(() -> storageOperation.executeUpdate());
            } catch (FailsafeException e) {
                if (OXException.class.isInstance(e.getCause())) {
                    throw (OXException) e.getCause();
                }
                throw e;
            } finally {
                /*
                 * ensure to (re-)invalidate caches outside of transaction afterwards
                 */
                invalidateStorage(session.getContextId(), session.getUserId());
            }
        }
        if (accounts.isEmpty() && isGuest(session)) {
            /*
             * include a virtual default calendar account for guest users
             */
            return Collections.singletonList(getVirtualDefaultAccount(session));
        }
        return decryptSecretProperties(session, sort(accounts));
    }

    @Override
    public List<CalendarAccount> getAccounts(Session session, String providerId, CalendarParameters parameters) throws OXException {
        return sort(findAll(getAccounts(session, parameters), providerId));
    }

    @Override
    public List<CalendarAccount> getAccounts(int contextId, int userId) throws OXException {
        return sort(initAccountStorage(contextId, null).loadAccounts(userId));
    }

    @Override
    public CalendarAccount getAccount(int contextId, int userId, int id) throws OXException {
        return initAccountStorage(contextId, null).loadAccount(userId, id);
    }

    @Override
    public List<CalendarAccount> getAccounts(int contextId, int[] userIds, String providerId) throws OXException {
        return sort(initAccountStorage(contextId, null).loadAccounts(userIds, providerId));
    }

    @Override
    public CalendarAccount getAccount(int contextId, int userId, String providerId) throws OXException {
        if (CalendarAccount.DEFAULT_ACCOUNT.getProviderId().equals(providerId)) {
            return initAccountStorage(contextId, null).loadAccount(userId, CalendarAccount.DEFAULT_ACCOUNT.getAccountId());
        }
        return initAccountStorage(contextId, null).loadAccount(userId, providerId);
    }
    @Override
    public List<CalendarAccount> getAccounts(int contextId, int userId, String providerId) throws OXException {
        return findAll(initAccountStorage(contextId, null).loadAccounts(userId), providerId);
    }

    @Override
    public CalendarAccount updateAccount(int contextId, int userId, int accountId, JSONObject internalConfig, JSONObject userConfig, long clientTimestamp) throws OXException {
        return updateAccount(contextId, userId, accountId, internalConfig, userConfig, clientTimestamp, null);
    }
    
    @Override
    public void migrate(String oldSecret, String newSecret, ServerSession session) throws OXException {
        for (CalendarAccount account : getAccountsWithSecretProperties(session.getContextId(), session.getUserId())) {
            if (migrateSecret(account, oldSecret, newSecret)) {
                updateAccount(session.getContextId(), session.getUserId(), account.getAccountId(), null, account.getUserConfiguration(), account.getLastModified().getTime());
            }
        }
    }

    @Override
    public boolean hasEncryptedItems(ServerSession session) throws OXException {
        return 0 < getAccountsWithSecretProperties(session.getContextId(), session.getUserId()).size();
    }

    @Override
    public void cleanUpEncryptedItems(String secret, ServerSession session) throws OXException {
        for (CalendarAccount account : getAccountsWithSecretProperties(session.getContextId(), session.getUserId())) {
            if (removeUnDecryptableValues(account, secret)) {
                updateAccount(session.getContextId(), session.getUserId(), account.getAccountId(), null, account.getUserConfiguration(), account.getLastModified().getTime());
            }
        }
    }

    @Override
    public void removeUnrecoverableItems(String secret, ServerSession session) throws OXException {
        for (CalendarAccount account : getAccountsWithSecretProperties(session.getContextId(), session.getUserId())) {
            if (removeUnDecryptableValues(account, secret)) {
                deleteAccounts(session.getContextId(), session.getUserId(), Collections.singletonList(account));
            }
        }
    }

    private boolean removeUnDecryptableValues(CalendarAccount account, String secret) throws OXException {
        boolean needsUpdate = false;
        for (String key : getProvider(account.getProviderId()).getSecretProperties()) {
            JSONObject userConfig = account.getUserConfiguration();
            if (null == userConfig || userConfig.isEmpty()) {
                continue;
            }
            String encryptedValue = userConfig.optString(key, null);
            if (Strings.isNotEmpty(encryptedValue)) {
                /*
                 * check if decryption works using the supplied secret, clear property, otherwise
                 */
                CryptoService cryptoService = requireService(CryptoService.class, services);
                try {
                    cryptoService.decrypt(encryptedValue, secret);
                } catch (OXException e) {
                    LoggerFactory.getLogger(CalendarAccountServiceImpl.class).trace("Decryption not possible in account {}, removing unencryptable value.", account, e);
                    userConfig.putSafe(key, "");
                    needsUpdate = true;
                }
            }
        }
        return needsUpdate;
    }

    private boolean migrateSecret(CalendarAccount account, String oldSecret, String newSecret) throws OXException {
        boolean needsUpdate = false;
        for (String key : getProvider(account.getProviderId()).getSecretProperties()) {
            JSONObject userConfig = account.getUserConfiguration();
            if (null == userConfig || userConfig.isEmpty()) {
                continue;
            }
            String encryptedValue = userConfig.optString(key, null);
            if (Strings.isNotEmpty(encryptedValue)) {
                /*
                 * check if decryption works using the new secret, migrate to new secret otherwise
                 */
                CryptoService cryptoService = requireService(CryptoService.class, services);
                try {
                    cryptoService.decrypt(encryptedValue, newSecret);
                } catch (OXException e) {
                    LoggerFactory.getLogger(CalendarAccountServiceImpl.class).trace("Decryption using new secret not successful in account {}, continuing with secret migration.", account, e);
                    userConfig.putSafe(key, cryptoService.encrypt(cryptoService.decrypt(encryptedValue, oldSecret), newSecret));
                    needsUpdate = true;
                }
            }
        }
        return needsUpdate;
    }
    
    private CalendarAccount updateAccount(int contextId, int userId, int accountId, JSONObject internalConfig, JSONObject userConfig, long clientTimestamp, CalendarParameters parameters) throws OXException {
        try {
            return new OSGiCalendarStorageOperation<CalendarAccount>(services, contextId, -1, parameters) {

                @Override
                protected CalendarAccount call(CalendarStorage storage) throws OXException {
                    CalendarAccount account = storage.getAccountStorage().loadAccount(userId, accountId);
                    if (null == account) {
                        throw CalendarExceptionCodes.ACCOUNT_NOT_FOUND.create(I(accountId));
                    }
                    if (null != account.getLastModified() && account.getLastModified().getTime() > clientTimestamp) {
                        throw CalendarExceptionCodes.CONCURRENT_MODIFICATION.create(String.valueOf(accountId), L(clientTimestamp), L(account.getLastModified().getTime()));
                    }
                    CalendarAccount accountUpdate = new DefaultCalendarAccount(account.getProviderId(), account.getAccountId(), account.getUserId(), internalConfig, userConfig, new Date());
                    storage.getAccountStorage().updateAccount(accountUpdate, clientTimestamp);
                    return storage.getAccountStorage().loadAccount(userId, accountId);
                }
            }.executeUpdate();
        } finally {
            invalidateStorage(contextId, userId, accountId);
        }
    }

    /**
     * Gets a list of auto-provisioning calendar providers where no calendar account is found in the supplied list of accounts, i.e. those
     * providers who where a provisioning task is required.
     *
     * @param session The current session
     * @param existingAccounts The accounts to check against the registered auto-provisioning calendar providers
     * @return The auto-provisioning calendar providers where no calendar account was found
     */
    List<AutoProvisioningCalendarProvider> getProvidersRequiringProvisioning(Session session, List<CalendarAccount> existingAccounts) throws OXException {
        CalendarProviderRegistry providerRegistry = getProviderRegistry();
        List<AutoProvisioningCalendarProvider> unprovisionedProviders = new ArrayList<AutoProvisioningCalendarProvider>();
        for (AutoProvisioningCalendarProvider calendarProvider : providerRegistry.getAutoProvisioningCalendarProviders()) {
            if (null == find(existingAccounts, calendarProvider.getId()) && hasCapability(calendarProvider, session)) {
                unprovisionedProviders.add(calendarProvider);
            }
        }
        return unprovisionedProviders;
    }

    /**
     * Prepares and stores a new calendar account.
     *
     * @param contextId The context identifier
     * @param calendarProvider The calendar provider
     * @param userId The user identifier
     * @param internalConfig The account's internal / protected configuration data
     * @param userConfig The account's external / user configuration data
     * @return The new calendar account
     */
    private CalendarAccount insertAccount(int contextId, CalendarProvider calendarProvider, int userId, JSONObject internalConfig, JSONObject userConfig, CalendarParameters parameters) throws OXException {
        try {
            return new OSGiCalendarStorageOperation<CalendarAccount>(services, contextId, -1, parameters) {

                @Override
                protected CalendarAccount call(CalendarStorage storage) throws OXException {
                    /*
                     * insert account after checking if maximum number of allowed accounts is reached for this provider
                     */
                    int maxAccounts = getMaxAccounts(calendarProvider, contextId, userId);
                    checkMaxAccountsNotReached(storage, calendarProvider, userId, maxAccounts);
                    return insertAccount(storage.getAccountStorage(), calendarProvider.getId(), userId, internalConfig, userConfig, maxAccounts);
                }
            }.executeUpdate();
        } finally {
            /*
             * (re-)invalidate caches outside of transaction
             */
            invalidateStorage(contextId, userId);
        }
    }

    /**
     * Prepares and stores a new calendar account.
     *
     * @param storage The calendar storage
     * @param providerId The provider identifier
     * @param userId The user identifier
     * @param internalConfig The account's internal / protected configuration data
     * @param userConfig The account's external / user configuration data
     * @param maxAccounts The maximum number of accounts allowed for this provider and user
     * @return The new calendar account
     */
    CalendarAccount insertAccount(CalendarAccountStorage storage, String providerId, int userId, JSONObject internalConfig, JSONObject userConfig, int maxAccounts) throws OXException {
        int accountId;
        if (CalendarAccount.DEFAULT_ACCOUNT.getProviderId().equals(providerId)) {
            accountId = CalendarAccount.DEFAULT_ACCOUNT.getAccountId();
        } else {
            accountId = storage.nextId();
        }
        storage.insertAccount(new DefaultCalendarAccount(providerId, accountId, userId, internalConfig, userConfig, new Date()), maxAccounts);
        return storage.loadAccount(userId, accountId);
    }

    /**
     * Optionally gets the calendar provider by its provider identifier.
     * 
     * @param providerId The identifier of the provider to get
     * @return The calendar provider, or <code>null</code> if unknown
     */
    CalendarProvider optProvider(String providerId) {
        CalendarProviderRegistry providerRegistry = services.getOptionalService(CalendarProviderRegistry.class);
        return null != providerRegistry ? providerRegistry.getCalendarProvider(providerId) : null;
    }

    private CalendarProvider getProvider(String providerId) throws OXException {
        CalendarProvider calendarProvider = getProviderRegistry().getCalendarProvider(providerId);
        if (null == calendarProvider) {
            throw CalendarExceptionCodes.PROVIDER_NOT_AVAILABLE.create(providerId);
        }
        return calendarProvider;
    }

    private CalendarProviderRegistry getProviderRegistry() throws OXException {
        CalendarProviderRegistry providerRegistry = services.getOptionalService(CalendarProviderRegistry.class);
        if (null == providerRegistry) {
            throw ServiceExceptionCode.absentService(CalendarProviderRegistry.class);
        }
        return providerRegistry;
    }

    /**
     * Initializes the calendar account storage for a specific context with default settings, i.e. no special transaction policy.
     *
     * @param contextId The context identifier
     * @param parameters The calendar parameters, or <code>null</code> if no available
     * @return The account storage
     */
    private CalendarAccountStorage initAccountStorage(int contextId, CalendarParameters parameters) throws OXException {
        CalendarStorageFactory storageFactory = requireService(CalendarStorageFactory.class, services);
        Context context = requireService(ContextService.class, services).getContext(contextId);
        Connection connection = null == parameters ? null : parameters.get(PARAMETER_CONNECTION(), Connection.class);
        if (null != connection) {
            SimpleDBProvider dbProvider = new SimpleDBProvider(connection, connection);
            return storageFactory.create(context, -1, null, dbProvider, DBTransactionPolicy.NO_TRANSACTIONS).getAccountStorage();
        }
        return storageFactory.create(context, -1, null).getAccountStorage();
    }

    private static CalendarAccount find(Collection<CalendarAccount> accounts, String providerId) {
        return accounts.stream().filter(account -> providerId.equals(account.getProviderId())).findFirst().orElse(null);
    }

    private static List<CalendarAccount> findAll(Collection<CalendarAccount> accounts, String providerId) {
        return accounts.stream().filter(account -> providerId.equals(account.getProviderId())).collect(Collectors.toList());
    }

    private static boolean isGuest(Session session) {
        return Boolean.TRUE.equals(session.getParameter(Session.PARAM_GUEST));
    }

    private static CalendarAccount getVirtualDefaultAccount(Session session) {
        return new DefaultCalendarAccount(CalendarAccount.DEFAULT_ACCOUNT.getProviderId(), CalendarAccount.DEFAULT_ACCOUNT.getAccountId(), session.getUserId(), new JSONObject(), new JSONObject(), new Date());
    }

    private void invalidateStorage(int contextId, int userId) throws OXException {
        invalidateStorage(contextId, userId, -1);
    }

    private void invalidateStorage(int contextId, int userId, int accountId) throws OXException {
        initAccountStorage(contextId, null).invalidateAccount(userId, accountId);
    }

    int getMaxAccounts(CalendarProvider provider, int contextId, int userId) throws OXException {
        int defaultValue = provider.getDefaultMaxAccounts();
        ConfigView view = requireService(ConfigViewFactory.class, services).getView(userId, contextId);
        return ConfigViews.getDefinedIntPropertyFrom(CalendarProviders.getMaxAccountsPropertyName(provider), defaultValue, view);
    }

    void checkMaxAccountsNotReached(CalendarStorage storage, CalendarProvider provider, int userId, int maxAccounts) throws OXException {
        if (0 < maxAccounts) {
            int numAccounts = storage.getAccountStorage().loadAccounts(new int[] { userId }, provider.getId()).size();
            if (maxAccounts <= numAccounts) {
                throw CalendarExceptionCodes.MAX_ACCOUNTS_EXCEEDED.create(provider.getId(), I(maxAccounts), I(numAccounts));
            }
        }
    }

    private CalendarProvider requireCapability(CalendarProvider provider, Session session) throws OXException {
        if (false == hasCapability(provider, session)) {
            throw CalendarExceptionCodes.MISSING_CAPABILITY.create(CalendarProviders.getCapabilityName(provider));
        }
        return provider;
    }

    private boolean hasCapability(CalendarProvider provider, Session session) throws OXException {
        String capabilityName = CalendarProviders.getCapabilityName(provider);
        CapabilitySet capabilities = requireService(CapabilityService.class, services).getCapabilities(session);
        return capabilities.contains(capabilityName);
    }
    
    private JSONObject encryptSecretProperties(Session session, int accountId, JSONObject userConfig, Set<String> secretProperties) throws OXException {
        if (null == userConfig || userConfig.isEmpty() || null == secretProperties || secretProperties.isEmpty()) {
            return userConfig;
        }
        for (String key : secretProperties) {
            String toEncrypt = userConfig.optString(key, null);
            if (Strings.isNotEmpty(toEncrypt)) {
                userConfig.putSafe(key, requireService(SecretEncryptionFactoryService.class, services)
                    .createService(getSecretEncryptionStrategy(session, accountId)).encrypt(session, toEncrypt));
            }
        }
        return userConfig;
    }

    private List<CalendarAccount> decryptSecretProperties(Session session, List<CalendarAccount> accounts) throws OXException {
        if (null == accounts || accounts.isEmpty()) {
            return accounts;
        }
        for (CalendarAccount account : accounts) {
            decryptSecretProperties(session, account);
        }
        return accounts;
    }

    private CalendarAccount decryptSecretProperties(Session session, CalendarAccount account) throws OXException {
        if (null == account || null == account.getUserConfiguration() || account.getUserConfiguration().isEmpty()) {
            return account;
        }
        CalendarProvider calendarProvider = optProvider(account.getProviderId());
        if (null == calendarProvider) {
            return account;
        }
        Set<String> secretProperties = calendarProvider.getSecretProperties();
        if (null == secretProperties || secretProperties.isEmpty()) {
            return account;
        }
        decryptSecretProperties(session, account.getAccountId(), account.getUserConfiguration(), secretProperties);
        return account;
    }

    private JSONObject decryptSecretProperties(Session session, int accountId, JSONObject userConfig, Set<String> secretProperties) throws OXException {
        if (null == userConfig || userConfig.isEmpty() || null == secretProperties || secretProperties.isEmpty()) {
            return userConfig;
        }
        for (String key : secretProperties) {
            String toDecrypt = userConfig.optString(key, null);
            if (Strings.isNotEmpty(toDecrypt)) {
                userConfig.putSafe(key, requireService(SecretEncryptionFactoryService.class, services)
                    .createService(getSecretEncryptionStrategy(session, accountId)).decrypt(session, toDecrypt, key));
            }
        }
        return userConfig;
    }
    
    private SecretEncryptionStrategy<String> getSecretEncryptionStrategy(Session session, int accountId) {
        AdministrativeCalendarAccountService accountService = this;
        return new SecretEncryptionStrategy<String>() {

            @Override
            public void update(String recrypted, String key) throws OXException {
                CalendarAccount account = accountService.getAccount(session.getContextId(), session.getUserId(), accountId);
                if (account != null) {
                    JSONObject userConfig = account.getUserConfiguration();
                    if (null != userConfig) {
                        userConfig.putSafe(key, recrypted);
                    }
                    accountService.updateAccount(session.getContextId(), session.getUserId(), accountId, null, userConfig, account.getLastModified().getTime());
                }
            }
        };
    }

    private List<CalendarAccount> getAccountsWithSecretProperties(int contextId, int userId) throws OXException {
        List<CalendarAccount> accountsWithSecretProperties = new ArrayList<CalendarAccount>();
        for (CalendarAccount account : getAccounts(contextId, userId)) {
            JSONObject userConfig = account.getUserConfiguration();
            if (null == userConfig || userConfig.isEmpty()) {
                continue;
            }
            CalendarProvider provider = optProvider(account.getProviderId());
            if (null == provider || null == provider.getSecretProperties() || provider.getSecretProperties().isEmpty()) {
                continue;
            }
            for (String key : provider.getSecretProperties()) {
                if (Strings.isNotEmpty(userConfig.optString(key, null))) {
                    accountsWithSecretProperties.add(account);
                    continue;
                }
            }
        }
        return accountsWithSecretProperties;
    }

    private static List<CalendarAccount> sort(List<CalendarAccount> accounts) {
        if (null != accounts && 1 < accounts.size()) {
            accounts.sort(ACCOUNT_COMPARATOR);
        }
        return accounts;
    }

}
