package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractCustomerSummary;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceCustomerSummary;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityCustomerSummary;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivablePlanCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitCustomerSummary;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerBusinessOverviewServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final CustomerId CUSTOMER = new CustomerId(11L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(AuthorityCatalog.CUSTOMERS_READ));

    @Test
    void independentlyAuthorizedCardsKeepTheirFixedOrderAndOpaqueUnavailableState() {
        Fixture fixture = new Fixture();
        fixture.visibleCustomer();
        fixture.customerAccess();
        fixture.sources();
        fixture.fields();
        when(fixture.contactAccess.access(CALLER, AuthorityCatalog.CONTACTS_READ,
                ContactAccessMode.READ))
                .thenThrow(denied());
        when(fixture.opportunities.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new OpportunityCustomerSummary(2L, new BigDecimal("10.10")));
        when(fixture.contracts.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new ContractCustomerSummary(3L, new BigDecimal("20.20")));
        when(fixture.invoices.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new InvoiceCustomerSummary(4L, new BigDecimal("30.30")));
        when(fixture.receivables.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new ReceivableCustomerSummary(5L, new BigDecimal("40.40")));
        when(fixture.plans.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new ReceivablePlanCustomerSummary(6L, new BigDecimal("50.50")));
        when(fixture.visits.summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(new ReturnVisitCustomerSummary(7L));

        var result = fixture.service().get(CALLER, CUSTOMER.getValue());

        assertTrue(result.isSuccess());
        assertEquals(CUSTOMER.getValue(), result.getData().customerId());
        assertEquals(Lists.SOURCES, result.getData().cards().stream()
                .map(card -> card.source()).toList());
        var unavailable = result.getData().cards().getFirst();
        assertEquals("UNAVAILABLE", unavailable.status());
        assertNull(unavailable.count());
        assertNull(unavailable.amount());
        assertEquals("AVAILABLE", result.getData().cards().get(1).status());
        assertEquals(2L, result.getData().cards().get(1).count());
        assertEquals(new BigDecimal("10.10"), result.getData().cards().get(1).amount());
        assertEquals(7L, result.getData().cards().getLast().count());
        assertNull(result.getData().cards().getLast().amount());
        verify(fixture.contacts, never()).summarizeByCustomer(eq(TENANT), eq(CUSTOMER), any());
    }

    @Test
    void hiddenCustomerFailsBeforeAnySourceAccessOrAggregate() {
        Fixture fixture = new Fixture();
        fixture.customerAccess();
        when(fixture.customers.findVisibleById(eq(TENANT), eq(CUSTOMER), any()))
                .thenReturn(Optional.empty());

        var result = fixture.service().get(CALLER, CUSTOMER.getValue());

        assertFalse(result.isSuccess());
        verify(fixture.contactAccess, never()).access(any(), any(), any());
        verify(fixture.contacts, never()).summarizeByCustomer(any(), any(), any());
    }

    @Test
    void resolvesCustomerAccessOnlyAfterTheReadTransactionStarts() {
        Fixture fixture = new Fixture();
        TestTransactionManager transactions = new TestTransactionManager();
        fixture.visibleCustomer();
        fixture.deniedSources();
        when(fixture.customerAccess.access(CALLER, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ)).thenAnswer(invocation -> {
            assertTrue(transactions.active());
            return new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER,
                    new CustomerVisibility(DataScope.TENANT, MEMBER, Set.of(),
                            CustomerAccessMode.READ));
        });

        var result = fixture.service(transactions).get(CALLER, CUSTOMER.getValue());

        assertTrue(result.isSuccess());
        assertFalse(transactions.active());
    }

    private static NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
    }

    private static final class Lists {
        private static final java.util.List<String> SOURCES = java.util.List.of(
                "CONTACT", "OPEN_OPPORTUNITY", "ACTIVE_CONTRACT", "PENDING_INVOICE",
                "ACTIVE_RECEIVABLE", "ACTIVE_RECEIVABLE_PLAN", "RETURN_VISIT");
    }

    private static final class Fixture {
        private final CustomerAccessResolver customerAccess = mock(CustomerAccessResolver.class);
        private final ContactAccessResolver contactAccess = mock(ContactAccessResolver.class);
        private final OpportunityAccessResolver opportunityAccess = mock(OpportunityAccessResolver.class);
        private final ContractAccessResolver contractAccess = mock(ContractAccessResolver.class);
        private final InvoiceAccessResolver invoiceAccess = mock(InvoiceAccessResolver.class);
        private final ReceivableAccessResolver receivableAccess = mock(ReceivableAccessResolver.class);
        private final ReturnVisitAccessResolver returnVisitAccess = mock(ReturnVisitAccessResolver.class);
        private final EffectiveFieldAccessResolver fieldAccess = mock(EffectiveFieldAccessResolver.class);
        private final CustomerRepository customers = mock(CustomerRepository.class);
        private final ContactRepository contacts = mock(ContactRepository.class);
        private final OpportunityRepository opportunities = mock(OpportunityRepository.class);
        private final ContractRepository contracts = mock(ContractRepository.class);
        private final InvoiceRepository invoices = mock(InvoiceRepository.class);
        private final ReceivableRepository receivables = mock(ReceivableRepository.class);
        private final ReceivablePlanRepository plans = mock(ReceivablePlanRepository.class);
        private final ReturnVisitRepository visits = mock(ReturnVisitRepository.class);

        private void customerAccess() {
            when(customerAccess.access(CALLER, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ))
                    .thenReturn(new CustomerAccessResolver.CustomerAccess(TENANT, MEMBER,
                            new CustomerVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    CustomerAccessMode.READ)));
        }

        private void visibleCustomer() {
            when(customers.findVisibleById(eq(TENANT), eq(CUSTOMER), any()))
                    .thenReturn(Optional.of(mock(Customer.class)));
        }

        private void sources() {
            when(contactAccess.access(CALLER, AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ))
                    .thenReturn(new ContactAccessResolver.ContactAccess(TENANT, MEMBER,
                            new ContactVisibility(DataScope.TENANT, MEMBER, Set.of(), ContactAccessMode.READ)));
            when(opportunityAccess.access(CALLER, AuthorityCatalog.OPPORTUNITIES_READ,
                    OpportunityAccessMode.READ)).thenReturn(new OpportunityAccessResolver.OpportunityAccess(
                    TENANT, MEMBER, new OpportunityVisibility(DataScope.TENANT, MEMBER, Set.of(),
                    OpportunityAccessMode.READ)));
            when(contractAccess.access(CALLER, AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ))
                    .thenReturn(new ContractAccessResolver.ContractAccess(TENANT, MEMBER,
                            new ContractVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    ContractAccessMode.READ)));
            when(invoiceAccess.access(CALLER, AuthorityCatalog.INVOICES_READ, InvoiceAccessMode.READ))
                    .thenReturn(new InvoiceAccessResolver.InvoiceAccess(TENANT, MEMBER,
                            new InvoiceVisibility(DataScope.TENANT, MEMBER, Set.of(),
                                    InvoiceAccessMode.READ)));
            when(receivableAccess.access(CALLER, AuthorityCatalog.RECEIVABLES_READ,
                    ReceivableAccessMode.READ)).thenReturn(receivableSource());
            when(receivableAccess.access(CALLER, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                    ReceivableAccessMode.READ)).thenReturn(receivableSource());
            when(returnVisitAccess.access(CALLER, AuthorityCatalog.RETURN_VISITS_READ,
                    ReturnVisitAccessMode.READ)).thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(
                    TENANT, MEMBER, new ReturnVisitVisibility(DataScope.TENANT, MEMBER, Set.of(),
                    ReturnVisitAccessMode.READ)));
        }

        private void deniedSources() {
            when(contactAccess.access(any(), any(), any())).thenThrow(denied());
            when(opportunityAccess.access(any(), any(), any())).thenThrow(denied());
            when(contractAccess.access(any(), any(), any())).thenThrow(denied());
            when(invoiceAccess.access(any(), any(), any())).thenThrow(denied());
            when(receivableAccess.access(any(), any(), any())).thenThrow(denied());
            when(returnVisitAccess.access(any(), any(), any())).thenThrow(denied());
        }

        private ReceivableAccessResolver.ReceivableAccess receivableSource() {
            return new ReceivableAccessResolver.ReceivableAccess(TENANT, MEMBER,
                    new ReceivableVisibility(DataScope.TENANT, MEMBER, Set.of(),
                            ReceivableAccessMode.READ));
        }

        private void fields() {
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.CONTACT)).thenReturn(read(
                    ContactFieldCatalog.CUSTOMER, ContactFieldCatalog.STATUS));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.OPPORTUNITY)).thenReturn(read(
                    OpportunityFieldCatalog.CUSTOMER, OpportunityFieldCatalog.STATUS,
                    OpportunityFieldCatalog.OUTCOME, OpportunityFieldCatalog.ESTIMATED_AMOUNT));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.CONTRACT)).thenReturn(read(
                    ContractFieldCatalog.CUSTOMER, ContractFieldCatalog.STATUS,
                    ContractFieldCatalog.TOTAL_AMOUNT));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.INVOICE)).thenReturn(read(
                    InvoiceFieldCatalog.CUSTOMER, InvoiceFieldCatalog.STATUS, InvoiceFieldCatalog.AMOUNT));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.RECEIVABLE)).thenReturn(read(
                    ReceivableFieldCatalog.CUSTOMER, ReceivableFieldCatalog.STATUS,
                    ReceivableFieldCatalog.AMOUNT));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.RECEIVABLE_PLAN)).thenReturn(read(
                    ReceivablePlanFieldCatalog.CUSTOMER, ReceivablePlanFieldCatalog.STATUS,
                    ReceivablePlanFieldCatalog.PLANNED_AMOUNT));
            when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.RETURN_VISIT)).thenReturn(read(
                    ReturnVisitFieldCatalog.CUSTOMER, ReturnVisitFieldCatalog.STATUS));
        }

        private Map<FieldCode, EffectiveFieldAccess> read(String... codes) {
            Map<FieldCode, EffectiveFieldAccess> values = new HashMap<>();
            for (String code : codes) {
                values.put(new FieldCode(code), new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.NONE));
            }
            return Map.copyOf(values);
        }

        private CustomerBusinessOverviewServiceImpl service() {
            return service(new TestTransactionManager());
        }

        private CustomerBusinessOverviewServiceImpl service(
                PlatformTransactionManager transactions) {
            return new CustomerBusinessOverviewServiceImpl(customerAccess, contactAccess,
                    opportunityAccess, contractAccess, invoiceAccess, receivableAccess,
                    returnVisitAccess, fieldAccess, customers, contacts, opportunities, contracts,
                    invoices, receivables, plans, visits,
                    new QueryServiceTemplate("test-app", transactions));
        }
    }
}
