package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

/** One explicit, versioned contact target for an atomic bulk command. */
@Data
@Accessors(chain = true)
public class ContactBatchVersionRequest {
    @NotNull
    @Positive
    private Long contactId;

    @NotNull
    @PositiveOrZero
    private Long version;
}
