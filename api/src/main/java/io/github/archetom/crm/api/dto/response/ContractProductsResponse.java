package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Complete, versioned contract product relation snapshot. */
@Data
@Accessors(chain = true)
public class ContractProductsResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long contractId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ContractProductSnapshotResponse> lines;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
}
