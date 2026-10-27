package io.github.archetom.crm.application.vo;

/** One address property, kept named so a client need not infer the address form. */
public record StageAddressPartVO(String code, String displayName, boolean required, int maxLength) {
}
