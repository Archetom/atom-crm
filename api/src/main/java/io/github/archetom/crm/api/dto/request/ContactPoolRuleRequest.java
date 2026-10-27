package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContactPoolRuleRequest {
    @NotBlank
    @Size(max = 30)
    private String type;
    @Min(1)
    @Max(3650)
    @NotNull
    private Integer inactiveDays;
}
