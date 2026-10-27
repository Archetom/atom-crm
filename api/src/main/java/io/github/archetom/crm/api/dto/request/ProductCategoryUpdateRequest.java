package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProductCategoryUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @Positive
    private Long parentCategoryId;
    @NotBlank @Size(max = 200)
    private String name;
}
