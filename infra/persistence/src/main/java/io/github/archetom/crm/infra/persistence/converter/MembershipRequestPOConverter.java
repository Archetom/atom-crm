package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.model.MembershipRequestStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.MembershipRequestPO;
import org.springframework.stereotype.Component;

/** Explicit conversion for auditable tenant-access requests. */
@Component
public class MembershipRequestPOConverter {

    public MembershipRequest toDomain(MembershipRequestPO po) {
        if (po == null) return null;
        return MembershipRequest.reconstitute(new MembershipRequest.Snapshot(
                new MembershipRequestId(po.getId()), new MemberId(po.getApplicantActorId()),
                new TenantId(po.getTargetTenantId()), optionalDepartmentId(po.getTargetDepartmentId()),
                po.getMessage(), MembershipRequestStatus.valueOf(po.getStatus()),
                optionalMemberId(po.getReviewerActorId()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public MembershipRequestPO toPO(MembershipRequest request) {
        MembershipRequestPO po = new MembershipRequestPO();
        po.setId(request.getId() == null ? null : request.getId().getValue());
        po.setApplicantActorId(request.getApplicantId().getValue());
        po.setTargetTenantId(request.getTargetTenantId().getValue());
        po.setTargetDepartmentId(request.getTargetDepartmentId() == null ? null : request.getTargetDepartmentId().getValue());
        po.setMessage(request.getMessage());
        po.setStatus(request.getStatus().name());
        po.setReviewerActorId(request.getReviewerId() == null ? null : request.getReviewerId().getValue());
        po.setVersion(request.getVersion());
        po.setCreatedTime(request.getCreatedTime());
        po.setUpdatedTime(request.getUpdatedTime());
        return po;
    }

    private DepartmentId optionalDepartmentId(Long value) {
        return value == null ? null : new DepartmentId(value);
    }

    private MemberId optionalMemberId(Long value) {
        return value == null ? null : new MemberId(value);
    }
}
