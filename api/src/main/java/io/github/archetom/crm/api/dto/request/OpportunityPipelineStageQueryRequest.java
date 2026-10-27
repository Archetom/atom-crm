package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Bounded query input for one immutable pipeline's tenant stage catalog. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class OpportunityPipelineStageQueryRequest extends QueryRequest {
    @TrimmedCodePointSize(max = 200)
    private String keyword;
    @Size(max = 20)
    private String status;
}
