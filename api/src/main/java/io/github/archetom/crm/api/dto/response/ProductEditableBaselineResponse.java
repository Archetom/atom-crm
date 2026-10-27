package io.github.archetom.crm.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Authoritative values that a caller may safely submit to replace product details.
 * Business fields are absent when {@code editable} is false.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductEditableBaselineResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean editable;
    private String name;
    private String code;
    private Long categoryId;
    private String unit;
    @Schema(minimum = "0")
    private BigDecimal listPrice;
    private String description;
}
