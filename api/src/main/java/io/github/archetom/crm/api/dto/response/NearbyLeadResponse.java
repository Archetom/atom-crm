package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NearbyLeadResponse {
    private LeadResponse lead;
    private Long distanceMeters;
}
