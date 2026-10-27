package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCommentCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.WorkTaskCommentService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskCommentVO;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.entity.WorkTaskComment;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.WorkTaskNotFoundException;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskCommentRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class WorkTaskCommentServiceImpl implements WorkTaskCommentService {

    private final WorkTaskRepository taskRepository;
    private final WorkTaskCommentRepository commentRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final WorkTaskApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<Pager<WorkTaskCommentVO>> query(
            AuthenticatedCaller caller, Long taskIdValue, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_COMMENT_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private WorkTaskId taskId;

                    @Override
                    public void validate() {
                        if (page == null || size == null || page < 1
                                || page > QueryRequest.MAX_PAGE || size < 1
                                || size > QueryRequest.MAX_SIZE) {
                            throw support.invalid("Work task comment page is invalid");
                        }
                        taskId = support.taskId(taskIdValue);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_READ);
                        memberId = support.memberId(caller.actorId());
                    }

                    @Override
                    public Pager<WorkTaskCommentVO> execute() {
                        requireVisible(caller, tenantId, memberId, taskId, false);
                        var pageResult = commentRepository.findPage(
                                tenantId, taskId, page, size);
                        Pager<WorkTaskCommentVO> result = new Pager<>();
                        result.setPageNum(pageResult.page());
                        result.setPageSize(pageResult.size());
                        result.setTotalNum(pageResult.total());
                        result.setObjectList(pageResult.items().stream()
                                .map(support::toVO).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<WorkTaskCommentVO> create(
            AuthenticatedCaller caller, Long taskIdValue,
            WorkTaskCommentCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_COMMENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private WorkTaskId taskId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null) {
                            throw support.invalid("Work task comment request is required");
                        }
                        taskId = support.taskId(taskIdValue);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        memberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public WorkTaskCommentVO execute() {
                        lockTenant(tenantId);
                        requireVisible(caller, tenantId, memberId, taskId, true);
                        return support.toVO(commentRepository.save(tenantId,
                                WorkTaskComment.create(tenantId, taskId, memberId,
                                        request.getContent(), now)));
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long taskIdValue,
            Long commentIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.WORK_TASK_COMMENT_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private WorkTaskId taskId;
                    private WorkTaskCommentId commentId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (version == null || version < 0) {
                            throw support.invalid("Work task comment version is invalid");
                        }
                        taskId = support.taskId(taskIdValue);
                        commentId = support.commentId(commentIdValue);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_WRITE);
                        memberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public Void execute() {
                        lockTenant(tenantId);
                        requireVisible(caller, tenantId, memberId, taskId, true);
                        WorkTaskComment comment = commentRepository.findByIdForUpdate(
                                        tenantId, taskId, commentId)
                                .orElseThrow(() -> support.notFound(
                                        "Work task comment was not found"));
                        if (!version.equals(comment.getVersion())) {
                            throw new AggregateVersionConflictException();
                        }
                        comment.delete(memberId, now);
                        commentRepository.save(tenantId, comment);
                        return null;
                    }
                });
    }

    private WorkTask requireVisible(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId,
            WorkTaskId taskId, boolean forUpdate) {
        boolean manager = caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE);
        return (forUpdate
                ? taskRepository.findVisibleByIdForUpdate(
                        tenantId, taskId, memberId, manager)
                : taskRepository.findVisibleById(
                        tenantId, taskId, memberId, manager))
                .orElseThrow(WorkTaskNotFoundException::new);
    }

    private void lockTenant(TenantId tenantId) {
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw support.notFound("Work task tenant was not found");
        }
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }
}
