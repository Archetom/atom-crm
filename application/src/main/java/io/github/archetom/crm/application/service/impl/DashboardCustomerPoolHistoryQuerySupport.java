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
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads pool event totals only from pools currently visible to the verified member. */
@Component
@RequiredArgsConstructor
class DashboardCustomerPoolHistoryQuerySupport {

    private final OrganizationAccessGuard organizationAccessGuard;
    private final CustomerAccessResolver customerAccessResolver;
    private final DashboardVisibilityNarrower visibilityNarrower;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final CustomerPoolCatalogQuery poolCatalogQuery;
    private final CustomerPoolRepository customerPoolRepository;

    CustomerPoolHistorySummary summarize(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("dashboard customer pool history range is required");
        }
        TenantId poolTenant = organizationAccessGuard.requireActiveMembership(
                caller, AuthorityCatalog.CUSTOMER_POOLS_READ);
        requireSameTenant(tenantId, poolTenant);
        CustomerAccessResolver.CustomerAccess source = customerAccessResolver.access(
                caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.OWNER_SCOPE);
        requireSameTenant(tenantId, source.tenantId());
        CustomerAccessResolver.CustomerAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        TenantAuthorizationEvaluator.Evaluation authorization = authorizationEvaluator.evaluate(
                tenantId, scoped.memberId());
        OrganizationMembership membership = authorization.membership();
        if (membership == null || !membership.isActive()) {
            throw denied("customer pool dashboard membership is unavailable");
        }
        boolean tenantWide = authorization.roles().stream().anyMatch(role ->
                role.isActive() && role.getSystemKey() == SystemRoleKey.TENANT_OWNER);
        return customerPoolRepository.summarizeEventHistory(tenantId,
                poolCatalogQuery.findAccessible(tenantId, scoped.memberId(),
                                membership.getDepartmentId(), tenantWide)
                        .stream().map(CustomerPoolCatalogQuery.Entry::id).toList(),
                scoped.visibility(), range);
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("dashboard customer pool history tenant is inconsistent");
        }
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }
}
