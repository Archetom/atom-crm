package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Test-scope transport seam for the B23-O-L1 mapper lane.
 *
 * <p>It intentionally has no HTTP implementation, endpoint, credential, or arbitrary filter
 * surface. Native records remain inside the external-adapter test boundary.</p>
 */
interface GleifTransport {

    GleifLeiPage findByLei(TenantId tenantId, String lei);

    GleifLeiPage searchByLegalName(TenantId tenantId, GleifLegalNameQuery query);
}

record GleifLegalNameQuery(String registeredName, String legalJurisdiction, int pageSize) {

    GleifLegalNameQuery {
        if (registeredName == null || registeredName.isBlank()
                || legalJurisdiction == null || legalJurisdiction.isBlank()
                || pageSize < 1 || pageSize > 20) {
            throw new IllegalArgumentException("GLEIF legal-name query is invalid");
        }
    }

    @Override
    public String toString() {
        return "GleifLegalNameQuery[registeredName=redacted]";
    }
}

/** A deliberately small JSON:API-like native envelope used only by offline fixtures. */
record GleifLeiPage(String jsonApiVersion, List<GleifLeiRecord> data) {

    GleifLeiPage {
        data = data == null ? null : List.copyOf(data);
    }

    @Override
    public String toString() {
        return "GleifLeiPage[redacted]";
    }
}

record GleifLeiRecord(
        String type,
        String lei,
        GleifEntity entity,
        GleifRegistration registration,
        boolean sourceDeclaredPartial,
        GleifExcludedNativeFields excludedFields
) {

    @Override
    public String toString() {
        return "GleifLeiRecord[redacted]";
    }
}

record GleifEntity(
        String legalName,
        String legalJurisdiction,
        String category,
        String status,
        LocalDate creationDate,
        GleifAddress legalAddress,
        String registrationAuthorityId,
        String registrationAuthorityEntityId
) {

    @Override
    public String toString() {
        return "GleifEntity[redacted]";
    }
}

record GleifAddress(
        List<String> addressLines,
        String city,
        String region,
        String country,
        String postalCode
) {

    GleifAddress {
        addressLines = addressLines == null ? null : List.copyOf(addressLines);
    }

    @Override
    public String toString() {
        return "GleifAddress[redacted]";
    }
}

record GleifRegistration(
        String status,
        Instant lastUpdateDate,
        Instant nextRenewalDate,
        String validationSources
) {

    @Override
    public String toString() {
        return "GleifRegistration[redacted]";
    }
}

/** Fields that must never cross the mapper boundary. */
record GleifExcludedNativeFields(
        String managingLou,
        String providerLocator,
        String relationship,
        String personName,
        String futureExtraField
) {

    @Override
    public String toString() {
        return "GleifExcludedNativeFields[redacted]";
    }
}

enum GleifTransportError {
    LIMITED,
    UNSUPPORTED,
    UNAVAILABLE
}

final class GleifTransportException extends RuntimeException {

    private final GleifTransportError error;

    GleifTransportException(GleifTransportError error) {
        super("GLEIF transport operation failed");
        this.error = Objects.requireNonNull(error, "GLEIF transport error must not be null");
    }

    GleifTransportError error() {
        return error;
    }
}
