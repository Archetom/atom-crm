package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.FieldLayoutPreferencePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FieldLayoutPreferencePOConverter {

    private final ObjectMapper objectMapper;

    public FieldLayoutPreferencePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FieldLayoutPreference toDomain(FieldLayoutPreferencePO po) {
        if (po == null) {
            return null;
        }
        try {
            List<String> ordered = readList(po.getOrderedCodes());
            Set<String> hidden = readSet(po.getHiddenCodes());
            Set<String> pinned = readSet(po.getPinnedCodes());
            Map<String, Integer> widths = objectMapper.readValue(
                    po.getColumnWidths(), new TypeReference<Map<String, Integer>>() { });
            return FieldLayoutPreference.reconstitute(new FieldLayoutPreference.Snapshot(
                    new TenantId(po.getTenantId()), new MemberId(po.getActorId()),
                    BusinessObjectType.valueOf(po.getObjectType()),
                    ordered.stream().map(FieldCode::new).toList(),
                    hidden.stream().map(FieldCode::new).collect(Collectors.toSet()),
                    pinned.stream().map(FieldCode::new).collect(Collectors.toSet()),
                    widths.entrySet().stream().collect(Collectors.toMap(
                            entry -> new FieldCode(entry.getKey()), Map.Entry::getValue,
                            (left, right) -> left, LinkedHashMap::new)),
                    po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored field layout preference is invalid", exception);
        }
    }

    public FieldLayoutPreferencePO toPO(FieldLayoutPreference preference) {
        if (preference == null) {
            return null;
        }
        try {
            FieldLayoutPreferencePO po = new FieldLayoutPreferencePO();
            po.setTenantId(preference.getTenantId().getValue());
            po.setActorId(preference.getMemberId().getValue());
            po.setObjectType(preference.getObjectType().name());
            po.setOrderedCodes(objectMapper.writeValueAsString(values(preference.getOrderedCodes())));
            po.setHiddenCodes(objectMapper.writeValueAsString(values(preference.getHiddenCodes())));
            po.setPinnedCodes(objectMapper.writeValueAsString(values(preference.getPinnedCodes())));
            po.setColumnWidths(objectMapper.writeValueAsString(preference.getColumnWidths().entrySet()
                    .stream().collect(Collectors.toMap(entry -> entry.getKey().getValue(),
                            Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new))));
            po.setVersion(preference.getVersion());
            po.setCreatedTime(preference.getCreatedTime());
            po.setUpdatedTime(preference.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Field layout preference cannot be serialized", exception);
        }
    }

    private List<String> readList(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<String>>() { });
    }

    private Set<String> readSet(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Set<String>>() { });
    }

    private List<String> values(java.util.Collection<FieldCode> codes) {
        return codes.stream().map(FieldCode::getValue).toList();
    }
}
