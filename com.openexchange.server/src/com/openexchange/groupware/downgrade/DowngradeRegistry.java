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

package com.openexchange.groupware.downgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.infostore.InfostoreDowngrade;
import com.openexchange.groupware.tasks.TasksDowngrade;
import com.openexchange.tools.oxfolder.downgrade.OXFolderDowngradeListener;

/**
 * {@link DowngradeRegistry} - A registry for instances of
 * {@link DowngradeListener} whose
 * {@link DowngradeListener#downgradePerformed(DowngradeEvent)} methods are
 * executed.
 * <p>
 * The added listeners should complete the following tasks:
 * <ul>
 * <li><b>Calendar/Task</b><br>
 * If user lost access to calendar/task module the following actions have to be
 * performed.
 * <ul>
 * <li>Delete all module's private items</li>
 * <li>Remove affected user from module's private items' participants. If no
 * participant remains, remove the item</li>
 * <li>Delete all attachments held by the items deleted through previous steps</li>
 * <li>Delete all links to/from the items deleted through previous steps</li>
 * <li>Delete all reminder bound to the items deleted through previous steps</li>
 * <li>Delete all module's private folders except the user's private default
 * folder</li>
 * <li>Delete all share permissions held by the user's private default folder</li>
 * <li>Delete all permissions held by module's public folders which grant
 * access for affected user. If necessary reassign to context's admin rather
 * than delete the permission in question</li>
 * </ul>
 * </li>
 *
 * <li><b>Infostore</b><br>
 * If user lost access to infostore module the following actions have to be
 * performed.
 * <ul>
 * <li>Delete all items and folders located below user's default infostore
 * folder</li>
 * <li>Delete all share permissions held by the user's default infostore folder</li>
 * <li>Delete all permissions held by other infostore folders which grant
 * access for affected user. If necessary reassign to context's admin rather
 * than delete the permission in question</li>
 * <li>Delete all links to remote infostore items</li>
 * <li>Delete all WebDAV properties and locks held by remote infostore folders</li>
 * </ul>
 * </li>
 *
 * <li><b>Full public folder access</b><br>
 * If user lost full public folder access he is no more able to see affected
 * public data, thus nothing has to be deleted here.</li>
 * <ul>
 * </ul>
 *
 * <li><b>Full shared folder access</b><br>
 * If user lost full shared folder access neither sharing nor viewing shared
 * folders is possible
 * <ul>
 * <li>Delete all share permissions held by user's private folders</li>
 * <li>Delete all share permissions granted to affected by other private
 * folders</li>
 * </ul>
 * </li>
 *
 * <li><b>Delegating tasks</b><br>
 * If user lost capability to delegate tasks all delegations of user's tasks
 * have to be removed.
 * <ul>
 * <li>Delete additional participants from task items created by user</li>
 * </ul>
 * </li>
 *
 * </ul>
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 *
 */
public final class DowngradeRegistry {

	private static final Comparator<DowngradeListener> COMPARATOR = new Comparator<DowngradeListener>() {
		@Override
        public int compare(final DowngradeListener o1, final DowngradeListener o2) {
			if (o1.getOrder() > o2.getOrder()) {
				return 1;
			} else if (o1.getOrder() < o2.getOrder()) {
				return -1;
			}
			return 0;
		}
	};

	private static volatile DowngradeRegistry instance;

	/**
	 * Initializes the singleton instance of {@link DowngradeRegistry}
	 */
	static void initInstance() {
		synchronized (DowngradeRegistry.class) {
			if (instance == null) {
				instance = new DowngradeRegistry();
				instance.init();
			}
		}
	}

	/**
	 * Releases the singleton instance of {@link DowngradeRegistry}
	 */
	static void releaseInstance() {
		synchronized (DowngradeRegistry.class) {
			if (instance != null) {
				instance = null;
			}
		}
	}

	/**
	 * @return The singleton instance of {@link DowngradeRegistry}
	 */
	public static DowngradeRegistry getInstance() {
		return instance;
	}

	private void init() {
        registerDowngradeListener(new InfostoreDowngrade());
        registerDowngradeListener(new TasksDowngrade());
		/*
		 * TODO: Insert downgrade listeners for user configuration, settings,
		 * quota and attachments
		 */
		/*
		 * Insert folder downgrade listener
		 */
		registerDowngradeListener(new OXFolderDowngradeListener());
		/*
		 * TODO: Insert downgrade listeners for FileStorage.
		 */
	}

	/**
	 * The class set to detect duplicate listeners
	 */
	private final Set<Class<? extends DowngradeListener>> classes;

	/**
	 * The listener list to keep the order
	 */
	private final List<DowngradeListener> listeners;

	/**
	 * The lock to synchronize accesses
	 */
	private final Lock registryLock;

	private DowngradeRegistry() {
		super();
		registryLock = new ReentrantLock();
		listeners = new ArrayList<DowngradeListener>();
		classes = new HashSet<Class<? extends DowngradeListener>>();
	}

	/**
	 * Fires the downgrade event. This method deletes invisible data according to the given data in {@link DowngradeEvent}. This must only
	 * be called from the "deleteinvisible" CLT or the according RMI call.
	 *
	 * @param downgradeEvent
	 *            the downgrade event
	 * @throws OXException
	 *             if downgrade event could not be performed
	 */
	public void fireDowngradeEvent(final DowngradeEvent downgradeEvent) throws OXException {
		registryLock.lock();
		try {
			final int size = listeners.size();
			final Iterator<DowngradeListener> iter = listeners.iterator();
			for (int i = 0; i < size; i++) {
				iter.next().downgradePerformed(downgradeEvent);
			}
		} finally {
			registryLock.unlock();
		}
	}

	/**
	 * Registers an instance of {@link DowngradeListener}.<br>
	 * <b>Note</b>: Only one instance of a certain {@link DowngradeListener}
	 * implementation is added, meaning if you try to register a certain
	 * implementation twice, the latter one is going to be discarded
	 *
	 * @param listener
	 *            the listener to register
	 * @return <code>true</code> if specified downgrade listener has been
	 *         added to registry; otherwise <code>false</code>
	 */
	public boolean registerDowngradeListener(final DowngradeListener listener) {
		registryLock.lock();
		try {
			if (classes.contains(listener.getClass())) {
				return false;
			}
			listeners.add(listener);
			Collections.sort(listeners, COMPARATOR);
			classes.add(listener.getClass());
			return true;
		} finally {
			registryLock.unlock();
		}
	}

	/**
	 * Removes given instance of {@link DowngradeListener} from this registry's
	 * known listeners.
	 *
	 * @param listener -
	 *            the listener to remove
	 */
	public void unregisterDowngradeListener(final DowngradeListener listener) {
		registryLock.lock();
		try {
			final Class<? extends DowngradeListener> clazz = listener.getClass();
			if (!classes.contains(clazz)) {
				return;
			}
			if (!listeners.remove(listener)) {
				/*
				 * Remove by reference did not work
				 */
				int size = listeners.size();
				for (int i = 0; i < size; i++) {
					if (clazz.equals(listeners.get(i).getClass())) {
						listeners.remove(i);
						// Reset size to leave loop
						size = 0;
					}
				}
			}
			classes.remove(clazz);
		} finally {
			registryLock.unlock();
		}
	}

}
