package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MarketingFormSubmissionId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;

/** Immutable, append-only fact for a public form submission that committed its configured record. */
public record MarketingFormSubmission(
        MarketingFormSubmissionId id,
        TenantId tenantId,
        MarketingFormId marketingFormId,
        long formVersion,
        MarketingFormTargetType targetType,
        long targetRecordId,
        String idempotencyKey,
        String submissionDigest,
        LocalDateTime submittedTime) {

    public MarketingFormSubmission {
        if (id == null || tenantId == null || marketingFormId == null || formVersion < 0
                || targetType == null || targetRecordId < 1 || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || submissionDigest == null || !submissionDigest.matches("[0-9a-f]{64}")
                || submittedTime == null) {
            throw new IllegalArgumentException("marketing form submission is invalid");
        }
    }
}
