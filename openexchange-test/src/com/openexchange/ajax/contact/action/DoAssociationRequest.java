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

package com.openexchange.ajax.contact.action;

import org.json.JSONArray;
import org.json.JSONException;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.FinalContactConstants;
import com.openexchange.ajax.container.Response;
import com.openexchange.ajax.framework.AbstractAJAXParser;
import com.openexchange.ajax.framework.Params;
import com.openexchange.groupware.container.Contact;

/**
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a>
 */
public class DoAssociationRequest extends AbstractContactRequest<DoAssociationResponse> {

    private final Contact contributor, aggregator;

    public DoAssociationRequest(Contact contributor, Contact aggregator) {
        super();
        this.contributor = contributor;
        this.aggregator = aggregator;
    }

    @Override
    public Object getBody() throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put(convert(contributor));
        arr.put(convert(aggregator));
        return arr;
    }

    @Override
    public Method getMethod() {
        return Method.PUT;
    }

    @Override
    public Parameter[] getParameters() {
        Params params = new Params(AJAXServlet.PARAMETER_ACTION, FinalContactConstants.ACTION_ASSOCIATE.getName());

        if (contributor.containsUserField20()) {
            params.add(FinalContactConstants.PARAMETER_UUID1.getName(), contributor.getUserField20());
        } else {
            params.add(FinalContactConstants.PARAMETER_FOLDER_ID1.getName(), String.valueOf(contributor.getParentFolderID()), FinalContactConstants.PARAMETER_CONTACT_ID1.getName(), String.valueOf(contributor.getObjectID()));
        }

        if (aggregator.containsUserField20()) {
            params.add(FinalContactConstants.PARAMETER_UUID2.getName(), aggregator.getUserField20());
        } else {
            params.add(FinalContactConstants.PARAMETER_FOLDER_ID2.getName(), String.valueOf(aggregator.getParentFolderID()), FinalContactConstants.PARAMETER_CONTACT_ID2.getName(), String.valueOf(aggregator.getObjectID()));
        }

        return params.toArray();
    }

    @Override
    public AbstractAJAXParser<? extends DoAssociationResponse> getParser() {
        return new AbstractAJAXParser<DoAssociationResponse>(true) {

            @Override
            public DoAssociationResponse createResponse(final Response response) {
                return new DoAssociationResponse(response);
            }
        };
    }

}
