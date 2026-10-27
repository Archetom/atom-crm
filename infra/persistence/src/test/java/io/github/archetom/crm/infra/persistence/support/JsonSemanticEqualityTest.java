package io.github.archetom.crm.infra.persistence.support;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSemanticEqualityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ignoresDatabaseJsonWhitespaceButPreservesArrayOrderAndValues() {
        assertTrue(JsonSemanticEquality.equal(objectMapper,
                "[\"enterprise\", \"growth\"]", "[\"enterprise\",\"growth\"]"));
        assertFalse(JsonSemanticEquality.equal(objectMapper,
                "[\"enterprise\", \"growth\"]", "[\"growth\",\"enterprise\"]"));
        assertFalse(JsonSemanticEquality.equal(objectMapper, null, "[]"));
    }

    @Test
    void rejectsInvalidPersistedJsonInsteadOfMaskingCorruptionAsAChange() {
        assertThrows(IllegalStateException.class,
                () -> JsonSemanticEquality.equal(objectMapper, "not-json", "[]"));
    }
}
