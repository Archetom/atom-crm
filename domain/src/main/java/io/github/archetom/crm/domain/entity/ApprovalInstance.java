package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalActionType;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalTaskStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned approval runtime with an immutable resolved route and mutable tasks. */
@Getter
public class ApprovalInstance extends AggregateRoot<ApprovalInstanceId> {

    public static final int MAX_WATCHERS = 100;
    /** Existing B11 routing boundary; OA may add four server-derived snapshot facts. */
    public static final int MAX_ROUTING_FACTS = 100;
    public static final int MAX_FACTS = 104;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_TASKS =
            ApprovalDefinition.MAX_STEPS * ApprovalStepDefinition.MAX_ACTORS;

    private ApprovalInstanceId id;
    private TenantId tenantId;
    private ApprovalDefinitionId definitionId;
    private Long definitionVersion;
    private BusinessObjectType objectType;
    private Long subjectId;
    private String idempotencyKey;
    private String title;
    private MemberId applicantMemberId;
    private ApprovalRepeatMode repeatMode;
    private ApprovalInstanceStatus status;
    private Integer currentStepOrder;
    private Map<FieldCode, List<String>> facts = Map.of();
    private Set<MemberId> managerMemberIds = Set.of();
    private Set<MemberId> watcherMemberIds = Set.of();
    private List<ApprovalTask> tasks = List.of();
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private ApprovalInstance() {
    }

    public record Snapshot(
            ApprovalInstanceId id,
            TenantId tenantId,
            ApprovalDefinitionId definitionId,
            Long definitionVersion,
            BusinessObjectType objectType,
            Long subjectId,
            String idempotencyKey,
            String title,
            MemberId applicantMemberId,
            ApprovalRepeatMode repeatMode,
            ApprovalInstanceStatus status,
            Integer currentStepOrder,
            Map<FieldCode, List<String>> facts,
            Set<MemberId> managerMemberIds,
            Set<MemberId> watcherMemberIds,
            List<ApprovalTask> tasks,
            Long version,
            LocalDateTime submittedTime,
            LocalDateTime completedTime,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static ApprovalInstance submit(
            TenantId tenantId,
            ApprovalDefinitionId definitionId,
            Long definitionVersion,
            BusinessObjectType objectType,
            Long subjectId,
            String idempotencyKey,
            String title,
            MemberId applicantMemberId,
            ApprovalRepeatMode repeatMode,
            Map<FieldCode, List<String>> facts,
            Collection<MemberId> managers,
            Collection<MemberId> watchers,
            Collection<ResolvedApprovalStep> route) {
        ApprovalInstance instance = new ApprovalInstance();
        instance.tenantId = requireTenant(tenantId);
        instance.definitionId = requireDefinition(definitionId);
        instance.definitionVersion = requireVersion(definitionVersion);
        instance.objectType = requireObject(objectType);
        instance.subjectId = requireSubject(subjectId);
        instance.idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        instance.title = required(title, MAX_TITLE_LENGTH, "approval title");
        instance.applicantMemberId = requireMember(applicantMemberId);
        instance.repeatMode = repeatMode == null ? ApprovalRepeatMode.ONCE : repeatMode;
        instance.facts = validateFacts(instance.objectType, facts);
        instance.managerMemberIds = boundedMembers(
                managers, ApprovalDefinition.MAX_MANAGERS, "approval managers");
        instance.watcherMemberIds = boundedMembers(watchers, MAX_WATCHERS, "approval watchers");
        instance.tasks = buildTasks(route);
        LocalDateTime now = LocalDateTime.now();
        instance.submittedTime = now;
        instance.createdTime = now;
        instance.updatedTime = now;
        instance.status = ApprovalInstanceStatus.PENDING;
        if (instance.tasks.isEmpty()) {
            instance.complete(ApprovalInstanceStatus.APPROVED);
        } else {
            instance.activateStep(instance.tasks.getFirst().getStepOrder());
        }
        return instance;
    }

    public static ApprovalInstance reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new ApprovalDomainException("approval instance snapshot must not be empty");
        }
        ApprovalInstance instance = new ApprovalInstance();
        instance.id = snapshot.id();
        instance.tenantId = requireTenant(snapshot.tenantId());
        instance.definitionId = requireDefinition(snapshot.definitionId());
        instance.definitionVersion = requireVersion(snapshot.definitionVersion());
        instance.objectType = requireObject(snapshot.objectType());
        instance.subjectId = requireSubject(snapshot.subjectId());
        instance.idempotencyKey = normalizeIdempotencyKey(snapshot.idempotencyKey());
        instance.title = required(snapshot.title(), MAX_TITLE_LENGTH, "approval title");
        instance.applicantMemberId = requireMember(snapshot.applicantMemberId());
        instance.repeatMode = snapshot.repeatMode() == null
                ? ApprovalRepeatMode.ONCE : snapshot.repeatMode();
        instance.status = snapshot.status() == null
                ? ApprovalInstanceStatus.CANCELLED : snapshot.status();
        instance.currentStepOrder = snapshot.currentStepOrder();
        instance.facts = validateFacts(instance.objectType, snapshot.facts());
        instance.managerMemberIds = boundedMembers(snapshot.managerMemberIds(),
                ApprovalDefinition.MAX_MANAGERS, "approval managers");
        instance.watcherMemberIds = boundedMembers(snapshot.watcherMemberIds(),
                MAX_WATCHERS, "approval watchers");
        instance.tasks = snapshot.tasks() == null ? List.of() : List.copyOf(snapshot.tasks());
        validateRuntime(instance);
        instance.restoreVersion(snapshot.version());
        instance.submittedTime = snapshot.submittedTime();
        instance.completedTime = snapshot.completedTime();
        instance.createdTime = snapshot.createdTime();
        instance.updatedTime = snapshot.updatedTime();
        return instance;
    }

    public void act(MemberId actor, ApprovalActionType action,
                    MemberId transferTarget, String remark) {
        ensurePending();
        requireMember(actor);
        if (action == null) {
            throw new ApprovalDomainException("approval action is required");
        }
        ApprovalTask task = tasks.stream().filter(value -> value.isPendingFor(actor))
                .findFirst().orElseThrow(() -> new ApprovalDomainException(
                        "caller does not own a pending approval task"));
        switch (action) {
            case APPROVE -> {
                task.approve(remark);
                afterApproval(task);
            }
            case REJECT -> {
                task.reject(remark);
                afterRejection(task);
            }
            case TRANSFER -> transfer(task, actor, transferTarget, remark);
        }
        updatedTime = LocalDateTime.now();
    }

    public void withdraw(MemberId actor) {
        ensurePending();
        if (!applicantMemberId.equals(actor)) {
            throw new ApprovalDomainException("only the applicant can withdraw an approval");
        }
        boolean acted = tasks.stream().anyMatch(task -> task.getStatus() == ApprovalTaskStatus.APPROVED
                || task.getStatus() == ApprovalTaskStatus.REJECTED
                || task.getStatus() == ApprovalTaskStatus.TRANSFERRED);
        if (acted) {
            throw new ApprovalDomainException("an acted approval cannot be withdrawn");
        }
        cancelOpenTasks();
        complete(ApprovalInstanceStatus.WITHDRAWN);
    }

    public void cancel() {
        ensurePending();
        cancelOpenTasks();
        complete(ApprovalInstanceStatus.CANCELLED);
    }

    public boolean isParticipant(MemberId memberId) {
        return memberId != null && (applicantMemberId.equals(memberId)
                || watcherMemberIds.contains(memberId)
                || tasks.stream().anyMatch(task -> task.getAssignedMemberId().equals(memberId)));
    }

    public boolean isManager(MemberId memberId) {
        return memberId != null && managerMemberIds.contains(memberId);
    }

    /** Whether this member owns a task that may receive a decision now. */
    public boolean hasPendingTaskFor(MemberId memberId) {
        return status == ApprovalInstanceStatus.PENDING && memberId != null
                && tasks.stream().anyMatch(task -> task.isPendingFor(memberId));
    }

    /** Whether the applicant may still withdraw without exposing task assignments. */
    public boolean canBeWithdrawnBy(MemberId memberId) {
        if (status != ApprovalInstanceStatus.PENDING || memberId == null
                || !applicantMemberId.equals(memberId)) {
            return false;
        }
        return tasks.stream().noneMatch(task -> task.getStatus() == ApprovalTaskStatus.APPROVED
                || task.getStatus() == ApprovalTaskStatus.REJECTED
                || task.getStatus() == ApprovalTaskStatus.TRANSFERRED);
    }

    /** Validates only aggregate-local transfer constraints; membership remains application-owned. */
    public boolean canTransferPendingTaskTo(MemberId actor, MemberId target) {
        if (!hasPendingTaskFor(actor) || target == null || actor.equals(target)
                || tasks.size() >= MAX_TASKS) {
            return false;
        }
        ApprovalTask current = tasks.stream().filter(task -> task.isPendingFor(actor))
                .findFirst().orElse(null);
        if (current == null) {
            return false;
        }
        boolean duplicate = tasks.stream().anyMatch(task -> task.getStepOrder()
                == current.getStepOrder() && task.getAssignedMemberId().equals(target)
                && task.isOpen());
        return !duplicate && (repeatMode != ApprovalRepeatMode.ONCE
                || !alreadyApprovedBeforeStep(target, current.getStepOrder()));
    }

    /** B29d's pending OA attachment collaborators are deliberately narrower than participants. */
    public boolean isCurrentOaAttachmentCollaborator(MemberId memberId) {
        return status == ApprovalInstanceStatus.PENDING && memberId != null
                && (applicantMemberId.equals(memberId)
                || managerMemberIds.contains(memberId)
                || tasks.stream().anyMatch(task -> task.isPendingFor(memberId)));
    }

    public void onPersisted(ApprovalInstanceId persistedId, Long persistedVersion,
                            LocalDateTime created, LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new ApprovalDomainException("persisted approval instance ID is invalid");
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

    private void transfer(ApprovalTask task, MemberId actor,
                          MemberId target, String remark) {
        requireMember(target);
        if (actor.equals(target)) {
            throw new ApprovalDomainException("approval task cannot be transferred to the same member");
        }
        boolean duplicate = tasks.stream().anyMatch(value -> value.getStepOrder() == task.getStepOrder()
                && value.getAssignedMemberId().equals(target) && value.isOpen());
        if (duplicate) {
            throw new ApprovalDomainException("transfer target already has an open task in this step");
        }
        if (repeatMode == ApprovalRepeatMode.ONCE
                && alreadyApprovedBeforeStep(target, task.getStepOrder())) {
            throw new ApprovalDomainException(
                    "transfer target already approved an earlier step");
        }
        if (tasks.size() >= MAX_TASKS) {
            throw new ApprovalDomainException("approval task history limit has been reached");
        }
        task.transfer(remark);
        List<ApprovalTask> next = new ArrayList<>(tasks);
        next.add(ApprovalTask.replacement(task, target));
        tasks = List.copyOf(next);
    }

    private void afterApproval(ApprovalTask acted) {
        if (acted.getMode() == ApprovalStepMode.ANY) {
            tasks.stream().filter(task -> task.getStepOrder() == acted.getStepOrder()
                            && task.getStatus() == ApprovalTaskStatus.PENDING)
                    .forEach(ApprovalTask::skip);
            completeStep(acted.getStepOrder());
            return;
        }
        if (acted.getMode() == ApprovalStepMode.SEQUENTIAL) {
            ApprovalTask next = tasks.stream()
                    .filter(task -> task.getStepOrder() == acted.getStepOrder()
                            && task.getStatus() == ApprovalTaskStatus.WAITING)
                    .min(Comparator.comparingInt(ApprovalTask::getActorOrder)).orElse(null);
            if (next != null) {
                activateSequential(next);
                return;
            }
            completeStep(acted.getStepOrder());
            return;
        }
        boolean pending = tasks.stream().anyMatch(task -> task.getStepOrder() == acted.getStepOrder()
                && task.getStatus() == ApprovalTaskStatus.PENDING);
        if (!pending) {
            completeStep(acted.getStepOrder());
        }
    }

    private void afterRejection(ApprovalTask acted) {
        if (acted.getMode() == ApprovalStepMode.ANY) {
            boolean pending = tasks.stream().anyMatch(task -> task.getStepOrder() == acted.getStepOrder()
                    && task.getStatus() == ApprovalTaskStatus.PENDING);
            if (pending) {
                return;
            }
        }
        cancelOpenTasks();
        complete(ApprovalInstanceStatus.REJECTED);
    }

    private void completeStep(int completedOrder) {
        Integer next = tasks.stream().map(ApprovalTask::getStepOrder)
                .filter(order -> order > completedOrder).distinct().min(Integer::compareTo)
                .orElse(null);
        if (next == null) {
            complete(ApprovalInstanceStatus.APPROVED);
        } else {
            activateStep(next);
        }
    }

    private void activateStep(int order) {
        currentStepOrder = order;
        List<ApprovalTask> waiting = tasks.stream()
                .filter(task -> task.getStepOrder() == order
                        && task.getStatus() == ApprovalTaskStatus.WAITING)
                .sorted(Comparator.comparingInt(ApprovalTask::getActorOrder)).toList();
        for (ApprovalTask task : waiting) {
            if (repeatMode == ApprovalRepeatMode.ONCE && alreadyApproved(task)) {
                task.skip();
            }
        }
        List<ApprovalTask> remaining = waiting.stream()
                .filter(task -> task.getStatus() == ApprovalTaskStatus.WAITING).toList();
        if (remaining.isEmpty()) {
            completeStep(order);
            return;
        }
        if (remaining.getFirst().getMode() == ApprovalStepMode.SEQUENTIAL) {
            activateSequential(remaining.getFirst());
        } else {
            remaining.forEach(ApprovalTask::activate);
        }
    }

    private void activateSequential(ApprovalTask task) {
        if (repeatMode == ApprovalRepeatMode.ONCE && alreadyApproved(task)) {
            task.skip();
            ApprovalTask next = tasks.stream()
                    .filter(value -> value.getStepOrder() == task.getStepOrder()
                            && value.getStatus() == ApprovalTaskStatus.WAITING)
                    .min(Comparator.comparingInt(ApprovalTask::getActorOrder)).orElse(null);
            if (next == null) {
                completeStep(task.getStepOrder());
            } else {
                activateSequential(next);
            }
        } else {
            task.activate();
        }
    }

    private boolean alreadyApproved(ApprovalTask task) {
        return alreadyApprovedBeforeStep(task.getAssignedMemberId(), task.getStepOrder());
    }

    private boolean alreadyApprovedBeforeStep(MemberId memberId, int stepOrder) {
        return tasks.stream().anyMatch(previous -> previous.getStepOrder() < stepOrder
                && previous.getAssignedMemberId().equals(memberId)
                && previous.getStatus() == ApprovalTaskStatus.APPROVED);
    }

    private void cancelOpenTasks() {
        tasks.stream().filter(ApprovalTask::isOpen).forEach(ApprovalTask::skip);
    }

    private void complete(ApprovalInstanceStatus terminal) {
        if (terminal == ApprovalInstanceStatus.PENDING) {
            throw new ApprovalDomainException("terminal approval status is required");
        }
        status = terminal;
        currentStepOrder = null;
        completedTime = LocalDateTime.now();
        updatedTime = completedTime;
    }

    private void ensurePending() {
        if (status != ApprovalInstanceStatus.PENDING) {
            throw new ApprovalDomainException("only pending approvals can change");
        }
    }

    private static List<ApprovalTask> buildTasks(Collection<ResolvedApprovalStep> route) {
        List<ResolvedApprovalStep> steps = route == null ? List.of()
                : route.stream().sorted(Comparator.comparingInt(ResolvedApprovalStep::order)).toList();
        if (steps.size() > ApprovalDefinition.MAX_STEPS) {
            throw new ApprovalDomainException("resolved approval route is too large");
        }
        Set<String> codes = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        List<ApprovalTask> result = new ArrayList<>();
        for (ResolvedApprovalStep step : steps) {
            if (!codes.add(step.code()) || !orders.add(step.order())) {
                throw new ApprovalDomainException("resolved approval route is ambiguous");
            }
            for (int index = 0; index < step.assignees().size(); index++) {
                if (result.size() >= MAX_TASKS) {
                    throw new ApprovalDomainException("resolved approval route is too large");
                }
                result.add(ApprovalTask.create(step.code(), step.name(), step.order(),
                        step.mode(), index, step.assignees().get(index),
                        ApprovalTaskStatus.WAITING));
            }
        }
        return List.copyOf(result);
    }

    private static void validateRuntime(ApprovalInstance instance) {
        if (instance.tasks.size() > MAX_TASKS
                || instance.tasks.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApprovalDomainException("stored approval tasks are invalid");
        }
        if (instance.status == ApprovalInstanceStatus.PENDING
                && (instance.currentStepOrder == null
                || instance.tasks.stream().noneMatch(task -> task.getStatus() == ApprovalTaskStatus.PENDING))) {
            throw new ApprovalDomainException("stored pending approval has no actionable task");
        }
        if (instance.status != ApprovalInstanceStatus.PENDING && instance.currentStepOrder != null) {
            throw new ApprovalDomainException("stored terminal approval has a current step");
        }
    }

    private static Map<FieldCode, List<String>> validateFacts(
            BusinessObjectType objectType, Map<FieldCode, List<String>> values) {
        if (values == null) {
            return Map.of();
        }
        int maximum = objectType == BusinessObjectType.OA_REQUEST
                ? MAX_FACTS : MAX_ROUTING_FACTS;
        if (values.size() > maximum
                || values.entrySet().stream().anyMatch(entry -> entry.getKey() == null)) {
            throw new ApprovalDomainException("approval facts are invalid");
        }
        LinkedHashMap<FieldCode, List<String>> normalized = new LinkedHashMap<>();
        values.forEach((key, raw) -> {
            List<String> list = raw == null ? List.of() : List.copyOf(raw);
            if (list.size() > FilterCondition.MAX_VALUES
                    || list.stream().anyMatch(value -> value == null || value.length() > 500)) {
                throw new ApprovalDomainException("approval fact values are invalid");
            }
            normalized.put(key, list);
        });
        return java.util.Collections.unmodifiableMap(normalized);
    }

    private static Set<MemberId> boundedMembers(Collection<MemberId> values,
                                                int maximum, String description) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<MemberId> set = new LinkedHashSet<>(values);
        if (values.size() > maximum || set.size() != values.size() || set.contains(null)) {
            throw new ApprovalDomainException(description + " are invalid");
        }
        return java.util.Collections.unmodifiableSet(set);
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new ApprovalDomainException("approval tenant is required");
        }
        return value;
    }

    private static ApprovalDefinitionId requireDefinition(ApprovalDefinitionId value) {
        if (value == null) {
            throw new ApprovalDomainException("approval definition is required");
        }
        return value;
    }

    private static Long requireVersion(Long value) {
        if (value == null || value < 0) {
            throw new ApprovalDomainException("approval definition version is invalid");
        }
        return value;
    }

    private static BusinessObjectType requireObject(BusinessObjectType value) {
        if (value == null) {
            throw new ApprovalDomainException("approval object type is required");
        }
        return value;
    }

    private static Long requireSubject(Long value) {
        if (value == null || value <= 0) {
            throw new ApprovalDomainException("approval subject ID must be positive");
        }
        return value;
    }

    private static MemberId requireMember(MemberId value) {
        if (value == null) {
            throw new ApprovalDomainException("approval member is required");
        }
        return value;
    }

    public static String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new ApprovalDomainException("approval idempotency key is invalid");
        }
        return normalized;
    }

    private static String required(String value, int maximum, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximum) {
            throw new ApprovalDomainException(description + " is invalid");
        }
        return normalized;
    }
}
