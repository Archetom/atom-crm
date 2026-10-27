package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for the department hierarchy. */
public interface DepartmentRepository {

    Department save(TenantId tenantId, Department department);

    Optional<Department> findById(TenantId tenantId, DepartmentId departmentId);

    List<Department> findAll(TenantId tenantId);

    List<Department> findByIds(TenantId tenantId, List<DepartmentId> departmentIds);

    /** Returns every descendant below {@code departmentId}, excluding the department itself. */
    List<Department> findDescendants(TenantId tenantId, DepartmentId departmentId);

    boolean existsActiveChild(TenantId tenantId, DepartmentId departmentId);

    boolean existsChild(TenantId tenantId, DepartmentId departmentId);
}
