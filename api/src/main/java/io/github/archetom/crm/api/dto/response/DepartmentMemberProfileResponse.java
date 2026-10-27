package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Safe member row for the department management workbench.
 *
 * <p>Login and role fields are omitted unless the authenticated caller holds the matching
 * server-side authority.</p>
 */
@Data
@Accessors(chain = true)
public class DepartmentMemberProfileResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    private String email;
    private String maskedPhoneNumber;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;
    private String loginName;
    private List<String> roleNames;
}
