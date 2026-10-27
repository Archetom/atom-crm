package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ContractCollaboratorBaselineResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ContractCollaboratorResponse> collaborators;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ContractCallerCollaboratorStateResponse callerState;
}
