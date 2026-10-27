package io.github.archetom.crm.application.vo;

import java.util.List;

public record StageDefinitionVO(
        String code,
        String name,
        int order,
        int probability,
        List<String> requiredFieldCodes,
        List<StageTaskVO> tasks,
        Long approvalDefinitionId,
        boolean editable,
        String blockerCode) {
}
