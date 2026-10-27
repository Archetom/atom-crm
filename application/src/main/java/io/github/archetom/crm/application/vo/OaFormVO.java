package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OaFormVO(
        Long id,
        String code,
        String name,
        String description,
        Long approvalDefinitionId,
        String approvalDefinitionName,
        String approvalDefinitionStatus,
        Long createdByActorId,
        String status,
        List<OaFormApprovalSelectionVO> approvalSelections,
        List<OaFormFieldVO> fields,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
