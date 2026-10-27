package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StageProgressResponse(String stageCode, String status,
                                    Map<String, List<String>> fieldValues,
                                    Set<String> completedTaskCodes, int approvalAttempt,
                                    LocalDateTime startedTime, LocalDateTime completedTime) {
}
