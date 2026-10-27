package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProductVO(
        Long id,
        Long categoryId,
        String categoryName,
        Long ownerActorId,
        String name,
        String code,
        String unit,
        BigDecimal listPrice,
        String description,
        String availability,
        String status,
        Map<String, List<String>> customFields,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime) {
}
