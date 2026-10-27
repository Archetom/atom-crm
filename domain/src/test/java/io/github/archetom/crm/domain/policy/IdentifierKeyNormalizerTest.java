package io.github.archetom.crm.domain.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierKeyNormalizerTest {

    @Test
    void normalizesFullWidthAndCaseEquivalentIdentifierInput() {
        assertEquals("acme-1", IdentifierKeyNormalizer.normalize("ＡＣＭＥ－１"));
    }

    @Test
    void preservesTheExistingTrimThenNfkcBoundarySemantics() {
        assertEquals(" acme ", IdentifierKeyNormalizer.normalize("\u2003ＡＣＭＥ\u2003"));
    }
}
