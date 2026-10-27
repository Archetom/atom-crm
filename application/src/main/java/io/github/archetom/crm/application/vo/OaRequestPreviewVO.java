package io.github.archetom.crm.application.vo;

import java.util.List;

public record OaRequestPreviewVO(
        Long formId,
        Long formVersion,
        Long definitionId,
        Long definitionVersion,
        boolean autoApproved,
        List<ResolvedApprovalStepVO> steps) {
}
