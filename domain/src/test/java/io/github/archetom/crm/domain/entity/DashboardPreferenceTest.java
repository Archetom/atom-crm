package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.DashboardPreferenceDomainException;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardModule;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardPreferenceTest {

    private final TenantId tenantId = new TenantId(9L);
    private final MemberId memberId = new MemberId(7L);

    @Test
    void relativeRangesRejectCustomDatesAndCustomRangesRemainBounded() {
        DashboardPreference preference = DashboardPreference.create(
                tenantId, memberId, DashboardTimeRange.CURRENT_MONTH,
                null, null, DashboardDataScope.SELF, Set.of());

        assertEquals(DashboardTimeRange.CURRENT_MONTH, preference.getTimeRange());
        assertNull(preference.getCustomStartDate());
        assertThrows(DashboardPreferenceDomainException.class,
                () -> preference.change(DashboardTimeRange.CURRENT_WEEK,
                        LocalDate.of(2026, 1, 1), null, DashboardDataScope.SELF, Set.of()));
        assertThrows(DashboardPreferenceDomainException.class,
                () -> preference.change(DashboardTimeRange.CUSTOM,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2),
                        DashboardDataScope.TENANT, Set.of()));
    }

    @Test
    void customRangeAndScopeRoundTripWithoutGrantingDomainEvents() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDateTime created = LocalDateTime.of(2026, 7, 18, 12, 0);
        DashboardPreference preference = DashboardPreference.reconstitute(
                new DashboardPreference.Snapshot(
                        tenantId, memberId, DashboardTimeRange.CUSTOM, start, end,
                        DashboardDataScope.DEPARTMENT_SUBTREE,
                        Set.of(DashboardModule.CUSTOMER_CONTACT_REMINDER), 3L, created,
                        created.plusMinutes(1)));

        assertEquals(start, preference.getCustomStartDate());
        assertEquals(end, preference.getCustomEndDate());
        assertEquals(DashboardDataScope.DEPARTMENT_SUBTREE, preference.getDataScope());
        assertEquals(Set.of(DashboardModule.CUSTOMER_CONTACT_REMINDER),
                preference.getHiddenModules());
        assertEquals(3L, preference.getVersion());
        assertFalse(preference.hasDomainEvents());
    }
}
