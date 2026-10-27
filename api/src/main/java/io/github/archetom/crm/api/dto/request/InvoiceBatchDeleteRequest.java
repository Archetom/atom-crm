package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically deletes a bounded set of invoices with their observed versions. */
@Data
@Accessors(chain = true)
public class InvoiceBatchDeleteRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid InvoiceBatchVersionRequest> invoices;
}
