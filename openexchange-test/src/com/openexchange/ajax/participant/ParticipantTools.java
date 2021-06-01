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

package com.openexchange.ajax.participant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.json.JSONException;
import com.openexchange.ajax.framework.AJAXClient;
import com.openexchange.ajax.user.actions.SearchRequest;
import com.openexchange.ajax.user.actions.SearchResponse;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.Participant;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.search.ContactSearchObject;
import com.openexchange.test.AbstractAssertTool;
import com.openexchange.user.User;

/**
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 */
public class ParticipantTools extends AbstractAssertTool {

    public static List<Participant> getParticipants(final AJAXClient client) throws OXException, IOException, JSONException {
        final ContactSearchObject search = new ContactSearchObject();
        search.setPattern("*");
        search.addFolder(FolderObject.SYSTEM_LDAP_FOLDER_ID);
        final SearchRequest request = new SearchRequest(search, SearchRequest.DEFAULT_COLUMNS);
        final SearchResponse response = client.execute(request);
        final List<Participant> participants = new ArrayList<Participant>();
        for (final User user : response.getUser()) {
            participants.add(new UserParticipant(user.getId()));
        }
        return participants;
    }

    public static List<Participant> createParticipants(final int... userIds) {
        final List<Participant> participants = new ArrayList<Participant>();
        for (final int userId : userIds) {
            participants.add(new UserParticipant(userId));
        }
        return participants;
    }

    public static List<Participant> getParticipants(AJAXClient client, final int count, final boolean noCreator, final int creatorId) throws Exception {
        List<Participant> participants = getParticipants(client);
        if (noCreator) {
            removeParticipant(participants, creatorId);
        }
        participants = extractByRandom(participants, count);
        return participants;
    }

    public static Participant getSomeParticipant(AJAXClient client) throws OXException, IOException, JSONException {
        return getParticipants(client, 1, client.getValues().getUserId()).get(0);
    }

    public static List<Participant> getParticipants(final AJAXClient client, final int count, final int creatorId) throws OXException, IOException, JSONException {
        List<Participant> participants = getParticipants(client);
        if (-1 != creatorId) {
            removeParticipant(participants, creatorId);
        }
        participants = extractByRandom(participants, count);
        return participants;
    }

    public static void removeParticipant(final List<Participant> participants, final int creatorId) {
        final Iterator<Participant> iter = participants.iterator();
        while (iter.hasNext()) {
            if (iter.next().getIdentifier() == creatorId) {
                iter.remove();
            }
        }
    }

    public static void assertParticipants(Participant[] participants, int... userIds) {
        for (int userId : userIds) {
            boolean contained = false;
            for (Participant participant : participants) {
                if (Participant.USER == participant.getType() && participant.getIdentifier() == userId) {
                    contained = true;
                    break;
                }
            }
            assertTrue("Participant with identifier " + userId + " is missing.", contained);
        }
    }

    private static final Random rand = new Random(System.currentTimeMillis());

    public static List<Participant> extractByRandom(final List<Participant> participants, final int count) {
        final List<Participant> retval = new ArrayList<Participant>();
        do {
            final Participant participant = participants.get(rand.nextInt(participants.size()));
            if (!retval.contains(participant)) {
                retval.add(participant);
            }
        } while (retval.size() < count && retval.size() < participants.size());
        return retval;
    }

    /**
     * Converts the specified {@link Participant}'s array to a {@link Set} of strings
     *
     * @param participant The {@link Participant}'s array to convert
     * @return A {@link Set} with the string representation of the specified {@link Participant}'s array
     */
    protected static Set<String> participants2String(final Participant[] participant) {
        if (participant == null) {
            return null;
        }

        final Set<String> hs = new HashSet<>();

        for (int a = 0; a < participant.length; a++) {
            hs.add(participant2String(participant[a]));
        }

        return hs;
    }

    /**
     * Converts the specified {@link Participant} object to string
     *
     * @param p The {@link Participant} to convert
     * @return The string version of the {@link Participant} (Type and identifier)
     */
    protected static String participant2String(final Participant p) {
        final StringBuffer sb = new StringBuffer();
        sb.append("T" + p.getType());
        sb.append("ID" + p.getIdentifier());

        return sb.toString();
    }

    /**
     * Converts the specified {@link UserParticipant}'s array to a {@link Set} of strings
     *
     * @param users The {@link UserParticipant}'s array
     * @return A {@link Set} with the string representation of the specified {@link UserParticipant}'s array
     */
    protected static Set<String> users2String(final UserParticipant[] users) {
        if (users == null) {
            return null;
        }

        final Set<String> hs = new HashSet<>();

        for (int a = 0; a < users.length; a++) {
            hs.add(user2String(users[a]));
        }

        return hs;
    }

    /**
     * Converts the specified {@link UserParticipant} object to string
     *
     * @param user The {@link UserParticipant} to convert
     * @return The string version of the {@link UserParticipant}
     */
    protected static String user2String(final UserParticipant user) {
        final StringBuffer sb = new StringBuffer();
        sb.append("ID" + user.getIdentifier());
        sb.append("C" + user.getConfirm());

        return sb.toString();
    }
}
