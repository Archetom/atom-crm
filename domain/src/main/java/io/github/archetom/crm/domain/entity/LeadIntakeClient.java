package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tenant-bound, hash-only credential allowed to submit public lead intake. */
@Getter
public class LeadIntakeClient extends AggregateRoot<LeadIntakeClientId> {

    public static final int MAX_ACTIVE_CLIENTS_PER_TENANT = 100;

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private LeadIntakeClientId id;
    private TenantId tenantId;
    private MemberId ownerMemberId;
    private String keyId;
    private String apiKeyHash;
    private boolean active;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private LeadIntakeClient() {
    }

    public record Snapshot(
            LeadIntakeClientId id, TenantId tenantId, MemberId ownerMemberId, String keyId,
            String apiKeyHash, boolean active, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static LeadIntakeClient create(
            TenantId tenantId, MemberId ownerMemberId, String keyId, String apiKeyHash) {
        LeadIntakeClient client = new LeadIntakeClient();
        client.tenantId = requireTenant(tenantId);
        client.ownerMemberId = requireOwner(ownerMemberId);
        client.keyId = requireKeyId(keyId);
        client.apiKeyHash = requireHash(apiKeyHash);
        client.active = true;
        client.createdTime = LocalDateTime.now();
        client.updatedTime = client.createdTime;
        return client;
    }

    public static LeadIntakeClient reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("lead intake client snapshot must not be empty");
        }
        LeadIntakeClient client = new LeadIntakeClient();
        client.id = snapshot.id();
        client.tenantId = requireTenant(snapshot.tenantId());
        client.ownerMemberId = requireOwner(snapshot.ownerMemberId());
        client.keyId = requireKeyId(snapshot.keyId());
        client.apiKeyHash = requireHash(snapshot.apiKeyHash());
        client.active = snapshot.active();
        client.restoreVersion(snapshot.version());
        client.createdTime = snapshot.createdTime();
        client.updatedTime = snapshot.updatedTime();
        return client;
    }

    public void deactivate() {
        if (active) {
            active = false;
            updatedTime = LocalDateTime.now();
        }
    }

    public void onPersisted(
            LeadIntakeClientId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new IllegalArgumentException("persisted lead intake client ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        this.createdTime = createdTime == null ? this.createdTime : createdTime;
        this.updatedTime = updatedTime == null ? this.updatedTime : updatedTime;
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new IllegalArgumentException("lead intake client tenant must not be empty");
        }
        return value;
    }

    private static MemberId requireOwner(MemberId value) {
        if (value == null) {
            throw new IllegalArgumentException("lead intake client owner must not be empty");
        }
        return value;
    }

    private static String requireKeyId(String value) {
        if (value == null || !KEY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("lead intake key ID is invalid");
        }
        return value;
    }

    private static String requireHash(String value) {
        String normalized = value == null ? null : value.toLowerCase(Locale.ROOT);
        if (normalized == null || !HASH.matcher(normalized).matches()) {
            throw new IllegalArgumentException("lead intake API key hash is invalid");
        }
        return normalized;
    }
}
