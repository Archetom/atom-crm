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
public class ReceivablePlanResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contractId;
    private Integer installment;
    private LocalDate dueDate;
    private BigDecimal plannedAmount;
    private String paymentMethod;
    private Integer reminderDays;
    private String remark;
    private BigDecimal receivedAmount;
    private BigDecimal remainingAmount;
    private String progress;
    private String status;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
