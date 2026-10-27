package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReportDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReportDefinitionStatus;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.model.ReportMeasureType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReportDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ReportDefinitionPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class ReportDefinitionPOConverter {
    private final ObjectMapper objectMapper;

    public ReportDefinitionPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReportDefinition toDomain(ReportDefinitionPO po) {
        if (po == null) return null;
        try {
            List<StoredFilter> storedFilters = objectMapper.readValue(
                    po.getFiltersJson(), new TypeReference<List<StoredFilter>>() { });
            List<StoredMeasure> storedMeasures = objectMapper.readValue(
                    po.getMeasuresJson(), new TypeReference<List<StoredMeasure>>() { });
            List<FilterCondition> filters = storedFilters.stream().map(value ->
                    new FilterCondition(new FieldCode(value.fieldCode()),
                            FilterOperator.valueOf(value.operator()), value.values())).toList();
            List<ReportMeasure> measures = storedMeasures.stream().map(value ->
                    new ReportMeasure(ReportMeasureType.valueOf(value.type()),
                            value.fieldCode() == null ? null : new FieldCode(value.fieldCode())))
                    .toList();
            return ReportDefinition.reconstitute(new ReportDefinition.Snapshot(
                    new ReportDefinitionId(po.getId()), new TenantId(po.getTenantId()),
                    new MemberId(po.getCreatorMemberId()), po.getName(), po.getNormalizedName(),
                    po.getDescription(), BusinessObjectType.valueOf(po.getObjectType()),
                    new FieldCode(po.getTimeFieldCode()),
                    ReportGranularity.valueOf(po.getGranularity()),
                    po.getDimensionFieldCode() == null ? null
                            : new FieldCode(po.getDimensionFieldCode()),
                    filters, measures, ReportDefinitionStatus.valueOf(po.getStatus()),
                    po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored report definition is invalid", exception);
        }
    }

    public ReportDefinitionPO toPO(ReportDefinition definition) {
        if (definition == null) return null;
        try {
            ReportDefinitionPO po = new ReportDefinitionPO();
            po.setId(definition.getId() == null ? null : definition.getId().getValue());
            po.setTenantId(definition.getTenantId().getValue());
            po.setCreatorMemberId(definition.getCreatorMemberId().getValue());
            po.setName(definition.getName());
            po.setNormalizedName(definition.getNormalizedName());
            po.setDescription(definition.getDescription());
            po.setObjectType(definition.getObjectType().name());
            po.setTimeFieldCode(definition.getTimeFieldCode().getValue());
            po.setGranularity(definition.getGranularity().name());
            po.setDimensionFieldCode(definition.getDimensionFieldCode() == null ? null
                    : definition.getDimensionFieldCode().getValue());
            po.setFiltersJson(objectMapper.writeValueAsString(definition.getFilters().stream()
                    .map(value -> new StoredFilter(value.fieldCode().getValue(),
                            value.operator().name(), value.values())).toList()));
            po.setMeasuresJson(objectMapper.writeValueAsString(definition.getMeasures().stream()
                    .map(value -> new StoredMeasure(value.type().name(),
                            value.fieldCode() == null ? null : value.fieldCode().getValue()))
                    .toList()));
            po.setStatus(definition.getStatus().name());
            po.setVersion(definition.getVersion());
            po.setCreatedTime(definition.getCreatedTime());
            po.setUpdatedTime(definition.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Report definition cannot be serialized", exception);
        }
    }

    private record StoredFilter(String fieldCode, String operator, List<String> values) { }
    private record StoredMeasure(String type, String fieldCode) { }
}
