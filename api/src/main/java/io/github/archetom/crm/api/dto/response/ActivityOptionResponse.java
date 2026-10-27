package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ActivityOptionResponse {
    private String type;
    private String value;
    private String label;
    private Integer displayOrder;
}
