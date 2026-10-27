package io.github.archetom.crm.domain.model;

/** Presentation-only dashboard scope that can only restrict source-record visibility. */
public enum DashboardDataScope {
    SELF,
    DEPARTMENT,
    DEPARTMENT_SUBTREE,
    TENANT
}
