package io.github.archetom.crm.application.vo;

import java.util.List;

/** Server-filtered field catalog for a new form or an existing form's resolved owner. */
public record MarketingFormConfigurationOptionsVO(
        List<MarketingFormConfigurationFieldVO> fields) {
    public MarketingFormConfigurationOptionsVO {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
