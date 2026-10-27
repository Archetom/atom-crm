package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ProductUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @NotBlank @Size(max = 200)
    private String name;
    @NotBlank @Size(max = 100)
    private String code;
    @Positive
    private Long categoryId;
    @Size(max = 50)
    private String unit;
    @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2)
    private BigDecimal listPrice;
    @Size(max = 2000)
    private String description;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
