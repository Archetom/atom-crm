package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record OpportunityPipelineStageVO(
        Long id, String pipelineCode, String code, String name, Integer displayOrder,
        Integer probability, String status, Long version, LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
