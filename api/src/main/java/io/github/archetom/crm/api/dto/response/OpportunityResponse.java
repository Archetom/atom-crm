package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class OpportunityResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long primaryContactId;
    private String name;
    private String pipelineCode;
    private String stageCode;
    private LocalDate expectedCloseDate;
    private BigDecimal estimatedAmount;
    private LocalDateTime nextContactTime;
    private String description;
    private String outcome;
    private LocalDateTime outcomeTime;
    private String outcomeRemark;
    private String status;
    private List<Long> contactIds;
    private List<OpportunityCollaboratorResponse> collaborators;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Boolean starred;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime lastActivityTime;
}
