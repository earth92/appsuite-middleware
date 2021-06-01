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

package com.openexchange.microsoft.graph.contacts.parser;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.common.collect.ImmutableList;
import com.openexchange.groupware.container.Contact;
import com.openexchange.microsoft.graph.api.MicrosoftGraphContactsAPI;
import com.openexchange.microsoft.graph.contacts.parser.consumers.BirthdayConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.EmailAddressesConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.FamilyConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.ImAddressesConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.NameConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.NoteConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.OccupationConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.PhoneNumbersConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.PhotoConsumer;
import com.openexchange.microsoft.graph.contacts.parser.consumers.PostalAddressesConsumer;

/**
 * {@link ContactParser}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.2
 */
public class ContactParser {

    private final List<BiConsumer<JSONObject, Contact>> elementParsers;

    /**
     * Initialises a new {@link ContactParser}.
     * 
     * @param googleContactsService The Google's {@link ContactsService}
     */
    public ContactParser(MicrosoftGraphContactsAPI api, String accessToken) {
        ImmutableList.Builder<BiConsumer<JSONObject, Contact>> listBuilder = ImmutableList.builder();
        listBuilder.add(new NameConsumer());
        listBuilder.add(new EmailAddressesConsumer());
        listBuilder.add(new PostalAddressesConsumer());
        listBuilder.add(new BirthdayConsumer());
        listBuilder.add(new OccupationConsumer());
        listBuilder.add(new PhoneNumbersConsumer());
        listBuilder.add(new FamilyConsumer());
        listBuilder.add(new ImAddressesConsumer());
        listBuilder.add(new NoteConsumer());
        listBuilder.add(new PhotoConsumer(api, accessToken));
        elementParsers = listBuilder.build();
    }

    /**
     * Parses the specified {@link JSONArray} and returns it as a {@link List} of {@link Contact}s
     * 
     * @param feed The {@link JSONArray} to parse
     * @return a {@link List} of {@link Contact}s
     */
    public List<Contact> parseFeed(JSONObject feed) {
        List<Contact> contacts = new LinkedList<Contact>();
        JSONArray array = feed.optJSONArray("value");
        for (int index = 0; index < array.length(); index++) {
            contacts.add(parseContactEntry(array.optJSONObject(index)));
        }
        return contacts;
    }

    /**
     * Parses the specified {@link JSONObject} to a {@link Contact}
     * 
     * @param entry The {@link JSONObject} to parse
     * @return a new {@link Contact}
     */
    private Contact parseContactEntry(JSONObject entry) {
        Contact c = new Contact();
        for (BiConsumer<JSONObject, Contact> consumer : elementParsers) {
            consumer.accept(entry, c);
        }
        return c;
    }
}
