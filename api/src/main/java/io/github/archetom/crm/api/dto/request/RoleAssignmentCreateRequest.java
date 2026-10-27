package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Bounded explicit member assignment input. */
@Data
@Accessors(chain = true)
public class RoleAssignmentCreateRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> actorIds;
}
