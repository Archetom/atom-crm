package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleAssignmentPO;
import org.springframework.stereotype.Component;

/** Explicit conversion for member-role assignment records. */
@Component
public class RoleAssignmentPOConverter {

    public RoleAssignment toDomain(RoleAssignmentPO po) {
        if (po == null) {
            return null;
        }
        return RoleAssignment.reconstitute(new RoleAssignment.Snapshot(
                po.getId(), new TenantId(po.getTenantId()), new MemberId(po.getActorId()),
                new RoleId(po.getRoleId()), po.getCreatedTime()));
    }

    public RoleAssignmentPO toPO(RoleAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        RoleAssignmentPO po = new RoleAssignmentPO();
        po.setId(assignment.getId());
        po.setTenantId(assignment.getTenantId().getValue());
        po.setActorId(assignment.getMemberId().getValue());
        po.setRoleId(assignment.getRoleId().getValue());
        po.setCreatedTime(assignment.getCreatedTime());
        return po;
    }
}
