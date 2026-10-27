package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleGrantPO;
import org.springframework.stereotype.Component;

/** Explicit conversion for tenant role grant records. */
@Component
public class RoleGrantPOConverter {

    public RoleGrant toDomain(RoleGrantPO po) {
        if (po == null) {
            return null;
        }
        return new RoleGrant(new AuthorityCode(po.getAuthorityCode()),
                po.getDataScope() == null ? null : DataScope.valueOf(po.getDataScope()));
    }

    public RoleGrantPO toPO(TenantId tenantId, RoleId roleId, RoleGrant grant) {
        if (tenantId == null || roleId == null || grant == null) {
            throw new IllegalArgumentException("tenant, role, and grant must not be null");
        }
        RoleGrantPO po = new RoleGrantPO();
        po.setTenantId(tenantId.getValue());
        po.setRoleId(roleId.getValue());
        po.setAuthorityCode(grant.authorityCode().getValue());
        po.setDataScope(grant.dataScope() == null ? null : grant.dataScope().name());
        return po;
    }
}
