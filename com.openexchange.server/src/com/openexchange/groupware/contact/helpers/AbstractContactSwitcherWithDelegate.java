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

package com.openexchange.groupware.contact.helpers;

import com.openexchange.exception.OXException;
import com.openexchange.groupware.container.Contact;

public class AbstractContactSwitcherWithDelegate implements ContactSwitcher {

	protected ContactSwitcher delegate;

//------//
	public ContactSwitcher getDelegate() {
		return delegate;
	}

	public void setDelegate(final ContactSwitcher delegate) {
		this.delegate = delegate;
	}

//------//
	@Override
    public Object creationdate(final Object... objects) throws OXException {
		return delegate.creationdate( objects );
	}

	@Override
    public Object anniversary(final Object... objects) throws OXException {
		return delegate.anniversary( objects) ;
	}

	@Override
    public Object birthday(final Object... objects) throws OXException {
		return delegate.birthday(objects );
	}

	@Override
    public Object imagelastmodified(final Object... objects) throws OXException {
		return delegate.imagelastmodified( objects );
	}

	@Override
    public Object lastmodified(final Object... objects) throws OXException {
		return delegate.lastmodified( objects );
	}

	@Override
    public Object assistantname(final Object... objects) throws OXException {
		return delegate.assistantname(objects);
	}

	@Override
    public Object branches(final Object... objects) throws OXException {
		return delegate.branches(objects);
	}

	@Override
    public Object businesscategory(final Object... objects) throws OXException {
		return delegate.businesscategory(objects);
	}

	@Override
    public Object categories(final Object... objects) throws OXException {
		return delegate.categories(objects);
	}

	@Override
    public Object cellulartelephone1(final Object... objects) throws OXException {
		return delegate.cellulartelephone1(objects);
	}

	@Override
    public Object cellulartelephone2(final Object... objects) throws OXException {
		return delegate.cellulartelephone2(objects);
	}

	@Override
    public Object citybusiness(final Object... objects) throws OXException {
		return delegate.citybusiness(objects);
	}

	@Override
    public Object cityhome(final Object... objects) throws OXException {
		return delegate.cityhome(objects);
	}

	@Override
    public Object cityother(final Object... objects) throws OXException {
		return delegate.cityother(objects);
	}

	@Override
    public Object commercialregister(final Object... objects) throws OXException {
		return delegate.commercialregister(objects);
	}

	@Override
    public Object company(final Object... objects) throws OXException {
		return delegate.company(objects);
	}

	@Override
    public Object contextid(final Object... objects) throws OXException {
		return delegate.contextid(objects);
	}

	@Override
    public Object countrybusiness(final Object... objects) throws OXException {
		return delegate.countrybusiness(objects);
	}

	@Override
    public Object countryhome(final Object... objects) throws OXException {
		return delegate.countryhome(objects);
	}

	@Override
    public Object countryother(final Object... objects) throws OXException {
		return delegate.countryother(objects);
	}

	@Override
    public Object createdby(final Object... objects) throws OXException {
		return delegate.createdby(objects);
	}

	@Override
    public Object defaultaddress(final Object... objects) throws OXException {
		return delegate.defaultaddress(objects);
	}

	@Override
    public Object department(final Object... objects) throws OXException {
		return delegate.department(objects);
	}

	@Override
    public Object displayname(final Object... objects) throws OXException {
		return delegate.displayname(objects);
	}

	@Override
    public Object distributionlist(final Object... objects) throws OXException {
		return delegate.distributionlist(objects);
	}

	@Override
    public Object email1(final Object... objects) throws OXException {
		return delegate.email1(objects);
	}

	@Override
    public Object email2(final Object... objects) throws OXException {
		return delegate.email2(objects);
	}

	@Override
    public Object email3(final Object... objects) throws OXException {
		return delegate.email3(objects);
	}

	@Override
    public Object employeetype(final Object... objects) throws OXException {
		return delegate.employeetype(objects);
	}

	@Override
	public boolean equals(final Object obj) {
		return delegate.equals(obj);
	}

	@Override
    public Object faxbusiness(final Object... objects) throws OXException {
		return delegate.faxbusiness(objects);
	}

	@Override
    public Object faxhome(final Object... objects) throws OXException {
		return delegate.faxhome(objects);
	}

	@Override
    public Object faxother(final Object... objects) throws OXException {
		return delegate.faxother(objects);
	}

	@Override
    public Object fileas(final Object... objects) throws OXException {
		return delegate.fileas(objects);
	}

	@Override
    public Object givenname(final Object... objects) throws OXException {
		return delegate.givenname(objects);
	}

	@Override
	public int hashCode() {
		return delegate.hashCode();
	}

	@Override
    public Object info(final Object... objects) throws OXException {
		return delegate.info(objects);
	}

	@Override
    public Object instantmessenger1(final Object... objects) throws OXException {
		return delegate.instantmessenger1(objects);
	}

	@Override
    public Object instantmessenger2(final Object... objects) throws OXException {
		return delegate.instantmessenger2(objects);
	}

	@Override
    public Object internaluserid(final Object... objects) throws OXException {
		return delegate.internaluserid(objects);
	}

	@Override
    public Object label(final Object... objects) throws OXException {
		return delegate.label(objects);
	}

	@Override
    public Object managername(final Object... objects) throws OXException {
		return delegate.managername(objects);
	}

	@Override
    public Object maritalstatus(final Object... objects) throws OXException {
		return delegate.maritalstatus(objects);
	}

	@Override
    public Object middlename(final Object... objects) throws OXException {
		return delegate.middlename(objects);
	}

	@Override
    public Object modifiedby(final Object... objects) throws OXException {
		return delegate.modifiedby(objects);
	}

	@Override
    public Object nickname(final Object... objects) throws OXException {
		return delegate.nickname(objects);
	}

	@Override
    public Object note(final Object... objects) throws OXException {
		return delegate.note(objects);
	}

	@Override
    public Object numberofattachments(final Object... objects) throws OXException {
		return delegate.numberofattachments(objects);
	}

	@Override
    public Object numberofchildren(final Object... objects) throws OXException {
		return delegate.numberofchildren(objects);
	}

	@Override
    public Object numberofdistributionlists(final Object... objects) throws OXException {
		return delegate.numberofdistributionlists(objects);
	}

	@Override
    public Object numberofemployee(final Object... objects) throws OXException {
		return delegate.numberofemployee(objects);
	}

	@Override
    public Object objectid(final Object... objects) throws OXException {
		return delegate.objectid(objects);
	}

	@Override
    public Object parentfolderid(final Object... objects) throws OXException {
		return delegate.parentfolderid(objects);
	}

	@Override
    public Object position(final Object... objects) throws OXException {
		return delegate.position(objects);
	}

	@Override
    public Object postalcodebusiness(final Object... objects) throws OXException {
		return delegate.postalcodebusiness(objects);
	}

	@Override
    public Object postalcodehome(final Object... objects) throws OXException {
		return delegate.postalcodehome(objects);
	}

	@Override
    public Object postalcodeother(final Object... objects) throws OXException {
		return delegate.postalcodeother(objects);
	}

	@Override
    public Object privateflag(final Object... objects) throws OXException {
		return delegate.privateflag(objects);
	}

	@Override
    public Object profession(final Object... objects) throws OXException {
		return delegate.profession(objects);
	}

	@Override
    public Object roomnumber(final Object... objects) throws OXException {
		return delegate.roomnumber(objects);
	}

	@Override
    public Object salesvolume(final Object... objects) throws OXException {
		return delegate.salesvolume(objects);
	}

	@Override
    public Object spousename(final Object... objects) throws OXException {
		return delegate.spousename(objects);
	}

	@Override
    public Object statebusiness(final Object... objects) throws OXException {
		return delegate.statebusiness(objects);
	}

	@Override
    public Object statehome(final Object... objects) throws OXException {
		return delegate.statehome(objects);
	}

	@Override
    public Object stateother(final Object... objects) throws OXException {
		return delegate.stateother(objects);
	}

	@Override
    public Object streetbusiness(final Object... objects) throws OXException {
		return delegate.streetbusiness(objects);
	}

	@Override
    public Object streethome(final Object... objects) throws OXException {
		return delegate.streethome(objects);
	}

	@Override
    public Object streetother(final Object... objects) throws OXException {
		return delegate.streetother(objects);
	}

	@Override
    public Object suffix(final Object... objects) throws OXException {
		return delegate.suffix(objects);
	}

	@Override
    public Object surname(final Object... objects) throws OXException {
		return delegate.surname(objects);
	}

	@Override
    public Object taxid(final Object... objects) throws OXException {
		return delegate.taxid(objects);
	}

	@Override
    public Object telephoneassistant(final Object... objects) throws OXException {
		return delegate.telephoneassistant(objects);
	}

	@Override
    public Object telephonebusiness1(final Object... objects) throws OXException {
		return delegate.telephonebusiness1(objects);
	}

	@Override
    public Object telephonebusiness2(final Object... objects) throws OXException {
		return delegate.telephonebusiness2(objects);
	}

	@Override
    public Object telephonecallback(final Object... objects) throws OXException {
		return delegate.telephonecallback(objects);
	}

	@Override
    public Object telephonecar(final Object... objects) throws OXException {
		return delegate.telephonecar(objects);
	}

	@Override
    public Object telephonecompany(final Object... objects) throws OXException {
		return delegate.telephonecompany(objects);
	}

	@Override
    public Object telephonehome1(final Object... objects) throws OXException {
		return delegate.telephonehome1(objects);
	}

	@Override
    public Object telephonehome2(final Object... objects) throws OXException {
		return delegate.telephonehome2(objects);
	}

	@Override
    public Object telephoneip(final Object... objects) throws OXException {
		return delegate.telephoneip(objects);
	}

	@Override
    public Object telephoneisdn(final Object... objects) throws OXException {
		return delegate.telephoneisdn(objects);
	}

	@Override
    public Object telephoneother(final Object... objects) throws OXException {
		return delegate.telephoneother(objects);
	}

	@Override
    public Object telephonepager(final Object... objects) throws OXException {
		return delegate.telephonepager(objects);
	}

	@Override
    public Object telephoneprimary(final Object... objects) throws OXException {
		return delegate.telephoneprimary(objects);
	}

	@Override
    public Object telephoneradio(final Object... objects) throws OXException {
		return delegate.telephoneradio(objects);
	}

	@Override
    public Object telephonetelex(final Object... objects) throws OXException {
		return delegate.telephonetelex(objects);
	}

	@Override
    public Object telephonettyttd(final Object... objects) throws OXException {
		return delegate.telephonettyttd(objects);
	}

	@Override
    public Object title(final Object... objects) throws OXException {
		return delegate.title(objects);
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

	@Override
    public Object url(final Object... objects) throws OXException {
		return delegate.url(objects);
	}

	@Override
    public Object userfield01(final Object... objects) throws OXException {
		return delegate.userfield01(objects);
	}

	@Override
    public Object userfield02(final Object... objects) throws OXException {
		return delegate.userfield02(objects);
	}

	@Override
    public Object userfield03(final Object... objects) throws OXException {
		return delegate.userfield03(objects);
	}

	@Override
    public Object userfield04(final Object... objects) throws OXException {
		return delegate.userfield04(objects);
	}

	@Override
    public Object userfield05(final Object... objects) throws OXException {
		return delegate.userfield05(objects);
	}

	@Override
    public Object userfield06(final Object... objects) throws OXException {
		return delegate.userfield06(objects);
	}

	@Override
    public Object userfield07(final Object... objects) throws OXException {
		return delegate.userfield07(objects);
	}

	@Override
    public Object userfield08(final Object... objects) throws OXException {
		return delegate.userfield08(objects);
	}

	@Override
    public Object userfield09(final Object... objects) throws OXException {
		return delegate.userfield09(objects);
	}

	@Override
    public Object userfield10(final Object... objects) throws OXException {
		return delegate.userfield10(objects);
	}

	@Override
    public Object userfield11(final Object... objects) throws OXException {
		return delegate.userfield11(objects);
	}

	@Override
    public Object userfield12(final Object... objects) throws OXException {
		return delegate.userfield12(objects);
	}

	@Override
    public Object userfield13(final Object... objects) throws OXException {
		return delegate.userfield13(objects);
	}

	@Override
    public Object userfield14(final Object... objects) throws OXException {
		return delegate.userfield14(objects);
	}

	@Override
    public Object userfield15(final Object... objects) throws OXException {
		return delegate.userfield15(objects);
	}

	@Override
    public Object userfield16(final Object... objects) throws OXException {
		return delegate.userfield16(objects);
	}

	@Override
    public Object userfield17(final Object... objects) throws OXException {
		return delegate.userfield17(objects);
	}

	@Override
    public Object userfield18(final Object... objects) throws OXException {
		return delegate.userfield18(objects);
	}

	@Override
    public Object userfield19(final Object... objects) throws OXException {
		return delegate.userfield19(objects);
	}

	@Override
    public Object userfield20(final Object... objects) throws OXException {
		return delegate.userfield20(objects);
	}

    @Override
    public Object numberofimages(Object... objects) throws OXException {
        return delegate.numberofimages(objects);
    }

    @Override
    public Object lastmodifiedofnewestattachment(Object... objects) throws OXException {
        return delegate.lastmodifiedofnewestattachment(objects);
    }

    @Override
    public Object usecount(Object... objects) throws OXException {
        return delegate.usecount(objects);
    }

    @Override
    public Object markasdistributionlist(Object[] objects) throws OXException {
        return delegate.markasdistributionlist(objects);
    }

    @Override
    public Object yomifirstname(Object[] objects) throws OXException {
        return delegate.yomifirstname(objects);
    }

    @Override
    public Object yomilastname(Object[] objects) throws OXException {
        return delegate.yomilastname(objects);
    }

    @Override
    public Object yomicompanyname(Object[] objects) throws OXException {
        return delegate.yomicompanyname(objects);
    }

    @Override
    public Object image1contenttype(Object[] objects) throws OXException {
        return delegate.image1contenttype(objects);
    }

    @Override
    public Object homeaddress(Object[] objects) throws OXException {
        return delegate.homeaddress(objects);
    }

    @Override
    public Object businessaddress(Object[] objects) throws OXException {
        return delegate.businessaddress(objects);
    }

    @Override
    public Object otheraddress(Object[] objects) throws OXException {
        return delegate.otheraddress(objects);
    }

    @Override
    public Object uid(Object[] objects) throws OXException {
        return delegate.uid(objects);
    }

    @Override
    public Object image1(Object[] objects) throws OXException {
        return delegate.image1(objects);
    }

    @Override
    public boolean _unknownfield(final Contact contact, final String fieldname, final Object value, final Object... additionalObjects) throws OXException {
        return delegate._unknownfield(contact, fieldname, value, additionalObjects);
    }
}
