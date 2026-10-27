package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadIntakeClientPO;
import org.springframework.stereotype.Component;

@Component
public class LeadIntakeClientPOConverter {
    public LeadIntakeClientPO toPO(LeadIntakeClient client) {
        if (client == null) {
            return null;
        }
        LeadIntakeClientPO po = new LeadIntakeClientPO();
        po.setId(client.getId() == null ? null : client.getId().getValue());
        po.setTenantId(client.getTenantId().getValue());
        po.setOwnerActorId(client.getOwnerMemberId().getValue());
        po.setKeyId(client.getKeyId());
        po.setApiKeyHash(client.getApiKeyHash());
        po.setActive(client.isActive());
        po.setVersion(client.getVersion());
        po.setCreatedTime(client.getCreatedTime());
        po.setUpdatedTime(client.getUpdatedTime());
        return po;
    }

    public LeadIntakeClient toDomain(LeadIntakeClientPO po) {
        if (po == null) {
            return null;
        }
        return LeadIntakeClient.reconstitute(new LeadIntakeClient.Snapshot(
                new LeadIntakeClientId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), po.getKeyId(), po.getApiKeyHash(),
                Boolean.TRUE.equals(po.getActive()), po.getVersion(), po.getCreatedTime(),
                po.getUpdatedTime()));
    }
}
