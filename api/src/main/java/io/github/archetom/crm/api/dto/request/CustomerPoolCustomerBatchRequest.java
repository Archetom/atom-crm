package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CustomerPoolCustomerBatchRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid CustomerVersionRequest> customers;
}
