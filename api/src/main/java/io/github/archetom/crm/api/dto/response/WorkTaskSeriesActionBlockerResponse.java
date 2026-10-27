package io.github.archetom.crm.api.dto.response;

/** Stable reason why a currently manageable series cannot expose an action. */
public enum WorkTaskSeriesActionBlockerResponse {
    SCHEDULER_UNAVAILABLE,
    PARTICIPANT_INELIGIBLE
}
