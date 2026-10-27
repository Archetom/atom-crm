package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.MarketingActivityDomainException;
import io.github.archetom.crm.domain.model.MarketingActivityRelatedObjectType;
import io.github.archetom.crm.domain.model.MarketingActivityStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
public class MarketingActivity extends AggregateRoot<MarketingActivityId> {
    public static final int MAX_PARTICIPANTS = 100;

    private MarketingActivityId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private String name;
    private MarketingActivityRelatedObjectType relatedObjectType;
    private MarketingFormId marketingFormId;
    private List<MemberId> participantMemberIds = List.of();
    private List<DepartmentId> participantDepartmentIds = List.of();
    private String activityType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal budget;
    private String address;
    private String description;
    private long viewCount;
    private long submissionCount;
    private boolean oneSubmissionPerUser;
    private MarketingActivityStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private MarketingActivity() {
    }

    public record Snapshot(
            MarketingActivityId id, TenantId tenantId, MemberId createdByMemberId,
            String name, MarketingActivityRelatedObjectType relatedObjectType,
            MarketingFormId marketingFormId,
            List<MemberId> participantMemberIds, List<DepartmentId> participantDepartmentIds,
            String activityType,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal budget,
            String address, String description, long viewCount, long submissionCount,
            boolean oneSubmissionPerUser,
            MarketingActivityStatus status, Long version, LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static MarketingActivity create(
            TenantId tenantId, MemberId createdByMemberId, String name,
            MarketingActivityRelatedObjectType relatedObjectType,
            MarketingFormId marketingFormId,
            Collection<MemberId> participantMemberIds,
            Collection<DepartmentId> participantDepartmentIds, String activityType,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal budget,
            String address, String description, long viewCount, long submissionCount,
            boolean oneSubmissionPerUser) {
        MarketingActivity activity = new MarketingActivity();
        activity.tenantId = requireTenant(tenantId);
        activity.createdByMemberId = requireMember(createdByMemberId, "marketing activity creator");
        activity.status = MarketingActivityStatus.ACTIVE;
        activity.apply(name, relatedObjectType, marketingFormId,
                participantMemberIds, participantDepartmentIds,
                activityType, startTime, endTime,
                budget, address, description, viewCount, submissionCount, oneSubmissionPerUser);
        activity.createdTime = activity.updatedTime;
        return activity;
    }

    public static MarketingActivity reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new MarketingActivityDomainException("marketing activity snapshot must not be empty");
        }
        MarketingActivity activity = new MarketingActivity();
        activity.id = snapshot.id();
        activity.tenantId = requireTenant(snapshot.tenantId());
        activity.createdByMemberId = requireMember(snapshot.createdByMemberId(), "marketing activity creator");
        activity.status = snapshot.status() == null ? MarketingActivityStatus.DELETED : snapshot.status();
        activity.apply(snapshot.name(), snapshot.relatedObjectType(), snapshot.marketingFormId(),
                snapshot.participantMemberIds(),
                snapshot.participantDepartmentIds(), snapshot.activityType(), snapshot.startTime(),
                snapshot.endTime(), snapshot.budget(),
                snapshot.address(), snapshot.description(), snapshot.viewCount(), snapshot.submissionCount(),
                snapshot.oneSubmissionPerUser());
        activity.restoreVersion(snapshot.version());
        activity.createdTime = snapshot.createdTime();
        activity.updatedTime = snapshot.updatedTime();
        return activity;
    }

    public void update(
            String name, MarketingActivityRelatedObjectType relatedObjectType,
            MarketingFormId marketingFormId,
            Collection<MemberId> participantMemberIds,
            Collection<DepartmentId> participantDepartmentIds, String activityType,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal budget,
            String address, String description, long viewCount, long submissionCount,
            boolean oneSubmissionPerUser) {
        ensureActive();
        apply(name, relatedObjectType, marketingFormId, participantMemberIds, participantDepartmentIds,
                activityType, startTime, endTime,
                budget, address, description, viewCount, submissionCount, oneSubmissionPerUser);
    }

    public void delete() {
        ensureActive();
        status = MarketingActivityStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == MarketingActivityStatus.ACTIVE;
    }

    public void onPersisted(
            MarketingActivityId id, Long version, LocalDateTime createdTime,
            LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new MarketingActivityDomainException("persisted marketing activity ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = createdTime;
        if (updatedTime != null) this.updatedTime = updatedTime;
    }

    private void apply(
            String name, MarketingActivityRelatedObjectType relatedObjectType,
            MarketingFormId marketingFormId,
            Collection<MemberId> participantMemberIds,
            Collection<DepartmentId> participantDepartmentIds, String activityType,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal budget,
            String address, String description, long viewCount, long submissionCount,
            boolean oneSubmissionPerUser) {
        this.name = required(name, 200, "marketing activity name");
        if (relatedObjectType == null) {
            throw new MarketingActivityDomainException("marketing activity related object is required");
        }
        this.relatedObjectType = relatedObjectType;
        this.marketingFormId = marketingFormId;
        this.participantMemberIds = members(participantMemberIds);
        this.participantDepartmentIds = departments(participantDepartmentIds);
        if (this.participantMemberIds.isEmpty() && this.participantDepartmentIds.isEmpty()
                || this.participantMemberIds.size() + this.participantDepartmentIds.size() > MAX_PARTICIPANTS) {
            throw new MarketingActivityDomainException("marketing activity participants are invalid");
        }
        if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
            throw new MarketingActivityDomainException("marketing activity time range is invalid");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.activityType = optional(activityType, 100, "marketing activity type");
        this.budget = money(budget);
        this.address = optional(address, 300, "marketing activity address");
        this.description = optional(description, 2000, "marketing activity description");
        this.viewCount = nonnegative(viewCount, "marketing activity view count");
        this.submissionCount = nonnegative(submissionCount, "marketing activity submission count");
        this.oneSubmissionPerUser = oneSubmissionPerUser;
        touch();
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new MarketingActivityDomainException("deleted marketing activities cannot change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) throw new MarketingActivityDomainException("marketing activity tenant is required");
        return value;
    }

    private static MemberId requireMember(MemberId value, String field) {
        if (value == null) throw new MarketingActivityDomainException(field + " is required");
        return value;
    }

    private static List<MemberId> members(Collection<MemberId> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_PARTICIPANTS) {
            throw new MarketingActivityDomainException("marketing activity participants are invalid");
        }
        LinkedHashSet<MemberId> distinct = new LinkedHashSet<>();
        for (MemberId value : values) {
            distinct.add(requireMember(value, "marketing activity participant"));
        }
        if (distinct.size() > MAX_PARTICIPANTS) {
            throw new MarketingActivityDomainException("marketing activity participants are invalid");
        }
        return List.copyOf(distinct);
    }

    private static List<DepartmentId> departments(Collection<DepartmentId> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_PARTICIPANTS) {
            throw new MarketingActivityDomainException("marketing activity participants are invalid");
        }
        LinkedHashSet<DepartmentId> distinct = new LinkedHashSet<>();
        for (DepartmentId value : values) {
            if (value == null) {
                throw new MarketingActivityDomainException("marketing activity participant department is required");
            }
            distinct.add(value);
        }
        if (distinct.size() > MAX_PARTICIPANTS) {
            throw new MarketingActivityDomainException("marketing activity participants are invalid");
        }
        return List.copyOf(distinct);
    }

    private static String required(String value, int max, String field) {
        String normalized = optional(value, max, field);
        if (normalized == null) throw new MarketingActivityDomainException(field + " is required");
        return normalized;
    }

    private static String optional(String value, int max, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new MarketingActivityDomainException(field + " is too long");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) return null;
        if (value.signum() < 0) throw new MarketingActivityDomainException("marketing activity budget is invalid");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (ArithmeticException exception) {
            throw new MarketingActivityDomainException("marketing activity budget is invalid");
        }
    }

    private static long nonnegative(long value, String field) {
        if (value < 0) throw new MarketingActivityDomainException(field + " is invalid");
        return value;
    }
}
