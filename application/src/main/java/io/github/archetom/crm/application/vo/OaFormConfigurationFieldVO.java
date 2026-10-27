package io.github.archetom.crm.application.vo;

/** A field that is active and writable for the current OA-form manager. */
public record OaFormConfigurationFieldVO(
        String fieldCode,
        String displayName,
        String fieldType,
        boolean required) {
}
