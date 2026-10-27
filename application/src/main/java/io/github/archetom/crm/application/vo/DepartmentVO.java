package io.github.archetom.crm.application.vo;

import lombok.Value;

/** Tenant-safe representation of a department tree node. */
@Value
public class DepartmentVO {
    Long id;
    Long parentDepartmentId;
    String name;
    String status;
    boolean active;
}
