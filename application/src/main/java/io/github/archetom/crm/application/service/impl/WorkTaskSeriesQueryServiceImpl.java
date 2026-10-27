package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.WorkTaskSeriesQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorkTaskSeriesOccurrenceVO;
import io.github.archetom.crm.application.vo.WorkTaskSeriesVO;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkTaskSeriesQueryServiceImpl implements WorkTaskSeriesQueryService {
    private final WorkTaskSeriesRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final WorkTaskSeriesApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<WorkTaskSeriesVO>> query(AuthenticatedCaller caller, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_QUERY, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId member; private long actualPage; private long actualSize;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ);
                member = support.memberId(caller.actorId()); actualPage = page == null ? 1 : page; actualSize = size == null ? 20 : size;
                if (actualPage < 1 || actualPage > QueryRequest.MAX_PAGE || actualSize < 1 || actualSize > QueryRequest.MAX_SIZE)
                    throw support.invalid("Work task series page is invalid");
            }
            @Override public Pager<WorkTaskSeriesVO> execute() {
                var source = repository.findPage(tenantId, actualPage, actualSize, member,
                        caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE));
                Pager<WorkTaskSeriesVO> result = new Pager<>(); result.setPageNum(source.page()); result.setPageSize(source.size());
                result.setTotalNum(source.total()); result.setObjectList(source.items().stream().map(support::toVO).toList()); return result;
            }
        });
    }

    @Override
    public Result<WorkTaskSeriesVO> get(AuthenticatedCaller caller, Long seriesIdValue) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_GET, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId member; private io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId;
            @Override public void validate() { tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ);
                member = support.memberId(caller.actorId()); seriesId = support.seriesId(seriesIdValue); }
            @Override public WorkTaskSeriesVO execute() { return support.toVO(repository.findVisibleById(tenantId, seriesId, member,
                    caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE)).orElseThrow(() -> support.notFound("Work task series was not found"))); }
        });
    }

    @Override
    public Result<Pager<WorkTaskSeriesOccurrenceVO>> occurrences(AuthenticatedCaller caller, Long seriesIdValue, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.WORK_TASK_SERIES_OCCURRENCE_QUERY, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId member; private io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId seriesId; private long actualPage; private long actualSize;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.WORK_TASKS_READ); member = support.memberId(caller.actorId());
                seriesId = support.seriesId(seriesIdValue); actualPage = page == null ? 1 : page; actualSize = size == null ? 20 : size;
                if (actualPage < 1 || actualPage > QueryRequest.MAX_PAGE || actualSize < 1 || actualSize > QueryRequest.MAX_SIZE)
                    throw support.invalid("Work task series occurrence page is invalid");
            }
            @Override public Pager<WorkTaskSeriesOccurrenceVO> execute() {
                repository.findVisibleById(tenantId, seriesId, member, caller.hasAuthority(AuthorityCatalog.WORK_TASKS_MANAGE))
                        .orElseThrow(() -> support.notFound("Work task series was not found"));
                var source = repository.findOccurrences(tenantId, seriesId, actualPage, actualSize);
                Pager<WorkTaskSeriesOccurrenceVO> result = new Pager<>(); result.setPageNum(source.page()); result.setPageSize(source.size());
                result.setTotalNum(source.total()); result.setObjectList(source.items().stream().map(support::toVO).toList()); return result;
            }
        });
    }
}
