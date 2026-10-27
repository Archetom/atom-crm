package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LeadConversionResponse {
    private Long leadId;
    private Long customerId;
    private Long contactId;
    private Long leadVersion;
}
