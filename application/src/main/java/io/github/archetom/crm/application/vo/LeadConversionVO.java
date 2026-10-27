package io.github.archetom.crm.application.vo;

/** IDs created by an atomic lead conversion. */
public record LeadConversionVO(Long leadId, Long customerId, Long contactId, Long leadVersion) {
}
