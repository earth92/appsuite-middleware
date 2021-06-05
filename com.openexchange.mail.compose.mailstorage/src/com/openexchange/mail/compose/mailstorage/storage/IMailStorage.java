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

package com.openexchange.mail.compose.mailstorage.storage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.exception.OXException;
import com.openexchange.mail.MailPath;
import com.openexchange.mail.Quota;
import com.openexchange.mail.compose.Attachment;
import com.openexchange.mail.compose.ClientToken;
import com.openexchange.mail.compose.MessageDescription;
import com.openexchange.mail.compose.MessageField;
import com.openexchange.mail.compose.SharedFolderReference;
import com.openexchange.session.Session;

/**
 * {@link IMailStorage} - Accesses mail storage.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.5
 */
public interface IMailStorage {

    /**
     * Looks-up the draft mail associated with given composition space.
     *
     * @param compositionSpaceId The composition space identifier
     * @param session The session providing user data
     * @return The mail storage identifier or empty
     * @throws OXException If there is no such association available
     */
    MailStorageResult<Optional<MailStorageId>> lookUp(UUID compositionSpaceId, Session session) throws OXException;

    /**
     * Looks-up the draft mails associated with given session-associated user.
     *
     * @param session The session providing user data
     * @return The look-up outcome
     * @throws OXException If mapping cannot be returned
     */
    MailStorageResult<LookUpOutcome> lookUp(Session session) throws OXException;

    /**
     * Looks-up the most recent draft mail for given composition space and validates it.
     *
     * @param compositionSpaceId The composition space identifier
     * @param session The session providing user data
     * @return The according message info
     * @throws OXException If no according mail exists
     */
    MailStorageResult<MessageInfo> lookUpMessage(UUID compositionSpaceId, Session session) throws OXException;

    /**
     * Generates the appropriate compose request to be used for mail transport.
     *
     * @param mailStorageId The mail storage identifier
     * @param clientToken The client token
     * @param request The request data
     * @param session The session providing user data
     * @return The compose request and meta information
     * @throws OXException If compose request and meta information cannot be returned
     * @throws MissingDraftException If draft mail is missing
     */
    MailStorageResult<ComposeRequestAndMeta> getForTransport(MailStorageId mailStorageId, ClientToken clientToken, AJAXRequestData request, Session session) throws OXException, MissingDraftException;

    /**
     * Validates the draft message representing given composition space.
     *
     * @param mailStorageId The mail storage identifier
     * @param session The session providing user data
     * @return The optional new draft path
     * @throws OXException If draft message cannot be returned
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<Optional<MailPath>> validate(MailStorageId mailStorageId, Session session) throws OXException, MissingDraftException;

    /**
     * Gets the draft message representing given composition space.
     *
     * @param mailStorageId The mail storage identifier
     * @param session The session providing user data
     * @return A {@link MessageInfo} instance describing the draft message
     * @throws OXException If draft message cannot be returned
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<MessageInfo> getMessage(MailStorageId mailStorageId, Session session) throws OXException, MissingDraftException;

    /**
     * Gets the draft messages representing given composition spaces.
     *
     * @param mailStorageIds The mail storage identifiers
     * @param fields The fields to pre-fill; if <code>null</code> or empty everything is filled
     * @param session The session providing user data
     * @return A mapping of composition space identifier to {@link MessageInfo}
     * @throws OXException If draft messages cannot be returned
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<Map<UUID, MessageInfo>> getMessages(Collection<? extends MailStorageId> mailStorageIds, Set<MessageField> fields, Session session) throws OXException, MissingDraftException;

    /**
     * Creates & stores a new mail representing given draft message.
     *
     * @param compositionSpaceId The composition space identifier
     * @param draftMessage The draft message
     * @param optionalSharedFolderRef The optional shared attachment folder reference
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link MessageInfo} instance describing the updated draft message
     * @throws OXException If creating a new mail fails
     */
    MailStorageResult<MessageInfo> createNew(UUID compositionSpaceId, MessageDescription draftMessage, Optional<SharedFolderReference> optionalSharedFolderRef, ClientToken clientToken, Session session) throws OXException;

    /**
     * Saves denoted draft mail as final draft.
     *
     * @param mailStorageId The mail storage identifier
     * @param clientToken The client token
     * @param session The session providing user data
     * @return The new draft path
     * @throws OXException If save attempt fails
     * @throws MissingDraftException If draft mail is missing
     */
    MailStorageResult<MailPath> saveAsFinalDraft(MailStorageId mailStorageId, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Updates the draft message associated with given composition space.
     *
     * @param mailStorageId The mail storage identifier
     * @param draftMessage The draft message providing the change to apply
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link MessageInfo} instance describing the updated draft message
     * @throws OXException If changes cannot be applied
     * @throws MissingDraftException If draft mail is missing
     */
    MailStorageResult<MessageInfo> update(MailStorageId mailStorageId, MessageDescription draftMessage, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Deletes the draft mail referenced by given mail path.
     *
     * @param mailStorageId The mail storage identifier
     * @param hardDelete Whether associated draft message is supposed to be permanently deleted or a backup should be moved to standard trash folder
     * @param deleteSharedAttachmentsFolderIfPresent Whether to delete the possibly existent shared attachments folder. Only effective if parameter <code>hardDelete</code> is set to <code>true</code>
     * @param clientToken The client token
     * @param session The session providing user data
     * @return <code>true</code> if mail has been successfully deleted; otherwise <code>false</code>
     * @throws OXException If deletion fails
     */
    MailStorageResult<Boolean> delete(MailStorageId mailStorageId, boolean hardDelete, boolean deleteSharedAttachmentsFolderIfPresent, ClientToken clientToken, Session session) throws OXException;

    /**
     * Adds the attachments from referenced original mail to draft message.
     *
     * @param mailStorageId The mail storage identifier
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link NewAttachmentsInfo} instance describing the added attachments and updated draft message
     * @throws OXException If attachments from referenced original mail cannot be added
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<NewAttachmentsInfo> addOriginalAttachments(MailStorageId mailStorageId, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Adds the vCard of session-associated user to draft message.
     *
     * @param mailStorageId The mail storage identifier
     * @param clientToken The client token
     * @param session The session providing user data
     * @param draftPath The draft message's path
     * @return A {@link NewAttachmentsInfo} instance describing the added attachments and updated draft message
     * @throws OXException If attachments cannot be added
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<NewAttachmentsInfo> addVCardAttachment(MailStorageId mailStorageId, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Adds the vCard of specified contact to draft message.
     *
     * @param mailStorageId The mail storage identifier
     * @param contactId The identifier of the contact
     * @param folderId The identifier of the folder, in which the contact resides
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link NewAttachmentsInfo} instance describing the added attachments and updated draft message
     * @throws OXException If attachments cannot be added
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<NewAttachmentsInfo> addContactVCardAttachment(MailStorageId mailStorageId, String contactId, String folderId, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Adds given attachments to a new mail then representing current draft message.
     *
     * @param mailStorageId The mail storage identifier
     * @param attachments The attachments to add
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link NewAttachmentsInfo} instance describing the added attachments and updated draft message
     * @throws OXException If attachments cannot be added
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<NewAttachmentsInfo> addAttachments(MailStorageId mailStorageId, List<Attachment> attachments, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Replaces given attachment resulting in a new mail then representing current draft message.
     *
     * @param mailStorageId The mail storage identifier
     * @param attachment The attachment to replace
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link NewAttachmentsInfo} instance describing the added attachments and updated draft message
     * @throws OXException If attachment cannot be replaced
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<NewAttachmentsInfo> replaceAttachment(MailStorageId mailStorageId, Attachment attachment, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Looks-up attachment for given identifier.
     *
     * @param mailStorageId The mail storage identifier
     * @param attachmentId The identifier of the attachment to look-up
     * @param session The session providing user data
     * @return The attachment
     * @throws OXException If attachment cannot be returned
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<Attachment> getAttachment(MailStorageId mailStorageId, UUID attachmentId, Session session) throws OXException, MissingDraftException;

    /**
     * Deletes a list of attachments from the draft message. Matching
     * happens based on contained attachment IDs.
     *
     * @param mailStorageId The mail storage identifier
     * @param attachmentIds The identifiers of the attachments to delete
     * @param clientToken The client token
     * @param session The session providing user data
     * @return A {@link MessageInfo} instance describing the updated draft message
     * @throws OXException If attachment cannot be deleted
     * @throws MissingDraftException If a draft mail is missing
     */
    MailStorageResult<MessageInfo> deleteAttachments(MailStorageId mailStorageId, List<UUID> attachmentIds, ClientToken clientToken, Session session) throws OXException, MissingDraftException;

    /**
     * Gets the mail storage quota for drafts folder.
     *
     * @param session The session providing user data
     * @return The quota
     * @throws OXException If getting quota failed
     */
    MailStorageResult<Quota> getStorageQuota(Session session) throws OXException;

}
