package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OrganizationMembershipPO;
import org.springframework.stereotype.Component;

/** Explicit conversion for tenant membership records. */
@Component
public class OrganizationMembershipPOConverter {

    public OrganizationMembership toDomain(OrganizationMembershipPO po) {
        if (po == null) return null;
        return OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(po.getId()), new TenantId(po.getTenantId()), new MemberId(po.getActorId()),
                optionalDepartmentId(po.getDepartmentId()), MembershipStatus.valueOf(po.getStatus()),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public OrganizationMembershipPO toPO(OrganizationMembership membership) {
        OrganizationMembershipPO po = new OrganizationMembershipPO();
        po.setId(membership.getId() == null ? null : membership.getId().getValue());
        po.setTenantId(membership.getTenantId().getValue());
        po.setActorId(membership.getMemberId().getValue());
        po.setDepartmentId(membership.getDepartmentId() == null ? null : membership.getDepartmentId().getValue());
        po.setStatus(membership.getStatus().name());
        po.setVersion(membership.getVersion());
        po.setCreatedTime(membership.getCreatedTime());
        po.setUpdatedTime(membership.getUpdatedTime());
        return po;
    }

    private DepartmentId optionalDepartmentId(Long value) {
        return value == null ? null : new DepartmentId(value);
    }
}
