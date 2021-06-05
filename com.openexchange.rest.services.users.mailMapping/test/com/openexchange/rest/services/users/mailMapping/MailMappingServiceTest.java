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

package com.openexchange.rest.services.users.mailMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Collections;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import com.openexchange.context.ContextService;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.mailmapping.MailResolver;
import com.openexchange.mailmapping.ResolvedMail;
import com.openexchange.server.MockingServiceLookup;
import com.openexchange.user.User;
import com.openexchange.user.UserService;

/**
 * {@link MailMappingServiceTest}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class MailMappingServiceTest {

    private MailMappingRESTService service = null;
    private MailResolver resolver = null;
    private MockingServiceLookup services = null;

    @Before
    public void setup() throws OXException {
        services = new MockingServiceLookup();
        service = new MailMappingRESTService(services);

        mockContext();
    }

    private void mockContext() throws OXException {
        ContextService contexts = services.mock(ContextService.class);

        Context ctx = mock(Context.class);
        when(contexts.getContext(42)).thenReturn(ctx);

        User charlie = mock(User.class);
        when(charlie.getPreferredLanguage()).thenReturn("en_US");
        when(charlie.getDisplayName()).thenReturn("Charlie");

        User linus = mock(User.class);
        when(linus.getPreferredLanguage()).thenReturn("de_DE");
        when(linus.getPreferredLanguage()).thenReturn("Linus");

        UserService users = services.mock(UserService.class);
        when(users.getUser(12, ctx)).thenReturn(charlie);
        when(users.getUser(13, ctx)).thenReturn(linus);

        resolver = services.mock(MailResolver.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveMail() throws OXException, JSONException {
        when(resolver.resolve("charlie@test.invalid")).thenReturn(new ResolvedMail(12, 42));

        MultivaluedMap<String, String> mmap = mock(MultivaluedMap.class);

        PathSegment segment = mock(PathSegment.class);
        when(segment.getPath()).thenReturn("charlie@test.invalid");

        when(segment.getMatrixParameters()).thenReturn(mmap);
        when(mmap.keySet()).thenReturn(Collections.<String> emptySet());

        JSONObject resolved = service.resolve(segment);
        assertEquals(1, resolved.length());

        JSONObject resolvedEntry = resolved.getJSONObject("charlie@test.invalid");
        assertEquals(I(12), resolvedEntry.get("uid"));
        assertEquals(I(42), resolvedEntry.get("cid"));

        JSONObject user = resolvedEntry.getJSONObject("user");
        assertEquals("en_US", user.get("language"));
        assertEquals("Charlie", user.get("displayName"));

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveMultipleMails() throws OXException, JSONException {
        when(resolver.resolve("charlie@test.invalid")).thenReturn(new ResolvedMail(12, 42));
        when(resolver.resolve("linus@test.invalid")).thenReturn(new ResolvedMail(13, 42));

        MultivaluedMap<String, String> mmap = mock(MultivaluedMap.class);

        PathSegment segment = mock(PathSegment.class);
        when(segment.getPath()).thenReturn("charlie@test.invalid");
        when(segment.getMatrixParameters()).thenReturn(mmap);
        when(mmap.keySet()).thenReturn(Collections.singleton("linus@test.invalid"));

        JSONObject resolved = service.resolve(segment);
        assertEquals(2, resolved.length());

        JSONObject charlie = resolved.getJSONObject("charlie@test.invalid");
        assertEquals(I(12), charlie.get("uid"));
        assertEquals(I(42), charlie.get("cid"));

        JSONObject linus = resolved.getJSONObject("linus@test.invalid");
        assertEquals(I(13), linus.get("uid"));
        assertEquals(I(42), linus.get("cid"));

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResolveUnknownMail() throws OXException {
        MultivaluedMap<String, String> mmap = mock(MultivaluedMap.class);

        PathSegment segment = mock(PathSegment.class);
        when(segment.getPath()).thenReturn("charlie@test.invalid");
        when(segment.getMatrixParameters()).thenReturn(mmap);

        JSONObject resolved = service.resolve(segment);
        assertTrue(resolved.isEmpty());
    }

    private static Integer I(int i) {
        return Integer.valueOf(i);
    }
}
