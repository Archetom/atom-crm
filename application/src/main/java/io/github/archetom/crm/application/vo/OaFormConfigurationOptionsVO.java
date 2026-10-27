package io.github.archetom.crm.application.vo;

import java.util.List;

/** Server-filtered configuration choices for OA-form creation and editing. */
public record OaFormConfigurationOptionsVO(
        List<OaFormApprovalDefinitionCandidateVO> approvalDefinitions,
        List<OaFormConfigurationFieldVO> fields) {
}
