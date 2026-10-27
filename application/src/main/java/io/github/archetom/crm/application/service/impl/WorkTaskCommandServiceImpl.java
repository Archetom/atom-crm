package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.WorkTaskCommandService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.exception.WorkTaskNotFoundException;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.repository.WorkTaskCommentRepository;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.BiConsumer;
import java.util.List;

@Service
public class WorkTaskCommandServiceImpl implements WorkTaskCommandService {

    private final WorkTaskRepository repository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final WorkTaskApplicationSupport support;
    private final WorkTaskOutputSupport outputSupport;
    private final WorkTaskCommentRepository commentRepository;
    private final AttachmentFileRepository attachmentFileRepository;
    private final WorkTaskReminderCoordinator reminderCoordinator;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Autowired
    public WorkTaskCommandServiceImpl(
            WorkTaskRepository repository, OrganizationMembershipRepository membershipRepository,
            OrganizationTenantRepository tenantRepository, OrganizationAccessGuard accessGuard,
            BusinessRecordAccessGuard recordAccessGuard, WorkTaskApplicationSupport support,
            WorkTaskOutputSupport outputSupport, WorkTaskCommentRepository commentRepository,
            AttachmentFileRepository attachmentFileRepository,
            WorkTaskReminderCoordinator reminderCoordinator,
            CommandServiceTemplate commandTemplate, Clock clock) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.accessGuard = accessGuard;
        this.recordAccessGuard = recordAccessGuard;
        this.support = support;
        this.outputSupport = outputSupport;
        this.commentRepository = commentRepository;
        this.attachmentFileRepository = attachmentFileRepository;
        this.reminderCoordinator = reminderCoordinator;
        this.commandTemplate = commandTemplate;
        this.clock = clock;
    }

    /** Compatibility constructor retained for pre-B24a isolated unit tests. */
    public WorkTaskCommandServiceImpl(
            WorkTaskRepository repository, OrganizationMembershipRepository membershipRepository,
            OrganizationTenantRepository tenantRepository, OrganizationAccessGuard accessGuard,
            BusinessRecordAccessGuard recordAccessGuard, WorkTaskApplicationSupport support,
            WorkTaskOutputSupport outputSupport, WorkTaskCommentRepository commentRepository,
            CommandServiceTemplate commandTemplate, Clock clock) {
        this(repository, membershipRepository, tenantRepository, accessGuard, recordAccessGuard, support,
                outputSupport, commentRepository, null, null, commandTemplate, clock);
    }

    @Override
    public Result<WorkTaskVO> create(
            AuthenticatedCaller caller, WorkTaskCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId creator;
                    private MemberId assignee;
                    private List<MemberId> followers;
                    private WorkTaskBusinessRecord businessRecord;
                    private io.github.archetom.crm.domain.model.WorkTaskPriority priority;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null) {
                            throw support.invalid("Work task create request is required");
                        }
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        creator = support.memberId(caller.actorId());
                        assignee = support.memberId(request.getAssigneeActorId());
                        followers = support.followerIds(request.getFollowerActorIds());
                        businessRecord = support.businessRecord(
                                request.getBusinessObjectType(), request.getBusinessRecordId());
                        priority = support.priority(request.getPriority(), true);
                        now = now();
                    }

                    @Override
                    public WorkTaskVO execute() {
                        lockTenant(tenantId);
                        requireActiveMember(tenantId, creator, true, "caller");
                        requireActiveMember(tenantId, assignee, false, "assignee");
                        followers.forEach(follower -> requireActiveMember(
                                tenantId, follower, false, "follower"));
                        requireWritableBusinessRecord(caller, tenantId, businessRecord);
                        WorkTask task = WorkTask.create(tenantId, creator, assignee,
                                request.getTitle(), request.getDescription(), priority,
                                request.getDueTime(), now);
                        task.replaceFollowers(followers, now);
                        task.updateBusinessRecord(businessRecord, now);
                        return outputSupport.toVO(caller, tenantId,
                                saveTask(tenantId, task, now), now);
                    }
                });
    }

    @Override
    public Result<WorkTaskVO> update(
            AuthenticatedCaller caller, Long taskIdValue,
            WorkTaskUpdateRequest request) {
        return mutate(UseCaseOperation.WORK_TASK_UPDATE, caller, taskIdValue,
                request == null ? null : request.getVersion(),
                (task, now) -> {
                    requireStructuralManager(caller, task);
                    task.updateStructure(request.getTitle(), request.getDescription(),
                            support.priority(request.getPriority(), false),
                            request.getDueTime(), now);
                }, request != null);
    }

    @Override
    public Result<WorkTaskVO> updateAssignee(
            AuthenticatedCaller caller, Long taskIdValue,
            WorkTaskAssigneeUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_ASSIGNEE_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId callerMemberId;
                    private MemberId assignee;
                    private WorkTaskId taskId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null) {
                            throw support.invalid("Work task assignee request is required");
                        }
                        if (request.getVersion() == null || request.getVersion() < 0) {
                            throw support.invalid("Work task version is invalid");
                        }
                        taskId = support.taskId(taskIdValue);
                        assignee = support.memberId(request.getAssigneeActorId());
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        callerMemberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public WorkTaskVO execute() {
                        lockTenant(tenantId);
                        WorkTask task = lockedVisible(
                                caller, tenantId, callerMemberId, taskId);
                        support.requireVersion(task, request.getVersion());
                        requireStructuralManager(caller, task);
                        requireActiveMember(tenantId, callerMemberId, true, "caller");
                        requireActiveMember(tenantId, assignee, false, "assignee");
                        task.reassign(assignee, now);
                        return outputSupport.toVO(caller, tenantId,
                                saveTask(tenantId, task, now), now);
                    }
                });
    }

    @Override
    public Result<WorkTaskVO> replaceFollowers(
            AuthenticatedCaller caller, Long taskIdValue,
            WorkTaskFollowerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_FOLLOWER_REPLACE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId callerMemberId;
                    private WorkTaskId taskId;
                    private List<MemberId> followers;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null || request.getVersion() == null
                                || request.getVersion() < 0
                                || request.getFollowerActorIds() == null) {
                            throw support.invalid("Work task follower request is invalid");
                        }
                        taskId = support.taskId(taskIdValue);
                        followers = support.followerIds(request.getFollowerActorIds());
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        callerMemberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public WorkTaskVO execute() {
                        lockTenant(tenantId);
                        WorkTask task = lockedVisible(
                                caller, tenantId, callerMemberId, taskId);
                        support.requireVersion(task, request.getVersion());
                        requireStructuralManager(caller, task);
                        requireActiveMember(tenantId, callerMemberId, true, "caller");
                        followers.forEach(follower -> requireActiveMember(
                                tenantId, follower, false, "follower"));
                        task.replaceFollowers(followers, now);
                        return outputSupport.toVO(caller, tenantId,
                                saveTask(tenantId, task, now), now);
                    }
                });
    }

    @Override
    public Result<WorkTaskVO> updateBusinessRecord(
            AuthenticatedCaller caller, Long taskIdValue,
            WorkTaskBusinessRecordUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_BUSINESS_RECORD_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId callerMemberId;
                    private WorkTaskId taskId;
                    private WorkTaskBusinessRecord businessRecord;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null || request.getVersion() == null
                                || request.getVersion() < 0) {
                            throw support.invalid("Work task business record request is invalid");
                        }
                        taskId = support.taskId(taskIdValue);
                        businessRecord = support.businessRecord(
                                request.getBusinessObjectType(), request.getBusinessRecordId());
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        callerMemberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public WorkTaskVO execute() {
                        lockTenant(tenantId);
                        WorkTask task = lockedVisible(
                                caller, tenantId, callerMemberId, taskId);
                        support.requireVersion(task, request.getVersion());
                        requireActiveMember(tenantId, callerMemberId, true, "caller");
                        // The link is task-owned. Resolving the old target would expose whether
                        // an omitted relationship exists and would trap deleted CRM targets.
                        requireWritableBusinessRecord(caller, tenantId, businessRecord);
                        task.updateBusinessRecord(businessRecord, now);
                        return outputSupport.toVO(caller, tenantId,
                                saveTask(tenantId, task, now), now);
                    }
                });
    }

    @Override
    public Result<WorkTaskVO> start(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return transition(UseCaseOperation.WORK_TASK_START, caller, taskId, request,
                WorkTask::start);
    }

    @Override
    public Result<WorkTaskVO> returnToTodo(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return transition(UseCaseOperation.WORK_TASK_RETURN_TO_TODO, caller, taskId, request,
                WorkTask::returnToTodo);
    }

    @Override
    public Result<WorkTaskVO> complete(
            AuthenticatedCaller caller, Long taskId, WorkTaskVersionRequest request) {
        return transition(UseCaseOperation.WORK_TASK_COMPLETE, caller, taskId, request,
                WorkTask::complete);
    }

    @Override
    public Result<WorkTaskVO> cancel(
            AuthenticatedCaller caller, Long taskIdValue, WorkTaskVersionRequest request) {
        return mutate(UseCaseOperation.WORK_TASK_CANCEL, caller, taskIdValue,
                request == null ? null : request.getVersion(),
                (task, now) -> {
                    requireStructuralManager(caller, task);
                    task.cancel(now);
                }, request != null);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long taskIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId callerMemberId;
                    private WorkTaskId taskId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        taskId = support.taskId(taskIdValue);
                        if (version == null || version < 0) {
                            throw support.invalid("Work task version is invalid");
                        }
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        callerMemberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public Void execute() {
                        lockTenant(tenantId);
                        WorkTask task = lockedVisible(
                                caller, tenantId, callerMemberId, taskId);
                        support.requireVersion(task, version);
                        requireStructuralManager(caller, task);
                        requireActiveMember(tenantId, callerMemberId, true, "caller");
                        boolean attachmentHistory = attachmentFileRepository != null
                                && attachmentFileRepository.existsHistoryByTargetForUpdate(tenantId,
                                new AttachmentTarget(AttachmentTargetType.WORK_TASK,
                                        taskId.getValue()));
                        task.delete(commentRepository.existsAny(tenantId, taskId),
                                attachmentHistory, now);
                        saveTask(tenantId, task, now);
                        return null;
                    }
                });
    }

    private Result<WorkTaskVO> transition(
            UseCaseOperation operation, AuthenticatedCaller caller, Long taskId,
            WorkTaskVersionRequest request, BiConsumer<WorkTask, LocalDateTime> action) {
        return mutate(operation, caller, taskId,
                request == null ? null : request.getVersion(),
                (task, now) -> {
                    MemberId memberId = support.memberId(caller.actorId());
                    if (!task.isAssignedTo(memberId)) {
                        throw support.denied(
                                "Only the current work task assignee can change progress");
                    }
                    action.accept(task, now);
                }, request != null);
    }

    private Result<WorkTaskVO> mutate(
            UseCaseOperation operation, AuthenticatedCaller caller, Long taskIdValue,
            Long version, BiConsumer<WorkTask, LocalDateTime> mutation,
            boolean requestPresent) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId callerMemberId;
            private WorkTaskId taskId;
            private LocalDateTime now;

            @Override
            public void validate() {
                if (!requestPresent) {
                    throw support.invalid("Work task request is required");
                }
                taskId = support.taskId(taskIdValue);
                if (version == null || version < 0) {
                    throw support.invalid("Work task version is invalid");
                }
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.WORK_TASKS_WRITE);
                callerMemberId = support.memberId(caller.actorId());
                now = now();
            }

            @Override
            public WorkTaskVO execute() {
                lockTenant(tenantId);
                WorkTask task = lockedVisible(caller, tenantId, callerMemberId, taskId);
                support.requireVersion(task, version);
                requireActiveMember(tenantId, callerMemberId, true, "caller");
                mutation.accept(task, now);
                return outputSupport.toVO(caller, tenantId,
                        saveTask(tenantId, task, now), now);
            }
        });
    }

    private WorkTask lockedVisible(
            AuthenticatedCaller caller, TenantId tenantId,
            MemberId callerMemberId, WorkTaskId taskId) {
        return repository.findVisibleByIdForUpdate(tenantId, taskId, callerMemberId,
                        caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                .orElseThrow(WorkTaskNotFoundException::new);
    }

    private void requireStructuralManager(AuthenticatedCaller caller, WorkTask task) {
        MemberId memberId = support.memberId(caller.actorId());
        if (!task.isCreatedBy(memberId)
                && !caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)) {
            throw support.denied(
                    "Only the work task creator or a task manager can manage the task");
        }
    }

    private OrganizationMembership requireActiveMember(
            TenantId tenantId, MemberId memberId,
            boolean callerMember, String role) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> callerMember
                        ? support.denied("Caller is not an active member of this tenant")
                        : support.invalid("Work task " + role
                                + " is not an active tenant member"));
    }

    private void requireWritableBusinessRecord(
            AuthenticatedCaller caller, TenantId tenantId,
            WorkTaskBusinessRecord businessRecord) {
        if (businessRecord == null) {
            return;
        }
        TenantId recordTenant = recordAccessGuard.requireVisible(caller,
                businessRecord.objectType(), businessRecord.recordId(), true);
        if (!tenantId.sameValueAs(recordTenant)) {
            throw support.denied("Business record tenant does not match work task tenant");
        }
    }

    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw support.notFound("Work task tenant was not found");
        }
    }

    private WorkTask saveTask(TenantId tenantId, WorkTask task, LocalDateTime now) {
        if (reminderCoordinator != null) {
            reminderCoordinator.validateForPersistence(task);
        }
        WorkTask saved = repository.save(tenantId, task);
        if (reminderCoordinator != null) {
            reminderCoordinator.synchronize(tenantId, saved, now);
        }
        return saved;
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
