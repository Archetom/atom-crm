package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class FilterConditionRequest {
    @NotBlank
    @Size(max = 64)
    private String fieldCode;
    @NotBlank
    @Size(max = 30)
    private String operator;
    @Size(max = 20)
    private List<@Size(max = 500) String> values = List.of();
}
