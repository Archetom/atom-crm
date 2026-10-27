package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.AuthorizationAlreadyExistsException;
import io.github.archetom.crm.domain.model.RoleStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.RoleGrantPOConverter;
import io.github.archetom.crm.infra.persistence.converter.TenantRolePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.RoleGrantMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.TenantRoleMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleGrantPO;
import io.github.archetom.crm.infra.persistence.mysql.po.TenantRolePO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Relational role repository with tenant predicates on every operation. */
@Repository
@RequiredArgsConstructor
public class TenantRoleRepositoryImpl implements TenantRoleRepository {

    private final TenantRoleMapper roleMapper;
    private final RoleGrantMapper grantMapper;
    private final TenantRolePOConverter roleConverter;
    private final RoleGrantPOConverter grantConverter;

    @Override
    public TenantRole save(TenantId tenantId, TenantRole role) {
        requireTenantMatch(tenantId, role);
        TenantRolePO po = roleConverter.toPO(role);
        try {
            if (po.getId() == null) {
                if (roleMapper.insert(po) != 1) {
                    throw new IllegalStateException("Tenant role insert did not affect exactly one row");
                }
            } else if (roleMapper.update(po,
                    tenantFilter(tenantId).eq(TenantRolePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            replaceGrants(tenantId, new RoleId(po.getId()), role.getGrants());
        } catch (DuplicateKeyException exception) {
            throw new AuthorizationAlreadyExistsException();
        }
        role.onPersisted(new RoleId(po.getId()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return role;
    }

    @Override
    public Optional<TenantRole> findById(TenantId tenantId, RoleId roleId) {
        if (roleId == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        TenantRolePO po = roleMapper.selectOne(tenantFilter(tenantId)
                .eq(TenantRolePO::getId, roleId.getValue()));
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public Optional<TenantRole> findBySystemKey(TenantId tenantId, SystemRoleKey systemKey) {
        if (systemKey == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        TenantRolePO po = roleMapper.selectOne(tenantFilter(tenantId)
                .eq(TenantRolePO::getSystemKey, systemKey.name()));
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public List<TenantRole> findByIds(TenantId tenantId, Collection<RoleId> roleIds) {
        requireTenant(tenantId);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(RoleId::getValue)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<TenantRolePO> roles = roleMapper.selectList(tenantFilter(tenantId)
                .in(TenantRolePO::getId, ids)
                .orderByAsc(TenantRolePO::getId));
        return toDomains(tenantId, roles);
    }

    @Override
    public PageResult<TenantRole> findPage(
            TenantId tenantId, RoleStatus status, String keyword, int page, int size) {
        LambdaQueryWrapper<TenantRolePO> query = tenantFilter(tenantId);
        if (status != null) {
            query.eq(TenantRolePO::getStatus, status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            query.apply("name LIKE CONCAT('%', {0}, '%') ESCAPE '!'",
                    SqlLikePattern.literal(keyword.trim()));
        }
        IPage<TenantRolePO> result = roleMapper.selectPage(new Page<>(page, size), query
                .orderByAsc(TenantRolePO::getSystemKey)
                .orderByAsc(TenantRolePO::getName)
                .orderByAsc(TenantRolePO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(result.getTotal(), 0),
                toDomains(tenantId, result.getRecords()));
    }

    @Override
    public void delete(TenantId tenantId, TenantRole role) {
        requireTenantMatch(tenantId, role);
        if (role.getId() == null) {
            throw new IllegalArgumentException("persisted role ID must not be null");
        }
        grantMapper.delete(grantFilter(tenantId).eq(RoleGrantPO::getRoleId, role.getId().getValue()));
        if (roleMapper.delete(tenantFilter(tenantId)
                .eq(TenantRolePO::getId, role.getId().getValue())
                .eq(TenantRolePO::getVersion, role.getVersion())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    private void replaceGrants(TenantId tenantId, RoleId roleId, List<RoleGrant> grants) {
        grantMapper.delete(grantFilter(tenantId).eq(RoleGrantPO::getRoleId, roleId.getValue()));
        for (RoleGrant grant : grants) {
            if (grantMapper.insert(grantConverter.toPO(tenantId, roleId, grant)) != 1) {
                throw new IllegalStateException("Role grant insert did not affect exactly one row");
            }
        }
    }

    private TenantRole toDomain(TenantRolePO po) {
        if (po == null) {
            return null;
        }
        TenantId tenantId = new TenantId(po.getTenantId());
        List<RoleGrant> grants = grantMapper.selectList(grantFilter(tenantId)
                        .eq(RoleGrantPO::getRoleId, po.getId())
                        .orderByAsc(RoleGrantPO::getAuthorityCode))
                .stream().map(grantConverter::toDomain).toList();
        return roleConverter.toDomain(po, grants);
    }

    private List<TenantRole> toDomains(TenantId tenantId, List<TenantRolePO> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roles.stream().map(TenantRolePO::getId).toList();
        Map<Long, List<RoleGrant>> grantsByRole = new LinkedHashMap<>();
        for (RoleGrantPO grant : grantMapper.selectList(grantFilter(tenantId)
                .in(RoleGrantPO::getRoleId, roleIds)
                .orderByAsc(RoleGrantPO::getRoleId, RoleGrantPO::getAuthorityCode))) {
            grantsByRole.computeIfAbsent(grant.getRoleId(), ignored -> new java.util.ArrayList<>())
                    .add(grantConverter.toDomain(grant));
        }
        return roles.stream()
                .map(role -> roleConverter.toDomain(role, grantsByRole.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    private LambdaQueryWrapper<TenantRolePO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<TenantRolePO>().eq(TenantRolePO::getTenantId, tenantId.getValue());
    }

    private LambdaQueryWrapper<RoleGrantPO> grantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<RoleGrantPO>().eq(RoleGrantPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, TenantRole role) {
        requireTenant(tenantId);
        if (role == null || role.getTenantId() == null || !tenantId.sameValueAs(role.getTenantId())) {
            throw new IllegalArgumentException("role tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
