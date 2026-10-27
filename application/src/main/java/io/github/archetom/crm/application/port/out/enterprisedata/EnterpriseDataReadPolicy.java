package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;

import java.time.Duration;
import java.util.Objects;

/** Deployment-owned source policy and controlled attribution for B23a reads. */
public record EnterpriseDataReadPolicy(
        boolean ready,
        EnterpriseDataSourceKind sourceKind,
        EnterpriseDataUsageMode maximumUsageMode,
        Duration maximumObservationTtl,
        EnterpriseDataAttribution searchResultAttribution,
        EnterpriseDataAttribution detailAttribution
) {
    public static final Duration CORE_MAX_OBSERVATION_TTL = Duration.ofMinutes(5);

    public EnterpriseDataReadPolicy {
        Objects.requireNonNull(maximumUsageMode, "maximum usage mode must not be null");
        maximumObservationTtl = maximumObservationTtl == null
                ? Duration.ZERO : maximumObservationTtl;
        if (!ready) {
            if (sourceKind != null || maximumUsageMode != EnterpriseDataUsageMode.NOT_READY
                    || !maximumObservationTtl.isZero() || searchResultAttribution != null
                    || detailAttribution != null) {
                throw new IllegalArgumentException("unavailable enterprise data policy is invalid");
            }
        } else {
            Objects.requireNonNull(sourceKind, "policy source kind must not be null");
            if (maximumUsageMode == EnterpriseDataUsageMode.NOT_READY
                    || maximumObservationTtl.isZero() || maximumObservationTtl.isNegative()
                    || maximumObservationTtl.compareTo(
                            CORE_MAX_OBSERVATION_TTL) > 0) {
                throw new IllegalArgumentException("enterprise data read policy is invalid");
            }
            requireContext(searchResultAttribution,
                    EnterpriseDataAttributionContext.SEARCH_RESULT);
            requireContext(detailAttribution, EnterpriseDataAttributionContext.DETAIL);
        }
    }

    public EnterpriseDataAttribution attribution(
            EnterpriseDataAttributionContext context) {
        return switch (context) {
            case SEARCH_RESULT -> searchResultAttribution;
            case DETAIL -> detailAttribution;
            default -> throw new IllegalArgumentException(
                    "attribution context is not available in B23a");
        };
    }

    public static EnterpriseDataReadPolicy unavailable() {
        return new EnterpriseDataReadPolicy(false, null,
                EnterpriseDataUsageMode.NOT_READY, Duration.ZERO, null, null);
    }

    private static void requireContext(
            EnterpriseDataAttribution attribution,
            EnterpriseDataAttributionContext expected) {
        if (attribution == null || attribution.context() != expected) {
            throw new IllegalArgumentException("required attribution context is not ready");
        }
    }
}
