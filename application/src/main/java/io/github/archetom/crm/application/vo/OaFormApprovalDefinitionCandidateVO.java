package io.github.archetom.crm.application.vo;

import java.util.List;

/** A runnable OA approval definition with its non-derived routing facts. */
public record OaFormApprovalDefinitionCandidateVO(
        Long id,
        String name,
        List<String> requiredFieldCodes) {
}
