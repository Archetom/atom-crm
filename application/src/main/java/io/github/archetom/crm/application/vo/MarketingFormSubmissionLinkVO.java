package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record MarketingFormSubmissionLinkVO(
        Long submissionId,
        String targetObjectType,
        Long targetRecordId,
        Long formVersion,
        LocalDateTime submittedTime) {
}
