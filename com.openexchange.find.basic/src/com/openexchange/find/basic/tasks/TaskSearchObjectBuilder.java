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

package com.openexchange.find.basic.tasks;

import static com.openexchange.java.Autoboxing.I;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.common.collect.ImmutableMap;
import com.openexchange.exception.OXException;
import com.openexchange.find.FindExceptionCode;
import com.openexchange.find.Module;
import com.openexchange.find.SearchRequest;
import com.openexchange.find.basic.Folders;
import com.openexchange.find.basic.Services;
import com.openexchange.find.common.FolderType;
import com.openexchange.find.facet.Filter;
import com.openexchange.folderstorage.FolderStorage;
import com.openexchange.folderstorage.Type;
import com.openexchange.folderstorage.UserizedFolder;
import com.openexchange.folderstorage.database.contentType.TaskContentType;
import com.openexchange.folderstorage.type.PrivateType;
import com.openexchange.folderstorage.type.PublicType;
import com.openexchange.folderstorage.type.SharedType;
import com.openexchange.groupware.search.TaskSearchObject;
import com.openexchange.java.Strings;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link TaskSearchBuilder}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public class TaskSearchObjectBuilder {

    private static enum SupportedField {

        TITLE("title"),
        DESCRIPTION("description"),
        STATUS("status"),
        FOLDER_TYPE("folder_type"),
        TYPE("type"),
        PARTICIPANT("participant"),
        ATTACHMENT("attachment"),
        ATTACHMENT_NAME("attachment_name");

        private final String fieldName;

        private SupportedField(String fieldName) {
            this.fieldName = fieldName;
        }

        private static final Map<String, SupportedField> MAPPING;
        static {
            SupportedField[] values = SupportedField.values();
            ImmutableMap.Builder<String, SupportedField> m = ImmutableMap.builder();
            for (SupportedField value : values) {
                m.put(value.fieldName, value);
            }
            MAPPING = m.build();
        }

        /**
         * Gets the field for given name
         *
         * @param fieldName The name
         * @return The associated field or <code>null</code>
         */
        static SupportedField supportedFieldFor(String fieldName) {
            return null == fieldName ? null : MAPPING.get(Strings.asciiLowerCase(fieldName));
        }
    }

    // -------------------------------------------------------------------------------------------------------------------- //

    private final TaskSearchObject searchObject;
    private final ServerSession session;

    /**
     * Initializes a new {@link TaskSearchBuilder}.
     */
    public TaskSearchObjectBuilder(ServerSession ss) {
        super();
        session = ss;
        searchObject = new TaskSearchObject();
    }

    /**
     * Build the {@link TaskSearchObject}
     *
     * @return the {@link TaskSearchObject}
     */
    public TaskSearchObject build() {
        return searchObject;
    }

    /**
     * Add the specified {@link Filter}s to the search object
     *
     * @param filters
     * @return This builder instance
     * @throws OXException if the provided {@Filter} contains an unsupported field (@see {@link SupportedField}).
     */
    public TaskSearchObjectBuilder addFilters(List<Filter> filters) throws OXException {
        for (Filter f : filters) {
            addFilter(f);
        }
        return this;
    }

    /**
     * Add a {@link Filter} to the search object
     *
     * @param filter The filter to add
     * @return This builder instance with filter added
     * @throws OXException If the provided {@link Filter} either contains an unsupported field or a field that is <code>null</code>
     */
    public TaskSearchObjectBuilder addFilter(Filter filter) throws OXException {
        for (String fieldName : filter.getFields()) {
            if (null == fieldName) {
                throw FindExceptionCode.NULL_FIELD.create(fieldName);
            }

            SupportedField sf = SupportedField.supportedFieldFor(fieldName);
            if (null == sf) {
                throw FindExceptionCode.UNSUPPORTED_FILTER_FIELD.create(fieldName);
            }

            switch (sf) {
            case TITLE:
                addTitleFilters(filter.getQueries());
                break;
            case DESCRIPTION:
                addDescriptionFilters(filter.getQueries());
                break;
            case STATUS:
                addStateFilters(filter.getQueries());
                break;
            case FOLDER_TYPE:
                addFolderTypeFilters(filter.getQueries());
                break;
            case TYPE:
                addRecurrenceTypeFilters(filter.getQueries());
                break;
            case PARTICIPANT:
                addParticipantFilters(filter.getQueries());
                break;
            case ATTACHMENT:
                /* fall-through */
            case ATTACHMENT_NAME:
                addAttachmentFilters(filter.getQueries());
                break;
            default:
                throw FindExceptionCode.UNSUPPORTED_FILTER_FIELD.create(fieldName);
            }
        }
        return this;
    }

    /**
     * Add the specified queries to the search object
     *
     * @param queries The queries to add
     * @return This builder instance with queries added
     */
    public TaskSearchObjectBuilder addQueries(List<String> queries) {
        for (String q : queries) {
            addQuery(q);
        }
        return this;
    }

    /**
     * Applies folder IDs to the search, depending on the existence of a specific folder ID or a folder type.
     *
     * @param folderID The folder ID to apply, or <code>null</code> if not specified
     * @param folderType The folder type for that all folder shall be applied; <code>null</code> if not specified.
     * @return This builder instance
     * @throws OXException
     */
    public TaskSearchObjectBuilder applyFolders(SearchRequest searchRequest) throws OXException {
        List<Integer> folderIDs = Folders.getIDs(searchRequest, Module.TASKS, session);
        if (folderIDs != null && !folderIDs.isEmpty()) {
            searchObject.setFolders(folderIDs);
        }
        return this;
    }

    /**
     * Add a query to the search object
     *
     * @param query The query to add
     * @return This builder instance with query added
     */
    public TaskSearchObjectBuilder addQuery(String query) {
        Set<String> queries = searchObject.getQueries();
        if (queries == null) {
            queries = new HashSet<String>();
        }
        String wrapped = wrapWithWildcards(query);
        if (null != wrapped) {
            queries.add(wrapped);
        }
        searchObject.setQueries(queries);
        return this;
    }

    /**
     * Append the title queries to the search object.
     *
     * @param filters
     */
    private void addTitleFilters(List<String> filters) {
        Set<String> tf = searchObject.getTitles();
        if (tf == null) {
            tf = new HashSet<String>(filters.size());
        }
        for (String q : filters) {
            String wrapped = wrapWithWildcards(q);
            if (null != wrapped) {
                tf.add(wrapped);
            }
        }
        searchObject.setTitles(tf);
    }

    /**
     * Append the description queries to the search object.
     *
     * @param filters
     */
    private void addDescriptionFilters(List<String> filters) {
        Set<String> df = searchObject.getNotes();
        if (df == null) {
            df = new HashSet<String>(filters.size());
        }
        for (String q : filters) {
            String wrapped = wrapWithWildcards(q);
            if (null != wrapped) {
                df.add(wrapped);
            }
        }
        searchObject.setNotes(df);
    }

    /**
     * Add the status filter
     *
     * @param filters
     * @throws OXException
     */
    private void addStateFilters(List<String> filters) throws OXException {
        Set<Integer> sf = searchObject.getStateFilters();
        if (sf == null) {
            sf = new HashSet<Integer>(filters.size());
        }
        for (String q : filters) {
            int s = Integer.parseInt(q);
            if (s > 5 || s < 1) {
                throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(q, "status");
            }
            sf.add(I(s));
        }
        searchObject.setStateFilters(sf);
    }

    /**
     * Pre-fetch all relevant folder identifiers, according to the specified filters
     *
     * @param filters
     * @throws OXException
     */
    private void addFolderTypeFilters(List<String> filters) throws OXException {
        for (String q : filters) {
            Type t;
            if (FolderType.PUBLIC.getIdentifier().equals(q)) {
                t = PublicType.getInstance();
            } else if (FolderType.SHARED.getIdentifier().equals(q)) {
                t = SharedType.getInstance();
            } else if (FolderType.PRIVATE.getIdentifier().equals(q)) {
                t = PrivateType.getInstance();
            } else {
                throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(q, "folder_type");
            }
            UserizedFolder[] folders = Services.getFolderService().getVisibleFolders(FolderStorage.REAL_TREE_ID, TaskContentType.getInstance(), t, false, session, null).getResponse();
            if (folders != null && folders.length > 0) {
                for (UserizedFolder uf : folders) {
                    searchObject.addFolder(Integer.parseInt(uf.getID()));
                }
            }
        }
    }

    /**
     * Add the attachment filters
     *
     * @param filters
     */
    private void addAttachmentFilters(List<String> filters) {
        Set<String> af = searchObject.getAttachmentNames();
        if (af == null) {
            af = new HashSet<String>(filters.size());
        }
        for (String q : filters) {
            String wrapped = wrapWithWildcards(q);
            if (null != wrapped) {
                af.add(wrapped);
            }
        }
        searchObject.setAttachmentNames(af);
    }

    /**
     * Set the recurrence type filter
     *
     * @param filters
     * @throws OXException
     */
    private void addRecurrenceTypeFilters(List<String> filters) throws OXException {
        for (String r : filters) {
            if (TaskType.SERIES.getIdentifier().equals(r)) {
                searchObject.setSeriesFilter(true);
            } else if (TaskType.SINGLE_TASK.getIdentifier().equals(r)) {
                searchObject.setSingleOccurrenceFilter(true);
            } else {
                throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(r, "type");
            }
        }
    }

    private void addParticipantFilters(List<String> filters) {
        Set<Integer> intP = searchObject.getUserIDs();
        Set<String> extP = searchObject.getExternalParticipants();

        if (intP == null) {
            intP = new HashSet<Integer>();
        }

        if (extP == null) {
            extP = new HashSet<String>();
        }

        for (String f : filters) {
            if (Strings.isNumeric(f)) {
                try {
                    intP.add(Integer.valueOf(f));
                } catch (NumberFormatException e) {
                    extP.add(f);
                }
            } else {
                extP.add(f);
            }
        }

        searchObject.setUserIDs(intP);
        searchObject.setExternalParticipants(extP);

        searchObject.setHasInternalParticipants(intP.size() > 0);
        searchObject.setHasExternalParticipants(extP.size() > 0);
    }

    /**
     * Wrap query with wild-cards.
     *
     * @param query
     * @return
     */
    private static String wrapWithWildcards(String query) {
        if (null == query) {
            return query;
        }

        String wrapped = query;
        if (!wrapped.startsWith("*")) {
            wrapped = '*' + query;
        }
        if (!wrapped.endsWith("*")) {
            wrapped = query + '*';
        }
        return wrapped;
    }

}
