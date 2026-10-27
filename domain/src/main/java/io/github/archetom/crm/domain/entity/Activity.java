package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ActivityDomainException;
import io.github.archetom.crm.domain.model.ActivityKind;
import io.github.archetom.crm.domain.model.ActivityStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** Tenant-owned timeline entry.  Its subject remains the source of authorization. */
@Getter
public class Activity extends AggregateRoot<ActivityId> {
    public static final int MAX_RELATIONS = 50;

    private ActivityId id;
    private TenantId tenantId;
    private BusinessObjectType subjectType;
    private Long subjectId;
    private ActivityKind kind;
    private MemberId creatorMemberId;
    private String category;
    private String purpose;
    private String content;
    private LocalDateTime occurredTime;
    private LocalDateTime nextContactTime;
    private List<ActivityRelation> relations = List.of();
    private ActivityStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Activity() {
    }

    public record Snapshot(ActivityId id, TenantId tenantId, BusinessObjectType subjectType,
                           Long subjectId, ActivityKind kind, MemberId creatorMemberId,
                           String category, String purpose, String content,
                           LocalDateTime occurredTime, LocalDateTime nextContactTime,
                           List<ActivityRelation> relations, ActivityStatus status, Long version,
                           LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static Activity followUp(TenantId tenantId, BusinessObjectType subjectType,
                                    Long subjectId, MemberId creatorMemberId, String category,
                                    String purpose, String content, LocalDateTime occurredTime,
                                    LocalDateTime nextContactTime,
                                    Collection<ActivityRelation> relations) {
        Activity value = new Activity();
        value.tenantId = tenant(tenantId);
        value.subjectType = subject(subjectType);
        value.subjectId = recordId(subjectId);
        value.kind = ActivityKind.FOLLOW_UP;
        value.creatorMemberId = creator(creatorMemberId);
        value.status = ActivityStatus.ACTIVE;
        value.apply(category, purpose, content, occurredTime, nextContactTime, relations);
        value.createdTime = value.updatedTime;
        return value;
    }

    /** Creates a server-originated, append-only timeline entry. */
    public static Activity system(TenantId tenantId, BusinessObjectType subjectType,
                                  Long subjectId, MemberId creatorMemberId, String category,
                                  String content) {
        Activity value = new Activity();
        value.tenantId = tenant(tenantId);
        value.subjectType = subject(subjectType);
        value.subjectId = recordId(subjectId);
        value.kind = ActivityKind.SYSTEM;
        value.creatorMemberId = creator(creatorMemberId);
        value.status = ActivityStatus.ACTIVE;
        value.apply(category, null, content, LocalDateTime.now(), null, List.of());
        value.createdTime = value.updatedTime;
        return value;
    }

    public static Activity reconstitute(Snapshot snapshot) {
        if (snapshot == null) throw new ActivityDomainException("activity snapshot is required");
        Activity value = new Activity();
        value.id = snapshot.id();
        value.tenantId = tenant(snapshot.tenantId());
        value.subjectType = subject(snapshot.subjectType());
        value.subjectId = recordId(snapshot.subjectId());
        value.kind = snapshot.kind() == null ? ActivityKind.SYSTEM : snapshot.kind();
        value.creatorMemberId = creator(snapshot.creatorMemberId());
        value.status = snapshot.status() == null ? ActivityStatus.DELETED : snapshot.status();
        value.category = optional(snapshot.category(), 100, "activity category");
        value.purpose = optional(snapshot.purpose(), 200, "activity purpose");
        value.content = required(snapshot.content(), 10_000, "activity content");
        value.occurredTime = time(snapshot.occurredTime(), "activity time");
        value.nextContactTime = optionalTime(snapshot.nextContactTime());
        value.relations = relations(snapshot.relations());
        value.restoreVersion(snapshot.version());
        value.createdTime = snapshot.createdTime();
        value.updatedTime = snapshot.updatedTime();
        return value;
    }

    public void replace(String category, String purpose, String content, LocalDateTime occurredTime,
                        LocalDateTime nextContactTime, Collection<ActivityRelation> relations) {
        ensureMutable();
        apply(category, purpose, content, occurredTime, nextContactTime, relations);
    }

    public void delete() {
        ensureMutable();
        status = ActivityStatus.DELETED;
        touch();
    }

    public boolean isDeleted() {
        return status == ActivityStatus.DELETED;
    }

    public boolean isUserFollowUp() {
        return kind == ActivityKind.FOLLOW_UP;
    }

    public void onPersisted(ActivityId id, Long version, LocalDateTime createdTime,
                            LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new ActivityDomainException("persisted activity ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = createdTime;
        if (updatedTime != null) this.updatedTime = updatedTime;
    }

    private void apply(String category, String purpose, String content, LocalDateTime occurredTime,
                       LocalDateTime nextContactTime, Collection<ActivityRelation> relations) {
        this.category = optional(category, 100, "activity category");
        this.purpose = optional(purpose, 200, "activity purpose");
        this.content = required(content, 10_000, "activity content");
        this.occurredTime = time(occurredTime, "activity time");
        this.nextContactTime = optionalTime(nextContactTime);
        this.relations = relations(relations);
        touch();
    }

    private void ensureMutable() {
        if (kind != ActivityKind.FOLLOW_UP || status != ActivityStatus.ACTIVE) {
            throw new ActivityDomainException("activity cannot be changed");
        }
    }

    private void touch() {
        LocalDateTime now = LocalDateTime.now();
        updatedTime = now.withNano(now.getNano() / 1_000 * 1_000);
    }

    private static List<ActivityRelation> relations(Collection<ActivityRelation> values) {
        LinkedHashMap<String, ActivityRelation> result = new LinkedHashMap<>();
        if (values != null) {
            for (ActivityRelation value : values) {
                if (value == null) throw new ActivityDomainException("activity relation is invalid");
                result.put(value.objectType().name() + ':' + value.recordId(), value);
            }
        }
        if (result.size() > MAX_RELATIONS) throw new ActivityDomainException("too many activity relations");
        return List.copyOf(result.values());
    }

    private static TenantId tenant(TenantId value) {
        if (value == null) throw new ActivityDomainException("activity tenant is required");
        return value;
    }

    private static MemberId creator(MemberId value) {
        if (value == null) throw new ActivityDomainException("activity creator is required");
        return value;
    }

    private static BusinessObjectType subject(BusinessObjectType value) {
        if (value == null || !ActivityRelation.supported(value)) {
            throw new ActivityDomainException("activity subject is unavailable");
        }
        return value;
    }

    private static Long recordId(Long value) {
        if (value == null || value <= 0) throw new ActivityDomainException("activity subject ID is invalid");
        return value;
    }

    private static LocalDateTime time(LocalDateTime value, String description) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new ActivityDomainException(description + " is invalid");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private static LocalDateTime optionalTime(LocalDateTime value) {
        return value == null ? null : time(value, "next contact time");
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) throw new ActivityDomainException(description + " is required");
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new ActivityDomainException(description + " is too long");
        }
        return normalized;
    }
}
