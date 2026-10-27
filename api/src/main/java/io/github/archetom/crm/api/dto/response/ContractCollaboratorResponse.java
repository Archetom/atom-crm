package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class ContractCollaboratorResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"READ", "WRITE"})
    private String access;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
            description = "Inclusive local date after which collaboration no longer grants access")
    private LocalDate effectiveUntil;
}
