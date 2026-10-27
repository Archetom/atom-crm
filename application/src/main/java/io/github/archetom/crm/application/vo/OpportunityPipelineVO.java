package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record OpportunityPipelineVO(
        Long id, String code, String name, Integer displayOrder, String status,
        Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
}
