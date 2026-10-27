package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Contains the intake credential exactly once, together with its non-secret client summary. */
@Data
@Accessors(chain = true)
public class LeadIntakeClientCreatedResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The persisted client summary, which never includes the credential")
    private LeadIntakeClientResponse client;
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Returned only by creation; it cannot be retrieved again")
    private String apiKey;
}
