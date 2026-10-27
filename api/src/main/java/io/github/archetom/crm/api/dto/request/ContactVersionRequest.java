package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContactVersionRequest {
    @NotNull
    @Positive
    private Long contactId;
    @NotNull
    @PositiveOrZero
    private Long version;
}
