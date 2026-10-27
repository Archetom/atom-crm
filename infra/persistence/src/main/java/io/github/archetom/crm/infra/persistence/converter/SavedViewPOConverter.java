package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.entity.SavedViewCondition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.SavedViewPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class SavedViewPOConverter {

    private final ObjectMapper objectMapper;

    public SavedViewPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SavedView toDomain(SavedViewPO po) {
        if (po == null) {
            return null;
        }
        try {
            List<StoredCondition> stored = objectMapper.readValue(
                    po.getConditionsJson(), new TypeReference<List<StoredCondition>>() { });
            List<SavedViewCondition> conditions = stored.stream().map(value -> new SavedViewCondition(
                    new FilterCondition(new FieldCode(value.fieldCode()),
                            FilterOperator.valueOf(value.operator()), value.values()),
                    Boolean.TRUE.equals(value.exposed())))
                    .toList();
            return SavedView.reconstitute(new SavedView.Snapshot(
                    new SavedViewId(po.getId()), new TenantId(po.getTenantId()),
                    new MemberId(po.getActorId()), BusinessObjectType.valueOf(po.getObjectType()),
                    po.getName(), conditions, po.getSortOrder(), Boolean.TRUE.equals(po.getHidden()),
                    Boolean.TRUE.equals(po.getDefaultView()), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored saved view is invalid", exception);
        }
    }

    public SavedViewPO toPO(SavedView view) {
        if (view == null) {
            return null;
        }
        try {
            SavedViewPO po = new SavedViewPO();
            po.setId(view.getId() == null ? null : view.getId().getValue());
            po.setTenantId(view.getTenantId().getValue());
            po.setActorId(view.getMemberId().getValue());
            po.setObjectType(view.getObjectType().name());
            po.setName(view.getName());
            po.setConditionsJson(objectMapper.writeValueAsString(view.getSavedConditions().stream()
                    .map(value -> new StoredCondition(value.filterCondition().fieldCode().getValue(),
                            value.filterCondition().operator().name(), value.filterCondition().values(),
                            value.exposed())).toList()));
            po.setSortOrder(view.getSortOrder());
            po.setHidden(view.isHidden());
            po.setDefaultView(view.isDefaultView());
            po.setVersion(view.getVersion());
            po.setCreatedTime(view.getCreatedTime());
            po.setUpdatedTime(view.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Saved view cannot be serialized", exception);
        }
    }

    private record StoredCondition(String fieldCode, String operator, List<String> values,
                                   Boolean exposed) {
    }
}
