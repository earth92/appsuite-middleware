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

package com.openexchange.mail.categories.sieve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import org.apache.jsieve.SieveException;
import org.apache.jsieve.TagArgument;
import org.apache.jsieve.parser.generated.Token;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.jsieve.commands.ActionCommand;
import com.openexchange.jsieve.commands.Command;
import com.openexchange.jsieve.commands.IfCommand;
import com.openexchange.jsieve.commands.Rule;
import com.openexchange.jsieve.commands.RuleComment;
import com.openexchange.jsieve.commands.TestCommand;
import com.openexchange.jsieve.commands.TestCommand.Commands;
import com.openexchange.mail.FullnameArgument;
import com.openexchange.mail.api.IMailFolderStorage;
import com.openexchange.mail.api.IMailMessageStorage;
import com.openexchange.mail.api.IMailMessageStorageMailFilterApplication;
import com.openexchange.mail.api.MailAccess;
import com.openexchange.mail.categories.MailCategoriesConstants;
import com.openexchange.mail.categories.MailCategoriesExceptionCodes;
import com.openexchange.mail.categories.ruleengine.MailCategoriesRuleEngine;
import com.openexchange.mail.categories.ruleengine.MailCategoriesRuleEngineExceptionCodes;
import com.openexchange.mail.categories.ruleengine.MailCategoryRule;
import com.openexchange.mail.categories.ruleengine.RuleType;
import com.openexchange.mailfilter.Credentials;
import com.openexchange.mailfilter.MailFilterService;
import com.openexchange.mailfilter.properties.CredentialSource;
import com.openexchange.mailfilter.properties.MailFilterProperty;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.Session;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * {@link SieveMailCategoriesRuleEngine}
 *
 * @author <a href="mailto:kevin.ruthmann@open-xchange.com">Kevin Ruthmann</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.8.2
 */
public class SieveMailCategoriesRuleEngine implements MailCategoriesRuleEngine {

    private final ServiceLookup services;

    /**
     * Initializes a new {@link SieveMailCategoriesRuleEngine}.
     */
    public SieveMailCategoriesRuleEngine(ServiceLookup services) {
        super();
        this.services = services;
    }

    @Override
    public boolean isApplicable(Session session) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        Set<String> capabilities = mailFilterService.getStaticCapabilities(creds);
        boolean applicable = capabilities.contains("imap4flags");
        if (!applicable) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SieveMailCategoriesRuleEngine.class);
            logger.warn("SIEVE server is not suitable for mail categories as it misses required \"imap4flags\" capability");
        }
        return applicable;
    }

    @Override
    public void setRule(Session session, MailCategoryRule rule, RuleType type) throws OXException {
        setRule(session, rule, type, true);
    }

    public void setRule(Session session, MailCategoryRule rule, RuleType type, boolean reorder) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        Rule oldRule = null;
        String rulename = rule.getFlag() == null ? MailCategoriesConstants.GENERAL_CATEGORY_ID : rule.getFlag();
        try {

            List<Rule> rules = mailFilterService.listRules(creds, type.getName());
            for (Rule tmpRule : rules) {
                if (tmpRule.getRuleComment().getRulename().equals(rulename)) {
                    oldRule = tmpRule;
                    break;
                }
            }

            Rule newRule = mailCategoryRule2SieveRule(rule, type);

            if (oldRule != null) {
                newRule.setPosition(oldRule.getPosition());
                newRule.getRuleComment().setUniqueid(oldRule.getUniqueId());
                mailFilterService.updateFilterRule(creds, newRule, oldRule.getUniqueId());
            } else {
                mailFilterService.createFilterRule(creds, newRule);
                if (reorder) {
                    mailFilterService.reorderRules(creds, new int[0]);
                }
            }
        } catch (SieveException e) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_SET_RULE.create(e.getMessage());
        }
    }

    @Override
    public void removeRule(Session session, String flag) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        List<Rule> rules = mailFilterService.listRules(creds, "category");
        for (Rule rule : rules) {
            if (rule.getRuleComment().getRulename().equals(flag)) {
                mailFilterService.deleteFilterRule(creds, rule.getUniqueId());
                break;
            }
        }

    }

    private Credentials getCredentials(Session session) {
        String loginName;
        {
            LeanConfigurationService mailFilterConfig = services.getService(LeanConfigurationService.class);
            String credsrc = mailFilterConfig.getProperty(session.getUserId(), session.getContextId(), MailFilterProperty.credentialSource);
            loginName = CredentialSource.SESSION_FULL_LOGIN.name().equals(credsrc) ? session.getLogin() : session.getLoginName();
        }
        Subject subject = (Subject) session.getParameter("kerberosSubject");
        String oauthToken = (String) session.getParameter(Session.PARAM_OAUTH_ACCESS_TOKEN);
        return new Credentials(loginName, session.getPassword(), session.getUserId(), session.getContextId(), null, subject, oauthToken);
    }

    private Rule mailCategoryRule2SieveRule(MailCategoryRule rule, RuleType type) throws SieveException {
        List<ActionCommand> actionCommands = new ArrayList<>(4);
        if (rule.getFlag() != null) {
            ArrayList<Object> argList = new ArrayList<>(1);
            argList.add(Collections.singletonList(rule.getFlag()));
            ActionCommand addFlagAction = new ActionCommand(ActionCommand.Commands.ADDFLAG, argList);
            actionCommands.add(addFlagAction);
        }

        String[] flagsToRemove = rule.getFlagsToRemove();
        if (flagsToRemove != null && flagsToRemove.length > 0) {
            ArrayList<Object> removeFlagList = new ArrayList<>(flagsToRemove.length);
            for (int i = 0; i < flagsToRemove.length; i++) {
                String flagToRemove = flagsToRemove[i];
                if (Strings.isNotEmpty(flagToRemove)) {
                    removeFlagList.add(flagToRemove);
                }
            }
            if (false == removeFlagList.isEmpty()) {
                ArrayList<Object> removeFlagArgList = new ArrayList<>();
                removeFlagArgList.add(removeFlagList);
                actionCommands.add(0, new ActionCommand(ActionCommand.Commands.REMOVEFLAG, removeFlagArgList));
            }
        }

        IfCommand ifCommand = new IfCommand(getCommand(rule), actionCommands);
        ArrayList<Command> commands = new ArrayList<Command>(Collections.singleton(ifCommand));
        int linenumber = 0;
        boolean commented = false;
        String ruleName = rule.getFlag() == null ? MailCategoriesConstants.GENERAL_CATEGORY_ID : rule.getFlag();
        RuleComment comment = new RuleComment(ruleName);
        comment.setFlags(Collections.singletonList(type.getName()));
        Rule result = new Rule(comment, commands, linenumber, commented);
        return result;
    }

    private TestCommand getCommand(MailCategoryRule rule) throws SieveException {
        if (!rule.hasSubRules()) {

            List<TestCommand> commands = new ArrayList<>(2);

            if (rule.getFlag() == null) {
                commands.add(new TestCommand(Commands.TRUE, Collections.emptyList(), new ArrayList<TestCommand>(0)));
            } else {
                List<Object> flagArgList = new ArrayList<Object>(4);
                flagArgList.add(createTagArgument("is"));
                flagArgList.add(Collections.singletonList(rule.getFlag()));
                commands.add(new TestCommand(Commands.NOT, new ArrayList<>(), Collections.singletonList(new TestCommand(Commands.HASFLAG, flagArgList, new ArrayList<TestCommand>()))));
            }
            List<Object> argList = new ArrayList<Object>(4);
            argList.add(createTagArgument("contains"));
            argList.add(rule.getHeaders());
            argList.add(rule.getValues());
            commands.add(new TestCommand(Commands.HEADER, argList, new ArrayList<TestCommand>()));
            return new TestCommand(Commands.ALLOF, new ArrayList<>(), commands);
        }

        ArrayList<TestCommand> subCommands = new ArrayList<>(rule.getSubRules().size());
        for (MailCategoryRule subTest : rule.getSubRules()) {
            subCommands.add(getCommand(subTest));
        }
        return rule.isAND() ? new TestCommand(Commands.ALLOF, new ArrayList<>(), subCommands) : new TestCommand(Commands.ANYOF, new ArrayList<>(), subCommands);
    }

    /**
     * Creates a {@link TagArgument} from the specified string value
     *
     * @param value The value of the {@link TagArgument}
     * @return the {@link TagArgument}
     */
    private static TagArgument createTagArgument(String value) {
        Token token = new Token();
        token.image = ":" + value;
        return new TagArgument(token);
    }

    @Override
    public MailCategoryRule getRule(Session session, String flag) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        List<Rule> rules = mailFilterService.listRules(creds, "category");
        String name = flag;
        if (flag == null) {
            name = MailCategoriesConstants.GENERAL_CATEGORY_ID;
        }
        for (Rule tmpRule : rules) {
            if (tmpRule.getRuleComment().getRulename().equals(name)) {
                return parseRootRule(tmpRule.getTestCommand(), flag);
            }
        }
        return null;
    }

    private MailCategoryRule parseRootRule(TestCommand command, String flag) throws OXException {
        if (command == null) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        //  assume command contains an ALLOF testcommand with a not hasflag test and another test
        if (command.getTestCommands() == null || command.getTestCommands().isEmpty()) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        List<TestCommand> twoCommands = command.getTestCommands();
        if (twoCommands.size() != 2) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        // assume command 0 is the not hasflag command
        TestCommand realTest = twoCommands.get(1);
        return parseRule(realTest, flag);
    }

    @SuppressWarnings("unchecked")
    private MailCategoryRule parseRule(TestCommand command, String flag) throws OXException {
        if (command == null) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        if (command.getTestCommands() != null && !command.getTestCommands().isEmpty()) {
            boolean isAND = false;
            // Any or All test
            if (command.getCommand().equals(Commands.ALLOF)) {
                isAND = true;
            }
            MailCategoryRule result = new MailCategoryRule(flag, isAND);
            for (TestCommand com : command.getTestCommands()) {
                result.addSubRule(parseRule(com, flag));
            }
            return result;
        }

        // header command
        if (!command.getCommand().equals(Commands.HEADER)) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        List<Object> argList = command.getArguments();
        if (argList == null || argList.isEmpty() || argList.size() != 3) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_RETRIEVE_RULE.create();
        }

        List<String> headers = (List<String>) argList.get(1);
        List<String> values = (List<String>) argList.get(2);
        return new MailCategoryRule(headers, values, flag);

    }

    @Override
    public void removeValueFromHeader(Session session, String value, String header) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        List<Rule> rules = mailFilterService.listRules(creds, "category");
        List<Rule> rules2update = new ArrayList<>();
        for (Rule rule : rules) {

            TestCommand test = rule.getTestCommand();
            Map<TestCommand, List<TestCommand>> toDeleteMap = new HashMap<>();
            boolean removed = false;
            if (test != null) {
                removed = removeValueFromHeader(null, test, value, header, toDeleteMap, rule.getIfCommand());
            }
            if (removed) {
                rules2update.add(rule);
            }
            for (Map.Entry<TestCommand, List<TestCommand>> entry : toDeleteMap.entrySet()) {
                TestCommand parent = entry.getKey();
                for (TestCommand deleteEntry : entry.getValue()) {
                    parent.removeTestCommand(deleteEntry);
                }
            }
        }

        for (Rule rule : rules2update) {
            TestCommand testCom = rule.getTestCommand();
            if (testCom != null) {
                if (testCom.getCommand() == TestCommand.Commands.ANYOF || testCom.getCommand() == TestCommand.Commands.ALLOF) {
                    if (testCom.getTestCommands().isEmpty()) {
                        mailFilterService.deleteFilterRule(creds, rule.getUniqueId());
                    } else {
                        // test whether it contains only the hasflag check
                        if (testCom.getTestCommands().size() == 1 && testCom.getTestCommands().get(0).getCommand().equals(Commands.NOT)) {
                            TestCommand com = testCom.getTestCommands().get(0);
                            if (com.getTestCommands().size() == 1 && com.getTestCommands().get(0).getCommand().equals(Commands.HASFLAG)) {
                                mailFilterService.deleteFilterRule(creds, rule.getUniqueId());
                                continue;
                            }
                        }
                        // test whether it is a empty rule of the 'general' category
                        if (testCom.getTestCommands().size() == 1 && testCom.getTestCommands().get(0).getCommand().equals(Commands.TRUE)) {
                            mailFilterService.deleteFilterRule(creds, rule.getUniqueId());
                            continue;
                        }
                    }
                }
            } else {
                mailFilterService.deleteFilterRule(creds, rule.getUniqueId());
                continue;
            }
            mailFilterService.updateFilterRule(creds, rule, rule.getUniqueId());
        }

    }

    @SuppressWarnings("unchecked")
    private boolean removeValueFromHeader(TestCommand parent, TestCommand child, String value, String header, Map<TestCommand, List<TestCommand>> deleteMap, IfCommand root) {
        boolean result = false;
        List<TestCommand> commands = child.getTestCommands();
        if (commands != null && commands.isEmpty() == false) {

            for (TestCommand subchild : commands) {
                boolean tmpResult = removeValueFromHeader(child, subchild, value, header, deleteMap, root);
                if (tmpResult) {
                    result = true;
                }
            }

        } else {
            List<Object> args = child.getArguments();
            if (!args.isEmpty()) {
                List<String> headers = (List<String>) args.get(1);
                if (headers.contains(header)) {
                    List<String> values = (List<String>) args.get(2);
                    while (values.contains(value)) {
                        boolean tmpResult = values.remove(value);
                        if (tmpResult == true) {
                            result = true;
                        }
                    }
                    if (values.isEmpty()) {
                        if (parent != null) {
                            List<TestCommand> deleteEntries = deleteMap.get(parent);
                            if (deleteEntries == null) {
                                deleteEntries = new ArrayList<>();
                                deleteMap.put(parent, deleteEntries);
                            }
                            deleteEntries.add(child);
                        } else {
                            root.setTestcommand(null);
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void initRuleEngineForUser(final Session session, final List<MailCategoryRule> rules) throws OXException {
        final MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        // Get old rules
        Credentials creds = getCredentials(session);
        List<Rule> oldRules = mailFilterService.listRules(creds, RuleType.SYSTEM_CATEGORY.getName());
        final int[] uids = new int[oldRules.size()];
        int x = 0;
        for (Rule rule : oldRules) {
            uids[x++] = rule.getUniqueId();
        }

        // Run task
        if (!rules.isEmpty()) {
            // Remove possible old rules
            if (uids.length > 0) {
                mailFilterService.deleteFilterRules(creds, uids);
            }
            // Create new rules
            for (MailCategoryRule rule : rules) {
                setRule(session, rule, RuleType.SYSTEM_CATEGORY, false);
            }
            mailFilterService.reorderRules(creds, new int[] {});
        }
    }

    @Override
    public void cleanUp(List<String> flags, Session session) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }

        Credentials creds = getCredentials(session);
        List<Rule> rules = mailFilterService.listRules(creds, RuleType.CATEGORY.getName());
        TIntList uidList = new TIntArrayList(rules.size());
        for (Rule rule : rules) {
            String name = rule.getRuleComment().getRulename();
            if (!flags.contains(name) && !name.equals(MailCategoriesConstants.GENERAL_CATEGORY_ID)) {
                uidList.add(rule.getRuleComment().getUniqueid());
            }
        }
        if (uidList.isEmpty()) {
            return;
        }

        mailFilterService.deleteFilterRules(creds, uidList.toArray());
    }

    @Override
    public boolean applyRule(Session session, MailCategoryRule rule) throws OXException {
        MailFilterService mailFilterService = services.getService(MailFilterService.class);
        if (mailFilterService == null) {
            throw MailCategoriesExceptionCodes.SERVICE_UNAVAILABLE.create(MailFilterService.class);
        }
        try {
            MailAccess<? extends IMailFolderStorage, ? extends IMailMessageStorage> mailAccess = null;
            try {
                FullnameArgument fa = new FullnameArgument("INBOX");
                mailAccess = MailAccess.getInstance(session, fa.getAccountId());
                mailAccess.connect();
                IMailMessageStorage messageStorage = mailAccess.getMessageStorage();

                IMailMessageStorageMailFilterApplication mailFilterMessageStorage = messageStorage.supports(IMailMessageStorageMailFilterApplication.class);
                if (null == mailFilterMessageStorage) {
                    return false;
                }

                if (!mailFilterMessageStorage.isMailFilterApplicationSupported()) {
                    return false;
                }
                Rule sieveRule = mailCategoryRule2SieveRule(rule, RuleType.CATEGORY);
                String filter = mailFilterService.convertToString(new Credentials(session), sieveRule);
                mailFilterMessageStorage.applyMailFilterScript(fa.getFullName(), filter, null, false); // As filter results are of no interest, discard OK results
            } finally {
                if (mailAccess != null) {
                    mailAccess.close();
                }
            }
        } catch (SieveException e) {
            throw MailCategoriesRuleEngineExceptionCodes.UNABLE_TO_SET_RULE.create(e.getMessage());
        }
        return true;
    }

}
