package io.github.archetom.crm.application.vo;

/** Safe adoption confirmation; current Customer data remains subject to ordinary B06 reads. */
public record EnterpriseDataAdoptionVO(
        Long customerId,
        Long customerVersion,
        EnterpriseDataObservationVO.AttributionVO attribution
) { }
