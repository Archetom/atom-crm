package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolMembership;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CustomerPoolAutomationServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId OWNER = new MemberId(7L);

    @Mock
    private CustomerPoolRepository poolRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerApplicationSupport customerSupport;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private CustomizationObjectLock objectLock;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CustomerPoolAutomationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new CustomerPoolAutomationServiceImpl(poolRepository, customerRepository,
                customerSupport, accessGuard, objectLock,
                new CommandServiceTemplate("test-app", transactionManager));
    }

    @Test
    void releasesBoundedCandidatesUsingTheVerifiedTenantAndSharedCustomerLock() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 9, 30);
        CustomerPoolRule rule = new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 3);
        CustomerPool pool = CustomerPool.create(TENANT, "Dormant accounts", Set.of(OWNER),
                Set.of(OWNER), Set.of(), 0, 0, true, List.of(rule));
        pool.onPersisted(new CustomerPoolId(11L), 0L, asOf.minusDays(10), asOf.minusDays(10));
        Customer customer = Customer.create(TENANT, OWNER, "Dormant account",
                null, null, null, null, null, List.of(), List.of());
        customer.onPersisted(new CustomerId(21L), 0L, asOf.minusDays(20), asOf.minusDays(10));
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE)))
                .thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(pool));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, pool.getId(), 25))
                .thenReturn(List.of());
        when(poolRepository.findByIdForUpdate(TENANT, pool.getId())).thenReturn(Optional.of(pool));
        when(poolRepository.findAutomationCandidates(
                TENANT, pool, rule, asOf.minusDays(3), 5)).thenReturn(List.of(customer.getId()));
        when(customerRepository.findById(TENANT, customer.getId())).thenReturn(Optional.of(customer));
        when(customerSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData());
        assertEquals(CustomerStatus.IN_POOL, customer.getStatus());
        verify(objectLock).acquire(TENANT, BusinessObjectType.CUSTOMER);
        verify(customerRepository).save(TENANT, customer, List.of());
        ArgumentCaptor<CustomerPoolMembership> membership =
                ArgumentCaptor.forClass(CustomerPoolMembership.class);
        verify(poolRepository).addMembership(membership.capture());
        assertEquals(TENANT, membership.getValue().tenantId());
        assertEquals(pool.getId(), membership.getValue().poolId());
        assertEquals(customer.getId(), membership.getValue().customerId());
        assertEquals(OWNER, membership.getValue().previousOwnerMemberId());
        assertEquals(asOf, membership.getValue().enteredTime());
        verify(poolRepository).recordRelease(
                TENANT, pool.getId(), customer.getId(), OWNER, asOf);
    }

    @Test
    void locksMultiplePoolsInIdentityOrder() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 10, 0);
        CustomerPoolRule rule = new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 3);
        CustomerPool first = pool(11L, "First pool", rule, asOf);
        CustomerPool second = pool(22L, "Second pool", rule, asOf);
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE)))
                .thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(second, first));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, second.getId(), 25))
                .thenReturn(List.of());
        when(poolRepository.findByIdForUpdate(TENANT, first.getId())).thenReturn(Optional.of(first));
        when(poolRepository.findByIdForUpdate(TENANT, second.getId())).thenReturn(Optional.of(second));
        when(poolRepository.findAutomationCandidates(
                TENANT, first, rule, asOf.minusDays(3), 5)).thenReturn(List.of());
        when(poolRepository.findAutomationCandidates(
                TENANT, second, rule, asOf.minusDays(3), 5)).thenReturn(List.of());
        when(customerSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        InOrder order = inOrder(poolRepository);
        order.verify(poolRepository).findByIdForUpdate(TENANT, first.getId());
        order.verify(poolRepository).findByIdForUpdate(TENANT, second.getId());
    }

    @Test
    void scansLaterPoolPagesWhenEarlierPoolsHaveNoCandidates() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 11, 0);
        CustomerPoolRule rule = new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 3);
        CustomerPool first = pool(11L, "Earlier pool", rule, asOf);
        CustomerPool second = pool(22L, "Later pool", rule, asOf);
        Customer customer = Customer.create(TENANT, OWNER, "Later dormant account",
                null, null, null, null, null, List.of(), List.of());
        customer.onPersisted(new CustomerId(31L), 0L, asOf.minusDays(20), asOf.minusDays(10));
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE)))
                .thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(first));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, first.getId(), 25))
                .thenReturn(List.of(second));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, second.getId(), 25))
                .thenReturn(List.of());
        when(poolRepository.findByIdForUpdate(TENANT, first.getId())).thenReturn(Optional.of(first));
        when(poolRepository.findByIdForUpdate(TENANT, second.getId())).thenReturn(Optional.of(second));
        when(poolRepository.findAutomationCandidates(
                TENANT, first, rule, asOf.minusDays(3), 5)).thenReturn(List.of());
        when(poolRepository.findAutomationCandidates(
                TENANT, second, rule, asOf.minusDays(3), 5)).thenReturn(List.of(customer.getId()));
        when(customerRepository.findById(TENANT, customer.getId())).thenReturn(Optional.of(customer));
        when(customerSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData());
        verify(poolRepository).findByIdForUpdate(TENANT, second.getId());
        verify(customerRepository).save(TENANT, customer, List.of());
    }

    @Test
    void resumesAfterPersistedCursorWhenAnEarlierPoolFillsTheCustomerLimit() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0);
        CustomerPoolRule rule = new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 3);
        CustomerPool first = pool(11L, "Always busy pool", rule, asOf);
        CustomerPool second = pool(22L, "Later pool", rule, asOf);
        Customer firstCustomer = customer(41L, "First dormant account", asOf);
        Customer secondCustomer = customer(42L, "Second dormant account", asOf);
        when(accessGuard.requireActiveMembership(any(),
                eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE))).thenReturn(TENANT);
        when(poolRepository.lockAutomationCursor(TENANT))
                .thenReturn(null, first.getId());
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(first, second));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, first.getId(), 25))
                .thenReturn(List.of(second));
        when(poolRepository.findByIdForUpdate(TENANT, first.getId())).thenReturn(Optional.of(first));
        when(poolRepository.findByIdForUpdate(TENANT, second.getId())).thenReturn(Optional.of(second));
        when(poolRepository.findAutomationCandidates(
                TENANT, first, rule, asOf.minusDays(3), 1)).thenReturn(List.of(firstCustomer.getId()));
        when(poolRepository.findAutomationCandidates(
                TENANT, second, rule, asOf.minusDays(3), 1)).thenReturn(List.of(secondCustomer.getId()));
        when(customerRepository.findById(TENANT, firstCustomer.getId()))
                .thenReturn(Optional.of(firstCustomer));
        when(customerRepository.findById(TENANT, secondCustomer.getId()))
                .thenReturn(Optional.of(secondCustomer));
        when(customerSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> firstRun = service.run(caller(), asOf, 1);
        Result<Integer> secondRun = service.run(caller(), asOf, 1);

        assertTrue(firstRun.isSuccess());
        assertTrue(secondRun.isSuccess());
        assertEquals(1, firstRun.getData());
        assertEquals(1, secondRun.getData());
        verify(poolRepository).saveAutomationCursor(TENANT, first.getId());
        verify(poolRepository).saveAutomationCursor(TENANT, second.getId());
        verify(customerRepository).save(TENANT, firstCustomer, List.of());
        verify(customerRepository).save(TENANT, secondCustomer, List.of());
    }

    @Test
    void scansAndLocksAtMostFiftyPoolsWhenNoPoolHasCandidates() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 13, 0);
        CustomerPoolRule rule = new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 3);
        List<CustomerPool> pools = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            CustomerPool pool = pool(index, "Pool " + index, rule, asOf);
            pools.add(pool);
            when(poolRepository.findByIdForUpdate(TENANT, pool.getId()))
                    .thenReturn(Optional.of(pool));
        }
        when(accessGuard.requireActiveMembership(any(),
                eq(AuthorityCatalog.CUSTOMER_POOLS_MANAGE))).thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.copyOf(pools.subList(0, 25)));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, pools.get(24).getId(), 25))
                .thenReturn(List.copyOf(pools.subList(25, 50)));
        when(poolRepository.findAutomationCandidates(
                eq(TENANT), any(CustomerPool.class), eq(rule), eq(asOf.minusDays(3)), eq(10)))
                .thenReturn(List.of());
        when(customerSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 10);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getData());
        verify(poolRepository, times(50)).findByIdForUpdate(
                eq(TENANT), any(CustomerPoolId.class));
        verify(poolRepository).saveAutomationCursor(TENANT, pools.get(49).getId());
        verify(poolRepository, never()).findAutomaticReleaseAfter(
                TENANT, pools.get(49).getId(), 25);
    }

    private CustomerPool pool(
            long id, String name, CustomerPoolRule rule, LocalDateTime asOf) {
        CustomerPool pool = CustomerPool.create(TENANT, name, Set.of(OWNER),
                Set.of(OWNER), Set.of(), 0, 0, true, List.of(rule));
        pool.onPersisted(new CustomerPoolId(id), 0L, asOf.minusDays(10), asOf.minusDays(10));
        return pool;
    }

    private Customer customer(long id, String name, LocalDateTime asOf) {
        Customer customer = Customer.create(TENANT, OWNER, name,
                null, null, null, null, null, List.of(), List.of());
        customer.onPersisted(new CustomerId(id), 0L,
                asOf.minusDays(20), asOf.minusDays(10));
        return customer;
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(OWNER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.CUSTOMER_POOLS_MANAGE));
    }
}
