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

package com.openexchange.pop3.storage;

import java.util.Map;
import com.openexchange.exception.OXException;

/**
 * {@link POP3StorageUIDLMap} - Maps POP3 UIDL to a fullname-UID-pair.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public interface POP3StorageUIDLMap {

    /**
     * Gets the fullname-UID-pairs to specified POP3 UIDLs.
     *
     * @param uidls The POP3 UIDLs
     * @return The fullname-UID-pairs to specified POP3 UIDLs. If no mapping could be found the corresponding entry is <code>null</code>
     * @throws OXException If mapping retrieval fails
     */
    public FullnameUIDPair[] getFullnameUIDPairs(String[] uidls) throws OXException;

    /**
     * Gets the fullname-UID-pair to specified POP3 UIDL.
     *
     * @param uidls The POP3 UIDL
     * @return The fullname-UID-pair to specified POP3 UIDL or <code>null</code> if no such mapping exists
     * @throws OXException If mapping retrieval fails
     */
    public FullnameUIDPair getFullnameUIDPair(String uidl) throws OXException;

    /**
     * Gets the POP3 UIDLs to specified fullname-UID-pairs.
     *
     * @param fullnameUIDPairs The fullname-UID-pairs
     * @return The POP3 UIDLs to specified fullname-UID-pairs
     * @throws OXException If mapping retrieval fails
     */
    public String[] getUIDLs(FullnameUIDPair[] fullnameUIDPairs) throws OXException;

    /**
     * Gets the POP3 UIDL to specified fullname-UID-pair.
     *
     * @param fullnameUIDPairs The fullname-UID-pair
     * @return The POP3 UIDL to specified fullname-UID-pair or <code>null</code> if no such mapping exists
     * @throws OXException If mapping retrieval fails
     */
    public String getUIDL(FullnameUIDPair fullnameUIDPair) throws OXException;

    /**
     * Adds specified mappings to this map.
     *
     * @param uidls The POP3 UIDLs
     * @param fullnameUIDPairs The fullname-UID-pairs. If no mapping could be found the corresponding entry is <code>null</code>
     * @throws OXException If adding mappings fails
     */
    public void addMappings(String[] uidls, FullnameUIDPair[] fullnameUIDPairs) throws OXException;

    /**
     * Gets all mappings known by this UIDL map.
     *
     * @return All mappings known by this UIDL map
     * @throws OXException If mapping retrieval fails
     */
    public Map<String, FullnameUIDPair> getAllUIDLs() throws OXException;

    /**
     * Deletes the mappings for specified UIDLs.
     *
     * @param uidls The UIDLs to clean from this map
     * @throws OXException If mapping deletion fails
     */
    public void deleteUIDLMappings(String[] uidls) throws OXException;

    /**
     * Deletes the mappings for specified fullname-UID-pairs.
     *
     * @param fullnameUIDPairs The fullname-UID-pairs to clean from this map
     * @throws OXException If mapping deletion fails
     */
    public void deleteFullnameUIDPairMappings(FullnameUIDPair[] fullnameUIDPairs) throws OXException;

}
