package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically changes the owner of bounded, versioned contacts. */
@Data
@Accessors(chain = true)
public class ContactBatchOwnerUpdateRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid ContactBatchVersionRequest> contacts;

    @NotNull
    @Positive
    private Long ownerActorId;
}
