package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.WorkTaskComment;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.WorkTaskCommentRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskCommentId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.converter.WorkTaskCommentPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskCommentMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskCommentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorkTaskCommentRepositoryImpl implements WorkTaskCommentRepository {

    private final WorkTaskCommentMapper mapper;
    private final WorkTaskCommentPOConverter converter;

    @Override
    public WorkTaskComment save(TenantId tenantId, WorkTaskComment comment) {
        requireTenantMatch(tenantId, comment);
        WorkTaskCommentPO po = converter.toPO(comment);
        if (po.getId() == null) {
            if (mapper.insert(po) != 1) {
                throw new IllegalStateException(
                        "Work task comment insert did not affect exactly one row");
            }
        } else if (mapper.update(po, taskFilter(tenantId, comment.getWorkTaskId())
                .eq(WorkTaskCommentPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        comment.onPersisted(new WorkTaskCommentId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return comment;
    }

    @Override
    public Optional<WorkTaskComment> findByIdForUpdate(
            TenantId tenantId, WorkTaskId taskId, WorkTaskCommentId commentId) {
        requireTask(tenantId, taskId);
        if (commentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(
                taskFilter(tenantId, taskId)
                        .eq(WorkTaskCommentPO::getId, commentId.getValue())
                        .eq(WorkTaskCommentPO::getStatus, "ACTIVE")
                        .last("FOR UPDATE"))));
    }

    @Override
    public PageResult<WorkTaskComment> findPage(
            TenantId tenantId, WorkTaskId taskId, int page, int size) {
        requireTask(tenantId, taskId);
        if (page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("work task comment page is invalid");
        }
        IPage<WorkTaskCommentPO> result = mapper.selectPage(new Page<>(page, size),
                taskFilter(tenantId, taskId)
                        .eq(WorkTaskCommentPO::getStatus, "ACTIVE")
                        .orderByAsc(WorkTaskCommentPO::getCreatedTime,
                                WorkTaskCommentPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()), result.getRecords().stream()
                .map(converter::toDomain).toList());
    }

    @Override
    public boolean existsAny(TenantId tenantId, WorkTaskId taskId) {
        requireTask(tenantId, taskId);
        return mapper.selectCount(taskFilter(tenantId, taskId)) > 0;
    }

    private LambdaQueryWrapper<WorkTaskCommentPO> taskFilter(
            TenantId tenantId, WorkTaskId taskId) {
        requireTask(tenantId, taskId);
        return new LambdaQueryWrapper<WorkTaskCommentPO>()
                .eq(WorkTaskCommentPO::getTenantId, tenantId.getValue())
                .eq(WorkTaskCommentPO::getWorkTaskId, taskId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, WorkTaskComment comment) {
        if (comment == null) {
            throw new IllegalArgumentException("work task comment is required");
        }
        requireTask(tenantId, comment.getWorkTaskId());
        if (comment.getTenantId() == null
                || !tenantId.sameValueAs(comment.getTenantId())) {
            throw new IllegalArgumentException(
                    "work task comment tenant does not match repository tenant");
        }
    }

    private void requireTask(TenantId tenantId, WorkTaskId taskId) {
        if (tenantId == null || taskId == null) {
            throw new IllegalArgumentException(
                    "tenant and work task ID are required");
        }
    }
}
