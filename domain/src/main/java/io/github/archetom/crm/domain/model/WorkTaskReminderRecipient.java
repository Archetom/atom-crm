package io.github.archetom.crm.domain.model;

/** A reminder can only address an existing Work-task participant role. */
public enum WorkTaskReminderRecipient {
    ASSIGNEE,
    CREATOR,
    FOLLOWERS
}
