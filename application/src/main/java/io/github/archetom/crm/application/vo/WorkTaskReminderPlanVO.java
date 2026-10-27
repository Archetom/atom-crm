package io.github.archetom.crm.application.vo;

import java.util.List;

public record WorkTaskReminderPlanVO(
        Long taskId, Long seriesId, Long version, Long notificationProjectionVersion,
        List<WorkTaskReminderStepVO> steps) {
}
