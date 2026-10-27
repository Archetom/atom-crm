package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OpportunityPipelineAlreadyExistsException;
import io.github.archetom.crm.domain.model.OpportunityPipelineQuery;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.OpportunityPipelinePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityPipelineMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityPipelineStageMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelinePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelineStagePO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/** MySQL tenant catalog adapter with binary-code predicates and stable SQL pagination. */
@Repository
@RequiredArgsConstructor
public class OpportunityPipelineRepositoryImpl implements OpportunityPipelineRepository {

    private final OpportunityPipelineMapper pipelineMapper;
    private final OpportunityPipelineStageMapper stageMapper;
    private final OpportunityPipelinePOConverter converter;

    @Override
    public OpportunityPipeline savePipeline(TenantId tenantId, OpportunityPipeline pipeline) {
        requirePipelineMatch(tenantId, pipeline);
        OpportunityPipelinePO po = converter.toPO(pipeline);
        try {
            if (po.getId() == null) {
                if (pipelineMapper.insert(po) != 1) {
                    throw new IllegalStateException("opportunity pipeline insert failed");
                }
            } else if (pipelineMapper.update(po, new LambdaUpdateWrapper<OpportunityPipelinePO>()
                    .eq(OpportunityPipelinePO::getTenantId, tenantId.getValue())
                    .eq(OpportunityPipelinePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            pipeline.onPersisted(new OpportunityPipelineId(po.getId()), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return pipeline;
        } catch (DuplicateKeyException exception) {
            throw new OpportunityPipelineAlreadyExistsException();
        }
    }

    @Override
    public OpportunityPipelineStage saveStage(TenantId tenantId, OpportunityPipelineStage stage) {
        requireStageMatch(tenantId, stage);
        OpportunityPipelineStagePO po = converter.toPO(stage);
        try {
            if (po.getId() == null) {
                if (stageMapper.insert(po) != 1) {
                    throw new IllegalStateException("opportunity pipeline stage insert failed");
                }
            } else if (stageMapper.update(po, new LambdaUpdateWrapper<OpportunityPipelineStagePO>()
                    .eq(OpportunityPipelineStagePO::getTenantId, tenantId.getValue())
                    .eq(OpportunityPipelineStagePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            stage.onPersisted(new OpportunityPipelineStageId(po.getId()), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return stage;
        } catch (DuplicateKeyException exception) {
            throw new OpportunityPipelineAlreadyExistsException();
        }
    }

    @Override
    public Optional<OpportunityPipeline> findPipelineById(
            TenantId tenantId, OpportunityPipelineId pipelineId) {
        requireTenant(tenantId);
        if (pipelineId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pipelineMapper.selectOne(pipelineFilter(tenantId)
                .eq(OpportunityPipelinePO::getId, pipelineId.getValue())))
                .map(converter::toDomain);
    }

    @Override
    public Optional<OpportunityPipelineStage> findStageById(
            TenantId tenantId, String pipelineCode, OpportunityPipelineStageId stageId) {
        requireTenant(tenantId);
        if (pipelineCode == null || stageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stageMapper.selectOne(stageFilter(tenantId, pipelineCode)
                .eq(OpportunityPipelineStagePO::getId, stageId.getValue())))
                .map(converter::toDomain);
    }

    @Override
    public Optional<OpportunityPipeline> findPipelineByCode(TenantId tenantId, String code) {
        requireTenant(tenantId);
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pipelineMapper.selectOne(pipelineFilter(tenantId)
                .eq(OpportunityPipelinePO::getCode, code))).map(converter::toDomain);
    }

    @Override
    public Optional<OpportunityPipelineStage> findStageByCode(
            TenantId tenantId, String pipelineCode, String stageCode) {
        requireTenant(tenantId);
        if (pipelineCode == null || stageCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stageMapper.selectOne(stageFilter(tenantId, pipelineCode)
                .eq(OpportunityPipelineStagePO::getCode, stageCode))).map(converter::toDomain);
    }

    @Override
    public PageResult<OpportunityPipeline> findPipelines(
            TenantId tenantId, OpportunityPipelineQuery query) {
        requireTenant(tenantId);
        requireQuery(query);
        IPage<OpportunityPipelinePO> result = pipelineMapper.selectPage(
                new Page<>(query.page(), query.size()), pipelineQuery(tenantId, query)
                        .orderByAsc(OpportunityPipelinePO::getDisplayOrder)
                        .orderByAsc(OpportunityPipelinePO::getCode)
                        .orderByAsc(OpportunityPipelinePO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0L, result.getTotal()), result.getRecords().stream()
                .map(converter::toDomain).toList());
    }

    @Override
    public Map<String, List<OpportunityPipelineStage>> findActiveStages(
            TenantId tenantId, Collection<String> pipelineCodes, int stageLimitPerPipeline) {
        requireTenant(tenantId);
        if (pipelineCodes == null || pipelineCodes.isEmpty()) {
            return Map.of();
        }
        if (stageLimitPerPipeline < 1 || stageLimitPerPipeline > 1_000) {
            throw new IllegalArgumentException("pipeline stage limit is invalid");
        }
        List<String> codes = pipelineCodes.stream()
                .filter(code -> code != null && !code.isBlank()).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<OpportunityPipelineStagePO> rows = stageMapper.selectList(
                new LambdaQueryWrapper<OpportunityPipelineStagePO>()
                        .eq(OpportunityPipelineStagePO::getTenantId, tenantId.getValue())
                        .in(OpportunityPipelineStagePO::getPipelineCode, codes)
                        .eq(OpportunityPipelineStagePO::getStatus,
                                OpportunityPipelineStatus.ACTIVE.name())
                        .orderByAsc(OpportunityPipelineStagePO::getPipelineCode)
                        .orderByAsc(OpportunityPipelineStagePO::getDisplayOrder)
                        .orderByAsc(OpportunityPipelineStagePO::getCode)
                        .orderByAsc(OpportunityPipelineStagePO::getId));
        LinkedHashMap<String, List<OpportunityPipelineStage>> result = new LinkedHashMap<>();
        for (OpportunityPipelineStagePO row : rows) {
            List<OpportunityPipelineStage> stages = result.computeIfAbsent(
                    row.getPipelineCode(), ignored -> new ArrayList<>());
            // The per-pipeline ceiling still applies; it just no longer needs its own query.
            if (stages.size() < stageLimitPerPipeline) {
                stages.add(converter.toDomain(row));
            }
        }
        return result;
    }

    @Override
    public PageResult<OpportunityPipelineStage> findStages(
            TenantId tenantId, String pipelineCode, OpportunityPipelineQuery query) {
        requireTenant(tenantId);
        if (pipelineCode == null) {
            throw new IllegalArgumentException("pipeline code is required");
        }
        requireQuery(query);
        IPage<OpportunityPipelineStagePO> result = stageMapper.selectPage(
                new Page<>(query.page(), query.size()), stageQuery(tenantId, pipelineCode, query)
                        .orderByAsc(OpportunityPipelineStagePO::getDisplayOrder)
                        .orderByAsc(OpportunityPipelineStagePO::getCode)
                        .orderByAsc(OpportunityPipelineStagePO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0L, result.getTotal()), result.getRecords().stream()
                .map(converter::toDomain).toList());
    }

    @Override
    public long countNonDeletedPipelines(TenantId tenantId) {
        requireTenant(tenantId);
        return pipelineMapper.selectCount(pipelineFilter(tenantId)
                .ne(OpportunityPipelinePO::getStatus, OpportunityPipelineStatus.DELETED.name()));
    }

    @Override
    public long countNonDeletedStages(TenantId tenantId, String pipelineCode) {
        requireTenant(tenantId);
        return stageMapper.selectCount(stageFilter(tenantId, pipelineCode)
                .ne(OpportunityPipelineStagePO::getStatus,
                        OpportunityPipelineStatus.DELETED.name()));
    }

    @Override
    public boolean existsActiveStage(TenantId tenantId, String pipelineCode) {
        requireTenant(tenantId);
        return stageMapper.exists(stageFilter(tenantId, pipelineCode)
                .eq(OpportunityPipelineStagePO::getStatus,
                        OpportunityPipelineStatus.ACTIVE.name()));
    }

    @Override
    public long countActiveStages(TenantId tenantId, String pipelineCode) {
        requireTenant(tenantId);
        return stageMapper.selectCount(stageFilter(tenantId, pipelineCode)
                .eq(OpportunityPipelineStagePO::getStatus,
                        OpportunityPipelineStatus.ACTIVE.name()));
    }

    @Override
    public boolean existsNonDeletedStage(TenantId tenantId, String pipelineCode) {
        requireTenant(tenantId);
        return stageMapper.exists(stageFilter(tenantId, pipelineCode)
                .ne(OpportunityPipelineStagePO::getStatus,
                        OpportunityPipelineStatus.DELETED.name()));
    }

    @Override
    public boolean existsActivePair(TenantId tenantId, String pipelineCode, String stageCode) {
        requireTenant(tenantId);
        if (pipelineCode == null || stageCode == null) {
            return false;
        }
        boolean pipelineActive = pipelineMapper.exists(pipelineFilter(tenantId)
                .eq(OpportunityPipelinePO::getCode, pipelineCode)
                .eq(OpportunityPipelinePO::getStatus, OpportunityPipelineStatus.ACTIVE.name()));
        return pipelineActive && stageMapper.exists(stageFilter(tenantId, pipelineCode)
                .eq(OpportunityPipelineStagePO::getCode, stageCode)
                .eq(OpportunityPipelineStagePO::getStatus,
                        OpportunityPipelineStatus.ACTIVE.name()));
    }

    private LambdaQueryWrapper<OpportunityPipelinePO> pipelineQuery(
            TenantId tenantId, OpportunityPipelineQuery query) {
        LambdaQueryWrapper<OpportunityPipelinePO> result = pipelineFilter(tenantId);
        if (query.status() != null) {
            result.eq(OpportunityPipelinePO::getStatus, query.status().name());
        }
        if (query.keyword() != null) {
            String literal = SqlLikePattern.literal(query.keyword());
            result.and(nested -> nested
                    .apply("code LIKE CONCAT({0}, '%') ESCAPE '!'", literal)
                    .or().apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", literal));
        }
        return result;
    }

    private LambdaQueryWrapper<OpportunityPipelineStagePO> stageQuery(
            TenantId tenantId, String pipelineCode, OpportunityPipelineQuery query) {
        LambdaQueryWrapper<OpportunityPipelineStagePO> result = stageFilter(tenantId, pipelineCode);
        if (query.status() != null) {
            result.eq(OpportunityPipelineStagePO::getStatus, query.status().name());
        }
        if (query.keyword() != null) {
            String literal = SqlLikePattern.literal(query.keyword());
            result.and(nested -> nested
                    .apply("code LIKE CONCAT({0}, '%') ESCAPE '!'", literal)
                    .or().apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", literal));
        }
        return result;
    }

    private LambdaQueryWrapper<OpportunityPipelinePO> pipelineFilter(TenantId tenantId) {
        return new LambdaQueryWrapper<OpportunityPipelinePO>()
                .eq(OpportunityPipelinePO::getTenantId, tenantId.getValue());
    }

    private LambdaQueryWrapper<OpportunityPipelineStagePO> stageFilter(
            TenantId tenantId, String pipelineCode) {
        return new LambdaQueryWrapper<OpportunityPipelineStagePO>()
                .eq(OpportunityPipelineStagePO::getTenantId, tenantId.getValue())
                .eq(OpportunityPipelineStagePO::getPipelineCode, pipelineCode);
    }

    private void requirePipelineMatch(TenantId tenantId, OpportunityPipeline pipeline) {
        requireTenant(tenantId);
        if (pipeline == null || pipeline.getTenantId() == null
                || !tenantId.sameValueAs(pipeline.getTenantId())) {
            throw new IllegalArgumentException("opportunity pipeline tenant does not match");
        }
    }

    private void requireStageMatch(TenantId tenantId, OpportunityPipelineStage stage) {
        requireTenant(tenantId);
        if (stage == null || stage.getTenantId() == null
                || !tenantId.sameValueAs(stage.getTenantId())) {
            throw new IllegalArgumentException("opportunity pipeline stage tenant does not match");
        }
    }

    private void requireQuery(OpportunityPipelineQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("opportunity pipeline query is required");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }
}
