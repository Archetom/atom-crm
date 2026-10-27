package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record InvoiceProfileVO(
        Long id, Long customerId, String titleType, String title,
        String taxNumber, String bankName, String bankAccount,
        String registeredAddress, String registeredPhone, String remark,
        Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
}
