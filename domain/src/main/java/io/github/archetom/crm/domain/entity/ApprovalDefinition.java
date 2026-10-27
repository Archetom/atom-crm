package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Versioned tenant approval definition; running instances use their own route snapshot. */
@Getter
public class ApprovalDefinition extends AggregateRoot<ApprovalDefinitionId> {

    public static final int MAX_STEPS = 50;
    public static final int MAX_SCOPE_MEMBERS = 500;
    public static final int MAX_MANAGERS = ApprovalStepDefinition.MAX_ACTORS;

    private ApprovalDefinitionId id;
    private TenantId tenantId;
    private BusinessObjectType objectType;
    private String name;
    private String normalizedName;
    private String description;
    private ApprovalDefinitionStatus status;
    private ApprovalRepeatMode repeatMode;
    private Set<MemberId> initiatorMemberIds = Set.of();
    private Set<DepartmentId> initiatorDepartmentIds = Set.of();
    private Set<MemberId> managerMemberIds = Set.of();
    private List<ApprovalStepDefinition> steps = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ApprovalDefinition() {
    }

    public record Snapshot(
            ApprovalDefinitionId id,
            TenantId tenantId,
            BusinessObjectType objectType,
            String name,
            String description,
            ApprovalDefinitionStatus status,
            ApprovalRepeatMode repeatMode,
            Set<MemberId> initiatorMemberIds,
            Set<DepartmentId> initiatorDepartmentIds,
            Set<MemberId> managerMemberIds,
            List<ApprovalStepDefinition> steps,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static ApprovalDefinition create(
            TenantId tenantId,
            BusinessObjectType objectType,
            String name,
            String description,
            ApprovalRepeatMode repeatMode,
            Collection<MemberId> initiatorMembers,
            Collection<DepartmentId> initiatorDepartments,
            Collection<MemberId> managers,
            Collection<ApprovalStepDefinition> steps) {
        ApprovalDefinition definition = new ApprovalDefinition();
        definition.tenantId = requireTenant(tenantId);
        definition.objectType = requireObjectType(objectType);
        definition.status = ApprovalDefinitionStatus.INACTIVE;
        definition.apply(name, description, repeatMode, initiatorMembers,
                initiatorDepartments, managers, steps);
        LocalDateTime now = LocalDateTime.now();
        definition.createdTime = now;
        definition.updatedTime = now;
        return definition;
    }

    public static ApprovalDefinition reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ApprovalDomainException("approval definition snapshot must not be empty");
        }
        ApprovalDefinition definition = new ApprovalDefinition();
        definition.id = snapshot.id();
        definition.tenantId = requireTenant(snapshot.tenantId());
        definition.objectType = requireObjectType(snapshot.objectType());
        definition.status = snapshot.status() == null
                ? ApprovalDefinitionStatus.INACTIVE : snapshot.status();
        definition.apply(snapshot.name(), snapshot.description(), snapshot.repeatMode(),
                snapshot.initiatorMemberIds(), snapshot.initiatorDepartmentIds(),
                snapshot.managerMemberIds(), snapshot.steps());
        definition.restoreVersion(snapshot.version());
        definition.createdTime = snapshot.createdTime();
        definition.updatedTime = snapshot.updatedTime();
        return definition;
    }

    public void replace(
            String name,
            String description,
            ApprovalRepeatMode repeatMode,
            Collection<MemberId> initiatorMembers,
            Collection<DepartmentId> initiatorDepartments,
            Collection<MemberId> managers,
            Collection<ApprovalStepDefinition> steps) {
        ensureNotDeleted();
        apply(name, description, repeatMode, initiatorMembers,
                initiatorDepartments, managers, steps);
        updatedTime = LocalDateTime.now();
    }

    public void changeStatus(ApprovalDefinitionStatus next) {
        ensureNotDeleted();
        if (next == null) {
            throw new ApprovalDomainException("approval definition status is required");
        }
        if (next == ApprovalDefinitionStatus.DELETED) {
            status = ApprovalDefinitionStatus.DELETED;
        } else {
            status = next;
        }
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == ApprovalDefinitionStatus.ACTIVE;
    }

    public boolean isApplicantAllowed(MemberId memberId, DepartmentId departmentId) {
        if (memberId == null) {
            return false;
        }
        return initiatorMemberIds.isEmpty() && initiatorDepartmentIds.isEmpty()
                || initiatorMemberIds.contains(memberId)
                || departmentId != null && initiatorDepartmentIds.contains(departmentId);
    }

    public boolean isManager(MemberId memberId) {
        return memberId != null && managerMemberIds.contains(memberId);
    }

    public void onPersisted(ApprovalDefinitionId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new ApprovalDomainException("persisted approval definition ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private void apply(
            String name,
            String description,
            ApprovalRepeatMode repeatMode,
            Collection<MemberId> initiatorMembers,
            Collection<DepartmentId> initiatorDepartments,
            Collection<MemberId> managers,
            Collection<ApprovalStepDefinition> steps) {
        this.name = required(name, 200, "approval definition name");
        normalizedName = this.name.toLowerCase(java.util.Locale.ROOT);
        this.description = optional(description, 1000, "approval definition description");
        this.repeatMode = repeatMode == null ? ApprovalRepeatMode.ONCE : repeatMode;
        initiatorMemberIds = boundedSet(initiatorMembers, MAX_SCOPE_MEMBERS,
                "approval initiator members");
        initiatorDepartmentIds = boundedSet(initiatorDepartments, MAX_SCOPE_MEMBERS,
                "approval initiator departments");
        managerMemberIds = boundedSet(managers, MAX_MANAGERS,
                "approval managers");
        this.steps = validateSteps(steps);
        if (this.steps.stream().anyMatch(step -> step.fallback() == ApprovalFallback.MANAGERS)
                && managerMemberIds.isEmpty()) {
            throw new ApprovalDomainException("manager fallback requires approval managers");
        }
    }

    private List<ApprovalStepDefinition> validateSteps(
            Collection<ApprovalStepDefinition> values) {
        List<ApprovalStepDefinition> source = values == null ? List.of() : List.copyOf(values);
        if (source.isEmpty() || source.size() > MAX_STEPS
                || source.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApprovalDomainException("approval definition steps are invalid");
        }
        Set<String> codes = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (ApprovalStepDefinition step : source) {
            if (!codes.add(step.code()) || !orders.add(step.order())) {
                throw new ApprovalDomainException("approval step code and order must be unique");
            }
        }
        long factFields = source.stream().flatMap(step -> step.conditions().stream())
                .map(condition -> condition.fieldCode()).distinct().count();
        if (factFields > ApprovalInstance.MAX_ROUTING_FACTS) {
            throw new ApprovalDomainException("approval routing fields exceed the supported limit");
        }
        return source.stream().sorted(Comparator.comparingInt(ApprovalStepDefinition::order)).toList();
    }

    private void ensureNotDeleted() {
        if (status == ApprovalDefinitionStatus.DELETED) {
            throw new ApprovalDomainException("deleted approval definitions cannot change");
        }
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ApprovalDomainException("approval tenant must not be empty");
        }
        return value;
    }

    private static BusinessObjectType requireObjectType(BusinessObjectType value) {
        if (value == null) {
            throw new ApprovalDomainException("approval object type must not be empty");
        }
        return value;
    }

    private static String required(String value, int maximum, String description) {
        String normalized = optional(value, maximum, description);
        if (normalized == null) {
            throw new ApprovalDomainException(description + " must not be empty");
        }
        return normalized;
    }

    private static String optional(String value, int maximum, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new ApprovalDomainException(description + " is too long");
        }
        return normalized;
    }

    private static <T> Set<T> boundedSet(Collection<T> values, int maximum, String description) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<T> set = new LinkedHashSet<>(values);
        if (values.size() > maximum || set.size() != values.size() || set.contains(null)) {
            throw new ApprovalDomainException(description + " are invalid");
        }
        return java.util.Collections.unmodifiableSet(set);
    }
}
