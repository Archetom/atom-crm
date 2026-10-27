package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContactCollaboratorResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long actorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"READ", "WRITE"})
    private String access;
}
