package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record MarketingFormVO(
        Long id,
        String publicId,
        String targetObjectType,
        String name,
        String title,
        String description,
        String submitButtonText,
        String successMessage,
        Long ownerActorId,
        String status,
        List<MarketingFormFieldVO> fields,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
    public MarketingFormVO {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
