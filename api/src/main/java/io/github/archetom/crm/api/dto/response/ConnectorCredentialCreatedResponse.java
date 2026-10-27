package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Contains a newly issued connector credential exactly once with its safe connector summary. */
@Data
@Accessors(chain = true)
public class ConnectorCredentialCreatedResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Connector summary without a credential value")
    private ConnectorResponse connector;
    @ToString.Exclude
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Returned only by connector creation or credential rotation; it cannot be retrieved again")
    private String credential;
}
