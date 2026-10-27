package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ProductCategoryResponse {
    private Long id;
    private Long parentCategoryId;
    private String name;
    private String status;
    private Long version;
    private List<ProductCategoryResponse> children;
}
