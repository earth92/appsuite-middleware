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

package com.openexchange.chronos.schedjoules.impl;

import static com.openexchange.java.Autoboxing.I;
import static com.openexchange.java.Autoboxing.L;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.openexchange.chronos.schedjoules.SchedJoulesResult;
import com.openexchange.chronos.schedjoules.SchedJoulesService;
import com.openexchange.chronos.schedjoules.api.SchedJoulesAPI;
import com.openexchange.chronos.schedjoules.api.SchedJoulesPageField;
import com.openexchange.chronos.schedjoules.api.auxiliary.SchedJoulesAPIDefaultValues;
import com.openexchange.chronos.schedjoules.api.auxiliary.SchedJoulesCalendar;
import com.openexchange.chronos.schedjoules.api.auxiliary.SchedJoulesPage;
import com.openexchange.chronos.schedjoules.exception.SchedJoulesAPIExceptionCodes;
import com.openexchange.chronos.schedjoules.impl.cache.SchedJoulesAPICache;
import com.openexchange.chronos.schedjoules.impl.cache.SchedJoulesCachedItemKey;
import com.openexchange.chronos.schedjoules.impl.cache.loader.SchedJoulesCountriesCacheLoader;
import com.openexchange.chronos.schedjoules.impl.cache.loader.SchedJoulesLanguagesCacheLoader;
import com.openexchange.chronos.schedjoules.impl.cache.loader.SchedJoulesPageCacheLoader;
import com.openexchange.chronos.schedjoules.osgi.Services;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Autoboxing;
import com.openexchange.java.Strings;

/**
 * {@link SchedJoulesServiceImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public class SchedJoulesServiceImpl implements SchedJoulesService, Reloadable {

    private static final int LANGUAGES_ID = -1;

    private static final int COUNTRIES_ID = -2;

    private static final Logger LOG = LoggerFactory.getLogger(SchedJoulesServiceImpl.class);

    private final SchedJoulesAPICache apiCache = new SchedJoulesAPICache();

    /**
     * Pages cache
     */
    private final LoadingCache<SchedJoulesCachedItemKey, SchedJoulesPage> pagesCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(24, TimeUnit.HOURS).refreshAfterWrite(24, TimeUnit.HOURS).build(new SchedJoulesPageCacheLoader(apiCache));

    /**
     * Countries cache
     */
    private final LoadingCache<SchedJoulesCachedItemKey, SchedJoulesPage> countriesCache = CacheBuilder.newBuilder().refreshAfterWrite(24, TimeUnit.HOURS).build(new SchedJoulesCountriesCacheLoader(apiCache));

    /**
     * Languages cache
     */
    private final LoadingCache<SchedJoulesCachedItemKey, SchedJoulesPage> languagesCache = CacheBuilder.newBuilder().refreshAfterWrite(24, TimeUnit.HOURS).build(new SchedJoulesLanguagesCacheLoader(apiCache));
    /**
     * Caches the root page item ids
     */
    private final Cache<String, Integer> rootItemIdCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterAccess(24, TimeUnit.HOURS).build();

    /**
     * Black listed itemids
     */
    private List<Integer> blacklistedItems;

    /**
     * Initialises a new {@link SchedJoulesServiceImpl}.
     */
    public SchedJoulesServiceImpl() {
        super();
        initialiseBlackListedItems();
    }

    /**
     * Initialises the black-listed items {@link List}
     */
    private void initialiseBlackListedItems() {
        LeanConfigurationService leanConfig = Services.getService(LeanConfigurationService.class);
        String property = leanConfig.getProperty(SchedJoulesProperty.itemBlacklist);
        if (Strings.isEmpty(property)) {
            blacklistedItems = Collections.emptyList();
            return;
        }
        String[] split = Strings.splitByComma(property);
        List<Integer> l = new ArrayList<>(split.length);
        for (String s : split) {
            try {
                l.add(Integer.valueOf(s));
            } catch (NumberFormatException e) {
                LOG.debug("The black-listed item id '{}' is not an integer. Ignoring", s, e);
            }
        }
        blacklistedItems = Collections.unmodifiableList(l);
        invalidateCaches();
    }

    /**
     * Invalidates the pages and root cache
     */
    private void invalidateCaches() {
        pagesCache.asMap().entrySet().stream().filter(predicate -> false == blacklistedItems.contains(Integer.valueOf(predicate.getKey().getItemId()))).forEach(entry -> {
            if (entry.getValue().getItemData().isObject()) {
                removeBlackListedItems(entry.getValue().getItemData().toObject());
            }
        });
        rootItemIdCache.asMap().entrySet().stream().filter(predicate -> false == blacklistedItems.contains(predicate.getValue()));
    }

    @Override
    public SchedJoulesResult getRoot(int contextId, Set<SchedJoulesPageField> filteredFields) throws OXException {
        return getRoot(contextId, SchedJoulesAPIDefaultValues.DEFAULT_LOCALE, SchedJoulesAPIDefaultValues.DEFAULT_LOCATION, filteredFields);
    }

    @Override
    public SchedJoulesResult getRoot(int contextId, String locale, String location, Set<SchedJoulesPageField> filteredFields) throws OXException {
        try {
            int itemId = rootItemIdCache.get(location, () -> {
                SchedJoulesAPI api = apiCache.getAPI(contextId);
                SchedJoulesPage rootPage = api.pages().getRootPage(locale, location);

                JSONObject itemData = (JSONObject) rootPage.getItemData();
                int rootPageItemId = itemData.getInt("item_id");

                pagesCache.put(new SchedJoulesCachedItemKey(contextId, rootPageItemId, locale), rootPage);
                return I(rootPageItemId);
            }).intValue();
            return getPage(contextId, itemId, locale, filteredFields);
        } catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @Override
    public SchedJoulesResult getPage(int contextId, int pageId, Set<SchedJoulesPageField> filteredFields) throws OXException {
        return getPage(contextId, pageId, SchedJoulesAPIDefaultValues.DEFAULT_LOCALE, filteredFields);
    }

    @Override
    public SchedJoulesResult getPage(int contextId, int pageId, String locale, Set<SchedJoulesPageField> filteredFields) throws OXException {
        if (blacklistedItems.contains(Integer.valueOf(pageId))) {
            throw SchedJoulesAPIExceptionCodes.PAGE_NOT_FOUND.create();
        }
        try {
            JSONObject content = (JSONObject) pagesCache.get(new SchedJoulesCachedItemKey(contextId, pageId, locale)).getItemData();
            filterContent(content, SchedJoulesPageField.toSring(filteredFields));
            return new SchedJoulesResult(content);
        } catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @Override
    public SchedJoulesResult listCountries(int contextId, String locale) throws OXException {
        try {
            return new SchedJoulesResult(countriesCache.get(new SchedJoulesCachedItemKey(contextId, COUNTRIES_ID, locale)).getItemData());
        } catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @Override
    public SchedJoulesResult listLanguages(int contextId) throws OXException {
        try {
            return new SchedJoulesResult(languagesCache.get(new SchedJoulesCachedItemKey(contextId, LANGUAGES_ID, null)).getItemData());
        } catch (ExecutionException e) {
            throw handleExecutionException(e);
        }
    }

    @Override
    public SchedJoulesResult search(int contextId, String query, String locale, int countryId, int categoryId, int maxRows, Set<SchedJoulesPageField> filteredFields) throws OXException {
        return new SchedJoulesResult(filterContent((JSONObject) apiCache.getAPI(contextId).pages().search(query, locale, countryId, categoryId, maxRows).getItemData(), SchedJoulesPageField.toSring(filteredFields)));
    }

    @Override
    public SchedJoulesCalendar getCalendar(int contextId, URL url, String etag, long lastModified) throws OXException {
        return apiCache.getAPI(contextId).calendar().getCalendar(url, etag, lastModified);
    }

    @Override
    public boolean isAvailable(int contextId) {
        try {
            apiCache.getAPI(contextId);
            return true;
        } catch (OXException e) {
            LOG.debug("No SchedJoules API available for context {}: {}", Autoboxing.I(contextId), e.getMessage());
            return false;
        }
    }

    ///////////////////////////////////// HELPERS ///////////////////////////////////

    /**
     * Filters the specified {@link JSONObject}
     *
     * @param content the {@link JSONObject} to filter
     * @param filteredFields the fields to remove
     * @return the filtered {@link JSONObject}
     * @throws OXException if a JSON error is occurred
     */
    private JSONObject filterContent(JSONObject content, Set<String> filteredFields) throws OXException {
        if (filteredFields == null || filteredFields.isEmpty()) {
            return content;
        }
        try {
            if (LOG.isTraceEnabled()) {
                long startTime = System.currentTimeMillis();
                removeBlackListedItems(content);
                filterJSONObject(content, filteredFields);
                LOG.trace("Filtered content in {} msec.", L(System.currentTimeMillis() - startTime));
            } else {
                removeBlackListedItems(content);
                filterJSONObject(content, filteredFields);
            }
            return content;
        } catch (JSONException e) {
            throw SchedJoulesAPIExceptionCodes.JSON_ERROR.create(e);
        }
    }

    /**
     * Removes any black-listed items from the content
     *
     * @param content The content from which to remove the black-listed items
     */
    private void removeBlackListedItems(JSONObject content) {
        JSONArray pageSections = content.optJSONArray(SchedJoulesPageField.PAGE_SECTIONS.getFieldName());
        if (pageSections == null || pageSections.isEmpty()) {
            return;
        }

        Iterator<Object> iterator = pageSections.iterator();
        while (iterator.hasNext()) {
            JSONObject obj = (JSONObject) iterator.next();
            JSONArray items = obj.optJSONArray(SchedJoulesPageField.ITEMS.getFieldName());
            if (items == null || items.isEmpty()) {
                continue;
            }
            Iterator<Object> itemsIterator = items.iterator();
            while (itemsIterator.hasNext()) {
                JSONObject next = (JSONObject) itemsIterator.next();
                JSONObject item = next.optJSONObject(SchedJoulesPageField.ITEM.getFieldName());
                if (item == null || item.isEmpty()) {
                    continue;
                }
                int itemId = item.optInt(SchedJoulesPageField.ITEM_ID.getFieldName());
                if (blacklistedItems.contains(Integer.valueOf(itemId))) {
                    itemsIterator.remove();
                }
            }
        }
    }

    /**
     * Filters the specified {@link JSONObject} and removes the specified fields
     *
     * @param content the {@link JSONObject} to filter
     * @return the filtered {@link JSONObject}
     * @throws OXException if a JSON error is occurred
     */
    private void filterJSONObject(JSONObject object, Set<String> filteredFields) throws JSONException, OXException {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (filteredFields.contains(key)) {
                keys.remove();
            } else {
                filterObject(object.get(key), filteredFields);
            }
        }
    }

    /**
     * Filters the specified {@link JSONArray}
     *
     * @param array The {@link JSONArray} to filter
     * @throws OXException if a JSON error is occurred
     * @throws JSONException if JSON parsing error is occurred
     */
    private void filterJSONArray(JSONArray array, Set<String> filteredFields) throws OXException, JSONException {
        for (int index = 0; index < array.length(); index++) {
            filterObject(array.get(index), filteredFields);
        }
    }

    /**
     * Filters the specified {@link JSONObject}
     *
     * @param array The {@link JSONObject} to filter
     * @throws OXException if a JSON error is occurred
     * @throws JSONException if JSON parsing error is occurred
     */
    private void filterObject(Object obj, Set<String> filteredFields) throws JSONException, OXException {
        if (obj instanceof JSONObject) {
            filterJSONObject((JSONObject) obj, filteredFields);
        } else if (obj instanceof JSONArray) {
            filterJSONArray((JSONArray) obj, filteredFields);
        }
    }

    /**
     * Handles the specified {@link ExecutionException}. If the cause of the exception is
     * an {@link OXException} then the cause is thrown, otherwise the {@link ExecutionException} is
     * wrapped in a the {@link SchedJoulesAPIExceptionCodes#UNEXPECTED_ERROR} exception.
     *
     * @param e the {@link ExecutionException} to handle
     * @return the wrapped {@link OXException}
     */
    private OXException handleExecutionException(ExecutionException e) {
        if (e.getCause() != null && e.getCause() instanceof OXException) {
            return (OXException) e.getCause();
        }
        return SchedJoulesAPIExceptionCodes.UNEXPECTED_ERROR.create(e, e.getMessage());
    }

    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        initialiseBlackListedItems();
    }

    @Override
    public Interests getInterests() {
        return DefaultInterests.builder().propertiesOfInterest(SchedJoulesProperty.itemBlacklist.getFQPropertyName()).build();
    }
}
