package io.github.archetom.crm.application.vo;

import java.util.List;

public record MarketingFormFieldVO(
        String fieldCode,
        String label,
        String placeholder,
        String helpText,
        boolean required,
        int sortOrder,
        String fieldType,
        List<MarketingFormFieldOptionVO> options
) {
    public MarketingFormFieldVO {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
