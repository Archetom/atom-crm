package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public, non-sensitive indication of whether the one-time local installation remains available. */
@Data
@Accessors(chain = true)
public class InitialInstallationStatusResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean installationRequired;
}
