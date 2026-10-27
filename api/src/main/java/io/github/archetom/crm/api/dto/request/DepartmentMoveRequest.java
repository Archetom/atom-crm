package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

/** Request to move a department; a null parent means the tenant root. */
@Data
@Accessors(chain = true)
public class DepartmentMoveRequest {

    @Positive(message = "Parent department ID must be positive")
    private Long parentDepartmentId;
}
