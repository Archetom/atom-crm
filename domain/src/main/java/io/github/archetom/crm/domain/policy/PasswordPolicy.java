package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.UserDomainException;
import java.nio.charset.StandardCharsets;

/**
 * password policy
 * @author hanfeng
 */
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    public static final int BCRYPT_MAX_BYTES = 72;

    /**
     * validate password
     */
    public void validate(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new UserDomainException("Password must not be empty");
        }

        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < MIN_LENGTH) {
            throw new UserDomainException("Password must contain at least " + MIN_LENGTH + " characters");
        }

        if (characterCount > MAX_LENGTH) {
            throw new UserDomainException("Password must contain no more than " + MAX_LENGTH + " characters");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new UserDomainException("Password exceeds the BCrypt 72-byte input limit");
        }
    }

    /**
     * check password
     */
    public PasswordStrength checkStrength(String password) {
        if (password == null) {
            return PasswordStrength.WEAK;
        }

        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < MIN_LENGTH
                || characterCount > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return PasswordStrength.WEAK;
        }

        if (characterCount >= 16) {
            return PasswordStrength.STRONG;
        }

        return PasswordStrength.MEDIUM;
    }

    public enum PasswordStrength {
        WEAK, MEDIUM, STRONG
    }
}
