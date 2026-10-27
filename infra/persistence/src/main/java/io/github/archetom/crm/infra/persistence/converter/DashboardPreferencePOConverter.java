package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardModule;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.DashboardPreferencePO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/** Typed conversion for dashboard preference persistence. */
@Component
public class DashboardPreferencePOConverter {

    private final ObjectMapper objectMapper;

    public DashboardPreferencePOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DashboardPreference toDomain(DashboardPreferencePO po) {
        if (po == null) {
            return null;
        }
        try {
            Set<DashboardModule> hiddenModules = objectMapper.readValue(
                    po.getHiddenModules() == null ? "[]" : po.getHiddenModules(),
                    new TypeReference<Set<DashboardModule>>() { });
            return DashboardPreference.reconstitute(new DashboardPreference.Snapshot(
                    new TenantId(po.getTenantId()), new MemberId(po.getActorId()),
                    DashboardTimeRange.valueOf(po.getTimeRange()), po.getCustomStartDate(),
                    po.getCustomEndDate(), DashboardDataScope.valueOf(po.getDataScope()),
                    hiddenModules,
                    po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored dashboard preference is invalid", exception);
        }
    }

    public DashboardPreferencePO toPO(DashboardPreference preference) {
        if (preference == null) {
            return null;
        }
        try {
            DashboardPreferencePO po = new DashboardPreferencePO();
            po.setTenantId(preference.getTenantId().getValue());
            po.setActorId(preference.getMemberId().getValue());
            po.setTimeRange(preference.getTimeRange().name());
            po.setCustomStartDate(preference.getCustomStartDate());
            po.setCustomEndDate(preference.getCustomEndDate());
            po.setDataScope(preference.getDataScope().name());
            po.setHiddenModules(objectMapper.writeValueAsString(preference.getHiddenModules()));
            po.setVersion(preference.getVersion());
            po.setCreatedTime(preference.getCreatedTime());
            po.setUpdatedTime(preference.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Dashboard preference cannot be serialized", exception);
        }
    }
}
