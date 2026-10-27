package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ContractVO(
        Long id, Long ownerActorId, Long customerId, Long opportunityId, Long contactId,
        String name, String number, LocalDate orderDate, LocalDate startDate,
        LocalDate endDate, String contractType, String paymentType, String remark,
        BigDecimal subtotal, BigDecimal discountRate, BigDecimal totalAmount,
        String status, List<ContractProductLineVO> products,
        List<ContractCollaboratorVO> collaborators,
        Map<String, List<String>> customFields, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
