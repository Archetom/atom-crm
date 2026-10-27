package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.RoleFieldPolicyId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned atomic field-policy set for one custom role and object. */
@Getter
public class RoleFieldPolicy extends AggregateRoot<RoleFieldPolicyId> {

    public static final int MAX_ACCESSES = 200;

    private RoleFieldPolicyId id;
    private TenantId tenantId;
    private RoleId roleId;
    private BusinessObjectType objectType;
    private List<RoleFieldAccess> accesses = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private RoleFieldPolicy() {
    }

    public record Snapshot(TenantId tenantId, RoleId roleId, BusinessObjectType objectType,
                           List<RoleFieldAccess> accesses, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static RoleFieldPolicy create(TenantId tenantId, RoleId roleId,
                                         BusinessObjectType objectType,
                                         Collection<RoleFieldAccess> accesses) {
        RoleFieldPolicy policy = new RoleFieldPolicy();
        policy.tenantId = requireTenant(tenantId);
        policy.roleId = requireRole(roleId);
        policy.objectType = requireObjectType(objectType);
        policy.id = new RoleFieldPolicyId(tenantId, roleId, objectType);
        policy.replace(accesses);
        policy.createdTime = policy.updatedTime;
        return policy;
    }

    public static RoleFieldPolicy reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomizationDomainException("role field policy snapshot must not be empty");
        }
        RoleFieldPolicy policy = new RoleFieldPolicy();
        policy.tenantId = requireTenant(snapshot.tenantId());
        policy.roleId = requireRole(snapshot.roleId());
        policy.objectType = requireObjectType(snapshot.objectType());
        policy.id = new RoleFieldPolicyId(policy.tenantId, policy.roleId, policy.objectType);
        policy.accesses = validateAccesses(snapshot.accesses());
        policy.restoreVersion(snapshot.version());
        policy.createdTime = snapshot.createdTime();
        policy.updatedTime = snapshot.updatedTime();
        return policy;
    }

    public void replace(Collection<RoleFieldAccess> accesses) {
        this.accesses = validateAccesses(accesses);
        updatedTime = LocalDateTime.now();
    }

    public void onPersisted(Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private static List<RoleFieldAccess> validateAccesses(Collection<RoleFieldAccess> accesses) {
        if (accesses != null && accesses.size() > MAX_ACCESSES) {
            throw new CustomizationDomainException("role field policy exceeds the field limit");
        }
        Map<FieldDefinitionId, RoleFieldAccess> unique = new LinkedHashMap<>();
        if (accesses != null) {
            for (RoleFieldAccess access : accesses) {
                if (access == null || unique.putIfAbsent(access.fieldId(), access) != null) {
                    throw new CustomizationDomainException("role field accesses must be unique by field");
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new CustomizationDomainException("role field policy tenant must not be empty");
        }
        return value;
    }

    private static RoleId requireRole(RoleId value) {
        if (value == null) {
            throw new CustomizationDomainException("role field policy role must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new CustomizationDomainException("role field policy object type must not be empty");
        }
        return value;
    }
}
