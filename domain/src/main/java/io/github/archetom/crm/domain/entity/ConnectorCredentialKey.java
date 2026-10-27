package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ConnectorDomainException;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** Hash-only authentication child of one connector registration. */
@Getter
public final class ConnectorCredentialKey {
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{24}");
    private static final Pattern VERIFIER = Pattern.compile("[0-9a-f]{64}");

    private final String keyId;
    private final String secretVerifier;
    private ConnectorCredentialStatus status;
    private final LocalDateTime createdTime;
    private LocalDateTime revokedTime;

    private ConnectorCredentialKey(String keyId, String secretVerifier,
                                   ConnectorCredentialStatus status,
                                   LocalDateTime createdTime, LocalDateTime revokedTime) {
        this.keyId = requireKeyId(keyId);
        this.secretVerifier = requireVerifier(secretVerifier);
        this.status = requireStatus(status);
        this.createdTime = requireTime(createdTime, "credential creation time");
        this.revokedTime = revokedTime;
        if ((status == ConnectorCredentialStatus.ACTIVE) != (revokedTime == null)) {
            throw new ConnectorDomainException("Connector credential lifecycle is invalid");
        }
    }

    public static ConnectorCredentialKey create(
            String keyId, String secretVerifier, LocalDateTime createdTime) {
        return new ConnectorCredentialKey(keyId, secretVerifier,
                ConnectorCredentialStatus.ACTIVE, createdTime, null);
    }

    public static ConnectorCredentialKey reconstitute(
            String keyId, String secretVerifier, ConnectorCredentialStatus status,
            LocalDateTime createdTime, LocalDateTime revokedTime) {
        return new ConnectorCredentialKey(
                keyId, secretVerifier, status, createdTime, revokedTime);
    }

    public boolean isActive() {
        return status == ConnectorCredentialStatus.ACTIVE;
    }

    void revoke(LocalDateTime now) {
        if (isActive()) {
            status = ConnectorCredentialStatus.REVOKED;
            revokedTime = requireTime(now, "credential revocation time");
        }
    }

    private static String requireKeyId(String value) {
        if (value == null || !KEY_ID.matcher(value).matches()) {
            throw new ConnectorDomainException("Connector credential key ID is invalid");
        }
        return value;
    }

    private static String requireVerifier(String value) {
        String normalized = value == null ? null : value.toLowerCase(Locale.ROOT);
        if (normalized == null || !VERIFIER.matcher(normalized).matches()) {
            throw new ConnectorDomainException("Connector credential verifier is invalid");
        }
        return normalized;
    }

    private static ConnectorCredentialStatus requireStatus(ConnectorCredentialStatus value) {
        if (value == null) {
            throw new ConnectorDomainException("Connector credential status is required");
        }
        return value;
    }

    private static LocalDateTime requireTime(LocalDateTime value, String description) {
        if (value == null) {
            throw new ConnectorDomainException(description + " is required");
        }
        return value;
    }
}
