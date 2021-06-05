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

package com.openexchange.test.fixtures;

import java.util.HashMap;
import java.util.Map;
import com.openexchange.exception.OXException;
import com.openexchange.mailaccount.internal.CustomMailAccount;
import com.openexchange.test.fixtures.transformators.BooleanTransformator;

/**
 * @author Martin Braun <martin.braun@open-xchange.com>
 */
public class MultiMailFixtureFactory implements FixtureFactory<CustomMailAccount> {

    private final FixtureLoader fixtureLoader;

    public MultiMailFixtureFactory(FixtureLoader fixtureLoader) {
        super();
        this.fixtureLoader = fixtureLoader;
    }

    @Override
    public Fixtures<CustomMailAccount> createFixture(final Map<String, Map<String, String>> entries) {
        return new MultiMailFixtures(entries, fixtureLoader);
    }

    private class MultiMailFixtures extends DefaultFixtures<CustomMailAccount> implements Fixtures<CustomMailAccount> {

        private final Map<String, Map<String, String>> entries;
        private final Map<String, Fixture<CustomMailAccount>> mailaccounts = new HashMap<String, Fixture<CustomMailAccount>>();

        public MultiMailFixtures(final Map<String, Map<String, String>> entries, FixtureLoader fixtureLoader) {
            super(CustomMailAccount.class, entries, fixtureLoader);
            this.entries = entries;

            addTransformator(new BooleanTransformator(), "unified_inbox_enabled");
            addTransformator(new BooleanTransformator(), "mail_secure");
            addTransformator(new BooleanTransformator(), "transport_secure");
        }

        @Override
        public Fixture<CustomMailAccount> getEntry(final String entryName) throws OXException {
            if (mailaccounts.containsKey(entryName)) {
                return mailaccounts.get(entryName);
            }
            final Map<String, String> values = entries.get(entryName);
            if (null == values) {
                throw new FixtureException("Entry with name " + entryName + " not found");
            }

            final CustomMailAccount customMailAccount = new CustomMailAccount(0);

            apply(customMailAccount, values);

            final Fixture<CustomMailAccount> fixture = new Fixture<CustomMailAccount>(customMailAccount, values.keySet().toArray(new String[values.size()]), values);

            mailaccounts.put(entryName, fixture);
            return fixture;
        }

    }
}
