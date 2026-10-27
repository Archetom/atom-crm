package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportRankingVO(
        String metric,
        LocalDate from,
        LocalDate to,
        List<ReportRankingItemVO> items) {

    public record ReportRankingItemVO(
            int rank, long ownerActorId, long count, BigDecimal amount) {
    }
}
