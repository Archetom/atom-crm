package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** Deployment-supplied region reference item; it is not organization membership data. */
@Data
@Accessors(chain = true)
public class RegionReferenceResponse {
    private String code;
    private String parentCode;
    private String name;
    private String level;
}
