package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class StageWorkflowRequest {
    @Min(0)
    private Long version;
    @NotBlank
    private String objectType;
    @NotBlank
    @Size(max = 200)
    private String name;
    @NotBlank
    @Size(max = 100)
    private String successName;
    @NotBlank
    @Size(max = 100)
    private String failureName;
    @Size(max = 500)
    private List<@NotNull @Positive Long> applicableActorIds;
    @Size(max = 500)
    private List<@NotNull @Positive Long> applicableDepartmentIds;
    @NotEmpty
    @Size(max = 50)
    private List<@NotNull @Valid StageDefinitionRequest> stages;
}
