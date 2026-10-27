package io.github.archetom.crm.application.service;

import io.github.archetom.crm.application.security.LeadIntakeApiKey;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.repository.LeadIntakeClientRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadIntakeAuthenticationServiceTest {

    @Test
    void resolvesTheClientOnlyWithinTheTenantCarriedByTheCredentialLocator() {
        TenantId credentialTenant = new TenantId(9L);
        TenantId otherTenant = new TenantId(10L);
        LeadIntakeApiKey.Generated key = LeadIntakeApiKey.generate(credentialTenant);
        LeadIntakeClient client = LeadIntakeClient.create(credentialTenant, new MemberId(7L),
                key.keyId(), LeadIntakeApiKey.sha256(key.secret()));
        LeadIntakeClientRepository repository = mock(LeadIntakeClientRepository.class);
        OrganizationMembershipRepository membershipRepository = activeMembership(
                credentialTenant, new MemberId(7L));
        when(repository.findActiveByKeyId(credentialTenant, key.keyId())).thenReturn(Optional.of(client));
        LeadIntakeAuthenticationService service = new LeadIntakeAuthenticationService(
                repository, membershipRepository);

        assertTrue(service.authenticate(key.value()).isPresent());

        String locatorForOtherTenant = LeadIntakeApiKey.generate(otherTenant).tenantLocator();
        assertFalse(service.authenticate(locatorForOtherTenant + "." + key.keyId() + "." + key.secret())
                .isPresent());
        verify(repository).findActiveByKeyId(credentialTenant, key.keyId());
        verify(repository).findActiveByKeyId(otherTenant, key.keyId());
    }

    @Test
    void rejectsCaseMismatchedKeyIdsEvenIfAPersistenceAdapterReturnsTheClient() {
        TenantId tenantId = new TenantId(9L);
        LeadIntakeApiKey.Generated credential = LeadIntakeApiKey.generate(tenantId);
        String canonicalKeyId = "AbCdEfGh";
        String nonCanonicalKeyId = "aBcDeFgH";
        LeadIntakeClient client = LeadIntakeClient.create(tenantId, new MemberId(7L),
                canonicalKeyId, LeadIntakeApiKey.sha256(credential.secret()));
        LeadIntakeClientRepository repository = mock(LeadIntakeClientRepository.class);
        OrganizationMembershipRepository membershipRepository = activeMembership(
                tenantId, new MemberId(7L));
        when(repository.findActiveByKeyId(tenantId, canonicalKeyId)).thenReturn(Optional.of(client));
        when(repository.findActiveByKeyId(tenantId, nonCanonicalKeyId)).thenReturn(Optional.of(client));
        LeadIntakeAuthenticationService service = new LeadIntakeAuthenticationService(
                repository, membershipRepository);

        assertEquals(canonicalKeyId, service.authenticate(credential.tenantLocator() + "."
                + canonicalKeyId + "." + credential.secret()).orElseThrow().keyId());
        assertFalse(service.authenticate(credential.tenantLocator() + "."
                + nonCanonicalKeyId + "." + credential.secret()).isPresent());
    }

    @Test
    void rejectsAValidClientOwnedByASuspendedMember() {
        TenantId tenantId = new TenantId(9L);
        MemberId ownerId = new MemberId(7L);
        LeadIntakeApiKey.Generated credential = LeadIntakeApiKey.generate(tenantId);
        LeadIntakeClient client = LeadIntakeClient.create(tenantId, ownerId,
                credential.keyId(), LeadIntakeApiKey.sha256(credential.secret()));
        LeadIntakeClientRepository repository = mock(LeadIntakeClientRepository.class);
        when(repository.findActiveByKeyId(tenantId, credential.keyId()))
                .thenReturn(Optional.of(client));
        OrganizationMembership membership = OrganizationMembership.activate(
                tenantId, ownerId, null);
        membership.suspend();
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        when(memberships.findByMember(tenantId, ownerId)).thenReturn(Optional.of(membership));

        LeadIntakeAuthenticationService service = new LeadIntakeAuthenticationService(
                repository, memberships);

        assertFalse(service.authenticate(credential.value()).isPresent());
    }

    private OrganizationMembershipRepository activeMembership(
            TenantId tenantId, MemberId memberId) {
        OrganizationMembershipRepository repository = mock(OrganizationMembershipRepository.class);
        when(repository.findByMember(tenantId, memberId)).thenReturn(Optional.of(
                OrganizationMembership.activate(tenantId, memberId, null)));
        return repository;
    }
}
