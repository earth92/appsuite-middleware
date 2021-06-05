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

package com.openexchange.mail.categories.impl;

import static com.openexchange.java.Autoboxing.B;
import static com.openexchange.java.Autoboxing.I;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import com.openexchange.capabilities.Capability;
import com.openexchange.capabilities.CapabilityService;
import com.openexchange.config.cascade.ConfigProperty;
import com.openexchange.config.cascade.ConfigView;
import com.openexchange.config.cascade.ConfigViewFactory;
import com.openexchange.config.cascade.ConfigViewScope;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.mail.categories.MailCategoriesConfigService;
import com.openexchange.mail.categories.MailCategoriesConstants;
import com.openexchange.mail.categories.MailCategoriesExceptionCodes;
import com.openexchange.mail.categories.MailCategoryConfig;
import com.openexchange.mail.categories.MailCategoryConfig.Builder;
import com.openexchange.mail.categories.MailObjectParameter;
import com.openexchange.mail.categories.ReorganizeParameter;
import com.openexchange.mail.categories.impl.osgi.Services;
import com.openexchange.mail.categories.organizer.MailCategoriesOrganizeExceptionCodes;
import com.openexchange.mail.categories.organizer.MailCategoriesOrganizer;
import com.openexchange.mail.categories.ruleengine.MailCategoriesRuleEngine;
import com.openexchange.mail.categories.ruleengine.MailCategoriesRuleEngineExceptionCodes;
import com.openexchange.mail.categories.ruleengine.MailCategoryRule;
import com.openexchange.mail.categories.ruleengine.RuleType;
import com.openexchange.mail.search.ANDTerm;
import com.openexchange.mail.search.HeaderTerm;
import com.openexchange.mail.search.ORTerm;
import com.openexchange.mail.search.SearchTerm;
import com.openexchange.mailaccount.Account;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.session.Session;
import com.openexchange.threadpool.AbstractTask;
import com.openexchange.threadpool.ThreadPoolService;

/**
 * {@link MailCategoriesConfigServiceImpl}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @since v7.8.2
 */
public class MailCategoriesConfigServiceImpl implements MailCategoriesConfigService {

    static final String INIT_TASK_STATUS_PROPERTY = "com.openexchange.mail.categories.ruleengine.sieve.init.run";

    private final static String FLAG_PREFIX = "$ox_";
    private static final String FROM_HEADER = "from";

    static final String FULLNAME = com.openexchange.mail.utils.MailFolderUtility.prepareFullname(Account.DEFAULT_ID, "INBOX");
    private static final String RULE_DEFINITION_PROPERTY_PREFIX = "com.openexchange.mail.categories.rules.";
    private static final String STATUS_NOT_YET_STARTED = "notyetstarted";
    private static final String STATUS_ERROR = "error";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_FINISHED = "finished";

    // ----------------------------------------------------------------------------------------------------------------------------------

    private final MailCategoriesRuleEngine ruleEngine;

    /**
     * Initializes a new {@link MailCategoriesConfigServiceImpl}.
     *
     * @param The rule engine to use
     */
    public MailCategoriesConfigServiceImpl(MailCategoriesRuleEngine ruleEngine) {
        super();
        this.ruleEngine = ruleEngine;
    }

    /**
     * Gets the rule engine
     *
     * @return The rule engine
     */
    public MailCategoriesRuleEngine getRuleEngine() {
        return ruleEngine;
    }

    @Override
    public List<MailCategoryConfig> getAllCategories(Session session, Locale locale, boolean onlyEnabled, boolean includeGeneral) throws OXException {
        String[] categories = getSystemCategoryNames(session);
        String[] userCategories = getUserCategoryNames(session);
        List<MailCategoryConfig> result = new ArrayList<>(categories.length);

        if (includeGeneral) {
            String name = getLocalizedName(session, locale, MailCategoriesConstants.GENERAL_CATEGORY_ID);
            String description = getLocalizedDescription(session, locale, MailCategoriesConstants.GENERAL_CATEGORY_ID);

            MailCategoryConfig generalConfig = new MailCategoryConfig.Builder().category(MailCategoriesConstants.GENERAL_CATEGORY_ID).isSystemCategory(true).enabled(true).force(true).name(name).description(description).build();
            result.add(generalConfig);
        }

        if (categories.length == 0 && userCategories.length == 0) {
            return new ArrayList<>();
        }

        for (String category : categories) {
            MailCategoryConfig config = getConfigByCategory(session, locale, category);
            if (onlyEnabled && !config.isActive()) {
                continue;
            }
            result.add(config);
        }
        for (String category : userCategories) {
            MailCategoryConfig config = getUserConfigByCategory(session, locale, category);
            if (onlyEnabled && !config.isActive()) {
                continue;
            }
            result.add(config);
        }
        return result;
    }

    @Override
    public String[] getAllFlags(Session session, boolean onlyEnabled, boolean onlyUserCategories) throws OXException {
        if (onlyUserCategories) {
            String[] userCategories = getUserCategoryNames(session);
            if (userCategories.length == 0) {
                return new String[0];
            }
            ArrayList<String> result = new ArrayList<>(userCategories.length);
            for (String category : userCategories) {
                if (onlyEnabled) {
                    boolean active = MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_ACTIVE, true, session);
                    if (!active) {
                        continue;
                    }
                }
                result.add(MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session));
            }

            return result.toArray(new String[result.size()]);
        }

        // Include system categories
        String[] categories = getSystemCategoryNames(session);
        String[] userCategories = getUserCategoryNames(session);
        if (categories.length == 0 && userCategories.length == 0) {
            return new String[0];
        }

        ArrayList<String> result = new ArrayList<>(categories.length + userCategories.length);
        for (String category : categories) {
            if (onlyEnabled) {
                boolean active = MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_ACTIVE, true, session);
                if (!active) {
                    boolean forced = MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FORCE, false, session);
                    if (!forced) {
                        continue;
                    }
                }
            }
            result.add(MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session));
        }
        for (String category : userCategories) {
            if (onlyEnabled) {
                boolean active = MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_ACTIVE, true, session);
                if (!active) {
                    continue;
                }
            }
            result.add(MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session));
        }

        return result.toArray(new String[result.size()]);
    }

    private MailCategoryConfig getConfigByCategory(Session session, Locale locale, String category) throws OXException {
        Builder builder = new Builder();
        builder.category(category);
        String name = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_NAME, null, session);
        if (Strings.isEmpty(name)) {
            name = getLocalizedName(session, locale, category);
        }
        builder.name(name);
        String description = getLocalizedDescription(session, locale, category);
        builder.description(description);
        builder.enabled(MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_ACTIVE, true, session));
        builder.force(MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FORCE, false, session));
        builder.flag(MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session));
        builder.isSystemCategory(isSystemCategory(category, session));
        MailCategoryConfig result = builder.build();
        if (result.getFlag() == null) {
            throw MailCategoriesExceptionCodes.INVALID_CONFIGURATION_EXTENDED.create(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG);
        }

        return result;
    }

    /**
     * Gets the user configuration for the given category
     *
     * @param session The users {@link Session}
     * @param locale The locale to use
     * @param category The name of the category
     * @return The {@link MailCategoryConfig}
     * @throws OXException
     */
    private MailCategoryConfig getUserConfigByCategory(Session session, Locale locale, String category) throws OXException {
        Builder builder = new Builder();
        builder.category(category);
        String name = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_NAME, null, session);
        if (Strings.isEmpty(name)) {
            name = getLocalizedName(session, locale, category);
        }
        builder.name(name);
        builder.enabled(MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_ACTIVE, true, session));
        builder.flag(MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session));
        builder.isSystemCategory(false);
        MailCategoryConfig result = builder.build();
        return result;
    }

    @Override
    public String getFlagByCategory(Session session, String category) throws OXException {
        return MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, null, session);
    }

    /**
     * Gets the localized name for the given category
     *
     * @param session The users session
     * @param locale The {@link Locale} to use
     * @param category The category name
     * @return The localized name. If the {@link Locale} is not available the fallback name is returned instead
     * @throws OXException
     */
    private String getLocalizedName(Session session, Locale locale, String category) throws OXException {

        String translation = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_NAME_LANGUAGE_PREFIX + locale.toString(), null, session);
        if (translation != null && !translation.isEmpty()) {
            return translation;
        }
        return MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FALLBACK, category, session);
    }

    /**
     * Gets the localized description for the given category
     *
     * @param session The users session
     * @param locale The {@link Locale} to use
     * @param category The category name
     * @return The localized description. If the {@link Locale} is not available the fallback description is returned instead
     * @throws OXException
     */
    private String getLocalizedDescription(Session session, Locale locale, String category) throws OXException {

        String translation = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_DESCRIPTION_LANGUAGE_PREFIX + locale.toString(), null, session);
        if (translation != null && !translation.isEmpty()) {
            return translation;
        }
        return MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_DESCRIPTION, null, session);
    }

    /**
     * Gets the names of the system categories
     *
     * @param session The current users {@link Session}
     * @return An array of the system categories for the users
     * @throws OXException
     */
    String[] getSystemCategoryNames(Session session) throws OXException {
        String categoriesString = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_IDENTIFIERS, null, session);
        if (Strings.isEmpty(categoriesString)) {
            return new String[0];
        }

        String result[] = Strings.splitByComma(categoriesString);
        return result == null ? new String[0] : result;
    }

    /**
     * Gets the names of the users categories
     *
     * @param session The users {@link Session}
     * @return An array of user category names
     * @throws OXException
     */
    private String[] getUserCategoryNames(Session session) throws OXException {
        String categoriesString = MailCategoriesConfigUtil.getValueFromProperty(MailCategoriesConstants.MAIL_USER_CATEGORIES_IDENTIFIERS, null, session);
        if (Strings.isEmpty(categoriesString)) {
            return new String[0];
        }

        String result[] = Strings.splitByComma(categoriesString);
        return result == null ? new String[0] : result;
    }

    /**
     * Checks if underlying rule engine is applicable for specified session.
     *
     * @param session The session to check for
     * @return <code>true</code> if rule engine is applicable; otherwise <code>false</code>
     * @throws OXException If check fort applicability fails
     */
    public boolean isRuleEngineApplicable(Session session) throws OXException {
        return ruleEngine.isApplicable(session);
    }

    @Override
    public boolean isSystemCategory(String category, Session session) throws OXException {
        String[] systemCategories = getSystemCategoryNames(session);
        for (String systemCategory : systemCategories) {
            if (category.equals(systemCategory)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isEnabled(Session session) throws OXException {
        return MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_SWITCH, true, session);
    }

    @Override
    public boolean isForced(Session session) throws OXException {
        return MailCategoriesConfigUtil.getBoolFromProperty(MailCategoriesConstants.MAIL_CATEGORIES_FORCE_SWITCH, false, session);
    }

    @Override
    public void enable(Session session, boolean enable) throws OXException {
        MailCategoriesConfigUtil.setProperty(MailCategoriesConstants.MAIL_CATEGORIES_SWITCH, String.valueOf(enable), session);
        if (enable) {
            initMailCategories(session);
        }
    }

    /**
     * Generates the flag name for the given category
     *
     * @param category The category
     * @return The flag term
     */
    String generateFlag(String category) {
        StringBuilder builder = new StringBuilder(FLAG_PREFIX);
        builder.append(category.hashCode());
        return builder.toString();
    }

    /**
     * Sets the given rule
     *
     * @param session The users {@link Session}
     * @param category The category this rule applies to to perform some checks.
     * @param rule The rule to set
     * @throws OXException
     */
    private void setRule(Session session, String category, MailCategoryRule rule) throws OXException {
        String flag = null;
        if (!category.equals(MailCategoriesConstants.GENERAL_CATEGORY_ID)) {
            flag = getFlagByCategory(session, category);
            if (Strings.isEmpty(flag)) {
                flag = generateFlag(category);
            }
        }
        if ((flag == null && rule.getFlag() != null) || (flag != null && !flag.equals(rule.getFlag()))) {
            throw MailCategoriesRuleEngineExceptionCodes.INVALID_RULE.create();
        }
        ruleEngine.setRule(session, rule, RuleType.CATEGORY);
    }

    /**
     * Gets the rule for the given category
     *
     * @param session The users {@link Session}
     * @param category The category name
     * @return The {@link MailCategoryRule}
     * @throws OXException
     */
    private MailCategoryRule getRule(Session session, String category) throws OXException {
        String flag = null;
        if (!category.equals(MailCategoriesConstants.GENERAL_CATEGORY_ID)) {
            flag = getFlagByCategory(session, category);
            if (Strings.isEmpty(flag)) {
                flag = generateFlag(category);
                MailCategoriesConfigUtil.setProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + category + MailCategoriesConstants.MAIL_CATEGORIES_FLAG, flag, session);
            }
        }
        return ruleEngine.getRule(session, flag);
    }

    /**
     * Removes the mail address from all rules
     *
     * @param session The users session
     * @param mailAddress The mail address to remove
     * @throws OXException
     */
    private void removeMailFromRules(Session session, String mailAddress) throws OXException {
        ruleEngine.removeValueFromHeader(session, mailAddress, "from");
    }

    /**
     * Creates the search term for the given rule
     *
     * @param rule The {@link MailCategoryRule}
     * @return The {@link SearchTerm}
     */
    SearchTerm<?> getSearchTerm(MailCategoryRule rule) {
        if (!rule.hasSubRules()) {
            if (rule.getHeaders().size() == 1 && rule.getValues().size() == 1) {

                return new HeaderTerm(rule.getHeaders().get(0), rule.getValues().get(0));
            }
            SearchTerm<?> result = null;
            for (String header : rule.getHeaders()) {
                for (String value : rule.getValues()) {
                    if (result == null) {
                        result = new HeaderTerm(header, value);
                    } else {
                        result = new ORTerm(result, new HeaderTerm(header, value));
                    }
                }
            }
            return result;

        }

        SearchTerm<?> result = null;
        if (rule.isAND()) {
            for (MailCategoryRule subRule : rule.getSubRules()) {
                result = result == null ? getSearchTerm(subRule) : new ANDTerm(result, getSearchTerm(subRule));
            }
        } else {
            for (MailCategoryRule subRule : rule.getSubRules()) {
                result = result == null ? getSearchTerm(subRule) : new ORTerm(result, getSearchTerm(subRule));
            }
        }
        return result;
    }

    @Override
    public void trainCategory(String category, List<String> addresses, boolean createRule, ReorganizeParameter reorganize, Session session) throws OXException {
        if (!createRule && !reorganize.isReorganize()) {
            // nothing to do
            return;
        }

        String flag = null;
        if (!category.equals(MailCategoriesConstants.GENERAL_CATEGORY_ID)) {
            flag = getFlagByCategory(session, category);
            if (Strings.isEmpty(flag)) {
                flag = generateFlag(category);
            }
        }

        MailCategoryRule newRule = null;
        if (createRule) {
            // create new rule

            // Remove from old rules
            for (String mailAddress : addresses) {
                removeMailFromRules(session, mailAddress);
            }

            // Update rule
            MailCategoryRule oldRule = getRule(session, category);
            for (String mailAddress : addresses) {
                if (newRule == null) {
                    if (null == oldRule) {        // Create new rule
                        newRule = new MailCategoryRule(Collections.singletonList(FROM_HEADER), new ArrayList<>(Collections.singleton(mailAddress)), flag);
                    } else {

                        if (oldRule.getSubRules() != null && !oldRule.getSubRules().isEmpty()) {
                            if (oldRule.isAND()) {
                                newRule = new MailCategoryRule(flag, false);
                                newRule.addSubRule(oldRule);
                                newRule.addSubRule(new MailCategoryRule(Collections.singletonList(FROM_HEADER), new ArrayList<>(Collections.singleton(mailAddress)), flag));
                            } else {
                                newRule = oldRule;
                                newRule.addSubRule(new MailCategoryRule(Collections.singletonList(FROM_HEADER), new ArrayList<>(Collections.singleton(mailAddress)), flag));
                            }
                        } else {
                            if (!oldRule.getHeaders().contains(FROM_HEADER)) {
                                oldRule.getHeaders().add(FROM_HEADER);
                            }
                            if (!oldRule.getValues().contains(mailAddress)) {
                                oldRule.getValues().add(mailAddress);
                            }
                            newRule = oldRule;
                        }

                    }
                } else {
                    if (!newRule.getValues().contains(mailAddress)) {
                        newRule.getValues().add(mailAddress);
                    }
                }
            }

            if (newRule != null) {
                // remove all previous category flags
                String[] flagsToRemove = getAllFlags(session, false, false);
                newRule.addFlagsToRemove(flagsToRemove);
                setRule(session, category, newRule);
            }

        } else {
            // create rule for reorganize only
            for (String mailAddress : addresses) {
                if (newRule == null) {
                    newRule = new MailCategoryRule(Collections.singletonList(FROM_HEADER), new ArrayList<>(Collections.singleton(mailAddress)), flag);
                } else {
                    if (!newRule.getValues().contains(mailAddress)) {
                        newRule.getValues().add(mailAddress);
                    }
                }
            }

            if (newRule != null) {
                // remove all previous category flags
                String[] flagsToRemove = getAllFlags(session, false, false);
                newRule.addFlagsToRemove(flagsToRemove);
            }
        }

        // Reorganize if necessary
        if (reorganize.isReorganize()) {
            List<OXException> warnings = reorganize.getWarnings();
            try {
                boolean supported = ruleEngine.applyRule(session, newRule);
                if (supported == false) {
                    SearchTerm<?> searchTerm = getSearchTerm(newRule);
                    if (searchTerm != null && newRule != null) {
                        MailCategoriesOrganizer.organizeExistingMails(session, FULLNAME, searchTerm, flag, newRule.getFlagsToRemove());
                    }
                }
            } catch (@SuppressWarnings("unused") OXException e) {
                if (warnings.isEmpty()) {
                    warnings.add(MailCategoriesOrganizeExceptionCodes.UNABLE_TO_ORGANIZE.create());
                }
            }
            EventAdmin eventAdmin = Services.optService(EventAdmin.class);
            if (eventAdmin != null) {
                Dictionary<String, Object> dic = new Hashtable<>(2);
                dic.put(PROP_USER_ID, I(session.getUserId()));
                dic.put(PROP_CONTEXT_ID, I(session.getContextId()));
                Event event = new Event(TOPIC_REORGANIZE, dic);
                eventAdmin.postEvent(event);
            }

        }

    }

    @Override
    public void updateConfigurations(List<MailCategoryConfig> configs, Session session, Locale locale) throws OXException {
        List<MailCategoryConfig> oldConfigs = getAllCategories(session, locale, false, true);

        for (MailCategoryConfig newConfig : configs) {

            int index = oldConfigs.indexOf(newConfig);
            if (index >= 0) {
                MailCategoryConfig oldConfig = oldConfigs.get(index);
                boolean rename = false;
                boolean switchStatus = false;
                if (newConfig.isEnabled() != oldConfig.isActive() && newConfig.isEnabled() != oldConfig.isEnabled()) {
                    if (oldConfig.isForced()) {
                        throw MailCategoriesExceptionCodes.SWITCH_NOT_ALLOWED.create(oldConfig.getCategory());
                    }
                    switchStatus = true;
                }
                String name = oldConfig.getName();
                if (!newConfig.getName().equals(name)) {
                    if (isSystemCategory(oldConfig.getCategory(), session)) {
                        throw MailCategoriesExceptionCodes.CHANGE_NAME_NOT_ALLOWED.create(oldConfig.getCategory());
                    }
                    rename = true;
                }

                if (switchStatus) {
                    MailCategoriesConfigUtil.activateProperty(oldConfig.getCategory(), newConfig.isEnabled(), session);
                }
                if (rename) {
                    MailCategoriesConfigUtil.setProperty(MailCategoriesConstants.MAIL_CATEGORIES_PREFIX + oldConfig.getCategory() + MailCategoriesConstants.MAIL_CATEGORIES_NAME, newConfig.getName(), session);
                }
            } else {
                throw MailCategoriesExceptionCodes.USER_CATEGORY_DOES_NOT_EXIST.create(newConfig.getCategory());
            }
        }

    }

    @Override
    public void addMails(Session session, List<MailObjectParameter> mails, String category) throws OXException {
        String flag = getFlagByCategory(session, category);
        String[] allFlags = getAllFlags(session, false, false);
        MailCategoriesOrganizer.organizeMails(session, FULLNAME, mails, flag, allFlags);
    }

    @Override
    public String getInitStatus(Session session) throws OXException {
        return MailCategoriesConfigUtil.getValueFromProperty(INIT_TASK_STATUS_PROPERTY, STATUS_NOT_YET_STARTED, session);
    }

    /**
     * Initializes mail categories
     *
     * @param session The users {@link Session}
     * @throws OXException
     */
    void initMailCategories(Session session) throws OXException {
        CapabilityService capService = Services.getService(CapabilityService.class);
        if (!capService.getCapabilities(session).contains(new Capability("mail_categories"))) {
            return;
        }

        MailCategoriesRuleEngine engine = Services.getService(MailCategoriesRuleEngine.class);
        String flags[] = getAllFlags(session, false, false);
        engine.cleanUp(Arrays.asList(flags), session);

        ConfigViewFactory configViewFactory = Services.getService(ConfigViewFactory.class);
        if (configViewFactory == null) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(ConfigViewFactory.class);
        }
        ConfigView view = configViewFactory.getView(session.getUserId(), session.getContextId());
        Boolean apply = view.get(MailCategoriesConstants.APPLY_OX_RULES_PROPERTY, Boolean.class);

        if (apply == null || !apply.booleanValue()) {
            return;
        }

        final ConfigProperty<String> hasRun = view.property(ConfigViewScope.USER.getScopeName(), MailCategoriesConfigServiceImpl.INIT_TASK_STATUS_PROPERTY, String.class);
        String currentStatus = hasRun.get();
        if (hasRun.isDefined() && (currentStatus.equals(STATUS_RUNNING) || currentStatus.equals(STATUS_FINISHED))) {
            return;
        }
        ThreadPoolService threadPoolService = Services.getService(ThreadPoolService.class);
        threadPoolService.submit(new InitTask(this, session, hasRun));

    }

    /**
     *
     * {@link InitTask} initializes mail category rules based on the configuration
     *
     * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
     * @since v7.10.2
     */
    private static final class InitTask extends AbstractTask<Boolean> {

        private final MailCategoriesConfigServiceImpl mailCategoriesService;
        private final Session session;
        private final ConfigProperty<String> hasRun;

        /**
         * Initializes a new {@link MailCategoriesConfigServiceImpl.InitTask}.
         */
        InitTask(MailCategoriesConfigServiceImpl mailCategoriesService, Session session, ConfigProperty<String> hasRun) {
            super();
            this.mailCategoriesService = mailCategoriesService;
            this.session = session;
            this.hasRun = hasRun;
        }

        @Override
        public Boolean call() throws Exception {
            boolean success = false;
            try {
                hasRun.set(STATUS_RUNNING);

                String categoryNames[] = mailCategoriesService.getSystemCategoryNames(session);
                List<MailCategoryRule> rules = new ArrayList<>();
                for (String categoryName : categoryNames) {
                    String propValue = MailCategoriesConfigUtil.getValueFromProperty(RULE_DEFINITION_PROPERTY_PREFIX + categoryName, "", session);
                    if (propValue.length() == 0) {
                        continue;
                    }
                    String addresses[] = Strings.splitByComma(propValue.trim());
                    String flag = mailCategoriesService.getFlagByCategory(session, categoryName);
                    if (flag == null) {
                        flag = mailCategoriesService.generateFlag(categoryName);
                    }
                    MailCategoryRule rule = new MailCategoryRule(Collections.singletonList(FROM_HEADER), Arrays.asList(addresses), flag);
                    rules.add(rule);
                }
                mailCategoriesService.getRuleEngine().initRuleEngineForUser(session, rules);
                for (MailCategoryRule rule : rules) {
                    boolean supported = mailCategoriesService.getRuleEngine().applyRule(session, rule);
                    if (supported == false) {
                        SearchTerm<?> searchTerm = mailCategoriesService.getSearchTerm(rule);
                        MailCategoriesOrganizer.organizeExistingMails(session, FULLNAME, searchTerm, rule.getFlag(), null);
                    }
                }

                success = true;
                hasRun.set(STATUS_FINISHED);
            } finally {
                if (false == success) {
                    try {
                        hasRun.set(STATUS_ERROR);
                    } catch (OXException ox) {
                    }
                }
            }
            return B(success);
        }

    }

}
