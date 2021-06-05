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

package com.openexchange.admin.rmi.manager;

import java.util.Set;
import com.openexchange.admin.rmi.OXUserInterface;
import com.openexchange.admin.rmi.dataobjects.Context;
import com.openexchange.admin.rmi.dataobjects.Credentials;
import com.openexchange.admin.rmi.dataobjects.Filestore;
import com.openexchange.admin.rmi.dataobjects.User;
import com.openexchange.admin.rmi.dataobjects.UserModuleAccess;
import com.openexchange.admin.user.copy.rmi.OXUserCopyInterface;

/**
 * {@link UserManager}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @since v7.10.1
 */
public class UserManager extends AbstractManager {

    private static UserManager INSTANCE;

    /**
     * Gets the instance of the {@link UserManager}
     *
     * @param host
     * @param masterCredentials
     * @return
     */
    public static UserManager getInstance(String host, Credentials masterCredentials) {
        if (INSTANCE == null) {
            INSTANCE = new UserManager(host, masterCredentials);
        }
        return INSTANCE;
    }

    /**
     * Initialises a new {@link UserManager}.
     *
     * @param rmiEndPointURL
     * @param masterCredentials
     */
    private UserManager(String rmiEndPointURL, Credentials masterCredentials) {
        super(rmiEndPointURL, masterCredentials);
    }

    /**
     * Creates the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @return The newly created {@link User}
     * @throws Exception if an error is occurred
     */
    public User create(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.create(context, user, contextAdminCredentials);
    }

    /**
     * Creates the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @return The newly created {@link User}
     * @throws Exception if an error is occurred
     */
    public User create(Context context, User user, UserModuleAccess userModuleAccess, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.create(context, user, userModuleAccess, contextAdminCredentials);
    }

    /**
     * Creates the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @return The newly created {@link User}
     * @throws Exception if an error is occurred
     */
    public User create(Context context, User user, String combination, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.create(context, user, combination, contextAdminCredentials);
    }

    /**
     * Retrieves all data of the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param User The {@link User}
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @return The {@link User} with all its data loaded
     * @throws Exception if an error is occurred
     */
    public User getData(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.getData(context, user, contextAdminCredentials);
    }

    /**
     * Retrieves all data of the specified {@link User}s in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param User The {@link User}s
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @return The {@link User}s with all its data loaded
     * @throws Exception if an error is occurred
     */
    public User[] getData(Context context, User[] user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.getData(context, user, contextAdminCredentials);
    }

    /**
     * Retrieves an array with all found {@link User} in the specified {@link Context}
     * that match the specified search pattern.
     *
     * @param context The {@link Context}
     * @param searchPattern The search pattern
     * @param contextAdminCredentials The context admin {@link Credentials}
     * @return An array with all found {@link User}s
     * @throws Exception if an error is occurred
     */
    public User[] search(Context context, String searchPattern, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.list(context, searchPattern, contextAdminCredentials, null, null);
    }

    /**
     * Returns an array with all {@link User}s in the specified {@link Context}
     *
     * @param context The context
     * @param contextAdminCredentials The context admin credentials
     * @return An array with all {@link User}s
     * @throws Exception if an error is occurred
     */
    public User[] listAll(Context context, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.listAll(context, contextAdminCredentials);
    }

    /**
     * Changes the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User} to change
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @throws Exception if an error is occurred
     */
    public void change(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        userInterface.change(context, user, contextAdminCredentials);
    }

    /**
     * Changes the capabilities of the specified {@link User} in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User} to change
     * @param capsToAdd The capabilities to add
     * @param capsToRemove The capabilities to remove
     * @param capsToDrop The capabilities to drop
     * @param contextAdminCredentials The context admin {@link Credentials}
     *
     * @throws Exception if an error is occurred
     */
    public void change(Context context, User user, Set<String> capsToAdd, Set<String> capsToRemove, Set<String> capsToDrop, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        userInterface.changeCapabilities(context, user, capsToAdd, capsToRemove, capsToDrop, contextAdminCredentials);
    }

    /**
     * Checks whether the specified {@link User} exists in the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param contextAdminCredentials The context admin {@link Credentials}
     * @return <code>true</code> if the user exists; <code>false</code> otherwise
     *
     * @throws Exception if an error is occurred
     */
    public boolean exists(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.exists(context, user, contextAdminCredentials);
    }

    /**
     * Moves a user's files from a context to his own storage.
     *
     * This operation is quota-aware and thus transfers current quota usage from context to user.
     *
     * @param context The context
     * @param user The user
     * @param filestore The {@link Filestore}
     * @param maxQuota The max quota
     * @param contextAdminCredentials The context admin {@link Credentials}
     * @throws Exception if an error is occurred
     */
    public void moveFromContextToUserFilestore(Context context, User user, Filestore filestore, long maxQuota, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        userInterface.moveFromContextToUserFilestore(context, user, filestore, maxQuota, contextAdminCredentials);
    }

    /**
     * Retrieve all user objects with given filestore for a given context.
     * If <code>filestoreId</code> is <code>null</code> all user objects with
     * a dedicated filestore for a given context are retrieved instead.
     *
     * @param context The context
     * @param filestoreId The {@link Filestore} identifier
     * @param contextAdminCredentials The context admin credentials
     * @return An array with {@link User}s
     * @throws Exception if an error is occurred
     */
    public User[] listUsersWithOwnFilestore(Context context, Integer filestoreId, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        return userInterface.listUsersWithOwnFilestore(context, contextAdminCredentials, filestoreId, null, null);
    }

    /**
     * Retrieves the {@link UserModuleAccess} for the specified {@link User}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param contextAdminCredentials The context admin credentials
     * @return The {@link UserModuleAccess}
     * @throws Exception if an error is occurred
     */
    public UserModuleAccess getModuleAccess(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInteface = getUserInterface();
        return userInteface.getModuleAccess(context, user, contextAdminCredentials);
    }

    /**
     * Changes the {@link UserModuleAccess} for the specified {@link User} in the specified
     * {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User}
     * @param access The {@link UserModuleAccess}
     * @param contextAdminCredentials The context admin credentials
     * @throws Exception if an error is occurred
     */
    public void changeModuleAccess(Context context, User user, UserModuleAccess access, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInteface = getUserInterface();
        userInteface.changeModuleAccess(context, user, access, contextAdminCredentials);
    }

    /**
     * Copies/Moves the specified {@link User} from the <code>source</code> to the
     * <code>destination</code> {@link Context}
     *
     * @param user The {@link User} to move/copy
     * @param source The source {@link Context}
     * @param destination The destination {@link Context}
     * @return the resulting {@link User} object with the new identifier of the user in the context
     * @throws Exception if an error is occurred
     */
    public User copy(User user, Context source, Context destination) throws Exception {
        OXUserCopyInterface userCopyInterface = getRemoteInterface(OXUserCopyInterface.RMI_NAME, OXUserCopyInterface.class);
        return userCopyInterface.copyUser(user, source, destination, getMasterCredentials());
    }

    /**
     * Deletes the specified {@link User} from the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User} to delete
     * @param contextAdminCredentials The context's admin {@link Credentials}
     *
     * @throws Exception if an error is occurred
     */
    public void delete(Context context, User user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        userInterface.delete(context, user, null, contextAdminCredentials);
    }

    /**
     * Deletes the specified {@link User} from the specified {@link Context}
     *
     * @param context The {@link Context}
     * @param user The {@link User} to delete
     * @param contextAdminCredentials The context's admin {@link Credentials}
     *
     * @throws Exception if an error is occurred
     */
    public void delete(Context context, User[] user, Credentials contextAdminCredentials) throws Exception {
        OXUserInterface userInterface = getUserInterface();
        userInterface.delete(context, user, null, contextAdminCredentials);
    }

    @Override
    void clean(Object object) {
        // Nothing to do, the user will be implicitly deleted when the context is deleted.
    }

    /**
     * Retrieves the remote {@link OXUserInterface}
     *
     * @return the remote {@link OXUserInterface}
     * @throws Exception if the remote interface cannot be retrieved
     */
    private OXUserInterface getUserInterface() throws Exception {
        return getRemoteInterface(OXUserInterface.RMI_NAME, OXUserInterface.class);
    }
}
