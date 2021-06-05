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

package com.openexchange.mailfilter.internal;

import static com.openexchange.java.Autoboxing.I;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.Subject;
import org.apache.jsieve.SieveException;
import org.apache.jsieve.TagArgument;
import org.apache.jsieve.parser.generated.Node;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.DefaultInterests;
import com.openexchange.config.Interests;
import com.openexchange.config.Reloadable;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import com.openexchange.jsieve.commands.ActionCommand;
import com.openexchange.jsieve.commands.IfCommand;
import com.openexchange.jsieve.commands.Rule;
import com.openexchange.jsieve.commands.RuleComment;
import com.openexchange.jsieve.commands.TestCommand;
import com.openexchange.jsieve.commands.TestCommand.Commands;
import com.openexchange.jsieve.commands.test.ITestCommand;
import com.openexchange.jsieve.export.Capabilities;
import com.openexchange.jsieve.export.RuleConverter;
import com.openexchange.jsieve.export.SieveHandler;
import com.openexchange.jsieve.export.SieveHandlerFactory;
import com.openexchange.jsieve.export.SieveTextFilter;
import com.openexchange.jsieve.export.SieveTextFilter.ClientRulesAndRequire;
import com.openexchange.jsieve.export.SieveTextFilter.RuleListAndNextUid;
import com.openexchange.jsieve.export.exceptions.OXSieveHandlerException;
import com.openexchange.jsieve.export.exceptions.OXSieveHandlerInvalidCredentialsException;
import com.openexchange.jsieve.visitors.Visitor;
import com.openexchange.jsieve.visitors.Visitor.OwnType;
import com.openexchange.mailfilter.Credentials;
import com.openexchange.mailfilter.MailFilterCommand;
import com.openexchange.mailfilter.MailFilterService;
import com.openexchange.mailfilter.exceptions.MailFilterExceptionCode;
import com.openexchange.mailfilter.properties.MailFilterProperty;
import com.openexchange.mailfilter.properties.PasswordSource;
import com.openexchange.metrics.micrometer.binders.CircuitBreakerMetrics;
import com.openexchange.server.ServiceExceptionCode;
import com.openexchange.server.ServiceLookup;
import com.openexchange.session.UserAndContext;
import io.micrometer.core.instrument.Metrics;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.function.CheckedRunnable;

/**
 * {@link MailFilterServiceImpl}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
public final class MailFilterServiceImpl implements MailFilterService, Reloadable {

    static final Logger LOGGER = LoggerFactory.getLogger(MailFilterServiceImpl.class);

    private static final String CATEGORY_FLAG = "category";
    private static final String SYSTEM_CATEGORY_FLAG = "syscategory";

    private static final class RuleAndPosition {

        final int position;
        final Rule rule;

        RuleAndPosition(Rule rule, int position) {
            super();
            this.rule = rule;
            this.position = position;
        }

    }

    private static final ConcurrentMap<UserAndContext, Object> LOCKS = new ConcurrentHashMap<UserAndContext, Object>(256, 0.9f, 1);

    /**
     * Gets the lock instance for specified session.
     *
     * @param creds The credentials
     * @return The lock instance
     */
    private static Object lockFor(Credentials creds) {
        if (null == creds) {
            // Any...
            return new Object();
        }
        UserAndContext key = UserAndContext.newInstance(creds.getUserid(), creds.getContextid());
        Object lock = LOCKS.get(key);
        if (null == lock) {
            Object newLock = new Object();
            lock = LOCKS.putIfAbsent(key, newLock);
            if (null == lock) {
                lock = newLock;
            }
        }
        return lock;
    }

    /**
     * Removes the lock instance associated with specified session.
     *
     * @param userId The user identifier
     * @param contextId The context identifier
     */
    public static void removeFor(int userId, int contextId) {
        LOCKS.remove(UserAndContext.newInstance(userId, contextId));
    }

    // ---------------------------------------------------------------------------------------------------------------- //

    private final Cache<HostAndPort, Capabilities> staticCapabilities;
    private final ServiceLookup services;
    private final AtomicReference<CircuitBreakerInfo> optionalCircuitBreaker;

    /**
     * Initializes a new {@link MailFilterServiceImpl}.
     *
     * @param services The {@link ServiceLookup} instance
     * @throws OXException
     */
    public MailFilterServiceImpl(ServiceLookup services) throws OXException {
        super();
        this.services = services;
        staticCapabilities = CacheBuilder.newBuilder().maximumSize(10).expireAfterWrite(30, TimeUnit.MINUTES).build();
        optionalCircuitBreaker = new AtomicReference<>(null);

        ConfigurationService config = getService(ConfigurationService.class);
        checkConfigfile(config);
        reinitBreaker(config);
    }

    /**
     * (Re-)Initializes the circuit breaker for the mail filter end-point.
     *
     * @param config The configuration service to use
     */
    public void reinitBreaker(ConfigurationService config) {
        boolean breakerEnabled = config.getBoolProperty(MailFilterProperty.enabled.getFQPropertyName(), ((Boolean) MailFilterProperty.enabled.getDefaultValue()).booleanValue());
        if (breakerEnabled) {
            CircuitBreaker circuitBreaker = null;
            try {
                int failureThreshold = Integer.parseInt(config.getProperty(MailFilterProperty.failureThreshold.getFQPropertyName(), MailFilterProperty.failureThreshold.getDefaultValue().toString()).trim());
                if (failureThreshold <= 0) {
                    throw new IllegalArgumentException("failureThreshold must be greater than 0 (zero).");
                }
                int failureExecutions = failureThreshold;
                {
                    String tmp = config.getProperty(MailFilterProperty.failureExecutions.getFQPropertyName(), MailFilterProperty.failureExecutions.getDefaultValue().toString()).trim();
                    if (Strings.isNotEmpty(tmp)) {
                        failureExecutions = Integer.parseInt(tmp);
                        if (failureExecutions < failureThreshold) {
                            failureExecutions = failureThreshold;
                        }
                    }
                }
                int successThreshold = Integer.parseInt(config.getProperty(MailFilterProperty.successThreshold.getFQPropertyName(), MailFilterProperty.successThreshold.getDefaultValue().toString()).trim());
                if (successThreshold <= 0) {
                    throw new IllegalArgumentException("successThreshold must be greater than 0 (zero).");
                }
                int successExecutions = successThreshold;
                {
                    String tmp = config.getProperty(MailFilterProperty.successExecutions.getFQPropertyName(), MailFilterProperty.successExecutions.getDefaultValue().toString()).trim();
                    if (Strings.isNotEmpty(tmp)) {
                        successExecutions = Integer.parseInt(tmp);
                        if (successExecutions < successThreshold) {
                            successExecutions = successThreshold;
                        }
                    }
                }
                int delayMillis = Integer.parseInt(config.getProperty(MailFilterProperty.delayMillis.getFQPropertyName(), MailFilterProperty.delayMillis.getDefaultValue().toString()).trim());
                if (delayMillis <= 0) {
                    throw new IllegalArgumentException("windowMillis must be greater than 0 (zero).");
                }
                circuitBreaker = new CircuitBreaker()
                    .withFailureThreshold(failureThreshold, failureExecutions)
                    .withSuccessThreshold(successThreshold, successExecutions)
                    .withDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .onOpen(() -> {
                        CircuitBreakerInfo circuitBreakerInfo = optionalCircuitBreaker.get();
                        if (circuitBreakerInfo != null) {
                            circuitBreakerInfo.incrementOpens();
                        }
                    })
                    .onHalfOpen(new CheckedRunnable() {

                        @Override
                        public void run() throws Exception {
                            LOGGER.info("Mail filter circuit breaker half-opened");
                        }
                    })
                    .onClose(new CheckedRunnable() {

                        @Override
                        public void run() throws Exception {
                            LOGGER.info("Mail filter circuit breaker closed");
                        }
                    });


            } catch (RuntimeException e) {
                LOGGER.warn("Invalid configuration for mail filter circuit breaker", e);
                circuitBreaker = null;
            }

            optionalCircuitBreaker.set(initCircuitBreakerInfo(circuitBreaker));
        } else {
            optionalCircuitBreaker.set(null);
        }

    }


    /**
     * Creates a new {@link CircuitBreakerInfo} instance for given {@link CircuitBreaker} and initializes
     * its monitoring metrics.
     *
     * @param circuitBreaker The breaker or <code>null</code>
     * @return The info or <code>null</code>
     */
    private CircuitBreakerInfo initCircuitBreakerInfo(CircuitBreaker circuitBreaker) {
        if (circuitBreaker == null) {
            return null;
        }

        CircuitBreakerMetrics metrics = new CircuitBreakerMetrics(circuitBreaker, "mailfilter", Optional.empty());
        metrics.bindTo(Metrics.globalRegistry);
        return new CircuitBreakerInfo(circuitBreaker, metrics);
    }

    /**
     * This method checks for a valid properties' file and throws an exception if none is there or one of the properties is missing
     *
     * @param config The configuration service
     * @throws OXException If the properties' file is invalid
     */
    private void checkConfigfile(ConfigurationService config) throws OXException {
        try {
            Properties file = config.getFile("mailfilter.properties");
            if (file.isEmpty()) {
                throw MailFilterExceptionCode.NO_PROPERTIES_FILE_FOUND.create();
            }
            for (final MailFilterProperty property : MailFilterProperty.values()) {
                if (!property.isOptional() && null == file.getProperty(property.getFQPropertyName())) {
                    throw MailFilterExceptionCode.PROPERTY_NOT_FOUND.create(property.getFQPropertyName());
                }
            }
            try {
                Integer.parseInt(file.getProperty(MailFilterProperty.connectionTimeout.getFQPropertyName()));
            } catch (NumberFormatException e) {
                throw MailFilterExceptionCode.PROPERTY_ERROR.create("Property " + MailFilterProperty.connectionTimeout.getFQPropertyName() + " is not an integer value", e);
            }
        } catch (OXException e) {
            throw e;
        } catch (Exception e) {
            throw MailFilterExceptionCode.PROBLEM.create(e.getMessage(), e);
        }

        // Check password source
        final String passwordSrc = config.getProperty(MailFilterProperty.passwordSource.getFQPropertyName());
        if (passwordSrc == null) {
            throw MailFilterExceptionCode.NO_VALID_PASSWORDSOURCE.create();
        }
        PasswordSource passwordSource = PasswordSource.passwordSourceFor(passwordSrc);
        switch (passwordSource) {
            case GLOBAL:
                final String masterpassword = config.getProperty(MailFilterProperty.masterPassword.getFQPropertyName());
                if (masterpassword.length() == 0) {
                    throw MailFilterExceptionCode.NO_MASTERPASSWORD_SET.create();
                }
                break;
            case SESSION:
                break;
            default:
                throw MailFilterExceptionCode.NO_VALID_PASSWORDSOURCE.create();
        }
    }

    /**
     * Closes given <tt>SieveHandler</tt> instance
     *
     * @param sieveHandler The <tt>SieveHandler</tt> instance to close
     * @throws OXException If closing fails
     */
    protected void closeSieveHandler(SieveHandler sieveHandler) throws OXException {
        if (null != sieveHandler) {
            try {
                sieveHandler.close();
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), I(sieveHandler.getSievePort()));
            }
        }
    }

    /**
     * Gets the active Sieve script or an empty string if no active script is available
     * <p>
     * <b>Must only be called when holding lock!</b>
     *
     * @param sieveHandler The Sieve handler to use
     * @param expectedScriptName The expected name for the active script or <code>null</code>
     * @return The action Siege script or an empty string
     */
    private String getScript(SieveHandler sieveHandler, String expectedScriptName) throws OXSieveHandlerException, UnsupportedEncodingException, IOException {
        String activeScript = sieveHandler.getActiveScript(expectedScriptName);
        return null != activeScript ? sieveHandler.getScript(activeScript) : "";
    }

    /**
     * Gets the optional circuit breaker for the mail filter end-point.
     *
     * @return The optional circuit breaker
     */
    private Optional<CircuitBreakerInfo> getOptionalCircuitBreaker() {
        return Optional.ofNullable(optionalCircuitBreaker.get());
    }

    @Override
    public final int createFilterRule(Credentials credentials, Rule rule) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());

                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                String script = (activeScript != null) ? sieveHandler.getScript(activeScript) : "";

                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);

                ClientRulesAndRequire clientrulesandrequire = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), null, rules.isError());

                if (isVacationRule(rule)) {
                    // A vacation rule...
                    List<Rule> clientrules = clientrulesandrequire.getRules();
                    for (Rule r : clientrules) {
                        if (isVacationRule(r)) {
                            throw MailFilterExceptionCode.DUPLICATE_VACATION_RULE.create();
                        }
                    }
                } else {
                    // Check for redirect rule
                    checkRedirects(rule, credentials);
                }

                changeIncomingVacationRule(credentials.getUserid(), credentials.getContextid(), rule);

                int nextuid = insertIntoPosition(rule, rules, clientrulesandrequire);
                String writeback = sieveTextFilter.writeback(clientrulesandrequire, new HashSet<String>(sieveHandler.getCapabilities().getSieve()));
                writeback = sieveTextFilter.rewriteRequire(writeback, script);
                LOGGER.debug("The following sieve script will be written:\n{}", writeback);
                writeScript(sieveHandler, activeScript, writeback, expectedScriptName);

                return nextuid;
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (SieveException e) {
                throw MailFilterExceptionCode.handleSieveException(e);
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public final void updateFilterRule(Credentials credentials, Rule rule, int uid) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());

                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                if (null == activeScript) {
                    throw MailFilterExceptionCode.NO_ACTIVE_SCRIPT.create();
                }

                String script = fixParsingError(sieveHandler.getScript(activeScript));

                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);

                ClientRulesAndRequire clientRulesAndReq = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), null, rules.isError());
                RuleAndPosition rightRule = getRightRuleForUniqueId(clientRulesAndReq.getRules(), uid);
                if (rightRule == null) {
                    throw MailFilterExceptionCode.NO_SUCH_ID.create(I(uid), I(credentials.getUserid()), I(credentials.getContextid()));
                }

                // Check redirect limit
                checkRedirects(rule, credentials);

                changeIncomingVacationRule(credentials.getUserid(), credentials.getContextid(), rightRule.rule);
                if (rightRule.position == rule.getPosition()) {
                    clientRulesAndReq.getRules().set(rightRule.position, rule);
                } else {
                    clientRulesAndReq.getRules().remove(rightRule.position);
                    clientRulesAndReq.getRules().add(rule.getPosition(), rule);
                }
                String writeback = sieveTextFilter.writeback(clientRulesAndReq, new HashSet<String>(sieveHandler.getCapabilities().getSieve()));
                writeback = sieveTextFilter.rewriteRequire(writeback, script);
                LOGGER.debug("The following sieve script will be written:\n{}", writeback);

                writeScript(sieveHandler, activeScript, writeback, expectedScriptName);
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (SieveException e) {
                throw MailFilterExceptionCode.handleSieveException(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public void deleteFilterRule(Credentials credentials, int uid) throws OXException {
        deleteFilterRules(credentials, uid);
    }

    @Override
    public void deleteFilterRules(Credentials credentials, int... uids) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());

                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                if (null == activeScript) {
                    throw MailFilterExceptionCode.NO_ACTIVE_SCRIPT.create();
                }

                String script = sieveHandler.getScript(activeScript);
                RuleListAndNextUid rulesandid = sieveTextFilter.readScriptFromString(script);
                ClientRulesAndRequire clientrulesandrequire = sieveTextFilter.splitClientRulesAndRequire(rulesandid.getRulelist(), null, rulesandid.isError());

                List<Rule> rules = clientrulesandrequire.getRules();
                for (int uid : uids) {
                    RuleAndPosition deletedrule = getRightRuleForUniqueId(rules, uid);
                    if (deletedrule == null) {
                        throw MailFilterExceptionCode.NO_SUCH_ID.create(I(uid), I(credentials.getUserid()), I(credentials.getContextid()));
                    }
                    rules.remove(deletedrule.rule);
                }
                String writeback = sieveTextFilter.writeback(clientrulesandrequire, new HashSet<String>(sieveHandler.getCapabilities().getSieve()));
                writeback = sieveTextFilter.rewriteRequire(writeback, script);
                writeScript(sieveHandler, activeScript, writeback, expectedScriptName);
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (SieveException e) {
                throw MailFilterExceptionCode.handleSieveException(e);
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public final void purgeFilters(Credentials credentials) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());

                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
                String writeback = sieveTextFilter.writeEmptyScript();
                writeScript(sieveHandler, activeScript, writeback, expectedScriptName);
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public final String getActiveScript(Credentials credentials) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                return getScript(sieveHandler, expectedScriptName);
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (NumberFormatException nfe) {
                throw MailFilterExceptionCode.NAN.create(nfe, MailFilterExceptionCode.getNANString(nfe));
            } catch (RuntimeException re) {
                throw MailFilterExceptionCode.PROBLEM.create(re, re.getMessage());
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public List<Rule> listRules(Credentials credentials, FilterType flag) throws OXException {
        return listRules(credentials, flag.getFlag());
    }

    @Override
    public List<Rule> listRules(Credentials credentials, String flag) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String script = getScript(sieveHandler, expectedScriptName);
                LOGGER.debug("The following sieve script will be parsed:\n{}", script);
                SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);
                removeErroneusRules(rules);

                ClientRulesAndRequire clientrulesandrequire = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), flag, rules.isError());
                List<Rule> clientRules = clientrulesandrequire.getRules();
                changeOutgoingVacationRule(credentials.getUserid(), credentials.getContextid(), clientRules);
                if (!flag.equals(CATEGORY_FLAG)) {
                    removeRules(clientRules, CATEGORY_FLAG);
                }
                if (!flag.equals(SYSTEM_CATEGORY_FLAG)) {
                    removeRules(clientRules, SYSTEM_CATEGORY_FLAG);
                }

                removeNestedRules(clientRules);

                return clientRules;
            } catch (SieveException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    private void removeRules(List<Rule> rules, String... flagsToRemove) {
        Iterator<Rule> iterator = rules.iterator();
        while (iterator.hasNext()) {
            Rule r = iterator.next();
            List<String> flags = r.getRuleComment().getFlags();
            for (String flagToRemove : flagsToRemove) {
                if (flags != null && flags.contains(flagToRemove)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    @Override
    public List<Rule> listRules(Credentials credentials, List<FilterType> exclusionFlags) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String script = getScript(sieveHandler, expectedScriptName);
                LOGGER.debug("The following sieve script will be parsed:\n{}", script);
                SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);
                removeErroneusRules(rules);

                ClientRulesAndRequire splittedRules = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), null, rules.isError());

                if (splittedRules.getFlaggedRules() != null) {
                    return exclude(splittedRules.getFlaggedRules(), exclusionFlags);
                }
                List<Rule> splitRules = splittedRules.getRules();
                removeRules(splitRules, CATEGORY_FLAG, SYSTEM_CATEGORY_FLAG);
                removeNestedRules(splitRules);
                return splitRules;
            } catch (SieveException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public void reorderRules(Credentials credentials, int[] uids) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                if (null == activeScript) {
                    throw MailFilterExceptionCode.NO_ACTIVE_SCRIPT.create();
                }

                String script = sieveHandler.getScript(activeScript);
                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);

                ClientRulesAndRequire clientrulesandrequire = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), null, rules.isError());
                List<Rule> clientrules = clientrulesandrequire.getRules();
                if (uids.length > clientrules.size()) {
                    LOGGER.debug("The contents of the reorder array are: {}", uids);
                    throw MailFilterExceptionCode.INVALID_REORDER_ARRAY.create(I(uids.length), I(clientrules.size()), I(credentials.getUserid()), I(credentials.getContextid()));
                }

                // Identify rule groups
                List<MailFilterGroup> groups = getMailFilterGroups(uids);
                List<Rule> tmpList = new ArrayList<>(clientrules);
                clientrules.clear();
                for (MailFilterGroup group : groups) {
                    clientrules.addAll(group.getOrderedRules(tmpList));
                }

                String writeback = sieveTextFilter.writeback(clientrulesandrequire, new HashSet<String>(sieveHandler.getCapabilities().getSieve()));
                writeback = sieveTextFilter.rewriteRequire(writeback, script);
                writeScript(sieveHandler, activeScript, writeback, expectedScriptName);
            } catch (SieveException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    private List<MailFilterGroup> getMailFilterGroups(int[] ids) {
        ArrayList<MailFilterGroup> result = new ArrayList<>(2);
        result.add(new PredefinedSystemCategoriesMailFilterGroup());
        result.add(new CategoriesMailFilterGroup());
        result.add(new GeneralMailFilterGroup(ids));
        return result;
    }

    @Override
    public final Rule getFilterRule(Credentials credentials, int uid) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveTextFilter sieveTextFilter = new SieveTextFilter(credentials);
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());

            try {
                handlerConnect(sieveHandler, credentials.getSubject());

                String expectedScriptName = getScriptName(credentials.getUserid(), credentials.getContextid());
                String activeScript = sieveHandler.getActiveScript(expectedScriptName);
                if (activeScript == null) {
                    throw MailFilterExceptionCode.NO_ACTIVE_SCRIPT.create();
                }

                String script = fixParsingError(sieveHandler.getScript(activeScript));
                RuleListAndNextUid rules = sieveTextFilter.readScriptFromString(script);

                ClientRulesAndRequire clientrulesandrequire = sieveTextFilter.splitClientRulesAndRequire(rules.getRulelist(), null, rules.isError());
                List<Rule> clientrules = clientrulesandrequire.getRules();
                RuleAndPosition rightRule = getRightRuleForUniqueId(clientrules, uid);

                // no rule found
                if (rightRule == null) {
                    return null;
                }

                return rightRule.rule;
            } catch (UnsupportedEncodingException e) {
                throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } catch (IOException e) {
                throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
            } catch (ParseException e) {
                throw MailFilterExceptionCode.SIEVE_ERROR.create(e, e.getMessage());
            } catch (SieveException e) {
                throw MailFilterExceptionCode.handleSieveException(e);
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public Set<String> getCapabilities(Credentials credentials) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                Capabilities capabilities = sieveHandler.getCapabilities();
                return new HashSet<String>(capabilities.getSieve());
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public Set<String> getStaticCapabilities(final Credentials credentials) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            final SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, Optional.empty(), true);
            sieveHandler.setConnectTimeout(1500);
            sieveHandler.setReadTimeout(2000);
            HostAndPort key = new HostAndPort(sieveHandler.getSieveHost(), sieveHandler.getSievePort());
            try {
                Capabilities capabilities = staticCapabilities.get(key, new Callable<Capabilities>() {

                    @Override
                    public Capabilities call() throws Exception {
                        try {
                            handlerConnect(sieveHandler, credentials.getSubject());
                            return sieveHandler.getCapabilities();
                        } catch (OXSieveHandlerException e) {
                            throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
                        } finally {
                            closeSieveHandler(sieveHandler);
                        }
                    }
                });
                return new HashSet<String>(capabilities.getSieve());
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof OXException) {
                    throw (OXException) t;
                }
                throw OXException.general("Failed loading Sieve capabilities", t);
            }
        }
    }

    @Override
    public void executeCommand(Credentials credentials, MailFilterCommand command) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                command.execute(new SieveProtocolImpl(sieveHandler, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid())));
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public void reloadConfiguration(ConfigurationService configService) {
        try {
            checkConfigfile(configService);
        } catch (OXException e) {
            LOGGER.error("Error while reloading 'mailfilter.properties': {}", e.getMessage(), e);
        }
    }

    @Override
    public Interests getInterests() {
        String[] configFileNames = new String[MailFilterProperty.values().length];
        int index = 0;
        for (MailFilterProperty mailFilterProperty : MailFilterProperty.values()) {
            configFileNames[index++] = mailFilterProperty.getFQPropertyName();
        }
        return DefaultInterests.builder().configFileNames("mailfilter.properties").configFileNames(configFileNames).build();
    }

    // ----------------------------------------------------------------------------------------------------------------------- //

    /**
     * Get the script name for the specified user in the specified context
     *
     * @param userId The user identifier
     * @param contextId the context identifier
     * @return The script name
     * @throws OXException
     */
    private String getScriptName(int userId, int contextId) throws OXException {
        LeanConfigurationService config = services.getServiceSafe(LeanConfigurationService.class);
        return config.getProperty(userId, contextId, MailFilterProperty.scriptName);
    }

    /**
     *
     * @param userId
     * @param contextId
     * @return
     * @throws OXException
     */
    boolean useSIEVEResponseCodes(int userId, int contextId) throws OXException {
        LeanConfigurationService config = services.getServiceSafe(LeanConfigurationService.class);
        return config.getBooleanProperty(userId, contextId, MailFilterProperty.useSIEVEResponseCodes);
    }

    private List<Rule> exclude(Map<String, List<Rule>> flagged, List<FilterType> exclusionFlags) {
        List<Rule> ret = new ArrayList<Rule>();
        for (FilterType flag : exclusionFlags) {
            flagged.remove(flag.getFlag());
        }
        for (List<Rule> l : flagged.values()) {
            ret.addAll(l);
        }
        return ret;
    }

    /**
     * Removes the erroneous rules from the list
     *
     * @param rules rule list
     */
    private void removeErroneusRules(RuleListAndNextUid rules) {
        if (rules.isError()) {
            Iterator<Rule> ruleIter = rules.getRulelist().iterator();
            while (ruleIter.hasNext()) {
                Rule rule = ruleIter.next();
                if (Strings.isNotEmpty(rule.getErrormsg())) {
                    ruleIter.remove();
                }
            }
        }
    }

    /**
     * Removes any nested rules from the specified Rule list
     *
     * @param rules The rule list
     */
    private void removeNestedRules(List<Rule> rules) {
        Iterator<Rule> iterator = rules.iterator();
        while (iterator.hasNext()) {
            Rule rule = iterator.next();
            IfCommand ifCommand = rule.getIfCommand();
            List<?> actionCommands = ifCommand.getActionCommands();
            if (containsNestedRule(actionCommands)) {
                iterator.remove();
            }
        }
    }

    /**
     * Checks if the specified list of commands contains a rule
     *
     * @param commands The list of commands
     * @return true if at least one of the commands is a rule; false otherwise
     */
    private boolean containsNestedRule(List<?> commands) {
        for (Object o : commands) {
            if (o instanceof Rule) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks the amount of redirect rules in the specified {@link Rule} and compares
     * those with the 'max_redirect' size (if available) from the Sieve server
     *
     * @param rule The {@link Rule}
     * @param credentials The {@link Credentials}
     * @throws OXException if the redirect commands in the specified {@link Rule} exceed
     *             the maximum allowed that is configured on the Sieve server
     */
    private void checkRedirects(Rule rule, Credentials credentials) throws OXException {
        // Check for redirect rule
        int size = getRedirectRuleSize(rule);
        if (size <= 0) {
            return;
        }
        Object maxRedirectObject = getExtendedProperties(credentials).get("MAXREDIRECTS");
        if (maxRedirectObject == null) {
            return;
        }
        if (false == (maxRedirectObject instanceof Integer)) {
            return;
        }
        Integer maxRedirects = (Integer) maxRedirectObject;
        if (size > maxRedirects.intValue()) {
            throw MailFilterExceptionCode.TOO_MANY_REDIRECT.create();
        }
    }

    /**
     * Change a vacation rule
     *
     * @param rule the rule to be changed
     * @throws SieveException
     */
    private void changeIncomingVacationRule(int userId, int contextId, Rule rule) throws SieveException {
        LeanConfigurationService config = services.getService(LeanConfigurationService.class);
        String vacationdomains = config.getProperty(userId, contextId, MailFilterProperty.vacationDomains);

        if (null != vacationdomains && 0 != vacationdomains.length()) {
            IfCommand ifCommand = rule.getIfCommand();
            if (isVacationRule(rule)) {
                List<Object> argList = new ArrayList<Object>();
                argList.add(createTagArg("is"));
                argList.add(createTagArg("domain"));

                List<String> header = new ArrayList<String>();
                header.add("From");

                String[] split = Strings.splitByComma(vacationdomains);

                argList.add(header);
                argList.add(Arrays.asList(split));
                TestCommand testcommand = ifCommand.getTestcommand();
                ITestCommand command = testcommand.getCommand();
                TestCommand newTestCommand = new TestCommand(Commands.ADDRESS, argList, new ArrayList<TestCommand>());
                if (Commands.TRUE.equals(command)) {
                    // No test until now
                    ifCommand.setTestcommand(newTestCommand);
                } else {
                    // Found other tests
                    List<TestCommand> arrayList = new ArrayList<TestCommand>();
                    arrayList.add(newTestCommand);
                    arrayList.add(testcommand);
                    ifCommand.setTestcommand(new TestCommand(Commands.ALLOF, new ArrayList<Object>(), arrayList));
                }
            }
        }
    }

    /**
     * Change the outgoing vacation rule
     *
     * @param clientrules
     * @throws SieveException
     */
    private void changeOutgoingVacationRule(int userId, int contextId, List<Rule> clientrules) throws SieveException {
        LeanConfigurationService config = services.getService(LeanConfigurationService.class);
        String vacationdomains = config.getProperty(userId, contextId, MailFilterProperty.vacationDomains);

        if (null != vacationdomains && 0 != vacationdomains.length()) {
            for (Rule rule : clientrules) {
                IfCommand ifCommand = rule.getIfCommand();
                if (isVacationRule(rule)) {
                    TestCommand testcommand = ifCommand.getTestcommand();
                    if (Commands.ADDRESS.equals(testcommand.getCommand())) {
                        // Test command found now check if it's the right one...
                        if (checkOwnVacation(testcommand.getArguments())) {
                            ifCommand.setTestcommand(new TestCommand(TestCommand.Commands.TRUE, new ArrayList<Object>(), new ArrayList<TestCommand>()));
                        }
                    } else if (Commands.ALLOF.equals(testcommand.getCommand())) {
                        // In this case we find "our" rule at the first place
                        List<TestCommand> testcommands = testcommand.getTestCommands();
                        if (null != testcommands && testcommands.size() > 1) {
                            TestCommand testCommand2 = testcommands.get(0);
                            if (checkOwnVacation(testCommand2.getArguments())) {
                                // now remove...
                                if (2 == testcommands.size()) {
                                    // If this is one of two convert the rule
                                    ifCommand.setTestcommand(testcommands.get(1));
                                } else if (testcommands.size() > 2) {
                                    // If we have more than one just remove it...
                                    testcommands.remove(0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check own vacation
     *
     * @param arguments
     * @return
     */
    private boolean checkOwnVacation(List<Object> arguments) {
        return null != arguments && null != arguments.get(0) && arguments.get(0) instanceof TagArgument && ":is".equals(((TagArgument) arguments.get(0)).getTag()) && null != arguments.get(1) && arguments.get(1) instanceof TagArgument && ":domain".equals(((TagArgument) arguments.get(1)).getTag()) && null != arguments.get(2) && arguments.get(2) instanceof List<?> && "From".equals(((List<?>) arguments.get(2)).get(0));
    }

    /**
     * Set the specified UID to the specified Rule
     *
     * @param rule the rule
     * @param uid the UID
     */
    private void setUIDInRule(Rule rule, int uid) {
        RuleComment name = rule.getRuleComment();
        if (null != name) {
            name.setUniqueid(uid);
        } else {
            rule.setRuleComments(new RuleComment(uid));
        }
    }

    /**
     * Determine whether the specified rule is a vacation rule
     *
     * @param rule
     * @return true if the specified rule is a vacation rule; false otherwise
     */
    private boolean isVacationRule(Rule rule) {
        RuleComment ruleComment = rule.getRuleComment();
        return (null != ruleComment) && (null != ruleComment.getFlags()) && ruleComment.getFlags().contains("vacation") && rule.getIfCommand() != null && ActionCommand.Commands.VACATION.equals(rule.getIfCommand().getFirstCommand());
    }

    /**
     * Determine the amount of redirect action commands in the given rule
     *
     * @param rule The rule
     * @return the amount of redirect commands
     */
    private int getRedirectRuleSize(Rule rule) {
        int result = 0;
        if (rule.getIfCommand() != null) {
            for (ActionCommand command : rule.getIfCommand().getActionCommands()) {
                if (ActionCommand.Commands.REDIRECT.equals(command.getCommand())) {
                    result++;
                }
            }
        }
        return result;
    }

    /**
     * Used to perform checks to set the right script name when writing
     *
     * @param sieveHandler the sieveHandler to use
     * @param activeScript the activeScript
     * @param writeback the write-back String
     * @param scriptName The configured name of the script
     * @throws OXSieveHandlerException
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    private void writeScript(SieveHandler sieveHandler, String activeScript, String writeback, String scriptName) throws OXSieveHandlerException, IOException, UnsupportedEncodingException {
        StringBuilder commandBuilder = new StringBuilder(64);

        if (null != activeScript && activeScript.equals(scriptName)) {
            sieveHandler.setScript(activeScript, writeback.getBytes(com.openexchange.java.Charsets.UTF_8), commandBuilder);
            sieveHandler.setScriptStatus(activeScript, true, commandBuilder);
        } else {
            sieveHandler.setScript(scriptName, writeback.getBytes(com.openexchange.java.Charsets.UTF_8), commandBuilder);
            sieveHandler.setScriptStatus(scriptName, true, commandBuilder);
        }
    }

    /**
     * Fix parsing
     *
     * @param script
     * @return
     */
    private String fixParsingError(String script) {
        String pattern = ":addresses\\s+:";
        return script.replaceAll(pattern, ":addresses \"\" :");
    }

    /**
     * Search within the given List of Rules for the one matching the specified UID
     */
    private RuleAndPosition getRightRuleForUniqueId(List<Rule> clientrules, int uniqueid) {
        for (int i = 0; i < clientrules.size(); i++) {
            Rule rule = clientrules.get(i);
            if (uniqueid == rule.getUniqueId()) {
                return new RuleAndPosition(rule, i);
            }
        }
        return null;
    }

    private TagArgument createTagArg(String string) {
        Token token = new Token();
        token.image = ":" + string;
        return new TagArgument(token);
    }

    /**
     * Connects the specified SIEVE handler.
     * <p>
     * Furthermore this method contains a thread synchronization if Kerberos is used. If the Kerberos subject is not <code>null</code>, that Kerberos subject object is used to only allow a single IMAP/SIEVE login per Kerberos
     * subject. The service ticket for the IMAP/SIEVE server is stored in the Kerberos subject once the IMAP/SIEVE login was successful. If multiple threads can execute this in concurrently, multiple service tickets are
     * requested, which is discouraged for performance reasons.
     *
     * @param sieveHandler The SIEVE handler to connect
     * @param kerberosSubject The optional Kerberos subject or <code>null</code>
     * @throws OXException
     * @throws OXSieveHandlerException
     * @throws PrivilegedActionException
     * @see {@link Credentials#getSubject()}
     */
    protected void handlerConnect(final SieveHandler sieveHandler, Subject kerberosSubject) throws OXException, OXSieveHandlerException {
        try {
            if (kerberosSubject != null) {
                synchronized (kerberosSubject) {
                    Subject.doAs(kerberosSubject, new PrivilegedExceptionAction<Object>() {

                        @Override
                        public Object run() throws Exception {
                            sieveHandler.initializeConnection();
                            return null;
                        }
                    });
                }
            } else {
                sieveHandler.initializeConnection();
            }
        } catch (OXSieveHandlerInvalidCredentialsException e) {
            throw MailFilterExceptionCode.INVALID_CREDENTIALS.create(e);
        } catch (PrivilegedActionException e) {
            throw MailFilterExceptionCode.SIEVE_COMMUNICATION_ERROR.create(e);
        } catch (UnsupportedEncodingException e) {
            throw MailFilterExceptionCode.UNSUPPORTED_ENCODING.create(e);
        } catch (IOException e) {
            throw MailFilterExceptionCode.IO_CONNECTION_ERROR.create(e, sieveHandler.getSieveHost(), Integer.valueOf(sieveHandler.getSievePort()));
        }
    }

    /**
     * Find the correct position into the array
     *
     * @param rule the rule to add
     * @param rules the rules list
     * @param clientRulesAndRequire
     * @return the UID of rule and hence the position
     * @throws OXException
     */
    private int insertIntoPosition(Rule rule, RuleListAndNextUid rules, ClientRulesAndRequire clientRulesAndRequire) throws OXException {
        int position = rule.getPosition();
        List<Rule> clientRules = clientRulesAndRequire.getRules();
        if (position > clientRules.size()) {
            throw MailFilterExceptionCode.BAD_POSITION.create(Integer.valueOf(position));
        }
        int nextUid = rules.getNextuid();
        setUIDInRule(rule, nextUid);
        if (position != -1) {
            clientRules.add(position, rule);
        } else {
            clientRules.add(rule);
            position = clientRules.size() - 1;
        }
        return nextUid;
    }

    /**
     * Gets the service of specified type
     *
     * @param clazz The service's class
     * @return The requested service
     * @throws OXException If the service is not available
     */
    private <S extends Object> S getService(final Class<? extends S> clazz) throws OXException {
        final S service = services.getService(clazz);
        if (service == null) {
            throw ServiceExceptionCode.SERVICE_UNAVAILABLE.create(clazz.getSimpleName());
        }
        return service;
    }

    // ----------------------------------------------------- Helper classes -------------------------------------------------------

    private static final class HostAndPort {

        private final String host;
        private final int port;
        private final int hashCode;

        HostAndPort(String host, int port) {
            super();
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("port out of range:" + port);
            }
            if (host == null) {
                throw new IllegalArgumentException("hostname can't be null");
            }
            this.host = host;
            this.port = port;
            hashCode = (Strings.asciiLowerCase(host).hashCode()) ^ port;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof HostAndPort)) {
                return false;
            }
            HostAndPort other = (HostAndPort) obj;
            if (port != other.port) {
                return false;
            }
            if (host == null) {
                if (other.host != null) {
                    return false;
                }
            } else if (!host.equals(other.host)) {
                return false;
            }
            return true;
        }
    }

    @Override
    public Map<String, Object> getExtendedProperties(Credentials credentials) throws OXException {
        Object lock = lockFor(credentials);
        synchronized (lock) {
            SieveHandler sieveHandler = SieveHandlerFactory.getSieveHandler(credentials, getOptionalCircuitBreaker());
            try {
                handlerConnect(sieveHandler, credentials.getSubject());
                Capabilities capabilities = sieveHandler.getCapabilities();
                return capabilities.getExtendedProperties();
            } catch (OXSieveHandlerException e) {
                throw MailFilterExceptionCode.handleParsingException(e, credentials, useSIEVEResponseCodes(credentials.getUserid(), credentials.getContextid()));
            } finally {
                closeSieveHandler(sieveHandler);
            }
        }
    }

    @Override
    public String convertToString(Credentials credentials, Rule rule) throws OXException {
        new SieveTextFilter(credentials).addRequired(rule);
        Node node = RuleConverter.rulesToNodes(Collections.singletonList(rule));
        try {
            StringBuilder result = new StringBuilder();
            @SuppressWarnings("unchecked") List<OwnType> list = (List<OwnType>) node.jjtAccept(new Visitor(), null);
            for (OwnType ownTyper : list) {
                result.append(ownTyper.getOutput().toString());
            }
            return result.toString();
        } catch (SieveException e) {
            throw MailFilterExceptionCode.SIEVE_ERROR.create(e.getMessage());
        }
    }
}
