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

package com.openexchange.admin.rmi.dataobjects;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import com.openexchange.admin.rmi.exceptions.EnforceableDataObjectException;
import com.openexchange.admin.rmi.exceptions.InvalidDataException;

/**
 * @author choeger
 *
 */
public abstract class EnforceableDataObject implements Serializable, Cloneable {

    private static final long serialVersionUID = 9068912974174606869L;

    private ArrayList<String> unset_members = null;

    /**
     * This method must be implemented and it must return a String array
     * containing all names of mandatory members of the corresponding class
     * required to CREATE data.
     *
     * @return String array containing names of mandatory members or null if
     *         unwanted
     */
    public abstract String[] getMandatoryMembersCreate();

    /**
     * This method must be implemented and it must return a String array
     * containing all names of mandatory members of the corresponding class
     * required to CHANGE data.
     *
     * @return String array containing names of mandatory members or null if
     *         unwanted
     */
    public abstract String[] getMandatoryMembersChange();

    /**
     * This method must be implemented and it must return a String array
     * containing all names of mandatory members of the corresponding class
     * required to DELETE data.
     *
     * @return String array containing names of mandatory members or null if
     *         unwanted
     */
    public abstract String[] getMandatoryMembersDelete();

    /**
     * This method must be implemented and it must return a String array
     * containing all names of mandatory members of the corresponding class
     * required to REGISTER data.
     *
     * @return String array containing names of mandatory members or null if
     *         unwanted
     */
    public abstract String[] getMandatoryMembersRegister();

    /**
     * Checks if the mandatory members for create are set for an object
     *
     * @return true if they are set; false otherwise
     * @throws EnforceableDataObjectException
     */
    public boolean mandatoryCreateMembersSet() throws EnforceableDataObjectException {
        return mandatoryMembersSet(getMandatoryMembersCreate());
    }

    /**
     * Checks if the mandatory members for change are set for an object
     *
     * @return true if they are set; false otherwise
     * @throws EnforceableDataObjectException
     */
    public boolean mandatoryChangeMembersSet() throws EnforceableDataObjectException {
        return mandatoryMembersSet(getMandatoryMembersChange());
    }

    /**
     * Checks if the mandatory members for delete are set for an object
     *
     * @return true if they are set; false otherwise
     * @throws EnforceableDataObjectException
     */
    public boolean mandatoryDeleteMembersSet() throws EnforceableDataObjectException {
        return mandatoryMembersSet(getMandatoryMembersDelete());
    }

    /**
     * Checks if the mandatory members for register are set for an object
     *
     * @return true if they are set; false otherwise
     * @throws EnforceableDataObjectException
     */
    public boolean mandatoryRegisterMembersSet() throws EnforceableDataObjectException {
        return mandatoryMembersSet(getMandatoryMembersRegister());
    }

    private boolean mandatoryMembersSet(final String[] members) throws EnforceableDataObjectException {
        this.unset_members.clear();

        if (members == null || members.length <= 0) {
            return true;
        }

        try {
            for (final String m : members) {
                Field f = this.getClass().getDeclaredField(m);
                f.setAccessible(true);
                Object val = f.get(this);
                if (val == null || (val instanceof String && ((String) val).equals(""))) {
                    this.unset_members.add(m);
                }
            }
            return this.unset_members.isEmpty();
        } catch (SecurityException e) {
            throw new EnforceableDataObjectException(e);
        } catch (NoSuchFieldException e) {
            throw new EnforceableDataObjectException("No such member: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new EnforceableDataObjectException(e);
        } catch (IllegalAccessException e) {
            throw new EnforceableDataObjectException(e);
        }
    }

    /**
     * Returns those fields which are failing during a mandatory members check. This method is intended to be used
     * after a call of {@link #mandatoryCreateMembersSet()}, {@link #mandatoryChangeMembersSet()},
     * {@link #mandatoryDeleteMembersSet()} or {@link #mandatoryRegisterMembersSet()} to determine the missing fields
     *
     * @return An {@link ArrayList<String>} containing the missing fields
     */
    public ArrayList<String> getUnsetMembers() {
        return this.unset_members;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        final EnforceableDataObject object = (EnforceableDataObject) super.clone();
        object.unset_members = new ArrayList<String>(this.unset_members);
        return object;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder ret = new StringBuilder(super.toString());
        ret.append("\n");
        ret.append(" Mandatory members:\n");
        ret.append("  Create: ");
        if (getMandatoryMembersCreate() != null && getMandatoryMembersCreate().length > 0) {
            for (final String m : getMandatoryMembersCreate()) {
                ret.append(m);
                ret.append(" ");
            }
            ret.append("\n");
        } else {
            ret.append(" NONE\n");
        }
        ret.append("  Change:");
        if (getMandatoryMembersChange() != null && getMandatoryMembersChange().length > 0) {
            for (final String m : getMandatoryMembersChange()) {
                ret.append(m);
                ret.append(" ");
            }
            ret.append("\n");
        } else {
            ret.append(" NONE\n");
        }
        ret.append("  Delete:");
        if (getMandatoryMembersDelete() != null && getMandatoryMembersDelete().length > 0) {
            for (final String m : getMandatoryMembersDelete()) {
                ret.append(m);
                ret.append(" ");
            }
            ret.append("\n");
        } else {
            ret.append(" NONE\n");
        }
        ret.append("  Register:");
        if (getMandatoryMembersRegister() != null && getMandatoryMembersRegister().length > 0) {
            for (final String m : getMandatoryMembersRegister()) {
                ret.append(m);
                ret.append(" ");
            }
            ret.append("\n");
        } else {
            ret.append(" NONE\n");
        }

        return ret.toString();
    }

    /**
     * This method is used to check that the mandatory fields specified for create aren't set to null through a
     * change
     *
     * @param enforcableobject
     * @throws InvalidDataException
     */
    public void testMandatoryCreateFieldsNull() throws InvalidDataException {
        final String[] mandatoryMembersCreate = this.getMandatoryMembersCreate();
        try {
            for (final String name : mandatoryMembersCreate) {
                StringBuilder sb = new StringBuilder("get");
                final String firstletter = name.substring(0, 1).toUpperCase();
                sb.append(firstletter);
                final String lasttext = name.substring(1);
                sb.append(lasttext);
                final Class<? extends EnforceableDataObject> class1 = this.getClass();
                final Method getter = class1.getMethod(sb.toString(), (Class[])null);
                sb = new StringBuilder("is");
                sb.append(firstletter);
                sb.append(lasttext);
                sb.append("set");
                final Method isset = this.getClass().getMethod(sb.toString(), (Class[])null);
                final Object getresult = getter.invoke(this, (Object[])null);
                final boolean issetresult = ((Boolean)isset.invoke(this, (Object[])null)).booleanValue();
                if (issetresult && null == getresult) {
                    throw new InvalidDataException("Field \"" + name + "\" is a mandatory field and can't be set to null.");
                }
            }
        } catch (SecurityException e) {
            throw new InvalidDataException(e);
        } catch (NoSuchMethodException e) {
            throw new InvalidDataException("No such method " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException(e);
        } catch (IllegalAccessException e) {
            throw new InvalidDataException(e);
        } catch (InvocationTargetException e) {
            throw new InvalidDataException(e);
        }
    }

    /**
     * The default constructor
     */
    public EnforceableDataObject() {
        this.unset_members = new ArrayList<String>();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((unset_members == null) ? 0 : unset_members.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof EnforceableDataObject)) {
            return false;
        }
        final EnforceableDataObject other = (EnforceableDataObject) obj;
        if (unset_members == null) {
            if (other.unset_members != null) {
                return false;
            }
        } else if (!unset_members.equals(other.unset_members)) {
            return false;
        }
        return true;
    }


}
