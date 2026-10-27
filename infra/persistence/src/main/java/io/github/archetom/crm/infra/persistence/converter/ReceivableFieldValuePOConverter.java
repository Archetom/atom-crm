package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReceivableFieldValue;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivableFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanFieldValuePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ReceivableFieldValuePOConverter {
    private final ObjectMapper objectMapper;

    public ReceivableFieldValuePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReceivableFieldValuePO toPO(
            TenantId tenantId, ReceivableId rootId,
            ReceivableFieldValue value, FieldDefinition field) {
        try {
            ReceivableFieldValuePO po = new ReceivableFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setReceivableId(rootId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Receivable field value cannot be serialized", exception);
        }
    }

    public ReceivablePlanFieldValuePO toPlanPO(
            TenantId tenantId, ReceivablePlanId rootId,
            ReceivableFieldValue value, FieldDefinition field) {
        try {
            ReceivablePlanFieldValuePO po = new ReceivablePlanFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setReceivablePlanId(rootId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Receivable plan field value cannot be serialized", exception);
        }
    }

    public ReceivableFieldValue toDomain(
            Long fieldId, String valuesJson, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(fieldId);
        if (field == null) {
            throw new IllegalStateException("Stored receivable field definition is missing");
        }
        try {
            return new ReceivableFieldValue(field.getCode(), objectMapper.readValue(
                    valuesJson, new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored receivable field value is invalid", exception);
        }
    }
}
