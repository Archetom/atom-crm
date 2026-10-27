package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ReturnVisitResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contactId;
    private ReturnVisitRelationStateResponse contactRelationState;
    private Long contractId;
    private ReturnVisitRelationStateResponse contractRelationState;
    private String visitNumber;
    private LocalDateTime visitTime;
    @Schema(nullable = true, allowableValues = {"IN_PERSON", "PHONE", "SMS", "EMAIL", "WECHAT"})
    private String visitForm;
    @Schema(nullable = true, allowableValues = {"VERY_SATISFIED", "SATISFIED", "NEUTRAL", "DISSATISFIED", "VERY_DISSATISFIED"})
    private String satisfaction;
    private String remark;
    private String status;
    @Schema(nullable = true, allowableValues = {"PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED"})
    private String approvalStatus;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
