package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerPoolHistorySummary;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardCustomerPoolHistoryQuerySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final DepartmentId DEPARTMENT = new DepartmentId(3L);
    private static final CustomerPoolId POOL = new CustomerPoolId(11L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(
            AuthorityCatalog.CUSTOMER_POOLS_READ, AuthorityCatalog.CUSTOMERS_READ));

    @Test
    void usesOnlyCurrentCatalogPoolsAndTheNarrowedOwnerScope() {
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        CustomerAccessResolver customerAccessResolver = mock(CustomerAccessResolver.class);
        DashboardVisibilityNarrower visibilityNarrower = mock(DashboardVisibilityNarrower.class);
        TenantAuthorizationEvaluator authorizationEvaluator = mock(TenantAuthorizationEvaluator.class);
        CustomerPoolCatalogQuery catalogQuery = mock(CustomerPoolCatalogQuery.class);
        CustomerPoolRepository poolRepository = mock(CustomerPoolRepository.class);
        DashboardCustomerPoolHistoryQuerySupport support =
                new DashboardCustomerPoolHistoryQuerySupport(accessGuard, customerAccessResolver,
                        visibilityNarrower, authorizationEvaluator, catalogQuery, poolRepository);
        CustomerVisibility sourceVisibility = new CustomerVisibility(
                DataScope.TENANT, MEMBER, Set.of(), CustomerAccessMode.OWNER_SCOPE);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, sourceVisibility);
        CustomerVisibility scopedVisibility = new CustomerVisibility(
                DataScope.DEPARTMENT, MEMBER, Set.of(DEPARTMENT), CustomerAccessMode.OWNER_SCOPE);
        CustomerAccessResolver.CustomerAccess scoped =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, scopedVisibility);
        OrganizationMembership membership = OrganizationMembership.activate(TENANT, MEMBER, DEPARTMENT);
        ReportDateRange range = new ReportDateRange(LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));
        CustomerPoolHistorySummary expected = new CustomerPoolHistorySummary(2L, 1L);
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.CUSTOMER_POOLS_READ))
                .thenReturn(TENANT);
        when(customerAccessResolver.access(CALLER, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.OWNER_SCOPE)).thenReturn(source);
        when(visibilityNarrower.narrow(source, DashboardDataScope.DEPARTMENT)).thenReturn(scoped);
        when(authorizationEvaluator.evaluate(TENANT, MEMBER)).thenReturn(
                new TenantAuthorizationEvaluator.Evaluation(membership, List.of(), List.of()));
        when(catalogQuery.findAccessible(TENANT, MEMBER, DEPARTMENT, false)).thenReturn(List.of(
                new CustomerPoolCatalogQuery.Entry(POOL, "Sales pool", CustomerPoolStatus.ACTIVE, false)));
        when(poolRepository.summarizeEventHistory(TENANT, List.of(POOL), scopedVisibility, range))
                .thenReturn(expected);

        CustomerPoolHistorySummary actual = support.summarize(
                CALLER, TENANT, DashboardDataScope.DEPARTMENT, range);

        assertEquals(expected, actual);
        verify(catalogQuery).findAccessible(TENANT, MEMBER, DEPARTMENT, false);
        verify(poolRepository).summarizeEventHistory(TENANT, List.of(POOL), scopedVisibility, range);
    }
}
