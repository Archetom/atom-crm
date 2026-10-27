package io.github.archetom.crm.api.dto.response;

/** A display-only preview step; assignment identities remain server-side. */
public record ApprovalPreviewStepResponse(String code, String name,
                                          boolean selectionRequired) {
}
