package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically deletes bounded, versioned leads. */
@Data
@Accessors(chain = true)
public class LeadBatchDeleteRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid LeadBatchVersionRequest> leads;
}
