package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OaRequestQueryRequest {
    @Positive
    private Long formId;
    private String status;
    @Min(1)
    private Integer page = 1;
    @Min(1)
    @Max(200)
    private Integer size = 20;
}
