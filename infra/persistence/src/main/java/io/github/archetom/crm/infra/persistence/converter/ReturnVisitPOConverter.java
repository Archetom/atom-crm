package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.model.ReturnVisitForm;
import io.github.archetom.crm.domain.model.ReturnVisitSatisfaction;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReturnVisitPOConverter {
    private final ReturnVisitFieldValuePOConverter fieldConverter;

    public ReturnVisitPOConverter(ReturnVisitFieldValuePOConverter fieldConverter) {
        this.fieldConverter = fieldConverter;
    }

    public ReturnVisitPO toPO(ReturnVisit value) {
        ReturnVisitPO po = new ReturnVisitPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setOwnerActorId(value.getOwnerMemberId().getValue());
        po.setCustomerId(value.getCustomerId().getValue());
        po.setContactId(value.getContactId() == null ? null : value.getContactId().getValue());
        po.setContractId(value.getContractId() == null ? null : value.getContractId().getValue());
        po.setVisitNumber(value.getVisitNumber());
        po.setVisitTime(value.getVisitTime());
        po.setVisitForm(value.getVisitForm() == null ? null : value.getVisitForm().name());
        po.setSatisfaction(value.getSatisfaction() == null ? null : value.getSatisfaction().name());
        po.setRemark(value.getRemark());
        po.setStatus(value.getStatus().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    public ReturnVisit toDomain(ReturnVisitPO root, List<ReturnVisitFieldValuePO> values,
                                Map<Long, FieldDefinition> fields) {
        return ReturnVisit.reconstitute(new ReturnVisit.Snapshot(new ReturnVisitId(root.getId()),
                new TenantId(root.getTenantId()), new MemberId(root.getOwnerActorId()),
                new CustomerId(root.getCustomerId()), root.getContactId() == null ? null
                : new ContactId(root.getContactId()), root.getContractId() == null ? null
                : new ContractId(root.getContractId()), root.getVisitNumber(), root.getVisitTime(),
                root.getRemark(), ReturnVisitForm.optional(root.getVisitForm()),
                ReturnVisitSatisfaction.optional(root.getSatisfaction()), ReturnVisitStatus.valueOf(root.getStatus()), values.stream().map(value ->
                fieldConverter.toDomain(value.getFieldId(), value.getValuesJson(), fields)).toList(),
                root.getVersion(), root.getCreatedTime(), root.getUpdatedTime()));
    }
}
