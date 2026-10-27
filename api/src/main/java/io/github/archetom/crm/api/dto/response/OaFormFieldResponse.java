package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OaFormFieldResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME", "INTEGER", "DECIMAL",
            "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE", "USER", "DEPARTMENT", "RELATION", "ADDRESS"})
    private String fieldType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean builtin;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean required;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean searchable;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE", "INACTIVE", "DELETED"})
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer sortOrder;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = FieldOptionResponse.class))
    private List<FieldOptionResponse> options;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessLevel;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String maskType;
}
