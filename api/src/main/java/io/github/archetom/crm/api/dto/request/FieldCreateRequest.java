package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class FieldCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String displayName;
    @Size(max = 500)
    private String description;
    @NotBlank
    @Size(max = 30)
    @Schema(allowableValues = {"TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME",
            "INTEGER", "DECIMAL", "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE", "USER",
            "DEPARTMENT", "RELATION", "ADDRESS"})
    private String fieldType;
    private Boolean required = false;
    private Boolean unique = false;
    private Boolean searchable = true;
    @Min(0)
    @Max(10000)
    private Integer displayOrder = 0;
    @Size(max = 100)
    private String sectionName;
    @Size(max = 100)
    private List<@Valid FieldOptionRequest> options = List.of();
}
