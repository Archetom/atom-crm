package io.github.archetom.crm.api.dto.response;

/** Public lifecycle of a Work task series; STOPPED does not expose the internal deletion state. */
public enum WorkTaskSeriesStatusResponse {
    DRAFT,
    ACTIVE,
    PAUSED,
    STOPPED,
    ENDED
}
