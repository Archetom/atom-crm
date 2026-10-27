package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Parses the public key form {@code tenantLocator.keyId.secret} without retaining its secret. */
public final class LeadIntakeApiKey {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final int TENANT_LOCATOR_LENGTH = 11;

    private LeadIntakeApiKey() {
    }

    public static Optional<Parts> parse(String value) {
        if (value == null || value.length() > 205) {
            return Optional.empty();
        }
        int firstSeparator = value.indexOf('.');
        int secondSeparator = value.indexOf('.', firstSeparator + 1);
        if (firstSeparator != TENANT_LOCATOR_LENGTH || secondSeparator - firstSeparator - 1 < 8
                || secondSeparator - firstSeparator - 1 > 64
                || secondSeparator != value.lastIndexOf('.')
                || value.length() - secondSeparator - 1 < 32) {
            return Optional.empty();
        }
        String tenantLocator = value.substring(0, firstSeparator);
        String keyId = value.substring(firstSeparator + 1, secondSeparator);
        String secret = value.substring(secondSeparator + 1);
        if (!keyId.matches("[A-Za-z0-9_-]{8,64}") || secret.length() > 128) {
            return Optional.empty();
        }
        return tenantId(tenantLocator).map(tenantId -> new Parts(tenantId, keyId, secret));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Generates a tenant locator, opaque public key ID, and 256-bit secret for a new client. */
    public static Generated generate(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required for a lead intake credential");
        }
        return new Generated(tenantLocator(tenantId), random(18), random(32));
    }

    private static String random(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String tenantLocator(TenantId tenantId) {
        return URL_ENCODER.encodeToString(ByteBuffer.allocate(Long.BYTES)
                .putLong(tenantId.getValue()).array());
    }

    private static Optional<TenantId> tenantId(String locator) {
        if (locator == null || !locator.matches("[A-Za-z0-9_-]{" + TENANT_LOCATOR_LENGTH + "}")) {
            return Optional.empty();
        }
        try {
            byte[] bytes = URL_DECODER.decode(locator);
            if (bytes.length != Long.BYTES) {
                return Optional.empty();
            }
            TenantId tenantId = new TenantId(ByteBuffer.wrap(bytes).getLong());
            return tenantLocator(tenantId).equals(locator) ? Optional.of(tenantId) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record Parts(TenantId tenantId, String keyId, String secret) {
        public String hash() {
            return sha256(secret);
        }
    }

    public record Generated(String tenantLocator, String keyId, String secret) {
        public String value() {
            return tenantLocator + "." + keyId + "." + secret;
        }
    }
}
