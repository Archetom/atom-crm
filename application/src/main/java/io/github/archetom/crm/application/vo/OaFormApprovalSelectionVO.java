package io.github.archetom.crm.application.vo;

/** Safe approval-step projection used to collect an OA applicant's selected approvers. */
public record OaFormApprovalSelectionVO(String code, String name, String mode) {
}
