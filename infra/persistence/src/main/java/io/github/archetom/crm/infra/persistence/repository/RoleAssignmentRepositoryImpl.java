package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.exception.AuthorizationAlreadyExistsException;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.RoleAssignmentPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.RoleAssignmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleAssignmentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Relational assignment repository that never performs an unscoped lookup. */
@Repository
@RequiredArgsConstructor
public class RoleAssignmentRepositoryImpl implements RoleAssignmentRepository {

    private final RoleAssignmentMapper assignmentMapper;
    private final RoleAssignmentPOConverter assignmentConverter;

    @Override
    public RoleAssignment save(TenantId tenantId, RoleAssignment assignment) {
        requireTenantMatch(tenantId, assignment);
        if (assignment.getId() != null) {
            throw new IllegalArgumentException("role assignments are immutable after creation");
        }
        RoleAssignmentPO po = assignmentConverter.toPO(assignment);
        try {
            if (assignmentMapper.insert(po) != 1) {
                throw new IllegalStateException("Role assignment insert did not affect exactly one row");
            }
        } catch (DuplicateKeyException exception) {
            throw new AuthorizationAlreadyExistsException();
        }
        assignment.onPersisted(po.getId(), po.getCreatedTime());
        return assignment;
    }

    @Override
    public List<RoleAssignment> findByMember(TenantId tenantId, MemberId memberId) {
        if (memberId == null) {
            requireTenant(tenantId);
            return List.of();
        }
        return assignmentMapper.selectList(tenantFilter(tenantId)
                        .eq(RoleAssignmentPO::getActorId, memberId.getValue())
                        .orderByAsc(RoleAssignmentPO::getRoleId))
                .stream().map(assignmentConverter::toDomain).toList();
    }

    @Override
    public List<RoleAssignment> findByMembers(TenantId tenantId, Collection<MemberId> memberIds) {
        requireTenant(tenantId);
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = memberIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(MemberId::getValue)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return assignmentMapper.selectList(tenantFilter(tenantId)
                        .in(RoleAssignmentPO::getActorId, ids)
                        .orderByAsc(RoleAssignmentPO::getActorId, RoleAssignmentPO::getRoleId))
                .stream().map(assignmentConverter::toDomain).toList();
    }

    @Override
    public PageResult<RoleAssignment> findByRole(TenantId tenantId, RoleId roleId, int page, int size) {
        requireRole(roleId);
        IPage<RoleAssignmentPO> result = assignmentMapper.selectPage(new Page<>(page, size),
                tenantFilter(tenantId)
                        .eq(RoleAssignmentPO::getRoleId, roleId.getValue())
                        .orderByAsc(RoleAssignmentPO::getActorId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(result.getTotal(), 0),
                result.getRecords().stream().map(assignmentConverter::toDomain).toList());
    }

    @Override
    public List<MemberId> findActiveMembersByRoles(
            TenantId tenantId, Collection<RoleId> roleIds, int limit) {
        requireTenant(tenantId);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roleIds.stream().map(roleId -> {
            requireRole(roleId);
            return roleId.getValue();
        }).distinct().toList();
        return assignmentMapper.findActiveMemberIdsByRoles(
                        tenantId.getValue(), ids, limit).stream()
                .map(MemberId::new)
                .toList();
    }

    @Override
    public boolean exists(TenantId tenantId, MemberId memberId, RoleId roleId) {
        if (memberId == null || roleId == null) {
            requireTenant(tenantId);
            return false;
        }
        return assignmentMapper.selectCount(tenantFilter(tenantId)
                .eq(RoleAssignmentPO::getActorId, memberId.getValue())
                .eq(RoleAssignmentPO::getRoleId, roleId.getValue())) > 0;
    }

    @Override
    public long countByRole(TenantId tenantId, RoleId roleId) {
        requireRole(roleId);
        return assignmentMapper.selectCount(tenantFilter(tenantId)
                .eq(RoleAssignmentPO::getRoleId, roleId.getValue()));
    }

    @Override
    public long countActiveMembersByRole(TenantId tenantId, RoleId roleId) {
        requireTenant(tenantId);
        requireRole(roleId);
        return assignmentMapper.countActiveMembersByRole(tenantId.getValue(), roleId.getValue());
    }

    @Override
    public boolean delete(TenantId tenantId, MemberId memberId, RoleId roleId) {
        if (memberId == null || roleId == null) {
            requireTenant(tenantId);
            return false;
        }
        return assignmentMapper.delete(tenantFilter(tenantId)
                .eq(RoleAssignmentPO::getActorId, memberId.getValue())
                .eq(RoleAssignmentPO::getRoleId, roleId.getValue())) == 1;
    }

    private LambdaQueryWrapper<RoleAssignmentPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<RoleAssignmentPO>()
                .eq(RoleAssignmentPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, RoleAssignment assignment) {
        requireTenant(tenantId);
        if (assignment == null || assignment.getTenantId() == null
                || !tenantId.sameValueAs(assignment.getTenantId())) {
            throw new IllegalArgumentException("assignment tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }

    private void requireRole(RoleId roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("role ID must not be null");
        }
    }
}
