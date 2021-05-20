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

package com.openexchange.chronos.itip.analyzers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.chronos.ical.ImportedCalendar;
import com.openexchange.chronos.itip.ITipAnalysis;
import com.openexchange.chronos.itip.ITipAnalyzer;
import com.openexchange.chronos.itip.ITipAnalyzerService;
import com.openexchange.chronos.itip.ITipIntegrationUtility;
import com.openexchange.chronos.itip.ITipMessage;
import com.openexchange.chronos.itip.ITipMethod;
import com.openexchange.chronos.itip.ITipSpecialHandling;
import com.openexchange.chronos.itip.LegacyAnalyzing;
import com.openexchange.chronos.itip.ical.ICal4JITipParser;
import com.openexchange.chronos.itip.osgi.Services;
import com.openexchange.chronos.service.CalendarSession;
import com.openexchange.config.ConfigurationService;
import com.openexchange.exception.OXException;

/**
 * {@link DefaultITipAnalyzerService}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class DefaultITipAnalyzerService implements ITipAnalyzerService {

    public static final Integer RANKING = new Integer(0);
    
    private final static Logger LOGGER = LoggerFactory.getLogger(DeclineCounterITipAnalyzer.class);

    private final ITipAnalyzer internalAnalyzer;
    private final List<ITipAnalyzer> analyzers;

    public DefaultITipAnalyzerService(ITipIntegrationUtility util) {
        super();
        this.analyzers = Collections.unmodifiableList(Arrays.<ITipAnalyzer> asList(
            new CancelAnalyzer(util),
            new RequestAnalyzer(util),
            new ReplyAnalyzer(util),
            new AddITipAnalyzer(util),
            new CancelITipAnalyzer(util),
            new DeclineCounterITipAnalyzer(util),
            new RefreshITipAnalyzer(util),
            new ReplyITipAnalyzer(util),
            new UpdateITipAnalyzer(util)
        ));
        internalAnalyzer = new InternalITipAnalyzer(util);
    }

    @Override
    public List<ITipAnalysis> analyze(InputStream ical, String format, CalendarSession session, Map<String, String> mailHeader) throws OXException {
        return analyze(ICal4JITipParser.importCalendar(ical), format, session, mailHeader);
    }

    public List<ITipAnalysis> analyze(ImportedCalendar calendar, String format, CalendarSession session, Map<String, String> mailHeader) throws OXException {
        int owner = 0;
        if (mailHeader.containsKey("com.openexchange.conversion.owner")) {
            owner = Integer.parseInt(mailHeader.get("com.openexchange.conversion.owner"));
        }

        List<ITipMessage> messages = ICal4JITipParser.parseMessage(calendar, owner, session);

        List<ITipAnalysis> result = new ArrayList<ITipAnalysis>(messages.size());
        for (ITipMessage message : messages) {
            ITipMethod method = message.getMethod();
            if (mailHeader.containsKey("X-Mailer") && mailHeader.get("X-Mailer").toLowerCase().contains("outlook")) {
                message.addFeature(ITipSpecialHandling.MICROSOFT);
            }

            /*
             * Get analyzer to use
             */
            boolean isLegacyScheduling = isLegacyScheduling();
            ITipAnalyzer analyzer = null;
            if (mailHeader.containsKey("X-Open-Xchange-Object")) {
                analyzer = internalAnalyzer;
            } else {
                for (ITipAnalyzer iTipAnalyzer : analyzers) {
                    if (iTipAnalyzer.getMethods().contains(method)) {
                        if (isLegacyScheduling) {
                            if (LegacyAnalyzing.class.isAssignableFrom(iTipAnalyzer.getClass())) {
                                analyzer = iTipAnalyzer;
                                break;
                            }
                        } else {
                            analyzer = iTipAnalyzer;
                            break;
                        }
                    }
                }
            }

            if (analyzer == null) {
                LOGGER.error("Could not find itip analyzer for method {}", method.toString());
            } else {
                result.add(analyzer.analyze(message, mailHeader, format, session));
            }
        }

        return result;
    }

    private boolean isLegacyScheduling() {
        ConfigurationService configurationService = Services.getService(ConfigurationService.class);
        return null == configurationService || configurationService.getBoolProperty("com.openexchange.calendar.useLegacyScheduling", false);
    }

}
