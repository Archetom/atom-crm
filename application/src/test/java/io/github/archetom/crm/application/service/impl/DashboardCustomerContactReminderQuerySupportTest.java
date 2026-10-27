package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerContactReminderSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardCustomerContactReminderQuerySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of());
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 7, 18, 12, 0);

    @Test
    void returnsAllCumulativeThresholdsForTheNarrowedCustomerVisibility() {
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        CustomerRepository repository = mock(CustomerRepository.class);
        CustomerVisibility visibility = mock(CustomerVisibility.class);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, visibility);
        when(organization.requireActiveMembership(CALLER, AuthorityCatalog.ACTIVITIES_READ))
                .thenReturn(TENANT);
        when(customers.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                .thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.DEPARTMENT_SUBTREE)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(readableFields());
        when(repository.summarizeContactReminders(TENANT, visibility, AS_OF)).thenReturn(
                new CustomerContactReminderSummary(12L, 8L, 5L, 3L, 1L, 2L));

        var result = support(organization, customers, narrower, fields, repository).summarize(
                CALLER, TENANT, DashboardDataScope.DEPARTMENT_SUBTREE, AS_OF);

        assertEquals("AVAILABLE", result.status());
        assertEquals(List.of("OVER_7_DAYS", "OVER_15_DAYS", "OVER_30_DAYS",
                        "OVER_3_MONTHS", "OVER_6_MONTHS", "OVERDUE"),
                result.buckets().stream().map(bucket -> bucket.bucket()).toList());
        assertEquals(List.of(12L, 8L, 5L, 3L, 1L, 2L),
                result.buckets().stream().map(bucket -> bucket.count()).toList());
        verify(narrower).narrow(source, DashboardDataScope.DEPARTMENT_SUBTREE);
    }

    @Test
    void keepsTheProjectionOpaqueWhenActivitiesOrRequiredCustomerFieldsAreUnavailable() {
        OrganizationAccessGuard deniedActivities = mock(OrganizationAccessGuard.class);
        when(deniedActivities.requireActiveMembership(CALLER, AuthorityCatalog.ACTIVITIES_READ))
                .thenThrow(denied());
        var activityDenied = support(deniedActivities, mock(CustomerAccessResolver.class),
                mock(DashboardVisibilityNarrower.class), mock(EffectiveFieldAccessResolver.class),
                mock(CustomerRepository.class)).summarize(CALLER, TENANT, DashboardDataScope.SELF, AS_OF);
        assertEquals("UNAVAILABLE", activityDenied.status());
        assertEquals(List.of(), activityDenied.buckets());

        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        CustomerVisibility visibility = mock(CustomerVisibility.class);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, visibility);
        when(organization.requireActiveMembership(CALLER, AuthorityCatalog.ACTIVITIES_READ))
                .thenReturn(TENANT);
        when(customers.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                .thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.SELF)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(Map.of());
        var fieldDenied = support(organization, customers, narrower, fields,
                mock(CustomerRepository.class)).summarize(CALLER, TENANT, DashboardDataScope.SELF, AS_OF);

        assertEquals("UNAVAILABLE", fieldDenied.status());
        assertEquals(List.of(), fieldDenied.buckets());
    }

    @Test
    void summarizesFollowUpCoverageFromTheSameNarrowedCustomerSource() {
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        CustomerRepository repository = mock(CustomerRepository.class);
        CustomerVisibility visibility = mock(CustomerVisibility.class);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, visibility);
        ReportDateRange range = new ReportDateRange(AS_OF.toLocalDate().withDayOfMonth(1),
                AS_OF.toLocalDate().withDayOfMonth(31));
        when(organization.requireActiveMembership(CALLER, AuthorityCatalog.ACTIVITIES_READ))
                .thenReturn(TENANT);
        when(customers.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                .thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.DEPARTMENT)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(readableFields());
        when(repository.summarizeFollowUpCoverage(TENANT, visibility, range)).thenReturn(
                new CustomerFollowUpSummary(8L, 3L));

        CustomerFollowUpSummary result = support(organization, customers, narrower, fields, repository)
                .summarizeFollowUpCoverage(CALLER, TENANT, DashboardDataScope.DEPARTMENT, range);

        assertEquals(8L, result.followedCount());
        assertEquals(3L, result.newUnfollowedCount());
        verify(narrower).narrow(source, DashboardDataScope.DEPARTMENT);
    }

    private DashboardCustomerContactReminderQuerySupport support(
            OrganizationAccessGuard organization, CustomerAccessResolver customers,
            DashboardVisibilityNarrower narrower, EffectiveFieldAccessResolver fields,
            CustomerRepository repository) {
        return new DashboardCustomerContactReminderQuerySupport(organization, customers, narrower,
                fields, repository);
    }

    private Map<FieldCode, EffectiveFieldAccess> readableFields() {
        EffectiveFieldAccess readable = new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE);
        return Map.of(
                new FieldCode(CustomerFieldCatalog.CREATED_TIME), readable,
                new FieldCode(CustomerFieldCatalog.STATUS), readable);
    }

    private NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
    }
}
