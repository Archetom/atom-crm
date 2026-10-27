package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.UserDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void enforcesLengthAndBcryptByteBoundaries() {
        assertThrows(UserDomainException.class, () -> policy.validate("a".repeat(11)));
        assertDoesNotThrow(() -> policy.validate("a".repeat(12)));
        assertDoesNotThrow(() -> policy.validate("a".repeat(64)));
        assertThrows(UserDomainException.class, () -> policy.validate("a".repeat(65)));

        // Twenty-five CJK characters are within the character limit but occupy 75 UTF-8 bytes.
        assertThrows(UserDomainException.class, () -> policy.validate("密".repeat(25)));
    }

    @Test
    void reportsStrengthWithoutCompositionRules() {
        assertEquals(PasswordPolicy.PasswordStrength.WEAK, policy.checkStrength("short"));
        assertEquals(PasswordPolicy.PasswordStrength.MEDIUM, policy.checkStrength("a".repeat(12)));
        assertEquals(PasswordPolicy.PasswordStrength.STRONG, policy.checkStrength("a".repeat(16)));
    }
}
