package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fresh allowlisted registration observation with no raw source payload. */
@Data
@Accessors(chain = true)
public class EnterpriseDataObservationResponse {
    private IdentifierResponse primaryIdentifier;
    private List<IdentifierResponse> identifiers;
    private String legalJurisdiction;
    private String registeredName;
    private String entityStatus;
    private String formationDateKind;
    private LocalDate formationDate;
    private AddressResponse registeredAddress;
    private String sourceKind;
    private boolean sourceDeclaredPartial;
    private AttributionResponse attribution;
    private Instant observedAt;
    private Instant sourceUpdatedAt;
    private String sourceRevision;
    private Instant expiresAt;
    private String usageMode;

    @Override
    public String toString() {
        return "EnterpriseDataObservationResponse[redacted]";
    }

    /** One scheme-validated registered identifier. */
    @Data
    @Accessors(chain = true)
    public static class IdentifierResponse {
        private String identityScope;
        private String registryScheme;
        private String canonicalValue;
        private String identifierStanding;

        @Override
        public String toString() {
            return "IdentifierResponse[redacted]";
        }
    }

    @Data
    @Accessors(chain = true)
    public static class AddressResponse {
        private String countryCode;
        private String region;
        private String locality;
        private String addressLine;
        private String postalCode;

        @Override
        public String toString() {
            return "AddressResponse[redacted]";
        }
    }

    @Data
    @Accessors(chain = true)
    public static class AttributionResponse {
        private String context;
        private String sourceName;
        private String informationUrl;
        private String disclaimer;
    }
}
