package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Bounded CSV export of the explicit contract rows selected from a current list page. */
@Data
@Accessors(chain = true)
public class ContractBatchCsvExportRequest {
    @NotEmpty
    @Size(min = 1, max = 100)
    @ArraySchema(minItems = 1, maxItems = 100,
            schema = @Schema(implementation = ContractBatchVersionRequest.class))
    private List<@Valid ContractBatchVersionRequest> contracts;

    /** The selected-row export deliberately has no caller-controlled fields or filters. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown contract selected CSV export property");
    }
}
