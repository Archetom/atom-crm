package io.github.archetom.crm.application.vo;

import io.github.archetom.crm.api.dto.response.ReturnVisitRelationStateResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReturnVisitVO(Long id, Long ownerActorId, Long customerId, Long contactId,
                            ReturnVisitRelationStateResponse contactRelationState, Long contractId,
                            ReturnVisitRelationStateResponse contractRelationState, String visitNumber, LocalDateTime visitTime,
                            String visitForm, String satisfaction, String remark, String status, String approvalStatus,
                            Map<String, List<String>> customFields,
                            Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
}
