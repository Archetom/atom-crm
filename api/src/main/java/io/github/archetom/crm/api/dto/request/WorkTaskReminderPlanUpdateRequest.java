package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskReminderPlanUpdateRequest {
    @NotNull @Min(0)
    private Long version;
    @NotNull @Size(max = 8)
    private List<@Valid @NotNull WorkTaskReminderStepRequest> steps;
}
