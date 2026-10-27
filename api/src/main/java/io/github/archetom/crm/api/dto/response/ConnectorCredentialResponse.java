package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ConnectorCredentialResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"ACTIVE", "REVOKED"})
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(nullable = true, format = "date-time")
    private LocalDateTime revokedTime;
}
