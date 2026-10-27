package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.valueobject.ConnectorRef;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/** Generates and parses {@code connectorRef.keyId.secret} credentials without retaining secrets. */
public final class ConnectorCredential {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{24}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String FAKE_VERIFIER = "0".repeat(64);

    private ConnectorCredential() {
    }

    public static Generated create() {
        return generated(new ConnectorRef(random(32)));
    }

    public static Generated rotate(ConnectorRef connectorRef) {
        if (connectorRef == null) {
            throw new IllegalArgumentException("connector reference is required");
        }
        return generated(connectorRef);
    }

    public static Optional<Parts> parse(String value) {
        if (value == null || value.length() != 112) {
            return Optional.empty();
        }
        int first = value.indexOf('.');
        int second = value.indexOf('.', first + 1);
        if (first != 43 || second != 68 || second != value.lastIndexOf('.')) {
            return Optional.empty();
        }
        String reference = value.substring(0, first);
        String keyId = value.substring(first + 1, second);
        String secret = value.substring(second + 1);
        if (!ConnectorRef.isCanonical(reference) || !KEY_ID.matcher(keyId).matches()
                || !SECRET.matcher(secret).matches() || !canonicalBytes(secret, 32)) {
            return Optional.empty();
        }
        return Optional.of(new Parts(new ConnectorRef(reference), keyId, secret));
    }

    public static boolean matches(String candidateVerifier, String storedVerifier) {
        String candidate = candidateVerifier == null ? FAKE_VERIFIER : candidateVerifier;
        String stored = storedVerifier == null ? FAKE_VERIFIER : storedVerifier;
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII),
                stored.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Generated generated(ConnectorRef connectorRef) {
        return new Generated(connectorRef, random(18), random(32));
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return ENCODER.encodeToString(value);
    }

    private static boolean canonicalBytes(String value, int expectedBytes) {
        try {
            byte[] decoded = DECODER.decode(value);
            return decoded.length == expectedBytes && ENCODER.encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record Parts(ConnectorRef connectorRef, String keyId, String secret) {
        public String verifier() {
            return sha256(secret);
        }

        public String fingerprint() {
            return sha256(value());
        }

        public String value() {
            return connectorRef.getValue() + "." + keyId + "." + secret;
        }

        @Override
        public String toString() {
            return "ConnectorCredential.Parts[REDACTED]";
        }
    }

    public record Generated(ConnectorRef connectorRef, String keyId, String secret) {
        public String verifier() {
            return sha256(secret);
        }

        public String value() {
            return connectorRef.getValue() + "." + keyId + "." + secret;
        }

        @Override
        public String toString() {
            return "ConnectorCredential.Generated[REDACTED]";
        }
    }
}
