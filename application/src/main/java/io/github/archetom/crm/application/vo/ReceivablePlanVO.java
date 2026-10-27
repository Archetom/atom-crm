package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReceivablePlanVO(
        Long id, Long ownerActorId, Long customerId, Long contractId, Integer installment,
        LocalDate dueDate, BigDecimal plannedAmount, String paymentMethod, Integer reminderDays,
        String remark, BigDecimal receivedAmount, BigDecimal remainingAmount, String progress,
        String status, Map<String, List<String>> customFields, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
