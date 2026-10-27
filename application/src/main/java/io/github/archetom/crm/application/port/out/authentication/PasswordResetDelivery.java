package io.github.archetom.crm.application.port.out.authentication;

import java.time.Instant;

/** Sends an already rendered, single-use password reset message to a verified account email. */
public interface PasswordResetDelivery {

    void deliver(PasswordResetMessage message);

    record PasswordResetMessage(String recipientEmail, String resetUrl, Instant expiresAt) {
        public PasswordResetMessage {
            if (recipientEmail == null || recipientEmail.isBlank() || resetUrl == null || resetUrl.isBlank()
                    || expiresAt == null) {
                throw new IllegalArgumentException("Password reset delivery message is invalid");
            }
        }

        @Override
        public String toString() {
            return "PasswordResetMessage[REDACTED]";
        }
    }
}
