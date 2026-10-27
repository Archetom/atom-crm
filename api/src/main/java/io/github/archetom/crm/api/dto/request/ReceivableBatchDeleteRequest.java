package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically deletes a bounded set of receivables with their observed versions. */
@Data
@Accessors(chain = true)
public class ReceivableBatchDeleteRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid ReceivableBatchVersionRequest> receivables;
}
