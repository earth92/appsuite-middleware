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

package com.openexchange.session.oauth;

/**
 * {@link RefreshResult}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.10.3
 */
public class RefreshResult {

    public static enum SuccessReason {
        /**
         * Access token is not expired yet
         */
        NON_EXPIRED,
        /**
         * Access token was successfully refreshed
         */
        REFRESHED,
        /**
         * Access token was refreshed by another thread in the meantime
         */
        CONCURRENT_REFRESH;
    }

    public static enum FailReason {
        /**
         * Session contains an invalid refresh token
         */
        INVALID_REFRESH_TOKEN,
        /**
         * Lock timeout was exceeded
         */
        LOCK_TIMEOUT,
        /**
         * A temporary error occurred, retry later
         */
        TEMPORARY_ERROR,
        /**
         * A permanent error occurred, retry will not help to resolve this
         */
        PERMANENT_ERROR;
    }

    public static RefreshResult success(SuccessReason reason) {
        RefreshResult result = new RefreshResult();
        result.successReason = reason;
        return result;
    }

    public static RefreshResult fail(FailReason reason, String description) {
        return fail(reason, description, null);
    }

    public static RefreshResult fail(FailReason reason, String description, Throwable t) {
        RefreshResult result = new RefreshResult();
        result.failReason = reason;
        if (description == null) {
            result.errorDesc = "Unknown";
        } else {
            result.errorDesc = description;
        }
        result.exception = t;
        return result;
    }

    private SuccessReason successReason;
    private FailReason failReason;
    private String errorDesc;
    private Throwable exception;

    private RefreshResult() {
        super();
    }

    public boolean isSuccess() {
        return successReason != null;
    }

    public boolean isFail() {
        return failReason != null;
    }

    public SuccessReason getSuccessReason() {
        return successReason;
    }

    public FailReason getFailReason() {
        return failReason;
    }

    public String getErrorDesc() {
        return errorDesc;
    }

    public boolean hasException() {
        return exception != null;
    }

    public Throwable getException() {
        return exception;
    }

}
