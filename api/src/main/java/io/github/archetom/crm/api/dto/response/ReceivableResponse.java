package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ReceivableResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contractId;
    private Long receivablePlanId;
    private String receiptNumber;
    private LocalDate receivedDate;
    private String paymentMethod;
    private BigDecimal amount;
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
