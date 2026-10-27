package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReturnVisitFieldValue;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitFieldValuePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ReturnVisitFieldValuePOConverter {
    private final ObjectMapper objectMapper;

    public ReturnVisitFieldValuePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReturnVisitFieldValuePO toPO(TenantId tenantId, ReturnVisitId visitId,
                                        ReturnVisitFieldValue value, FieldDefinition field) {
        try {
            ReturnVisitFieldValuePO po = new ReturnVisitFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setReturnVisitId(visitId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1 ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Return visit field value cannot be serialized", exception);
        }
    }

    public ReturnVisitFieldValue toDomain(Long fieldId, String valuesJson,
                                          Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(fieldId);
        if (field == null) throw new IllegalStateException("Stored return visit field is missing");
        try {
            return new ReturnVisitFieldValue(field.getCode(), objectMapper.readValue(valuesJson,
                    new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored return visit field value is invalid", exception);
        }
    }
}
