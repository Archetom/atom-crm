package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReceivablePlanPOConverter {
    private final ReceivableFieldValuePOConverter fieldConverter;

    public ReceivablePlanPOConverter(ReceivableFieldValuePOConverter fieldConverter) {
        this.fieldConverter = fieldConverter;
    }

    public ReceivablePlanPO toPO(ReceivablePlan value) {
        ReceivablePlanPO po = new ReceivablePlanPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setOwnerActorId(value.getOwnerMemberId().getValue());
        po.setCustomerId(value.getCustomerId().getValue());
        po.setContractId(value.getContractId().getValue());
        po.setInstallment(value.getInstallment());
        po.setDueDate(value.getDueDate());
        po.setPlannedAmount(value.getPlannedAmount());
        po.setPaymentMethod(value.getPaymentMethod());
        po.setReminderDays(value.getReminderDays());
        po.setRemark(value.getRemark());
        po.setStatus(value.getStatus().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    public ReceivablePlan toDomain(
            ReceivablePlanPO po, List<ReceivablePlanFieldValuePO> values,
            Map<Long, io.github.archetom.crm.domain.entity.FieldDefinition> fields) {
        return ReceivablePlan.reconstitute(new ReceivablePlan.Snapshot(
                new ReceivablePlanId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), new CustomerId(po.getCustomerId()),
                new ContractId(po.getContractId()), po.getInstallment(), po.getDueDate(),
                po.getPlannedAmount(), po.getPaymentMethod(), po.getReminderDays(),
                po.getRemark(), ReceivablePlanStatus.valueOf(po.getStatus()), values.stream()
                .map(value -> fieldConverter.toDomain(value.getFieldId(), value.getValuesJson(), fields))
                .toList(), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }
}
