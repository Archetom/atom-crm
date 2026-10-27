package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProductCategoryCreateRequest {
    @Positive
    private Long parentCategoryId;
    @NotBlank @Size(max = 200)
    private String name;
}
