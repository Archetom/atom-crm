package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class StageDefinitionRequest {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotNull
    @Min(0)
    @Max(10000)
    private Integer order;
    @NotNull
    @Min(0)
    @Max(100)
    private Integer probability;
    @Size(max = 100)
    private List<@NotBlank @Size(max = 64) String> requiredFieldCodes;
    @Size(max = 100)
    private List<@NotNull @Valid StageTaskRequest> tasks;
    @Positive
    private Long approvalDefinitionId;
}
