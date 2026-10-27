package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.model.RoleStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenant-owned role that composes code-defined capability grants. */
@Getter
public class TenantRole extends AggregateRoot<RoleId> {

    private RoleId id;
    private TenantId tenantId;
    private String name;
    private String description;
    private RoleStatus status;
    private SystemRoleKey systemKey;
    private List<RoleGrant> grants = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private TenantRole() {
    }

    public record Snapshot(RoleId id, TenantId tenantId, String name, String description,
                           RoleStatus status, SystemRoleKey systemKey, List<RoleGrant> grants,
                           Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static TenantRole createCustom(TenantId tenantId, String name, String description,
                                          Collection<RoleGrant> grants) {
        TenantRole role = new TenantRole();
        role.tenantId = requireTenant(tenantId);
        role.name = requireName(name);
        role.description = normalizeDescription(description);
        role.status = RoleStatus.ACTIVE;
        role.grants = requireUniqueGrants(grants);
        LocalDateTime now = LocalDateTime.now();
        role.createdTime = now;
        role.updatedTime = now;
        return role;
    }

    public static TenantRole createSystem(TenantId tenantId, SystemRoleKey systemKey) {
        if (systemKey == null) {
            throw new AuthorizationDomainException("system role key must not be empty");
        }
        TenantRole role = new TenantRole();
        role.tenantId = requireTenant(tenantId);
        role.name = systemKey == SystemRoleKey.TENANT_OWNER ? "Tenant Owner" : "Tenant Member";
        role.description = systemKey == SystemRoleKey.TENANT_OWNER
                ? "Built-in tenant owner role" : "Built-in tenant member role";
        role.status = RoleStatus.ACTIVE;
        role.systemKey = systemKey;
        LocalDateTime now = LocalDateTime.now();
        role.createdTime = now;
        role.updatedTime = now;
        return role;
    }

    public static TenantRole reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new AuthorizationDomainException("role snapshot must not be empty");
        }
        TenantRole role = new TenantRole();
        role.id = snapshot.id();
        role.tenantId = requireTenant(snapshot.tenantId());
        role.name = requireName(snapshot.name());
        role.description = normalizeDescription(snapshot.description());
        role.status = snapshot.status() == null ? RoleStatus.INACTIVE : snapshot.status();
        role.systemKey = snapshot.systemKey();
        role.grants = requireUniqueGrants(snapshot.grants());
        role.restoreVersion(snapshot.version());
        role.createdTime = snapshot.createdTime();
        role.updatedTime = snapshot.updatedTime();
        return role;
    }

    public void update(String name, String description) {
        ensureCustom();
        this.name = requireName(name);
        this.description = normalizeDescription(description);
        updatedTime = LocalDateTime.now();
    }

    public void deactivate() {
        ensureCustom();
        status = RoleStatus.INACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public void activate() {
        ensureCustom();
        status = RoleStatus.ACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public void replaceGrants(Collection<RoleGrant> grants) {
        ensureCustom();
        this.grants = requireUniqueGrants(grants);
        updatedTime = LocalDateTime.now();
    }

    public TenantRole copyAs(String copiedName) {
        ensureCustom();
        return createCustom(tenantId, copiedName, description, grants);
    }

    public void assertDeletable(boolean hasAssignments) {
        ensureCustom();
        if (hasAssignments) {
            throw new AuthorizationDomainException("assigned roles cannot be deleted");
        }
    }

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    public boolean isSystemRole() {
        return systemKey != null;
    }

    public void onPersisted(RoleId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null) {
            throw new AuthorizationDomainException("persisted role ID must not be empty");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new AuthorizationDomainException("persisted role ID cannot change");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void ensureCustom() {
        if (isSystemRole()) {
            throw new AuthorizationDomainException("system roles are immutable");
        }
    }

    private static List<RoleGrant> requireUniqueGrants(Collection<RoleGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return List.of();
        }
        Map<String, RoleGrant> unique = new LinkedHashMap<>();
        for (RoleGrant grant : grants) {
            if (grant == null || unique.putIfAbsent(grant.authorityCode().getValue(), grant) != null) {
                throw new AuthorizationDomainException("role grants must be non-null and unique by authority");
            }
        }
        return List.copyOf(unique.values());
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new AuthorizationDomainException("role tenant must not be empty");
        }
        return tenantId;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new AuthorizationDomainException("role name is invalid");
        }
        return name.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        if (description.trim().length() > 500) {
            throw new AuthorizationDomainException("role description is too long");
        }
        return description.trim();
    }
}
