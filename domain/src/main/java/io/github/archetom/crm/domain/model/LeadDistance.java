package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.Lead;

/** A visible lead paired with its computed distance from the query point. */
public record LeadDistance(Lead lead, long distanceMeters) {
}
