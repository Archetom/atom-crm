package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Versioned stage workflow metadata independent from runtime records. */
@Getter
public class StageWorkflowDefinition extends AggregateRoot<StageWorkflowId> {

    public static final int MAX_STAGES = 50;
    public static final int MAX_SCOPE_MEMBERS = 500;

    private StageWorkflowId id;
    private TenantId tenantId;
    private BusinessObjectType objectType;
    private String name;
    private String normalizedName;
    private String successName;
    private String failureName;
    private StageWorkflowStatus status;
    private Set<MemberId> applicableMemberIds = Set.of();
    private Set<DepartmentId> applicableDepartmentIds = Set.of();
    private List<StageDefinition> stages = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private StageWorkflowDefinition() {
    }

    public record Snapshot(
            StageWorkflowId id,
            TenantId tenantId,
            BusinessObjectType objectType,
            String name,
            String successName,
            String failureName,
            StageWorkflowStatus status,
            Set<MemberId> applicableMemberIds,
            Set<DepartmentId> applicableDepartmentIds,
            List<StageDefinition> stages,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static StageWorkflowDefinition create(
            TenantId tenantId,
            BusinessObjectType objectType,
            String name,
            String successName,
            String failureName,
            Collection<MemberId> applicableMembers,
            Collection<DepartmentId> applicableDepartments,
            Collection<StageDefinition> stages) {
        StageWorkflowDefinition workflow = new StageWorkflowDefinition();
        workflow.tenantId = requireTenant(tenantId);
        workflow.objectType = requireObject(objectType);
        workflow.status = StageWorkflowStatus.INACTIVE;
        workflow.apply(name, successName, failureName, applicableMembers,
                applicableDepartments, stages);
        LocalDateTime now = LocalDateTime.now();
        workflow.createdTime = now;
        workflow.updatedTime = now;
        return workflow;
    }

    public static StageWorkflowDefinition reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new StageWorkflowDomainException("stage workflow snapshot must not be empty");
        }
        StageWorkflowDefinition workflow = new StageWorkflowDefinition();
        workflow.id = snapshot.id();
        workflow.tenantId = requireTenant(snapshot.tenantId());
        workflow.objectType = requireObject(snapshot.objectType());
        workflow.status = snapshot.status() == null
                ? StageWorkflowStatus.INACTIVE : snapshot.status();
        workflow.apply(snapshot.name(), snapshot.successName(), snapshot.failureName(),
                snapshot.applicableMemberIds(), snapshot.applicableDepartmentIds(),
                snapshot.stages());
        workflow.restoreVersion(snapshot.version());
        workflow.createdTime = snapshot.createdTime();
        workflow.updatedTime = snapshot.updatedTime();
        return workflow;
    }

    public void replace(
            String name,
            String successName,
            String failureName,
            Collection<MemberId> applicableMembers,
            Collection<DepartmentId> applicableDepartments,
            Collection<StageDefinition> stages) {
        ensureNotDeleted();
        apply(name, successName, failureName, applicableMembers,
                applicableDepartments, stages);
        updatedTime = LocalDateTime.now();
    }

    public void changeStatus(StageWorkflowStatus next) {
        ensureNotDeleted();
        if (next == null) {
            throw new StageWorkflowDomainException("stage workflow status is required");
        }
        if (!allowedStatusTransitions().contains(next)) {
            throw new StageWorkflowDomainException("stage workflow status transition is not allowed");
        }
        status = next;
        updatedTime = LocalDateTime.now();
    }

    public Set<StageWorkflowStatus> allowedStatusTransitions() {
        return switch (status) {
            case ACTIVE -> Set.of(StageWorkflowStatus.INACTIVE, StageWorkflowStatus.DELETED);
            case INACTIVE -> Set.of(StageWorkflowStatus.ACTIVE, StageWorkflowStatus.DELETED);
            case DELETED -> Set.of();
        };
    }

    public boolean isActive() {
        return status == StageWorkflowStatus.ACTIVE;
    }

    public boolean isApplicable(MemberId memberId, DepartmentId departmentId) {
        return memberId != null && (applicableMemberIds.isEmpty()
                && applicableDepartmentIds.isEmpty()
                || applicableMemberIds.contains(memberId)
                || departmentId != null && applicableDepartmentIds.contains(departmentId));
    }

    public StageDefinition firstStage() {
        return stages.getFirst();
    }

    public StageDefinition requireStage(String stageCode) {
        return stages.stream().filter(stage -> stage.code().equals(stageCode)).findFirst()
                .orElseThrow(() -> new StageWorkflowDomainException(
                        "stage does not belong to the workflow"));
    }

    public StageDefinition nextStage(String stageCode) {
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).code().equals(stageCode)) {
                return index + 1 < stages.size() ? stages.get(index + 1) : null;
            }
        }
        throw new StageWorkflowDomainException("stage does not belong to the workflow");
    }

    public void onPersisted(StageWorkflowId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new StageWorkflowDomainException("persisted stage workflow ID is invalid");
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
            String successName,
            String failureName,
            Collection<MemberId> applicableMembers,
            Collection<DepartmentId> applicableDepartments,
            Collection<StageDefinition> stages) {
        this.name = text(name, 200, "stage workflow name");
        normalizedName = this.name.toLowerCase(Locale.ROOT);
        this.successName = text(successName, 100, "stage workflow success name");
        this.failureName = text(failureName, 100, "stage workflow failure name");
        applicableMemberIds = boundedSet(applicableMembers,
                MAX_SCOPE_MEMBERS, "stage workflow members");
        applicableDepartmentIds = boundedSet(applicableDepartments,
                MAX_SCOPE_MEMBERS, "stage workflow departments");
        this.stages = validateStages(stages);
    }

    private List<StageDefinition> validateStages(Collection<StageDefinition> values) {
        List<StageDefinition> source = values == null ? List.of() : List.copyOf(values);
        if (source.isEmpty() || source.size() > MAX_STAGES
                || source.stream().anyMatch(java.util.Objects::isNull)) {
            throw new StageWorkflowDomainException("stage workflow stages are invalid");
        }
        Set<String> codes = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (StageDefinition stage : source) {
            if (!codes.add(stage.code()) || !orders.add(stage.order())) {
                throw new StageWorkflowDomainException("stage code and order must be unique");
            }
        }
        return source.stream().sorted(Comparator.comparingInt(StageDefinition::order)).toList();
    }

    private void ensureNotDeleted() {
        if (status == StageWorkflowStatus.DELETED) {
            throw new StageWorkflowDomainException("deleted stage workflows cannot change");
        }
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new StageWorkflowDomainException("stage workflow tenant is required");
        }
        return value;
    }

    private static BusinessObjectType requireObject(BusinessObjectType value) {
        if (value == null) {
            throw new StageWorkflowDomainException("stage workflow object type is required");
        }
        return value;
    }

    private static String text(String value, int maximum, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximum) {
            throw new StageWorkflowDomainException(description + " is invalid");
        }
        return normalized;
    }

    private static <T> Set<T> boundedSet(Collection<T> values, int maximum, String description) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<T> set = new LinkedHashSet<>(values);
        if (values.size() > maximum || set.size() != values.size() || set.contains(null)) {
            throw new StageWorkflowDomainException(description + " are invalid");
        }
        return java.util.Collections.unmodifiableSet(set);
    }
}
