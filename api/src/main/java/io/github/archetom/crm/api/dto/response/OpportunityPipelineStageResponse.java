package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;

public record OpportunityPipelineStageResponse(
        Long id, String pipelineCode, String code, String name,
        Integer displayOrder, Integer probability, String status, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
