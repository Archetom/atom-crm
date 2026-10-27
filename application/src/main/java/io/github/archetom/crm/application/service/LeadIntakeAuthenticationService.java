package io.github.archetom.crm.application.service;

import io.github.archetom.crm.application.security.LeadIntakeApiKey;
import io.github.archetom.crm.domain.repository.LeadIntakeClientRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Optional;

/** Verifies public lead-intake credentials without exposing tenant selection to the request. */
@Service
@RequiredArgsConstructor
public class LeadIntakeAuthenticationService {

    private final LeadIntakeClientRepository repository;
    private final OrganizationMembershipRepository membershipRepository;

    public Optional<VerifiedLeadIntake> authenticate(String apiKey) {
        return LeadIntakeApiKey.parse(apiKey).flatMap(parts -> repository.findActiveByKeyId(
                        parts.tenantId(), parts.keyId())
                .filter(client -> parts.tenantId().equals(client.getTenantId()))
                .filter(client -> parts.keyId().equals(client.getKeyId()))
                .filter(client -> MessageDigest.isEqual(
                        client.getApiKeyHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        parts.hash().getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .filter(client -> membershipRepository.findByMember(
                                client.getTenantId(), client.getOwnerMemberId())
                        .filter(OrganizationMembership::isActive).isPresent())
                .map(client -> new VerifiedLeadIntake(client.getKeyId(), client.getTenantId().getValue(),
                        client.getOwnerMemberId().getValue())));
    }

    public record VerifiedLeadIntake(String keyId, long tenantId, long ownerActorId) {
    }
}
