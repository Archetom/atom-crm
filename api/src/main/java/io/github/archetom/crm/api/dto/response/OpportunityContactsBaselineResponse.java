package io.github.archetom.crm.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Authoritative association values that a caller may safely submit to replace
 * an opportunity's contacts. Relationship values are absent when editable is false.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpportunityContactsBaselineResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean editable;
    private Long customerId;
    private List<OpportunityContactResponse> contacts;
}
