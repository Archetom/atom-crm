package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StageProgressVO(
        String stageCode,
        String status,
        Map<String, List<String>> fieldValues,
        Set<String> completedTaskCodes,
        int approvalAttempt,
        LocalDateTime startedTime,
        LocalDateTime completedTime) {
}
