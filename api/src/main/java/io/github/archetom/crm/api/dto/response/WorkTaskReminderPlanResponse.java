package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskReminderPlanResponse {
    private Long taskId;
    private Long seriesId;
    private Long version;
    private Long notificationProjectionVersion;
    private List<WorkTaskReminderStepResponse> steps;
}
