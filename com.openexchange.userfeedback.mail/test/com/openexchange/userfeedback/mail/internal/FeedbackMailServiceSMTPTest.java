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
package com.openexchange.userfeedback.mail.internal;

import static com.openexchange.java.Autoboxing.I;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import javax.mail.Transport;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.config.ConfigurationService;
import com.openexchange.config.lean.LeanConfigurationService;
import com.openexchange.exception.OXException;
import com.openexchange.net.ssl.SSLSocketFactoryProvider;
import com.openexchange.userfeedback.FeedbackService;
import com.openexchange.userfeedback.exception.FeedbackExceptionCodes;
import com.openexchange.userfeedback.export.ExportResult;
import com.openexchange.userfeedback.export.ExportResultConverter;
import com.openexchange.userfeedback.export.ExportType;
import com.openexchange.userfeedback.mail.filter.FeedbackMailFilter;
import com.openexchange.userfeedback.mail.osgi.Services;

/**
 * {@link FeedbackMailServiceSMTPTest}
 *
 * @author <a href="mailto:vitali.sjablow@open-xchange.com">Vitali Sjablow</a>
 * @since v7.8.4
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Services.class, FeedbackService.class, SSLSocketFactoryProvider.class, FeedbackMailServiceSMTP.class, LeanConfigurationService.class, Transport.class})
public class FeedbackMailServiceSMTPTest {

    @Mock
    private ConfigurationService configService;
    @Mock
    private FeedbackService feedbackService;
    @Mock
    private LeanConfigurationService leanConfigurationService;
    @Mock
    private Transport transport;

    private FeedbackMailFilter filter;

    private Properties properties;

    @Before
    public void setUp() throws Exception {
        filter = new FeedbackMailFilter("1", new HashMap<String, String>(),  "sub", "body", 0l, 0l, "", false);

        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Services.class);
        PowerMockito.when(Services.getService(ConfigurationService.class)).thenReturn(configService);
        PowerMockito.when(Services.getService(FeedbackService.class)).thenReturn(feedbackService);
        PowerMockito.when(Services.getService(FeedbackService.class)).thenReturn(feedbackService);
        PowerMockito.when(Services.getService(LeanConfigurationService.class)).thenReturn(leanConfigurationService);

        Mockito.when(configService.getProperty(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn("");
        Mockito.when(I(configService.getIntProperty(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))).thenReturn(I(1));

        ExportResultConverter value = new ExportResultConverter() {

            @Override
            public ExportResult get(ExportType type) {
                return new ExportResult() {
                    @Override
                    public Object getResult() {
                        String source = "This is the source of my input stream";
                        InputStream in = null;
                        try {
                            in = IOUtils.toInputStream(source, "UTF-8");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return in;
                    }
                };
            }
        };
        Mockito.when(feedbackService.export("1", filter)).thenReturn(value);
        properties = new Properties();
    }

    @Test
    public void sendFeedbackMail_FailInvalidAddresses() throws Exception {
        FeedbackMailServiceSMTP service = new FeedbackMailServiceSMTP();
        FeedbackMailServiceSMTP serviceSpy = PowerMockito.spy(service);

        PowerMockito.whenNew(Transport.class).withAnyArguments().thenReturn(transport);
        PowerMockito.doNothing().when(transport).connect(ArgumentMatchers.any(String.class), ArgumentMatchers.any(Integer.class).intValue(), ArgumentMatchers.any(String.class), ArgumentMatchers.any(String.class));

        PowerMockito.doReturn(properties).when(serviceSpy, PowerMockito.method(FeedbackMailServiceSMTP.class, "getSMTPProperties")).withArguments(leanConfigurationService);
        filter.getRecipients().put("dsfa", "");
        try {
            serviceSpy.sendFeedbackMail(filter);
        } catch (OXException e) {
            assertEquals(FeedbackExceptionCodes.INVALID_EMAIL_ADDRESSES, e.getExceptionCode());
            return;
        }
        // should never get here
        assertFalse(true);
    }

    @Test
    public void sendFeedbackMail_FailInvalidSMTP() throws Exception {
        FeedbackMailServiceSMTP service = new FeedbackMailServiceSMTP();
        FeedbackMailServiceSMTP serviceSpy = PowerMockito.spy(service);

        PowerMockito.doReturn(properties).when(serviceSpy, PowerMockito.method(FeedbackMailServiceSMTP.class, "getSMTPProperties")).withArguments(leanConfigurationService);

        filter.getRecipients().put("dsfa@blub.de", "");
        try {
            serviceSpy.sendFeedbackMail(filter);
        } catch (OXException e) {
            assertEquals(FeedbackExceptionCodes.INVALID_SMTP_CONFIGURATION, e.getExceptionCode());
            return;
        }
        // should never get here
        assertFalse(true);
    }
}
