package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class FieldDefinitionResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    private String description;
    @Schema(allowableValues = {"TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME",
            "INTEGER", "DECIMAL", "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE", "USER",
            "DEPARTMENT", "RELATION", "ADDRESS"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean builtin;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean required;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean unique;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean searchable;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer displayOrder;
    private String sectionName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FieldOptionResponse> options;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = FilterOperatorOptionResponse.class))
    private List<FilterOperatorOptionResponse> filterOperators;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedTime;
}
