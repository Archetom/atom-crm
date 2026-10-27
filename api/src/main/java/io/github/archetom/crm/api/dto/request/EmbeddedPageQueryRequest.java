package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class EmbeddedPageQueryRequest extends QueryRequest {
    @Size(max = 30)
    private String objectType;
    @Size(max = 20)
    private String platform;
    @Size(max = 20)
    private String status;
}
