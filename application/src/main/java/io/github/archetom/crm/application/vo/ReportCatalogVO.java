package io.github.archetom.crm.application.vo;

import java.util.List;

public record ReportCatalogVO(
        String objectType, List<String> supportedObjectTypes,
        List<ReportCatalogFieldVO> timeFields,
        List<ReportCatalogFieldVO> dimensionFields,
        List<ReportCatalogFieldVO> sumFields,
        List<ReportCatalogFieldVO> filterFields,
        List<String> measureTypes) {

    public record ReportCatalogFieldVO(
            String fieldCode, String displayName, String fieldType,
            List<String> granularities, List<String> operators,
            List<FieldOptionVO> options) { }
}
