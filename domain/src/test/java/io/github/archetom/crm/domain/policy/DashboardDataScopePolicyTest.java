package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardDataScopePolicyTest {

    private static final DepartmentId DEPARTMENT_ID = new DepartmentId(11L);

    @Test
    void everyAuthorizedAndPreferredScopePairResolvesToTheirNarrowerScope() {
        assertNarrowed(DataScope.SELF,
                DataScope.SELF, DataScope.SELF, DataScope.SELF, DataScope.SELF);
        assertNarrowed(DataScope.DEPARTMENT,
                DataScope.SELF, DataScope.DEPARTMENT,
                DataScope.DEPARTMENT, DataScope.DEPARTMENT);
        assertNarrowed(DataScope.DEPARTMENT_SUBTREE,
                DataScope.SELF, DataScope.DEPARTMENT,
                DataScope.DEPARTMENT_SUBTREE, DataScope.DEPARTMENT_SUBTREE);
        assertNarrowed(DataScope.TENANT,
                DataScope.SELF, DataScope.DEPARTMENT,
                DataScope.DEPARTMENT_SUBTREE, DataScope.TENANT);
    }

    @Test
    void missingDepartmentDowngradesDepartmentPresentationToSelf() {
        assertEquals(DataScope.SELF, DashboardDataScopePolicy.narrow(
                DataScope.TENANT, DashboardDataScope.DEPARTMENT, null));
        assertEquals(DataScope.SELF, DashboardDataScopePolicy.narrow(
                DataScope.TENANT, DashboardDataScope.DEPARTMENT_SUBTREE, null));
        assertEquals(DataScope.SELF, DashboardDataScopePolicy.narrow(
                DataScope.DEPARTMENT_SUBTREE, DashboardDataScope.TENANT, null));
    }

    @Test
    void incompleteScopesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDataScopePolicy.narrow(
                null, DashboardDataScope.SELF, DEPARTMENT_ID));
        assertThrows(IllegalArgumentException.class, () -> DashboardDataScopePolicy.narrow(
                DataScope.SELF, null, DEPARTMENT_ID));
    }

    private static void assertNarrowed(DataScope source, DataScope... expected) {
        DashboardDataScope[] preferred = DashboardDataScope.values();
        for (int index = 0; index < preferred.length; index++) {
            assertEquals(expected[index], DashboardDataScopePolicy.narrow(
                    source, preferred[index], DEPARTMENT_ID));
        }
    }
}
