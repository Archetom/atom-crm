package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ConnectorResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Globally unique opaque connector reference")
    private String connectorRef;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"ACTIVE", "DISABLED", "RETIRED"})
    private String status;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ConnectorCredentialResponse.class))
    private List<ConnectorCredentialResponse> credentials;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
    @Schema(nullable = true, format = "date-time")
    private LocalDateTime retiredTime;
}
