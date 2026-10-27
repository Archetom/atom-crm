package io.github.archetom.crm.domain.model;

/** One-shot lifecycle for a private CRM CSV import draft. */
public enum CrmImportDraftState {
    ACTIVE,
    CONSUMED,
    WITHDRAWN,
    EXPIRED
}
