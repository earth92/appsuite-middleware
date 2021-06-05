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
package com.openexchange.find.facet;

import static com.google.common.base.Preconditions.checkNotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import com.openexchange.find.SearchRequest;

/**
 * {@link Filter}s are used to narrow down search results by conditions.
 * A filter belongs to a {@link FacetValue}. Multiple filters may be
 * contained in a {@link SearchRequest}.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since 7.6.0
 */
public class Filter implements Serializable {

    public static final Filter NO_FILTER = new Filter(Collections.<String>emptyList(), "");

    private static final long serialVersionUID = -5712151560300214639L;

    private final String id;

    private final String displayName;

    private final List<String> fields;

    private final List<String> queries;

    /**
     * Convenient constructor for {@link Filter#Filter(String, String, List, List)}.
     * Id and display name are set to null, query is wrapped within {@link Collections#singletonList(Object)}.
     *
     * @param fields
     *   The fields to filter on.
     * @param query
     *   The query to search for.
     */
    public Filter(List<String> fields, String query) {
        this(fields, Collections.singletonList(query));
    }

    /**
     * Convenient constructor for {@link Filter#Filter(String, String, List, List)}.
     * Query is wrapped within {@link Collections#singletonList(Object)}.
     *
     * @param id
     *   The unique id of this filter within a list of filters of a {@link FacetValue}.
     *   If this filter is meant to be the only one for a facet value, it should be <code>null</code>.
     * @param displayName
     *   The display name of this filter (shown within a client).
     *   If this filter is meant to be the only one for a facet value, it should be <code>null</code>.
     * @param fields
     *   The fields to filter on.
     * @param query
     *   The query to search for.
     */
    public Filter(String id, String displayName, List<String> fields, String query) {
        this(id, displayName, fields, Collections.singletonList(query));
    }

    /**
     * Convenient constructor for {@link Filter#Filter(String, String, List, List)}.
     * Id and display name are set to null.
     *
     * @param fields
     *   The fields to filter on.
     * @param queries
     *   The queries to search for.
     */
    public Filter(List<String> fields, List<String> queries) {
        this(null, null, fields, queries);
    }

    /**
     * @param id
     *   The unique id of this filter within a list of filters of a {@link FacetValue}.
     *   If this filter is meant to be the only one for a facet value, it should be <code>null</code>.
     * @param displayName
     *   The display name of this filter (shown within a client).
     *   If this filter is meant to be the only one for a facet value, it should be <code>null</code>.
     * @param fields
     *   The fields to filter on.
     * @param queries
     *   The queries to search for.
     */
    public Filter(String id, String displayName, List<String> fields, List<String> queries) {
        super();
        checkNotNull(fields);
        checkNotNull(queries);
        this.id = id;
        this.displayName = displayName;
        this.fields = fields;
        this.queries = queries;
    }

    /**
     * @return the filters id, possibly <code>null</code>
     */
    public String getId() {
        return id;
    }

    /**
     * @return the filters display name, possibly <code>null</code>
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * The module specific fields to which the filter shall apply.
     * E.g. a mail folder or a contacts mail address.
     * Must neither be <code>null</code> nor empty.
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * The queries to filter on in the given fields.
     * Never <code>null</code> or empty.
     */
    public List<String> getQueries() {
        return queries;
    }

    /**
     * Creates a new filter with the given field and query.
     */
    public static Filter of(String field, String query) {
        return new FilterBuilder().addField(field).addQuery(query).build();
    }

    /**
     * Creates a new filter with the given field and queries.
     */
    public static Filter of(String field, List<String> queries) {
        FilterBuilder fb = new FilterBuilder().addField(field);
        for (String query : queries) {
            fb.addQuery(query);
        }
        return fb.build();
    }

    /**
     * Creates a new filter with the given fields and query.
     */
    public static Filter of(List<String> fields, String query) {
        FilterBuilder fb = new FilterBuilder();
        for (String field : fields) {
            fb.addField(field);
        }

        fb.addQuery(query);
        return fb.build();
    }

    /**
     * Creates a new filter with the given fields and queries.
     */
    public static Filter of(List<String> fields, List<String> queries) {
        FilterBuilder fb = new FilterBuilder();
        for (String field : fields) {
            fb.addField(field);
        }
        for (String query : queries) {
            fb.addQuery(query);
        }
        return fb.build();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
        result = prime * result + ((fields == null) ? 0 : fields.hashCode());
        result = prime * result + ((queries == null) ? 0 : queries.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Filter other = (Filter) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (displayName == null) {
            if (other.displayName != null)
                return false;
        } else if (!displayName.equals(other.displayName))
            return false;
        if (fields == null) {
            if (other.fields != null)
                return false;
        } else if (!fields.equals(other.fields))
            return false;
        if (queries == null) {
            if (other.queries != null)
                return false;
        } else if (!queries.equals(other.queries))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Filter [");
        if (id != null) {
            sb.append("id=").append(id).append(", ");
        }
        if (displayName != null) {
            sb.append("displayName=").append(displayName).append(", ");
        }

        sb.append("fields=").append(fields).append(", ");
        sb.append("queries=").append(queries);
        sb.append("]");
        return sb.toString();
    }

}
