package io.github.archetom.crm.application.vo;

import java.util.List;

public record OaFormFieldVO(
        String fieldCode,
        String displayName,
        String description,
        String fieldType,
        boolean builtin,
        boolean required,
        boolean searchable,
        String status,
        int sortOrder,
        List<FieldOptionVO> options,
        String accessLevel,
        String maskType) {
}
