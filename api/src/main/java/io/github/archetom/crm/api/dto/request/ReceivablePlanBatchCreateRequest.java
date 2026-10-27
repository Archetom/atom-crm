package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReceivablePlanBatchCreateRequest {
    @NotNull @Positive
    private Long contractId;
    @NotEmpty @Size(max = 100)
    private List<@NotNull @Valid ReceivablePlanDraftRequest> plans;
}
