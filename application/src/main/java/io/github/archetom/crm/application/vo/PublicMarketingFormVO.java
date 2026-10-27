package io.github.archetom.crm.application.vo;

import java.util.List;

public record PublicMarketingFormVO(
        String publicId,
        String title,
        String description,
        String submitButtonText,
        String successMessage,
        List<MarketingFormFieldVO> fields
) {
    public PublicMarketingFormVO {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
