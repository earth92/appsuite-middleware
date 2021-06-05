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

package com.openexchange.sessiond;

import java.util.LinkedList;
import java.util.List;
import com.openexchange.session.Session;



/**
 * A filter for sessions inspired by RFC 1960 and the use of this RFC by the OSGi framework for
 * filtering services. Filters use a prefix notation with parenthesis around filter expressions.
 * The string representation of a filter is defined by the following grammar:
 *
 * <pre>
 *   &lt;filter&gt; ::= '(' &lt;filtercomp&gt; ')'
 *   &lt;filtercomp&gt; ::= &lt;and&gt; | &lt;or&gt; | &lt;not&gt; | &lt;item&gt;
 *   &lt;and&gt; ::= '&amp;' &lt;filterlist&gt;
 *   &lt;or&gt; ::= '|' &lt;filterlist&gt;
 *   &lt;not&gt; ::= '!' &lt;filter&gt;
 *   &lt;filterlist&gt; ::= &lt;filter&gt; | &lt;filter&gt; &lt;filterlist&gt;
 *   &lt;item&gt; ::= &lt;simple&gt;
 *   &lt;simple&gt; ::= &lt;attr&gt; &lt;filtertype&gt; &lt;value&gt;
 *   &lt;filtertype&gt; ::= &lt;equal&gt;
 *   &lt;equal&gt; ::= '='
 * </pre>
 *
 * <code>&lt;attr&gt;</code> is a string representing a field of a session object or a key in the properties
 * of a session. Attribute names are case sensitive. <code>&lt;value&gt;</code> is a string representing the
 * value of a field or property. The constants of this class define the attribute names that are matched
 * against their according fields in the session objetcs. Be careful with white spaces, especially within
 * values. All characters between <code>&lt;filtertype&gt;</code> and ')' are matched as is.
 *
 * Examples:
 * <pre>
 *   &quot;(com.openexchange.example.SessionMarker=true)&quot;
 *   &quot;(!(&quot; + SessionFilter.CLIENT + &quot;=com.openexchange.ox.gui.dhtml))&quot;
 *   &quot;(&amp;(&quot; + SessionFilter.CONTEXT_ID + &quot;=123)(com.openexchange.example.CustomProperty=be61f8e5a3d64f43b68c2f43527a8704))&quot;
 * </pre>
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.1
 */
public class SessionFilter {

    /**
     * The attribute to filter by context ID
     */
    public static final String CONTEXT_ID = "contextId";

    /**
     * The attribute to filter by user ID
     */
    public static final String USER_ID = "userId";

    /**
     * The attribute to filter by session ID
     */
    public static final String SESSION_ID = "sessionId";

    /**
     * The attribute to filter by session secret
     */
    public static final String SECRET = "secret";

    /**
     * The attribute to filter by session hash
     */
    public static final String HASH = "hash";

    /**
     * The attribute to filter by auth ID
     */
    public static final String AUTH_ID = "authId";

    /**
     * The attribute to filter by client identifier
     */
    public static final String CLIENT = "client";

    /**
     * Creates a filter from specified expression.
     *
     * @param filterString The filter expression
     * @return The yielded filter
     * @throws IllegalArgumentException If filter expression is invalid
     */
    public static SessionFilter create(String filterString) throws IllegalArgumentException {
        Matcher matcher = new Parser(filterString).parse();
        return new SessionFilter(filterString, matcher);
    }

    /**
     * Creates a filter from specified secret.
     *
     * @param secret The session secret to filter by
     * @return The yielded filter
     */
    public static SessionFilter createSecretFilter(String secret) {
        String filterString = "(" + SessionFilter.SECRET + "=" + secret + ")";
        Matcher matcher = new Parser(filterString).parse();
        return new SessionFilter(filterString, matcher);
    }

    // -------------------------------------------------------------------------------------------------------------------- //

    private final String filterString;
    private final Matcher matcher;

    private SessionFilter(String filterString, Matcher matcher) {
        super();
        this.filterString = filterString;
        this.matcher = matcher;
    }

    /**
     * Checks if specified session applies to this session filter.
     *
     * @param session The session to test
     * @return <code>true</code> if applicable; otherwise <code>false</code>
     */
    public boolean apply(Session session) {
        return matcher.matches(new SessionMatchee(session));
    }

    /**
     * Returns the string representation of this filter that was initially parsed.
     */
    @Override
    public String toString() {
        return filterString;
    }

    static final class SessionMatchee implements Matchee {

        private final Session session;

        SessionMatchee(Session session) {
            super();
            this.session = session;
        }

        @Override
        public boolean matches(String attr, String value, FilterType type) {
            if (CONTEXT_ID.equals(attr)) {
                return type.isApplicable(Integer.toString(session.getContextId()), value);
            } else if (USER_ID.equals(attr)) {
                return type.isApplicable(Integer.toString(session.getUserId()), (value));
            } else if (SESSION_ID.equals(attr)) {
                String sessionID = session.getSessionID();
                if (sessionID == null) {
                    return false;
                }
                return type.isApplicable(sessionID, value);
            } else if (SECRET.equals(attr)) {
                String secret = session.getSecret();
                if (secret == null) {
                    return false;
                }
                return type.isApplicable(secret, value);
            } else if (HASH.equals(attr)) {
                String hash = session.getHash();
                if (hash == null) {
                    return false;
                }
                return type.isApplicable(hash, value);
            } else if (AUTH_ID.equals(attr)) {
                String authId = session.getAuthId();
                if (authId == null) {
                    return false;
                }
                return type.isApplicable(authId, value);
            } else if (CLIENT.equals(attr)) {
                String client = session.getClient();
                if (client == null) {
                    return false;
                }
                return type.isApplicable(client, value);
            }

            Object parameter = session.getParameter(attr);
            if (parameter != null && String.class.isAssignableFrom(parameter.getClass())) {
                return type.isApplicable((String) parameter, value);
            }

            return false;
        }

    }

    static final class Parser {

        private final String filterString;

        private final char[] filterChars;

        private int pos;

        Parser(String filterString) {
            super();
            this.filterString = filterString;
            filterChars = filterString.toCharArray();
            pos = 0;
        }

        Matcher parse() throws IllegalArgumentException {
            try {
                Matcher parseFilter = parseFilter();
                if (pos != filterChars.length) {
                    throw new IllegalArgumentException("Unexpected end of filter string '" + filterString + "'");
                }

                return parseFilter;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Unexpected end of filter string '" + filterString + "'");
            }
        }

        private Matcher parseFilter() {
            skipWhitespace();

            if ('(' != filterChars[pos++]) {
                throw new IllegalArgumentException("Missing '(' at position " + pos + " of '" + filterString + "'");
            }

            Matcher filterComp = parseFilterComp();
            skipWhitespace();
            if (')' != filterChars[pos++]) {
                throw new IllegalArgumentException("Missing ')' at position " + pos + " of '" + filterString + "'");
            }
            skipWhitespace();
            return filterComp;
        }

        private Matcher parseFilterComp() {
            skipWhitespace();

            switch (filterChars[pos]) {
                case '&':
                    pos++;
                    return new Junction(true, parseFilterList());
                case '|':
                    pos++;
                    return new Junction(false, parseFilterList());
                case '!':
                    pos++;
                    return new Not(parseFilter());
                default:
                    return parseSimple();
            }
        }

        private List<Matcher> parseFilterList() {
            skipWhitespace();

            List<Matcher> matchers = new LinkedList<Matcher>();
            while (pos < filterChars.length) {
                matchers.add(parseFilter());
                if (filterChars[pos] == ')') {
                    break;
                }
            }

            if (matchers.size() < 2) {
                throw new IllegalArgumentException("Unexpected filter expression: " + filterString + "'");
            }

            return matchers;
        }

        private Simple parseSimple() {
            skipWhitespace();

            StringBuilder attr = new StringBuilder();
            StringBuilder value = new StringBuilder();
            FilterType type = null;
            boolean parseValue = false;
            for (int length = filterChars.length; pos < length; pos++) {
                char c = filterChars[pos];
                if (c == '=') {
                    type = FilterType.EQUAL;
                    parseValue = true;
                } else if (c == ')') {
                    break;
                } else {
                    if (parseValue) {
                        value.append(c);
                    } else {
                        attr.append(c);
                    }
                }
            }

            if (attr.length() == 0) {
                throw new IllegalArgumentException("Missing attribute at position " + pos + " of '" + filterString + "'");
            }

            if (value.length() == 0) {
                throw new IllegalArgumentException("Missing value at position " + pos + " of '" + filterString + "'");
            }

            if (type == null) {
                throw new IllegalArgumentException("Missing filter type at position " + pos + " of '" + filterString + "'");
            }

            return new Simple(attr.toString(), value.toString(), type);
        }

        private void skipWhitespace() {
            while (pos < filterChars.length && Character.isWhitespace(filterChars[pos])) {
                pos++;
            }
        }
    }

    static interface Matcher {
        boolean matches(Matchee matchee);
    }

    static interface Matchee {
        boolean matches(String attr, String value, FilterType type);
    }

    static interface Applicator {
        boolean isApplicable(String str1, String str2);
    }

    static enum FilterType implements Applicator {
        EQUAL(new Applicator() {
            @Override
            public boolean isApplicable(String str1, String str2) {
                if (str1 == null) {
                    if (str2 == null) {
                        return true;
                    }

                    return false;
                }

                if (str2 == null) {
                    return false;
                }

                return str1.equals(str2);
            }
        });

        private final Applicator applicator;
        private FilterType(Applicator applicator) {
            this.applicator = applicator;
        }
        @Override
        public boolean isApplicable(String str1, String str2) {
            return applicator.isApplicable(str1, str2);
        }

    }

    static final class Not implements Matcher {

        private final Matcher matcher;

        Not(Matcher matcher) {
            super();
            this.matcher = matcher;
        }

        @Override
        public boolean matches(Matchee matchee) {
            return !matcher.matches(matchee);
        }

    }

    static final class Junction implements Matcher {

        private final List<Matcher> matchers;

        private final boolean and;

        Junction(boolean and, List<Matcher> matchers) {
            super();
            this.and = and;
            this.matchers = matchers;
        }

        @Override
        public boolean matches(Matchee matchee) {
            if (and) {
                for (Matcher matcher : matchers) {
                    if (!matcher.matches(matchee)) {
                        return false;
                    }
                }
                return true;
            }

            for (Matcher matcher : matchers) {
                if (matcher.matches(matchee)) {
                    return true;
                }
            }
            return false;
        }

    }

    static final class Simple implements Matcher {

        private final String attr;
        private final String value;
        private final FilterType type;

        Simple(String attr, String value, FilterType type) {
            super();
            this.attr = attr;
            this.value = value;
            this.type = type;
        }

        String getAttr() {
            return attr;
        }

        String getValue() {
            return value;
        }

        FilterType getType() {
            return type;
        }

        @Override
        public boolean matches(Matchee matchee) {
            return matchee.matches(attr, value, type);
        }

    }

}
