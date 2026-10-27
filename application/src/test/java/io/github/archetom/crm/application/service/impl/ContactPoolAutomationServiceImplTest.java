package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolMembership;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContactPoolRepository;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
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
class ContactPoolAutomationServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId OWNER = new MemberId(7L);

    @Mock
    private ContactPoolRepository poolRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private ContactApplicationSupport contactSupport;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private CustomizationObjectLock objectLock;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ContactPoolAutomationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ContactPoolAutomationServiceImpl(poolRepository, contactRepository,
                contactSupport, accessGuard, objectLock,
                new CommandServiceTemplate("test-app", transactionManager));
    }

    @Test
    void releasesBoundedCandidatesUsingTheVerifiedTenantAndSharedContactLock() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 9, 30);
        ContactPoolRule rule = new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 3);
        ContactPool pool = ContactPool.create(TENANT, "Dormant accounts", Set.of(OWNER),
                Set.of(OWNER), Set.of(), 0, 0, true, List.of(rule));
        pool.onPersisted(new ContactPoolId(11L), 0L, asOf.minusDays(10), asOf.minusDays(10));
        Contact contact = Contact.create(TENANT, OWNER, "Dormant account",
                null, null, null, null, null, List.of(), List.of());
        contact.onPersisted(new ContactId(21L), 0L, asOf.minusDays(20), asOf.minusDays(10));
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CONTACT_POOLS_MANAGE)))
                .thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(pool));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, pool.getId(), 25))
                .thenReturn(List.of());
        when(poolRepository.findByIdForUpdate(TENANT, pool.getId())).thenReturn(Optional.of(pool));
        when(poolRepository.findAutomationCandidates(
                TENANT, pool, rule, asOf.minusDays(3), 5)).thenReturn(List.of(contact.getId()));
        when(contactRepository.findById(TENANT, contact.getId())).thenReturn(Optional.of(contact));
        when(contactSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData());
        assertEquals(ContactStatus.IN_POOL, contact.getStatus());
        verify(objectLock).acquire(TENANT, BusinessObjectType.CONTACT);
        verify(contactRepository).save(TENANT, contact, List.of());
        ArgumentCaptor<ContactPoolMembership> membership =
                ArgumentCaptor.forClass(ContactPoolMembership.class);
        verify(poolRepository).addMembership(membership.capture());
        assertEquals(TENANT, membership.getValue().tenantId());
        assertEquals(pool.getId(), membership.getValue().poolId());
        assertEquals(contact.getId(), membership.getValue().contactId());
        assertEquals(OWNER, membership.getValue().previousOwnerMemberId());
        assertEquals(asOf, membership.getValue().enteredTime());
    }

    @Test
    void locksMultiplePoolsInIdentityOrder() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 10, 0);
        ContactPoolRule rule = new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 3);
        ContactPool first = pool(11L, "First pool", rule, asOf);
        ContactPool second = pool(22L, "Second pool", rule, asOf);
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CONTACT_POOLS_MANAGE)))
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
        when(contactSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        InOrder order = inOrder(poolRepository);
        order.verify(poolRepository).findByIdForUpdate(TENANT, first.getId());
        order.verify(poolRepository).findByIdForUpdate(TENANT, second.getId());
    }

    @Test
    void scansLaterPoolPagesWhenEarlierPoolsHaveNoCandidates() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 11, 0);
        ContactPoolRule rule = new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 3);
        ContactPool first = pool(11L, "Earlier pool", rule, asOf);
        ContactPool second = pool(22L, "Later pool", rule, asOf);
        Contact contact = Contact.create(TENANT, OWNER, "Later dormant account",
                null, null, null, null, null, List.of(), List.of());
        contact.onPersisted(new ContactId(31L), 0L, asOf.minusDays(20), asOf.minusDays(10));
        when(accessGuard.requireActiveMembership(any(),
                org.mockito.ArgumentMatchers.eq(AuthorityCatalog.CONTACT_POOLS_MANAGE)))
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
                TENANT, second, rule, asOf.minusDays(3), 5)).thenReturn(List.of(contact.getId()));
        when(contactRepository.findById(TENANT, contact.getId())).thenReturn(Optional.of(contact));
        when(contactSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 5);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData());
        verify(poolRepository).findByIdForUpdate(TENANT, second.getId());
        verify(contactRepository).save(TENANT, contact, List.of());
    }

    @Test
    void resumesAfterPersistedCursorWhenAnEarlierPoolFillsTheContactLimit() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0);
        ContactPoolRule rule = new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 3);
        ContactPool first = pool(11L, "Always busy pool", rule, asOf);
        ContactPool second = pool(22L, "Later pool", rule, asOf);
        Contact firstContact = contact(41L, "First dormant account", asOf);
        Contact secondContact = contact(42L, "Second dormant account", asOf);
        when(accessGuard.requireActiveMembership(any(),
                eq(AuthorityCatalog.CONTACT_POOLS_MANAGE))).thenReturn(TENANT);
        when(poolRepository.lockAutomationCursor(TENANT))
                .thenReturn(null, first.getId());
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.of(first, second));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, first.getId(), 25))
                .thenReturn(List.of(second));
        when(poolRepository.findByIdForUpdate(TENANT, first.getId())).thenReturn(Optional.of(first));
        when(poolRepository.findByIdForUpdate(TENANT, second.getId())).thenReturn(Optional.of(second));
        when(poolRepository.findAutomationCandidates(
                TENANT, first, rule, asOf.minusDays(3), 1)).thenReturn(List.of(firstContact.getId()));
        when(poolRepository.findAutomationCandidates(
                TENANT, second, rule, asOf.minusDays(3), 1)).thenReturn(List.of(secondContact.getId()));
        when(contactRepository.findById(TENANT, firstContact.getId()))
                .thenReturn(Optional.of(firstContact));
        when(contactRepository.findById(TENANT, secondContact.getId()))
                .thenReturn(Optional.of(secondContact));
        when(contactSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> firstRun = service.run(caller(), asOf, 1);
        Result<Integer> secondRun = service.run(caller(), asOf, 1);

        assertTrue(firstRun.isSuccess());
        assertTrue(secondRun.isSuccess());
        assertEquals(1, firstRun.getData());
        assertEquals(1, secondRun.getData());
        verify(poolRepository).saveAutomationCursor(TENANT, first.getId());
        verify(poolRepository).saveAutomationCursor(TENANT, second.getId());
        verify(contactRepository).save(TENANT, firstContact, List.of());
        verify(contactRepository).save(TENANT, secondContact, List.of());
    }

    @Test
    void scansAndLocksAtMostFiftyPoolsWhenNoPoolHasCandidates() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 13, 0);
        ContactPoolRule rule = new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 3);
        List<ContactPool> pools = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            ContactPool pool = pool(index, "Pool " + index, rule, asOf);
            pools.add(pool);
            when(poolRepository.findByIdForUpdate(TENANT, pool.getId()))
                    .thenReturn(Optional.of(pool));
        }
        when(accessGuard.requireActiveMembership(any(),
                eq(AuthorityCatalog.CONTACT_POOLS_MANAGE))).thenReturn(TENANT);
        when(poolRepository.findAutomaticReleaseAfter(TENANT, null, 25))
                .thenReturn(List.copyOf(pools.subList(0, 25)));
        when(poolRepository.findAutomaticReleaseAfter(TENANT, pools.get(24).getId(), 25))
                .thenReturn(List.copyOf(pools.subList(25, 50)));
        when(poolRepository.findAutomationCandidates(
                eq(TENANT), any(ContactPool.class), eq(rule), eq(asOf.minusDays(3)), eq(10)))
                .thenReturn(List.of());
        when(contactSupport.fields(TENANT)).thenReturn(List.of());

        Result<Integer> result = service.run(caller(), asOf, 10);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getData());
        verify(poolRepository, times(50)).findByIdForUpdate(
                eq(TENANT), any(ContactPoolId.class));
        verify(poolRepository).saveAutomationCursor(TENANT, pools.get(49).getId());
        verify(poolRepository, never()).findAutomaticReleaseAfter(
                TENANT, pools.get(49).getId(), 25);
    }

    private ContactPool pool(
            long id, String name, ContactPoolRule rule, LocalDateTime asOf) {
        ContactPool pool = ContactPool.create(TENANT, name, Set.of(OWNER),
                Set.of(OWNER), Set.of(), 0, 0, true, List.of(rule));
        pool.onPersisted(new ContactPoolId(id), 0L, asOf.minusDays(10), asOf.minusDays(10));
        return pool;
    }

    private Contact contact(long id, String name, LocalDateTime asOf) {
        Contact contact = Contact.create(TENANT, OWNER, name,
                null, null, null, null, null, List.of(), List.of());
        contact.onPersisted(new ContactId(id), 0L,
                asOf.minusDays(20), asOf.minusDays(10));
        return contact;
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(OWNER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.CONTACT_POOLS_MANAGE));
    }
}
