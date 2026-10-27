package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.Customer;

/** A visible customer paired with its computed distance from the query point. */
public record CustomerDistance(Customer customer, long distanceMeters) {
}
