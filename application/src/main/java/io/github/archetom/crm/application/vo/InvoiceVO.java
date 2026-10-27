package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InvoiceVO(
        Long id, Long ownerActorId, Long customerId, Long contractId,
        String applicationNumber, BigDecimal amount, LocalDate requestedDate,
        String invoiceType, String titleType, String title, String taxNumber,
        String bankName, String bankAccount, String registeredAddress,
        String registeredPhone, String contactName, String contactPhone,
        String shippingAddress, String remark, String status, String invoiceNumber,
        LocalDate issuedDate, String logisticsNumber,
        Map<String, List<String>> customFields, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
