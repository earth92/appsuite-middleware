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

package com.openexchange.mail.json.actions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONValue;
import com.openexchange.ajax.AJAXServlet;
import com.openexchange.ajax.Mail;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AJAXRequestDataTools;
import com.openexchange.ajax.requesthandler.AJAXRequestResult;
import com.openexchange.ajax.requesthandler.annotation.restricted.RestrictedAction;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.exception.OXException;
import com.openexchange.json.cache.JsonCacheService;
import com.openexchange.json.cache.JsonCaches;
import com.openexchange.mail.IndexRange;
import com.openexchange.mail.MailExceptionCode;
import com.openexchange.mail.MailField;
import com.openexchange.mail.MailServletInterface;
import com.openexchange.mail.MailSortField;
import com.openexchange.mail.OrderDirection;
import com.openexchange.mail.categories.MailCategoriesConfigService;
import com.openexchange.mail.dataobjects.MailMessage;
import com.openexchange.mail.json.MailRequest;
import com.openexchange.mail.json.MailRequestSha1Calculator;
import com.openexchange.mail.json.converters.MailConverter;
import com.openexchange.mail.json.osgi.MailJSONActivator;
import com.openexchange.mail.json.utils.ColumnCollection;
import com.openexchange.mail.search.ANDTerm;
import com.openexchange.mail.search.FlagTerm;
import com.openexchange.mail.search.SearchTerm;
import com.openexchange.mail.search.UserFlagTerm;
import com.openexchange.server.ServiceLookup;
import com.openexchange.threadpool.ThreadPools;
import com.openexchange.tools.iterator.SearchIterator;
import com.openexchange.tools.iterator.SearchIterators;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link AllAction}
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
@RestrictedAction(module = AbstractMailAction.MODULE, type = RestrictedAction.Type.READ)
public final class AllAction extends AbstractMailAction implements MailRequestSha1Calculator {

    /**
     * Initializes a new {@link AllAction}.
     *
     * @param services The service look-up
     */
    public AllAction(ServiceLookup services) {
        super(services);
    }

    @Override
    protected AJAXRequestResult perform(MailRequest req) throws OXException {
        final boolean cache = req.optBool("cache", false);
        if (cache && CACHABLE_FORMATS.contains(req.getRequest().getFormat())) {
            final JsonCacheService jsonCache = JsonCaches.getCache();
            if (null != jsonCache) {
                final String sha1Sum = getSha1For(req);
                final String id = "com.openexchange.mail." + sha1Sum;
                final ServerSession session = req.getSession();
                final JSONValue jsonValue = jsonCache.opt(id, session.getUserId(), session.getContextId());
                final AJAXRequestResult result;
                if (null == jsonValue) {
                    /*
                     * Check mailbox size
                     */
                    final MailServletInterface mailInterface = getMailInterface(req);
                    final String folderId = req.checkParameter(Mail.PARAMETER_MAILFOLDER);
                    if (mailInterface.getMessageCount(folderId) <= mailInterface.getMailConfig().getMailProperties().getMailFetchLimit()) {
                        /*
                         * Mailbox considered small enough for direct hand-off
                         */
                        return perform0(req, mailInterface, false);
                    }
                    /*
                     * Return empty array immediately
                     */
                    result = new AJAXRequestResult(new JSONArray(0), "json");
                    result.setResponseProperty("cached", Boolean.TRUE);
                } else {
                    result = new AJAXRequestResult(jsonValue, "json");
                    result.setResponseProperty("cached", Boolean.TRUE);
                }
                /*-
                 * Update cache with separate thread
                 */
                final AJAXRequestData requestData = req.getRequest().copyOf();
                requestData.setProperty("mail.sha1", sha1Sum);
                requestData.setProperty("mail.sha1calc", this);
                requestData.setProperty(id, jsonValue);
                final MailRequest mailRequest = new MailRequest(requestData, session);
                final Runnable r = new Runnable() {

                    @Override
                    public void run() {
                        final ServerSession session = mailRequest.getSession();
                        MailServletInterface mailInterface = null;
                        boolean locked = false;
                        try {
                            if (!jsonCache.lock(id, session.getUserId(), session.getContextId())) {
                                // Couldn't acquire lock
                                return;
                            }
                            locked = true;
                            mailInterface = MailServletInterface.getInstance(session);
                            final AJAXRequestResult requestResult = perform0(mailRequest, mailInterface, true);
                            MailConverter.getInstance().convert(mailRequest.getRequest(), requestResult, session, null);
                        } catch (Exception e) {
                            // Something went wrong
                            try {
                                jsonCache.delete(id, session.getUserId(), session.getContextId());
                            } catch (Exception ignore) {
                                // Ignore
                            }
                        } finally {
                            if (null != mailInterface) {
                                try {
                                    mailInterface.close(true);
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                            if (locked) {
                                try {
                                    jsonCache.unlock(id, session.getUserId(), session.getContextId());
                                } catch (Exception e) {
                                    // Ignore
                                }
                            }
                        }
                    }
                };
                ThreadPools.getThreadPool().submit(ThreadPools.trackableTask(r));
                /*
                 * Return cached JSON result
                 */
                return result;
            }
        }
        /*
         * Perform
         */
        return perform0(req, getMailInterface(req), false);
    }

    protected AJAXRequestResult perform0(MailRequest req, MailServletInterface mailInterface, boolean cache) throws OXException {
        try {
            // Read parameters
            String folderId = req.checkParameter(Mail.PARAMETER_MAILFOLDER);
            ColumnCollection columnCollection = req.checkColumnsAndHeaders(true);
            int[] columns = columnCollection.getFields();
            String[] headers = columnCollection.getHeaders();
            String sort = req.getParameter(AJAXServlet.PARAMETER_SORT);
            String order = req.getParameter(AJAXServlet.PARAMETER_ORDER);
            if (sort != null && order == null) {
                throw MailExceptionCode.MISSING_PARAM.create(AJAXServlet.PARAMETER_ORDER);
            }
            final int[] fromToIndices;
            {
                final String s = req.getParameter(AJAXServlet.PARAMETER_LIMIT);
                if (null == s) {
                    final int leftHandLimit = req.optInt(AJAXServlet.LEFT_HAND_LIMIT);
                    final int rightHandLimit = req.optInt(AJAXServlet.RIGHT_HAND_LIMIT);
                    if (leftHandLimit == MailRequest.NOT_FOUND || rightHandLimit == MailRequest.NOT_FOUND) {
                        fromToIndices = null;
                    } else {
                        fromToIndices = new int[] { leftHandLimit < 0 ? 0 : leftHandLimit, rightHandLimit < 0 ? 0 : rightHandLimit };
                        if (fromToIndices[0] >= fromToIndices[1]) {
                            return new AJAXRequestResult(Collections.<MailMessage> emptyList(), "mail");
                        }
                    }
                } else {
                    int start;
                    int end;
                    try {
                        final int pos = s.indexOf(',');
                        if (pos < 0) {
                            start = 0;
                            final int i = Integer.parseInt(s.trim());
                            end = i < 0 ? 0 : i;
                        } else {
                            int i = Integer.parseInt(s.substring(0, pos).trim());
                            start = i < 0 ? 0 : i;
                            i = Integer.parseInt(s.substring(pos + 1).trim());
                            end = i < 0 ? 0 : i;
                        }
                    } catch (NumberFormatException e) {
                        throw MailExceptionCode.INVALID_INT_VALUE.create(e, s);
                    }
                    if (start >= end) {
                        return new AJAXRequestResult(Collections.<MailMessage> emptyList(), "mail");
                    }
                    fromToIndices = new int[] { start, end };
                }
            }

            final boolean ignoreSeen = req.optBool("unseen");
            final boolean ignoreDeleted = getIgnoreDeleted(req, false);
            final boolean filterApplied = (ignoreSeen || ignoreDeleted);
            columns = prepareColumns(columns);
            /*
             * Get mail interface
             */
            OrderDirection orderDirection = OrderDirection.ASC;
            if (order != null) {
                if (order.equalsIgnoreCase("asc")) {
                    orderDirection = OrderDirection.ASC;
                } else if (order.equalsIgnoreCase("desc")) {
                    orderDirection = OrderDirection.DESC;
                } else {
                    throw AjaxExceptionCodes.INVALID_PARAMETER_VALUE.create(AJAXServlet.PARAMETER_ORDER, order);
                }
            }
            /*
             * Start response
             */
            Collection<OXException> warnings = null;
            final long start = System.currentTimeMillis();
            List<MailMessage> mails = new LinkedList<MailMessage>();
            SearchIterator<MailMessage> it = null;
            try {
                /*
                 * Check for thread-sort
                 */
                int sortCol = req.getSortFieldFor(sort);
                String category_filter = req.getParameter("categoryid");
                if (filterApplied || category_filter != null) {
                    mailInterface.openFor(folderId);
                    SearchTerm<?> searchTerm;
                    {
                        SearchTerm<?> first = ignoreSeen ? new FlagTerm(MailMessage.FLAG_SEEN, false) : null;
                        SearchTerm<?> second = ignoreDeleted ? new FlagTerm(MailMessage.FLAG_DELETED, false) : null;
                        searchTerm = null != first && null != second ? new ANDTerm(first, second) : (null == first ? second : first);

                        // Check if mail categories are enabled
                        if (category_filter != null && !category_filter.equals("none") && isMailCategoriesEnabled(req.getSession())) {
                            MailCategoriesConfigService categoriesService = MailJSONActivator.SERVICES.get().getOptionalService(MailCategoriesConfigService.class);
                            if (categoriesService != null) {
                                if (category_filter.equals("general")) {
                                    // Special case with unkeyword
                                    String categoryNames[] = categoriesService.getAllFlags(req.getSession(), true, false);
                                    if (categoryNames.length != 0) {
                                        if (searchTerm != null) {
                                            searchTerm = new ANDTerm(searchTerm, new UserFlagTerm(categoryNames, false));
                                        } else {
                                            searchTerm = new UserFlagTerm(categoryNames, false);
                                        }
                                    }
                                } else {
                                    // Normal case with keyword
                                    String flag = categoriesService.getFlagByCategory(req.getSession(), category_filter);
                                    if (flag == null) {
                                        throw MailExceptionCode.INVALID_PARAMETER_VALUE.create(category_filter);
                                    }

                                    // test if category is a system category
                                    if (categoriesService.isSystemCategory(category_filter, req.getSession())) {
                                        // Add active user categories as unkeywords
                                        String[] unkeywords = categoriesService.getAllFlags(req.getSession(), true, true);
                                        if (unkeywords.length != 0) {
                                            if (searchTerm != null) {
                                                searchTerm = new ANDTerm(searchTerm, new ANDTerm(new UserFlagTerm(flag, true), new UserFlagTerm(unkeywords, false)));
                                            } else {
                                                searchTerm = new ANDTerm(new UserFlagTerm(flag, true), new UserFlagTerm(unkeywords, false));
                                            }
                                        } else {
                                            if (searchTerm != null) {
                                                searchTerm = new ANDTerm(searchTerm, new UserFlagTerm(flag, true));
                                            } else {
                                                searchTerm = new UserFlagTerm(flag, true);
                                            }
                                        }
                                    } else {
                                        if (searchTerm != null) {
                                            searchTerm = new ANDTerm(searchTerm, new UserFlagTerm(flag, true));
                                        } else {
                                            searchTerm = new UserFlagTerm(flag, true);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    IndexRange indexRange = null == fromToIndices ? IndexRange.NULL : new IndexRange(fromToIndices[0], fromToIndices[1]);
                    MailSortField sortField = MailSortField.getField(sortCol);

                    MailMessage[] result;
                    MailField[] fields = MailField.getFields(columns);
                    result = mailInterface.searchMails(folderId, indexRange, sortField, orderDirection, searchTerm, fields, headers);

                    for (MailMessage mm : result) {
                        if (null != mm) {
                            if (!mm.containsAccountId()) {
                                mm.setAccountId(mailInterface.getAccountID());
                            }
                            mails.add(mm);
                        }
                    }

                    warnings = mailInterface.getWarnings();

                } else {
                    // Get iterator
                    it = mailInterface.getAllMessages(folderId, sortCol, orderDirection.getOrder(), columns, headers, fromToIndices, AJAXRequestDataTools.parseBoolParameter("continuation", req.getRequest()));
                    for (int i = it.size(); i-- > 0;) {
                        mails.add(it.next());
                    }
                    warnings = mailInterface.getWarnings();
                }
            } finally {
                SearchIterators.close(it);
            }
            final AJAXRequestResult result = new AJAXRequestResult(mails, "mail").setDurationByStart(start);
            if (cache) {
                result.setResponseProperty("cached", Boolean.TRUE);
            }
            if (warnings != null) {
                result.addWarnings(warnings);
            }
            return result;
        } catch (RuntimeException e) {
            throw MailExceptionCode.UNEXPECTED_ERROR.create(e, e.getMessage());
        }
    }

    /**
     * Checks if given session-associated user holds <code>"mail_categories"</code> capability.
     *
     * @param session The session providing user information
     * @return <code>true</code> if mail categories are enabled; otherwise <code>false</code>
     * @throws OXException If check fails
     */
    private boolean isMailCategoriesEnabled(ServerSession session) throws OXException {
        CapabilityService capabilityService = MailJSONActivator.SERVICES.get().getService(CapabilityService.class);
        return null != capabilityService && capabilityService.getCapabilities(session).contains("mail_categories");
    }

    @Override
    public String getSha1For(MailRequest req) throws OXException {
        final String id = req.getRequest().getProperty("mail.sha1");
        if (null != id) {
            return id;
        }
        final String sha1Sum = JsonCaches.getSHA1Sum("all", req.checkParameter(Mail.PARAMETER_MAILFOLDER), req.checkParameter(AJAXServlet.PARAMETER_COLUMNS), req.getParameter(AJAXServlet.PARAMETER_SORT), req.getParameter(AJAXServlet.PARAMETER_ORDER),
            req.getParameter("limit"), req.getParameter(AJAXServlet.LEFT_HAND_LIMIT), req.getParameter(AJAXServlet.RIGHT_HAND_LIMIT), req.getParameter("unseen"), req.getParameter("deleted"));
        return sha1Sum;
    }

}
