package io.github.archetom.crm.application.oa;

/** Durable lifecycle of one exact OA announcement publication generation. */
public enum OaAnnouncementPublicationOccurrenceStatus {
    READY,
    RETRY_WAIT,
    LEASED,
    PUBLISHED,
    CANCELLED,
    DEAD;

    public boolean terminal() {
        return this == PUBLISHED || this == CANCELLED || this == DEAD;
    }
}
