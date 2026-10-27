package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;

/** A saved-view filter condition and the explicit browser exposure preference. */
public record SavedViewCondition(FilterCondition filterCondition, boolean exposed) {

    public SavedViewCondition {
        if (filterCondition == null) {
            throw new CustomizationDomainException("saved view filter condition is required");
        }
    }
}
