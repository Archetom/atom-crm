package io.github.archetom.crm.domain.model;

/** Fixed management-safe outcomes for a one-shot announcement publication. */
public enum OaAnnouncementPublicationSafeReason {
    MISFIRE_EXPIRED,
    SCHEDULING_MEMBER_UNAUTHORIZED,
    AUDIENCE_INVALID,
    AUDIENCE_EMPTY,
    AUDIENCE_LIMIT,
    ATTACHMENT_UNAVAILABLE,
    RETRY_EXHAUSTED,
    RETRY,
    CANCELLED,
    SUPERSEDED
}
