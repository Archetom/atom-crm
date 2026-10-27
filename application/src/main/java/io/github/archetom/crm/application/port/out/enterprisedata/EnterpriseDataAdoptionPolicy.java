package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deployment-owned permission for a single, explicit B23 Customer adoption. It deliberately
 * carries policy references rather than source values, locators, provider credentials, or raw
 * response evidence.
 */
public record EnterpriseDataAdoptionPolicy(
        boolean ready,
        EnterpriseDataSourceKind sourceKind,
        EnterpriseDataUsageMode maximumUsageMode,
        Duration maximumObservationTtl,
        Duration provenanceRetention,
        String sourceBindingRef,
        String adapterEvidenceRef,
        String policyVersion,
        String attributionVersion,
        String mappingVersion,
        Set<EnterpriseDataAdoptableFact> allowedFacts,
        EnterpriseDataAttribution previewAttribution,
        EnterpriseDataAttribution adoptionConfirmationAttribution
) {
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    public static final Duration CORE_MAX_OBSERVATION_TTL = Duration.ofMinutes(5);
    public static final Duration MAX_PROVENANCE_RETENTION = Duration.ofDays(3650);

    public EnterpriseDataAdoptionPolicy {
        Objects.requireNonNull(maximumUsageMode, "adoption usage mode must not be null");
        maximumObservationTtl = maximumObservationTtl == null ? Duration.ZERO : maximumObservationTtl;
        provenanceRetention = provenanceRetention == null ? Duration.ZERO : provenanceRetention;
        allowedFacts = allowedFacts == null ? Set.of() : Set.copyOf(allowedFacts);
        if (!ready) {
            if (sourceKind != null || maximumUsageMode != EnterpriseDataUsageMode.NOT_READY
                    || !maximumObservationTtl.isZero() || !provenanceRetention.isZero()
                    || sourceBindingRef != null || adapterEvidenceRef != null || policyVersion != null
                    || attributionVersion != null || mappingVersion != null || !allowedFacts.isEmpty()
                    || previewAttribution != null || adoptionConfirmationAttribution != null) {
                throw new IllegalArgumentException("unavailable enterprise data adoption policy is invalid");
            }
        } else {
            Objects.requireNonNull(sourceKind, "adoption source kind must not be null");
            if (maximumUsageMode != EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS
                    || invalid(maximumObservationTtl, CORE_MAX_OBSERVATION_TTL)
                    || invalid(provenanceRetention, MAX_PROVENANCE_RETENTION)
                    || !allowedFacts.contains(io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact.REGISTERED_IDENTITY)) {
                throw new IllegalArgumentException("enterprise data adoption policy is invalid");
            }
            reference(sourceBindingRef, "source binding reference");
            reference(adapterEvidenceRef, "adapter evidence reference");
            reference(policyVersion, "policy version");
            reference(attributionVersion, "attribution version");
            reference(mappingVersion, "mapping version");
            context(previewAttribution, EnterpriseDataAttributionContext.PREVIEW);
            context(adoptionConfirmationAttribution,
                    EnterpriseDataAttributionContext.ADOPTION_CONFIRMATION);
        }
    }

    public static EnterpriseDataAdoptionPolicy unavailable() {
        return new EnterpriseDataAdoptionPolicy(false, null, EnterpriseDataUsageMode.NOT_READY,
                Duration.ZERO, Duration.ZERO, null, null, null, null, null, Set.of(), null, null);
    }

    private static boolean invalid(Duration value, Duration maximum) {
        return value.isZero() || value.isNegative() || value.compareTo(maximum) > 0;
    }

    private static void reference(String value, String name) {
        if (value == null || !REFERENCE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void context(EnterpriseDataAttribution value,
                                EnterpriseDataAttributionContext expected) {
        if (value == null || value.context() != expected) {
            throw new IllegalArgumentException("required adoption attribution is unavailable");
        }
    }
}
