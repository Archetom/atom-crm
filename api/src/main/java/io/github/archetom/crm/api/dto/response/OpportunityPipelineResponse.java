package io.github.archetom.crm.api.dto.response;

import java.time.LocalDateTime;

public record OpportunityPipelineResponse(
        Long id, String code, String name, Integer displayOrder, String status,
        Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
}
