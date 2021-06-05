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

package com.openexchange.subscribe.xing;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.fileholder.IFileHolder;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.generic.FolderUpdaterRegistry;
import com.openexchange.groupware.generic.FolderUpdaterService;
import com.openexchange.java.Streams;
import com.openexchange.java.Strings;
import com.openexchange.log.LogProperties;
import com.openexchange.oauth.KnownApi;
import com.openexchange.oauth.OAuthServiceMetaData;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.subscribe.Subscription;
import com.openexchange.subscribe.SubscriptionErrorMessage;
import com.openexchange.subscribe.oauth.AbstractOAuthSubscribeService;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;
import com.openexchange.tools.iterator.SearchIteratorDelegator;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.xing.Address;
import com.openexchange.xing.Contacts;
import com.openexchange.xing.PhotoUrls;
import com.openexchange.xing.User;
import com.openexchange.xing.UserField;
import com.openexchange.xing.XingAPI;
import com.openexchange.xing.access.XingExceptionCodes;
import com.openexchange.xing.access.XingOAuthAccess;
import com.openexchange.xing.access.XingOAuthAccessProvider;
import com.openexchange.xing.exception.XingException;
import com.openexchange.xing.exception.XingUnlinkedException;
import com.openexchange.xing.session.WebAuthSession;

/**
 * {@link XingSubscribeService}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class XingSubscribeService extends AbstractOAuthSubscribeService {

    /**
     * The subscription source id
     */
    public static final String SOURCE_ID = "com.openexchange.subscribe.xing";

    /** The logger constant */
    static final Logger LOG = LoggerFactory.getLogger(XingSubscribeService.class);

    // -------------------------------------------------------------------------------------------------------------------------- //

    private interface PhotoHandler {

        void handlePhoto(User xingUser, Contact contact, Subscription subscription, ServerSession session) throws OXException;
    }

    private final class CollectingPhotoHandler implements PhotoHandler {

        private final Map<String, String> photoUrlsMap;

        /**
         * Initializes a new {@link CollectingPhotoHandler}.
         */
        CollectingPhotoHandler(Map<String, String> photoUrlsMap) {
            super();
            this.photoUrlsMap = photoUrlsMap;
        }

        @Override
        public void handlePhoto(User xingUser, Contact contact, Subscription subscription, ServerSession session) throws OXException {
            final PhotoUrls photoUrls = xingUser.getPhotoUrls();
            String url = photoUrls.getMaxiThumbUrl();
            if (url == null) {
                url = photoUrls.getLargestAvailableUrl();
            }

            final String id = xingUser.getId();
            if (url != null && isNotNull(id)) {
                photoUrlsMap.put(id, url);
            }
        }
    }

    private final PhotoHandler loadingPhotoHandler = new PhotoHandler() {

        @Override
        public void handlePhoto(final User xingUser, final Contact contact, Subscription subscription, ServerSession session) throws OXException {
            if (null == xingUser || null == contact) {
                return;
            }

            final PhotoUrls photoUrls = xingUser.getPhotoUrls();
            String url = photoUrls.getMaxiThumbUrl();
            if (url == null) {
                url = photoUrls.getLargestAvailableUrl();
            }

            if (url != null) {
                XingOAuthAccess xingAccess = getXingOAuthAccess(subscription, session);
                XingAPI<WebAuthSession> api = xingAccess.getXingAPI();
                try {
                    IFileHolder photo = api.getPhoto(url);
                    if (photo != null) {
                        byte[] bytes = Streams.stream2bytes(photo.getStream());
                        contact.setImage1(bytes);
                        contact.setImageContentType(photo.getContentType());
                    }
                } catch (XingException e) {
                    throw SubscriptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
                } catch (IOException e) {
                    throw SubscriptionErrorMessage.UNEXPECTED_ERROR.create(e, e.getMessage());
                }
            }
        }
    };

    // -------------------------------------------------------------------------------------------------------------------------- //

    private final ServiceLookup services;

    /**
     * Initializes a new {@link XingSubscribeService}.
     *
     * @param oAuthServiceMetaData The {@link OAuthServiceMetaData}
     * @param services The {@link ServiceLookup}
     * @throws OXException
     */
    public XingSubscribeService(OAuthServiceMetaData oauthServiceMetadata, ServiceLookup services) throws OXException {
        super(oauthServiceMetadata, "com.openexchange.subscribe.xing", FolderObject.CONTACT, "XING", services);
        this.services = services;
    }

    @Override
    public Collection<?> getContent(final Subscription subscription) throws OXException {
        try {
            final ServerSession session = subscription.getSession();
            final XingOAuthAccess xingOAuthAccess = getXingOAuthAccess(subscription, session);
            final XingAPI<WebAuthSession> xingAPI = xingOAuthAccess.getXingAPI();
            final String userId = xingOAuthAccess.getXingUserId();
            final List<UserField> userFields = Arrays.asList(UserField.values());

            // Request first chunk to determine total number of contacts
            final int firstChunkLimit = 25;
            final Contacts contacts = xingAPI.getContactsFrom(userId, firstChunkLimit, 0, null, userFields);
            List<User> chunk = contacts.getUsers();
            if (chunk.size() < firstChunkLimit) {
                // Obtained less than requested; no more contacts available then
                return convert(chunk, loadingPhotoHandler, subscription, session);
            }
            final int maxLimit = 25;
            final int total = contacts.getTotal();
            // Check availability of tracked services needed for manual storing for contacts
            final FolderUpdaterRegistry folderUpdaterRegistry = Services.getOptionalService(FolderUpdaterRegistry.class);
            final ThreadPoolService threadPool = Services.getOptionalService(ThreadPoolService.class);
            final FolderUpdaterService<Contact> folderUpdater = null == folderUpdaterRegistry ? null : folderUpdaterRegistry.<Contact> getFolderUpdater(subscription);
            if (null == threadPool || null == folderUpdater) {
                // Retrieve all
                final List<User> users = new ArrayList<>(total);
                users.addAll(chunk);
                int offset = chunk.size();
                // Request remaining chunks
                while (offset < total) {
                    final int remain = total - offset;
                    chunk = xingAPI.getContactsFrom(userId, remain > maxLimit ? maxLimit : remain, offset, null, userFields).getUsers();
                    users.addAll(chunk);
                    offset += chunk.size();
                }
                // All retrieved
                LOG.info("Going to convert {} XING contacts for user {} in context {}", I(total), I(session.getUserId()), I(session.getContextId()));
                final Map<String, String> photoUrlsMap = new HashMap<>(total);
                final PhotoHandler photoHandler = new CollectingPhotoHandler(photoUrlsMap);
                final List<Contact> retval = convert(chunk, photoHandler, subscription, session);
                LOG.info("Converted {} XING contacts for user {} in context {}", I(total), I(session.getUserId()), I(session.getContextId()));

                // TODO: Schedule a separate task to fill photos

                return retval;
            }
            // Schedule task for remainder...
            final int startOffset = chunk.size();
            threadPool.submit(new AbstractTask<Void>() {

                @Override
                public Void call() throws Exception {
                    LogProperties.put(LogProperties.Name.SUBSCRIPTION_ADMIN, "true");
                    try {
                        int off = startOffset;
                        while (off < total) {
                            final int remain = total - off;
                            final List<User> chunk = xingAPI.getContactsFrom(userId, remain > maxLimit ? maxLimit : remain, off, null, userFields).getUsers();
                            // Store them
                            final List<Contact> convertees = convert(chunk, loadingPhotoHandler, subscription, session);
                            LOG.info("Converted {} XING contacts for user {} in context {}", I(chunk.size()), I(session.getUserId()), I(session.getContextId()));
                            folderUpdater.save(new SearchIteratorDelegator<>(convertees), subscription);
                            // Next chunk...
                            off += chunk.size();
                        }
                        return null;
                    } finally {
                        LogProperties.remove(LogProperties.Name.SUBSCRIPTION_ADMIN);
                    }
                }
            });
            // Return first chunk with this thread
            return convert(chunk, loadingPhotoHandler, subscription, session);
        } catch (XingUnlinkedException e) {
            throw XingExceptionCodes.UNLINKED_ERROR.create();
        } catch (XingException e) {
            throw XingExceptionCodes.XING_ERROR.create(e, e.getMessage());
        } catch (RuntimeException e) {
            throw XingExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    @Override
    public void modifyOutgoing(final Subscription subscription) throws OXException {
        if (Strings.isNotEmpty(subscription.getSecret())) {
            try {
                // No extra null or empty check, it will be checked on super
                String displayName = getOAuthAccount(subscription.getSession(), subscription).getDisplayName();
                subscription.setDisplayName(displayName);
            } catch (Exception e) {
                LOG.debug("Failed to get OAuth account's display name", e);
            }
        }
        super.modifyOutgoing(subscription);
    }

    @Override
    protected KnownApi getKnownApi() {
        return KnownApi.XING;
    }

    /**
     * Converts specified XING users to contacts
     *
     * @param xingContacts The XING users
     * @param optPhotoHandler The photo handler
     * @return The resulting contacts
     */
    protected List<Contact> convert(final List<User> xingContacts, final PhotoHandler optPhotoHandler, Subscription subscription, final ServerSession session) {
        final List<Contact> ret = new ArrayList<>(xingContacts.size());
        for (final User xingContact : xingContacts) {
            ret.add(convert(xingContact, optPhotoHandler, subscription, session));
        }
        return ret;
    }

    /**
     * Gets the XING OAuth access.
     *
     * @param subscription The subscription information
     * @param session The associated session
     * @return The XING OAuth access
     * @throws OXException If XING OAuth access cannot be returned
     */
    protected XingOAuthAccess getXingOAuthAccess(Subscription subscription, ServerSession session) throws OXException {
        final XingOAuthAccessProvider provider = services.getService(XingOAuthAccessProvider.class);
        if (null == provider) {
            throw ServiceExceptionCode.absentService(XingOAuthAccessProvider.class);
        }

        int xingOAuthAccount;
        {
            Object accountId = subscription.getConfiguration().get("account");
            if (null == accountId) {
                xingOAuthAccount = provider.getXingOAuthAccount(session);
            } else {
                if (accountId instanceof Integer) {
                    xingOAuthAccount = ((Integer) accountId).intValue();
                } else {
                    xingOAuthAccount = Integer.parseInt(accountId.toString());
                }
            }
        }

        return provider.accessFor(xingOAuthAccount, session);
    }

    private Contact convert(final User xingUser, final PhotoHandler optPhotoHandler, Subscription subscription, final ServerSession session) {
        if (null == xingUser) {
            return null;
        }
        final Contact oxContact = new Contact();
        boolean email1Set = false;
        {
            final String s = xingUser.getActiveMail();
            if (isNotNull(s)) {
                oxContact.setEmail1(s);
                email1Set = true;
            }
        }
        {
            final String s = xingUser.getDisplayName();
            if (isNotNull(s)) {
                oxContact.setDisplayName(Strings.abbreviate(s, 320));
            }
        }
        {
            final String s = xingUser.getFirstName();
            if (isNotNull(s)) {
                oxContact.setGivenName(Strings.abbreviate(s, 128));
            }
        }
        {
            final String s = xingUser.getLastName();
            if (isNotNull(s)) {
                oxContact.setSurName(Strings.abbreviate(s, 128));
            }
        }
        {
            final String s = xingUser.getGender();
            if (isNotNull(s)) {
                oxContact.setTitle("m".equals(s) ? "Mr." : "Mrs.");
            }
        }
        {
            final String s = xingUser.getHaves();
            if (isNotNull(s)) {
                oxContact.setUserField02(Strings.abbreviate(s, 64));
            }
        }
        {
            final String s = xingUser.getInterests();
            if (isNotNull(s)) {
                oxContact.setUserField01(Strings.abbreviate(s, 64));
            }
        }
        {
            final String s = xingUser.getWants();
            if (isNotNull(s)) {
                oxContact.setUserField03(Strings.abbreviate(s, 64));
            }
        }
        {
            final Map<String, Object> m = xingUser.getProfessionalExperience();
            if (null != m && !m.isEmpty()) {
                final Map<String, Object> primaryCompany = Map.class.cast(m.get("primary_company"));
                if (null != primaryCompany && !primaryCompany.isEmpty()) {
                    // Name
                    Object s = primaryCompany.get("name");
                    if (isNotNull(s)) {
                        oxContact.setCompany(Strings.abbreviate(s.toString(), 512));
                    }

                    // Title
                    s = primaryCompany.get("title");
                    if (isNotNull(s)) {
                        oxContact.setPosition(Strings.abbreviate(s.toString(), 128));
                    }
                }
            }
        }
        {
            final String s = xingUser.getOrganisationMember();
            if (isNotNull(s)) {
                oxContact.setUserField04(Strings.abbreviate(s, 64));
            }
        }
        {
            final String s = xingUser.getPermalink();
            if (isNotNull(s)) {
                oxContact.setURL(Strings.abbreviate(s, 128));
            }
        }
        {
            final Date d = xingUser.getBirthDate();
            if (null != d) {
                oxContact.setBirthday(d);
            }
        }
        boolean email2Set = false;
        {
            final Address a = xingUser.getBusinessAddress();
            if (null != a) {
                String s = a.getCity();
                if (isNotNull(s)) {
                    oxContact.setCityBusiness(Strings.abbreviate(s, 64));
                }
                s = a.getCountry();
                if (isNotNull(s)) {
                    oxContact.setCountryBusiness(Strings.abbreviate(s, 64));
                }
                s = a.getEmail();
                if (isNotNull(s)) {
                    if (email1Set) {
                        oxContact.setEmail2(Strings.abbreviate(s, 256));
                        email2Set = true;
                    } else {
                        oxContact.setEmail1(Strings.abbreviate(s, 256));
                        email1Set = true;
                    }
                }
                s = a.getFax();
                if (isNotNull(s)) {
                    oxContact.setFaxBusiness(Strings.abbreviate(s, 64));
                }
                s = a.getMobilePhone();
                if (isNotNull(s)) {
                    oxContact.setCellularTelephone1(Strings.abbreviate(s, 64));
                }
                s = a.getPhone();
                if (isNotNull(s)) {
                    oxContact.setTelephoneBusiness1(Strings.abbreviate(s, 64));
                }
                s = a.getProvince();
                if (isNotNull(s)) {
                    oxContact.setStateBusiness(Strings.abbreviate(s, 64));
                }
                s = a.getStreet();
                if (isNotNull(s)) {
                    oxContact.setStreetBusiness(Strings.abbreviate(s, 64));
                }
                s = a.getZipCode();
                if (isNotNull(s)) {
                    oxContact.setPostalCodeBusiness(Strings.abbreviate(s, 64));
                }
            }
        }
        {
            final Address a = xingUser.getPrivateAddress();
            if (null != a) {
                String s = a.getCity();
                if (isNotNull(s)) {
                    oxContact.setCityHome(Strings.abbreviate(s, 64));
                }
                s = a.getCountry();
                if (isNotNull(s)) {
                    oxContact.setCountryHome(Strings.abbreviate(s, 64));
                }
                s = a.getEmail();
                if (isNotNull(s)) {
                    if (email1Set) {
                        if (email2Set) {
                            oxContact.setEmail3(Strings.abbreviate(s, 256));
                        } else {
                            oxContact.setEmail2(Strings.abbreviate(s, 256));
                            email2Set = true;
                        }
                    } else {
                        oxContact.setEmail1(Strings.abbreviate(s, 256));
                        email1Set = true;
                    }
                }
                s = a.getFax();
                if (isNotNull(s)) {
                    oxContact.setFaxHome(Strings.abbreviate(s, 64));
                }
                s = a.getMobilePhone();
                if (isNotNull(s)) {
                    oxContact.setCellularTelephone2(Strings.abbreviate(s, 64));
                }
                s = a.getPhone();
                if (isNotNull(s)) {
                    oxContact.setTelephoneHome1(Strings.abbreviate(s, 64));
                }
                s = a.getProvince();
                if (isNotNull(s)) {
                    oxContact.setStateHome(Strings.abbreviate(s, 64));
                }
                s = a.getStreet();
                if (isNotNull(s)) {
                    oxContact.setStreetHome(Strings.abbreviate(s, 64));
                }
                s = a.getZipCode();
                if (isNotNull(s)) {
                    oxContact.setPostalCodeHome(Strings.abbreviate(s, 64));
                }
            }
        }
        {
            final Map<String, String> instantMessagingAccounts = xingUser.getInstantMessagingAccounts();
            if (null != instantMessagingAccounts) {
                final String skypeId = instantMessagingAccounts.get("skype");
                if (isNotNull(skypeId)) {
                    oxContact.setInstantMessenger1(Strings.abbreviate(skypeId, 64));
                }
                for (final Map.Entry<String, String> e : instantMessagingAccounts.entrySet()) {
                    if (!"skype".equals(e.getKey()) && !"null".equals(e.getValue())) {
                        oxContact.setInstantMessenger2(Strings.abbreviate(e.getValue(), 64));
                        break;
                    }
                }
            }

        }
        if (null != optPhotoHandler) {
            try {
                optPhotoHandler.handlePhoto(xingUser, oxContact, subscription, session);
            } catch (Exception e) {
                LOG.warn("Could not handle photo from XING contact {} ({}).", xingUser.getDisplayName(), xingUser.getId());
            }
        }
        return oxContact;
    }

    protected boolean isNotNull(Object s) {
        return null != s && !"null".equals(s.toString());
    }
}
