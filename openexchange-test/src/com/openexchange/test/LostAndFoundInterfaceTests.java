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

package com.openexchange.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.openexchange.test.concurrent.ParallelSuite;

/**
 * A collection of interface tests that have been found by find_tests_without_suites.rb
 *
 * @author <a href="mailto:tobias.prinz@open-xchange.com">Tobias Prinz</a>
 */
@RunWith(ParallelSuite.class)
@Suite.SuiteClasses({
    // TODO Fix or remove disabled tests
    com.openexchange.ajax.MailTest.class,
//    com.openexchange.ajax.appointment.bugtests.Bug19500Test_NewAppointmentRequestWeirdBehaviour.class,
    com.openexchange.ajax.appointment.bugtests.Bug4497Test_SharedAppDeletedByParticipant.class,
    com.openexchange.ajax.appointment.bugtests.Bug5144Test_UserGetsRemovedFromParticipantList.class,
    com.openexchange.ajax.appointment.bugtests.Bug7883Test_ReminderIsSyncedAndCrashesOutlook.class,
//    com.openexchange.ajax.appointment.recurrence.Bug12212Test.class,
    com.openexchange.ajax.appointment.recurrence.Bug12280Test.class,
    com.openexchange.ajax.appointment.recurrence.Bug12495Test.class,
    com.openexchange.ajax.appointment.recurrence.Bug12496Test.class,
//    com.openexchange.ajax.contact.Bug18862Test.class,
//    com.openexchange.ajax.contact.Bug19543Test_DeletingContactsInDistributionList.class,
    com.openexchange.ajax.contact.Bug19984Test.class,
    com.openexchange.ajax.contact.GetTest.class,
    com.openexchange.ajax.contact.ManagedSearchTests.class,
    com.openexchange.ajax.folder.api2.Bug15672Test.class,
    com.openexchange.ajax.folder.api2.Bug16284Test.class,
    com.openexchange.ajax.folder.api2.SubscribeTest.class,
//    com.openexchange.ajax.importexport.Bug17392Test.class,
    com.openexchange.ajax.importexport.Bug20360Test_UmlautBreaksImport.class,
//    com.openexchange.ajax.importexport.Bug20738Test.class,
//    com.openexchange.ajax.importexport.ICalImportExportServletTest.class,
    com.openexchange.ajax.importexport.VCardImportExportServletTest.class,
    com.openexchange.ajax.infostore.test.CreateAndDeleteInfostoreTest.class,
    com.openexchange.ajax.mail.AllRequestAndResponseTest.class,
    com.openexchange.ajax.mail.AttachmentTest.class,
    com.openexchange.ajax.mail.Bug12409Test.class,
    com.openexchange.ajax.mail.Bug14234Test.class,
    com.openexchange.ajax.mail.CopyMailWithManagerTest.class,
    com.openexchange.ajax.mail.NewMailTest.class,
//    com.openexchange.ajax.mail.addresscollector.ConfigurationTest.class,
//    com.openexchange.ajax.mail.addresscollector.MailTest.class,
    com.openexchange.ajax.passwordchange.PasswordChangeUpdateAJAXTest.class,
//    com.openexchange.ajax.session.AutologinTest.class,
    com.openexchange.ajax.task.BasicManagedTaskTests.class,
    com.openexchange.ajax.task.Bug10941Test.class,
    com.openexchange.ajax.task.Bug14450Test.class,
    com.openexchange.ajax.task.Bug21026Test.class,
})
public class LostAndFoundInterfaceTests {
}
