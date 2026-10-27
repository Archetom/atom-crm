package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalDefinitionResponse(Long id, String objectType, String name,
                                         String description, String status, String repeatMode,
                                         List<Long> initiatorActorIds,
                                         List<Long> initiatorDepartmentIds,
                                         List<Long> managerActorIds,
                                         List<ApprovalStepResponse> steps, Long version,
                                         LocalDateTime createdTime, LocalDateTime updatedTime) {
}
