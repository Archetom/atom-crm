package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.PerformanceTargetDomainException;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A versioned member-level target for one calendar month and one server-defined measure.
 *
 * <p>Targets deliberately remain separate from business records. They never alter contract
 * amounts or make a dashboard data scope more permissive.</p>
 */
@Getter
public class PerformanceTarget extends AggregateRoot<PerformanceTargetId> {

    private static final int MINIMUM_YEAR = 2000;
    private static final int MAXIMUM_YEAR = 9999;
    private static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("99999999999999999.99");

    private PerformanceTargetId id;
    private TenantId tenantId;
    private MemberId memberId;
    private PerformanceTargetMetric metric;
    private int targetYear;
    private int targetMonth;
    private BigDecimal amount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private PerformanceTarget() {
    }

    public record Snapshot(
            PerformanceTargetId id,
            TenantId tenantId,
            MemberId memberId,
            PerformanceTargetMetric metric,
            int targetYear,
            int targetMonth,
            BigDecimal amount,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static PerformanceTarget create(
            TenantId tenantId,
            MemberId memberId,
            PerformanceTargetMetric metric,
            int targetYear,
            int targetMonth,
            BigDecimal amount) {
        PerformanceTarget target = new PerformanceTarget();
        target.tenantId = requireTenant(tenantId);
        target.memberId = requireMember(memberId);
        target.assign(metric, targetYear, targetMonth, amount);
        LocalDateTime now = LocalDateTime.now();
        target.createdTime = now;
        target.updatedTime = now;
        return target;
    }

    public static PerformanceTarget reconstitute(Snapshot snapshot) {
        if (snapshot == null || snapshot.id() == null) {
            throw new PerformanceTargetDomainException("performance target snapshot is incomplete");
        }
        PerformanceTarget target = new PerformanceTarget();
        target.id = snapshot.id();
        target.tenantId = requireTenant(snapshot.tenantId());
        target.memberId = requireMember(snapshot.memberId());
        target.assign(snapshot.metric(), snapshot.targetYear(), snapshot.targetMonth(), snapshot.amount());
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
            PerformanceTargetId id,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
        if (id == null) {
            throw new PerformanceTargetDomainException("persisted performance target ID is required");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new PerformanceTargetDomainException("persisted performance target ID cannot change");
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
            int targetMonth,
            BigDecimal amount) {
        if (metric == null) {
            throw new PerformanceTargetDomainException("performance target metric is required");
        }
        if (targetYear < MINIMUM_YEAR || targetYear > MAXIMUM_YEAR) {
            throw new PerformanceTargetDomainException("performance target year is invalid");
        }
        if (targetMonth < 1 || targetMonth > 12) {
            throw new PerformanceTargetDomainException("performance target month is invalid");
        }
        this.metric = metric;
        this.targetYear = targetYear;
        this.targetMonth = targetMonth;
        this.amount = normalizeAmount(amount);
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(MAXIMUM_AMOUNT) > 0) {
            throw new PerformanceTargetDomainException("performance target amount is invalid");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new PerformanceTargetDomainException("performance target tenant is required");
        }
        return tenantId;
    }

    private static MemberId requireMember(MemberId memberId) {
        if (memberId == null) {
            throw new PerformanceTargetDomainException("performance target member is required");
        }
        return memberId;
    }
}
