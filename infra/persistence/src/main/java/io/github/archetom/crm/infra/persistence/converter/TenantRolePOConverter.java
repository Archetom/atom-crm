package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.RoleStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.TenantRolePO;
import org.springframework.stereotype.Component;

import java.util.List;

/** Explicit conversion between tenant roles and relational records. */
@Component
public class TenantRolePOConverter {

    public TenantRole toDomain(TenantRolePO po, List<RoleGrant> grants) {
        if (po == null) {
            return null;
        }
        return TenantRole.reconstitute(new TenantRole.Snapshot(
                new RoleId(po.getId()),
                new TenantId(po.getTenantId()),
                po.getName(),
                po.getDescription(),
                RoleStatus.valueOf(po.getStatus()),
                po.getSystemKey() == null ? null : SystemRoleKey.valueOf(po.getSystemKey()),
                grants,
                po.getVersion(),
                po.getCreatedTime(),
                po.getUpdatedTime()));
    }

    public TenantRolePO toPO(TenantRole role) {
        if (role == null) {
            return null;
        }
        TenantRolePO po = new TenantRolePO();
        po.setId(role.getId() == null ? null : role.getId().getValue());
        po.setTenantId(role.getTenantId().getValue());
        po.setName(role.getName());
        po.setDescription(role.getDescription());
        po.setStatus(role.getStatus().name());
        po.setSystemKey(role.getSystemKey() == null ? null : role.getSystemKey().name());
        po.setVersion(role.getVersion());
        po.setCreatedTime(role.getCreatedTime());
        po.setUpdatedTime(role.getUpdatedTime());
        return po;
    }
}
