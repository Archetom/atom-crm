package io.github.archetom.crm.application.vo;

import java.util.List;

/** Provider-neutral, tenant-resolved enterprise-data query capability. */
public record EnterpriseDataCapabilityVO(
        boolean ready,
        List<String> queryModes,
        List<String> legalJurisdictions,
        List<IdentifierTypeVO> identifierTypes,
        int maxCandidates,
        String usageMode
) {
    public EnterpriseDataCapabilityVO {
        queryModes = queryModes == null ? List.of() : List.copyOf(queryModes);
        legalJurisdictions = legalJurisdictions == null ? List.of() : List.copyOf(legalJurisdictions);
        identifierTypes = identifierTypes == null ? List.of() : List.copyOf(identifierTypes);
    }

    /** Stable names from the governed identity-scope and registry-scheme catalogs. */
    public record IdentifierTypeVO(String identityScope, String registryScheme) {
    }
}
