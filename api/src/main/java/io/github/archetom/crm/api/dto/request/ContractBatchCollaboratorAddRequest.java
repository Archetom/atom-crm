package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Adds or updates the named collaborators on every versioned contract target. */
@Data
@Accessors(chain = true)
public class ContractBatchCollaboratorAddRequest {
    @NotEmpty
    @Size(min = 1, max = 100)
    @ArraySchema(minItems = 1, maxItems = 100,
            schema = @Schema(implementation = ContractBatchVersionRequest.class))
    private List<@Valid ContractBatchVersionRequest> contracts;

    @NotEmpty
    @Size(min = 1, max = 100)
    @ArraySchema(minItems = 1, maxItems = 100,
            schema = @Schema(implementation = ContractCollaboratorRequest.class))
    private List<@Valid ContractCollaboratorRequest> collaborators;
}
