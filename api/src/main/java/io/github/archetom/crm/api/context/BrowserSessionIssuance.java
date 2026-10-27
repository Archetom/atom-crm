package io.github.archetom.crm.api.context;

import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;

import java.time.Instant;

/**
 * A browser session that has just been established or resumed.
 *
 * <p>It carries the public response body beside the cookie material. The token is deliberately
 * kept out of {@link AuthenticationSessionResponse}: only {@code infra/rest} may place it in an
 * HttpOnly cookie, and it must never be serialized into a response body, a log, or a trace.</p>
 */
public record BrowserSessionIssuance(
        AuthenticationSessionResponse session, String token, Instant expiresAt) {

    public BrowserSessionIssuance {
        if (session == null) {
            throw new IllegalArgumentException("session response is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("session token is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("session expiry is required");
        }
    }

    /** The token is secret; a session issuance never renders it. */
    @Override
    public String toString() {
        return "BrowserSessionIssuance[REDACTED]";
    }
}
