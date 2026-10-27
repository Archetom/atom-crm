package io.github.archetom.crm.infra.security;

import io.github.archetom.crm.application.port.out.authentication.AuthenticationTokenCodec;
import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.PasswordLoginChallenge;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/** HMAC-protected, purpose-separated browser-session and password-login challenge codec. */
@Component
public class HmacAuthenticationTokenCodec implements AuthenticationTokenCodec {

    private static final String VERSION = "v1";
    private static final String LOGIN_CHALLENGE = "login";
    private static final String BROWSER_SESSION = "session";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] signingKey;

    public HmacAuthenticationTokenCodec(
            @org.springframework.beans.factory.annotation.Value("${atom.authentication.signing-key:}")
            String configuredSigningKey,
            Environment environment
    ) {
        signingKey = resolveSigningKey(configuredSigningKey, environment);
    }

    @Override
    public String issueLoginChallenge(PasswordLoginChallenge challenge) {
        return issue(LOGIN_CHALLENGE, String.join(":",
                Long.toString(challenge.actorId()),
                Long.toString(challenge.sourceTenantId()),
                Long.toString(challenge.userVersion()),
                Long.toString(challenge.expiresAt().getEpochSecond())));
    }

    @Override
    public Optional<PasswordLoginChallenge> readLoginChallenge(String value) {
        return read(LOGIN_CHALLENGE, value).flatMap(payload -> {
            String[] parts = payload.split(":", -1);
            if (parts.length != 4) return Optional.empty();
            try {
                Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[3]));
                if (!expiresAt.isAfter(Instant.now())) return Optional.empty();
                return Optional.of(new PasswordLoginChallenge(
                        Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]), expiresAt));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    @Override
    public String issueBrowserSession(BrowserSession session) {
        return issue(BROWSER_SESSION, String.join(":",
                Long.toString(session.actorId()),
                Long.toString(session.sourceTenantId()),
                Long.toString(session.tenantId()),
                Long.toString(session.userVersion()),
                Long.toString(session.expiresAt().getEpochSecond())));
    }

    @Override
    public Optional<BrowserSession> readBrowserSession(String value) {
        return read(BROWSER_SESSION, value).flatMap(payload -> {
            String[] parts = payload.split(":", -1);
            if (parts.length != 5) return Optional.empty();
            try {
                Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
                if (!expiresAt.isAfter(Instant.now())) return Optional.empty();
                return Optional.of(new BrowserSession(
                        Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                        Long.parseLong(parts[3]), expiresAt));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private String issue(String purpose, String payload) {
        String payloadPart = ENCODER.encodeToString(payload.getBytes(StandardCharsets.US_ASCII));
        String unsigned = VERSION + "." + purpose + "." + payloadPart;
        return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
    }

    private Optional<String> read(String expectedPurpose, String value) {
        if (value == null || value.length() > 1024) return Optional.empty();
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !expectedPurpose.equals(parts[1])) {
            return Optional.empty();
        }
        String unsigned = parts[0] + "." + parts[1] + "." + parts[2];
        try {
            byte[] actualSignature = DECODER.decode(parts[3]);
            if (!parts[3].equals(ENCODER.encodeToString(actualSignature))) return Optional.empty();
            if (!MessageDigest.isEqual(sign(unsigned), actualSignature)) return Optional.empty();
            byte[] payload = DECODER.decode(parts[2]);
            String canonical = ENCODER.encodeToString(payload);
            if (!parts[2].equals(canonical)) return Optional.empty();
            return Optional.of(new String(payload, StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }

    private static byte[] resolveSigningKey(String configured, Environment environment) {
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] decoded = DECODER.decode(configured);
                if (decoded.length < 32 || !ENCODER.encodeToString(decoded).equals(configured)) {
                    throw new IllegalArgumentException("Authentication signing key must be canonical and at least 32 bytes");
                }
                return decoded;
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Authentication signing key is invalid", exception);
            }
        }
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            throw new IllegalStateException("atom.authentication.signing-key must be configured in prod");
        }
        byte[] generated = new byte[32];
        RANDOM.nextBytes(generated);
        return generated;
    }
}
