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

package com.openexchange.messaging.json.cacheing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.openexchange.caching.Cache;
import com.openexchange.exception.OXException;
import com.openexchange.messaging.IndexRange;
import com.openexchange.messaging.MessagingContent;
import com.openexchange.messaging.MessagingExceptionCodes;
import com.openexchange.messaging.MessagingField;
import com.openexchange.messaging.MessagingMessage;
import com.openexchange.messaging.MessagingMessageAccess;
import com.openexchange.messaging.MessagingPart;
import com.openexchange.messaging.OrderDirection;
import com.openexchange.messaging.SearchTerm;
import com.openexchange.session.Session;

/**
 * {@link CacheingMessageAccess}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class CacheingMessageAccess implements MessagingMessageAccess {

    private final MessagingMessageAccess delegate;

    private final Cache cache;

    // private final String folderPrefix;

    // private final Session session;

    /**
     * The prefix for a group name: &lt;context-id&gt; + "/" + &lt;folder-prefix&gt; + "/"
     */
    private final String groupNamePrefix;

    public CacheingMessageAccess(final MessagingMessageAccess delegate, final Cache cache, final String folderPrefix, final Session session) {
        this.delegate = delegate;
        this.cache = cache;
        // this.folderPrefix = folderPrefix;
        // this.session = session;
        groupNamePrefix = new StringBuilder(null == session ? "" : Integer.toString(session.getContextId())).append('/').append(folderPrefix).append('/').toString();
    }

    @Override
    public MessagingPart getAttachment(final String folder, final String messageId, final String sectionId) throws OXException {
        return delegate.getAttachment(folder, messageId, sectionId);
    }

    @Override
    public void appendMessages(final String folder, final MessagingMessage[] messages) throws OXException {
        delegate.appendMessages(folder, messages);
    }

    @Override
    public List<String> copyMessages(final String sourceFolder, final String destFolder, final String[] messageIds, final boolean fast) throws OXException {
        return delegate.copyMessages(sourceFolder, destFolder, messageIds, fast);
    }

    @Override
    public void deleteMessages(final String folder, final String[] messageIds, final boolean hardDelete) throws OXException {
        delegate.deleteMessages(folder, messageIds, hardDelete);
    }

    @Override
    public List<MessagingMessage> getAllMessages(final String folder, final IndexRange indexRange, final MessagingField sortField, final OrderDirection order, final MessagingField... fields) throws OXException {
        clear(folder);
        return remember(delegate.getAllMessages(folder, indexRange, sortField, order, addDefaultFields(fields)));
    }

    private MessagingField[] addDefaultFields(final MessagingField[] fields) {
        final Set<MessagingField> allFields = null == fields ? new HashSet<MessagingField>(12) : new HashSet<MessagingField>(Arrays.asList(fields));
        allFields.add(MessagingField.FOLDER_ID);
        allFields.add(MessagingField.ID);
        allFields.add(MessagingField.SUBJECT);
        allFields.add(MessagingField.FROM);
        allFields.add(MessagingField.RECEIVED_DATE);
        allFields.add(MessagingField.BODY);
        allFields.add(MessagingField.HEADERS);
        allFields.add(MessagingField.PICTURE);
        return allFields.toArray(new MessagingField[allFields.size()]);
    }

    @Override
    public MessagingMessage getMessage(final String folder, final String id, final boolean peek) throws OXException {
        MessagingMessage msg = get(folder, id);
        if (msg != null) {
            return msg;
        }
        msg = delegate.getMessage(folder, id, peek);
        if (msg == null) {
            throw MessagingExceptionCodes.MESSAGE_NOT_FOUND.create(id, folder);
        }
        return remember(msg);
    }

    @Override
    public List<MessagingMessage> getMessages(final String folder, final String[] messageIds, final MessagingField[] fields) throws OXException {
        final Map<String, MessagingMessage> allMessages = new HashMap<String, MessagingMessage>(messageIds.length);
        final List<String> idsToLoad = new ArrayList<String>(messageIds.length);

        for (final String id : messageIds) {
            final MessagingMessage cached = get(folder, id);
            if (cached == null) {
                idsToLoad.add(id);
            } else {
                allMessages.put(id, cached);
            }
        }

        if (!idsToLoad.isEmpty()) {
            final List<MessagingMessage> messages = delegate.getMessages(folder, idsToLoad.toArray(new String[idsToLoad.size()]), fields);
            remember(messages);
            if (allMessages.isEmpty()) {
                return remember(messages);
            }
            for (final MessagingMessage messagingMessage : messages) {
                if (null != messagingMessage) {
                    allMessages.put(messagingMessage.getId(), messagingMessage);
                }
            }
        }

        final List<MessagingMessage> messages = new ArrayList<MessagingMessage>(messageIds.length);
        for (final String id : messageIds) {
            messages.add(allMessages.get(id));
        }

        return remember(messages);
    }

    private List<MessagingMessage> remember(final List<MessagingMessage> messages) throws OXException {
        for (final MessagingMessage messagingMessage : messages) {
            if (null != messagingMessage) {
                remember(messagingMessage);
            }
        }
        return messages;
    }

    @Override
    public List<String> moveMessages(final String sourceFolder, final String destFolder, final String[] messageIds, final boolean fast) throws OXException {
        return delegate.moveMessages(sourceFolder, destFolder, messageIds, fast);
    }

    @Override
    public MessagingMessage perform(final MessagingMessage message, final String action) throws OXException {
        return delegate.perform(message, action);
    }

    @Override
    public MessagingMessage perform(final String folder, final String id, final String action) throws OXException {
        return delegate.perform(folder, id, action);
    }

    @Override
    public MessagingMessage perform(final String action) throws OXException {
        return delegate.perform(action);
    }

    @Override
    public List<MessagingMessage> searchMessages(final String folder, final IndexRange indexRange, final MessagingField sortField, final OrderDirection order, final SearchTerm<?> searchTerm, final MessagingField[] fields) throws OXException {
        return delegate.searchMessages(folder, indexRange, sortField, order, searchTerm, fields);
    }

    @Override
    public void updateMessage(final MessagingMessage message, final MessagingField[] fields) throws OXException {
        delegate.updateMessage(message, fields);
    }

    protected MessagingMessage get(final String folder, final String id) {
        return (MessagingMessage) cache.getFromGroup(id, getGroupName(folder));
    }

    @SuppressWarnings("deprecation")
    protected MessagingMessage remember(final MessagingMessage message) throws OXException {
        final String groupName = getGroupName(message.getFolder());
        final String key = message.getId();

        if (key != null) {
            cache.putInGroup(key, groupName, message);
        }

        return message;
    }

    protected void clear(final String folderId) {
        cache.invalidateGroup(getGroupName(folderId));

    }

    protected String getGroupName(final String folderId) {
        return new StringBuilder(groupNamePrefix).append(folderId).toString();
    }

    @Override
    public MessagingContent resolveContent(final String folder, final String id, final String referenceId) throws OXException {
        return delegate.resolveContent(folder, id, referenceId);
    }

}
