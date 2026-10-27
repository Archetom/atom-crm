package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.model.WorkRecurrenceResolution;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskReminderPlan;
import io.github.archetom.crm.domain.model.WorkTaskSeriesStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Versioned future-task template. Generated tasks remain independent WorkTask roots. */
@Getter
public class WorkTaskSeries extends AggregateRoot<WorkTaskSeriesId> {
    public static final int MAX_TITLE_CODE_POINTS = WorkTask.MAX_TITLE_CODE_POINTS;
    public static final int MAX_DESCRIPTION_CODE_POINTS = WorkTask.MAX_DESCRIPTION_CODE_POINTS;
    public static final int MAX_FOLLOWERS = WorkTask.MAX_FOLLOWERS;
    public static final int MAX_OCCURRENCES = 10_000;

    private WorkTaskSeriesId id;
    private TenantId tenantId;
    private MemberId createdByMemberId;
    private MemberId currentAssigneeMemberId;
    private Set<MemberId> followerMemberIds = Set.of();
    private String title;
    private String description;
    private WorkTaskPriority priority;
    /** Future occurrence template only; generated tasks own their copied plan. */
    private WorkTaskReminderPlan reminderPlan = WorkTaskReminderPlan.empty();
    private WorkRecurrenceRule recurrenceRule;
    private WorkTaskSeriesStatus status;
    private long generation;
    private long nextOccurrenceSequence;
    private LocalDateTime nextNominalLocalTime;
    /** Persisted UTC projection; reads must not reinterpret the rule against local tzdb. */
    private LocalDateTime nextDueTime;
    private long occurrenceCount;
    private long skippedCount;
    private String lastSafeReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private WorkTaskSeries() { }

    public record Snapshot(
            WorkTaskSeriesId id, TenantId tenantId, MemberId createdByMemberId,
            MemberId currentAssigneeMemberId, Set<MemberId> followerMemberIds,
            String title, String description, WorkTaskPriority priority,
            WorkTaskReminderPlan reminderPlan,
            WorkRecurrenceRule recurrenceRule, WorkTaskSeriesStatus status,
            long generation, long nextOccurrenceSequence, LocalDateTime nextNominalLocalTime,
            LocalDateTime nextDueTime,
            long occurrenceCount, long skippedCount, String lastSafeReason, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        /** Source-compatible B27a reconstruction shape. */
        public Snapshot(
                WorkTaskSeriesId id, TenantId tenantId, MemberId createdByMemberId,
                MemberId currentAssigneeMemberId, Set<MemberId> followerMemberIds,
                String title, String description, WorkTaskPriority priority,
                WorkRecurrenceRule recurrenceRule, WorkTaskSeriesStatus status,
                long generation, long nextOccurrenceSequence, LocalDateTime nextNominalLocalTime,
                LocalDateTime nextDueTime, long occurrenceCount, long skippedCount,
                String lastSafeReason, Long version, LocalDateTime createdTime,
                LocalDateTime updatedTime) {
            this(id, tenantId, createdByMemberId, currentAssigneeMemberId, followerMemberIds,
                    title, description, priority, WorkTaskReminderPlan.empty(), recurrenceRule,
                    status, generation, nextOccurrenceSequence, nextNominalLocalTime, nextDueTime,
                    occurrenceCount, skippedCount, lastSafeReason, version, createdTime, updatedTime);
        }
    }

    public static WorkTaskSeries create(
            TenantId tenantId, MemberId creator, MemberId assignee, Collection<MemberId> followers,
            String title, String description, WorkTaskPriority priority,
            WorkRecurrenceRule recurrenceRule, LocalDateTime now) {
        WorkTaskSeries series = new WorkTaskSeries();
        series.tenantId = requireTenant(tenantId);
        series.createdByMemberId = requireMember(creator, "work task series creator");
        series.currentAssigneeMemberId = requireMember(assignee, "work task series assignee");
        series.followerMemberIds = followers(followers);
        series.title = requiredText(title, MAX_TITLE_CODE_POINTS, "work task series title");
        series.description = optionalText(description, MAX_DESCRIPTION_CODE_POINTS, "work task series description");
        series.priority = priority == null ? WorkTaskPriority.NORMAL : priority;
        series.reminderPlan = WorkTaskReminderPlan.empty();
        series.recurrenceRule = requireRule(recurrenceRule);
        series.status = WorkTaskSeriesStatus.DRAFT;
        series.generation = 1;
        series.nextOccurrenceSequence = 1;
        series.setNextOccurrence(recurrenceRule.firstNominal());
        series.createdTime = requiredTime(now, "work task series creation time");
        series.updatedTime = series.createdTime;
        return series;
    }

    public static WorkTaskSeries reconstitute(Snapshot snapshot) {
        if (snapshot == null) throw new WorkTaskDomainException("work task series snapshot is required");
        WorkTaskSeries series = new WorkTaskSeries();
        series.id = snapshot.id();
        series.tenantId = requireTenant(snapshot.tenantId());
        series.createdByMemberId = requireMember(snapshot.createdByMemberId(), "work task series creator");
        series.currentAssigneeMemberId = requireMember(snapshot.currentAssigneeMemberId(), "work task series assignee");
        series.followerMemberIds = followers(snapshot.followerMemberIds());
        series.title = requiredText(snapshot.title(), MAX_TITLE_CODE_POINTS, "work task series title");
        series.description = optionalText(snapshot.description(), MAX_DESCRIPTION_CODE_POINTS, "work task series description");
        series.priority = snapshot.priority() == null ? WorkTaskPriority.NORMAL : snapshot.priority();
        series.reminderPlan = snapshot.reminderPlan() == null
                ? WorkTaskReminderPlan.empty() : snapshot.reminderPlan();
        series.recurrenceRule = requireRule(snapshot.recurrenceRule());
        series.status = snapshot.status() == null ? WorkTaskSeriesStatus.DRAFT : snapshot.status();
        if (snapshot.generation() < 1 || snapshot.nextOccurrenceSequence() < 1
                || snapshot.occurrenceCount() < 0 || snapshot.occurrenceCount() > MAX_OCCURRENCES
                || snapshot.skippedCount() < 0) {
            throw new WorkTaskDomainException("work task series counters are invalid");
        }
        series.generation = snapshot.generation();
        series.nextOccurrenceSequence = snapshot.nextOccurrenceSequence();
        series.nextNominalLocalTime = snapshot.nextNominalLocalTime() == null ? null
                : requiredTime(snapshot.nextNominalLocalTime(), "work task series next occurrence");
        series.nextDueTime = snapshot.nextDueTime() == null ? null
                : requiredTime(snapshot.nextDueTime(), "work task series next due time");
        if (series.status == WorkTaskSeriesStatus.ACTIVE && series.nextNominalLocalTime == null) {
            throw new WorkTaskDomainException("active work task series next occurrence is required");
        }
        if (series.status == WorkTaskSeriesStatus.ACTIVE && series.nextDueTime == null) {
            throw new WorkTaskDomainException("active work task series next due time is required");
        }
        series.occurrenceCount = snapshot.occurrenceCount();
        series.skippedCount = snapshot.skippedCount();
        series.lastSafeReason = safeReason(snapshot.lastSafeReason());
        series.restoreVersion(snapshot.version());
        series.createdTime = requiredTime(snapshot.createdTime(), "work task series creation time");
        series.updatedTime = requiredTime(snapshot.updatedTime(), "work task series update time");
        return series;
    }

    public void updateFuture(
            MemberId assignee, Collection<MemberId> followers, String title, String description,
            WorkTaskPriority priority, WorkRecurrenceRule rule, LocalDateTime now) {
        ensureMutable();
        currentAssigneeMemberId = requireMember(assignee, "work task series assignee");
        followerMemberIds = followers(followers);
        this.title = requiredText(title, MAX_TITLE_CODE_POINTS, "work task series title");
        this.description = optionalText(description, MAX_DESCRIPTION_CODE_POINTS, "work task series description");
        this.priority = priority == null ? WorkTaskPriority.NORMAL : priority;
        recurrenceRule = requireRule(rule);
        nextGeneration();
        if (!resetFuture(now) && status == WorkTaskSeriesStatus.ACTIVE) {
            status = WorkTaskSeriesStatus.ENDED;
            lastSafeReason = "TIME_RANGE";
        } else if (nextNominalLocalTime == null) {
            // A future-only edit of a non-executing template is intentionally not a
            // lifecycle transition. It can be corrected and activated later.
            lastSafeReason = "TIME_RANGE";
        }
        touch(now);
    }

    /** Replaces only the template copied into future generated tasks. */
    public void replaceReminderPlanTemplate(WorkTaskReminderPlan nextPlan, LocalDateTime now) {
        ensureMutable();
        WorkTaskReminderPlan next = nextPlan == null ? WorkTaskReminderPlan.empty() : nextPlan;
        if (!reminderPlan.equals(next)) {
            reminderPlan = next;
            nextGeneration();
        }
        touch(now);
    }

    public void activate(LocalDateTime now) {
        if (status == WorkTaskSeriesStatus.DELETED || status == WorkTaskSeriesStatus.ENDED) {
            throw new WorkTaskDomainException("work task series cannot be activated");
        }
        if (status != WorkTaskSeriesStatus.ACTIVE) {
            LocalDateTime next = nextFutureCursor(now);
            if (next == null) {
                throw new WorkTaskDomainException("work task series has no future occurrence");
            }
            status = WorkTaskSeriesStatus.ACTIVE;
            setNextOccurrence(next);
            touch(now);
        }
    }

    public void pause(LocalDateTime now, String reason) {
        if (status != WorkTaskSeriesStatus.ACTIVE) {
            throw new WorkTaskDomainException("only active work task series can be paused");
        }
        status = WorkTaskSeriesStatus.PAUSED;
        lastSafeReason = safeReason(reason == null ? "PAUSED" : reason);
        touch(now);
    }

    public void pauseForSafety(LocalDateTime now, String reason) {
        if (status == WorkTaskSeriesStatus.ACTIVE) {
            status = WorkTaskSeriesStatus.PAUSED;
            lastSafeReason = safeReason(reason);
            touch(now);
        }
    }

    public void delete(LocalDateTime now) {
        if (status == WorkTaskSeriesStatus.DELETED) {
            throw new WorkTaskDomainException("work task series is already deleted");
        }
        status = WorkTaskSeriesStatus.DELETED;
        setNextOccurrence(null);
        lastSafeReason = "DELETED";
        touch(now);
    }

    /** Calculates one coalesced due occurrence and advances only this series' future cursor. */
    public DueOccurrence advanceToLatestDue(LocalDateTime evaluationUtc, LocalDateTime committedTime) {
        if (status != WorkTaskSeriesStatus.ACTIVE || nextNominalLocalTime == null) return null;
        if (occurrenceCount >= MAX_OCCURRENCES) {
            status = WorkTaskSeriesStatus.ENDED;
            setNextOccurrence(null);
            lastSafeReason = "OCCURRENCE_CAP";
            touch(committedTime);
            return null;
        }
        if (recurrenceRule.resolve(nextNominalLocalTime).resolvedUtcTime().isAfter(evaluationUtc)) return null;
        LocalDateTime latest = nextNominalLocalTime;
        long skippedBeforeLatest = 0;
        long remainingCapacity = MAX_OCCURRENCES - occurrenceCount;
        while (skippedBeforeLatest + 1 < remainingCapacity) {
            LocalDateTime following = recurrenceRule.nextNominalPersistable(latest).orElse(null);
            if (following == null || recurrenceRule.resolve(following).resolvedUtcTime().isAfter(evaluationUtc)) break;
            latest = following;
            skippedBeforeLatest++;
        }
        WorkRecurrenceResolution resolution = recurrenceRule.resolve(latest);
        long sequence = nextOccurrenceSequence + skippedBeforeLatest;
        LocalDateTime following = recurrenceRule.nextNominalPersistable(latest).orElse(null);
        occurrenceCount = checkedAdd(occurrenceCount, skippedBeforeLatest + 1);
        skippedCount = checkedAdd(skippedCount, skippedBeforeLatest);
        if (skippedBeforeLatest > 0) lastSafeReason = "COALESCED_MISFIRE";
        nextOccurrenceSequence = checkedAdd(sequence, 1);
        if (occurrenceCount >= MAX_OCCURRENCES || following == null) {
            status = WorkTaskSeriesStatus.ENDED;
            setNextOccurrence(null);
            lastSafeReason = occurrenceCount >= MAX_OCCURRENCES ? "OCCURRENCE_CAP" : "TIME_RANGE";
        } else {
            setNextOccurrence(following);
        }
        touch(committedTime);
        return new DueOccurrence(generation, sequence, resolution, skippedBeforeLatest);
    }

    public void recordOpenSkip(LocalDateTime now) {
        skippedCount = checkedAdd(skippedCount, 1);
        lastSafeReason = "OPEN_TASK";
        touch(now);
    }

    public boolean isVisibleTo(MemberId memberId, boolean manager) {
        return status.isVisible() && (manager || isCreatedBy(memberId) || isAssignedTo(memberId)
                || memberId != null && followerMemberIds.contains(memberId));
    }

    public boolean isCreatedBy(MemberId memberId) {
        return memberId != null && createdByMemberId.sameValueAs(memberId);
    }

    public boolean isAssignedTo(MemberId memberId) {
        return memberId != null && currentAssigneeMemberId.sameValueAs(memberId);
    }

    public void onPersisted(WorkTaskSeriesId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new WorkTaskDomainException("persisted work task series ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = truncate(createdTime);
        if (updatedTime != null) this.updatedTime = truncate(updatedTime);
    }

    private boolean resetFuture(LocalDateTime now) {
        LocalDateTime candidate = nextFutureCursor(now);
        setNextOccurrence(candidate);
        return candidate != null;
    }

    private void setNextOccurrence(LocalDateTime nominal) {
        nextNominalLocalTime = nominal;
        nextDueTime = nominal == null ? null : recurrenceRule.resolve(nominal).resolvedUtcTime();
    }

    private LocalDateTime nextFutureCursor(LocalDateTime now) {
        LocalDateTime localNow = requiredTime(now, "work task series current time")
                .atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(
                        java.time.ZoneId.of(recurrenceRule.zoneId())).toLocalDateTime();
        LocalDateTime candidate = recurrenceRule.nominalAtOrAfterPersistable(localNow).orElse(null);
        while (candidate != null) {
            if (!recurrenceRule.resolve(candidate).resolvedUtcTime().isBefore(now)) {
                return candidate;
            }
            candidate = recurrenceRule.nextNominalPersistable(candidate).orElse(null);
        }
        return null;
    }

    private void ensureMutable() {
        if (status == WorkTaskSeriesStatus.DELETED || status == WorkTaskSeriesStatus.ENDED) {
            throw new WorkTaskDomainException("finished work task series is immutable");
        }
    }

    private void nextGeneration() {
        if (generation == Long.MAX_VALUE) throw new WorkTaskDomainException("work task series generation is exhausted");
        generation++;
        nextOccurrenceSequence = 1;
    }

    private static long checkedAdd(long value, long addition) {
        try { return Math.addExact(value, addition); }
        catch (ArithmeticException exception) { throw new WorkTaskDomainException("work task series counter is exhausted"); }
    }

    private void touch(LocalDateTime now) { updatedTime = requiredTime(now, "work task series update time"); }
    private static WorkRecurrenceRule requireRule(WorkRecurrenceRule value) {
        if (value == null) throw new WorkTaskDomainException("work recurrence rule is required");
        return value;
    }
    private static TenantId requireTenant(TenantId value) {
        if (value == null) throw new WorkTaskDomainException("work task series tenant is required");
        return value;
    }
    private static MemberId requireMember(MemberId value, String description) {
        if (value == null) throw new WorkTaskDomainException(description + " is required");
        return value;
    }
    private static Set<MemberId> followers(Collection<MemberId> values) {
        if (values == null || values.isEmpty()) return Set.of();
        if (values.size() > MAX_FOLLOWERS || values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new WorkTaskDomainException("work task series followers are invalid");
        }
        LinkedHashSet<MemberId> result = new LinkedHashSet<>(values);
        if (result.size() != values.size()) throw new WorkTaskDomainException("work task series followers must be unique");
        return Set.copyOf(result);
    }
    private static String requiredText(String value, int max, String description) {
        String result = optionalText(value, max, description);
        if (result == null) throw new WorkTaskDomainException(description + " is required");
        return result;
    }
    private static String optionalText(String value, int max, String description) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.codePointCount(0, result.length()) > max) throw new WorkTaskDomainException(description + " is too long");
        return result;
    }
    private static String safeReason(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (!result.matches("[A-Z0-9_]{1,64}")) throw new WorkTaskDomainException("work task series safe reason is invalid");
        return result;
    }
    private static LocalDateTime requiredTime(LocalDateTime value, String description) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new WorkTaskDomainException(description + " is invalid");
        }
        return truncate(value);
    }
    private static LocalDateTime truncate(LocalDateTime value) { return value.withNano(value.getNano() / 1_000 * 1_000); }

    public record DueOccurrence(long generation, long sequence,
                                WorkRecurrenceResolution resolution, long coalescedSkippedCount) {
        public DueOccurrence {
            if (generation < 1 || sequence < 1 || resolution == null || coalescedSkippedCount < 0) {
                throw new IllegalArgumentException("work task series due occurrence is invalid");
            }
        }
    }
}
