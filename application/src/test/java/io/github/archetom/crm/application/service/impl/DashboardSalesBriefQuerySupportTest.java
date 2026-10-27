package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardSalesBriefQuerySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of());
    private static final ReportDateRange CURRENT = new ReportDateRange(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    private static final ReportDateRange PREVIOUS = new ReportDateRange(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

    @Test
    void returnsAnAvailableCustomerMetricWithoutTurningOtherDeniedSourcesIntoZeros() {
        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        CustomerVisibility visibility = mock(CustomerVisibility.class);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, visibility);
        when(customers.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                .thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.SELF)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(Map.of(
                new FieldCode(CustomerFieldCatalog.CREATED_TIME), readable(),
                new FieldCode(CustomerFieldCatalog.STATUS), readable()));
        when(customerRepository.summarizeCreated(TENANT, visibility, CURRENT))
                .thenReturn(new DashboardMetricSummary(2L, null));
        when(customerRepository.summarizeCreated(TENANT, visibility, PREVIOUS))
                .thenReturn(new DashboardMetricSummary(1L, null));

        DashboardSalesBriefQuerySupport support = support(
                customers, narrower, fields, customerRepository);
        var values = support.summarize(CALLER, TENANT, DashboardDataScope.SELF, CURRENT, PREVIOUS);

        assertEquals(9, values.size());
        var customer = values.getFirst();
        assertEquals("NEW_CUSTOMER", customer.metric());
        assertEquals("AVAILABLE", customer.status());
        assertEquals("COUNT", customer.unit());
        assertEquals(new BigDecimal("2"), customer.value());
        assertEquals(new BigDecimal("1"), customer.previousValue());
        assertEquals(new BigDecimal("100.00"), customer.comparisonRate());
        values.stream().skip(1).forEach(value -> {
            assertEquals("UNAVAILABLE", value.status());
            assertNull(value.value());
            assertNull(value.previousValue());
            assertNull(value.comparisonRate());
        });
    }

    @Test
    void returnsTheCallerCreatedFollowUpMetricOnlyForSelfScope() {
        ActivityRepository activities = mock(ActivityRepository.class);
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        when(organization.requireActiveMembership(CALLER, AuthorityCatalog.ACTIVITIES_READ))
                .thenReturn(TENANT);
        when(activities.summarizeCreatedFollowUps(TENANT, MEMBER, CURRENT))
                .thenReturn(new DashboardMetricSummary(4L, null));
        when(activities.summarizeCreatedFollowUps(TENANT, MEMBER, PREVIOUS))
                .thenReturn(new DashboardMetricSummary(2L, null));

        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        when(customers.access(any(), any(), any())).thenThrow(new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable"));
        var values = support(customers, mock(DashboardVisibilityNarrower.class),
                mock(EffectiveFieldAccessResolver.class), mock(CustomerRepository.class), organization, activities)
                .summarize(CALLER, TENANT, DashboardDataScope.SELF, CURRENT, PREVIOUS);

        var followUp = values.getLast();
        assertEquals("NEW_FOLLOW_UP", followUp.metric());
        assertEquals("AVAILABLE", followUp.status());
        assertEquals(new BigDecimal("4"), followUp.value());
        assertEquals(new BigDecimal("2"), followUp.previousValue());
        assertEquals(new BigDecimal("100.00"), followUp.comparisonRate());
    }

    @Test
    void summarizesConvertedCustomersOnlyWhenTheConversionFieldsAreReadable() {
        CustomerAccessResolver customers = mock(CustomerAccessResolver.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        CustomerVisibility visibility = mock(CustomerVisibility.class);
        CustomerAccessResolver.CustomerAccess source =
                new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER, visibility);
        when(customers.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                .thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.DEPARTMENT)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.CUSTOMER)).thenReturn(Map.of(
                new FieldCode(CustomerFieldCatalog.DEAL_STATUS), readable(),
                new FieldCode(CustomerFieldCatalog.STATUS), readable()));
        when(customerRepository.summarizeConverted(TENANT, visibility, CURRENT))
                .thenReturn(new DashboardMetricSummary(4L, null));

        DashboardMetricSummary result = support(customers, narrower, fields, customerRepository)
                .summarizeConvertedCustomer(CALLER, TENANT, DashboardDataScope.DEPARTMENT, CURRENT);

        assertEquals(4L, result.count());
    }

    private DashboardSalesBriefQuerySupport support(
            CustomerAccessResolver customers,
            DashboardVisibilityNarrower narrower,
            EffectiveFieldAccessResolver fields,
            CustomerRepository customerRepository) {
        ContactAccessResolver contacts = mock(ContactAccessResolver.class);
        LeadAccessResolver leads = mock(LeadAccessResolver.class);
        OpportunityAccessResolver opportunities = mock(OpportunityAccessResolver.class);
        ContractAccessResolver contracts = mock(ContractAccessResolver.class);
        ReceivableAccessResolver receivables = mock(ReceivableAccessResolver.class);
        NonRetryableApplicationException denied = new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
        when(contacts.access(any(), any(), any())).thenThrow(denied);
        when(leads.access(any(), any(), any())).thenThrow(denied);
        when(opportunities.access(any(), any(), any())).thenThrow(denied);
        when(contracts.access(any(), any(), any())).thenThrow(denied);
        when(receivables.access(any(), any(), any())).thenThrow(denied);
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        when(organization.requireActiveMembership(any(), eq(AuthorityCatalog.ACTIVITIES_READ)))
                .thenThrow(denied);
        return new DashboardSalesBriefQuerySupport(
                customers, contacts, leads, opportunities, contracts, receivables,
                organization, narrower, fields, customerRepository,
                mock(ContactRepository.class),
                mock(LeadRepository.class), mock(OpportunityRepository.class),
                mock(ContractRepository.class), mock(ReceivableRepository.class),
                mock(ActivityRepository.class));
    }

    private DashboardSalesBriefQuerySupport support(
            CustomerAccessResolver customers,
            DashboardVisibilityNarrower narrower,
            EffectiveFieldAccessResolver fields,
            CustomerRepository customerRepository,
            OrganizationAccessGuard organization,
            ActivityRepository activities) {
        ContactAccessResolver contacts = mock(ContactAccessResolver.class);
        LeadAccessResolver leads = mock(LeadAccessResolver.class);
        OpportunityAccessResolver opportunities = mock(OpportunityAccessResolver.class);
        ContractAccessResolver contracts = mock(ContractAccessResolver.class);
        ReceivableAccessResolver receivables = mock(ReceivableAccessResolver.class);
        NonRetryableApplicationException denied = new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
        when(contacts.access(any(), any(), any())).thenThrow(denied);
        when(leads.access(any(), any(), any())).thenThrow(denied);
        when(opportunities.access(any(), any(), any())).thenThrow(denied);
        when(contracts.access(any(), any(), any())).thenThrow(denied);
        when(receivables.access(any(), any(), any())).thenThrow(denied);
        return new DashboardSalesBriefQuerySupport(
                customers, contacts, leads, opportunities, contracts, receivables,
                organization, narrower, fields, customerRepository, mock(ContactRepository.class),
                mock(LeadRepository.class), mock(OpportunityRepository.class),
                mock(ContractRepository.class), mock(ReceivableRepository.class), activities);
    }

    private EffectiveFieldAccess readable() {
        return new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE);
    }
}
