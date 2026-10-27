package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Bounded active-member query scoped to one visible department, optionally including its subtree. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class DepartmentMemberQueryRequest extends MemberProfileQueryRequest {

    @Schema(description = "Include active descendants of the selected visible department")
    private boolean includeDescendants;
}
