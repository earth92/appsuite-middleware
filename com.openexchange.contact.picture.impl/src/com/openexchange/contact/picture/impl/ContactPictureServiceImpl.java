/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.contact.picture.impl;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.contact.picture.ContactPicture;
import com.openexchange.contact.picture.ContactPictureExceptionCodes;
import com.openexchange.contact.picture.ContactPictureService;
import com.openexchange.contact.picture.PictureSearchData;
import com.openexchange.contact.picture.finder.ContactPictureFinder;
import com.openexchange.contact.picture.finder.PictureResult;
import com.openexchange.exception.OXException;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.session.Session;

/**
 * {@link ContactPictureServiceImpl} is a service the retrieve a picture for a given user id, contact id or email address.
 *
 * @author <a href="mailto:daniel.becker@open-xchange.com">Daniel Becker</a>
 * @since v7.10.1
 */
public class ContactPictureServiceImpl extends RankingAwareNearRegistryServiceTracker<ContactPictureFinder> implements ContactPictureService {

    private final static Logger LOGGER = LoggerFactory.getLogger(ContactPictureServiceImpl.class);

    /**
     * Initializes a new {@link ContactPictureServiceImpl}.
     *
     * @param context The {@link BundleContext}
     */
    public ContactPictureServiceImpl(BundleContext context) {
        super(context, ContactPictureFinder.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public ContactPicture getPicture(Session session, PictureSearchData searchData) {
        return get(session, searchData, (ContactPictureFinder f, Session s, PictureSearchData d) -> {
            return f.getPicture(s, d);
        });
    }

    @Override
    public String getETag(Session session, PictureSearchData searchData) {
        ContactPicture picture = get(session, searchData, (ContactPictureFinder f, Session s, PictureSearchData d) -> {
            return f.getETag(s, d);
        });
        return null == picture ? null : picture.getETag();
    }

    @Override
    public Date getLastModified(Session session, PictureSearchData searchData) {
        ContactPicture picture = get(session, searchData, (ContactPictureFinder f, Session s, PictureSearchData d) -> {
            return f.getLastModified(s, d);
        });
        return null == picture ? ContactPicture.UNMODIFIED : picture.getLastModified();
    }

    // ---------------------------------------------------------------------------------------------

    private ContactPicture get(Session session, PictureSearchData searchData, ResultWrapper f) {
        try {
            if (null == session) {
                throw ContactPictureExceptionCodes.MISSING_SESSION.create();
            }

            PictureSearchData data = searchData;
            // Ask each finder if it contains the picture
            for (Iterator<ContactPictureFinder> iterator = iterator(); iterator.hasNext();) {
                ContactPictureFinder next = iterator.next();

                // Try to get contact picture
                PictureResult pictureResult = f.apply(next, session, data);
                if (pictureResult.wasFound()) {
                    return pictureResult.getPicture();
                }
                data = mergeResult(data, pictureResult);
            }
        } catch (OXException e) {
            LOGGER.debug("Unable to get contact picture. Using fallback instead.", e);
        }
        return ContactPicture.NOT_FOUND;
    }

    private PictureSearchData mergeResult(PictureSearchData data, PictureResult pictureResult) {
        PictureSearchData modified = pictureResult.getData();

        // Use client parameters prior the data we found out
        Integer userId = data.hasUser() ? data.getUserId() : modified.getUserId();
        String accountId = data.hasAccount() ? data.getAccountId() : modified.getAccountId();
        String folderId = data.hasFolder() ? data.getFolderId() : modified.getFolderId();
        String contactId = data.hasContact() ? data.getContactId() : modified.getContactId();

        LinkedHashSet<String> set = new LinkedHashSet<>(data.getEmails());
        set.addAll(modified.getEmails());

        return new PictureSearchData(userId, accountId, folderId, contactId, set);
    }

    // ---------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface ResultWrapper {

        PictureResult apply(ContactPictureFinder finder, Session session, PictureSearchData data) throws OXException;
    }

}
