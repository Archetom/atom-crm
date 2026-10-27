package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** Controlled projection of one lead into a customer and contact. */
@Data
@Accessors(chain = true)
public class LeadConvertRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @Size(max = 200)
    private String customerName;

    @Size(max = 200)
    private String contactName;

    @Size(max = 200)
    private String contactJobTitle;

    @Size(max = 200)
    private Map<@Size(max = 64) String, @Size(max = 20) List<@Size(max = 500) String>>
            customerCustomFields;

    @Size(max = 200)
    private Map<@Size(max = 64) String, @Size(max = 20) List<@Size(max = 500) String>>
            contactCustomFields;
}
