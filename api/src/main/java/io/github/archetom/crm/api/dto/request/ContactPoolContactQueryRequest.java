package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ContactPoolContactQueryRequest extends QueryRequest {
    @Size(max = 200)
    private String name;
    @Positive
    private Long savedViewId;
}
