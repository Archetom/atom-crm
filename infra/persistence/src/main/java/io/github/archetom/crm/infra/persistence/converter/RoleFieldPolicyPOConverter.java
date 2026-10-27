package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.RoleFieldAccess;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleFieldAccessPO;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleFieldPolicyPO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoleFieldPolicyPOConverter {

    public RoleFieldPolicy toDomain(RoleFieldPolicyPO po, List<RoleFieldAccessPO> accessRows,
                                    java.util.Map<Long, FieldCode> fieldCodes) {
        if (po == null) {
            return null;
        }
        List<RoleFieldAccess> accesses = accessRows.stream().map(row -> new RoleFieldAccess(
                new FieldDefinitionId(row.getFieldId()), requireCode(fieldCodes, row.getFieldId()),
                FieldAccessLevel.valueOf(row.getAccessLevel()), FieldMaskType.valueOf(row.getMaskType())))
                .toList();
        return RoleFieldPolicy.reconstitute(new RoleFieldPolicy.Snapshot(
                new TenantId(po.getTenantId()), new RoleId(po.getRoleId()),
                BusinessObjectType.valueOf(po.getObjectType()), accesses,
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public RoleFieldPolicyPO toPO(RoleFieldPolicy policy) {
        RoleFieldPolicyPO po = new RoleFieldPolicyPO();
        po.setTenantId(policy.getTenantId().getValue());
        po.setRoleId(policy.getRoleId().getValue());
        po.setObjectType(policy.getObjectType().name());
        po.setVersion(policy.getVersion());
        po.setCreatedTime(policy.getCreatedTime());
        po.setUpdatedTime(policy.getUpdatedTime());
        return po;
    }

    public RoleFieldAccessPO toAccessPO(RoleFieldPolicy policy, RoleFieldAccess access) {
        RoleFieldAccessPO po = new RoleFieldAccessPO();
        po.setTenantId(policy.getTenantId().getValue());
        po.setRoleId(policy.getRoleId().getValue());
        po.setObjectType(policy.getObjectType().name());
        po.setFieldId(access.fieldId().getValue());
        po.setAccessLevel(access.accessLevel().name());
        po.setMaskType(access.maskType().name());
        return po;
    }

    private FieldCode requireCode(java.util.Map<Long, FieldCode> fieldCodes, Long fieldId) {
        FieldCode code = fieldCodes.get(fieldId);
        if (code == null) {
            throw new IllegalStateException("Stored role field policy references an unknown field");
        }
        return code;
    }
}
