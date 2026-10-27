package io.github.archetom.crm.application.port.out.authentication;

import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Optional;

/** Durable, one-use password-reset capability store. Raw tokens are never persisted. */
public interface PasswordResetTokenStore {

    IssuedPasswordResetToken issue(TenantId sourceTenantId, UserId actorId, Instant expiresAt);

    Optional<ActivePasswordResetToken> findActiveForUpdate(String rawToken, Instant now);

    void markConsumed(long tokenId, Instant consumedAt);

    record IssuedPasswordResetToken(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedPasswordResetToken[REDACTED]";
        }
    }

    record ActivePasswordResetToken(long tokenId, TenantId sourceTenantId, UserId actorId) {
        public ActivePasswordResetToken {
            if (tokenId <= 0 || sourceTenantId == null || actorId == null) {
                throw new IllegalArgumentException("Password reset token subject is invalid");
            }
        }
    }
}
