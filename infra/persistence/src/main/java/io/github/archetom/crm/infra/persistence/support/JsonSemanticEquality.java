package io.github.archetom.crm.infra.persistence.support;

import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/** Compares persisted JSON values independently of database whitespace normalization. */
public final class JsonSemanticEquality {

    private JsonSemanticEquality() {
    }

    public static boolean equal(ObjectMapper objectMapper, String left, String right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted custom-field JSON is invalid", exception);
        }
    }
}
