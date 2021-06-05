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

package com.openexchange.jsieve.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.jsieve.SieveException;
import org.apache.jsieve.TagArgument;
import com.openexchange.jsieve.commands.test.ITestCommand;

/**
 * A {@link TestCommand} is used as part of a control command. It is used to
 * specify whether or not the block of code given to the control command is
 * executed.
 *
 * General supported tests: "address", "allof", "anyof", "exists", "false",
 * "header", "not", "size", and "true"
 *
 * Need require "envelope"
 * <ul>
 * <li><code>address [ADDRESS-PART] [COMPARATOR] [MATCH-TYPE] &lt;header-list: string-list&gt; &lt;key-list: string-list&gt;</code></li>
 * <li><code>envelope [COMPARATOR] [ADDRESS-PART] [MATCH-TYPE] &lt;envelope-part: string-list&gt; &lt;key-list: string-list&gt;</code></li>
 * <li><code>[ADDRESS-PART] = ":localpart" / ":domain" / ":all"</code></li>
 * <li><code>exists &lt;header-names: string-list&gt;</code></li>
 * <li><code>false</code></li>
 * <li><code>true</code></li>
 * <li><code>not &lt;test&gt;</code></li>
 * <li><code>size &lt;":over" / ":under"&gt; &lt;limit: number&gt;</code></li>
 * <li><code>header [COMPARATOR] [MATCH-TYPE] &lt;header-names: string-list&gt; &lt;key-list: string-list&gt;</code></li>
 * <li><code>body [COMPARATOR] [MATCH-TYPE] [BODY-TRANSFORM] &lt;key-list: string-list&gt;</code></li>
 * <li><code>allof &lt;tests: test-list&gt;</code> (logical AND)</li>
 * <li><code>anyof &lt;tests: test-list&gt; </code> (logical OR)</code></li>
 * <li><code>Match-types are ":is", ":contains", and ":matches"</code></li>
 * </ul>
 *
 * @author <a href="mailto:dennis.sieben@open-xchange.com">Dennis Sieben</a>
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class TestCommand extends Command {

    /**
     * {@link Commands} - Supported sieve test commands
     *
     * <p>Order of arguments:</p>
     * <ul>
     * <li>command name</li>
     * <li>number of minimum arguments</li>
     * <li>number of maximum arguments</li>
     * <li>part (either address part or body part elements)</li>
     * <li>comparators</li>
     * <li>match types</li>
     * <li>JSON mappings to sieve match types</li>
     * <li>required plugins</li>
     * <li>other arguments</li>
     * </ul>
     */
    public enum Commands implements ITestCommand {

        /**
         * <p>The "address" test matches Internet addresses in structured headers that
         * contain addresses. The type of match is specified by the optional match argument,
         * which defaults to ":is" if not specified</p>
         * <code>address [ADDRESS-PART] [COMPARATOR] [MATCH-TYPE] &lt;header-list: string-list&gt; &lt;key-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.1">RFC-5228: Test address</a></p>
         */
        ADDRESS("address", 2, Integer.MAX_VALUE, addressParts(), standardComparators(), standardMatchTypes(), standardJSONMatchTypes(), null, null),
        /**
         * <p>The "envelope" test is true if the specified part of the [SMTP] (or equivalent)
         * envelope matches the specified key. The type of match is specified by the optional
         * match argument, which defaults to ":is" if not specified</p>
         * <code>envelope [COMPARATOR] [ADDRESS-PART] [MATCH-TYPE] &lt;envelope-part: string-list&gt; &lt;key-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.4">RFC-5228: Test envelope</a></p>
         */
        ENVELOPE("envelope", 2, Integer.MAX_VALUE, addressParts(), standardComparators(), standardMatchTypes(), standardJSONMatchTypes(), Collections.singletonList("envelope"), null),
        /**
         * <p>The "exists" test is true if the headers listed in the header-names argument exist within the message. All of the headers must exist or the test is false.
         * <code>exists &lt;header-names: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.5">RFC-5228: Test exists</a></p>
         */
        EXISTS("exists", 1, 1, null, null, null, null, null, null),
        /**
         * <p>The "false" test always evaluates to false.</p>
         * <code>false</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.6">RFC-5228: Test false</a></p>
         */
        FALSE("false", 0, 0, null, null, null, null, null, null),
        /**
         * <p>The "true" test always evaluates to true.</p>
         * <code>true</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.10">RFC-5228: Test true</a></p>
         */
        TRUE("true", 0, 0, null, null, null, null, null, null),
        /**
         * <p>The "not" test takes some other test as an argument, and yields the opposite result.</p>
         * <code>not &lt;test&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.8">RFC-5228: Test not</a></p>
         */
        NOT("not", 0, 0, null, null, null, null, null, null),
        /**
         * <p>The "size" test deals with the size of a message.</p>
         * <code>size &lt;":over" / ":under"&gt; &lt;limit: number&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.9">RFC-5228: Test size</a></p>
         */
        SIZE("size", 1, 1, null, null, matchTypeSize(), standardJSONSizeMatchTypes(), null, null),
        /**
         * <p>The "header" test evaluates to true if the value of any of the named
         * headers, ignoring leading and trailing whitespace, matches any key. The
         * type of match is specified by the optional match argument, which defaults
         * to ":is" if not specified</p>
         * <code>header [COMPARATOR] [MATCH-TYPE] &lt;header-names: string-list&gt; &lt;key-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.7">RFC-5228: Test header</a></p>
         */
        HEADER("header", 2, Integer.MAX_VALUE, standardAddressPart(), standardComparators(), standardMatchTypes(), headerJSONMatchTypes(), null, null),
        /**
         * <p>The "allof" test performs a logical AND on the tests supplied to it.</p>
         * <code>allof &lt;tests: test-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.2">RFC-5228: Test allof</a></p>
         */
        ALLOF("allof", 0, 0, null, null, null, null, null, null),
        /**
         * <p>The "anyof" test performs a logical OR on the tests supplied to it.</p>
         * <code>anyof &lt;tests: test-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5228#section-5.3">RFC-5228: Test anyof</a></p>
         */
        ANYOF("anyof", 0, 0, null, null, null, null, null, null),
        /**
         * <p>The body test matches content in the body of an email message, that
         * is, anything following the first empty line after the header. </p>
         * <code>body [COMPARATOR] [MATCH-TYPE] [BODY-TRANSFORM] &lt;key-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5173#section-4">RFC-5173: Test body</a></p>
         */
        BODY("body", 1, 1, standardBodyPart(), null, standardMatchTypes(), standardJSONMatchTypes(), Collections.singletonList("body"), null),
        /**
         * <p>The date test matches date/time information derived from headers
         * containing [RFC2822] date-time values. The date/time information is
         * extracted from the header, shifted to the specified time zone, and
         * the value of the given date-part is determined. The test returns
         * true if the resulting string matches any of the strings specified in
         * the key-list, as controlled by the comparator and match keywords.</p>
         * <code>date [&lt;":zone" &lt;time-zone: string&gt;&gt; / ":originalzone"] [COMPARATOR] [MATCH-TYPE] &lt;header-name: string&gt; &lt;date-part: string&gt; &lt;key-list: string-list&gt;
         * <p><a href="https://tools.ietf.org/html/rfc5260#section-4">RFC-5260: Date Test</a></p>
         *
         */
        DATE("date", 2, Integer.MAX_VALUE, null, null, dateMatchTypes(), dateJSONMatchTypes(), Collections.singletonList("date"), dateOtherArguments()),
        /**
         * <p>The currentdate test is similar to the date test, except that it
         * operates on the current date/time rather than a value extracted from
         * the message header.</p>
         * <code>currentdate [":zone" &lt;time-zone: string&gt;] [COMPARATOR] [MATCH-TYPE] &lt;date-part: string&gt; ;&ltkey-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5260#section-5">RFC-5260: Test currentdate</a></p>
         */
        CURRENTDATE("currentdate", 2, Integer.MAX_VALUE, null, null, dateMatchTypes(), dateJSONMatchTypes(), Collections.singletonList("date"), currentDateOtherArguments()),
        /**
         * <p>The hasflag test evaluates to true if any of the variables matches any flag name.</p>
         * <code>hasflag [MATCH-TYPE] [COMPARATOR] [&lt;variable-list: string-list&gt;] &lt;list-of-flags: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5232#section-4">RFC-5232: Test hasflag</a></p>
         */
        HASFLAG("hasflag", 1, Integer.MAX_VALUE, null, standardComparators(), standardMatchTypes(), standardJSONMatchTypes(), Arrays.asList("imap4flags", "imapflags"), null),
        /**
         * <p> The "string" test evaluates to true if any of the source strings matches any key. The type of match defaults to ":is".</p>
         * <code>string [MATCH-TYPE] [COMPARATOR] &lt;source: string-list&gt; &lt;key-list: string-list&gt;</code>
         * <p><a href="https://tools.ietf.org/html/rfc5229#section-5">RFC-5229: Test string</a></p>
         */
        STRING("string", 2, Integer.MAX_VALUE, null, standardComparators(), standardMatchTypes(), standardJSONMatchTypes(), Arrays.asList("variables"), null);


        ////////////////////////////////// MATCH TYPES /////////////////////////////////////////////////

        /**
         * <p>Specifies the match types ':over' and ':under' as described
         * in <a href="https://tools.ietf.org/html/rfc5228#section-5.9">RFC-5228: Test size</a>.</p>
         *
         * @return A {@link Map} with the size match types
         */
        private static Map<String, String> matchTypeSize() {
            final Map<String, String> match_type_size = new HashMap<String, String>(2);
            match_type_size.put(MatchType.over.getArgumentName(), MatchType.over.getRequire());
            match_type_size.put(MatchType.under.getArgumentName(), MatchType.under.getRequire());
            return match_type_size;
        }

        /**
         * <p>Specifies the addresspart arguments ':localpart', ':domain', ':all' as described
         * in <a href="href=https://tools.ietf.org/html/rfc5228#section-2.7.4">RFC-5228</a>.</p>
         *
         * @return A {@link Map} with the standard addressparts
         */
        private static Map<String, String> standardAddressPart() {
            final Map<String, String> standard_address_part = new HashMap<String, String>(3);
            standard_address_part.put(AddressParts.localpart.getSieveArgument(), AddressParts.localpart.getNeededCapabilities());
            standard_address_part.put(AddressParts.domain.getSieveArgument(), AddressParts.domain.getNeededCapabilities());
            standard_address_part.put(AddressParts.all.getSieveArgument(), AddressParts.all.getNeededCapabilities());
            // Add further extensions here...
            return standard_address_part;
        }

        /**
         * <p>Specifies the match types ':content' and ':text' as described
         * in <a https://tools.ietf.org/html/rfc5173#section-5">RFC-5173: Body transform</a>.</p>
         *
         * @return A {@link Map} with the body match types
         */
        private static Map<String, String> standardBodyPart() {
            final Map<String, String> standard_address_part = new HashMap<String, String>(3);
            //standard_address_part.put(":raw", "");
            standard_address_part.put(":content", "");
            standard_address_part.put(":text", "");
            // Add further extensions here...
            return standard_address_part;
        }

        /**
         * <p>Specifies the addressparts ':user' and ':detail' as described
         * in <a href="https://tools.ietf.org/html/rfc5233#section-4">RFC-5233: Subaddress Comparisons</a> in addition to the
         * standard addressparts (see #{@link TestCommand.Commands#standardAddressPart())}.</p>
         *
         * @return A {@link Map} with the subaddress addressparts
         */
        private static Map<String, String> addressParts() {
            final Map<String, String> standard_address_parts = new HashMap<>(5);
            for (AddressParts part : AddressParts.values()) {
                standard_address_parts.put(part.getSieveArgument(), part.getNeededCapabilities());
            }
            return standard_address_parts;
        }

        /**
         * <p>Specifies the standard match types ':is', ':contains', ':matches' as described
         * in <a href=https://tools.ietf.org/html/rfc5228#section-2.7.1">RFC-5228</a>.</p>
         *
         * @return A {@link Map} with tthe standard match types
         */
        private static Map<String, String> standardMatchTypes() {
            final Map<String, String> standard_match_types = new HashMap<String, String>(4);
            standard_match_types.put(MatchType.is.getArgumentName(), MatchType.is.getRequire());
            standard_match_types.put(MatchType.contains.getArgumentName(), MatchType.contains.getRequire());
            standard_match_types.put(MatchType.matches.getArgumentName(), MatchType.matches.getRequire());
            // Add further extensions here... and don't forget to raise the
            // initial number
            standard_match_types.put(MatchType.regex.getArgumentName(), MatchType.regex.getRequire());
            return standard_match_types;
        }

        /**
         * The match types that are applicable to dates
         *
         * @return A {@link Map} with the match types
         */
        private static Map<String, String> dateMatchTypes() {
            final Map<String, String> standard_match_types = new HashMap<String, String>(2);
            standard_match_types.put(MatchType.is.getArgumentName(), MatchType.is.getRequire());
            standard_match_types.put(MatchType.value.getArgumentName(), MatchType.value.getRequire());
            return standard_match_types;
        }

        /**
         * Specifies the ':zone' and ':originalzone' argument as described in
         * <a href="https://tools.ietf.org/html/rfc5260#section-4.1">RFC-5260: Zone and Originalzone arguments</a>
         *
         * @return A hashtable with the argument
         */
        private static Map<String, String> dateOtherArguments() {
            final Map<String, String> arguments = new HashMap<String, String>(2);
            arguments.put(":zone", "");
            arguments.put(":originalzone", "");
            return arguments;
        }

        /**
         * Specifies the ':zone' argument as described in
         * <a href="https://tools.ietf.org/html/rfc5260#section-4.1">RFC-5260: Zone and Originalzone arguments</a>
         *
         * @return A hashtable with the argument
         */
        private static Map<String, String> currentDateOtherArguments() {
            final Map<String, String> arguments = new HashMap<String, String>(1);
            arguments.put(":zone", "");
            return arguments;
        }

        ///////////////////////////////////// JSON MAPPINGS ////////////////////////////////////////////

        /**
         * The JSON mapping of the size match types ({@link #matchTypeSize()})
         *
         * @return A {@link List} with the mappings
         */
        private static List<JSONMatchType> standardJSONSizeMatchTypes() {
            final List<JSONMatchType> sizeMatchTypes = Collections.synchronizedList(new ArrayList<JSONMatchType>(4));
            sizeMatchTypes.add(new JSONMatchType(MatchType.over.name(), MatchType.over.getRequire(), 0));
            sizeMatchTypes.add(new JSONMatchType(MatchType.under.name(), MatchType.under.getRequire(), 0));

            // add not matchers
            sizeMatchTypes.add(new JSONMatchType(MatchType.over.getNotName(), MatchType.over.getRequire(), 2));
            sizeMatchTypes.add(new JSONMatchType(MatchType.under.getNotName(), MatchType.under.getRequire(), 2));

            return sizeMatchTypes;
        }

        /**
         * The JSON mappings for the standard match types ({@link #standardMatchTypes()})
         *
         * @return A {@link List} with the mappings
         */
        private static List<JSONMatchType> standardJSONMatchTypes() {
            final List<JSONMatchType> standard_match_types = Collections.synchronizedList(new ArrayList<JSONMatchType>(12));

            // add normal matcher
            standard_match_types.add(new JSONMatchType(MatchType.regex.name(), MatchType.regex.getRequire(), 0));
            standard_match_types.add(new JSONMatchType(MatchType.is.name(), MatchType.is.getRequire(), 0));
            standard_match_types.add(new JSONMatchType(MatchType.contains.name(), MatchType.contains.getRequire(), 0));
            standard_match_types.add(new JSONMatchType(MatchType.matches.name(), MatchType.matches.getRequire(), 0));
            standard_match_types.add(new JSONMatchType(MatchType.startswith.name(), MatchType.startswith.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.endswith.name(), MatchType.endswith.getRequire(), 2));

            // add not matchers
            standard_match_types.add(new JSONMatchType(MatchType.regex.getNotName(), MatchType.regex.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.is.getNotName(), MatchType.is.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.contains.getNotName(), MatchType.contains.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.matches.getNotName(), MatchType.matches.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.startswith.getNotName(), MatchType.startswith.getRequire(), 2));
            standard_match_types.add(new JSONMatchType(MatchType.endswith.getNotName(), MatchType.endswith.getRequire(), 2));

            return standard_match_types;
        }

        /**
         * The JSON mappings for the header match types
         *
         * @return A {@link List} with mappings
         */
        private static List<JSONMatchType> headerJSONMatchTypes() {
            final List<JSONMatchType> headerMatchTypes = Collections.synchronizedList(new ArrayList<JSONMatchType>(14));
            headerMatchTypes.addAll(standardJSONMatchTypes());

            //add normal matcher
            headerMatchTypes.add(new JSONMatchType(MatchType.exists.name(), MatchType.exists.getRequire(), 2));
            // Add not matcher
            headerMatchTypes.add(new JSONMatchType(MatchType.exists.getNotName(), MatchType.exists.getRequire(), 2));

            return headerMatchTypes;
        }

        /**
         * The JSON mappings for dates
         *
         * @return A {@link List} with the mappings
         */
        private static List<JSONMatchType> dateJSONMatchTypes() {
            final List<JSONMatchType> dateMatchTypes = Collections.synchronizedList(new ArrayList<JSONMatchType>(14));
            // add normal matcher
            dateMatchTypes.add(new JSONMatchType(MatchType.is.name(), MatchType.is.getRequire(), 0));
            dateMatchTypes.add(new JSONMatchType(MatchType.ge.name(), MatchType.ge.getRequire(), 0));
            dateMatchTypes.add(new JSONMatchType(MatchType.le.name(), MatchType.le.getRequire(), 0));

            // add not matchers
            dateMatchTypes.add(new JSONMatchType(MatchType.is.getNotName(), MatchType.is.getRequire(), 2));
            dateMatchTypes.add(new JSONMatchType(MatchType.ge.getNotName(), MatchType.ge.getRequire(), 2));
            dateMatchTypes.add(new JSONMatchType(MatchType.le.getNotName(), MatchType.le.getRequire(), 2));

            return dateMatchTypes;
        }

        ///////////////////////////////////// COMPARATORS ///////////////////////////////////////////////////

        /**
         * Specifies the standard comparators used for matching.
         *
         * @return A {@link Map} with the comparators
         */
        private static Map<String, String> standardComparators() {
            final Map<String, String> standard_comparators = new HashMap<String, String>(2);
            standard_comparators.put("i;ascii-casemap", "");
            standard_comparators.put("i;octet", "");
            // Add further extensions to comparator here
            // e.g. standard_comparators.put("\"i;test\"",
            // "\"comparator-test\"");
            return standard_comparators;
        }

        /**
         * Defines if this command can take a address argument or not
         */
        private final Map<String, String> address;

        /**
         * The number of arguments which this command takes
         */
        private final int numberOfArguments;

        /**
         * Defines how many arguments this test can have at max
         */
        private final int maxNumberOfArguments;

        /**
         * The name of the command
         */
        private final String commandName;

        /**
         * Defines if this command can take a comparator argument or not
         */
        private final Map<String, String> comparator;

        /**
         * Defines if this command can take a match-type argument or not
         */
        private final Map<String, String> matchTypes;

        /**
         * Defines additional allowed arguments
         */
        private final Map<String, String> otherArguments;

        /**
         * Needed for the resolution of the configuration parameters for JSON
         */
        private final List<JSONMatchType> jsonMatchTypes;

        /**
         * Defines if this command needs a require or not
         */
        private final List<String> required;

        /**
         * Initializes a new {@link Commands}.
         *
         * @param commandName The command's name
         * @param numberOfArguments The minimum number of arguments
         * @param maxNumberOfArguments The maximum number of arguments
         * @param address The part match types
         * @param comparator The comparators
         * @param matchTypes The match types
         * @param jsonMatchTypes The json mappings of the match types
         * @param required The 'require'
         * @param otherArguments Other optional arguments
         */
        Commands(final String commandName, final int numberOfArguments, int maxNumberOfArguments, final Map<String, String> address, final Map<String, String> comparator, final Map<String, String> matchTypes, List<JSONMatchType> jsonMatchTypes,
            final List<String> required, final Map<String, String> otherArguments) {
            this.commandName = commandName;
            this.numberOfArguments = numberOfArguments;
            this.maxNumberOfArguments = maxNumberOfArguments;
            this.address = address;
            this.comparator = comparator;
            this.matchTypes = matchTypes;
            this.jsonMatchTypes = jsonMatchTypes;
            this.required = required;
            this.otherArguments = otherArguments;
        }

        @Override
        public final int getNumberOfArguments() {
            return numberOfArguments;
        }

        @Override
        public final int getMaxNumberOfArguments() {
            return maxNumberOfArguments;
        }

        @Override
        public final String getCommandName() {
            return commandName;
        }

        @Override
        public final Map<String, String> getAddress() {
            return null == address ? null : Collections.unmodifiableMap(address);
        }

        @Override
        public final Map<String, String> getOtherArguments() {
            return null == otherArguments ? null : Collections.unmodifiableMap(otherArguments);
        }

        @Override
        public final Map<String, String> getComparator() {
            return null == comparator ? null : Collections.unmodifiableMap(comparator);
        }

        @Override
        public final List<String> getRequired() {
            return null == required ? null : Collections.unmodifiableList(required);
        }

        @Override
        public final Map<String, String> getMatchTypes() {
            return null == matchTypes ? null : Collections.unmodifiableMap(matchTypes);
        }

        @Override
        public List<JSONMatchType> getJsonMatchTypes() {
            return null == jsonMatchTypes ? null : Collections.unmodifiableList(jsonMatchTypes);
        }
    }

    private ITestCommand command;

    private final List<String> tagArguments;

    private final List<Object> arguments;

    private final List<TestCommand> testCommands;

    private final List<String> optRequired = new ArrayList<>();

    private final boolean useIndexComparator = false;
    private final int indexOfComparator = -1;

    /**
     * Initialises a new {@link TestCommand}.
     */
    public TestCommand() {
        super();
        this.testCommands = new ArrayList<TestCommand>();
        this.arguments = new ArrayList<Object>();
        this.tagArguments = new ArrayList<String>();
    }

    /**
     * Initialises a new {@link TestCommand}.
     *
     * @param command The {@link ITestCommand}
     * @param arguments A {@link List} with arguments
     * @param testcommands A {@link List} with {@link TestCommand}s
     * @throws SieveException If the command structure is invalid
     */
    public TestCommand(final ITestCommand command, final List<Object> arguments, final List<TestCommand> testcommands) throws SieveException {
        this.command = command;
        this.tagArguments = new ArrayList<String>();
        this.arguments = arguments;
        for (final Object arg : this.arguments) {
            if (arg instanceof TagArgument) {
                final TagArgument tagarg = (TagArgument) arg;
                this.tagArguments.add(tagarg.getTag());
            }
        }
        this.testCommands = testcommands;
        checkCommand();
    }

    /**
     * Checks the {@link TestCommand} for validity
     *
     * @throws SieveException If the command structure is invalid
     */
    private void checkCommand() throws SieveException {
        if (null != this.tagArguments) {
            final ArrayList<String> tagArray = new ArrayList<String>(this.tagArguments);
            final Map<String, String> matchTypes = this.command.getMatchTypes();
            if (null != matchTypes) {
                tagArray.removeAll(matchTypes.keySet());
            }
            final Map<String, String> address = this.command.getAddress();
            if (null != address) {
                tagArray.removeAll(address.keySet());
            }
            if (tagArray.contains(":comparator")) {
                throw new SieveException("Sieve comparators aren't supported by this implementation");
            }

            final Map<String, String> otherArguments = this.command.getOtherArguments();
            if (null != otherArguments) {
                tagArray.removeAll(otherArguments.keySet());
            }

            if (!tagArray.isEmpty()) {
                throw new SieveException("One of the tagArguments: " + tagArray + " is not valid for " + this.command.getCommandName());
            }

            if (null != this.arguments && this.command.getNumberOfArguments() >= 0) {
                final int realArguments = this.arguments.size() - this.tagArguments.size();
                final int minArguments = this.command.getNumberOfArguments() + (useIndexComparator ? 1 : 0);
                final int maxArguments = this.command.getMaxNumberOfArguments();
                if (realArguments < minArguments || realArguments > maxArguments) {
                    throw new SieveException("The number of arguments (" + realArguments + ") for " + this.command.getCommandName() + " is not valid.");
                }
            }
        }

        // Add test for testcommands here only anyof and allof are allowed to
        // take further tests
    }

    /**
     * Returns the {@link ITestCommand}
     *
     * @return the {@link ITestCommand}
     */
    public final ITestCommand getCommand() {
        return command;
    }

    /**
     * Sets the {@link TestCommand}
     *
     * @param command the command to set
     */
    public final void setCommand(final Commands command) {
        this.command = command;
    }

    /**
     * Returns a {@link List} with all the tag arguments
     *
     * @return a {@link List} with all the tag arguments
     */
    public final List<String> getTagArguments() {
        return tagArguments;
    }

    /**
     * Adds a tag argument
     *
     * @param o The tag argument to add
     * @return <code>true</code if the argument was added to the {@link List}; false otherwise
     * @see java.util.List#add(java.lang.Object)
     */
    public boolean addTagArguments(final String o) {
        return tagArguments.add(o);
    }

    /**
     * Returns a {@link List} with all the arguments
     *
     * @return a {@link List} with all the arguments
     */
    public final List<Object> getArguments() {
        return arguments;
    }

    /**
     * Retrieves the position of an argument
     *
     * @param arg The argument
     * @return The position or <code>-1</code>
     */
    public int getArgumentPosition(Object arg) {
        for (int x = 0; x < arguments.size(); x++) {
            if (arg.equals(arguments.get(x))) {
                return x;
            }
        }
        return -1;
    }

    /**
     * This method returns the match type of this command
     *
     * @return The match type or <code>null</code>
     */
    public final String getMatchType() {
        Map<String, String> matchTypes = this.command.getMatchTypes();
        if (matchTypes == null) {
            return null;
        }

        List<String> arrayList = new ArrayList<String>(matchTypes.keySet());
        arrayList.retainAll(this.tagArguments);
        return 1 == arrayList.size() ? arrayList.get(0) : null;
    }

    /**
     * This method returns the address part of this command
     *
     * @return The address part or <code>null</code>
     */
    public final String getAddressPart() {
        Map<String, String> address = this.command.getAddress();
        if (null == address) {
            return null;
        }

        List<String> arrayList = new ArrayList<String>(address.keySet());
        arrayList.retainAll(this.tagArguments);
        return 1 == arrayList.size() ? arrayList.get(0) : null;
    }

    /**
     * Returns a {@link List} with all the {@link TestCommand}s
     *
     * @return a {@link List} with all the {@link TestCommand}s
     */
    public final List<TestCommand> getTestCommands() {
        return testCommands;
    }

    /**
     * Removes the specified {@link TestCommand}
     *
     * @param command The {@link TestCommand} to remove
     */
    public final void removeTestCommand(TestCommand command) {
        this.testCommands.remove(command);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ": " + this.command.getCommandName() + " : " + this.tagArguments + " : " + this.arguments + " : " + this.testCommands;
    }

    @Override
    public HashSet<String> getRequired() {
        final HashSet<String> retval = new HashSet<String>();
        final List<String> required = this.command.getRequired();
        if (null != required && !required.isEmpty()) {
            retval.addAll(required);
        }
        // Here we add require for the comparator rule if there are any
        if (useIndexComparator) {
            final ArrayList<String> object = (ArrayList<String>) this.arguments.get(indexOfComparator);
            final String string = this.command.getComparator().get(object.get(0));
            if (null != string && !string.equals("")) {
                retval.add(string);
            }
        }
        for (final TestCommand command : this.getTestCommands()) {
            retval.addAll(command.getRequired());
        }
        for (final String text : this.tagArguments) {
            Map<String, String> matchTypes = this.command.getMatchTypes();
            String string = null == matchTypes ? null : matchTypes.get(text);
            if (null != string && (0 != string.length())) {
                retval.add(string);
            } else {
                Map<String, String> address = this.command.getAddress();
                string = null == address ? null : address.get(text);
                if (null != string && (0 != string.length())) {
                    retval.add(string);
                }
            }
        }

        retval.addAll(optRequired);

        return retval;
    }

    @Override
    public void addOptionalRequired(String required) {
        optRequired.add(required);
    }
}
