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

package com.openexchange.admin.plugins;

import java.util.Date;
import com.openexchange.groupware.container.Contact;


/**
 * Takes a {@link com.openexchange.admin.rmi.dataobjects.User} and adapts it to a {@link Contact}.
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.6.0
 */
public class ContactAdapter extends Contact {

    private static final long serialVersionUID = -1080817657555712440L;

    private final com.openexchange.admin.rmi.dataobjects.User delegate;
    private final int contextId;

    public ContactAdapter(com.openexchange.admin.rmi.dataobjects.User delegate, final int contextId) {
        super();
        this.delegate = delegate;
        this.contextId = contextId;
    }

    @Override
    public int getContextId() {
        return contextId;
    }

    @Override
    public final int getObjectID() {
        return delegate.getId().intValue();
    }

    @Override
    public String getSurName() {
        return delegate.getSur_name();
    }

    @Override
    public String getGivenName() {
        return delegate.getGiven_name();
    }

    @Override
    public final Date getBirthday() {
        return delegate.getBirthday();
    }

    @Override
    public final Date getAnniversary() {
        return delegate.getAnniversary();
    }

    @Override
    public final String getBranches() {
        return delegate.getBranches();
    }

    @Override
    public String getBusinessCategory() {
        return delegate.getBusiness_category();
    }

    @Override
    public String getPostalCodeBusiness() {
        return delegate.getPostal_code_business();
    }

    @Override
    public String getStateBusiness() {
        return delegate.getState_business();
    }

    @Override
    public String getStreetBusiness() {
        return delegate.getStreet_business();
    }

    @Override
    public String getTelephoneCallback() {
        return delegate.getTelephone_callback();
    }

    @Override
    public String getCityHome() {
        return delegate.getCity_home();
    }

    @Override
    public String getCommercialRegister() {
        return delegate.getCommercial_register();
    }

    @Override
    public String getCountryHome() {
        return delegate.getCountry_home();
    }

    @Override
    public final String getCompany() {
        return delegate.getCompany();
    }

    @Override
    public final String getDepartment() {
        return delegate.getDepartment();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplay_name();
    }

    @Override
    public final String getEmail2() {
        return delegate.getEmail2();
    }

    @Override
    public final String getEmail3() {
        return delegate.getEmail3();
    }

    @Override
    public final String getEmployeeType() {
        return delegate.getEmployeeType();
    }

    @Override
    public String getFaxBusiness() {
        return delegate.getFax_business();
    }

    @Override
    public String getFaxHome() {
        return delegate.getFax_home();
    }

    @Override
    public String getFaxOther() {
        return delegate.getFax_other();
    }

    @Override
    public String getInstantMessenger1() {
        return delegate.getInstant_messenger1();
    }

    @Override
    public String getInstantMessenger2() {
        return delegate.getInstant_messenger2();
    }

    @Override
    public String getTelephoneIP() {
        return delegate.getTelephone_ip();
    }

    @Override
    public String getTelephoneISDN() {
        return delegate.getTelephone_isdn();
    }

    @Override
    public String getManagerName() {
        return delegate.getManager_name();
    }

    @Override
    public String getMaritalStatus() {
        return delegate.getMarital_status();
    }

    @Override
    public String getCellularTelephone1() {
        return delegate.getCellular_telephone1();
    }

    @Override
    public String getCellularTelephone2() {
        return delegate.getCellular_telephone2();
    }

    @Override
    public final String getInfo() {
        return delegate.getInfo();
    }

    @Override
    public final String getNickname() {
        return delegate.getNickname();
    }

    @Override
    public String getNumberOfChildren() {
        return delegate.getNumber_of_children();
    }

    @Override
    public final String getNote() {
        return delegate.getNote();
    }

    @Override
    public String getNumberOfEmployee() {
        return delegate.getNumber_of_employee();
    }

    @Override
    public String getTelephonePager() {
        return delegate.getTelephone_pager();
    }

    @Override
    public final String getTelephoneAssistant() {
        return delegate.getTelephone_assistant();
    }

    @Override
    public final String getTelephoneBusiness1() {
        return delegate.getTelephone_business1();
    }

    @Override
    public final String getTelephoneBusiness2() {
        return delegate.getTelephone_business2();
    }

    @Override
    public final String getTelephoneCar() {
        return delegate.getTelephone_car();
    }

    @Override
    public final String getTelephoneCompany() {
        return delegate.getTelephone_company();
    }

    @Override
    public final String getTelephoneHome1() {
        return delegate.getTelephone_home1();
    }

    @Override
    public final String getTelephoneHome2() {
        return delegate.getTelephone_home2();
    }

    @Override
    public final String getTelephoneOther() {
        return delegate.getTelephone_other();
    }

    @Override
    public final String getPosition() {
        return delegate.getPosition();
    }

    @Override
    public String getPostalCodeHome() {
        return delegate.getPostal_code_home();
    }

    @Override
    public final String getProfession() {
        return delegate.getProfession();
    }

    @Override
    public final String getTelephoneRadio() {
        return delegate.getTelephone_radio();
    }

    @Override
    public final String getRoomNumber() {
        return delegate.getRoom_number();
    }

    @Override
    public final String getSalesVolume() {
        return delegate.getSales_volume();
    }

    @Override
    public final String getCityOther() {
        return delegate.getCity_other();
    }

    @Override
    public final String getCountryOther() {
        return delegate.getCountry_other();
    }

    @Override
    public final String getMiddleName() {
        return delegate.getMiddle_name();
    }

    @Override
    public final String getPostalCodeOther() {
        return delegate.getPostal_code_other();
    }

    @Override
    public final String getStateOther() {
        return delegate.getState_other();
    }

    @Override
    public final String getStreetOther() {
        return delegate.getStreet_other();
    }

    @Override
    public final String getSpouseName() {
        return delegate.getSpouse_name();
    }

    @Override
    public final String getStateHome() {
        return delegate.getState_home();
    }

    @Override
    public final String getStreetHome() {
        return delegate.getStreet_home();
    }

    @Override
    public final String getTaxID() {
        return delegate.getTax_id();
    }

    @Override
    public final String getTelephoneTelex() {
        return delegate.getTelephone_telex();
    }

    @Override
    public final String getTitle() {
        return delegate.getTitle();
    }

    @Override
    public String getTelephoneTTYTTD() {
        return delegate.getTelephone_ttytdd();
    }

    @Override
    public String getURL() {
        return delegate.getUrl();
    }

    @Override
    public final String getCityBusiness() {
        return delegate.getCity_business();
    }

    @Override
    public final String getCountryBusiness() {
        return delegate.getCountry_business();
    }

    @Override
    public final String getAssistantName() {
        return delegate.getAssistant_name();
    }

    @Override
    public final String getTelephonePrimary() {
        return delegate.getTelephone_primary();
    }

    @Override
    public final String getCategories() {
        return delegate.getCategories();
    }

    @Override
    public final String getEmail1() {
        return delegate.getEmail1();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj instanceof ContactAdapter) {
            return delegate.equals(((ContactAdapter) obj).delegate);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
