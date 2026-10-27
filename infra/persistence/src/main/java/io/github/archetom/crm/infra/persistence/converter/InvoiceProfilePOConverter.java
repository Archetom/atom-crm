package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.model.InvoiceTitleType;
import io.github.archetom.crm.domain.model.InvoiceProfileStatus;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceProfileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceProfilePO;
import org.springframework.stereotype.Component;

@Component
public class InvoiceProfilePOConverter {
    public InvoiceProfilePO toPO(InvoiceProfile profile) {
        InvoiceProfilePO po = new InvoiceProfilePO();
        po.setId(profile.getId() == null ? null : profile.getId().getValue());
        po.setTenantId(profile.getTenantId().getValue());
        po.setCustomerId(profile.getCustomerId().getValue());
        po.setTitleType(profile.getTitleType().name());
        po.setTitle(profile.getTitle());
        po.setTaxNumber(profile.getTaxNumber());
        po.setBankName(profile.getBankName());
        po.setBankAccount(profile.getBankAccount());
        po.setRegisteredAddress(profile.getRegisteredAddress());
        po.setRegisteredPhone(profile.getRegisteredPhone());
        po.setRemark(profile.getRemark());
        po.setStatus(profile.getStatus().name());
        po.setVersion(profile.getVersion());
        po.setCreatedTime(profile.getCreatedTime());
        po.setUpdatedTime(profile.getUpdatedTime());
        return po;
    }

    public InvoiceProfile toDomain(InvoiceProfilePO po) {
        return InvoiceProfile.reconstitute(new InvoiceProfile.Snapshot(
                new InvoiceProfileId(po.getId()), new TenantId(po.getTenantId()),
                new CustomerId(po.getCustomerId()), InvoiceTitleType.valueOf(po.getTitleType()),
                po.getTitle(), po.getTaxNumber(), po.getBankName(), po.getBankAccount(),
                po.getRegisteredAddress(), po.getRegisteredPhone(), po.getRemark(),
                InvoiceProfileStatus.valueOf(po.getStatus()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }
}
