package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Purpose-separated hash-only public draft credential; it is never an authenticated caller. */
public final class MarketingFileDraftCredential {
    /**
     * A short-lived public draft capability, not an HTTP authentication credential. Keeping it
     * out of {@code Authorization} prevents public routes from accidentally accepting or
     * forwarding an internal bearer token.
     */
    public static final String HEADER_NAME = "X-Public-Upload-Capability";
    private static final byte[] PURPOSE = "atom-crm:b29b:marketing-file-draft:v1\u0000"
            .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private MarketingFileDraftCredential() { }

    public static Issued issue() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return new Issued(Base64.getUrlEncoder().withoutPadding().encodeToString(secret), digest(secret));
    }

    public static void requireMatches(String capability, MarketingFileDraft draft) {
        if (draft == null || !MessageDigest.isEqual(draft.getCredentialDigest(), digest(parse(capability)))) {
            throw unavailable();
        }
    }

    private static byte[] parse(String capability) {
        if (capability == null) {
            throw unavailable();
        }
        String raw = capability;
        if (!raw.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        try {
            byte[] value = Base64.getUrlDecoder().decode(raw);
            if (value.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(value).equals(raw)) {
                throw unavailable();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private static byte[] digest(byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PURPOSE);
            digest.update(secret);
            return digest.digest();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static NonRetryableApplicationException unavailable() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Marketing file draft is unavailable");
    }

    public record Issued(String secret, byte[] digest) {
        public Issued { digest = Arrays.copyOf(digest, digest.length); }
        @Override public byte[] digest() { return Arrays.copyOf(digest, digest.length); }
    }
}
