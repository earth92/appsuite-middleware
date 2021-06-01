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

package com.openexchange.twitter.internal;

import java.util.Date;
import twitter4j.User;

/**
 * {@link UserImpl} - The user implementation.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class UserImpl implements com.openexchange.twitter.User {

    private final User twitter4jUser;

    /**
     * Initializes a new {@link UserImpl}.
     *
     * @param twitter4jUser The twitter4j user
     */
    public UserImpl(final User twitter4jUser) {
        super();
        this.twitter4jUser = twitter4jUser;
    }

    @Override
    public Date getCreatedAt() {
        return twitter4jUser.getCreatedAt();
    }

    @Override
    public String getDescription() {
        return twitter4jUser.getDescription();
    }

    @Override
    public int getFavouritesCount() {
        return twitter4jUser.getFavouritesCount();
    }

    @Override
    public int getFollowersCount() {
        return twitter4jUser.getFollowersCount();
    }

    @Override
    public int getFriendsCount() {
        return twitter4jUser.getFriendsCount();
    }

    @Override
    public long getId() {
        return twitter4jUser.getId();
    }

    @Override
    public String getLocation() {
        return twitter4jUser.getLocation();
    }

    @Override
    public String getName() {
        return twitter4jUser.getName();
    }

    @Override
    public String getProfileBackgroundColor() {
        return twitter4jUser.getProfileBackgroundColor();
    }

    @Override
    public String getProfileBackgroundImageUrl() {
        return twitter4jUser.getProfileImageURL();
    }

    @Override
    public String getProfileImageURL() {
        return twitter4jUser.getProfileImageURL();
    }

    @Override
    public String getProfileLinkColor() {
        return twitter4jUser.getProfileLinkColor();
    }

    @Override
    public String getProfileSidebarBorderColor() {
        return twitter4jUser.getProfileSidebarBorderColor();
    }

    @Override
    public String getProfileSidebarFillColor() {
        return twitter4jUser.getProfileSidebarFillColor();
    }

    @Override
    public String getProfileTextColor() {
        return twitter4jUser.getProfileTextColor();
    }

    public int getRateLimitLimit() {
        return twitter4jUser.getRateLimitStatus().getLimit();
    }

    public int getRateLimitRemaining() {
        return twitter4jUser.getRateLimitStatus().getRemaining();
    }

    public long getRateLimitReset() {
        return twitter4jUser.getRateLimitStatus().getResetTimeInSeconds();
    }

    @Override
    public String getScreenName() {
        return twitter4jUser.getScreenName();
    }

    public Date getStatusCreatedAt() {
        return twitter4jUser.getStatus().getCreatedAt();
    }

    @Override
    public int getStatusesCount() {
        return twitter4jUser.getStatusesCount();
    }

    public long getStatusId() {
        return twitter4jUser.getStatus().getId();
    }

    public String getStatusInReplyToScreenName() {
        return twitter4jUser.getStatus().getInReplyToScreenName();
    }

    public long getStatusInReplyToStatusId() {
        return twitter4jUser.getStatus().getInReplyToStatusId();
    }

    public long getStatusInReplyToUserId() {
        return twitter4jUser.getStatus().getInReplyToUserId();
    }

    public String getStatusSource() {
        return twitter4jUser.getStatus().getSource();
    }

    public String getStatusText() {
        return twitter4jUser.getStatus().getText();
    }

    @Override
    public String getTimeZone() {
        return twitter4jUser.getTimeZone();
    }

    @Override
    public String getURL() {
        return twitter4jUser.getURL();
    }

    @Override
    public int getUtcOffset() {
        return twitter4jUser.getUtcOffset();
    }

    @Override
    public boolean isGeoEnabled() {
        return twitter4jUser.isGeoEnabled();
    }

    @Override
    public boolean isProtected() {
        return twitter4jUser.isProtected();
    }

    @Override
    public boolean isVerified() {
        return twitter4jUser.isVerified();
    }

    @Override
    public String toString() {
        return twitter4jUser.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((twitter4jUser == null) ? 0 : twitter4jUser.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UserImpl)) {
            return false;
        }
        final UserImpl other = (UserImpl) obj;
        if (twitter4jUser == null) {
            if (other.twitter4jUser != null) {
                return false;
            }
        } else if (!twitter4jUser.equals(other.twitter4jUser)) {
            return false;
        }
        return true;
    }

}
