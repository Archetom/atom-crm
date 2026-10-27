package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.vo.DashboardOverviewVO;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerContactReminderSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Builds a customer-contact reminder only when both customer and activity sources are readable. */
@Component
@RequiredArgsConstructor
class DashboardCustomerContactReminderQuerySupport {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final OrganizationAccessGuard organizationAccessGuard;
    private final CustomerAccessResolver customerAccessResolver;
    private final DashboardVisibilityNarrower visibilityNarrower;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final CustomerRepository customerRepository;

    DashboardOverviewVO.CustomerContactReminderVO summarize(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            LocalDateTime asOf) {
        try {
            CustomerAccessResolver.CustomerAccess scoped = resolveSource(
                    caller, tenantId, preferredScope);
            return available(customerRepository.summarizeContactReminders(
                    tenantId, scoped.visibility(), asOf));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return unavailable();
        }
    }

    CustomerFollowUpSummary summarizeFollowUpCoverage(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("dashboard customer follow-up range is required");
        }
        CustomerAccessResolver.CustomerAccess scoped = resolveSource(caller, tenantId, preferredScope);
        return customerRepository.summarizeFollowUpCoverage(tenantId, scoped.visibility(), range);
    }

    private CustomerAccessResolver.CustomerAccess resolveSource(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope) {
        TenantId activityTenant = organizationAccessGuard.requireActiveMembership(
                caller, AuthorityCatalog.ACTIVITIES_READ);
        requireSameTenant(tenantId, activityTenant);
        CustomerAccessResolver.CustomerAccess source = customerAccessResolver.access(
                caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        CustomerAccessResolver.CustomerAccess scoped = visibilityNarrower.narrow(
                source, preferredScope);
        requireFields(tenantId, scoped);
        return scoped;
    }

    private DashboardOverviewVO.CustomerContactReminderVO available(
            CustomerContactReminderSummary summary) {
        return new DashboardOverviewVO.CustomerContactReminderVO(AVAILABLE, List.of(
                bucket("OVER_7_DAYS", summary.over7Days()),
                bucket("OVER_15_DAYS", summary.over15Days()),
                bucket("OVER_30_DAYS", summary.over30Days()),
                bucket("OVER_3_MONTHS", summary.over3Months()),
                bucket("OVER_6_MONTHS", summary.over6Months()),
                bucket("OVERDUE", summary.overdue())));
    }

    private DashboardOverviewVO.CustomerContactReminderBucketVO bucket(String bucket, long count) {
        return new DashboardOverviewVO.CustomerContactReminderBucketVO(bucket, count);
    }

    private void requireFields(
            TenantId tenantId, CustomerAccessResolver.CustomerAccess source) {
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), BusinessObjectType.CUSTOMER);
        for (String code : List.of(CustomerFieldCatalog.CREATED_TIME, CustomerFieldCatalog.STATUS)) {
            EffectiveFieldAccess access = fields.get(new FieldCode(code));
            if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                    || access.maskType() != FieldMaskType.NONE) {
                throw denied("customer contact reminder field is unavailable");
            }
        }
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("dashboard customer contact reminder tenant is inconsistent");
        }
    }

    private DashboardOverviewVO.CustomerContactReminderVO unavailable() {
        return new DashboardOverviewVO.CustomerContactReminderVO(UNAVAILABLE, List.of());
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }
}
