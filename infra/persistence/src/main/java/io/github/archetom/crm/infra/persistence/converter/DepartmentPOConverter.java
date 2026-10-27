package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.DepartmentPO;
import org.springframework.stereotype.Component;

/** Explicit conversion for tenant-owned departments. */
@Component
public class DepartmentPOConverter {

    public Department toDomain(DepartmentPO po) {
        if (po == null) return null;
        return Department.reconstitute(new Department.Snapshot(
                new DepartmentId(po.getId()), new TenantId(po.getTenantId()), parentId(po.getParentDepartmentId()),
                po.getName(), DepartmentStatus.valueOf(po.getStatus()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    public DepartmentPO toPO(Department department) {
        DepartmentPO po = new DepartmentPO();
        po.setId(department.getId() == null ? null : department.getId().getValue());
        po.setTenantId(department.getTenantId().getValue());
        po.setParentDepartmentId(department.getParentId() == null ? 0L : department.getParentId().getValue());
        po.setName(department.getName());
        po.setStatus(department.getStatus().name());
        po.setVersion(department.getVersion());
        po.setCreatedTime(department.getCreatedTime());
        po.setUpdatedTime(department.getUpdatedTime());
        return po;
    }

    private DepartmentId parentId(Long value) {
        return value == null || value <= 0 ? null : new DepartmentId(value);
    }
}
