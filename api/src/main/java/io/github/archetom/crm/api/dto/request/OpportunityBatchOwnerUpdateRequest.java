package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically changes the owner of bounded, versioned opportunities. */
@Data
@Accessors(chain = true)
public class OpportunityBatchOwnerUpdateRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid OpportunityBatchVersionRequest> opportunities;

    @NotNull
    @Positive
    private Long ownerActorId;
}
