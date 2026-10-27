package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.StageWorkflowComment;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.StageWorkflowCommentRepository;
import io.github.archetom.crm.domain.valueobject.StageWorkflowCommentId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.StageWorkflowCommentPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowCommentMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowCommentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StageWorkflowCommentRepositoryImpl implements StageWorkflowCommentRepository {

    private final StageWorkflowCommentMapper mapper;
    private final StageWorkflowCommentPOConverter converter;

    @Override
    public StageWorkflowComment save(TenantId tenantId, StageWorkflowComment comment) {
        requireTenantMatch(tenantId, comment);
        StageWorkflowCommentPO po = converter.toPO(comment);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) {
                throw new IllegalStateException("Stage comment insert did not affect one row");
            }
        } else if (mapper.update(po, recordFilter(tenantId, comment.getWorkflowRecordId())
                .eq(StageWorkflowCommentPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        comment.onPersisted(new StageWorkflowCommentId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return comment;
    }

    @Override
    public Optional<StageWorkflowComment> findById(
            TenantId tenantId, StageWorkflowRecordId recordId,
            StageWorkflowCommentId commentId) {
        if (commentId == null) {
            requireRecord(tenantId, recordId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(
                recordFilter(tenantId, recordId)
                        .eq(StageWorkflowCommentPO::getId, commentId.getValue()))));
    }

    @Override
    public PageResult<StageWorkflowComment> findPage(
            TenantId tenantId, StageWorkflowRecordId recordId, int page, int size) {
        requireRecord(tenantId, recordId);
        if (page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("stage comment page is invalid");
        }
        IPage<StageWorkflowCommentPO> result = mapper.selectPage(new Page<>(page, size),
                recordFilter(tenantId, recordId)
                        .eq(StageWorkflowCommentPO::getStatus, "ACTIVE")
                        .orderByAsc(StageWorkflowCommentPO::getCreatedTime,
                                StageWorkflowCommentPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                result.getRecords().stream().map(converter::toDomain).toList());
    }

    private LambdaQueryWrapper<StageWorkflowCommentPO> recordFilter(
            TenantId tenantId, StageWorkflowRecordId recordId) {
        requireRecord(tenantId, recordId);
        return new LambdaQueryWrapper<StageWorkflowCommentPO>()
                .eq(StageWorkflowCommentPO::getTenantId, tenantId.getValue())
                .eq(StageWorkflowCommentPO::getWorkflowRecordId, recordId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, StageWorkflowComment comment) {
        if (comment == null) {
            throw new IllegalArgumentException("stage comment is required");
        }
        requireRecord(tenantId, comment.getWorkflowRecordId());
        if (comment.getTenantId() == null || !tenantId.sameValueAs(comment.getTenantId())) {
            throw new IllegalArgumentException("stage comment tenant does not match");
        }
    }

    private void requireRecord(TenantId tenantId, StageWorkflowRecordId recordId) {
        if (tenantId == null || recordId == null) {
            throw new IllegalArgumentException("tenant and stage record are required");
        }
    }
}
