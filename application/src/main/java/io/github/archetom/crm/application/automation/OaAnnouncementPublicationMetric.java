package io.github.archetom.crm.application.automation;

/** Fixed counters for the OA one-shot publication lane. */
public enum OaAnnouncementPublicationMetric {
    SCHEDULED,
    CLAIMED,
    PUBLISHED,
    CANCELLED,
    RETRY,
    DEAD,
    MISFIRE
}
