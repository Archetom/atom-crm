package io.github.archetom.crm.application.vo;

import java.util.List;

/** A lead field the resolved marketing-form owner may safely expose publicly. */
public record MarketingFormConfigurationFieldVO(
        String fieldCode,
        String displayName,
        String fieldType,
        boolean required,
        List<MarketingFormFieldOptionVO> options) {
    public MarketingFormConfigurationFieldVO {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
