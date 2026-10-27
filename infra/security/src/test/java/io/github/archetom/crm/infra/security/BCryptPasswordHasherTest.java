package io.github.archetom.crm.infra.security;

import io.github.archetom.crm.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adapter that decides whether a password is accepted.
 *
 * <p>Every credential check in the system ends here, and the ways this can go wrong are quiet
 * ones: an unsalted digest still verifies its own input, a mis-wired strength still produces a
 * usable hash, and a null argument that throws instead of refusing turns a failed login into a
 * server error. None of those show up as a compile failure, so they are pinned here.</p>
 *
 * <p>Strength 10 is the cheapest value the constructor admits and is used wherever the test only
 * needs a working hash; BCrypt cost is exponential and the strength itself is asserted separately.</p>
 */
class BCryptPasswordHasherTest {

    private static final int CHEAPEST_ADMITTED_STRENGTH = 10;

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(CHEAPEST_ADMITTED_STRENGTH);

    @Test
    void aPasswordVerifiesAgainstItsOwnHash() {
        String plainText = "correct horse battery staple";
        assertTrue(hasher.matches(plainText, PasswordHash.fromTrustedHash(hasher.hash(plainText))));
    }

    @Test
    void aDifferentPasswordDoesNotVerify() {
        PasswordHash stored = PasswordHash.fromTrustedHash(hasher.hash("correct horse battery staple"));
        assertFalse(hasher.matches("Correct horse battery staple", stored));
        assertFalse(hasher.matches("correct horse battery stapl", stored));
        assertFalse(hasher.matches("", stored));
    }

    /**
     * The property that separates a password hash from a plain digest. Without a per-hash salt the
     * two calls would agree, and identical stored passwords would become visible to anyone reading
     * the table.
     */
    @Test
    void hashingTheSamePasswordTwiceProducesDifferentHashes() {
        String plainText = "correct horse battery staple";
        String first = hasher.hash(plainText);
        String second = hasher.hash(plainText);
        assertNotEquals(first, second);
        assertTrue(hasher.matches(plainText, PasswordHash.fromTrustedHash(first)));
        assertTrue(hasher.matches(plainText, PasswordHash.fromTrustedHash(second)));
    }

    /**
     * A missing credential is a refusal, not an exception: callers pass what arrived on the request,
     * and a stored hash may be absent for an account that has never set one.
     */
    @Test
    void absentInputIsRefusedRatherThanThrown() {
        assertFalse(hasher.matches(null, PasswordHash.fromTrustedHash(hasher.hash("secret"))));
        assertFalse(hasher.matches("secret", null));
        assertFalse(hasher.matches(null, null));
    }

    @Test
    void theConfiguredStrengthReachesTheEncoder() {
        assertTrue(new BCryptPasswordHasher(10).hash("secret").startsWith("$2a$10$"));
        assertTrue(new BCryptPasswordHasher(11).hash("secret").startsWith("$2a$11$"));
    }

    @Test
    void strengthOutsideTheSupportedRangeIsRejected() {
        assertEquals("BCrypt strength must be between 10 and 16",
                assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordHasher(9))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordHasher(17));
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordHasher(0));
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordHasher(-1));
    }

    /**
     * A hash must fit {@link PasswordHash}'s storage limit, or a successful hash would fail on the
     * way to the database.
     */
    @Test
    void theProducedHashFitsTheStoredValueObject() {
        String hash = hasher.hash("correct horse battery staple");
        assertEquals(60, hash.length());
        assertEquals(hash, PasswordHash.fromTrustedHash(hash).valueForPersistence());
    }
}
