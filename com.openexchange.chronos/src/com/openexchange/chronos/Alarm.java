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

package com.openexchange.chronos;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

/**
 * {@link Alarm}
 *
 * @author <a href="mailto:tobias.friedrich@open-xchange.com">Tobias Friedrich</a>
 * @since v7.10.0
 * @see <a href="https://tools.ietf.org/html/rfc5545#section-3.8.6">RFC 5545, section 3.8.6</a>
 */
public class Alarm {

    private int id;
    private String uid;
    private RelatedTo relatedTo;
    private Trigger trigger;
    private Date acknowledged;
    private AlarmAction action;
    private Repeat repeat;
    private ExtendedProperties extendedProperties;
    private List<Attachment> attachments;
    private String description;
    private String summary;
    private List<Attendee> attendees;
    private long timestamp;

    private final EnumSet<AlarmField> setFields;

    /**
     * Initializes a new {@link Alarm}.
     */
    public Alarm() {
        super();
        this.setFields = EnumSet.noneOf(AlarmField.class);
    }

    /**
     * Initializes a new {@link Alarm}.
     *
     * @param trigger The trigger to apply
     * @param action The action to apply
     */
    public Alarm(Trigger trigger, AlarmAction action) {
        this();
        setTrigger(trigger);
        setAction(action);
    }

    /**
     * Gets a value indicating whether a specific property is set in the alarm or not.
     *
     * @param field The field to check
     * @return <code>true</code> if the field is set, <code>false</code>, otherwise
     */
    public boolean isSet(AlarmField field) {
        return setFields.contains(field);
    }

    /**
     * Gets the internal identifier of the alarm.
     *
     * @return The internal identifier
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the internal identifier of the alarm.
     *
     * @param value The internal identifier to set
     */
    public void setId(int value) {
        id = value;
        setFields.add(AlarmField.ID);
    }

    /**
     * Removes the internal identifier of the alarm.
     */
    public void removeId() {
        id = 0;
        setFields.remove(AlarmField.ID);
    }

    /**
     * Gets a value indicating whether the internal identifier of the alarm has been set or not.
     *
     * @return <code>true</code> if the internal identifier is set, <code>false</code>, otherwise
     */
    public boolean containsId() {
        return isSet(AlarmField.ID);
    }

    /**
     * Gets the universal identifier of the alarm.
     *
     * @return The universal identifier
     */
    public String getUid() {
        return uid;
    }

    /**
     * Sets the universal identifier of the alarm.
     *
     * @param value The universal identifier to set
     */
    public void setUid(String value) {
        uid = value;
        setFields.add(AlarmField.UID);
    }

    /**
     * Removes the universal identifier of the alarm.
     */
    public void removeUid() {
        uid = null;
        setFields.remove(AlarmField.UID);
    }

    /**
     * Gets a value indicating whether the universal identifier of the alarm has been set or not.
     *
     * @return <code>true</code> if the universal identifier is set, <code>false</code>, otherwise
     */
    public boolean containsUid() {
        return setFields.contains(AlarmField.UID);
    }

    /**
     * Gets the related-to of the alarm.
     *
     * @return The related-to
     */
    public RelatedTo getRelatedTo() {
        return relatedTo;
    }

    /**
     * Sets the related-to of the alarm.
     *
     * @param value The related-to to set
     */
    public void setRelatedTo(RelatedTo value) {
        relatedTo = value;
        setFields.add(AlarmField.RELATED_TO);
    }

    /**
     * Removes the related-to of the alarm.
     */
    public void removeRelatedTo() {
        relatedTo = null;
        setFields.remove(AlarmField.RELATED_TO);
    }

    /**
     * Gets a value indicating whether the related-to of the alarm has been set or not.
     *
     * @return <code>true</code> if the related-to is set, <code>false</code>, otherwise
     */
    public boolean containsRelatedTo() {
        return setFields.contains(AlarmField.RELATED_TO);
    }

    /**
     * Gets the acknowledged date of the alarm.
     *
     * @return The acknowledged date
     */
    public Date getAcknowledged() {
        return acknowledged;
    }

    /**
     * Sets the acknowledged date of the alarm.
     *
     * @param value The acknowledged date to set
     */
    public void setAcknowledged(Date value) {
        acknowledged = value;
        setFields.add(AlarmField.ACKNOWLEDGED);
    }

    /**
     * Removes the acknowledged date of the alarm.
     */
    public void removeAcknowledged() {
        acknowledged = null;
        setFields.remove(AlarmField.ACKNOWLEDGED);
    }

    /**
     * Gets a value indicating whether the acknowledged date of the alarm has been set or not.
     *
     * @return <code>true</code> if the acknowledged date is set, <code>false</code>, otherwise
     */
    public boolean containsAcknowledged() {
        return isSet(AlarmField.ACKNOWLEDGED);
    }

    /**
     * Gets the action of the alarm.
     *
     * @return The action
     */
    public AlarmAction getAction() {
        return action;
    }

    /**
     * Sets the action of the alarm.
     *
     * @param value The action to set
     */
    public void setAction(AlarmAction value) {
        action = value;
        setFields.add(AlarmField.ACTION);
    }

    /**
     * Removes the action of the alarm.
     */
    public void removeAction() {
        action = null;
        setFields.remove(AlarmField.ACTION);
    }

    /**
     * Gets a value indicating whether the action of the alarm has been set or not.
     *
     * @return <code>true</code> if the action is set, <code>false</code>, otherwise
     */
    public boolean containsAction() {
        return setFields.contains(AlarmField.ACTION);
    }

    /**
     * Gets the additional repetitions of the alarm's trigger.
     *
     * @return The additional repetitions
     */
    public Repeat getRepeat() {
        return repeat;
    }

    /**
     * Sets the additional repetitions of the alarm's trigger.
     *
     * @param value The additional repetitions to set
     */
    public void setRepeat(Repeat value) {
        repeat = value;
        setFields.add(AlarmField.REPEAT);
    }

    /**
     * Removes the additional repetitions of the alarm.
     */
    public void removeRepeat() {
        repeat = null;
        setFields.remove(AlarmField.REPEAT);
    }

    /**
     * Gets a value indicating whether the additional repetitions of the alarm has been set or not.
     *
     * @return <code>true</code> if the additional repetitions is set, <code>false</code>, otherwise
     */
    public boolean containsRepeat() {
        return isSet(AlarmField.REPEAT);
    }

    /**
     * Gets the trigger of the alarm.
     *
     * @return The alarm trigger
     */
    public Trigger getTrigger() {
        return trigger;
    }

    /**
     * Sets the trigger of the alarm.
     *
     * @param value The alarm trigger to set
     */
    public void setTrigger(Trigger value) {
        trigger = value;
        setFields.add(AlarmField.TRIGGER);
    }

    /**
     * Removes the trigger of the alarm.
     */
    public void removeTrigger() {
        trigger = null;
        setFields.remove(AlarmField.TRIGGER);
    }

    /**
     * Gets a value indicating whether the trigger of the alarm has been set or not.
     *
     * @return <code>true</code> if the trigger is set, <code>false</code>, otherwise
     */
    public boolean containsTrigger() {
        return isSet(AlarmField.TRIGGER);
    }

    /**
     * Gets the extended properties of the alarm.
     *
     * @return The extended properties
     */
    public ExtendedProperties getExtendedProperties() {
        return extendedProperties;
    }

    /**
     * Sets the extended properties of the alarm.
     *
     * @param value The extended properties to set
     */
    public void setExtendedProperties(ExtendedProperties value) {
        extendedProperties = value;
        setFields.add(AlarmField.EXTENDED_PROPERTIES);
    }

    /**
     * Removes the extended properties of the alarm.
     */
    public void removeExtendedProperties() {
        extendedProperties = null;
        setFields.remove(AlarmField.EXTENDED_PROPERTIES);
    }

    /**
     * Gets a value indicating whether extended properties of the alarm have been set or not.
     *
     * @return <code>true</code> if extended properties are set, <code>false</code>, otherwise
     */
    public boolean containsExtendedProperties() {
        return setFields.contains(AlarmField.EXTENDED_PROPERTIES);
    }

    /**
     * Gets the attachments of the alarm.
     *
     * @return The attachments
     */
    public List<Attachment> getAttachments() {
        return attachments;
    }

    /**
     * Sets the attachments of the alarm.
     *
     * @param attachments The attachments to set
     */
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
        setFields.add(AlarmField.ATTACHMENTS);
    }

    /**
     * Removes the attachments of the alarm.
     */
    public void removeAttachments() {
        this.attachments = null;
        setFields.remove(AlarmField.ATTACHMENTS);
    }

    /**
     * Gets a value indicating whether attachments of the alarm have been set or not.
     *
     * @return <code>true</code> if attachments are set, <code>false</code>, otherwise
     */
    public boolean containsAttachments() {
        return setFields.contains(AlarmField.ATTACHMENTS);
    }

    /**
     * Gets the description of the alarm.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the alarm.
     *
     * @param description The description to set
     */
    public void setDescription(String description) {
        this.description = description;
        setFields.add(AlarmField.DESCRIPTION);
    }

    /**
     * Removes the description of the alarm.
     */
    public void removeDescription() {
        this.description = null;
        setFields.remove(AlarmField.DESCRIPTION);
    }

    /**
     * Gets a value indicating whether the description of the alarm has been set or not.
     *
     * @return <code>true</code> if the description is set, <code>false</code>, otherwise
     */
    public boolean containsDescription() {
        return setFields.contains(AlarmField.DESCRIPTION);
    }

    /**
     * Gets the summary of the alarm.
     *
     * @return The summary
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the summary of the alarm.
     *
     * @param summary The summary to set
     */
    public void setSummary(String summary) {
        this.summary = summary;
        setFields.add(AlarmField.SUMMARY);
    }

    /**
     * Removes the summary of the alarm.
     */
    public void removeSummary() {
        this.summary = null;
        setFields.remove(AlarmField.SUMMARY);
    }

    /**
     * Gets a value indicating whether the summary of the alarm has been set or not.
     *
     * @return <code>true</code> if the summary is set, <code>false</code>, otherwise
     */
    public boolean containsSummary() {
        return setFields.contains(AlarmField.SUMMARY);
    }

    /**
     * Gets the attendees of the alarm.
     *
     * @return The attendees
     */
    public List<Attendee> getAttendees() {
        return attendees;
    }

    /**
     * Sets the attendees of the alarm.
     *
     * @param mailAddresses The mailAddresses to set
     */
    public void setAttendees(List<Attendee> attendees) {
        this.attendees = attendees;
        setFields.add(AlarmField.ATTENDEES);
    }

    /**
     * Removes attendees of the alarm.
     */
    public void removeAttendees() {
        this.attendees = null;
        setFields.remove(AlarmField.ATTENDEES);
    }

    /**
     * Gets a value indicating whether attendees of the alarm have been set or not.
     *
     * @return <code>true</code> if attendees are set, <code>false</code>, otherwise
     */
    public boolean containsAttendees() {
        return setFields.contains(AlarmField.ATTENDEES);
    }

    /**
     * Gets the timestamp of the alarm.
     * 
     * @return The timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of the alarm.
     * 
     * @param The timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        setFields.add(AlarmField.TIMESTAMP);
    }

    /**
     * Gets a value indicating whether the timestamp of the alarm has been set or not.
     *
     * @return <code>true</code> if the timestamp is set, <code>false</code>, otherwise
     */
    public boolean containsTimestamp() {
        return setFields.contains(AlarmField.TIMESTAMP);
    }

    /**
     * Removes the last modified of the alarm.
     */
    public void removeTimestamp() {
        this.timestamp = 0;
        setFields.remove(AlarmField.TIMESTAMP);
    }

    @Override
    public String toString() {
        return "Alarm [action=" + action + ", trigger=" + trigger + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((acknowledged == null) ? 0 : acknowledged.hashCode());
        result = prime * result + ((action == null) ? 0 : action.hashCode());
        result = prime * result + ((attachments == null) ? 0 : attachments.hashCode());
        result = prime * result + ((attendees == null) ? 0 : attendees.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((extendedProperties == null) ? 0 : extendedProperties.hashCode());
        result = prime * result + id;
        result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
        result = prime * result + ((relatedTo == null) ? 0 : relatedTo.hashCode());
        result = prime * result + ((repeat == null) ? 0 : repeat.hashCode());
        result = prime * result + ((setFields == null) ? 0 : setFields.hashCode());
        result = prime * result + ((summary == null) ? 0 : summary.hashCode());
        result = prime * result + ((trigger == null) ? 0 : trigger.hashCode());
        result = prime * result + ((uid == null) ? 0 : uid.hashCode());
        return result;
    }

}
