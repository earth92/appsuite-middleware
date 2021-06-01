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

package com.openexchange.find.basic.mail;

import static com.openexchange.find.basic.mail.Constants.FIELD_BCC;
import static com.openexchange.find.basic.mail.Constants.FIELD_BODY;
import static com.openexchange.find.basic.mail.Constants.FIELD_CC;
import static com.openexchange.find.basic.mail.Constants.FIELD_FILENAME_NAME;
import static com.openexchange.find.basic.mail.Constants.FIELD_FROM;
import static com.openexchange.find.basic.mail.Constants.FIELD_HAS_ATTACHMENT;
import static com.openexchange.find.basic.mail.Constants.FIELD_SUBJECT;
import static com.openexchange.find.basic.mail.Constants.FIELD_TO;
import static com.openexchange.find.basic.mail.Constants.FROM_AND_TO_FIELDS;
import static com.openexchange.find.basic.mail.Constants.FROM_FIELDS;
import static com.openexchange.find.basic.mail.Constants.TO_FIELDS;
import static com.openexchange.find.common.CommonConstants.FIELD_DATE;
import static com.openexchange.find.common.CommonConstants.QUERY_LAST_MONTH;
import static com.openexchange.find.common.CommonConstants.QUERY_LAST_WEEK;
import static com.openexchange.find.common.CommonConstants.QUERY_LAST_YEAR;
import static com.openexchange.find.common.CommonFacetType.DATE;
import static com.openexchange.find.common.CommonFacetType.GLOBAL;
import static com.openexchange.find.common.CommonStrings.LAST_MONTH;
import static com.openexchange.find.common.CommonStrings.LAST_WEEK;
import static com.openexchange.find.common.CommonStrings.LAST_YEAR;
import static com.openexchange.find.facet.Facets.newExclusiveBuilder;
import static com.openexchange.find.facet.Facets.newSimpleBuilder;
import static com.openexchange.find.mail.MailFacetType.CONTACTS;
import static com.openexchange.find.mail.MailFacetType.FILENAME;
import static com.openexchange.find.mail.MailFacetType.MAIL_TEXT;
import static com.openexchange.find.mail.MailFacetType.SUBJECT;
import static com.openexchange.find.mail.MailStrings.FACET_FILENAME_NAME;
import static com.openexchange.find.mail.MailStrings.FACET_FROM;
import static com.openexchange.find.mail.MailStrings.FACET_FROM_AND_TO;
import static com.openexchange.find.mail.MailStrings.FACET_MAIL_TEXT;
import static com.openexchange.find.mail.MailStrings.FACET_SUBJECT;
import static com.openexchange.find.mail.MailStrings.FACET_TO;
import static com.openexchange.java.SimpleTokenizer.tokenize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.configuration.ServerConfig;
import com.openexchange.contact.AutocompleteParameters;
import com.openexchange.exception.OXException;
import com.openexchange.find.AbstractFindRequest;
import com.openexchange.find.AutocompleteRequest;
import com.openexchange.find.AutocompleteResult;
import com.openexchange.find.Document;
import com.openexchange.find.FindExceptionCode;
import com.openexchange.find.Module;
import com.openexchange.find.SearchRequest;
import com.openexchange.find.SearchResult;
import com.openexchange.find.basic.AbstractContactFacetingModuleSearchDriver;
import com.openexchange.find.basic.Services;
import com.openexchange.find.basic.common.Comparison;
import com.openexchange.find.common.CommonFacetType;
import com.openexchange.find.common.CommonStrings;
import com.openexchange.find.common.FolderType;
import com.openexchange.find.facet.ActiveFacet;
import com.openexchange.find.facet.DisplayItem;
import com.openexchange.find.facet.Facet;
import com.openexchange.find.facet.FacetType;
import com.openexchange.find.facet.FacetValue;
import com.openexchange.find.facet.Facets;
import com.openexchange.find.facet.Facets.DefaultFacetBuilder;
import com.openexchange.find.facet.Filter;
import com.openexchange.find.facet.Option;
import com.openexchange.find.facet.SimpleDisplayItem;
import com.openexchange.find.facet.SimpleFacet;
import com.openexchange.find.mail.MailDocument;
import com.openexchange.find.mail.MailFacetType;
import com.openexchange.find.mail.MailStrings;
import com.openexchange.find.spi.SearchConfiguration;
import com.openexchange.find.util.DisplayItems;
import com.openexchange.find.util.TimeFrame;
import com.openexchange.groupware.container.Contact;
import com.openexchange.i18n.I18nServiceRegistry;
import com.openexchange.java.Strings;
import com.openexchange.java.util.Pair;
import com.openexchange.java.util.TimeZones;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.IndexRange;
import com.openexchange.mail.MailField;
import com.openexchange.mail.MailServletInterface;
import com.openexchange.mail.MailSortField;
import com.openexchange.mail.OrderDirection;
import com.openexchange.mail.api.IMailFolderStorage;
import com.openexchange.mail.api.IMailMessageStorage;
import com.openexchange.mail.api.MailCapabilities;
import com.openexchange.mail.dataobjects.MailFolder;
import com.openexchange.mail.dataobjects.MailFolder.DefaultFolderType;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.search.ANDTerm;
import com.openexchange.mail.search.BccTerm;
import com.openexchange.mail.search.BodyTerm;
import com.openexchange.mail.search.CatenatingTerm;
import com.openexchange.mail.search.CcTerm;
import com.openexchange.mail.search.ComparablePattern;
import com.openexchange.mail.search.ComparisonType;
import com.openexchange.mail.search.FileNameTerm;
import com.openexchange.mail.search.FromTerm;
import com.openexchange.mail.search.ORTerm;
import com.openexchange.mail.search.ReceivedDateTerm;
import com.openexchange.mail.search.SearchTerm;
import com.openexchange.mail.search.SentDateTerm;
import com.openexchange.mail.search.SubjectTerm;
import com.openexchange.mail.search.ToTerm;
import com.openexchange.mail.search.UserFlagTerm;
import com.openexchange.mail.utils.MailFolderUtility;
import com.openexchange.mailaccount.MailAccount;
import com.openexchange.tools.session.ServerSession;

/**
 * A basic implementation to search within the mail module. Based on {@link IMailMessageStorage} and {@link SearchTerm}.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.0
 */
public class BasicMailDriver extends AbstractContactFacetingModuleSearchDriver {

    private static final Logger LOG = LoggerFactory.getLogger(BasicMailDriver.class);

    /** Denotes if simple queries (from the global facet) be searched within the mail body */
    private final boolean searchMailBody;

    /**
     * Initializes a new {@link BasicMailDriver}.
     *
     * @param virtualAllMessagesFolder Name of an optional virtual all-messages folder for primary accounts
     * @param searchMailBody <code>true</code> to also search in messages' bodies; otherwise <code>false</code>
     */
    public BasicMailDriver(final boolean searchMailBody) {
        super();
        this.searchMailBody = searchMailBody;
    }

    @Override
    public Module getModule() {
        return Module.MAIL;
    }

    @Override
    public boolean isValidFor(ServerSession session) throws OXException {
        return session.getUserConfiguration().hasWebMail() && session.getUserConfiguration().hasContact();
    }

    @Override
    public SearchConfiguration getSearchConfiguration(ServerSession session) throws OXException {
        SearchConfiguration config = new SearchConfiguration();
        if (Strings.isEmpty(getAllMessageFolder(session))) {
            config.setRequiresFolder();
        }

        return config;
    }

    private String getAllMessageFolder(ServerSession session) throws OXException {
        ConfigViewFactory factory = Services.requireService(ConfigViewFactory.class);
        ConfigView view = factory.getView(session.getUserId(), session.getContextId());
        return view.get("com.openexchange.find.basic.mail.allMessagesFolder", String.class);
    }

    @Override
    protected Set<FolderType> getSupportedFolderTypes(AutocompleteRequest autocompleteRequest, ServerSession session) {
        return FOLDER_TYPE_NOT_SUPPORTED;
    }

    @Override
    protected AutocompleteResult doAutocomplete(AutocompleteRequest autocompleteRequest, ServerSession session) throws OXException {
        String prefix = autocompleteRequest.getPrefix();
        AutocompleteParameters parameters = AutocompleteParameters.newInstance();
        parameters.put(AutocompleteParameters.REQUIRE_EMAIL, Boolean.TRUE);
        parameters.put(AutocompleteParameters.IGNORE_DISTRIBUTION_LISTS, Boolean.TRUE);
        List<Contact> contacts = autocompleteContacts(session, autocompleteRequest, parameters);
        List<Facet> facets = new ArrayList<Facet>(6);
        List<String> prefixTokens = null;
        int minimumSearchCharacters = ServerConfig.getInt(ServerConfig.Property.MINIMUM_SEARCH_CHARACTERS);

        final boolean prefixAvailable = Strings.isNotEmpty(prefix) && prefix.length() >= minimumSearchCharacters;
        Object[] values = accessMailStorage(autocompleteRequest, session, new MailServletClosure<Object[]>() {

            @Override
            public Object[] call(MailServletInterface mailServletInterface, MailFolder folder) throws OXException {
                Object[] vals = new Object[4];
                vals[0] = folder;
                vals[1] = prefixAvailable ? Boolean.valueOf(mailServletInterface.getMailConfig().getCapabilities().hasFileNameSearch()) : Boolean.FALSE;
                vals[2] = Boolean.valueOf(mailServletInterface.getMailConfig().getCapabilities().hasAttachmentMarker());
                vals[3] = mailServletInterface.getSentFolder(mailServletInterface.getAccountID());
                return vals;
            }
        });

        if (prefixAvailable) {
            prefixTokens = tokenize(prefix, minimumSearchCharacters);
            if (prefixTokens.isEmpty()) {
                prefixTokens = Collections.singletonList(prefix);
            }

            boolean addFileNameSearch = ((Boolean) values[1]).booleanValue();
            addSimpleFacets(facets, prefix, prefixTokens, addFileNameSearch);
        } else {
            prefixTokens = Collections.emptyList();
        }
        if ((values[2] != null) && ((Boolean) values[2]).booleanValue()) {
            addHasAttachmentFacet(facets);
        }

        MailFolder folder = (MailFolder) values[0];
        boolean toAsDefaultOption = folder.isSent();
        List<ActiveFacet> activeContactFacets = autocompleteRequest.getActiveFacets(MailFacetType.CONTACTS);
        if (!toAsDefaultOption && activeContactFacets != null && !activeContactFacets.isEmpty()) {
            toAsDefaultOption = true;
        }

        addContactsFacet(toAsDefaultOption, facets, contacts, prefix, prefixTokens, session);
        addDateFacet(facets);

        return new AutocompleteResult(facets);
    }

    private static void addHasAttachmentFacet(List<Facet> facets) {
        List<String> fields = Collections.singletonList(Constants.FIELD_HAS_ATTACHMENT);

        Facet hasAttachmentFacet = newExclusiveBuilder(MailFacetType.HAS_ATTACHMENT)
            .addValue(buildStaticFacetValue(CommonStrings.FACET_VALUE_TRUE, MailStrings.HAS_ATTACHMENT_TRUE, fields, CommonStrings.FACET_VALUE_TRUE))
            .build();
        facets.add(hasAttachmentFacet);
    }

    private static FacetValue buildStaticFacetValue(String id, String displayName, List<String> filterFields, String filterQuery) {
        return FacetValue.newBuilder(id).withLocalizableDisplayItem(displayName).withFilter(Filter.of(filterFields, filterQuery)).build();
    }

    @Override
    public SearchResult doSearch(final SearchRequest searchRequest, ServerSession session) throws OXException {
        final MailField[] mailFields;
        final String[] headers;
        if (searchRequest.getColumns().isUnset()) {
            mailFields = MailField.FIELDS_LOW_COST;
            headers = null;
        } else {
            mailFields = MailField.getMatchingFields(searchRequest.getColumns().getIntColumns());
            headers = searchRequest.getColumns().getStringColumns();
        }

        TimeZone tz = TimeZone.getTimeZone(session.getUser().getTimeZone());

        List<MailMessage> messages = accessMailStorage(searchRequest, session, new MailServletClosure<List<MailMessage>>() {

            @Override
            public List<MailMessage> call(MailServletInterface mailServletInterface, MailFolder folder) throws OXException {
                SearchTerm<?> searchTerm = prepareSearchTerm(folder, searchRequest, mailServletInterface.getMailAccess().getMailConfig().getCapabilities(), tz);
                List<MailMessage> messages = searchMessages(mailServletInterface, folder, searchTerm, mailFields, headers, searchRequest.getStart(), searchRequest.getSize());
                return messages;
            }
        });

        List<Document> documents = new ArrayList<Document>(messages.size());
        for (MailMessage message : messages) {
            documents.add(new MailDocument(message));
        }

        return new SearchResult(-1, searchRequest.getStart(), documents, searchRequest.getActiveFacets());
    }

    private <R> R accessMailStorage(AbstractFindRequest request, ServerSession session, MailServletClosure<R> closure) throws OXException {
        long start = System.currentTimeMillis();
        String folderId = request.getFolderId();
        FullnameArgument fullnameArgument = determineFolder(session, folderId);
        MailServletInterface mailServletInterface = MailServletInterface.getInstance(session);
        try {
            mailServletInterface.openFor(MailFolderUtility.prepareFullname(fullnameArgument.getAccountId(), fullnameArgument.getFullname()));
            IMailFolderStorage folderStorage = mailServletInterface.getMailAccess().getFolderStorage();
            MailFolder folder = folderStorage.getFolder(fullnameArgument.getFullname());
            if (!folder.containsDefaultFolderType() && folder.getFullname().equals(folderStorage.getSentFolder())) {
                folder.setDefaultFolder(true);
                folder.setDefaultFolderType(DefaultFolderType.SENT);
            }
            return closure.call(mailServletInterface, folder);
        } finally {
            mailServletInterface.close(true);
            long diff = System.currentTimeMillis() - start;
            LOG.debug("Transaction for MailAccess lasted {}ms. Request type: {}", Long.valueOf(diff), request.getClass().getSimpleName());
        }
    }

    private static interface MailServletClosure<R> {

        R call(MailServletInterface mailServletInterface, MailFolder folder) throws OXException;

    }

    private FullnameArgument determineFolder(ServerSession session, String folderId) throws OXException {
        if (folderId == null) {
            String allMessageFolder = getAllMessageFolder(session);
            if (Strings.isEmpty(allMessageFolder)) {
                throw FindExceptionCode.MISSING_MANDATORY_FACET.create(CommonFacetType.FOLDER.getId());
            }
            if (!allMessageFolder.startsWith(MailFolder.MAIL_PREFIX)) {
                allMessageFolder = MailFolderUtility.prepareFullname(MailAccount.DEFAULT_ID, allMessageFolder);
            }
            folderId = allMessageFolder;
        }

        return MailFolderUtility.prepareMailFolderParam(folderId);
    }

    private static void addSimpleFacets(List<Facet> facets, String prefix, List<String> prefixTokens, boolean addFileNameSearch) {
        if (!prefixTokens.isEmpty()) {

            facets.add(newSimpleBuilder(GLOBAL).withSimpleDisplayItem(prefix).withFilter(Filter.of(GLOBAL.getId(), prefixTokens)).build());

            facets.add(buildSimpleFacet(SUBJECT, FACET_SUBJECT, prefix, FIELD_SUBJECT, prefixTokens));

            facets.add(buildSimpleFacet(MAIL_TEXT, FACET_MAIL_TEXT, prefix, FIELD_BODY, prefixTokens));

            if (addFileNameSearch) {
                facets.add(buildSimpleFacet(FILENAME, FACET_FILENAME_NAME, prefix, FIELD_FILENAME_NAME, prefixTokens));
            }
        }
    }

    private static SimpleFacet buildSimpleFacet(FacetType type, String formatString, String formatArg, String filterField, List<String> prefixTokens) {
        return newSimpleBuilder(type).withFormattableDisplayItem(formatString, formatArg).withFilter(Filter.of(filterField, prefixTokens)).build();
    }

    private static void addDateFacet(List<Facet> facets) {
        facets.add(Facets.newExclusiveBuilder(DATE).addValue(FacetValue.newBuilder(QUERY_LAST_WEEK).withLocalizableDisplayItem(LAST_WEEK).withFilter(Filter.of(FIELD_DATE, QUERY_LAST_WEEK)).build()).addValue(FacetValue.newBuilder(QUERY_LAST_MONTH).withLocalizableDisplayItem(LAST_MONTH).withFilter(Filter.of(FIELD_DATE, QUERY_LAST_MONTH)).build()).addValue(FacetValue.newBuilder(QUERY_LAST_YEAR).withLocalizableDisplayItem(LAST_YEAR).withFilter(Filter.of(FIELD_DATE, QUERY_LAST_YEAR)).build()).build());
    }

    private static void addContactsFacet(boolean toAsDefaultOption, List<Facet> facets, List<Contact> contacts, String prefix, List<String> prefixTokens, ServerSession session) {
        DefaultFacetBuilder builder = Facets.newDefaultBuilder(CONTACTS);
        boolean valuesAdded = false;
        for (Contact contact : contacts) {
            String valueId = prepareFacetValueId("contact", session.getContextId(), Integer.toString(contact.getObjectID()));
            List<String> queries = extractMailAddessesFrom(contact);
            builder.addValue(buildContactValue(valueId, queries, DisplayItems.convert(contact, session.getUser().getLocale(), Services.optionalService(I18nServiceRegistry.class)), toAsDefaultOption));
            valuesAdded = true;
        }

        if (!prefix.isEmpty() && !prefixTokens.isEmpty()) {
            builder.addValue(buildContactValue(prefix, tokenize(prefix), new SimpleDisplayItem(prefix), toAsDefaultOption));
            valuesAdded = true;
        }

        if (valuesAdded) {
            facets.add(builder.build());
        }
    }

    private static FacetValue buildContactValue(String valueId, List<String> queries, DisplayItem item, boolean toAsDefaultOption) {
        Option from = Option.newInstance("from", FACET_FROM, Filter.of(FROM_FIELDS, queries));
        Option to = Option.newInstance("to", FACET_TO, Filter.of(TO_FIELDS, queries));
        Option all = Option.newInstance("all", FACET_FROM_AND_TO, Filter.of(FROM_AND_TO_FIELDS, queries));

        List<Option> options = new ArrayList<Option>(3);
        if (toAsDefaultOption) {
            options.add(to);
            options.add(from);
            options.add(all);
        } else {
            options.add(from);
            options.add(to);
            options.add(all);
        }

        return FacetValue.newBuilder(valueId).withDisplayItem(item).withOptions(options).build();
    }

    static List<MailMessage> searchMessages(MailServletInterface mailServletInterface, MailFolder folder, SearchTerm<?> searchTerm, MailField[] fields, String headers[], int start, int size) throws OXException {
        MailSortField sortField = folder.isSent() ? MailSortField.SENT_DATE : MailSortField.RECEIVED_DATE;
        IndexRange indexRange = new IndexRange(start, start + size);
        OrderDirection orderDirection = OrderDirection.DESC;
        String fullname = folder.getFullname();

        MailMessage[] messages = mailServletInterface.searchMails(MailFolderUtility.prepareFullname(mailServletInterface.getMailAccess().getAccountId(), fullname), indexRange, sortField, orderDirection, searchTerm, fields, headers);
        List<MailMessage> resultMessages = new ArrayList<MailMessage>(messages.length);
        Collections.addAll(resultMessages, messages);
        return resultMessages;
    }

    private static enum OP {
        OR, AND;
    }

    private static CatenatingTerm catenationFor(OP op, SearchTerm<?> t1, SearchTerm<?> t2) {
        switch (op) {
            case OR:
                return new ORTerm(t1, t2);

            case AND:
                return new ANDTerm(t1, t2);
        }

        return null;
    }

    /**
     * Checks given fields.
     * <ul>
     * <li>Add MailField.ID if MailField.ORIGINAL_ID is contained
     * <li>Add MailField.FOLDER_ID if MailField.ORIGINAL_FOLDER_ID is contained
     * </ul>
     *
     * @param mailFields The fields to check
     * @return The checked fields
     */
    protected static MailField[] prepareColumns(MailField[] mailFields) {
        MailField[] fields = mailFields;

        EnumSet<MailField> set = EnumSet.copyOf(Arrays.asList(fields));
        if (set.contains(MailField.ORIGINAL_FOLDER_ID) && !set.contains(MailField.FOLDER_ID)) {
            MailField[] tmp = fields;
            fields = new MailField[tmp.length + 1];
            fields[0] = MailField.FOLDER_ID;
            System.arraycopy(tmp, 0, fields, 1, tmp.length);
        }
        if (set.contains(MailField.ORIGINAL_ID) && !set.contains(MailField.ID)) {
            MailField[] tmp = fields;
            fields = new MailField[tmp.length + 1];
            fields[0] = MailField.ID;
            System.arraycopy(tmp, 0, fields, 1, tmp.length);
        }

        return fields;
    }

    protected SearchTerm<?> prepareSearchTerm(MailFolder folder, SearchRequest searchRequest, MailCapabilities mailCapabilities) throws OXException {
        return prepareSearchTerm(folder, searchRequest, mailCapabilities, TimeZone.getDefault());
    }

    protected SearchTerm<?> prepareSearchTerm(MailFolder folder, SearchRequest searchRequest, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        List<String> queryFields = searchMailBody ? Constants.QUERY_FIELDS_BODY : Constants.QUERY_FIELDS;
        SearchTerm<?> queryTerm = prepareQueryTerm(folder, queryFields, searchRequest.getQueries(), mailCapabilities, userTimeZone);

        List<SearchTerm<?>> facetTerms = new LinkedList<SearchTerm<?>>();
        SearchTerm<?> timeTerm = prepareDateTerm(searchRequest, folder, userTimeZone);
        if (timeTerm != null) {
            facetTerms.add(timeTerm);
        }

        SearchTerm<?> subjectTerm = prepareTermForFacet(searchRequest, MailFacetType.SUBJECT, folder, OP.AND, OP.AND, OP.AND, mailCapabilities, userTimeZone);
        if (subjectTerm != null) {
            facetTerms.add(subjectTerm);
        }

        SearchTerm<?> bodyTerm = prepareTermForFacet(searchRequest, MailFacetType.MAIL_TEXT, folder, OP.AND, OP.AND, OP.AND, mailCapabilities, userTimeZone);
        if (bodyTerm != null) {
            facetTerms.add(bodyTerm);
        }

        SearchTerm<?> attachmentTerm = prepareTermForFacet(searchRequest, MailFacetType.FILENAME, folder, OP.AND, OP.AND, OP.AND, mailCapabilities, userTimeZone);
        if (attachmentTerm != null) {
            facetTerms.add(attachmentTerm);
        }

        SearchTerm<?> hasAttachmentTerm = prepareTermForFacet(searchRequest, MailFacetType.HAS_ATTACHMENT, folder, OP.AND, OP.AND, OP.AND, mailCapabilities, userTimeZone);
        if (hasAttachmentTerm != null) {
            facetTerms.add(hasAttachmentTerm);
        }

        SearchTerm<?> contactsTerm = prepareTermForFacet(searchRequest, MailFacetType.CONTACTS, folder, OP.AND, OP.OR, OP.OR, mailCapabilities, userTimeZone);
        if (contactsTerm != null) {
            facetTerms.add(contactsTerm);
        }

        SearchTerm<?> facetTerm = null;
        if (!facetTerms.isEmpty()) {
            if (facetTerms.size() == 1) {
                facetTerm = facetTerms.get(0);
            } else {
                Iterator<SearchTerm<?>> it = facetTerms.iterator();
                SearchTerm<?> t1 = it.next();
                SearchTerm<?> t2 = it.next();
                CatenatingTerm finalTerm = catenationFor(OP.AND, t1, t2);
                while (it.hasNext()) {
                    CatenatingTerm newTerm = catenationFor(OP.AND, finalTerm.getSecondTerm(), it.next());
                    finalTerm.setSecondTerm(newTerm);
                }
                facetTerm = finalTerm;
            }
        }

        SearchTerm<?> searchTerm = null;
        if (facetTerm == null || queryTerm == null) {
            if (facetTerm != null) {
                searchTerm = facetTerm;
            } else {
                searchTerm = queryTerm;
            }
        } else {
            searchTerm = new ANDTerm(queryTerm, facetTerm);
        }

        return searchTerm;
    }

    private static SearchTerm<?> prepareQueryTerm(MailFolder folder, List<String> fields, List<String> queries, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        if (queries == null || queries.isEmpty()) {
            return null;
        }

        return termFor(fields, queries, OP.OR, OP.AND, folder.isSent(), mailCapabilities, userTimeZone);
    }

    private static SearchTerm<?> prepareDateTerm(SearchRequest searchRequest, MailFolder folder, TimeZone userTimeZone) throws OXException {
        List<ActiveFacet> dateFacets = searchRequest.getActiveFacets(DATE);
        if (dateFacets != null && !dateFacets.isEmpty()) {
            ActiveFacet dateFacet = dateFacets.get(0);
            Filter filter = dateFacet.getFilter();
            if (filter != Filter.NO_FILTER) {
                Pair<Comparison, Long> parsed = parseDateQuery(filter.getQueries().get(0));
                Comparison comparison = parsed.getFirst();
                Long timestamp = parsed.getSecond();
                return buildDateTerm(comparison, timestamp.longValue(), folder.isSent(), userTimeZone);
            }

            // No filter...
            String timeFramePattern = dateFacet.getValueId();
            TimeFrame timeFrame = TimeFrame.valueOf(timeFramePattern);
            if (timeFrame == null) {
                throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(timeFramePattern, FIELD_DATE);
            }

            Comparison fromComparison;
            Comparison toComparison;
            if (timeFrame.isInclusive()) {
                fromComparison = Comparison.GREATER_EQUALS;
                toComparison = Comparison.LOWER_EQUALS;
            } else {
                fromComparison = Comparison.GREATER_THAN;
                toComparison = Comparison.LOWER_THAN;
            }

            long from = timeFrame.getFrom();
            long to = timeFrame.getTo();
            if (to < 0L) {
                return buildDateTerm(fromComparison, from, folder.isSent(), userTimeZone);
            }

            SearchTerm<?> fromTerm = buildDateTerm(fromComparison, from, folder.isSent(), userTimeZone);
            SearchTerm<?> toTerm = buildDateTerm(toComparison, to, folder.isSent(), userTimeZone);
            return new ANDTerm(fromTerm, toTerm);
        }

        return null;
    }

    private static SearchTerm<?> prepareTermForFacet(SearchRequest searchRequest, FacetType type, MailFolder folder, OP filterOP, OP fieldOP, OP queryOP, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        List<ActiveFacet> facets = searchRequest.getActiveFacets(type);
        if (facets != null && !facets.isEmpty()) {
            List<Filter> filters = new LinkedList<Filter>();
            for (ActiveFacet facet : facets) {
                Filter filter = facet.getFilter();
                if (filter != Filter.NO_FILTER) {
                    filters.add(filter);
                }
            }

            return prepareFilterTerm(folder, filters, filterOP, fieldOP, queryOP, mailCapabilities, userTimeZone);
        }

        return null;
    }

    private static SearchTerm<?> prepareFilterTerm(MailFolder folder, List<Filter> filters, OP filterOP, OP fieldOP, OP queryOP, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        if (filters.size() == 1) {
            return termFor(filters.get(0), fieldOP, queryOP, folder.isSent(), mailCapabilities, userTimeZone);
        }

        Iterator<Filter> it = filters.iterator();
        Filter f1 = it.next();
        Filter f2 = it.next();
        CatenatingTerm finalTerm = catenationFor(filterOP, termFor(f1, fieldOP, queryOP, folder.isSent(), mailCapabilities, userTimeZone), termFor(f2, fieldOP, queryOP, folder.isSent(), mailCapabilities, userTimeZone));
        while (it.hasNext()) {
            CatenatingTerm newTerm = catenationFor(filterOP, finalTerm.getSecondTerm(), termFor(it.next(), fieldOP, queryOP, folder.isSent(), mailCapabilities, userTimeZone));
            finalTerm.setSecondTerm(newTerm);
        }

        return finalTerm;
    }

    private static SearchTerm<?> termFor(Filter filter, OP fieldOP, OP queryOP, boolean isOutgoingFolder, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        List<String> fields = filter.getFields();
        if (fields == null || fields.isEmpty()) {
            throw FindExceptionCode.INVALID_FILTER_NO_FIELDS.create(filter);
        }

        List<String> queries = filter.getQueries();
        if (queries == null || queries.isEmpty()) {
            throw FindExceptionCode.INVALID_FILTER_NO_QUERIES.create(filter);
        }

        return termFor(fields, queries, fieldOP, queryOP, isOutgoingFolder, mailCapabilities, userTimeZone);
    }

    private static SearchTerm<?> termFor(List<String> fields, List<String> queries, OP fieldOP, OP queryOP, boolean isOutgoingFolder, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        if (fields.size() > 1) {
            Iterator<String> it = fields.iterator();
            String f1 = it.next();
            String f2 = it.next();
            CatenatingTerm finalTerm = catenationFor(fieldOP, termForField(f1, queries, queryOP, isOutgoingFolder, mailCapabilities, userTimeZone), termForField(f2, queries, queryOP, isOutgoingFolder, mailCapabilities, userTimeZone));
            while (it.hasNext()) {
                String f = it.next();
                CatenatingTerm newTerm = catenationFor(fieldOP, finalTerm.getSecondTerm(), termForField(f, queries, queryOP, isOutgoingFolder, mailCapabilities, userTimeZone));
                finalTerm.setSecondTerm(newTerm);
            }

            return finalTerm;
        }

        return termForField(fields.iterator().next(), queries, queryOP, isOutgoingFolder, mailCapabilities, userTimeZone);
    }

    private static SearchTerm<?> termForField(String field, List<String> queries, OP queryOP, boolean isOutgoingFolder, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        if (queries.size() > 1) {
            Iterator<String> it = queries.iterator();
            String q1 = it.next();
            String q2 = it.next();
            CatenatingTerm finalTerm = catenationFor(queryOP, termForQuery(field, q1, isOutgoingFolder, mailCapabilities, userTimeZone), termForQuery(field, q2, isOutgoingFolder, mailCapabilities, userTimeZone));
            while (it.hasNext()) {
                String q = it.next();
                CatenatingTerm newTerm = catenationFor(queryOP, finalTerm.getSecondTerm(), termForQuery(field, q, isOutgoingFolder, mailCapabilities, userTimeZone));
                finalTerm.setSecondTerm(newTerm);
            }

            return finalTerm;
        }

        return termForQuery(field, queries.iterator().next(), isOutgoingFolder, mailCapabilities, userTimeZone);
    }

    private static SearchTerm<?> termForQuery(String field, String query, boolean isOutgoingFolder, MailCapabilities mailCapabilities, TimeZone userTimeZone) throws OXException {
        if (FIELD_FROM.equals(field)) {
            return new FromTerm(query);
        } else if (FIELD_TO.equals(field)) {
            return new ToTerm(query);
        } else if (FIELD_CC.equals(field)) {
            return new CcTerm(query);
        } else if (FIELD_BCC.equals(field)) {
            return new BccTerm(query);
        } else if (FIELD_SUBJECT.equals(field)) {
            return new SubjectTerm(query);
        } else if (FIELD_BODY.equals(field)) {
            return new BodyTerm(query);
        } else if (FIELD_DATE.equals(field)) {
            Pair<Comparison, Long> parsed = parseDateQuery(query);
            Comparison comparison = parsed.getFirst();
            Long timestamp = parsed.getSecond();
            return buildDateTerm(comparison, timestamp.longValue(), isOutgoingFolder, userTimeZone);
        } else if (FIELD_FILENAME_NAME.equals(field)) {
            if (false == mailCapabilities.hasFileNameSearch()) {
                throw FindExceptionCode.UNSUPPORTED_FILTER_FIELD.create(field);
            }
            return new FileNameTerm(query);
        } else if (FIELD_HAS_ATTACHMENT.equals(field)) {
            if (false == mailCapabilities.hasAttachmentMarker()) {
                throw FindExceptionCode.UNSUPPORTED_FILTER_FIELD.create(field);
            }
            boolean hasAttachment = Boolean.parseBoolean(query);
            return new UserFlagTerm(hasAttachment ? MailMessage.USER_HAS_ATTACHMENT : MailMessage.USER_HAS_NO_ATTACHMENT, true);
        }

        throw FindExceptionCode.UNSUPPORTED_FILTER_FIELD.create(field);
    }

    private static SearchTerm<?> buildDateTerm(Comparison comparison, long timestamp, boolean isOutgoingFolder, TimeZone tz) {
        Calendar instance = Calendar.getInstance(tz);
        instance.setTimeInMillis(timestamp);
        Date date = new Date(timestamp + tz.getOffset(timestamp) - TimeZone.getDefault().getOffset(timestamp));
        ComparisonType type;
        switch (comparison) {
            case EQUALS: {
                type = ComparisonType.EQUALS;
                break;
            }

            case GREATER_THAN: {
                type = ComparisonType.GREATER_THAN;
                break;
            }

            case LOWER_THAN: {
                type = ComparisonType.LESS_THAN;
                break;
            }

            case GREATER_EQUALS: {
                type = ComparisonType.GREATER_EQUALS;
                break;
            }

            case LOWER_EQUALS: {
                type = ComparisonType.LESS_EQUALS;
                break;
            }

            default: {
                type = ComparisonType.EQUALS;
                break;
            }
        }

        return dateTermFor(type, date, isOutgoingFolder);
    }

    private static SearchTerm<ComparablePattern<java.util.Date>> dateTermFor(ComparisonType comparisonType, Date date, boolean isOutgoingFolder) {
        if (isOutgoingFolder) {
            return new SentDateTerm(comparisonType, date);
        }

        return new ReceivedDateTerm(comparisonType, date);
    }

    private static Pair<Comparison, Long> parseDateQuery(String query) throws OXException {
        if (Strings.isEmpty(query)) {
            throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(query, FIELD_DATE);
        }

        Comparison comparison;
        long timestamp;
        Calendar cal = new GregorianCalendar(TimeZones.UTC);
        if (QUERY_LAST_WEEK.equals(query)) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            comparison = Comparison.GREATER_EQUALS;
            timestamp = cal.getTime().getTime();
        } else if (QUERY_LAST_MONTH.equals(query)) {
            cal.add(Calendar.MONTH, -1);
            comparison = Comparison.GREATER_EQUALS;
            timestamp = cal.getTime().getTime();
        } else if (QUERY_LAST_YEAR.equals(query)) {
            cal.add(Calendar.YEAR, -1);
            comparison = Comparison.GREATER_EQUALS;
            timestamp = cal.getTime().getTime();
        } else {
            throw FindExceptionCode.UNSUPPORTED_FILTER_QUERY.create(query, FIELD_DATE);
        }

        return new Pair<Comparison, Long>(comparison, Long.valueOf(timestamp));
    }
}
