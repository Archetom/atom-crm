package io.github.archetom.crm.application.vo;

import java.util.List;

/** A caller-bound ephemeral preview; its opaque reference is consumed by a later adoption attempt. */
public record EnterpriseDataAdoptionPreviewVO(
        EnterpriseDataObservationVO observation,
        String previewReference,
        List<DiffVO> diffs
) {
    public EnterpriseDataAdoptionPreviewVO {
        if (observation == null || previewReference == null || previewReference.isBlank()) {
            throw new IllegalArgumentException("enterprise data adoption preview is invalid");
        }
        diffs = diffs == null ? List.of() : List.copyOf(diffs);
    }

    @Override public String toString() { return "EnterpriseDataAdoptionPreviewVO[redacted]"; }

    public record DiffVO(String fact, String targetLocator, boolean changed) { }
}
