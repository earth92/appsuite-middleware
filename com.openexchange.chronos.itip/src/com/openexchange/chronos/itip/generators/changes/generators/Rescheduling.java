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

package com.openexchange.chronos.itip.generators.changes.generators;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.dmfs.rfc5545.DateTime;
import com.openexchange.chronos.Event;
import com.openexchange.chronos.EventField;
import com.openexchange.chronos.itip.Messages;
import com.openexchange.chronos.itip.generators.ArgumentType;
import com.openexchange.chronos.itip.generators.Sentence;
import com.openexchange.chronos.itip.generators.changes.ChangeDescriptionGenerator;
import com.openexchange.chronos.itip.osgi.Services;
import com.openexchange.chronos.itip.tools.ITipEventUpdate;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.regional.RegionalSettingsService;

/**
 * {@link Rescheduling}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class Rescheduling implements ChangeDescriptionGenerator {
    //TODO: Series Description

    private static enum Format {
        SAME_DAY, DIFFERENT_DAYS
    }

    private final EventField[] FIELDS = new EventField[] { EventField.START_DATE, EventField.END_DATE };

    @Override
    public List<Sentence> getDescriptions(Context ctx, Event original, Event updated, ITipEventUpdate diff, Locale locale, TimeZone timezone, int recipientUserId) {
        List<Sentence> sentences = handleChangedTimeZones(original, updated);
        if (timeChanged(original, updated)) {
            String msg = Messages.HAS_RESCHEDULED;
            sentences.add(new Sentence(msg).add(timeString(original, diff, locale, timezone, ctx, recipientUserId), ArgumentType.ORIGINAL).add(updatedTimeString(updated, diff, locale, timezone, ctx, recipientUserId), ArgumentType.UPDATED));
        }
        return sentences;
    }

    @Override
    public List<Sentence> getDescriptions(Context ctx, Event original, Event updated, ITipEventUpdate diff, Locale locale, TimeZone timezone) {
        return getDescriptions(ctx, original, updated, diff, locale, timezone, 0);
    }

    private String timeString(Event event, ITipEventUpdate diff, Locale locale, TimeZone timezone, Context ctx, int recipientUserId) {
        Format format = chooseFormat(diff);
        if (differentDays(event.getStartDate(), event.getEndDate())) {
            format = Format.DIFFERENT_DAYS;
        }
        return time(format, event, locale, timezone, ctx, recipientUserId);
    }

    private String updatedTimeString(Event event, ITipEventUpdate diff, Locale locale, TimeZone timezone, Context ctx, int recipientUserId) {
        Format format = chooseFormat(diff);
        if (differentDays(event.getStartDate(), event.getEndDate())) {
            format = Format.DIFFERENT_DAYS;
        }
        return updatedTime(format, event, locale, timezone, ctx, recipientUserId);
    }

    private String updatedTime(Format format, Event updated, Locale locale, TimeZone timezone, Context ctx, int recipientUserId) {
        Date startDate = new Date(updated.getStartDate().getTimestamp());
        Date endDate = new Date(updated.getEndDate().getTimestamp());

        RegionalSettingsService regionalSettingsService = Services.getService(RegionalSettingsService.class);
        DateFormat longDate;
        if (null == regionalSettingsService) {
            longDate = DateFormat.getDateInstance(DateFormat.LONG, locale);
        } else {
            longDate = regionalSettingsService.getDateFormat(ctx.getContextId(), recipientUserId, locale, DateFormat.LONG);
        }
        longDate.setTimeZone(timezone);
        if (updated.getStartDate().isAllDay()) {
            longDate.setTimeZone(TimeZone.getTimeZone("UTC"));
            endDate = forceCorrectDay(endDate);
        }

        DateFormat time;
        if (null == regionalSettingsService) {
            time = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        } else {
            time = regionalSettingsService.getTimeFormat(ctx.getContextId(), recipientUserId, locale, DateFormat.SHORT);
        }
        time.setTimeZone(timezone);
        switch (format) {
            case SAME_DAY:
                if (updated.getStartDate().isAllDay()) {
                    return String.format("%s (%s)", longDate.format(startDate), Messages.FULL_TIME);
                }
                    return String.format("%s - %s", time.format(startDate), time.format(endDate));
            case DIFFERENT_DAYS:
                if (updated.getStartDate().isAllDay()) {
                    return String.format("%s - %s (%s)", longDate.format(startDate), longDate.format(endDate), new Sentence(Messages.FULL_TIME).getMessage(locale));
                }
                    return String.format("%s - %s", longDate.format(startDate) + " " + time.format(startDate), longDate.format(endDate) + " " + time.format(endDate));
        }
        return ""; // Won't happen
    }

    private String time(Format format, Event original, Locale locale, TimeZone timezone, Context ctx, int recipientUserId) {
        Date startDate = new Date(original.getStartDate().getTimestamp());
        Date endDate = new Date(original.getEndDate().getTimestamp());

        RegionalSettingsService regionalSettingsService = Services.getService(RegionalSettingsService.class);
        DateFormat longDate;
        if (null != regionalSettingsService && recipientUserId > 0) {
            longDate = regionalSettingsService.getDateFormat(ctx.getContextId(), recipientUserId, locale, DateFormat.LONG);
        } else {
            longDate = DateFormat.getDateInstance(DateFormat.LONG, locale);
        }
        if (original.getStartDate().isAllDay()) {
            longDate.setTimeZone(TimeZone.getTimeZone("UTC"));
            endDate = forceCorrectDay(endDate);
        } else {
            longDate.setTimeZone(timezone);
        }

        DateFormat time;
        if (null != regionalSettingsService && recipientUserId > 0) {
            time = regionalSettingsService.getTimeFormat(ctx.getContextId(), recipientUserId, locale, DateFormat.SHORT);
        } else {
            time = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        }
        time.setTimeZone(timezone);

        switch (format) {
            case SAME_DAY:
                if (original.getStartDate().isAllDay()) {
                    return String.format("%s (%s)", longDate.format(startDate), Messages.FULL_TIME);
                }
                    return String.format("%s - %s", longDate.format(startDate) + " " + time.format(startDate), time.format(endDate));
            case DIFFERENT_DAYS:
                if (original.getStartDate().isAllDay()) {
                    return String.format("%s - %s (%s)", longDate.format(startDate), longDate.format(endDate), new Sentence(Messages.FULL_TIME).getMessage(locale));
                }
                    return String.format("%s - %s", longDate.format(startDate) + " " + time.format(startDate), longDate.format(endDate) + " " + time.format(endDate));
        }
        return ""; // Won't happen
    }

    private Date forceCorrectDay(Date endDate) {
        return new Date(endDate.getTime() - 1000); // Move this before midnight, so the time formatting routines don't lie
    }

    private Format chooseFormat(ITipEventUpdate diff) {

        if (diff.getUpdatedFields().contains(EventField.START_DATE)) {
            if (differentDays(diff.getOriginal().getStartDate(), diff.getUpdate().getStartDate())) {
                return Format.DIFFERENT_DAYS;
            }
        }

        if (diff.getUpdatedFields().contains(EventField.END_DATE)) {
            if (differentDays(diff.getOriginal().getEndDate(), diff.getUpdate().getEndDate())) {
                return Format.DIFFERENT_DAYS;
            }
        }

        return Format.SAME_DAY;
    }

    private boolean differentDays(DateTime original, DateTime update) {
        if (original.getYear() != update.getYear()) {
            return true;
        }

        if (original.getMonth() != update.getMonth()) {
            return true;
        }

        if (original.getDayOfMonth() != update.getDayOfMonth()) {
            return true;
        }

        return false;
    }

    @Override
    public EventField[] getFields() {
        return FIELDS;
    }

    private List<Sentence> handleChangedTimeZones(Event original, Event updated) {
        List<Sentence> sentences = new LinkedList<>();

        if (null != original) {
            String defaultValue = "UTC";
            String originalStartId = notNull(original, original.getStartDate(), original.getStartDate().getTimeZone()) ? original.getStartDate().getTimeZone().getID() : defaultValue;
            String originalEndId = notNull(original, original.getEndDate(), original.getEndDate().getTimeZone()) ? original.getEndDate().getTimeZone().getID() : defaultValue;
            String updatedStartId = notNull(updated, updated.getStartDate(), updated.getStartDate().getTimeZone()) ? updated.getStartDate().getTimeZone().getID() : defaultValue;
            String updatedEndId = notNull(updated, updated.getEndDate(), updated.getEndDate().getTimeZone()) ? updated.getEndDate().getTimeZone().getID() : defaultValue;

            // Both dates were the same and changed to the same?
            if (originalStartId.equals(originalEndId) && updatedStartId.equals(updatedEndId)) {
                if (false == originalStartId.equals(updatedStartId)) {
                    sentences.add(new Sentence(Messages.HAS_RESCHEDULED_TIMEZONE).add(originalStartId, ArgumentType.ORIGINAL).add(updatedStartId, ArgumentType.UPDATED));
                }
            } else {
                // Separate start and end date sentences
                if (false == originalStartId.equals(updatedStartId)) {
                    sentences.add(new Sentence(Messages.HAS_RESCHEDULED_TIMEZONE_START_DATE).add(originalStartId, ArgumentType.ORIGINAL).add(updatedStartId, ArgumentType.UPDATED));
                }
                if (false == originalEndId.equals(updatedEndId)) {
                    sentences.add(new Sentence(Messages.HAS_RESCHEDULED_TIMEZONE_END_DATE).add(originalEndId, ArgumentType.ORIGINAL).add(updatedEndId, ArgumentType.UPDATED));
                }
            }
        }
        return sentences;
    }

    private boolean notNull(Object... o) {
        for (Object obj : o) {
            if (null == obj) {
                return false;
            }
        }
        return true;
    }

    private boolean timeChanged(Event original, Event update) {
        return false == (original.getStartDate().getTimestamp() == update.getStartDate().getTimestamp() && original.getEndDate().getTimestamp() == update.getEndDate().getTimestamp());
    }
}
