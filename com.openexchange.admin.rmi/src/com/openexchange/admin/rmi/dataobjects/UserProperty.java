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

package com.openexchange.admin.rmi.dataobjects;

import java.io.Serializable;
import java.util.Map;
import com.google.common.collect.ImmutableMap;

/**
 * Class representing one configuration property for a user
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.8.0
 */
public class UserProperty implements Serializable {

    private static final long serialVersionUID = 1751457900331133343L;

    private final String scope;
    private final String name;
    private final String value;
    private final Map<String, String> metadata;
    private final boolean sysEnvVariable;

    /**
     *
     * Initializes a new {@link UserProperty}.
     *
     * @param scope The scope
     * @param name The name of the property
     * @param value The value of the property
     * @param sysEnvVariable <code>true</code> to signal that a server-scoped property's value originates from a system environment variable; otherwise <code>false</code>
     */
    public UserProperty(String scope, String name, String value, boolean sysEnvVariable) {
        this(scope, name, value, ImmutableMap.of(), sysEnvVariable);
    }

    /**
     * Initializes a new {@link UserProperty}.
     *
     * @param scope The scope
     * @param name The name
     * @param value The value
     * @param metadata The metadata
     * @param sysEnvVariable <code>true</code> to signal that a server-scoped property's value originates from a system environment variable; otherwise <code>false</code>
     */
    public UserProperty(String scope, String name, String value, Map<String, String> metadata, boolean sysEnvVariable) {
        this.scope = scope;
        this.name = name;
        this.value = value;
        this.metadata = metadata;
        this.sysEnvVariable = sysEnvVariable;
    }

    /**
     * Checks if server-scoped property value originates from a system environment variable.
     *
     * @return <code>true</code> if value originates from a system environment variable; otherwise <code>false</code>
     */
    public boolean isSysEnvVariable() {
        return sysEnvVariable;
    }

    /**
     * Gets the scope
     *
     * @return The scope
     */
    public String getScope() {
        return scope;
    }

    /**
     * Gets the name
     *
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the value
     *
     * @return The value
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the metadata
     *
     * @return The metadata
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns the state in pattern: "property-name: property-value; Scope: property-scope"<br>
     * <br>
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(": ").append(value).append("; Scope: ").append(scope);
        if (false == metadata.isEmpty()) {
            builder.append("; Metadata: ").append(metadata);
        }
        builder.append("; Env-Variable: ").append(sysEnvVariable);
        return builder.toString();
    }
}
