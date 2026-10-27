package io.github.archetom.crm.infra.security;

import io.github.archetom.crm.domain.service.PasswordHasher;
import io.github.archetom.crm.domain.service.PasswordVerifier;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Spring Security adapter for the domain password-hashing port.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher, PasswordVerifier {

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher(
            @Value("${atom.security.password.bcrypt-strength:12}") int strength) {
        if (strength < 10 || strength > 16) {
            throw new IllegalArgumentException("BCrypt strength must be between 10 and 16");
        }
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String plainText) {
        return encoder.encode(plainText);
    }

    @Override
    public boolean matches(String plainText, PasswordHash passwordHash) {
        return plainText != null
                && passwordHash != null
                && encoder.matches(plainText, passwordHash.valueForPersistence());
    }
}
