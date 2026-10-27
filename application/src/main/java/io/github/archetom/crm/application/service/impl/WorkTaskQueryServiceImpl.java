package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.WorkTaskQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskVO;
import io.github.archetom.crm.domain.exception.WorkTaskNotFoundException;
import io.github.archetom.crm.domain.model.WorkTaskParticipantRole;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskQuery;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkTaskQueryServiceImpl implements WorkTaskQueryService {

    private final WorkTaskRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final WorkTaskApplicationSupport support;
    private final WorkTaskOutputSupport outputSupport;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;

    @Override
    public Result<Pager<WorkTaskVO>> query(
            AuthenticatedCaller caller, WorkTaskQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private WorkTaskParticipantRole participantRole;
                    private Set<WorkTaskStatus> statuses;
                    private Set<WorkTaskPriority> priorities;
                    private LocalDateTime dueFrom;
                    private LocalDateTime dueBefore;
                    private WorkTaskBusinessRecord businessRecord;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        if (request == null || request.getPage() == null
                                || request.getSize() == null
                                || request.getPage() < 1
                                || request.getPage() > QueryRequest.MAX_PAGE
                                || request.getSize() < 1
                                || request.getSize() > QueryRequest.MAX_SIZE) {
                            throw support.invalid("Work task query is invalid");
                        }
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_READ);
                        memberId = support.memberId(caller.actorId());
                        participantRole = enumValue(WorkTaskParticipantRole.class,
                                request.getParticipantRole(), WorkTaskParticipantRole.ALL,
                                "Work task participant role is invalid");
                        statuses = enumSet(WorkTaskStatus.class, request.getStatuses(),
                                "Work task status filter is invalid");
                        if (statuses.contains(WorkTaskStatus.DELETED)) {
                            throw support.invalid("Deleted work tasks cannot be queried");
                        }
                        priorities = enumSet(WorkTaskPriority.class, request.getPriorities(),
                                "Work task priority filter is invalid");
                        dueFrom = time(request.getDueFromInclusive(),
                                "Work task due-time lower bound is invalid");
                        dueBefore = time(request.getDueBefore(),
                                "Work task due-time upper bound is invalid");
                        if (dueFrom != null && dueBefore != null
                                && !dueFrom.isBefore(dueBefore)) {
                            throw support.invalid(
                                    "Work task due-time range must be non-empty");
                        }
                        businessRecord = support.businessRecord(
                                request.getBusinessObjectType(), request.getBusinessRecordId());
                        now = now();
                    }

                    @Override
                    public Pager<WorkTaskVO> execute() {
                        requireReadableBusinessRecord(caller, tenantId, businessRecord);
                        var page = repository.findPage(tenantId, new WorkTaskQuery(
                                request.getPage(), request.getSize(), memberId,
                                caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE),
                                participantRole, statuses, priorities, dueFrom, dueBefore,
                                businessRecord));
                        Pager<WorkTaskVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(outputSupport.toVOs(
                                caller, tenantId, page.items(), now));
                        return result;
                    }
                });
    }

    @Override
    public Result<WorkTaskVO> get(AuthenticatedCaller caller, Long taskIdValue) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private io.github.archetom.crm.domain.valueobject.WorkTaskId taskId;
                    private LocalDateTime now;

                    @Override
                    public void validate() {
                        taskId = support.taskId(taskIdValue);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORK_TASKS_READ);
                        memberId = support.memberId(caller.actorId());
                        now = now();
                    }

                    @Override
                    public WorkTaskVO execute() {
                        return outputSupport.toVO(caller, tenantId,
                                repository.findVisibleById(
                                        tenantId, taskId, memberId,
                                        caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                                .orElseThrow(WorkTaskNotFoundException::new), now);
                    }
                });
    }

    private <E extends Enum<E>> E enumValue(
            Class<E> type, String value, E fallback, String message) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw support.invalid(message);
        }
    }

    private <E extends Enum<E>> Set<E> enumSet(
            Class<E> type, java.util.Collection<String> values, String message) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<E> result = new LinkedHashSet<>();
        try {
            for (String value : values) {
                result.add(Enum.valueOf(type, value == null ? ""
                        : value.trim().toUpperCase(Locale.ROOT)));
            }
        } catch (IllegalArgumentException exception) {
            throw support.invalid(message);
        }
        return Set.copyOf(result);
    }

    private LocalDateTime time(LocalDateTime value, String message) {
        if (value == null) {
            return null;
        }
        if (value.getYear() < 1000 || value.getYear() > 9999) {
            throw support.invalid(message);
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private void requireReadableBusinessRecord(
            AuthenticatedCaller caller, TenantId tenantId,
            WorkTaskBusinessRecord businessRecord) {
        if (businessRecord == null) {
            return;
        }
        TenantId recordTenant = recordAccessGuard.requireVisible(caller,
                businessRecord.objectType(), businessRecord.recordId(), false);
        if (!tenantId.sameValueAs(recordTenant)) {
            throw support.denied("Business record tenant does not match work task tenant");
        }
    }
}
