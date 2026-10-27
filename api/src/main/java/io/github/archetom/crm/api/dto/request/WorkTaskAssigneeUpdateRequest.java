package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkTaskAssigneeUpdateRequest {

    @NotNull
    @Positive
    private Long assigneeActorId;

    @NotNull
    @Min(0)
    private Long version;
}
