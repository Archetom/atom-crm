package io.github.archetom.crm.application.vo;

import java.time.LocalDate;
import java.util.List;

public record MarketingFormSubmissionTrendVO(
        Long formId,
        LocalDate from,
        LocalDate to,
        String granularity,
        List<MarketingFormSubmissionTrendPointVO> points) {

    public record MarketingFormSubmissionTrendPointVO(
            LocalDate periodStart, long acceptedCount) {
    }
}
