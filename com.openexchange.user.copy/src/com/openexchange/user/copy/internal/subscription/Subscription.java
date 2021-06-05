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

package com.openexchange.user.copy.internal.subscription;

import java.util.List;
import com.openexchange.user.copy.internal.genconf.ConfAttribute;

/**
 * {@link Subscription}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 */
public class Subscription {

    private int id;

    private int configId;

    private String sourceId;

    private String folderId;

    private long lastUpdate;

    private int enabled;

    private long created;

    private long lastModified;

    private List<ConfAttribute> boolAttributes;

    private List<ConfAttribute> stringAttributes;


    public Subscription() {
        super();
    }

    public int getId() {
        return id;
    }

    public void setId(final int id) {
        this.id = id;
    }

    public int getConfigId() {
        return configId;
    }

    public void setConfigId(final int configId) {
        this.configId = configId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(final String sourceId) {
        this.sourceId = sourceId;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(final String folderId) {
        this.folderId = folderId;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(final long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getEnabled() {
        return enabled;
    }

    public void setEnabled(final int enabled) {
        this.enabled = enabled;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(final long created) {
        this.created = created;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(final long lastModified) {
        this.lastModified = lastModified;
    }

    public List<ConfAttribute> getBoolAttributes() {
        return boolAttributes;
    }

    public void setBoolAttributes(final List<ConfAttribute> boolAttributes) {
        this.boolAttributes = boolAttributes;
    }

    public List<ConfAttribute> getStringAttributes() {
        return stringAttributes;
    }

    public void setStringAttributes(final List<ConfAttribute> stringAttributes) {
        this.stringAttributes = stringAttributes;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (created ^ (created >>> 32));
        result = prime * result + enabled;
        result = prime * result + (int) (lastModified ^ (lastModified >>> 32));
        result = prime * result + (int) (lastUpdate ^ (lastUpdate >>> 32));
        result = prime * result + ((sourceId == null) ? 0 : sourceId.hashCode());
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
        final Subscription other = (Subscription) obj;
        if (created != other.created) {
            return false;
        }
        if (enabled != other.enabled) {
            return false;
        }
        if (lastModified != other.lastModified) {
            return false;
        }
        if (lastUpdate != other.lastUpdate) {
            return false;
        }
        if (sourceId == null) {
            if (other.sourceId != null) {
                return false;
            }
        } else if (!sourceId.equals(other.sourceId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Subscription [id=" + id + ", configId=" + configId + ", sourceId=" + sourceId + ", folderId=" + folderId + ", lastUpdate=" + lastUpdate + ", enabled=" + enabled + ", created=" + created + ", lastModified=" + lastModified + "]";
    }

}
