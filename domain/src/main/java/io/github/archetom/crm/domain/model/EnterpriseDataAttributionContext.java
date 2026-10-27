package io.github.archetom.crm.domain.model;

/** Governed contexts in which source attribution can be required and rendered. */
public enum EnterpriseDataAttributionContext {
    SEARCH_RESULT,
    DETAIL,
    PREVIEW,
    ADOPTION_CONFIRMATION,
    CUSTOMER_READ,
    EXPORT
}
