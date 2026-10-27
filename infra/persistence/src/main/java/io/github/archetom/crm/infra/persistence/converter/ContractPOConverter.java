package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.ContractCollaborator;
import io.github.archetom.crm.domain.entity.ContractFieldValue;
import io.github.archetom.crm.domain.entity.ContractProductLine;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.ContractCollaboratorAccess;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractProductLinePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ContractPOConverter {
    private final ObjectMapper objectMapper;

    public ContractPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContractPO toPO(Contract contract) {
        ContractPO po = new ContractPO();
        po.setId(contract.getId() == null ? null : contract.getId().getValue());
        po.setTenantId(contract.getTenantId().getValue());
        po.setOwnerActorId(contract.getOwnerMemberId().getValue());
        po.setCustomerId(contract.getCustomerId().getValue());
        po.setOpportunityId(contract.getOpportunityId() == null ? null
                : contract.getOpportunityId().getValue());
        po.setContactId(contract.getContactId() == null ? null
                : contract.getContactId().getValue());
        po.setName(contract.getName());
        po.setContractNumber(contract.getNumber());
        po.setContractNumberKey(contract.getNumberKey());
        po.setOrderDate(contract.getOrderDate());
        po.setStartDate(contract.getStartDate());
        po.setEndDate(contract.getEndDate());
        po.setContractType(contract.getContractType());
        po.setPaymentType(contract.getPaymentType());
        po.setRemark(contract.getRemark());
        po.setSubtotal(contract.getSubtotal());
        po.setDiscountRate(contract.getDiscountRate());
        po.setTotalAmount(contract.getTotalAmount());
        po.setStatus(contract.getStatus().name());
        po.setVersion(contract.getVersion());
        po.setCreatedTime(contract.getCreatedTime());
        po.setUpdatedTime(contract.getUpdatedTime());
        return po;
    }

    public Contract toDomain(
            ContractPO po, List<ContractCollaboratorPO> collaborators,
            List<ContractProductLinePO> products,
            List<ContractFieldValuePO> values,
            Map<Long, FieldDefinition> fieldsById) {
        return Contract.reconstitute(new Contract.Snapshot(
                new ContractId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), new CustomerId(po.getCustomerId()),
                po.getOpportunityId() == null ? null : new OpportunityId(po.getOpportunityId()),
                po.getContactId() == null ? null : new ContactId(po.getContactId()),
                po.getName(), po.getContractNumber(), po.getContractNumberKey(),
                po.getOrderDate(), po.getStartDate(), po.getEndDate(), po.getContractType(),
                po.getPaymentType(), po.getRemark(), po.getSubtotal(), po.getDiscountRate(),
                po.getTotalAmount(), ContractStatus.valueOf(po.getStatus()),
                products.stream().map(this::toProductLine).toList(),
                collaborators.stream().map(value -> new ContractCollaborator(
                        new MemberId(value.getActorId()),
                        ContractCollaboratorAccess.valueOf(value.getAccessLevel()),
                        value.getEffectiveUntil())).toList(),
                values.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public ContractCollaboratorPO toPO(
            TenantId tenantId, ContractId contractId,
            ContractCollaborator collaborator) {
        ContractCollaboratorPO po = new ContractCollaboratorPO();
        po.setTenantId(tenantId.getValue());
        po.setContractId(contractId.getValue());
        po.setActorId(collaborator.memberId().getValue());
        po.setAccessLevel(collaborator.access().name());
        po.setEffectiveUntil(collaborator.effectiveUntil());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public ContractProductLinePO toPO(
            TenantId tenantId, ContractId contractId, ContractProductLine line) {
        ContractProductLinePO po = new ContractProductLinePO();
        po.setTenantId(tenantId.getValue());
        po.setContractId(contractId.getValue());
        po.setProductId(line.productId().getValue());
        po.setProductName(line.productName());
        po.setProductCode(line.productCode());
        po.setCategoryName(line.categoryName());
        po.setUnit(line.unit());
        po.setListPrice(line.listPrice());
        po.setUnitPrice(line.unitPrice());
        po.setQuantity(line.quantity());
        po.setDiscountRate(line.discountRate());
        po.setLineAmount(line.lineAmount());
        po.setDisplayOrder(line.displayOrder());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public ContractFieldValuePO toPO(
            TenantId tenantId, ContractId contractId,
            ContractFieldValue value, FieldDefinition field) {
        try {
            ContractFieldValuePO po = new ContractFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setContractId(contractId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Contract field value cannot be serialized", exception);
        }
    }

    private ContractFieldValue toFieldValue(
            ContractFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException("Stored contract field definition is missing");
        }
        try {
            return new ContractFieldValue(field.getCode(), objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored contract field value is invalid", exception);
        }
    }

    private ContractProductLine toProductLine(ContractProductLinePO po) {
        return new ContractProductLine(new ProductId(po.getProductId()),
                po.getProductName(), po.getProductCode(), po.getCategoryName(), po.getUnit(),
                po.getListPrice(), po.getUnitPrice(), po.getQuantity(), po.getDiscountRate(),
                po.getLineAmount(), po.getDisplayOrder());
    }
}
