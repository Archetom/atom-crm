package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalPreviewVO(
        Long definitionId,
        Long definitionVersion,
        Long subjectId,
        boolean executable,
        String blockerCode,
        List<ApprovalPreviewStepVO> steps) {
}
