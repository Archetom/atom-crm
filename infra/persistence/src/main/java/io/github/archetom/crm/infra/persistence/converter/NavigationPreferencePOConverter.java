package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.NavigationPreferencePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

/** JSON conversion for bounded navigation code lists. */
@Component
public class NavigationPreferencePOConverter {

    private final ObjectMapper objectMapper;

    public NavigationPreferencePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NavigationPreference toDomain(NavigationPreferencePO po) {
        if (po == null) {
            return null;
        }
        try {
            List<String> orderedCodes = objectMapper.readValue(
                    po.getOrderedCodes(), new TypeReference<List<String>>() { });
            Set<String> hiddenCodes = objectMapper.readValue(
                    po.getHiddenCodes(), new TypeReference<Set<String>>() { });
            return NavigationPreference.reconstitute(new NavigationPreference.Snapshot(
                    new TenantId(po.getTenantId()), new MemberId(po.getActorId()),
                    orderedCodes, hiddenCodes, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored navigation preference is invalid", exception);
        }
    }

    public NavigationPreferencePO toPO(NavigationPreference preference) {
        if (preference == null) {
            return null;
        }
        try {
            NavigationPreferencePO po = new NavigationPreferencePO();
            po.setTenantId(preference.getTenantId().getValue());
            po.setActorId(preference.getMemberId().getValue());
            po.setOrderedCodes(objectMapper.writeValueAsString(preference.getOrderedCodes()));
            po.setHiddenCodes(objectMapper.writeValueAsString(preference.getHiddenCodes()));
            po.setVersion(preference.getVersion());
            po.setCreatedTime(preference.getCreatedTime());
            po.setUpdatedTime(preference.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Navigation preference cannot be serialized", exception);
        }
    }
}
