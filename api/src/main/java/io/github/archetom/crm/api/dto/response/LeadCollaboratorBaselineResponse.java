package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Complete, versioned collaborator state for an authorized replacement operation.
 */
@Data
@Accessors(chain = true)
public class LeadCollaboratorBaselineResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<LeadCollaboratorResponse> collaborators;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LeadCallerCollaboratorStateResponse callerState;
}
