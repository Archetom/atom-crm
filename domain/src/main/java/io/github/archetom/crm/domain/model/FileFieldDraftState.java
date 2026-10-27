package io.github.archetom.crm.domain.model;

/** One-shot pre-create staging lifecycle for governed FILE values. */
public enum FileFieldDraftState {
    ACTIVE,
    CONSUMED,
    WITHDRAWN,
    EXPIRED
}
