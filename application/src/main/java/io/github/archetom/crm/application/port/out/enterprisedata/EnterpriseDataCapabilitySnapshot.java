package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.policy.RegisteredIdentifierCatalog;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;

import java.util.Set;

/** Safe, tenant-resolved query capability with no provider account diagnostics. */
public record EnterpriseDataCapabilitySnapshot(
        boolean ready,
        Set<EnterpriseQueryMode> queryModes,
        Set<LegalJurisdiction> legalJurisdictions,
        Set<RegisteredIdentifierType> identifierTypes,
        int maxCandidates
) {

    public static final int CORE_MAX_CANDIDATES = 20;

    public EnterpriseDataCapabilitySnapshot {
        queryModes = queryModes == null ? Set.of() : Set.copyOf(queryModes);
        legalJurisdictions = legalJurisdictions == null ? Set.of() : Set.copyOf(legalJurisdictions);
        identifierTypes = identifierTypes == null ? Set.of() : Set.copyOf(identifierTypes);
        if (!ready) {
            if (!queryModes.isEmpty() || !legalJurisdictions.isEmpty()
                    || !identifierTypes.isEmpty() || maxCandidates != 0) {
                throw new IllegalArgumentException("unavailable capability must not advertise operations");
            }
        } else {
            if (queryModes.isEmpty() || identifierTypes.isEmpty()
                    || maxCandidates < 1 || maxCandidates > CORE_MAX_CANDIDATES) {
                throw new IllegalArgumentException("enterprise data capability is invalid");
            }
            if (queryModes.contains(EnterpriseQueryMode.REGISTERED_NAME_SEARCH)
                    && legalJurisdictions.isEmpty()) {
                throw new IllegalArgumentException("name search requires governed legal jurisdictions");
            }
            if (identifierTypes.stream().anyMatch(type -> !RegisteredIdentifierCatalog.supports(
                    type.identityScope(), type.registryScheme()))) {
                throw new IllegalArgumentException("capability contains an unsupported identifier type");
            }
        }
    }

    public static EnterpriseDataCapabilitySnapshot unavailable() {
        return new EnterpriseDataCapabilitySnapshot(false, Set.of(), Set.of(), Set.of(), 0);
    }
}
