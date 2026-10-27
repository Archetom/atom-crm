package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OpportunityVO(
        Long id,
        Long ownerActorId,
        Long customerId,
        Long primaryContactId,
        String name,
        String pipelineCode,
        String stageCode,
        LocalDate expectedCloseDate,
        BigDecimal estimatedAmount,
        LocalDateTime nextContactTime,
        String description,
        String outcome,
        LocalDateTime outcomeTime,
        String outcomeRemark,
        String status,
        List<Long> contactIds,
        List<OpportunityCollaboratorVO> collaborators,
        Map<String, List<String>> customFields,
        boolean starred,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime lastActivityTime) {
}
