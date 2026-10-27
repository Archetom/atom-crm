package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.PerformanceTargetDomainException;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.DepartmentPerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A versioned department target for one calendar period and server-defined measure.
 *
 * <p>Annual, quarterly and monthly targets are deliberately independent management values.
 * They are not inferred from member targets and never widen the dashboard's business scope.</p>
 */
@Getter
public class DepartmentPerformanceTarget extends AggregateRoot<DepartmentPerformanceTargetId> {

    private static final int MINIMUM_YEAR = 2000;
    private static final int MAXIMUM_YEAR = 9999;
    private static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("99999999999999999.99");

    private DepartmentPerformanceTargetId id;
    private TenantId tenantId;
    private DepartmentId departmentId;
    private PerformanceTargetMetric metric;
    private int targetYear;
    private PerformanceTargetPeriod period;
    private int periodNumber;
    private BigDecimal amount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private DepartmentPerformanceTarget() {
    }

    public record Snapshot(
            DepartmentPerformanceTargetId id,
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber,
            BigDecimal amount,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static DepartmentPerformanceTarget create(
            TenantId tenantId,
            DepartmentId departmentId,
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber,
            BigDecimal amount) {
        DepartmentPerformanceTarget target = new DepartmentPerformanceTarget();
        target.tenantId = requireTenant(tenantId);
        target.departmentId = requireDepartment(departmentId);
        target.assign(metric, targetYear, period, periodNumber, amount);
        LocalDateTime now = LocalDateTime.now();
        target.createdTime = now;
        target.updatedTime = now;
        return target;
    }

    public static DepartmentPerformanceTarget reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new PerformanceTargetDomainException("department performance target snapshot is incomplete");
        }
        DepartmentPerformanceTarget target = new DepartmentPerformanceTarget();
        target.id = snapshot.id();
        target.tenantId = requireTenant(snapshot.tenantId());
        target.departmentId = requireDepartment(snapshot.departmentId());
        target.assign(snapshot.metric(), snapshot.targetYear(), snapshot.period(),
                snapshot.periodNumber(), snapshot.amount());
        target.restoreVersion(snapshot.version());
        target.createdTime = snapshot.createdTime();
        target.updatedTime = snapshot.updatedTime();
        return target;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = normalizeAmount(amount);
        updatedTime = LocalDateTime.now();
    }

    public void onPersisted(
            DepartmentPerformanceTargetId id,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
        if (id == null) {
            throw new PerformanceTargetDomainException("persisted department performance target ID is required");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new PerformanceTargetDomainException("persisted department performance target ID cannot change");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void assign(
            PerformanceTargetMetric metric,
            int targetYear,
            PerformanceTargetPeriod period,
            int periodNumber,
            BigDecimal amount) {
        if (metric == null) {
            throw new PerformanceTargetDomainException("department performance target metric is required");
        }
        if (targetYear < MINIMUM_YEAR || targetYear > MAXIMUM_YEAR) {
            throw new PerformanceTargetDomainException("department performance target year is invalid");
        }
        validatePeriod(period, periodNumber);
        this.metric = metric;
        this.targetYear = targetYear;
        this.period = period;
        this.periodNumber = periodNumber;
        this.amount = normalizeAmount(amount);
    }

    private static void validatePeriod(PerformanceTargetPeriod period, int periodNumber) {
        if (period == null) {
            throw new PerformanceTargetDomainException("department performance target period is required");
        }
        boolean valid = switch (period) {
            case YEAR -> periodNumber == 1;
            case QUARTER -> periodNumber >= 1 && periodNumber <= 4;
            case MONTH -> periodNumber >= 1 && periodNumber <= 12;
        };
        if (!valid) {
            throw new PerformanceTargetDomainException("department performance target period number is invalid");
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(MAXIMUM_AMOUNT) > 0) {
            throw new PerformanceTargetDomainException("department performance target amount is invalid");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new PerformanceTargetDomainException("department performance target tenant is required");
        }
        return tenantId;
    }

    private static DepartmentId requireDepartment(DepartmentId departmentId) {
        if (departmentId == null) {
            throw new PerformanceTargetDomainException("department performance target department is required");
        }
        return departmentId;
    }
}
