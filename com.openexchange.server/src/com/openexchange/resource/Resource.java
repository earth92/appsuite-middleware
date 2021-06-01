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

package com.openexchange.resource;

import java.util.Date;

/**
 * {@link Resource} - This is the data container class for resources.
 *
 * @author <a href="mailto:marcus@open-xchange.org">Marcus Klein</a>
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class Resource {

    /**
     * The identifier of the resource.
     */
    private int identifier = -1;

    private boolean identifierSet;

    /**
     * This is the name of this resource that can have character restrictions.
     */
    private String simpleName;
    private boolean simpleNameSet;

    /**
     * The display name of the resource. Currently the identifier is also used as display name so this attribute will be filled with the
     * identifier. But the identifier is limited in the characters that can be used.
     */
    private String displayName;
    private boolean displayNameSet;

    /**
     * Mail address of this resource.
     */
    private String mail;
    private boolean mailSet;

    /**
     * If a resource is not available, it can't be booked.
     */
    private boolean available;
    private boolean availableSet;

    /**
     * Description of this resource.
     */
    private String description;
    private boolean descriptionSet;

    /**
     * Timestamp of the last modification of this resource.
     */
    private Date lastModified;
    private boolean lastModifiedSet;

    /**
     * Default constructor.
     */
    public Resource() {
        super();
    }

    /**
     * Fills this resource's non-set fields with the one from specified source resource
     *
     * @param src The source resource
     */
    public void fill(final Resource src) {
        if (!availableSet) {
            setAvailable(src.isAvailable());
        }
        if (!descriptionSet) {
            setDescription(src.getDescription());
        }
        if (!displayNameSet) {
            setDisplayName(src.getDisplayName());
        }
        if (!identifierSet) {
            setIdentifier(src.getIdentifier());
        }
        if (!mailSet) {
            setMail(src.getMail());
        }
        if (!simpleNameSet) {
            setSimpleName(src.getSimpleName());
        }
        if (!lastModifiedSet) {
            setLastModified(src.getLastModified());
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (available ? 1231 : 1237);
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
        result = prime * result + identifier;
        result = prime * result + ((lastModified == null) ? 0 : lastModified.hashCode());
        result = prime * result + ((mail == null) ? 0 : mail.hashCode());
        result = prime * result + ((simpleName == null) ? 0 : simpleName.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Resource other = (Resource) obj;
        if (available != other.available) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (displayName == null) {
            if (other.displayName != null) {
                return false;
            }
        } else if (!displayName.equals(other.displayName)) {
            return false;
        }
        if (identifier != other.identifier) {
            return false;
        }
        if (lastModified == null) {
            if (other.lastModified != null) {
                return false;
            }
        } else if (!lastModified.equals(other.lastModified)) {
            return false;
        }
        if (mail == null) {
            if (other.mail != null) {
                return false;
            }
        } else if (!mail.equals(other.mail)) {
            return false;
        }
        if (simpleName == null) {
            if (other.simpleName != null) {
                return false;
            }
        } else if (!simpleName.equals(other.simpleName)) {
            return false;
        }
        return true;
    }

    /**
     * Performs an equality check which ignores the last-modified timestamp
     *
     * @param other The other resource to compare with
     * @return <code>true</code> if this resource is equal to other resource; otherwise <code>false</code>
     */
    public boolean equalsWithoutLastModified(final Resource other) {
        if (available != other.available) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (displayName == null) {
            if (other.displayName != null) {
                return false;
            }
        } else if (!displayName.equals(other.displayName)) {
            return false;
        }
        if (identifier != other.identifier) {
            return false;
        }
        if (mail == null) {
            if (other.mail != null) {
                return false;
            }
        } else if (!mail.equals(other.mail)) {
            return false;
        }
        if (simpleName == null) {
            if (other.simpleName != null) {
                return false;
            }
        } else if (!simpleName.equals(other.simpleName)) {
            return false;
        }
        return true;
    }

    /**
     * Setter for identifier.
     *
     * @param identifier identifier.
     */
    public void setIdentifier(final int identifier) {
        this.identifier = identifier;
        identifierSet = true;
    }

    /**
     * Checks if identifier has been set
     *
     * @return <code>true</code> if identifier has been set; otherwise <code>false</code>
     */
    public boolean isIdentifierSet() {
        return identifierSet;
    }

    /**
     * Removes identifier
     */
    public void removeIdentifier() {
        identifier = -1;
        identifierSet = false;
    }

    /**
     * Getter for identifier.
     *
     * @return the identifier.
     */
    public int getIdentifier() {
        return identifier;
    }

    /**
     * Getter for displayName.
     *
     * @return the display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns if the resource is available. If a resource is available, it can be booked.
     *
     * @return <code>true</code> if the resource is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks if available has been set
     *
     * @return <code>true</code> if available has been set; otherwise <code>false</code>
     */
    public boolean isAvailableSet() {
        return availableSet;
    }

    /**
     * Removes available
     */
    public void removeAvailable() {
        available = false;
        availableSet = false;
    }

    /**
     * Setter for displayName.
     *
     * @param displayName Display name.
     */
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
        displayNameSet = true;
    }

    /**
     * Checks if display name has been set
     *
     * @return <code>true</code> if display name has been set; otherwise <code>false</code>
     */
    public boolean isDisplayNameSet() {
        return displayNameSet;
    }

    /**
     * Removes display name
     */
    public void removeDisplayName() {
        displayName = null;
        displayNameSet = false;
    }

    /**
     * Setter for available.
     *
     * @param available <code>true</code> if the resource is available.
     */
    public void setAvailable(final boolean available) {
        this.available = available;
        availableSet = true;
    }

    /**
     * Setter for description.
     *
     * @param description Description.
     */
    public void setDescription(final String description) {
        this.description = description;
        descriptionSet = true;
    }

    /**
     * Checks if description has been set
     *
     * @return <code>true</code> if description has been set; otherwise <code>false</code>
     */
    public boolean isDescriptionSet() {
        return descriptionSet;
    }

    /**
     * Removes description
     */
    public void removeDescription() {
        description = null;
        descriptionSet = false;
    }

    /**
     * Getter for description.
     *
     * @return Description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Getter for last-modified timestamp
     *
     * @return Returns the lastModified timestamp.
     */
    public Date getLastModified() {
        return lastModified;
    }

    /**
     * Checks if last-modified timestamp has been set
     *
     * @return <code>true</code> if last-modified timestamp has been set; otherwise <code>false</code>
     */
    public boolean isLastModifiedSet() {
        return lastModifiedSet;
    }

    /**
     * Removes last-modified timestamp
     */
    public void removeLastModified() {
        lastModified = null;
        lastModifiedSet = false;
    }

    /**
     * Setter for last-modified timestamp
     *
     * @param lastModified The lastModified to set.
     */
    public void setLastModified(final Date lastModified) {
        this.lastModified = lastModified;
        lastModifiedSet = true;
    }

    /**
     * Setter for last-modified timestamp
     *
     * @param lastModified The lastModified to set; the milliseconds since January 1, 1970, 00:00:00 GMT.
     */
    public void setLastModified(final long lastModified) {
        this.lastModified = new Date(lastModified);
        lastModifiedSet = true;
    }

    /**
     * @return the mail
     */
    public String getMail() {
        return mail;
    }

    /**
     * @param mail the mail to set
     */
    public void setMail(final String mail) {
        this.mail = mail;
        mailSet = true;
    }

    /**
     * Checks if mail has been set
     *
     * @return <code>true</code> if mail has been set; otherwise <code>false</code>
     */
    public boolean isMailSet() {
        return mailSet;
    }

    /**
     * Removes mail
     */
    public void removeMail() {
        mail = null;
        mailSet = false;
    }

    /**
     * @return the simpleName
     */
    public final String getSimpleName() {
        return simpleName;
    }

    /**
     * @param simpleName the simpleName to set
     */
    public final void setSimpleName(final String simpleName) {
        this.simpleName = simpleName;
        simpleNameSet = true;
    }

    /**
     * Checks if simple name has been set
     *
     * @return <code>true</code> if simple name has been set; otherwise <code>false</code>
     */
    public boolean isSimpleNameSet() {
        return simpleNameSet;
    }

    /**
     * Removes simple name
     */
    public void removeSimpleName() {
        simpleName = null;
        simpleNameSet = false;
    }

    @Override
    public String toString() {
        return new StringBuilder(64).append(super.toString()).append(" ID=").append(identifier).append(", identifier=\"").append(simpleName).append(
            "\", displayName=\"").append(displayName).append("\", mail=\"").append(mail).append("\", available=").append(available).append(
            ", description=\"").append(description).append("\", lastModified=").append(lastModified).toString();
    }
}
