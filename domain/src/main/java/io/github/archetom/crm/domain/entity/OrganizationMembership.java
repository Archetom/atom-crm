package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OrganizationDomainException;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Tenant-scoped authorization relationship between a verified actor and an organization. */
@Getter
public class OrganizationMembership extends AggregateRoot<MembershipId> {

    private MembershipId id;
    private TenantId tenantId;
    private MemberId memberId;
    private DepartmentId departmentId;
    private MembershipStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OrganizationMembership() {
    }

    public record Snapshot(MembershipId id, TenantId tenantId, MemberId memberId, DepartmentId departmentId,
                           MembershipStatus status, Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static OrganizationMembership activate(TenantId tenantId, MemberId memberId, DepartmentId departmentId) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.tenantId = requireTenant(tenantId);
        membership.memberId = requireMember(memberId);
        membership.departmentId = departmentId;
        membership.status = MembershipStatus.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        membership.createdTime = now;
        membership.updatedTime = now;
        return membership;
    }

    public static OrganizationMembership reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OrganizationDomainException("membership snapshot must not be empty");
        }
        OrganizationMembership membership = new OrganizationMembership();
        membership.id = snapshot.id();
        membership.tenantId = requireTenant(snapshot.tenantId());
        membership.memberId = requireMember(snapshot.memberId());
        membership.departmentId = snapshot.departmentId();
        membership.status = snapshot.status() == null ? MembershipStatus.SUSPENDED : snapshot.status();
        membership.restoreVersion(snapshot.version());
        membership.createdTime = snapshot.createdTime();
        membership.updatedTime = snapshot.updatedTime();
        return membership;
    }

    public void assignDepartment(DepartmentId departmentId) {
        ensureActive();
        this.departmentId = departmentId;
        updatedTime = LocalDateTime.now();
    }

    public void suspend() {
        status = MembershipStatus.SUSPENDED;
        updatedTime = LocalDateTime.now();
    }

    public void resume() {
        status = MembershipStatus.ACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void onPersisted(MembershipId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null) {
            throw new OrganizationDomainException("persisted membership ID must not be empty");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new OrganizationDomainException("persisted membership ID cannot change");
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

    private void ensureActive() {
        if (!isActive()) {
            throw new OrganizationDomainException("suspended memberships cannot change departments");
        }
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new OrganizationDomainException("membership tenant must not be empty");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId) {
        if (memberId == null) {
            throw new OrganizationDomainException("membership member must not be empty");
        }
        return memberId;
    }
}
