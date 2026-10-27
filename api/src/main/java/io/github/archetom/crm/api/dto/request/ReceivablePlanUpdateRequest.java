package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceivablePlanUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    private LocalDate dueDate;
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 20, fraction = 2)
    private BigDecimal plannedAmount;
    @Size(max = 50)
    private String paymentMethod;
    @PositiveOrZero
    private Integer reminderDays;
    @Size(max = 2000)
    private String remark;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
