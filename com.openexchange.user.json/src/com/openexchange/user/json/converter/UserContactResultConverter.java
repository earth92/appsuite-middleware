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

package com.openexchange.user.json.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.common.collect.ImmutableSet;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.anonymizer.AnonymizerService;
import com.openexchange.ajax.anonymizer.Anonymizers;
import com.openexchange.ajax.anonymizer.Module;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.Converter;
import com.openexchange.ajax.requesthandler.ResultConverter;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;
import com.openexchange.tools.session.ServerSession;
import com.openexchange.user.User;
import com.openexchange.user.json.Utility;
import com.openexchange.user.json.actions.GetAction;
import com.openexchange.user.json.dto.UserContact;

/**
 * {@link UserContactResultConverter} - JSON result converter for user contacts.
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 */
public class UserContactResultConverter implements ResultConverter {

    private static final Set<String> EXPECTED_NAMES = ImmutableSet.of(
    		AJAXServlet.PARAMETER_COLUMNS, AJAXServlet.PARAMETER_SORT, AJAXServlet.PARAMETER_ORDER, AJAXServlet.LEFT_HAND_LIMIT,
    		AJAXServlet.RIGHT_HAND_LIMIT, AJAXServlet.PARAMETER_TIMEZONE, AJAXServlet.PARAMETER_SESSION, AJAXServlet.PARAMETER_ACTION);

	@Override
	public String getInputFormat() {
		return "usercontact";
	}

	@Override
	public String getOutputFormat() {
		return "json";
	}

	@Override
	public Quality getQuality() {
		return Quality.GOOD;
	}

	@Override
	public void convert(AJAXRequestData requestData, AJAXRequestResult result, ServerSession session, Converter converter) throws OXException {
    	/*
    	 * determine timezone
    	 */
        String timeZoneID = requestData.getParameter("timezone");
        if (null == timeZoneID) {
        	timeZoneID = session.getUser().getTimeZone();
        }
        /*
         * get requested column IDs and additional user attributes
         */
        int[] columnIDs = Utility.parseOptionalIntArrayParameter(AJAXServlet.PARAMETER_COLUMNS, requestData);
        Map<String, List<String>> attributeParameters = Utility.getAttributeParameters(EXPECTED_NAMES, requestData);
		/*
		 * convert current result object
		 */
        Object resultObject = result.getResultObject();
        if (null == resultObject) {
			resultObject = JSONObject.NULL;
        } else if (GetAction.ACTION.equalsIgnoreCase(requestData.getAction())) {
			/*
			 * convert single user contact
			 */
			UserContact userContact = (UserContact)resultObject;

			User optUser = userContact.getUser();
			if (Anonymizers.isGuest(session)) {
                if (null != optUser && session.getUserId() != optUser.getId()) {
                    Set<Integer> sharingUsers = Anonymizers.getSharingUsersFor(session.getContextId(), session.getUserId());
                    if (false == sharingUsers.contains(Integer.valueOf(optUser.getId()))) {
                        userContact.setContact(Anonymizers.optAnonymize(userContact.getContact(), Module.CONTACT, session));
                        userContact.setUser(Anonymizers.optAnonymize(optUser, Module.USER, session));
                    }
                }
			} else {
			    if (null != optUser && session.getUserId() != optUser.getId() && Anonymizers.isNonVisibleGuest(optUser.getId(), session)) {
			        userContact.setContact(Anonymizers.optAnonymize(userContact.getContact(), Module.CONTACT, session));
	                userContact.setUser(Anonymizers.optAnonymize(optUser, Module.USER, session));
			    }
			}

			resultObject = userContact.serialize(timeZoneID, session);
        } else {
            /*
             * convert multiple user contacts into array
             */
            List<UserContact> userContacts = (List<UserContact>) resultObject;
            JSONArray jArray = new JSONArray(userContacts.size());

            AnonymizerService<Contact> contactAnonymizer = Anonymizers.optAnonymizerFor(Module.CONTACT);
            AnonymizerService<User> userAnonymizer = Anonymizers.optAnonymizerFor(Module.USER);
            if (Anonymizers.isGuest(session)) {
                Set<Integer> sharingUsers = null;
                for (UserContact userContact : userContacts) {
                    User optUser = userContact.getUser();
                    if (null != optUser && session.getUserId() != optUser.getId()) {
                        if (null == sharingUsers) {
                            sharingUsers = Anonymizers.getSharingUsersFor(session.getContextId(), session.getUserId());
                        }
                        if (false == sharingUsers.contains(Integer.valueOf(optUser.getId()))) {
                            userContact.setContact(contactAnonymizer.anonymize(userContact.getContact(), session));
                            userContact.setUser(userAnonymizer.anonymize(optUser, session));
                        }
                    }
                    jArray.put(userContact.serialize(session, columnIDs, timeZoneID, attributeParameters));
                }
            } else {
                for (UserContact userContact : userContacts) {
                    User optUser = userContact.getUser();
                    if (null != optUser && session.getUserId() != optUser.getId() && Anonymizers.isNonVisibleGuest(optUser.getId(), session)) {
                        userContact.setContact(contactAnonymizer.anonymize(userContact.getContact(), session));
                        userContact.setUser(userAnonymizer.anonymize(optUser, session));
                    }
                    jArray.put(userContact.serialize(session, columnIDs, timeZoneID, attributeParameters));
                }
            }

            resultObject = jArray;
        }
        result.setResultObject(resultObject, "json");
	}

}
