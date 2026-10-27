package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/** Canonical public locator {@code tenantLocator.formKey}; it is an identifier, not a credential. */
public final class MarketingFormPublicId {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final int TENANT_LOCATOR_LENGTH = 11;

    private MarketingFormPublicId() {
    }

    public static Generated generate(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required for a marketing form");
        }
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return new Generated(tenantLocator(tenantId), URL_ENCODER.encodeToString(bytes));
    }

    public static Optional<Parts> parse(String value) {
        if (value == null || value.length() > 76) {
            return Optional.empty();
        }
        int separator = value.indexOf('.');
        if (separator != TENANT_LOCATOR_LENGTH || separator != value.lastIndexOf('.')) {
            return Optional.empty();
        }
        String locator = value.substring(0, separator);
        String publicKey = value.substring(separator + 1);
        if (!publicKey.matches("[A-Za-z0-9_-]{16,64}")) {
            return Optional.empty();
        }
        return tenantId(locator).map(tenantId -> new Parts(tenantId, publicKey));
    }

    public static String value(TenantId tenantId, String publicKey) {
        if (tenantId == null || publicKey == null
                || !publicKey.matches("[A-Za-z0-9_-]{16,64}")) {
            throw new IllegalArgumentException("marketing form public identifier is invalid");
        }
        return tenantLocator(tenantId) + "." + publicKey;
    }

    private static String tenantLocator(TenantId tenantId) {
        return URL_ENCODER.encodeToString(ByteBuffer.allocate(Long.BYTES)
                .putLong(tenantId.getValue()).array());
    }

    private static Optional<TenantId> tenantId(String locator) {
        if (locator == null
                || !locator.matches("[A-Za-z0-9_-]{" + TENANT_LOCATOR_LENGTH + "}")) {
            return Optional.empty();
        }
        try {
            byte[] bytes = URL_DECODER.decode(locator);
            if (bytes.length != Long.BYTES) {
                return Optional.empty();
            }
            TenantId tenantId = new TenantId(ByteBuffer.wrap(bytes).getLong());
            return tenantLocator(tenantId).equals(locator)
                    ? Optional.of(tenantId) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record Parts(TenantId tenantId, String publicKey) {
    }

    public record Generated(String tenantLocator, String publicKey) {
        public String value() {
            return tenantLocator + "." + publicKey;
        }
    }
}
