package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.entity.Contact;

/** A visible contact paired with its computed distance from the query point. */
public record ContactDistance(Contact contact, long distanceMeters) {
}
