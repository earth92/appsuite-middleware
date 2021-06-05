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

package com.openexchange.importexport.importers;

import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.junit4.PowerMockRunner;
import com.openexchange.exception.OXException;
import com.openexchange.groupware.contact.helpers.ContactSwitcher;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.importexport.ImportResult;
import com.openexchange.importexport.formats.csv.PropertyDrivenMapper;
import com.openexchange.server.ServiceLookup;
import com.openexchange.test.mock.MockUtils;


/**
 * {@link CSVContactImporterTest}
 *
 * @author <a href="mailto:martin.schneider@open-xchange.com">Martin Schneider</a>
 * @since 7.6.0
 */
@RunWith(PowerMockRunner.class)
public class CSVContactImporterTest {

    /**
     * Unit under test
     */
    private CSVContactImporter csvContactImporter;

    @Mock
    private ServiceLookup serviceLookup;

    private final List<String> fields = new LinkedList<String>(Arrays.asList(new String[] {
        "Titel", "Voornaam", "Middelste naam", "Achternaam", "Achtervoegsel", "Bedrijf", "Afdeling", "Functie", "Werkadres, straat",
        "Werkadres 2, straat", "Werkadres 3, straat", "Werkadres, plaats", "Werkadres, provincie", "Werkadres, postcode",
        "Land/regio (werk)", "Huisadres, straat", "Huisadres, straat 2", "Huisadres, straat 3", "Huisadres, plaats",
        "Huisadres, provincie", "Huisadres, postcode", "Land/regio (thuis)", "Ander adres, straat", "Ander adres, straat 2",
        "Ander adres, straat 3", "Ander adres, plaats", "Ander adres, provincie", "Ander adres, postcode", "Land/regio (anders)",
        "Telefoon assistent", "Fax op werk", "Telefoon op werk", "Telefoon op werk 2", "Terugbellen", "Autotelefoon",
        "Hoofdtelefoon bedrijf", "Fax thuis", "Telefoon thuis", "Telefoon thuis 2", "ISDN", "Mobiele telefoon", "Andere fax",
        "Andere telefoon", "Pager", "Hoofdtelefoon", "Radiotelefoon", "Teksttelefoon", "Telex", "Account", "Adreslijstserver",
        "Ander adres, postbusnummer", "Beroep", "Categorien", "E-mailadres", "E-mailtype", "Weergavenaam voor e-mail", "E-mailadres 2",
        "E-mailtype 2", "E-mail, weergegeven naam 2", "E-mailadres 3", "E-mailtype 3", "E-mail, weergegeven naam 3", "Factuurinformatie",
        "Gebruiker 1", "Gebruiker 2", "Gebruiker 3", "Gebruiker 4", "Geslacht", "Gevoeligheid", "Hobby's", "Huisadres, postbusnummer",
        "Initialen", "Internet Beschikbaarheidsinfo", "Kantoorlocatie", "Kinderen", "Locatie", "Naam assistent", "Naam manager",
        "Notities", "Organisatie-id", "Partner", "Prioriteit", "Priv", "Referentie van", "Reisafstand", "Sofinummer", "Speciale datum",
        "Taal", "Trefwoorden", "Verjaardag", "Webpagina", "Werkadres, postbusnummer" }));


    private final List<String> emptyEntry = new LinkedList<String>(Arrays.asList(new String[] {
        "", "Hans", "", "Wurst", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
        "", "", "", "", "Niet-gespecificeerd", "Normaal", "", "", "H.W.", "", "", "", "", "", "", "", "", "", "Normaal", "Onwaar", "", "",
        "", "0.0.00", "", "", "0-0-00", "", ""
    }));

    private final List<String> midEntry = new LinkedList<String>(Arrays.asList(new String[] {
        "Dr.", "Hans", "Jocachim", "Wurst", "Achtervoegsel", "Betreff", "afdeling", "Funktion", "Arbeitsstrasse",
        "Arbeitsstrasse2", "Arbeitsstrasse3", "Platz", "Province", "PLZ",
        "NRW-NL", "Hausstrasse", "Hausstrasse2", "Hausstrasse3", "Hausstrasse Platz",
        "Hausstrasse Province", "Hausstrasse PLZ", "Land", "Andere Adresse strasse", "Andere Adresse strasse2",
        "Andere Adresse strasse3", "Andere Adresse platz", "Andere Adresse provinz", "Andere Adresse PLZ", "Andere Adresse land",
        "Telefonassistent", "FaxWerk", "TelefonWerk", "TelefonWerk2", "Terugbellen", "AutoTel",
        "Hofftelefon", "FaxThius", "TelefonThuis", "TelefonThuis2", "isdn", "obile", "andere fax",
        "andere tel", "pager", "Hooftelefon", "Radiotel", "Teksttelefoon", "telex", "account", "adreslistserver",
        "Andere adresse postbusnummer", "Beroep", "Categorien: keine", "hans@wurst.nl", "maintype adresse", "hans voor mail", "emial 2",
        "email type 2", "email weergeven 2", "email 3", "email type 3", "mail weergeven 3", "facturrinformation",
        "gebruiker1", "gebruiker2", "gebruiker3", "gebruiker4", "geschlecht", "gevoeligheid", "hobbies", "adresse at home",
        "HansWurstinitialien HW", "Internet Beschickbaarheidsinfo", "Kantorrlokation", "Kinder 10", "Lokation", "Assistensaerztin name", "Manager Name",
        "Notizen: nett", "Id der organisation", "Partner: keine", "Prioritaet: hoch", "Priv: ja", "Referentie van", "Reisafstand", "Sofinummer", "29.02.2000",
        "Taal", "Trefwoorden", "00-00-0000", "webpagina", "postbusnummer" }));

    private final int lineNumber = 1;

    private ImportResult result;

    private final boolean[] atLeastOneFieldInserted = new boolean[10];

    private final Properties properties = new Properties();

    private final String propertiesString = "encoding=UTF-8\n" +
        "first_name=Voornaam\n" +
        "last_name=Achternaam\n" +
        "second_name=Middelste naam\n" +
        "suffix=Achtervoegsel\n" +
        "title=Titel\n" +
        "street_home=Huisadres, straat\n" +
        "postal_code_home=Huisadres, postcode\n" +
        "city_home=Huisadres, plaats\n" +
        "country_home=Land/regio (thuis)\n" +
        "birthday=Verjaardag\n" +
        "number_of_children=Kinderen\n" +
        "profession=Beroep\n" +
        "spouse_name=Partner\n" +
        "note=Notities\n" +
        "department=Afdeling\n" +
        "position=Functie\n" +
        "street_business=Werkadres, straat\n" +
        "postal_code_business=Werkadres, postcode\n" +
        "country_business=Land/regio (werk)\n" +
        "manager_name=Naam manager\n" +
        "assistant_name=Naam assistent\n" +
        "street_other=Ander adres, straat\n" +
        "postal_code_other=Ander adres, postcode\n" +
        "country_other=Land/regio (anders)\n" +
        "telephone_business1=Telefoon op werk\n" +
        "telephone_business2=Telefoon op werk 2\n" +
        "fax_business=Fax op werk\n" +
        "telephone_callback=Terugbellen\n" +
        "telephone_car=Autotelefoon\n" +
        "telephone_home1=Telefoon thuis\n" +
        "telephone_home2=Telefoon thuis 2\n" +
        "fax_home=Fax thuis\n" +
        "cellular_telephone1=Mobiele telefoon\n" +
        "telephone_other=Andere telefoon\n" +
        "fax_other=Andere fax\n" +
        "email1=E-mailadres\n" +
        "email2=E-mailadres 2\n" +
        "email3=E-mailadres 3\n" +
        "telephone_isdn=ISDN\n" +
        "telephone_pager=Pager\n" +
        "telephone_radio=Radiotelefoon\n" +
        "telephone_telex=Telex\n" +
        "telephone_assistant=Telefoon assistent\n" +
        "company=Bedrijf\n" +
        "city_business=Werkplaats\n" +
        "country_business=Werkland\n" +
        "display_name=Weergavenaam\n" +
        "state_business=Werkprovincie\n" +
        "state_home=Provincie\n" +
        "url=Webpagina 1\n" +
        "birthday_year=Geboortejaar\n" +
        "birthday_month=Geboortemaand\n" +
        "birthday_day=Geboortedag";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Arrays.fill(atLeastOneFieldInserted, false);
        result = new ImportResult();
        result.setFolder("27944");

        csvContactImporter = new CSVContactImporter(serviceLookup);

        properties.load(new StringReader(propertiesString));

        MockUtils.injectValueIntoPrivateField(csvContactImporter, "currentMapper", new PropertyDrivenMapper(properties, "test.properties"));
    }

     @Test
     public void testConvertCsvToContact_importCorrect_nameSetCorrectlyBirthdayIgnored() throws OXException {
        ContactSwitcher contactSwitcher = csvContactImporter.getContactSwitcher(null);

        Contact contact = csvContactImporter.convertCsvToContact(fields, emptyEntry, contactSwitcher, lineNumber, result, atLeastOneFieldInserted);

        Assert.assertEquals("Hans", contact.getGivenName());
        Assert.assertEquals("Wurst", contact.getSurName());
        Assert.assertNull(contact.getBirthday());
    }

     @Test
     public void testConvertCsvToContact_importCorrect_correctlySet() throws OXException {
        ContactSwitcher contactSwitcher = csvContactImporter.getContactSwitcher(null);

        Contact contact = csvContactImporter.convertCsvToContact(fields, midEntry, contactSwitcher, lineNumber, result, atLeastOneFieldInserted);

        Assert.assertEquals("Hans", contact.getGivenName());
        Assert.assertEquals("Wurst", contact.getSurName());
        Assert.assertEquals("hans@wurst.nl", contact.getEmail1());
        Assert.assertEquals("Dr.", contact.getTitle());
        Assert.assertEquals("Partner: keine", contact.getSpouseName());
        Assert.assertEquals("Hausstrasse", contact.getStreetHome());
        Assert.assertEquals("Funktion", contact.getPosition());
    }
}
