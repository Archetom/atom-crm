package io.github.archetom.crm.application.vo;

import lombok.Value;

import java.util.List;

/**
 * Department-workbench member projection with authority-gated account and role fields.
 *
 * <p>This projection is intentionally separate from the reusable member profile so callers of
 * generic organization lookups never receive department-specific account or role data.</p>
 */
@Value
public class DepartmentMemberProfileVO {
    Long actorId;
    String displayName;
    String email;
    String maskedPhoneNumber;
    boolean active;
    String departmentName;
    String loginName;
    List<String> roleNames;
}
