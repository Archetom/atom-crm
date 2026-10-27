package io.github.archetom.crm.application.vo;

/** Current failure action semantics, derived from the running workflow. */
public record StageOutcomeOptionVO(String code, String label, boolean remarkRequired,
                                   int remarkMaxLength) {
}
