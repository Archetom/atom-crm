package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** Adoption confirmation does not bypass normal B06 Customer read and field-access policies. */
@Data
@Accessors(chain = true)
public class EnterpriseDataAdoptionResponse {
    private Long customerId;
    private Long customerVersion;
    private EnterpriseDataObservationResponse.AttributionResponse attribution;
}
