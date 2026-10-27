package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Safe enterprise-data capability without provider binding or account details. */
@Data
@Accessors(chain = true)
public class EnterpriseDataCapabilityResponse {
    private boolean ready;
    private List<String> queryModes;
    private List<String> legalJurisdictions;
    private List<IdentifierTypeResponse> identifierTypes;
    private int maxCandidates;
    private String usageMode;

    /** One governed identity-scope and registry-scheme pair. */
    @Data
    @Accessors(chain = true)
    public static class IdentifierTypeResponse {
        private String identityScope;
        private String registryScheme;
    }
}
