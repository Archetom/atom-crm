package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomic replacement of a custom role's complete grant set. */
@Data
@Accessors(chain = true)
public class RoleGrantReplaceRequest {

    @NotNull
    @Min(0)
    private Long version;

    @NotNull
    @Size(max = 200)
    private List<@Valid RoleGrantRequest> grants = List.of();
}
