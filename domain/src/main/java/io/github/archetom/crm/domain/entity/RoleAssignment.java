package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Explicit tenant-scoped association between an active member and a role. */
@Getter
public final class RoleAssignment {

    private Long id;
    private TenantId tenantId;
    private MemberId memberId;
    private RoleId roleId;
    private LocalDateTime createdTime;

    private RoleAssignment() {
    }

    public record Snapshot(Long id, TenantId tenantId, MemberId memberId, RoleId roleId,
                           LocalDateTime createdTime) {
    }

    public static RoleAssignment assign(TenantId tenantId, MemberId memberId, RoleId roleId) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.tenantId = requireTenant(tenantId);
        assignment.memberId = requireMember(memberId);
        assignment.roleId = requireRole(roleId);
        assignment.createdTime = LocalDateTime.now();
        return assignment;
    }

    public static RoleAssignment reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new AuthorizationDomainException("role assignment snapshot must not be empty");
        }
        RoleAssignment assignment = new RoleAssignment();
        assignment.id = snapshot.id();
        assignment.tenantId = requireTenant(snapshot.tenantId());
        assignment.memberId = requireMember(snapshot.memberId());
        assignment.roleId = requireRole(snapshot.roleId());
        assignment.createdTime = snapshot.createdTime();
        return assignment;
    }

    public void onPersisted(Long id, LocalDateTime createdTime) {
        if (id == null || id <= 0) {
            throw new AuthorizationDomainException("persisted assignment ID must be positive");
        }
        if (this.id != null && !this.id.equals(id)) {
            throw new AuthorizationDomainException("persisted assignment ID cannot change");
        }
        this.id = id;
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new AuthorizationDomainException("assignment tenant must not be empty");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId) {
        if (memberId == null) {
            throw new AuthorizationDomainException("assignment member must not be empty");
        }
        return memberId;
    }

    private static RoleId requireRole(RoleId roleId) {
        if (roleId == null) {
            throw new AuthorizationDomainException("assignment role must not be empty");
        }
        return roleId;
    }
}
