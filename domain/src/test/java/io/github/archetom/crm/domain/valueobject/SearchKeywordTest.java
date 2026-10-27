package io.github.archetom.crm.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchKeywordTest {

    @Test
    void appliesTheSameTrimBoundaryAsPersistedIdentifierKeys() {
        assertEquals("ACME", new SearchKeyword(" ACME ").value());
        assertEquals("\u2003ACME\u2003", SearchKeyword.normalize("\u2003ACME\u2003"));
        assertTrue(SearchKeyword.optional(" \t\n").isEmpty());
        assertTrue(SearchKeyword.optional("\u2003").isEmpty());
        assertNull(SearchKeyword.normalize(null));
    }

    @Test
    void rejectsBlankValuesWhenConstructingTheValueObject() {
        assertThrows(IllegalArgumentException.class, () -> new SearchKeyword(" \t"));
    }

    @Test
    void enforcesTheLimitAfterNormalizationInUnicodeCodePoints() {
        String maximum = "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS);
        assertEquals(maximum, new SearchKeyword(" " + maximum + " ").value());

        String tooLong = "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS + 1);
        assertThrows(IllegalArgumentException.class, () -> SearchKeyword.normalize(tooLong));
        assertFalse(SearchKeyword.optional("  ").isPresent());
    }
}
