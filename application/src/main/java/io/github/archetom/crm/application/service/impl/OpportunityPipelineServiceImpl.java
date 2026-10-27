package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.OpportunityPipelineCatalogLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.OpportunityPipelineService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OpportunityPipelineStageVO;
import io.github.archetom.crm.application.vo.OpportunityPipelineVO;
import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.exception.OpportunityPipelineNotFoundException;
import io.github.archetom.crm.domain.model.OpportunityPipelineQuery;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Tenant sales-pipeline catalog commands and queries with an explicit catalog lock. */
@Service
@RequiredArgsConstructor
public class OpportunityPipelineServiceImpl implements OpportunityPipelineService {

    private static final int MAX_PIPELINES = 100;
    private static final int MAX_STAGES = 100;

    private final OpportunityPipelineRepository repository;
    private final OpportunityPipelineCatalogLock catalogLock;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<OpportunityPipelineVO>> query(
            AuthenticatedCaller caller, OpportunityPipelineQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_READ);
                        query = query(request);
                    }

                    @Override
                    public Pager<OpportunityPipelineVO> execute() {
                        PageResult<OpportunityPipeline> result = repository.findPipelines(
                                tenantId, query);
                        return pager(result, OpportunityPipelineServiceImpl::pipelineVO);
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineVO> get(AuthenticatedCaller caller, Long pipelineIdValue) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_READ);
                        pipelineId = pipelineId(pipelineIdValue);
                    }

                    @Override
                    public OpportunityPipelineVO execute() {
                        return pipelineVO(requirePipeline(tenantId, pipelineId));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineVO> create(
            AuthenticatedCaller caller, OpportunityPipelineCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline create request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                    }

                    @Override
                    public OpportunityPipelineVO execute() {
                        catalogLock.acquire(tenantId);
                        if (repository.countNonDeletedPipelines(tenantId) >= MAX_PIPELINES) {
                            throw denied("Opportunity pipeline capacity has been reached");
                        }
                        OpportunityPipeline pipeline = OpportunityPipeline.create(tenantId,
                                request.getCode(), request.getName(), request.getDisplayOrder());
                        return pipelineVO(repository.savePipeline(tenantId, pipeline));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineVO> update(
            AuthenticatedCaller caller, Long pipelineIdValue,
            OpportunityPipelineUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline update request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                    }

                    @Override
                    public OpportunityPipelineVO execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        requireVersion(pipeline.getVersion(), request.getVersion());
                        pipeline.update(request.getName(), request.getDisplayOrder());
                        return pipelineVO(repository.savePipeline(tenantId, pipeline));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineVO> changeStatus(
            AuthenticatedCaller caller, Long pipelineIdValue,
            OpportunityPipelineStatusUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineStatus status;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline status request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                        status = transitionStatus(request.getStatus());
                    }

                    @Override
                    public OpportunityPipelineVO execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        requireVersion(pipeline.getVersion(), request.getVersion());
                        if (status == OpportunityPipelineStatus.ACTIVE) {
                            if (!repository.existsActiveStage(tenantId, pipeline.getCode())) {
                                throw denied("Opportunity pipeline has no active stage");
                            }
                            pipeline.activate();
                        } else {
                            pipeline.deactivate();
                        }
                        return pipelineVO(repository.savePipeline(tenantId, pipeline));
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long pipelineIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                    }

                    @Override
                    public Void execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        requireVersion(pipeline.getVersion(), version);
                        if (repository.existsNonDeletedStage(tenantId, pipeline.getCode())) {
                            throw denied("Opportunity pipeline still has stages");
                        }
                        pipeline.delete();
                        repository.savePipeline(tenantId, pipeline);
                        return null;
                    }
                });
    }

    @Override
    public Result<Pager<OpportunityPipelineStageVO>> queryStages(
            AuthenticatedCaller caller, Long pipelineIdValue,
            OpportunityPipelineStageQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineQuery query;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_READ);
                        pipelineId = pipelineId(pipelineIdValue);
                        query = query(request);
                    }

                    @Override
                    public Pager<OpportunityPipelineStageVO> execute() {
                        OpportunityPipeline pipeline = requirePipeline(tenantId, pipelineId);
                        PageResult<OpportunityPipelineStage> result = repository.findStages(
                                tenantId, pipeline.getCode(), query);
                        return pager(result, OpportunityPipelineServiceImpl::stageVO);
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineStageVO> getStage(
            AuthenticatedCaller caller, Long pipelineIdValue, Long stageIdValue) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineStageId stageId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_READ);
                        pipelineId = pipelineId(pipelineIdValue);
                        stageId = stageId(stageIdValue);
                    }

                    @Override
                    public OpportunityPipelineStageVO execute() {
                        OpportunityPipeline pipeline = requirePipeline(tenantId, pipelineId);
                        return stageVO(requireStage(tenantId, pipeline, stageId));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineStageVO> createStage(
            AuthenticatedCaller caller, Long pipelineIdValue,
            OpportunityPipelineStageCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline stage create request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                    }

                    @Override
                    public OpportunityPipelineStageVO execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        if (repository.countNonDeletedStages(tenantId,
                                pipeline.getCode()) >= MAX_STAGES) {
                            throw denied("Opportunity pipeline stage capacity has been reached");
                        }
                        OpportunityPipelineStage stage = OpportunityPipelineStage.create(tenantId,
                                pipeline.getCode(), request.getCode(), request.getName(),
                                request.getDisplayOrder(), request.getProbability());
                        return stageVO(repository.saveStage(tenantId, stage));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineStageVO> updateStage(
            AuthenticatedCaller caller, Long pipelineIdValue, Long stageIdValue,
            OpportunityPipelineStageUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineStageId stageId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline stage update request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                        stageId = stageId(stageIdValue);
                    }

                    @Override
                    public OpportunityPipelineStageVO execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        OpportunityPipelineStage stage = requireMutableStage(tenantId, pipeline, stageId);
                        requireVersion(stage.getVersion(), request.getVersion());
                        stage.update(request.getName(), request.getDisplayOrder(),
                                request.getProbability());
                        return stageVO(repository.saveStage(tenantId, stage));
                    }
                });
    }

    @Override
    public Result<OpportunityPipelineStageVO> changeStageStatus(
            AuthenticatedCaller caller, Long pipelineIdValue, Long stageIdValue,
            OpportunityPipelineStageStatusUpdateRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineStageId stageId;
                    private OpportunityPipelineStatus status;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity pipeline stage status request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                        stageId = stageId(stageIdValue);
                        status = transitionStatus(request.getStatus());
                    }

                    @Override
                    public OpportunityPipelineStageVO execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        OpportunityPipelineStage stage = requireMutableStage(tenantId, pipeline, stageId);
                        requireVersion(stage.getVersion(), request.getVersion());
                        if (status == OpportunityPipelineStatus.ACTIVE) {
                            stage.activate();
                        } else {
                            if (pipeline.isActive() && stage.isActive()
                                    && repository.countActiveStages(tenantId,
                                    pipeline.getCode()) <= 1) {
                                throw denied("An active opportunity pipeline requires one active stage");
                            }
                            stage.deactivate();
                        }
                        return stageVO(repository.saveStage(tenantId, stage));
                    }
                });
    }

    @Override
    public Result<Void> deleteStage(
            AuthenticatedCaller caller, Long pipelineIdValue, Long stageIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PIPELINE_STAGE_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OpportunityPipelineId pipelineId;
                    private OpportunityPipelineStageId stageId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_MANAGE);
                        pipelineId = pipelineId(pipelineIdValue);
                        stageId = stageId(stageIdValue);
                    }

                    @Override
                    public Void execute() {
                        catalogLock.acquire(tenantId);
                        OpportunityPipeline pipeline = requireMutablePipeline(tenantId, pipelineId);
                        OpportunityPipelineStage stage = requireMutableStage(tenantId, pipeline, stageId);
                        requireVersion(stage.getVersion(), version);
                        stage.delete();
                        repository.saveStage(tenantId, stage);
                        return null;
                    }
                });
    }

    private OpportunityPipelineQuery query(OpportunityPipelineQueryRequest request) {
        if (request == null) {
            throw invalid("Opportunity pipeline query request is required");
        }
        return new OpportunityPipelineQuery(keyword(request.getKeyword()),
                queryStatus(request.getStatus()), page(request.getPage()), size(request.getSize()));
    }

    private OpportunityPipelineQuery query(OpportunityPipelineStageQueryRequest request) {
        if (request == null) {
            throw invalid("Opportunity pipeline stage query request is required");
        }
        return new OpportunityPipelineQuery(keyword(request.getKeyword()),
                queryStatus(request.getStatus()), page(request.getPage()), size(request.getSize()));
    }

    private OpportunityPipeline requirePipeline(TenantId tenantId, OpportunityPipelineId id) {
        return repository.findPipelineById(tenantId, id)
                .orElseThrow(OpportunityPipelineNotFoundException::new);
    }

    private OpportunityPipeline requireMutablePipeline(
            TenantId tenantId, OpportunityPipelineId id) {
        OpportunityPipeline pipeline = requirePipeline(tenantId, id);
        if (pipeline.isDeleted()) {
            throw denied("Opportunity pipeline is unavailable");
        }
        return pipeline;
    }

    private OpportunityPipelineStage requireStage(
            TenantId tenantId, OpportunityPipeline pipeline, OpportunityPipelineStageId id) {
        return repository.findStageById(tenantId, pipeline.getCode(), id)
                .orElseThrow(OpportunityPipelineNotFoundException::new);
    }

    private OpportunityPipelineStage requireMutableStage(
            TenantId tenantId, OpportunityPipeline pipeline, OpportunityPipelineStageId id) {
        OpportunityPipelineStage stage = requireStage(tenantId, pipeline, id);
        if (stage.isDeleted()) {
            throw denied("Opportunity pipeline stage is unavailable");
        }
        return stage;
    }

    private OpportunityPipelineStatus transitionStatus(String value) {
        OpportunityPipelineStatus status = queryStatus(value);
        if (status == null || status == OpportunityPipelineStatus.DELETED) {
            throw invalid("Opportunity pipeline status is invalid");
        }
        return status;
    }

    private OpportunityPipelineStatus queryStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OpportunityPipelineStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Opportunity pipeline status is invalid");
        }
    }

    private String keyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String keyword = value.trim();
        if (keyword.codePointCount(0, keyword.length()) > 200) {
            throw invalid("Opportunity pipeline keyword is too long");
        }
        return keyword;
    }

    private int page(Integer value) {
        if (value == null || value < 1 || value > 1_000_000) {
            throw invalid("Opportunity pipeline page is invalid");
        }
        return value;
    }

    private int size(Integer value) {
        if (value == null || value < 1 || value > 200) {
            throw invalid("Opportunity pipeline page size is invalid");
        }
        return value;
    }

    private OpportunityPipelineId pipelineId(Long value) {
        try {
            return new OpportunityPipelineId(value);
        } catch (RuntimeException exception) {
            throw invalid("Opportunity pipeline ID is invalid");
        }
    }

    private OpportunityPipelineStageId stageId(Long value) {
        try {
            return new OpportunityPipelineStageId(value);
        } catch (RuntimeException exception) {
            throw invalid("Opportunity pipeline stage ID is invalid");
        }
    }

    private void requireVersion(Long actual, Long expected) {
        if (expected == null || !expected.equals(actual)) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.VERSION_CONFLICT,
                    "Opportunity pipeline version is stale");
        }
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw invalid(message + " is required");
        }
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED, message);
    }

    private static OpportunityPipelineVO pipelineVO(OpportunityPipeline value) {
        return new OpportunityPipelineVO(value.getId().getValue(), value.getCode(),
                value.getName(), value.getDisplayOrder(), value.getStatus().name(),
                value.getVersion(), value.getCreatedTime(), value.getUpdatedTime());
    }

    private static OpportunityPipelineStageVO stageVO(OpportunityPipelineStage value) {
        return new OpportunityPipelineStageVO(value.getId().getValue(),
                value.getPipelineCode(), value.getCode(), value.getName(),
                value.getDisplayOrder(), value.getProbability(), value.getStatus().name(),
                value.getVersion(), value.getCreatedTime(), value.getUpdatedTime());
    }

    private static <S, T> Pager<T> pager(
            PageResult<S> source, java.util.function.Function<S, T> mapper) {
        Pager<T> result = new Pager<>();
        result.setPageNum((int) source.page());
        result.setPageSize((int) source.size());
        result.setTotalNum(source.total());
        result.setObjectList(source.items().stream().map(mapper).toList());
        return result;
    }
}
