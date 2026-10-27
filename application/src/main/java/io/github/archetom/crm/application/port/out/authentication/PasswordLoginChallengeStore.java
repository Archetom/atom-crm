package io.github.archetom.crm.application.port.out.authentication;

import java.time.Instant;

/**
 * Stores only a digest of an issued password-login challenge until it is atomically consumed.
 *
 * <p>The signed challenge remains self-describing, but it cannot establish more than one browser
 * session during its short lifetime.</p>
 */
public interface PasswordLoginChallengeStore {

    void issue(String challenge, Instant expiresAt);

    boolean consume(String challenge, Instant now);
}
