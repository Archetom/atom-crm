package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class FieldUpdateRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Size(max = 100)
    private String displayName;
    @Size(max = 500)
    private String description;
    private Boolean required = false;
    private Boolean unique;
    private Boolean searchable = true;
    @Min(0)
    @Max(10000)
    private Integer displayOrder = 0;
    @Size(max = 100)
    private String sectionName;
    @Size(max = 100)
    private List<@Valid FieldOptionRequest> options = List.of();
}
