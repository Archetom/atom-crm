package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;

/** MySQL-backed current identity reservations; false never discloses the occupying Customer. */
public interface CustomerRegisteredIdentityStore {
    List<RegisteredIdentityAssociation> reserve(TenantId tenantId, CustomerId customerId,
                                                  Collection<RegisteredIdentifierKey> identities);

    record RegisteredIdentityAssociation(RegisteredIdentifierKey identity, long associationId) {
        public RegisteredIdentityAssociation {
            if (identity == null || associationId < 1) {
                throw new IllegalArgumentException("registered identity association is invalid");
            }
        }
    }
}
