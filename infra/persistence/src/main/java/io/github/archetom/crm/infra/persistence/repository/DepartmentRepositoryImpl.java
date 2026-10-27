package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OrganizationAlreadyExistsException;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.DepartmentPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.DepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.DepartmentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Every department query is constrained by its explicit tenant. */
@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

    private final DepartmentMapper departmentMapper;
    private final DepartmentPOConverter departmentPOConverter;

    @Override
    public Department save(TenantId tenantId, Department department) {
        requireTenantMatch(tenantId, department);
        DepartmentPO po = departmentPOConverter.toPO(department);
        try {
            if (po.getId() == null) {
                if (departmentMapper.insert(po) != 1) {
                    throw new IllegalStateException("Department insert did not affect exactly one row");
                }
            } else if (departmentMapper.update(po, tenantFilter(tenantId).eq(DepartmentPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new OrganizationAlreadyExistsException();
        }
        department.onPersisted(new DepartmentId(po.getId()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return department;
    }

    @Override
    public Optional<Department> findById(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return Optional.empty();
        return Optional.ofNullable(departmentPOConverter.toDomain(
                departmentMapper.selectOne(tenantFilter(tenantId).eq(DepartmentPO::getId, departmentId.getValue()))));
    }

    @Override
    public List<Department> findAll(TenantId tenantId) {
        return departmentMapper.selectList(tenantFilter(tenantId)
                        .orderByAsc(DepartmentPO::getParentDepartmentId, DepartmentPO::getId))
                .stream().map(departmentPOConverter::toDomain).toList();
    }

    @Override
    public List<Department> findByIds(TenantId tenantId, List<DepartmentId> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) return List.of();
        List<Long> ids = departmentIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(DepartmentId::getValue)
                .distinct()
                .toList();
        if (ids.isEmpty()) return List.of();
        return departmentMapper.selectList(tenantFilter(tenantId).in(DepartmentPO::getId, ids))
                .stream().map(departmentPOConverter::toDomain).toList();
    }

    @Override
    public List<Department> findDescendants(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return List.of();
        Map<Long, List<Department>> childrenByParent = new HashMap<>();
        for (Department department : findAll(tenantId)) {
            if (department.getParentId() != null) {
                childrenByParent.computeIfAbsent(department.getParentId().getValue(), ignored -> new java.util.ArrayList<>())
                        .add(department);
            }
        }
        List<Department> descendants = new java.util.ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(departmentId.getValue());
        visited.add(departmentId.getValue());
        while (!pending.isEmpty()) {
            for (Department child : childrenByParent.getOrDefault(pending.removeFirst(), List.of())) {
                if (visited.add(child.getId().getValue())) {
                    descendants.add(child);
                    pending.addLast(child.getId().getValue());
                }
            }
        }
        return descendants;
    }

    @Override
    public boolean existsActiveChild(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return false;
        return departmentMapper.selectCount(tenantFilter(tenantId)
                .eq(DepartmentPO::getParentDepartmentId, departmentId.getValue())
                .eq(DepartmentPO::getStatus, "ACTIVE")) > 0;
    }

    @Override
    public boolean existsChild(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return false;
        return departmentMapper.selectCount(tenantFilter(tenantId)
                .eq(DepartmentPO::getParentDepartmentId, departmentId.getValue())) > 0;
    }

    private LambdaQueryWrapper<DepartmentPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<DepartmentPO>().eq(DepartmentPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, Department department) {
        requireTenant(tenantId);
        if (department == null || department.getTenantId() == null
                || !tenantId.sameValueAs(department.getTenantId())) {
            throw new IllegalArgumentException("department tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
    }
}
