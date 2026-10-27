package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Request to rename a tenant-owned department. */
@Data
@Accessors(chain = true)
public class DepartmentRenameRequest {

    @NotBlank(message = "Department name must not be empty")
    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String name;
}
