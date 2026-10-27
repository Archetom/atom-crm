package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityCollaborator;
import io.github.archetom.crm.domain.entity.OpportunityFieldValue;
import io.github.archetom.crm.domain.entity.OpportunityProductLine;
import io.github.archetom.crm.domain.model.OpportunityCollaboratorAccess;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityProductLinePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OpportunityPOConverter {

    private final ObjectMapper objectMapper;

    public OpportunityPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpportunityPO toPO(Opportunity opportunity) {
        OpportunityPO po = new OpportunityPO();
        po.setId(opportunity.getId() == null
                ? null : opportunity.getId().getValue());
        po.setTenantId(opportunity.getTenantId().getValue());
        po.setOwnerActorId(opportunity.getOwnerMemberId().getValue());
        po.setCustomerId(opportunity.getCustomerId().getValue());
        po.setName(opportunity.getName());
        po.setPipelineCode(opportunity.getPipelineCode());
        po.setStageCode(opportunity.getStageCode());
        po.setExpectedCloseDate(opportunity.getExpectedCloseDate());
        po.setEstimatedAmount(opportunity.getEstimatedAmount());
        po.setNextContactTime(opportunity.getNextContactTime());
        po.setDescription(opportunity.getDescription());
        po.setOutcome(opportunity.getOutcome().name());
        po.setOutcomeTime(opportunity.getOutcomeTime());
        po.setOutcomeRemark(opportunity.getOutcomeRemark());
        po.setStatus(opportunity.getStatus().name());
        po.setVersion(opportunity.getVersion());
        po.setCreatedTime(opportunity.getCreatedTime());
        po.setUpdatedTime(opportunity.getUpdatedTime());
        return po;
    }

    public Opportunity toDomain(
            OpportunityPO po,
            List<OpportunityContactPO> contacts,
            List<OpportunityCollaboratorPO> collaborators,
            List<OpportunityFieldValuePO> fieldValues,
            List<OpportunityProductLinePO> productLines,
            Map<Long, FieldDefinition> fieldsById) {
        ContactId primary = contacts.stream()
                .filter(value -> Boolean.TRUE.equals(value.getPrimaryContact()))
                .map(value -> new ContactId(value.getContactId()))
                .findFirst().orElse(null);
        return Opportunity.reconstitute(new Opportunity.Snapshot(
                new OpportunityId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getOwnerActorId()), new CustomerId(po.getCustomerId()),
                primary, po.getName(), po.getPipelineCode(), po.getStageCode(),
                po.getExpectedCloseDate(), po.getEstimatedAmount(),
                po.getNextContactTime(), po.getDescription(),
                OpportunityOutcome.valueOf(po.getOutcome()), po.getOutcomeTime(),
                po.getOutcomeRemark(), OpportunityStatus.valueOf(po.getStatus()),
                contacts.stream().map(value -> new ContactId(value.getContactId())).toList(),
                collaborators.stream().map(value -> new OpportunityCollaborator(
                        new MemberId(value.getActorId()),
                        OpportunityCollaboratorAccess.valueOf(value.getAccessLevel()))).toList(),
                fieldValues.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                productLines.stream().map(this::toProductLine).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public OpportunityProductLinePO toPO(
            TenantId tenantId, OpportunityId opportunityId,
            OpportunityProductLine line) {
        OpportunityProductLinePO po = new OpportunityProductLinePO();
        po.setTenantId(tenantId.getValue());
        po.setOpportunityId(opportunityId.getValue());
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

    public OpportunityContactPO toPO(
            TenantId tenantId, OpportunityId opportunityId, CustomerId customerId,
            ContactId contactId, ContactId primaryContactId) {
        OpportunityContactPO po = new OpportunityContactPO();
        po.setTenantId(tenantId.getValue());
        po.setOpportunityId(opportunityId.getValue());
        po.setContactId(contactId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setPrimaryContact(primaryContactId != null
                && primaryContactId.sameValueAs(contactId));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    public OpportunityCollaboratorPO toPO(
            TenantId tenantId, OpportunityId opportunityId,
            OpportunityCollaborator collaborator) {
        OpportunityCollaboratorPO po = new OpportunityCollaboratorPO();
        po.setTenantId(tenantId.getValue());
        po.setOpportunityId(opportunityId.getValue());
        po.setActorId(collaborator.memberId().getValue());
        po.setAccessLevel(collaborator.access().name());
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    public OpportunityFieldValuePO toPO(
            TenantId tenantId, OpportunityId opportunityId,
            OpportunityFieldValue value, FieldDefinition field) {
        try {
            OpportunityFieldValuePO po = new OpportunityFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setOpportunityId(opportunityId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1
                    ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Opportunity field value cannot be serialized", exception);
        }
    }

    private OpportunityFieldValue toFieldValue(
            OpportunityFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException(
                    "Stored opportunity field definition is missing");
        }
        try {
            List<String> values = objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { });
            return new OpportunityFieldValue(field.getCode(), values);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Stored opportunity field value is invalid", exception);
        }
    }

    private OpportunityProductLine toProductLine(OpportunityProductLinePO po) {
        return new OpportunityProductLine(new ProductId(po.getProductId()),
                po.getProductName(), po.getProductCode(), po.getCategoryName(),
                po.getUnit(), po.getListPrice(), po.getUnitPrice(), po.getQuantity(),
                po.getDiscountRate(), po.getLineAmount(), po.getDisplayOrder());
    }
}
