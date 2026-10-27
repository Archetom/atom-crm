package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OaFormCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String code;
    @NotBlank
    @Size(max = 200)
    private String name;
    @Size(max = 2000)
    private String description;
    @NotNull
    @Positive
    private Long approvalDefinitionId;
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid OaFormFieldRequest> fields;
}
