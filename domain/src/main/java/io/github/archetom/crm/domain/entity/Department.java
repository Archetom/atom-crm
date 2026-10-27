package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OrganizationDomainException;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Tenant-owned department with an application-validated tree position. */
@Getter
public class Department extends AggregateRoot<DepartmentId> {

    private DepartmentId id;
    private TenantId tenantId;
    private DepartmentId parentId;
    private String name;
    private DepartmentStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Department() {
    }

    public record Snapshot(DepartmentId id, TenantId tenantId, DepartmentId parentId, String name,
                           DepartmentStatus status, Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static Department create(TenantId tenantId, DepartmentId parentId, String name) {
        Department department = new Department();
        department.tenantId = requireTenant(tenantId);
        department.parentId = parentId;
        department.name = requireName(name);
        department.status = DepartmentStatus.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        department.createdTime = now;
        department.updatedTime = now;
        return department;
    }

    public static Department reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OrganizationDomainException("department snapshot must not be empty");
        }
        Department department = new Department();
        department.id = snapshot.id();
        department.tenantId = requireTenant(snapshot.tenantId());
        department.parentId = snapshot.parentId();
        department.name = requireName(snapshot.name());
        department.status = snapshot.status() == null ? DepartmentStatus.INACTIVE : snapshot.status();
        department.restoreVersion(snapshot.version());
        department.createdTime = snapshot.createdTime();
        department.updatedTime = snapshot.updatedTime();
        return department;
    }

    public void rename(String name) {
        ensureMutable();
        this.name = requireName(name);
        updatedTime = LocalDateTime.now();
    }

    public void moveTo(DepartmentId parentId) {
        ensureMutable();
        if (id != null && id.sameValueAs(parentId)) {
            throw new OrganizationDomainException("a department cannot be its own parent");
        }
        this.parentId = parentId;
        updatedTime = LocalDateTime.now();
    }

    public void deactivate() {
        ensureMutable();
        status = DepartmentStatus.INACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public void delete() {
        ensureMutable();
        status = DepartmentStatus.DELETED;
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == DepartmentStatus.ACTIVE;
    }

    public void onPersisted(DepartmentId id, Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null) {
            throw new OrganizationDomainException("persisted department ID must not be empty");
        }
        if (this.id != null && !this.id.sameValueAs(id)) {
            throw new OrganizationDomainException("persisted department ID cannot change");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void ensureMutable() {
        if (status == DepartmentStatus.DELETED) {
            throw new OrganizationDomainException("deleted departments cannot change");
        }
    }

    private static TenantId requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new OrganizationDomainException("department tenant must not be empty");
        }
        return tenantId;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new OrganizationDomainException("department name is invalid");
        }
        return name.trim();
    }
}
