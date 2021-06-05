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

package com.openexchange.subscribe.internal;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.subscribe.SubscriptionErrorMessage.INACTIVE_SOURCE;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.generic.FolderUpdaterRegistry;
import com.openexchange.groupware.generic.FolderUpdaterService;
import com.openexchange.groupware.generic.FolderUpdaterServiceV2;
import com.openexchange.groupware.generic.TargetFolderDefinition;
import com.openexchange.log.LogProperties;
import com.openexchange.session.Session;
import com.openexchange.subscribe.SubscribeService;
import com.openexchange.subscribe.Subscription;
import com.openexchange.subscribe.SubscriptionErrorMessage;
import com.openexchange.subscribe.SubscriptionExecutionService;
import com.openexchange.subscribe.SubscriptionSource;
import com.openexchange.subscribe.SubscriptionSourceDiscoveryService;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIteratorDelegator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.oxfolder.OXFolderAccess;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link SubscriptionExecutionServiceImpl}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class SubscriptionExecutionServiceImpl implements SubscriptionExecutionService, FolderUpdaterRegistry {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SubscriptionExecutionServiceImpl.class);

    /*-
     * -------------------------------- Guard stuff -------------------------------
     */

    private static final ConcurrentMap<SubscriptionKey, Object> GUARD_MAP = new ConcurrentHashMap<>(1024, 0.9f, 1);

    private static final Object PRESENT = new Object();

    /**
     * Acquires the lock only if it is free at the time of invocation.
     * <p>
     * Acquires the lock if it is available and returns immediately with the value {@code true}. If the lock is not available then this
     * method will return immediately with the value {@code false}.
     *
     * @param subscriptionId The subscription identifier
     * @param session The associated session
     * @return {@code true} if the lock was acquired and {@code false} otherwise
     */
    private static boolean tryLock(final int subscriptionId, final Session session) {
        return (null == GUARD_MAP.putIfAbsent(key(subscriptionId, session), PRESENT));
    }

    /**
     * Releases the lock.
     *
     * @param subscriptionId The subscription identifier
     * @param session The associated session
     */
    private static void unlock(final int subscriptionId, final Session session) {
        GUARD_MAP.remove(key(subscriptionId, session));
    }

    /*-
     * -------------------------------- Member stuff -------------------------------
     */

    private final SubscriptionSourceDiscoveryService discoverer;
    private final Collection<FolderUpdaterService<?>> folderUpdaters;
    private final ContextService contextService;

    public SubscriptionExecutionServiceImpl(final SubscriptionSourceDiscoveryService discoverer, final Collection<FolderUpdaterService<?>> folderUpdaters, final ContextService contexts) {
        this.discoverer = discoverer;
        this.folderUpdaters = folderUpdaters;
        this.contextService = contexts;
    }

    @Override
    public int executeSubscription(final String sourceId, final ServerSession session, final int subscriptionId) throws OXException {
        if (false == tryLock(subscriptionId, session)) {
            // Concurrent subscribe attempt
            return 0;
        }
        LogProperties.put(LogProperties.Name.SUBSCRIPTION_ADMIN, "true");
        try {
            final SubscribeService subscribeService = discoverer.getSource(sourceId).getSubscribeService();
            final Subscription subscription = subscribeService.loadSubscription(session.getContext(), subscriptionId, null);
            if (subscription == null) {
                throw SubscriptionErrorMessage.SubscriptionNotFound.create(I(session.getContextId()), I(subscriptionId));
            }
            subscription.setSession(session);
            final boolean knowsSource = discoverer.filter(subscription.getUserId(), session.getContextId()).knowsSource(subscribeService.getSubscriptionSource().getId());
            if (!knowsSource) {
                throw INACTIVE_SOURCE.create();
            }
            subscribeService.touch(session.getContext(), subscriptionId);
            final SearchIterator<?> data = subscribeService.loadContent(subscription);
            storeData(data, subscription, null);
            return data.size();
        } finally {
            LogProperties.remove(LogProperties.Name.SUBSCRIPTION_ADMIN);
            unlock(subscriptionId, session);
        }
    }

    /**
     * Stores the data
     *
     * @param data contents of this subscription
     * @param subscription The {@link Subscription}
     * @param session The {@link ServerSession}
     * @throws OXException In case data can't be saved
     */
    protected <T> void storeData(final SearchIterator<T> data, final Subscription subscription, Collection<OXException> optErrors) throws OXException {
        @SuppressWarnings("unchecked") FolderUpdaterService<T> folderUpdater = getFolderUpdater(subscription);
        if (folderUpdater instanceof FolderUpdaterServiceV2) {
            ((FolderUpdaterServiceV2<T>) folderUpdater).save(data, subscription, optErrors);
        } else {
            if (folderUpdater == null) {
                throw SubscriptionErrorMessage.UNEXPECTED_ERROR.create("Missing FolderUpdateService.");
            }
            folderUpdater.save(data, subscription);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public FolderUpdaterService getFolderUpdater(final TargetFolderDefinition target) throws OXException {
        final FolderObject folder = getFolder(target.getContext().getContextId(), target.getFolderIdAsInt());

        //
        final boolean moreThanOneSubscriptionOnThisFolder = isThereMoreThanOneSubscriptionOnThisFolder(target.getContext(), target.getFolderId(), null);
        for (final FolderUpdaterService updater : folderUpdaters) {
            if (updater.handles(folder)) {
                // if there are 2 or more subscriptions on the folder: use the multiple-variant of the strategy if it exists
                if (moreThanOneSubscriptionOnThisFolder && updater.usesMultipleStrategy()) {
                    return updater;
                }
                if (!moreThanOneSubscriptionOnThisFolder && !updater.usesMultipleStrategy()) {
                    return updater;
                }

            }
        }

        // if there are 2 or more subscriptions on a folder but no multiple-variant Strategy is available use a single one
        for (final FolderUpdaterService<?> updater : folderUpdaters) {
            if (updater.handles(folder)) {
                return updater;
            }
        }
        return null;
    }

    /**
     * Gets the folder
     *
     * @param contextId The context id
     * @param folderId The folder id
     * @return The {@link FolderObject}
     * @throws OXException
     */
    protected FolderObject getFolder(final int contextId, final int folderId) throws OXException {
        final OXFolderAccess ofa = new OXFolderAccess(contextService.getContext(contextId));
        return ofa.getFolderObject(folderId);
    }

    @Override
    public int executeSubscription(final ServerSession session, final int subscriptionId) throws OXException {
        if (false == tryLock(subscriptionId, session)) {
            // Concurrent subscribe attempt
            return 0;
        }
        LogProperties.put(LogProperties.Name.SUBSCRIPTION_ADMIN, "true");
        try {
            final Context context = session.getContext();
            final SubscriptionSource source = discoverer.getSource(context, subscriptionId);
            if (source == null) {
                throw INACTIVE_SOURCE.create();
            }
            final SubscribeService subscribeService = source.getSubscribeService();
            final Subscription subscription = subscribeService.loadSubscription(context, subscriptionId, null);
            if (subscription == null) {
                throw SubscriptionErrorMessage.SubscriptionNotFound.create();
            }
            subscription.setSession(session);
            final boolean knowsSource = discoverer.filter(subscription.getUserId(), context.getContextId()).knowsSource(subscribeService.getSubscriptionSource().getId());
            if (!knowsSource) {
                throw INACTIVE_SOURCE.create();
            }
            subscribeService.touch(session.getContext(), subscriptionId);
            final SearchIterator<?> data = subscribeService.loadContent(subscription);
            storeData(data, subscription, null);
            return data.size();
        } finally {
            LogProperties.remove(LogProperties.Name.SUBSCRIPTION_ADMIN);
            unlock(subscriptionId, session);
        }
    }

    @Override
    public int executeSubscriptions(List<Subscription> subscriptionsToRefresh, ServerSession session, Collection<OXException> optErrors) throws OXException {
        int sum = 0;
        for (Subscription subscription : subscriptionsToRefresh) {
            if (!subscription.isEnabled()) {
                LOG.debug("Skipping subscription {} because it is disabled", subscription.getDisplayName());
                continue;
            }
            final int subscriptionId = subscription.getId();
            if (false == tryLock(subscriptionId, session)) {
                continue;
            }
            subscription.setSession(session);
            LogProperties.put(LogProperties.Name.SUBSCRIPTION_ADMIN, "true");
            try {
                // Get subscription source
                SubscriptionSource source = subscription.getSource();
                if (source == null) {
                    throw INACTIVE_SOURCE.create();
                }

                // Get associated subscription service
                SubscribeService subscribeService = source.getSubscribeService();
                subscribeService.touch(session.getContext(), subscriptionId);

                // Fetch data elements & store them batch-wise
                SearchIterator<?> data = subscribeService.loadContent(subscription);
                try {
                    storeData(data, subscription, optErrors);
                    sum += data.size();
                } catch (OXException e) {
                    // Failed batch storing - fall back to one-by-one if optional "error collecting" is enabled
                    if (null == optErrors) {
                        throw e;
                    }

                    // Re-fetch data elements & close remaining resources
                    SearchIterator<?> newData;
                    if (data instanceof SearchIteratorDelegator) {
                        newData = SearchIteratorDelegator.class.cast(data).newSearchIterator();
                    } else {
                        newData = subscribeService.loadContent(subscription);
                    }
                    SearchIterators.close(data);
                    data = null;

                    // Store them one-by-one
                    sum = 0;
                    while (newData.hasNext()) {
                        Object element = newData.next();

                        try {
                            data = new SingleSearchIterator<>(element);
                            storeData(data, subscription, null);
                            sum++;
                        } catch (OXException x) {
                            optErrors.add(x);
                        }
                    }
                } finally {
                    SearchIterators.close(data);
                }
            } catch (OXException oxException) {
                if (subscriptionsToRefresh.size() == 1) {
                    throw oxException;
                }
                LOG.warn("Subscription for {} cannot be updated. Move to next one.", subscription.getSource().getId(), oxException);
            } finally {
                LogProperties.remove(LogProperties.Name.SUBSCRIPTION_ADMIN);
                unlock(subscriptionId, session);
            }
        }
        return sum;
    }

    private boolean isThereMoreThanOneSubscriptionOnThisFolder(final Context context, final String folder, final String secret) throws OXException {
        final List<SubscriptionSource> sources = discoverer.getSources();
        final List<Subscription> allSubscriptionsOnThisFolder = new ArrayList<>(10);
        for (final SubscriptionSource subscriptionSource : sources) {
            final Collection<Subscription> subscriptions = subscriptionSource.getSubscribeService().loadSubscriptions(context, folder, secret);
            if (subscriptions != null) {
                allSubscriptionsOnThisFolder.addAll(subscriptions);
            }
        }
        return allSubscriptionsOnThisFolder.size() >= 2;
    }

    /*-
     * -------------------------- Helper class --------------------------
     */

    private static SubscriptionKey key(final int subscriptionId, final Session session) {
        return new SubscriptionKey(subscriptionId, session.getUserId(), session.getContextId());
    }

    private static final class SubscriptionKey {

        private final int subscriptionId;
        private final int userId;
        private final int contextId;
        private final int hash;

        SubscriptionKey(final int subscriptionId, final int userId, final int contextId) {
            super();
            this.subscriptionId = subscriptionId;
            this.userId = userId;
            this.contextId = contextId;
            final int prime = 31;
            int result = 1;
            result = prime * result + contextId;
            result = prime * result + subscriptionId;
            result = prime * result + userId;
            hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SubscriptionKey)) {
                return false;
            }
            final SubscriptionKey other = (SubscriptionKey) obj;
            if (contextId != other.contextId) {
                return false;
            }
            if (subscriptionId != other.subscriptionId) {
                return false;
            }
            if (userId != other.userId) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return new StringBuilder(32).append('{').append("subscriptionId=").append(subscriptionId).append(", userId=").append(userId).append(", contextId=").append(contextId).append('}').toString();
        }

    } // End of class SubscriptionKey

    private static class SingleSearchIterator<E> implements SearchIterator<E> {

        private E value;

        SingleSearchIterator(E value) {
            super();
            this.value = value;
        }

        @Override
        public boolean hasNext() {
            return null != value;
        }

        @Override
        public E next() {
            if (null == value) {
                throw new NoSuchElementException();
            }
            E retval = value;
            value = null;
            return retval;
        }

        @Override
        public void close() {
            // Nothing to do
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean hasWarnings() {
            return false;
        }

        @Override
        public void addWarning(OXException warning) {
            // Nothing to do
        }

        @Override
        public OXException[] getWarnings() {
            return null;
        }

    } // End of class SingleSearchIterator

}
