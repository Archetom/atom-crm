package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpportunityContactResponse {
    private Long id;
    private String name;
    private Boolean primary;
}
