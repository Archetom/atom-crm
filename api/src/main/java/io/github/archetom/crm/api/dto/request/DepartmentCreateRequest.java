package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Request to create a department under an optional in-tenant parent. */
@Data
@Accessors(chain = true)
public class DepartmentCreateRequest {

    @Positive(message = "Parent department ID must be positive")
    private Long parentDepartmentId;

    @NotBlank(message = "Department name must not be empty")
    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String name;
}
