package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;

import java.util.Objects;

/** Fixed one-page registered-name query; no provider filters or pagination tokens. */
public record EnterpriseNameSearchQuery(
        SearchKeyword registeredName,
        LegalJurisdiction legalJurisdiction,
        int limit
) {
    public EnterpriseNameSearchQuery {
        Objects.requireNonNull(registeredName, "registered name must not be null");
        Objects.requireNonNull(legalJurisdiction, "legal jurisdiction must not be null");
        if (limit < 1 || limit > EnterpriseDataCapabilitySnapshot.CORE_MAX_CANDIDATES) {
            throw new IllegalArgumentException("enterprise name search limit is invalid");
        }
    }

    @Override
    public String toString() {
        return "EnterpriseNameSearchQuery[registeredName=redacted]";
    }
}
