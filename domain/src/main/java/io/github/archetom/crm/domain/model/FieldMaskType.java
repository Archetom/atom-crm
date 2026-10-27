package io.github.archetom.crm.domain.model;

/** Read-time masking, ordered from least to most restrictive. */
public enum FieldMaskType {
    NONE,
    PARTIAL,
    FULL
}
