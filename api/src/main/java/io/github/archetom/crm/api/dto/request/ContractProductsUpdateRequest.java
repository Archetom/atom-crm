package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Complete replacement input for a contract's product relation. */
@Data
@Accessors(chain = true)
public class ContractProductsUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @NotNull @Size(max = 100)
    private List<@NotNull @Valid ContractProductLineRequest> lines;
}
