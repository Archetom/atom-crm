package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReportCatalogResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    private List<String> supportedObjectTypes;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportCatalogFieldResponse.class))
    private List<ReportCatalogFieldResponse> timeFields;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportCatalogFieldResponse.class))
    private List<ReportCatalogFieldResponse> dimensionFields;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportCatalogFieldResponse.class))
    private List<ReportCatalogFieldResponse> sumFields;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportCatalogFieldResponse.class))
    private List<ReportCatalogFieldResponse> filterFields;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
    private List<String> measureTypes;

    @Data
    @Accessors(chain = true)
    public static class ReportCatalogFieldResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String fieldCode;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String displayName;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String fieldType;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        private List<String> granularities;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        private List<String> operators;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = FieldOptionResponse.class))
        private List<FieldOptionResponse> options;
    }
}
