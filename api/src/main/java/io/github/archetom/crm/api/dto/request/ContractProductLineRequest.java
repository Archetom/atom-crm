package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ContractProductLineRequest {
    @NotNull @Positive
    private Long productId;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4)
    private BigDecimal quantity;
    @DecimalMin("0") @Digits(integer = 16, fraction = 2)
    private BigDecimal unitPrice;
}
