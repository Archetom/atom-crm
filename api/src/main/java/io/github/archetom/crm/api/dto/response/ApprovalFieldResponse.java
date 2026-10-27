package io.github.archetom.crm.api.dto.response;

public record ApprovalFieldResponse(String fieldCode, String displayName,
                                    String fieldType, boolean required) {
}
