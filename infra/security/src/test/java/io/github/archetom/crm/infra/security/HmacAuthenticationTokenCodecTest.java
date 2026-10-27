package io.github.archetom.crm.infra.security;

import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.PasswordLoginChallenge;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HmacAuthenticationTokenCodecTest {

    private static final String SIGNING_KEY = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    @Test
    void roundTripsOnlyTheExpectedPurposeAndRejectsTampering() {
        HmacAuthenticationTokenCodec codec = new HmacAuthenticationTokenCodec(SIGNING_KEY, new StandardEnvironment());
        PasswordLoginChallenge challenge = new PasswordLoginChallenge(7L, 8L, 3L, Instant.now().plusSeconds(60));

        String token = codec.issueLoginChallenge(challenge);
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThat(codec.readLoginChallenge(token)).hasValueSatisfying(decoded -> {
            assertThat(decoded.actorId()).isEqualTo(challenge.actorId());
            assertThat(decoded.sourceTenantId()).isEqualTo(challenge.sourceTenantId());
            assertThat(decoded.userVersion()).isEqualTo(challenge.userVersion());
            assertThat(decoded.expiresAt()).isEqualTo(challenge.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        });
        assertThat(codec.readBrowserSession(token)).isEmpty();
        assertThat(codec.readLoginChallenge(tampered)).isEmpty();
    }

    @Test
    void expiresAndRoundTripsSignedBrowserSessions() {
        HmacAuthenticationTokenCodec codec = new HmacAuthenticationTokenCodec(SIGNING_KEY, new StandardEnvironment());
        BrowserSession active = new BrowserSession(7L, 8L, 9L, 3L, Instant.now().plusSeconds(60));
        BrowserSession expired = new BrowserSession(7L, 8L, 9L, 3L, Instant.now().minusSeconds(1));

        assertThat(codec.readBrowserSession(codec.issueBrowserSession(active))).hasValueSatisfying(decoded -> {
            assertThat(decoded.actorId()).isEqualTo(active.actorId());
            assertThat(decoded.sourceTenantId()).isEqualTo(active.sourceTenantId());
            assertThat(decoded.tenantId()).isEqualTo(active.tenantId());
            assertThat(decoded.userVersion()).isEqualTo(active.userVersion());
            assertThat(decoded.expiresAt()).isEqualTo(active.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        });
        assertThat(codec.readBrowserSession(codec.issueBrowserSession(expired))).isEmpty();
    }
}
