package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.DashboardPreferenceDomainException;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardModule;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.model.DashboardTimeWindow;
import io.github.archetom.crm.domain.valueobject.DashboardPreferenceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Current-member dashboard defaults. The selected data scope is a presentation
 * restriction and never grants access to a source business record.
 */
@Getter
public class DashboardPreference extends AggregateRoot<DashboardPreferenceId> {

    public static final DashboardTimeRange DEFAULT_TIME_RANGE = DashboardTimeRange.CURRENT_MONTH;
    public static final DashboardDataScope DEFAULT_DATA_SCOPE = DashboardDataScope.SELF;
    public static final int MAX_CUSTOM_RANGE_DAYS = DashboardTimeWindow.MAXIMUM_DAYS;
    private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private DashboardPreferenceId id;
    private TenantId tenantId;
    private MemberId memberId;
    private DashboardTimeRange timeRange;
    private LocalDate customStartDate;
    private LocalDate customEndDate;
    private DashboardDataScope dataScope;
    private Set<DashboardModule> hiddenModules = Set.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private DashboardPreference() {
    }

    public record Snapshot(TenantId tenantId, MemberId memberId,
                           DashboardTimeRange timeRange, LocalDate customStartDate,
                           LocalDate customEndDate, DashboardDataScope dataScope,
                           Set<DashboardModule> hiddenModules,
                           Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static DashboardPreference create(
            TenantId tenantId, MemberId memberId, DashboardTimeRange timeRange,
            LocalDate customStartDate, LocalDate customEndDate,
            DashboardDataScope dataScope, Collection<DashboardModule> hiddenModules) {
        DashboardPreference preference = new DashboardPreference();
        preference.tenantId = requireTenant(tenantId);
        preference.memberId = requireMember(memberId);
        preference.id = new DashboardPreferenceId(preference.tenantId, preference.memberId);
        preference.change(timeRange, customStartDate, customEndDate, dataScope, hiddenModules);
        preference.createdTime = preference.updatedTime;
        return preference;
    }

    public static DashboardPreference reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new DashboardPreferenceDomainException(
                    "dashboard preference snapshot must not be empty");
        }
        DashboardPreference preference = new DashboardPreference();
        preference.tenantId = requireTenant(snapshot.tenantId());
        preference.memberId = requireMember(snapshot.memberId());
        preference.id = new DashboardPreferenceId(preference.tenantId, preference.memberId);
        preference.assign(snapshot.timeRange(), snapshot.customStartDate(),
                snapshot.customEndDate(), snapshot.dataScope(), snapshot.hiddenModules());
        preference.restoreVersion(snapshot.version());
        preference.createdTime = snapshot.createdTime();
        preference.updatedTime = snapshot.updatedTime();
        return preference;
    }

    public void change(DashboardTimeRange timeRange, LocalDate customStartDate,
                       LocalDate customEndDate, DashboardDataScope dataScope,
                       Collection<DashboardModule> hiddenModules) {
        assign(timeRange, customStartDate, customEndDate, dataScope, hiddenModules);
        updatedTime = LocalDateTime.now();
    }

    public void onPersisted(Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void assign(DashboardTimeRange timeRange, LocalDate customStartDate,
                        LocalDate customEndDate, DashboardDataScope dataScope,
                        Collection<DashboardModule> hiddenModules) {
        if (timeRange == null || dataScope == null) {
            throw new DashboardPreferenceDomainException(
                    "dashboard time range and data scope must not be empty");
        }
        if (timeRange == DashboardTimeRange.CUSTOM) {
            requireCustomRange(customStartDate, customEndDate);
        } else if (customStartDate != null || customEndDate != null) {
            throw new DashboardPreferenceDomainException(
                    "custom dates are only valid for the CUSTOM dashboard time range");
        }
        this.timeRange = timeRange;
        this.customStartDate = customStartDate;
        this.customEndDate = customEndDate;
        this.dataScope = dataScope;
        this.hiddenModules = normalizedHiddenModules(hiddenModules);
    }

    private static void requireCustomRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new DashboardPreferenceDomainException(
                    "CUSTOM dashboard time range requires both dates");
        }
        if (startDate.isBefore(MIN_DATE) || endDate.isAfter(MAX_DATE)
                || endDate.isBefore(startDate)) {
            throw new DashboardPreferenceDomainException(
                    "dashboard custom date range is invalid");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays > MAX_CUSTOM_RANGE_DAYS) {
            throw new DashboardPreferenceDomainException(
                    "dashboard custom date range exceeds the supported limit");
        }
    }

    private static Set<DashboardModule> normalizedHiddenModules(
            Collection<DashboardModule> hiddenModules) {
        if (hiddenModules == null || hiddenModules.isEmpty()) {
            return Set.of();
        }
        if (hiddenModules.size() > DashboardModule.values().length) {
            throw new DashboardPreferenceDomainException(
                    "dashboard hidden modules exceed the catalog limit");
        }
        EnumSet<DashboardModule> result = EnumSet.noneOf(DashboardModule.class);
        for (DashboardModule module : hiddenModules) {
            if (module == null) {
                throw new DashboardPreferenceDomainException(
                        "dashboard hidden module must not be empty");
            }
            result.add(module);
        }
        return Set.copyOf(result);
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new DashboardPreferenceDomainException(
                    "dashboard preference tenant must not be empty");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId) {
        if (memberId == null) {
            throw new DashboardPreferenceDomainException(
                    "dashboard preference member must not be empty");
        }
        return memberId;
    }
}
