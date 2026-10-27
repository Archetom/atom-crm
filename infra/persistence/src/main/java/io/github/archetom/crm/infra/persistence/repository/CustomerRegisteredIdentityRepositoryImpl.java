package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.enterprisedata.CustomerRegisteredIdentityStore;
import io.github.archetom.crm.domain.exception.RegisteredIdentityAlreadyClaimedException;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerRegisteredIdentityMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerRegisteredIdentityPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Uses the tenant/scheme/value unique key as the only current-identity arbitration authority. */
@Repository
@RequiredArgsConstructor
public class CustomerRegisteredIdentityRepositoryImpl implements CustomerRegisteredIdentityStore {
    private final CustomerRegisteredIdentityMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RegisteredIdentityAssociation> reserve(
            TenantId tenantId, CustomerId customerId,
            Collection<RegisteredIdentifierKey> identities) {
        if (tenantId == null || customerId == null || identities == null || identities.isEmpty()) {
            throw new IllegalArgumentException("registered identity reservation is invalid");
        }
        List<RegisteredIdentifierKey> ordered = new ArrayList<>(identities);
        if (ordered.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("registered identity reservation is invalid");
        }
        ordered.sort(Comparator.comparing((RegisteredIdentifierKey value) -> value.getIdentityScope().name())
                .thenComparing(value -> value.getRegistryScheme().name())
                .thenComparing(RegisteredIdentifierKey::getCanonicalValue));
        if (ordered.size() > 20 || new LinkedHashSet<>(ordered).size() != ordered.size()) {
            throw new IllegalArgumentException("registered identity reservation is invalid");
        }
        List<RegisteredIdentityAssociation> associations = new ArrayList<>(ordered.size());
        for (RegisteredIdentifierKey identity : ordered) {
            associations.add(new RegisteredIdentityAssociation(identity, reserveOne(tenantId, customerId, identity)));
        }
        return List.copyOf(associations);
    }

    private long reserveOne(TenantId tenantId, CustomerId customerId, RegisteredIdentifierKey identity) {
        CustomerRegisteredIdentityPO candidate = new CustomerRegisteredIdentityPO();
        candidate.setTenantId(tenantId.getValue());
        candidate.setCustomerId(customerId.getValue());
        candidate.setIdentityScopeCode(identity.getIdentityScope().name());
        candidate.setRegistrySchemeCode(identity.getRegistryScheme().name());
        candidate.setCanonicalValue(identity.getCanonicalValue());
        try {
            if (mapper.insert(candidate) != 1 || candidate.getId() == null) {
                throw new IllegalStateException("registered identity insert did not produce an association");
            }
            return candidate.getId();
        } catch (DuplicateKeyException exception) {
            CustomerRegisteredIdentityPO current = mapper.selectCurrentForUpdate(tenantId.getValue(),
                    identity.getIdentityScope().name(), identity.getRegistryScheme().name(),
                    identity.getCanonicalValue());
            if (current != null && customerId.getValue().equals(current.getCustomerId())
                    && current.getId() != null) {
                return current.getId();
            }
            throw new RegisteredIdentityAlreadyClaimedException();
        }
    }
}
