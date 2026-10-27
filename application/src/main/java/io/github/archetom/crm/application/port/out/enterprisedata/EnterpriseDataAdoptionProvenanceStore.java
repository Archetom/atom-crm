package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Append-only, value-free adoption evidence; ordinary Customer reads never expose this port. */
public interface EnterpriseDataAdoptionProvenanceStore {
    void append(Adoption adoption);

    int purgeExpired(TenantId tenantId, Instant now, int limit);

    record Adoption(
            UUID operationId,
            TenantId tenantId,
            CustomerId customerId,
            MemberId actorId,
            long primaryIdentityAssociationId,
            EnterpriseDataSourceKind sourceKind,
            String sourceBindingRef,
            String adapterEvidenceRef,
            String policyVersion,
            String attributionVersion,
            String mappingVersion,
            Instant observedAt,
            Instant expiresAt,
            long expectedCustomerVersion,
            long committedCustomerVersion,
            Instant deleteAfter,
            List<Selection> selections
    ) {
        public Adoption {
            Objects.requireNonNull(operationId, "adoption operation must not be null");
            Objects.requireNonNull(tenantId, "adoption tenant must not be null");
            Objects.requireNonNull(customerId, "adoption customer must not be null");
            Objects.requireNonNull(actorId, "adoption actor must not be null");
            Objects.requireNonNull(sourceKind, "adoption source kind must not be null");
            Objects.requireNonNull(observedAt, "adoption observed time must not be null");
            Objects.requireNonNull(expiresAt, "adoption expiry must not be null");
            Objects.requireNonNull(deleteAfter, "adoption delete deadline must not be null");
            selections = selections == null ? List.of() : List.copyOf(selections);
            if (primaryIdentityAssociationId < 1 || expectedCustomerVersion < 0
                    || committedCustomerVersion < expectedCustomerVersion
                    || !expiresAt.isAfter(observedAt) || !deleteAfter.isAfter(observedAt)
                    || selections.isEmpty() || selections.size() > 20) {
                throw new IllegalArgumentException("enterprise data adoption evidence is invalid");
            }
        }
    }

    record Selection(EnterpriseDataAdoptableFact fact, String targetLocator, boolean changed) {
        public Selection {
            Objects.requireNonNull(fact, "adoption fact must not be null");
            if (targetLocator == null || !targetLocator.matches("[A-Za-z0-9:_-]{1,128}")) {
                throw new IllegalArgumentException("adoption target locator is invalid");
            }
        }
    }
}
