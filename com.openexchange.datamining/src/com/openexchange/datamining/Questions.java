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

package com.openexchange.datamining;

import static com.openexchange.java.Autoboxing.f;
import static com.openexchange.java.Autoboxing.i;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * {@link Questions}
 *
 * @author <a href="mailto:karsten.will@open-xchange.com">Karsten Will</a>
 */
public class Questions {

    public static final String NUMBER_OF_USERS_WHO_ACTIVATED_MINI_CALENDAR = "numberOfUsersWhoActivatedMiniCalendar";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_INFOSTORE_DEFAULT = "numberOfUsersWhoSelectedListViewAsInfostoreDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_H_SPLIT_VIEW_AS_CONTACTS_DEFAULT = "numberOfUsersWhoSelectedHSplitViewAsContactsDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_TASKS_DEFAULT = "numberOfUsersWhoSelectedListViewAsTasksDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT = "numberOfUsersWhoSelectedListViewAsContactsDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT2 = NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT;

    public static final String NUMBER_OF_USERS_WHO_SELECTED_CARDS_VIEW_AS_CONTACTS_DEFAULT = "numberOfUsersWhoSelectedCardsViewAsContactsDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CALENDAR_DEFAULT = "numberOfUsersWhoSelectedListViewAsCalendarDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_CALENDAR_VIEW_AS_CALENDAR_DEFAULT = "numberOfUsersWhoSelectedCalendarViewAsCalendarDefault";

    public static final String NUMBER_OF_USERS_WHO_SELECTED_TEAM_VIEW_AS_CALENDAR_DEFAULT = "numberOfUsersWhoSelectedTeamViewAsCalendarDefault";

    public static final String NUMBER_OF_USERS_WITH_LINKED_SOCIAL_NETWORKING_ACCOUNTS = "numberOfUsersWithLinkedSocialNetworkingAccounts";

    public static final String AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CONTACTS_AT_ALL = "averageNumberOfContactsPerUserWhoHasContactsAtAll";

    public static final String AVERAGE_DRAFT_MAIL_USAGE = "averageDraftMailUsage";

    public static final String AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CREATED_CONTACTS = "averageNumberOfContactsPerUserWhoHasCreatedContacts";

    public static final String AVERAGE_NUMBER_OF_APPOINTMENTS_PER_USER_WHO_HAS_APPOINTMENTS_AT_ALL = "averageNumberOfAppointmentsPerUserWhoHasAppointmentsAtAll";

    public static final String AVERAGE_NUMBER_OF_TASKS_PER_USER_WHO_HAS_TASKS_AT_ALL = "averageNumberOfTasksPerUserWhoHasTasksAtAll";

    public static final String AVERAGE_NUMBER_OF_DOCUMENTS_PER_USER_WHO_HAS_DOCUMENTS_AT_ALL = "averageNumberOfDocumentsPerUserWhoHasDocumentsAtAll";

    public static final String NUMBER_OF_USERS_WHO_CHANGED_THEIR_CONTACTS_IN_THE_LAST30_DAYS = "numberOfUsersWhoChangedTheirContactsInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_HAVE_CONTACTS = "numberOfUsersWhoHaveContacts";

    public static final String NUMBER_OF_USERS_WHO_CREATED_CONTACTS = "numberOfUsersWhoCreatedContacts";

    public static final String MAXIMUM_NUMBER_OF_CONTACTS_FOR_ONE_USER = "maximumNumberOfContactsForOneUser";

    public static final String MAXIMUM_NUMBER_OF_CREATED_CONTACTS_FOR_ONE_USER = "maximumNumberOfCreatedContactsForOneUser";

    public static final String MAXIMUM_NUMBER_OF_CREATED_APPOINTMENTS_FOR_ONE_USER = "maximumNumberOfCreatedAppointmentsForOneUser";

    public static final String MAXIMUM_NUMBER_OF_CREATED_DOCUMENTS_FOR_ONE_USER = "maximumNumberOfCreatedDocumentsForOneUser";

    public static final String MAXIMUM_NUMBER_OF_CREATED_TASKS_FOR_ONE_USER = "maximumNumberOfCreatedTasksForOneUser";

    public static final String NUMBER_OF_CONTACTS = "numberOfContacts";

    public static final String NUMBER_OF_USER_CREATED_CONTACTS = "numberOfUSerCreatedContacts";

    public static final String NUMBER_OF_USERS_WHO_CREATED_APPOINTMENTS = "numberOfUsersWhoCreatedAppointments";

    public static final String NUMBER_OF_APPOINTMENTS = "numberOfAppointments";

    public static final String NUMBER_OF_USERS_WHO_CREATED_TASKS = "numberOfUsersWhoCreatedTasks";

    public static final String NUMBER_OF_TASKS = "numberOfTasks";

    public static final String NUMBER_OF_USERS_WHO_CREATED_DOCUMENTS = "numberOfUsersWhoCreatedDocuments";

    public static final String NUMBER_OF_DOCUMENTS = "numberOfDocuments";

    public static final String NUMBER_OF_USERS_WITH_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS = "numberOfUsersWithNewInfostoreObjectsInTheLast30Days";

    public static final String NUMBER_OF_CHANGED_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS = "numberOfChangedInfostoreObjectsInTheLast30Days";

    public static final String NUMBER_OF_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS = "numberOfNewInfostoreObjectsInTheLast30Days";

    public static final String NUMBER_OF_INFOSTORE_OBJECTS = "numberOfInfostoreObjects";

    public static final String NUMBER_OF_USERS_WHO_CHANGED_THEIR_CALENDAR_IN_THE_LAST30_DAYS = "numberOfUsersWhoChangedTheirCalendarInTheLast30Days";

    public static final String NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR_THAT_ARE_IN_THE_FUTURE_AND_ARE_NOT_YEARLY_SERIES = "numberOfUsersWithEventsInPrivateCalendarThatAreInTheFutureAndAreNotYearlySeries";

    public static final String NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR = "numberOfUsersWithEventsInPrivateCalendar";

    public static final String NUMBER_OF_USERS = "numberOfUsers";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_LINKEDIN = "numberOfUsersConnectedToLinkedIn";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_TWITTER = "numberOfUsersConnectedToTwitter";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_GOOGLE = "numberOfUsersConnectedToGoogle";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_MSN = "numberOfUsersConnectedToMSN";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_YAHOO = "numberOfUsersConnectedToYahoo";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_XING = "numberOfUsersConnectedToXing";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_TONLINE = "numberOfUsersConnectedToTOnline";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_WEBDE = "numberOfUsersConnectedToWebDe";

    public static final String NUMBER_OF_USERS_CONNECTED_TO_GMX = "numberOfUsersConnectedToGMX";

    public static final String NUMBER_OF_USERS_WITH_TASKS = "numberOfUsersWithTasks";

    public static final String NUMBER_OF_USERS_WHO_CHANGED_THEIR_TASKS_IN_THE_LAST30_DAYS = "numberOfUsersWhoChangedTheirTasksInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_OX6UI_IN_THE_LAST_30_DAYS = "numberOfUsersWhoLoggedInWithClientOX6UIInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_APPSUITEUI_IN_THE_LAST_30_DAYS = "numberOfUsersWhoLoggedInWithClientAppSuiteUIInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_EAS_IN_THE_LAST_30_DAYS = "numberOfUsersWhoUsedEASInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_MOBILEUI_IN_THE_LAST_30_DAYS = "numberOfUsersWhoLoggedInWithClientMobileUIInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CALDAV_IN_THE_LAST_30_DAYS = "numberOfUsersWhoUsedCalDAVInTheLast30Days";

    public static final String NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CARDDAV_IN_THE_LAST_30_DAYS = "numberOfUsersWhoUsedCardDAVInTheLast30Days";

    public static final String AVERAGE_DOCUMENT_SIZE = "averageDocumentSize";

    protected static void reportNumberOfUsers() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS);
            String sql = "SELECT count(*) FROM user";
            BigInteger count = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS, count.toString());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    protected static void reportNumberOfUsersWithEventsInPrivateCalendar() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR);
            String sql = "SELECT COUNT(DISTINCT cid, user) FROM calendar_event WHERE account=0 AND folder IS NULL;";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    private static void handleError(Exception e) {
        //TODO properly handle errors
        System.out.println("Error : " + e.getMessage());
    }

    protected static void reportNumberOfUsersWithEventsInPrivateCalendarThatAreInTheFutureAndAreNotYearlySeries() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR_THAT_ARE_IN_THE_FUTURE_AND_ARE_NOT_YEARLY_SERIES);
            String sql = "SELECT COUNT(DISTINCT cid, user) FROM calendar_event WHERE account=0 AND start > NOW() AND folder IS NULL AND (rrule IS NULL OR rrule NOT LIKE '%FREQ=YEARLY%');";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WITH_EVENTS_IN_PRIVATE_CALENDAR_THAT_ARE_IN_THE_FUTURE_AND_ARE_NOT_YEARLY_SERIES, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoChangedTheirCalendarInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CHANGED_THEIR_CALENDAR_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT cid, user) FROM calendar_event WHERE account=0 AND timestamp>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CHANGED_THEIR_CALENDAR_IN_THE_LAST30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfNewInfostoreObjectsInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT infostore_id, cid) FROM infostore_document WHERE DATE(FROM_UNIXTIME(SUBSTRING(CAST(creating_date AS CHAR) FROM 1 FOR 10))) BETWEEN (NOW() - INTERVAL 30 DAY) AND NOW();";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfChangedInfostoreObjectsInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_CHANGED_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT infostore_id, cid) FROM infostore_document WHERE creating_date>= " + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_CHANGED_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWithNewInfostoreObjectsInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WITH_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT created_by, cid) FROM infostore_document WHERE creating_date>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WITH_NEW_INFOSTORE_OBJECTS_IN_THE_LAST30_DAYS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfContacts() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_CONTACTS);
            String sql = "SELECT count(*) FROM prg_contacts WHERE userid IS NULL;";
            BigInteger numberOfContacts = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_CONTACTS, numberOfContacts.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUserCreatedContacts() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USER_CREATED_CONTACTS);
            String sql = "SELECT count(*) FROM prg_contacts WHERE userid IS NULL AND field02 IS NOT NULL AND field03 IS NOT NULL;";
            BigInteger numberOfContacts = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USER_CREATED_CONTACTS, numberOfContacts.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfAppointments() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_APPOINTMENTS);
            String sql = "SELECT count(*) FROM calendar_event WHERE account=0;";
            BigInteger numberOfAppointments = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_APPOINTMENTS, numberOfAppointments.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfTasks() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_TASKS);
            String sql = "SELECT count(*) FROM task;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_TASKS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfDocuments() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_DOCUMENTS);
            String sql = "SELECT count(*) FROM infostore_document;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_DOCUMENTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoHaveContacts() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_HAVE_CONTACTS);
            String sql = "SELECT count(DISTINCT created_from, cid) FROM prg_contacts WHERE userid IS NULL;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_HAVE_CONTACTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoCreatedContacts() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CREATED_CONTACTS);
            String sql = "SELECT count(DISTINCT created_from, cid) FROM prg_contacts WHERE userid IS NULL AND field02 IS NOT NULL AND field03 IS NOT NULL;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CREATED_CONTACTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportMaximumNumberOfContactsForOneUser() {
        try {
            Datamining.allTheQuestions.add(MAXIMUM_NUMBER_OF_CREATED_CONTACTS_FOR_ONE_USER);
            // GROUP BY CLAUSE: ensure ONLY_FULL_GROUP_BY compatibility
            String sql = "SELECT MAX(count) FROM (SELECT cid, created_from, count(*) AS count FROM prg_contacts WHERE userid IS NULL GROUP BY cid, created_from) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.maximumForAllSchemata(sql);
            Datamining.report(MAXIMUM_NUMBER_OF_CREATED_CONTACTS_FOR_ONE_USER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportMaximumNumberOfCreatedAppointmentsForOneUser() {
        try {
            Datamining.allTheQuestions.add(MAXIMUM_NUMBER_OF_CREATED_APPOINTMENTS_FOR_ONE_USER);
            // GROUP BY CLAUSE: ensure ONLY_FULL_GROUP_BY compatibility
            String sql = "SELECT MAX(count) FROM (SELECT COUNT(*) AS count FROM calendar_event WHERE account=0 GROUP BY cid, createdBy) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.maximumForAllSchemata(sql);
            Datamining.report(MAXIMUM_NUMBER_OF_CREATED_APPOINTMENTS_FOR_ONE_USER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportMaximumNumberOfCreatedDocumentsForOneUser() {
        try {
            Datamining.allTheQuestions.add(MAXIMUM_NUMBER_OF_CREATED_DOCUMENTS_FOR_ONE_USER);
            String sql = "SELECT MAX(count) FROM (SELECT cid, created_by, count(*) AS count FROM infostore_document) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.maximumForAllSchemata(sql);
            Datamining.report(MAXIMUM_NUMBER_OF_CREATED_DOCUMENTS_FOR_ONE_USER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportMaximumNumberOfCreatedTasksForOneUser() {
        try {
            Datamining.allTheQuestions.add(MAXIMUM_NUMBER_OF_CREATED_TASKS_FOR_ONE_USER);
            String sql = "SELECT MAX(count) FROM (SELECT cid, created_from, count(*) AS count FROM task) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.maximumForAllSchemata(sql);
            Datamining.report(MAXIMUM_NUMBER_OF_CREATED_TASKS_FOR_ONE_USER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportMaximumNumberOfCreatedContactsForOneUser() {
        try {
            Datamining.allTheQuestions.add(MAXIMUM_NUMBER_OF_CREATED_CONTACTS_FOR_ONE_USER);
            // GROUP BY CLAUSE: ensure ONLY_FULL_GROUP_BY compatibility
            String sql = "SELECT MAX(count) FROM (SELECT cid, created_from, count(*) AS count FROM prg_contacts WHERE userid IS NULL AND field02 IS NOT NULL AND field03 IS NOT NULL GROUP BY cid, created_from) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.maximumForAllSchemata(sql);
            Datamining.report(MAXIMUM_NUMBER_OF_CREATED_CONTACTS_FOR_ONE_USER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoCreatedAppointments() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CREATED_APPOINTMENTS);
            String sql = "SELECT COUNT(DISTINCT cid, createdBy) FROM calendar_event WHERE account=0;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CREATED_APPOINTMENTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoCreatedTasks() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CREATED_TASKS);
            String sql = "SELECT count(DISTINCT created_from, cid) FROM task;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CREATED_TASKS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoCreatedDocuments() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CREATED_DOCUMENTS);
            String sql = "SELECT count(DISTINCT created_by, cid) FROM infostore_document;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CREATED_DOCUMENTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoChangedTheirContactsInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CHANGED_THEIR_CONTACTS_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT created_from, cid) FROM prg_contacts WHERE userid IS NULL AND field02 IS NOT NULL AND field03 IS NOT NULL AND changing_date>= " + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CHANGED_THEIR_CONTACTS_IN_THE_LAST30_DAYS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportSliceAndDiceOnDocumentSize() {
        try {

            String[] size1 = {
                "1", "3000", "10000", "30000", "100000", "300000", "1000000", "3000000", "10000000", "30000000", "100000000", "300000000",
                "1000000000" };
            String[] size2 = {
                "3000", "10000", "30000", "100000", "300000", "1000000", "3000000", "10000000", "30000000", "100000000", "300000000",
                "1000000000", "9999999999" };
            if (size1.length == size2.length) {
                for (int i = 0; i < size1.length; i++) {
                    Datamining.allTheQuestions.add("numberOfDocumentsBetween" + Tools.humanReadableBytes(size1[i]) + "And" + Tools.humanReadableBytes(size2[i]));
                    String sql = "SELECT COUNT(DISTINCT infostore.cid, infostore.id, infostore.version) FROM infostore LEFT OUTER JOIN infostore_document " + "ON infostore.cid = infostore_document.cid AND infostore.id = infostore_document.infostore_id " + "AND infostore.version = infostore_document.version_number WHERE infostore_document.file_size BETWEEN " + size1[i] + " AND " + size2[i];
                    BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
                    Datamining.report("numberOfDocumentsBetween" + Tools.humanReadableBytes(size1[i]) + "And" + Tools.humanReadableBytes(size2[i]), numberOfInfostoreObjects.toString());
                }
            } else {
                System.out.println("Error : Ranges in reportSliceAndDiceOnDocumentSize are not equal");
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportSliceAndDiceOnDraftMailSize() {
        try {
            LinkedHashMap<Integer, Integer> dms = Datamining.draftMailOverAllSchemata(new int[] { 5000, 10000, 500000 });
            int ll = 1;
            for (final Entry<Integer, Integer> entry : dms.entrySet()) {
                Datamining.report("draftMailSizeBetween" + Tools.humanReadableBytes("" + ll) + "And" + Tools.humanReadableBytes("" + entry.getKey()), Integer.toString(entry.getValue() == null ? 0 : i(entry.getValue())));
                ll = i(entry.getKey());
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportSliceAndDiceOnExternalAccountUsage() {
        try {
            int MAXEXT = 5;
            HashMap<Integer, Integer> eaos = Datamining.externalAccountsOverAllSchemata(MAXEXT);
            for (final Entry<Integer, Integer> entry : eaos.entrySet()) {
                Datamining.report("usersHaving" + (i(entry.getKey()) < MAXEXT ? entry.getKey() : "MoreOrEqual" + entry.getKey()) + "ExternalAccounts", Integer.toString(entry.getValue() == null ? 0 : i(entry.getValue())));
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportAverageDocumentSize() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_DOCUMENT_SIZE);

            String sql = "SELECT AVG(file_size) FROM infostore_document;";
            Float result = Datamining.averageForAllSchemata(sql);
            int resultInt = Math.round(f(result));

            Datamining.report(AVERAGE_DOCUMENT_SIZE, Tools.humanReadableBytes(Integer.toString(resultInt)));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportAverageNumberOfContactsPerUserWhoHasContactsAtAll() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CONTACTS_AT_ALL);
            // take numberOfContacts
            // take numberOfUsers and subtract numberOfContexts (thereby not counting the context-admins) -> relevant number of users
            // divide the two (numberOfContacts / (numberOfUsers - numberOfContexts)) -> average number of contacts per User

            float numberOfContacts = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_CONTACTS)));
            float numberOfUsers = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USERS_WHO_HAVE_CONTACTS)));

            Datamining.report(AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CONTACTS_AT_ALL, Float.toString(numberOfContacts / numberOfUsers));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportAverageNumberOfContactsPerUserWhoHasCreatedContacts() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CREATED_CONTACTS);
            // take numberOfContacts
            // take numberOfUsers and subtract numberOfContexts (thereby not counting the context-admins) -> relevant number of users
            // divide the two (numberOfContacts / (numberOfUsers - numberOfContexts)) -> average number of contacts per User

            float numberOfContacts = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USER_CREATED_CONTACTS)));
            float numberOfUsers = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USERS_WHO_CREATED_CONTACTS)));

            Datamining.report(AVERAGE_NUMBER_OF_CONTACTS_PER_USER_WHO_HAS_CREATED_CONTACTS, Float.toString(numberOfContacts / numberOfUsers));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportAverageNumberOfAppointmentsPerUserWhoHasAppointmentsAtAll() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_NUMBER_OF_APPOINTMENTS_PER_USER_WHO_HAS_APPOINTMENTS_AT_ALL);

            float numberOfAppointments = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_APPOINTMENTS)));
            float numberOfUsers = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USERS_WHO_CREATED_APPOINTMENTS)));

            Datamining.report(AVERAGE_NUMBER_OF_APPOINTMENTS_PER_USER_WHO_HAS_APPOINTMENTS_AT_ALL, Float.toString(numberOfAppointments / numberOfUsers));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportAverageNumberOfTasksPerUserWhoHasTasksAtAll() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_NUMBER_OF_TASKS_PER_USER_WHO_HAS_TASKS_AT_ALL);

            float numberOfTasks = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_TASKS)));
            float numberOfUsers = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USERS_WHO_CREATED_TASKS)));

            Datamining.report(AVERAGE_NUMBER_OF_TASKS_PER_USER_WHO_HAS_TASKS_AT_ALL, Float.toString(numberOfTasks / numberOfUsers));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportAverageNumberOfDocumentsPerUserWhoHasDocumentsAtAll() {
        try {
            Datamining.allTheQuestions.add(AVERAGE_NUMBER_OF_DOCUMENTS_PER_USER_WHO_HAS_DOCUMENTS_AT_ALL);

            float numberOfDocuments = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_DOCUMENTS)));
            float numberOfUsers = f(Float.valueOf(Datamining.getOneAnswer(NUMBER_OF_USERS_WHO_CREATED_DOCUMENTS)));

            Datamining.report(AVERAGE_NUMBER_OF_DOCUMENTS_PER_USER_WHO_HAS_DOCUMENTS_AT_ALL, Float.toString(numberOfDocuments / numberOfUsers));
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWithLinkedSocialNetworkingAccounts() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WITH_LINKED_SOCIAL_NETWORKING_ACCOUNTS);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o UNION SELECT s.user_id, s.cid FROM subscriptions s) AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WITH_LINKED_SOCIAL_NETWORKING_ACCOUNTS, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedTeamViewAsCalendarDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_TEAM_VIEW_AS_CALENDAR_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"team\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_TEAM_VIEW_AS_CALENDAR_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedCalendarViewAsCalendarDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_CALENDAR_VIEW_AS_CALENDAR_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"calendar\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_CALENDAR_VIEW_AS_CALENDAR_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedListViewAsCalendarDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CALENDAR_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"list\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CALENDAR_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedCardsViewAsContactsDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_CARDS_VIEW_AS_CONTACTS_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"contacts/cards\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_CARDS_VIEW_AS_CONTACTS_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedListViewAsContactsDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT2);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"contacts/phonelist\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT2, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedListViewAsTasksDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_TASKS_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"tasks/list\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_TASKS_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedHSplitViewAsTasksDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_H_SPLIT_VIEW_AS_CONTACTS_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"tasks/split\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_H_SPLIT_VIEW_AS_CONTACTS_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedListViewAsInfostoreDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_INFOSTORE_DEFAULT);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"infostore/list\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_INFOSTORE_DEFAULT, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoSelectedHSplitViewAsInfostoreDefault() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT2);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"view\":\"infostore/split\"%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_SELECTED_LIST_VIEW_AS_CONTACTS_DEFAULT2, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersWhoActivatedMiniCalendar() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_ACTIVATED_MINI_CALENDAR);
            String sql = "SELECT count(*) FROM user_setting WHERE value LIKE '%\"minicalendar\":{\"expanded\":true}%';";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_ACTIVATED_MINI_CALENDAR, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToLinkedIn() {
        String networkName = "linkedin";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_LINKEDIN);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_LINKEDIN, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToTwitter() {
        String networkName = "twitter";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_TWITTER);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_TWITTER, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToGoogle() {
        String networkName = "google";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_GOOGLE);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_GOOGLE, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToMSN() {
        String networkName = "msn";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_MSN);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_MSN, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToYahoo() {
        String networkName = "yahoo";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_YAHOO);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_YAHOO, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToXing() {
        String networkName = "xing";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_XING);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_XING, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToTOnline() {
        String networkName = "t-online";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_TONLINE);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_TONLINE, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToGMX() {
        String networkName = "gmx";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_GMX);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_GMX, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    public static void reportNumberOfUsersConnectedToWebDe() {
        String networkName = "web";
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_CONNECTED_TO_WEBDE);
            String sql = "SELECT COUNT(*) FROM (SELECT o.user, o.cid FROM oauthAccounts o where o.serviceId LIKE '%" + networkName + "%' UNION SELECT s.user_id, s.cid FROM subscriptions s where s.source_id LIKE '%" + networkName + "%') AS x;";
            BigInteger numberOfInfostoreObjects = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_CONNECTED_TO_WEBDE, numberOfInfostoreObjects.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWithTasks() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WITH_TASKS);
            String sql = "select count(distinct created_from, cid) from task";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WITH_TASKS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoChangedTheirTasksInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_CHANGED_THEIR_TASKS_IN_THE_LAST30_DAYS);
            String sql = "SELECT count(DISTINCT created_from, cid) FROM task WHERE last_modified>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_CHANGED_THEIR_TASKS_IN_THE_LAST30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientOX6UIInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_OX6UI_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:com.openexchange.ox.gui.dhtml' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_OX6UI_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientAppSuiteUIInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_APPSUITEUI_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:open-xchange-appsuite' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_APPSUITEUI_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientEASInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_EAS_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:USM-EAS' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_EAS_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientMobileUIInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_MOBILEUI_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:com.openexchange.mobileapp' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_MOBILEUI_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientCalDAVInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CALDAV_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:CALDAV' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CALDAV_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }

    protected static void reportNumberOfUsersWhoLoggedInWithClientCardDAVInTheLast30Days() {
        try {
            Datamining.allTheQuestions.add(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CARDDAV_IN_THE_LAST_30_DAYS);
            String sql = "SELECT count(DISTINCT id, cid) FROM user_attribute WHERE name = 'client:CARDDAV' AND value>=" + Tools.calculate30DaysBack() + ";";
            BigInteger numberOfUsers = Datamining.countOverAllSchemata(sql);
            Datamining.report(NUMBER_OF_USERS_WHO_LOGGED_IN_WITH_CLIENT_CARDDAV_IN_THE_LAST_30_DAYS, numberOfUsers.toString());
        } catch (Exception e) {
            handleError(e);
        }
    }
}
