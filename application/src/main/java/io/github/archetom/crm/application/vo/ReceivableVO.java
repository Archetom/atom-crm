package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReceivableVO(
        Long id, Long ownerActorId, Long customerId, Long contractId, Long receivablePlanId,
        String receiptNumber, LocalDate receivedDate, String paymentMethod, BigDecimal amount,
        String remark, String status, String approvalStatus, Map<String, List<String>> customFields, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
