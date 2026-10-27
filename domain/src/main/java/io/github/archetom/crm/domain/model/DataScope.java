package io.github.archetom.crm.domain.model;

/** Tenant-bounded data visibility attached to a capability grant. */
public enum DataScope {
    SELF,
    DEPARTMENT,
    DEPARTMENT_SUBTREE,
    TENANT
}
