package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ConnectorDomainException;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Tenant-bound opaque connector registration and its bounded active credentials. */
@Getter
public final class ConnectorRegistration extends AggregateRoot<ConnectorId> {
    public static final int MAX_ACTIVE_CREDENTIALS = 2;

    private ConnectorId id;
    private TenantId tenantId;
    private ConnectorRef connectorRef;
    private ConnectorStatus status;
    private final List<ConnectorCredentialKey> credentialKeys = new ArrayList<>();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime retiredTime;

    private ConnectorRegistration() {
    }

    public record CredentialSnapshot(
            String keyId, String secretVerifier, ConnectorCredentialStatus status,
            LocalDateTime createdTime, LocalDateTime revokedTime) {
        @Override
        public String toString() {
            return "ConnectorRegistration.CredentialSnapshot[REDACTED]";
        }
    }

    public record Snapshot(
            ConnectorId id, TenantId tenantId, ConnectorRef connectorRef,
            ConnectorStatus status, List<CredentialSnapshot> credentials, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime, LocalDateTime retiredTime) {
    }

    public static ConnectorRegistration create(
            TenantId tenantId, ConnectorRef connectorRef,
            String keyId, String secretVerifier, LocalDateTime now) {
        ConnectorRegistration registration = new ConnectorRegistration();
        registration.tenantId = requireTenant(tenantId);
        registration.connectorRef = requireRef(connectorRef);
        registration.status = ConnectorStatus.ACTIVE;
        registration.credentialKeys.add(ConnectorCredentialKey.create(
                keyId, secretVerifier, requireTime(now)));
        registration.createdTime = now;
        registration.updatedTime = now;
        return registration;
    }

    public static ConnectorRegistration reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new ConnectorDomainException("Connector registration snapshot is invalid");
        }
        ConnectorRegistration registration = new ConnectorRegistration();
        registration.id = snapshot.id();
        registration.tenantId = requireTenant(snapshot.tenantId());
        registration.connectorRef = requireRef(snapshot.connectorRef());
        registration.status = requireStatus(snapshot.status());
        List<CredentialSnapshot> credentials = snapshot.credentials() == null
                ? List.of() : snapshot.credentials();
        var keyIds = new HashSet<String>();
        for (CredentialSnapshot credential : credentials) {
            if (credential == null || !keyIds.add(credential.keyId())) {
                throw new ConnectorDomainException("Connector credential keys are duplicated");
            }
            registration.credentialKeys.add(ConnectorCredentialKey.reconstitute(
                    credential.keyId(), credential.secretVerifier(), credential.status(),
                    credential.createdTime(), credential.revokedTime()));
        }
        registration.createdTime = requireTime(snapshot.createdTime());
        registration.updatedTime = requireTime(snapshot.updatedTime());
        registration.retiredTime = snapshot.retiredTime();
        registration.requireLifecycle();
        registration.restoreVersion(snapshot.version());
        return registration;
    }

    public List<ConnectorCredentialKey> getCredentialKeys() {
        return List.copyOf(credentialKeys);
    }

    public void rotateCredential(String keyId, String secretVerifier, LocalDateTime now) {
        requireNotRetired();
        if (credentialKeys.stream().anyMatch(key -> key.getKeyId().equals(keyId))) {
            throw new ConnectorDomainException("Connector credential key ID cannot be reused");
        }
        if (activeCredentialCount() >= MAX_ACTIVE_CREDENTIALS) {
            throw new ConnectorDomainException(
                    "Connector already has the maximum number of active credentials");
        }
        credentialKeys.add(ConnectorCredentialKey.create(keyId, secretVerifier, requireTime(now)));
        touch(now);
    }

    public void revokeCredential(String keyId, LocalDateTime now) {
        requireNotRetired();
        ConnectorCredentialKey key = credentialKeys.stream()
                .filter(value -> value.getKeyId().equals(keyId) && value.isActive())
                .findFirst()
                .orElseThrow(() -> new ConnectorDomainException(
                        "Active connector credential was not found"));
        key.revoke(requireTime(now));
        touch(now);
    }

    public void changeStatus(ConnectorStatus target, LocalDateTime now) {
        if (target == null) {
            throw new ConnectorDomainException("Connector status is required");
        }
        switch (target) {
            case ACTIVE -> activate(now);
            case DISABLED -> disable(now);
            case RETIRED -> retire(now);
        }
    }

    public void activate(LocalDateTime now) {
        requireNotRetired();
        if (status == ConnectorStatus.ACTIVE) {
            return;
        }
        if (activeCredentialCount() == 0) {
            throw new ConnectorDomainException(
                    "A connector without an active credential cannot be enabled");
        }
        status = ConnectorStatus.ACTIVE;
        touch(now);
    }

    public void disable(LocalDateTime now) {
        requireNotRetired();
        if (status != ConnectorStatus.DISABLED) {
            status = ConnectorStatus.DISABLED;
            touch(now);
        }
    }

    public void retire(LocalDateTime now) {
        requireNotRetired();
        LocalDateTime retiredAt = requireTime(now);
        credentialKeys.forEach(key -> key.revoke(retiredAt));
        status = ConnectorStatus.RETIRED;
        retiredTime = retiredAt;
        touch(retiredAt);
    }

    public long activeCredentialCount() {
        return credentialKeys.stream().filter(ConnectorCredentialKey::isActive).count();
    }

    public void onPersisted(ConnectorId persistedId, Long persistedVersion,
                            LocalDateTime persistedCreatedTime,
                            LocalDateTime persistedUpdatedTime) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new ConnectorDomainException("Persisted connector ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (persistedCreatedTime != null) {
            createdTime = persistedCreatedTime;
        }
        if (persistedUpdatedTime != null) {
            updatedTime = persistedUpdatedTime;
        }
    }

    private void requireLifecycle() {
        long activeCredentials = activeCredentialCount();
        if (activeCredentials > MAX_ACTIVE_CREDENTIALS) {
            throw new ConnectorDomainException("Connector has too many active credentials");
        }
        if ((status == ConnectorStatus.RETIRED) != (retiredTime != null)) {
            throw new ConnectorDomainException("Connector retirement lifecycle is invalid");
        }
        if (status == ConnectorStatus.RETIRED && activeCredentials != 0) {
            throw new ConnectorDomainException("Retired connector credentials must be revoked");
        }
    }

    private void requireNotRetired() {
        if (status == ConnectorStatus.RETIRED) {
            throw new ConnectorDomainException("Retired connector cannot be changed");
        }
    }

    private void touch(LocalDateTime now) {
        updatedTime = requireTime(now);
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ConnectorDomainException("Connector tenant is required");
        }
        return value;
    }

    private static ConnectorRef requireRef(ConnectorRef value) {
        if (value == null) {
            throw new ConnectorDomainException("Connector reference is required");
        }
        return value;
    }

    private static ConnectorStatus requireStatus(ConnectorStatus value) {
        if (value == null) {
            throw new ConnectorDomainException("Connector status is required");
        }
        return value;
    }

    private static LocalDateTime requireTime(LocalDateTime value) {
        if (value == null) {
            throw new ConnectorDomainException("Connector lifecycle time is required");
        }
        return value;
    }
}
