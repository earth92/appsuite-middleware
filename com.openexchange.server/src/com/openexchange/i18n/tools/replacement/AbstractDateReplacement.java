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

package com.openexchange.i18n.tools.replacement;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import com.openexchange.i18n.tools.TemplateReplacement;

/**
 * {@link AbstractDateReplacement} - An abstract class for date string replacements using {@link DateFormat#format(Date)}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public abstract class AbstractDateReplacement implements TemplateReplacement {

    private static final String PAT_ZONE = ", z";

    private static final String PAT_DAY_OF_WEEK = "EEEE, ";

    protected final boolean withTime;

    protected Date date;

    protected boolean changed;

    protected DateFormat dateFormat;

    protected Locale locale;

    protected TimeZone timeZone;

    /**
     * Initializes a new {@link AbstractDateReplacement}
     *
     * @param date The date
     * @param withTime <code>true</code> to include given date's time information; otherwise <code>false</code>
     */
    protected AbstractDateReplacement(final Date date, final boolean withTime) {
        this(date, withTime, null, null);
    }

    /**
     * Initializes a new {@link AbstractDateReplacement}
     *
     * @param date The date
     * @param withTime <code>true</code> to include given date's time and time zone; otherwise <code>false</code>
     * @param locale The locale
     * @param timeZone The time zone; may be <code>null</code>
     */
    protected AbstractDateReplacement(final Date date, final boolean withTime, final Locale locale, TimeZone timeZone) {
        super();
        this.withTime = withTime;
        this.date = date;
        this.dateFormat = getDateFormat(withTime, locale, timeZone);
        if (!withTime) {
            this.timeZone = TimeZone.getTimeZone("UTC"); // No need to start calculating TZ offset with dates only. Assume UTC.
        } else {
            this.timeZone = timeZone;
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        final AbstractDateReplacement clone = (AbstractDateReplacement) super.clone();
        clone.date = (Date) (this.date == null ? null : this.date.clone());
        clone.dateFormat = (DateFormat) (this.dateFormat == null ? null : dateFormat.clone());
        clone.locale = (Locale) (this.locale == null ? null : this.locale.clone());
        clone.timeZone = (TimeZone) (this.timeZone == null ? null : timeZone.clone());
        return clone;
    }

    @Override
    public TemplateReplacement getClone() throws CloneNotSupportedException {
        return (TemplateReplacement) clone();
    }

    @Override
    public final boolean changed() {
        return changed;
    }

    @Override
    public boolean relevantChange() {
        return changed();
    }

    @Override
    public final TemplateReplacement setChanged(final boolean changed) {
        this.changed = changed;
        return this;
    }

    /**
     * Gets the replacement for associated date template or an empty string if applied {@link Date} object is <code>null</code>
     */
    @Override
    public String getReplacement() {
        return date == null ? "" : dateFormat.format(date);
    }

    /**
     * Applies given time zone to this replacement.
     * <p>
     * If given time zone is <code>null</code>, it is treated as a no-op.
     *
     * @param timeZone The new time zone to apply
     * @return This replacement with new time zone applied
     */
    @Override
    public final TemplateReplacement setTimeZone(final TimeZone timeZone) {
        applyTimeZone(timeZone);
        return this;
    }

    /**
     * Applies given locale to this replacement.
     * <p>
     * If given locale is <code>null</code>, it is treated as a no-op.
     *
     * @param locale The new locale to apply
     * @return This replacement with new locale applied
     */
    @Override
    public final TemplateReplacement setLocale(final Locale locale) {
        applyLocale(locale);
        return this;
    }

    @Override
    public boolean merge(final TemplateReplacement other) {
        if (!AbstractDateReplacement.class.isInstance(other)) {
            /*
             * Class mismatch or null
             */
            return false;
        }
        if (!getToken().equals(other.getToken())) {
            /*
             * Token mismatch
             */
            return false;
        }
        if (!other.changed()) {
            /*
             * Other replacement does not reflect a changed value; leave unchanged
             */
            return false;
        }
        final AbstractDateReplacement o = (AbstractDateReplacement) other;
        this.date = null == o.date ? null : new Date(o.date.getTime());
        this.changed = true;
        return true;
    }

    private void applyLocale(final Locale locale) {
        if (locale == null || locale.equals(this.locale)) {
            return;
        }
        this.locale = locale;
        this.dateFormat = getDateFormat(withTime, locale, this.timeZone);
    }

    private void applyTimeZone(final TimeZone timeZone) {
        // No need to do anything if no timezone is specified or if the timezone hasn't changed or if we're only dealing
        // with days anyway.
        if (timeZone == null || timeZone.equals(this.timeZone) || !withTime) {
            return;
        }
        if (withTime && this.timeZone == null && dateFormat instanceof SimpleDateFormat) {
            /*
             * Time zone was not set before: extend pattern to contain time zone information
             */
            final SimpleDateFormat simpleDateFormat = (SimpleDateFormat) dateFormat;
            final String pattern = simpleDateFormat.toPattern();
            simpleDateFormat.applyPattern(new StringBuilder(pattern.length() + 3).append(pattern).append(PAT_ZONE).toString());

        }
        this.timeZone = timeZone;
        this.dateFormat.setTimeZone(timeZone);
    }

    private static DateFormat getDateFormat(final boolean withTime, final Locale locale, final TimeZone timeZone) {
        final Locale l = locale == null ? Locale.ENGLISH : locale;
        if (withTime) {
            final SimpleDateFormat sdf = (SimpleDateFormat) DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, l);
            /*
             * Add day of week to pattern and time zone if not null
             */
            final String pattern = sdf.toPattern();
            final StringBuilder builder = new StringBuilder(pattern.length() + 9).append(PAT_DAY_OF_WEEK).append(pattern);
            if (null != timeZone) {
                builder.append(PAT_ZONE);
                sdf.applyPattern(builder.toString());
                sdf.setTimeZone(timeZone);
            } else {
                sdf.applyPattern(builder.toString());
            }
            return sdf;
        }
        /*
         * The date-only instance
         */
        DateFormat format = DateFormat.getDateInstance(DateFormat.DEFAULT, l);
        if (timeZone != null) {
            format.setTimeZone(timeZone);
        }
        return format;
    }

    @Override
    public TemplateReplacement setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

}
