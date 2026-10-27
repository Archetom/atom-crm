package io.github.archetom.crm.infra.persistence.support;

import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlLikePatternTest {

    @Test
    void escapesLikeMetacharactersAndTheEscapeCharacter() {
        assertEquals("ACME!%!_!!", SqlLikePattern.literal("ACME%_!"));
    }

    @Test
    void preservesOrdinaryTextAndNull() {
        assertEquals("ACME 100", SqlLikePattern.literal("ACME 100"));
        assertNull(SqlLikePattern.literal(null));
    }

    @Test
    void normalizesBeforeEscapingFullWidthLikeMetacharactersForIdentifierPrefixes() {
        assertEquals("!%!_!!", SqlLikePattern.normalizedIdentifierPrefixLiteral(
                new SearchKeyword("％＿！")));
    }
}
