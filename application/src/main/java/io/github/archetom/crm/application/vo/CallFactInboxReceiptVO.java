package io.github.archetom.crm.application.vo;

/** Value-free adapter acknowledgement; it intentionally exposes no connector or delivery identity. */
public record CallFactInboxReceiptVO(String outcome) { }
