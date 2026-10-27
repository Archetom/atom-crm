package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Set;

/** Public current-caller navigation preference. */
@Data
@Accessors(chain = true)
public class NavigationPreferenceResponse {
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    private List<String> orderedCodes;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    private Set<String> hiddenCodes;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", nullable = true)
    private Long version;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = NavigationItemResponse.class))
    private List<NavigationItemResponse> availableItems;
}
