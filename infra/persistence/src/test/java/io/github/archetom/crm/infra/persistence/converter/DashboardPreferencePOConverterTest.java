package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardModule;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.DashboardPreferencePO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DashboardPreferencePOConverterTest {

    @Test
    void converterPreservesTypedValuesCompositeIdentityVersionAndAuditTimes() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 18, 12, 0);
        DashboardPreference preference = DashboardPreference.reconstitute(
                new DashboardPreference.Snapshot(
                        new TenantId(9L), new MemberId(7L), DashboardTimeRange.CUSTOM,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        DashboardDataScope.DEPARTMENT,
                        Set.of(DashboardModule.CUSTOMER_CONTACT_REMINDER), 4L, created,
                        created.plusMinutes(1)));
        DashboardPreferencePOConverter converter = new DashboardPreferencePOConverter(new ObjectMapper());

        DashboardPreferencePO po = converter.toPO(preference);
        DashboardPreference restored = converter.toDomain(po);

        assertEquals(9L, po.getTenantId());
        assertEquals(7L, po.getActorId());
        assertEquals("CUSTOM", po.getTimeRange());
        assertEquals("DEPARTMENT", po.getDataScope());
        assertEquals("[\"CUSTOMER_CONTACT_REMINDER\"]", po.getHiddenModules());
        assertEquals(Set.of(DashboardModule.CUSTOMER_CONTACT_REMINDER),
                restored.getHiddenModules());
        assertEquals(4L, restored.getVersion());
        assertEquals(created, restored.getCreatedTime());
        assertEquals(LocalDate.of(2026, 12, 31), restored.getCustomEndDate());
        assertFalse(restored.hasDomainEvents());
    }
}
