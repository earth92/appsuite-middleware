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

package com.openexchange.http.grizzly;

import java.io.File;
import java.util.TimeZone;
import com.openexchange.java.Strings;

/**
 * {@link GrizzlyAccessLogConfig} - The configuration for Grizzly access log.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @since v7.10.0
 */
public class GrizzlyAccessLogConfig {

    /** The constant configuration instance for disabled access log */
    public static final GrizzlyAccessLogConfig NOT_ENABLED_CONFIG = new GrizzlyAccessLogConfig() {

        @Override
        public boolean isEnabled() {
            return false;
        }
    };

    /** The log format to use */
    public static enum Format {
        /** A format compatible with Apache's <em>common</em> format. */
        COMMON("common"),
        /** A format compatible with Apache's <em>combined</em> format. */
        COMBINED("combined"),
        /** A format compatible with Apache's <em>common with virtual-hosts</em> format. */
        VHOST_COMMON("vhost_common"),
        /** A format compatible with Apache's <em>combined with virtual-hosts</em> format. */
        VHOST_COMBINED("vhost_combined"),
        /** A format compatible with Apache's <em>referer</em> format. */
        REFERER("referer"),
        /** A format compatible with Apache's <em>user-agent</em> format. */
        AGENT("agent"),
        ;

        private final String id;

        private Format(String id) {
            this.id = id;
        }

        /**
         * Gets the identifier
         *
         * @return The identifier
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the log format for specified identifier
         *
         * @param format The format identifier to look-up
         * @return The associated format or <code>null</code>
         */
        public static Format formatFor(String format) {
            if (null == format) {
                return null;
            }

            String lc = Strings.asciiLowerCase(format);
            for (Format f : Format.values()) {
                if (f.id.equals(lc)) {
                    return f;
                }
            }
            return null;
        }

    }

    /** The rotate behavior to use */
    public static enum RotatePolicy {
        /**
         * No rotation at all.
         */
        NONE("none"),
        /**
         * Access logs will be rotated hourly.
         * <p>
         * For example, if the file name specified was `access.log`, files will be archived on a hourly basis with names like `access-yyyyMMDDhh.log`
         */
        HOURLY("hourly"),
        /**
         * Access logs will be rotated daily.
         * <p>
         * For example, if the file name specified was `access.log`, files will be archived on a daily basis with names like `access-yyyyMMDD.log`
         */
        DAILY("daily"),
        ;

        private final String id;

        private RotatePolicy(String id) {
            this.id = id;
        }

        /**
         * Gets the identifier
         *
         * @return The identifier
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the rotate policy for specified identifier
         *
         * @param rotatePolicy The rotate policy identifier to look-up
         * @return The associated rotate policy or <code>null</code>
         */
        public static RotatePolicy rotatePolicyFor(String rotatePolicy) {
            if (null == rotatePolicy) {
                return null;
            }

            String lc = Strings.asciiLowerCase(rotatePolicy);
            for (RotatePolicy rp : RotatePolicy.values()) {
                if (rp.id.equals(lc)) {
                    return rp;
                }
            }
            return null;
        }
    }

    /**
     * Creates a new builder instance.
     *
     * @param file The location of the access log file; e.g. <code>"/tmp/access.log"</code>
     * @return The new builder
     */
    public static Builder builder(File file) {
        return new Builder(file);
    }

    /** The builder for an instance of <code>GrizzlyAccessLogConfig</code> */
    public static class Builder {

        private final File file;
        private Format format;
        private RotatePolicy rotatePolicy;
        private boolean synchronous;
        private int statusThreshold;
        private TimeZone timeZone;

        Builder(File file) {
            super();
            this.file = file;
            synchronous = false;
            format = Format.COMBINED;
            rotatePolicy = RotatePolicy.NONE;
        }

        /**
         * Sets the format
         *
         * @param format The format to set
         * @return This builder
         */
        public Builder withFormat(Format format) {
            this.format = format;
            return this;
        }

        /**
         * Sets the rotate policy
         *
         * @param rotatePolicy The rotate policy to set
         * @return This builder
         */
        public Builder withRotatePolicy(RotatePolicy rotatePolicy) {
            this.rotatePolicy = rotatePolicy;
            return this;
        }

        /**
         * Sets the synchronous
         *
         * @param synchronous The synchronous to set
         * @return This builder
         */
        public Builder withSynchronous(boolean synchronous) {
            this.synchronous = synchronous;
            return this;
        }

        /**
         * Sets the statusThreshold
         *
         * @param statusThreshold The statusThreshold to set
         * @return This builder
         */
        public Builder withStatusThreshold(int statusThreshold) {
            this.statusThreshold = statusThreshold;
            return this;
        }

        /**
         * Sets the time zone
         *
         * @param timeZone The time zone to set
         * @return This builder
         */
        public Builder withTimeZone(TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        /**
         * Creates the <code>GrizzlyAccessLogConfig</code> instance from this builder's arguments.
         *
         * @return The resulting <code>GrizzlyAccessLogConfig</code> instance
         */
        public GrizzlyAccessLogConfig build() {
            return new GrizzlyAccessLogConfig(file, format, rotatePolicy, synchronous, statusThreshold, timeZone);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------

    private final File file;
    private final Format format;
    private final RotatePolicy rotatePolicy;
    private final boolean synchronous;
    private final int statusThreshold;
    private final TimeZone timeZone;

    /**
     * Initializes a new {@link GrizzlyAccessLogConfig}.
     */
    GrizzlyAccessLogConfig() {
        this(null, null, null, false, 0, null);
    }

    /**
     * Initializes a new {@link GrizzlyAccessLogConfig}.
     */
    GrizzlyAccessLogConfig(File file, Format format, RotatePolicy rotatePolicy, boolean synchronous, int statusThreshold, TimeZone timeZone) {
        super();
        this.file = file;
        this.format = format;
        this.rotatePolicy = rotatePolicy;
        this.synchronous = synchronous;
        this.statusThreshold = statusThreshold;
        this.timeZone = timeZone;
    }

    /**
     * Checks whether access log is enabled.
     *
     * @return <code>true</code> if enabled; otherwise <code>false</code>
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * Gets the access log file.
     *
     * @return The file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the log record format.
     * <p>
     * If this configuration is not explicitly set, it will default to the NCSA extended/combined log format.
     *
     * @return The format
     */
    public Format getFormat() {
        return format;
    }


    /**
     * Gets the rotate policy
     *
     * @return The rotate policy
     */
    public RotatePolicy getRotatePolicy() {
        return rotatePolicy;
    }


    /**
     * Checks whether access log entries should be written `synchronously` or not.
     * <p>
     * If not specified, logging will occur asynchronously.
     *
     * @return The synchronous
     */
    public boolean isSynchronous() {
        return synchronous;
    }


    /**
     * Gets the minimum HTTP status code that will trigger an entry in the access log.
     * <p>
     * If not specified, then all status codes are valid.
     *
     * @return The statusThreshold
     */
    public int getStatusThreshold() {
        return statusThreshold;
    }


    /**
     * Gets the time zone for the time-stamped log records.
     * <p>
     * If not specified, it will default to the time zone of the system running the server.
     *
     * @return The time zone
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }



}
