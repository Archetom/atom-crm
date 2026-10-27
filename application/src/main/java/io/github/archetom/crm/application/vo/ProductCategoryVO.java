package io.github.archetom.crm.application.vo;

import java.util.List;

public record ProductCategoryVO(
        Long id,
        Long parentCategoryId,
        String name,
        String status,
        Long version,
        List<ProductCategoryVO> children) {
}
