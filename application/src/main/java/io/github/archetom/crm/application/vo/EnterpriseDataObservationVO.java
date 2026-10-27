package io.github.archetom.crm.application.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fixed public projection of one fresh allowlisted enterprise-data observation. */
public record EnterpriseDataObservationVO(
        IdentifierVO primaryIdentifier,
        List<IdentifierVO> identifiers,
        String legalJurisdiction,
        String registeredName,
        String entityStatus,
        String formationDateKind,
        LocalDate formationDate,
        AddressVO registeredAddress,
        String sourceKind,
        boolean sourceDeclaredPartial,
        AttributionVO attribution,
        Instant observedAt,
        Instant sourceUpdatedAt,
        String sourceRevision,
        Instant expiresAt,
        String usageMode
) {
    public EnterpriseDataObservationVO {
        identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
    }

    @Override
    public String toString() {
        return "EnterpriseDataObservationVO[redacted]";
    }

    /** Scheme-validated identifier projection, including Companies House numbers. */
    public record IdentifierVO(
            String identityScope,
            String registryScheme,
            String canonicalValue,
            String identifierStanding
    ) {
        @Override
        public String toString() {
            return "IdentifierVO[redacted]";
        }
    }

    public record AddressVO(
            String countryCode,
            String region,
            String locality,
            String addressLine,
            String postalCode
    ) {
        @Override
        public String toString() {
            return "AddressVO[redacted]";
        }
    }

    public record AttributionVO(
            String context,
            String sourceName,
            String informationUrl,
            String disclaimer
    ) {
    }
}
