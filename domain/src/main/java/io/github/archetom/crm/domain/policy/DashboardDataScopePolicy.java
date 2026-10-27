package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.DepartmentId;

/** Intersects one source's effective authorization with a presentation-only dashboard scope. */
public final class DashboardDataScopePolicy {

    private DashboardDataScopePolicy() {
    }

    /**
     * Returns only the maximum scope the dashboard may present. Callers still
     * retain the source visibility's member, collaborator, and access-mode
     * predicates and resolve department IDs independently for that source.
     */
    public static DataScope narrow(
            DataScope sourceEffectiveScope,
            DashboardDataScope preferredScope,
            DepartmentId currentDepartmentId) {
        if (sourceEffectiveScope == null || preferredScope == null) {
            throw new IllegalArgumentException("dashboard and source data scopes must not be null");
        }
        DataScope authorized = DataScopePolicy.restrictToMembership(
                sourceEffectiveScope, currentDepartmentId);
        DataScope preferred = DataScopePolicy.restrictToMembership(
                toDataScope(preferredScope), currentDepartmentId);
        return DataScopePolicy.covers(authorized, preferred) ? preferred : authorized;
    }

    private static DataScope toDataScope(DashboardDataScope scope) {
        return switch (scope) {
            case SELF -> DataScope.SELF;
            case DEPARTMENT -> DataScope.DEPARTMENT;
            case DEPARTMENT_SUBTREE -> DataScope.DEPARTMENT_SUBTREE;
            case TENANT -> DataScope.TENANT;
        };
    }
}
