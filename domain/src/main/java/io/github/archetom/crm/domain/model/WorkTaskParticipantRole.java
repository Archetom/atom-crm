package io.github.archetom.crm.domain.model;

/** Fixed list filter; it never grants task visibility. */
public enum WorkTaskParticipantRole {
    ALL,
    CREATOR,
    ASSIGNEE,
    FOLLOWER
}
