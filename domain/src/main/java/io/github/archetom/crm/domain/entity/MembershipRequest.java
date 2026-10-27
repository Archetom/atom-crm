package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OrganizationDomainException;
import io.github.archetom.crm.domain.model.MembershipRequestStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Auditable request for a verified actor to join a target tenant. */
@Getter
public class MembershipRequest extends AggregateRoot<MembershipRequestId> {

    private MembershipRequestId id;
    private MemberId applicantId;
    private TenantId targetTenantId;
    private DepartmentId targetDepartmentId;
    private String message;
    private MembershipRequestStatus status;
    private MemberId reviewerId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private MembershipRequest() {
    }

    public record Snapshot(MembershipRequestId id, MemberId applicantId, TenantId targetTenantId,
                           DepartmentId targetDepartmentId, String message, MembershipRequestStatus status,
                           MemberId reviewerId, Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static MembershipRequest submit(MemberId applicantId, TenantId targetTenantId,
                                           DepartmentId targetDepartmentId, String message) {
        MembershipRequest request = new MembershipRequest();
        request.applicantId = requireMember(applicantId, "applicant");
        request.targetTenantId = requireTenant(targetTenantId);
        request.targetDepartmentId = targetDepartmentId;
        request.message = normalizeMessage(message);
        request.status = MembershipRequestStatus.PENDING;
        LocalDateTime now = LocalDateTime.now();
        request.createdTime = now;
        request.updatedTime = now;
        return request;
    }

    public static MembershipRequest reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OrganizationDomainException("membership request snapshot must not be empty");
        }
        MembershipRequest request = new MembershipRequest();
        request.id = snapshot.id();
        request.applicantId = requireMember(snapshot.applicantId(), "applicant");
        request.targetTenantId = requireTenant(snapshot.targetTenantId());
        request.targetDepartmentId = snapshot.targetDepartmentId();
        request.message = normalizeMessage(snapshot.message());
        request.status = snapshot.status() == null ? MembershipRequestStatus.REJECTED : snapshot.status();
        request.reviewerId = snapshot.reviewerId();
        request.restoreVersion(snapshot.version());
        request.createdTime = snapshot.createdTime();
        request.updatedTime = snapshot.updatedTime();
        return request;
    }

    public void approve(MemberId reviewerId) {
        requirePending();
        this.reviewerId = requireMember(reviewerId, "reviewer");
        status = MembershipRequestStatus.APPROVED;
        updatedTime = LocalDateTime.now();
    }

    public void reject(MemberId reviewerId) {
        requirePending();
        this.reviewerId = requireMember(reviewerId, "reviewer");
        status = MembershipRequestStatus.REJECTED;
        updatedTime = LocalDateTime.now();
    }

    public void withdraw(MemberId applicantId) {
        requirePending();
        if (!this.applicantId.sameValueAs(applicantId)) {
            throw new OrganizationDomainException("only the applicant can withdraw a membership request");
        }
        status = MembershipRequestStatus.WITHDRAWN;
        updatedTime = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == MembershipRequestStatus.PENDING;
    }

    public void onPersisted(MembershipRequestId id, Long version, LocalDateTime createdTime,
                            LocalDateTime updatedTime) {
        if (id == null) {
            throw new OrganizationDomainException("persisted membership request ID must not be empty");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new OrganizationDomainException("persisted membership request ID cannot change");
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

    private void requirePending() {
        if (!isPending()) {
            throw new OrganizationDomainException("membership request is no longer pending");
        }
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new OrganizationDomainException("target tenant must not be empty");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId, String role) {
        if (memberId == null) {
            throw new OrganizationDomainException(role + " member must not be empty");
        }
        return memberId;
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim();
        if (normalized.length() > 500) {
            throw new OrganizationDomainException("membership request message is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
