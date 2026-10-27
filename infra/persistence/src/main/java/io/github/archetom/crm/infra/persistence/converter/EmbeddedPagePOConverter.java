package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.EmbeddedPage;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.EmbeddedPagePO;
import org.springframework.stereotype.Component;

@Component
public class EmbeddedPagePOConverter {

    public EmbeddedPage toDomain(EmbeddedPagePO po) {
        if (po == null) {
            return null;
        }
        return EmbeddedPage.reconstitute(new EmbeddedPage.Snapshot(
                new EmbeddedPageId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getCreatedByActorId()), BusinessObjectType.valueOf(po.getObjectType()),
                ClientPlatform.valueOf(po.getPlatform()), po.getName(), po.getDescription(),
                po.getUrlTemplate(), po.getSortOrder(), EmbeddedPageStatus.valueOf(po.getStatus()),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public EmbeddedPagePO toPO(EmbeddedPage page) {
        if (page == null) {
            return null;
        }
        EmbeddedPagePO po = new EmbeddedPagePO();
        po.setId(page.getId() == null ? null : page.getId().getValue());
        po.setTenantId(page.getTenantId().getValue());
        po.setCreatedByActorId(page.getCreatedBy().getValue());
        po.setObjectType(page.getObjectType().name());
        po.setPlatform(page.getPlatform().name());
        po.setName(page.getName());
        po.setDescription(page.getDescription());
        po.setUrlTemplate(page.getUrlTemplate());
        po.setSortOrder(page.getSortOrder());
        po.setStatus(page.getStatus().name());
        po.setVersion(page.getVersion());
        po.setCreatedTime(page.getCreatedTime());
        po.setUpdatedTime(page.getUpdatedTime());
        return po;
    }
}
