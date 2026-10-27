package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Bounded role query that never accepts a tenant identifier. */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class RoleQueryRequest extends QueryRequest {

    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status;

    @Size(max = 100)
    private String keyword;

    private Boolean assignableOnly = false;
}
