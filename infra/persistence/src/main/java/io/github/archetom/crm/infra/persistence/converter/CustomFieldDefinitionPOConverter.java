package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldStatus;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class CustomFieldDefinitionPOConverter {

    private final ObjectMapper objectMapper;

    public CustomFieldDefinitionPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FieldDefinition toDomain(CustomFieldDefinitionPO po) {
        if (po == null) {
            return null;
        }
        try {
            List<FieldOption> options = objectMapper.readValue(
                    po.getOptionsJson(), new TypeReference<List<FieldOption>>() { });
            return FieldDefinition.reconstitute(new FieldDefinition.Snapshot(
                    new FieldDefinitionId(po.getId()), new TenantId(po.getTenantId()),
                    BusinessObjectType.valueOf(po.getObjectType()), new FieldCode(po.getFieldCode()),
                    po.getDisplayName(), po.getDescription(), CustomFieldType.valueOf(po.getFieldType()),
                    Boolean.TRUE.equals(po.getBuiltin()), Boolean.TRUE.equals(po.getRequired()),
                    Boolean.TRUE.equals(po.getUniqueValue()), Boolean.TRUE.equals(po.getSearchable()),
                    CustomFieldStatus.valueOf(po.getStatus()), po.getDisplayOrder(), po.getSectionName(),
                    options, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored custom field definition is invalid", exception);
        }
    }

    public CustomFieldDefinitionPO toPO(FieldDefinition field) {
        if (field == null) {
            return null;
        }
        try {
            CustomFieldDefinitionPO po = new CustomFieldDefinitionPO();
            po.setId(field.getId() == null ? null : field.getId().getValue());
            po.setTenantId(field.getTenantId().getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldCode(field.getCode().getValue());
            po.setDisplayName(field.getDisplayName());
            po.setDescription(field.getDescription());
            po.setFieldType(field.getFieldType().name());
            po.setBuiltin(field.isBuiltin());
            po.setRequired(field.isRequired());
            po.setUniqueValue(field.isUnique());
            po.setSearchable(field.isSearchable());
            po.setStatus(field.getStatus().name());
            po.setDisplayOrder(field.getDisplayOrder());
            po.setSectionName(field.getSectionName());
            po.setOptionsJson(objectMapper.writeValueAsString(field.getOptions()));
            po.setVersion(field.getVersion());
            po.setCreatedTime(field.getCreatedTime());
            po.setUpdatedTime(field.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Custom field definition cannot be serialized", exception);
        }
    }
}
