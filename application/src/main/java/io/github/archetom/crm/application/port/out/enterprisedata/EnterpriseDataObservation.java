package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fixed allowlist of one source observation; it contains no raw provider payload. */
public record EnterpriseDataObservation(
        RegisteredIdentifier primaryIdentifier,
        List<RegisteredIdentifier> identifiers,
        LegalJurisdiction legalJurisdiction,
        String registeredName,
        RegisteredEntityStatus entityStatus,
        FormationDateKind formationDateKind,
        LocalDate formationDate,
        RegisteredAddressObservation registeredAddress,
        EnterpriseDataSourceKind sourceKind,
        boolean sourceDeclaredPartial,
        Instant sourceUpdatedAt,
        String sourceRevision
) {

    public static final int MAX_IDENTIFIERS = 20;
    public EnterpriseDataObservation {
        Objects.requireNonNull(primaryIdentifier, "primary identifier must not be null");
        identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
        if (identifiers.size() > MAX_IDENTIFIERS) {
            throw new IllegalArgumentException("observation contains too many identifiers");
        }
        registeredName = required(registeredName, 200, "registered name");
        Objects.requireNonNull(entityStatus, "entity status must not be null");
        if (formationDateKind == null ^ formationDate == null) {
            throw new IllegalArgumentException("formation date and kind must be present together");
        }
        Objects.requireNonNull(sourceKind, "source kind must not be null");
        sourceRevision = optional(sourceRevision, 200, "source revision");
        requireIdentifiers(primaryIdentifier, identifiers);
    }

    @Override
    public String toString() {
        return "EnterpriseDataObservation[redacted]";
    }

    private static void requireIdentifiers(
            RegisteredIdentifier primaryIdentifier, List<RegisteredIdentifier> identifiers) {
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("observation must contain a registered identifier");
        }
        Set<RegisteredIdentifierKey> keys = new HashSet<>();
        for (RegisteredIdentifier identifier : identifiers) {
            if (identifier == null || !keys.add(identifier.key())) {
                throw new IllegalArgumentException("observation identifiers are invalid");
            }
        }
        if (!identifiers.contains(primaryIdentifier)) {
            throw new IllegalArgumentException("primary identifier must belong to the observation");
        }
    }

    private static String required(String value, int maxCodePoints, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return normalized;
    }

    private static String optional(String value, int maxCodePoints, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return normalized;
    }
}
