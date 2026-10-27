package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** Values safe for a caller to use as a complete product replacement baseline. */
public record ProductEditableBaselineVO(
        Long id,
        Long version,
        boolean editable,
        String name,
        String code,
        Long categoryId,
        String unit,
        BigDecimal listPrice,
        String description) {
}
