package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically resets the issue data of a bounded set of issued invoices. */
@Data
@Accessors(chain = true)
public class InvoiceBatchResetRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid InvoiceBatchVersionRequest> invoices;
}
