package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivableFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReceivablePOConverter {
    private final ReceivableFieldValuePOConverter fieldConverter;

    public ReceivablePOConverter(ReceivableFieldValuePOConverter fieldConverter) {
        this.fieldConverter = fieldConverter;
    }

    public ReceivablePO toPO(Receivable value) {
        ReceivablePO po = new ReceivablePO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setOwnerActorId(value.getOwnerMemberId().getValue());
        po.setCustomerId(value.getCustomerId().getValue());
        po.setContractId(value.getContractId().getValue());
        po.setReceivablePlanId(value.getPlanId() == null ? null : value.getPlanId().getValue());
        po.setReceiptNumber(value.getReceiptNumber());
        po.setReceivedDate(value.getReceivedDate());
        po.setPaymentMethod(value.getPaymentMethod());
        po.setAmount(value.getAmount());
        po.setRemark(value.getRemark());
        po.setStatus(value.getStatus().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    public Receivable toDomain(
            ReceivablePO po, List<ReceivableFieldValuePO> values,
            Map<Long, io.github.archetom.crm.domain.entity.FieldDefinition> fields) {
        return Receivable.reconstitute(new Receivable.Snapshot(
                new ReceivableId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), new CustomerId(po.getCustomerId()),
                new ContractId(po.getContractId()), po.getReceivablePlanId() == null ? null
                : new ReceivablePlanId(po.getReceivablePlanId()), po.getReceiptNumber(),
                po.getReceivedDate(), po.getPaymentMethod(), po.getAmount(), po.getRemark(),
                ReceivableStatus.valueOf(po.getStatus()), values.stream().map(value ->
                fieldConverter.toDomain(value.getFieldId(), value.getValuesJson(), fields)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }
}
