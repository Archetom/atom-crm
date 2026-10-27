package io.github.archetom.crm.application.vo;

/** Public-safe acknowledgement that a draft was withdrawn or had already expired. */
public record PublicMarketingFileDraftWithdrawalVO(Long draftId, boolean withdrawn) {
}
