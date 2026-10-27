package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkTaskBusinessRecordUpdateRequest {

    /** Both relation properties may be absent to clear the current relation. */
    @Size(max = 30)
    private String businessObjectType;

    @Positive
    private Long businessRecordId;

    @NotNull
    @Min(0)
    private Long version;
}
