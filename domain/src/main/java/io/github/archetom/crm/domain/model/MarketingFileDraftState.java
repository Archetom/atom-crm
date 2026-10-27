package io.github.archetom.crm.domain.model;

/** One-shot lifecycle of an anonymous MarketingForm FILE draft. */
public enum MarketingFileDraftState {
    ACTIVE,
    CONSUMED,
    WITHDRAWN,
    EXPIRED
}
