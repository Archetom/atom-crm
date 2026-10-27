package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fixed B18b sales dashboard projection. */
public record DashboardOverviewVO(
        LocalDate asOfDate,
        String timeRange,
        LocalDate from,
        LocalDate to,
        String preferredDataScope,
        String granularity,
        List<CardVO> cards,
        List<SalesBriefVO> salesBrief,
        List<DataSummaryVO> dataSummary,
        SalesFunnelVO salesFunnel,
        CustomerContactReminderVO customerContactReminder,
        List<RankingVO> rankings,
        TargetVO target) {

    public record CardVO(
            String metric,
            String status,
            Long count,
            BigDecimal amount,
            List<PointVO> points) {
    }

    public record PointVO(LocalDate periodStart, long count, BigDecimal amount) {
    }

    /** One fixed dashboard brief metric with a server-computed comparable period delta. */
    public record SalesBriefVO(
            String metric,
            String status,
            String unit,
            BigDecimal value,
            BigDecimal previousValue,
            BigDecimal comparisonRate) {
    }

    /** One closed, source-specific total in the dashboard data-summary area. */
    public record DataSummaryVO(String metric, String status, String unit, BigDecimal value) {
    }

    /** A same-snapshot, server-grouped opportunity funnel for the visible sales-pipeline catalog. */
    public record SalesFunnelVO(String status, List<SalesFunnelPipelineVO> pipelines) {
    }

    public record SalesFunnelPipelineVO(
            String code, String name, List<SalesFunnelStageVO> stages) {
    }

    public record SalesFunnelStageVO(
            String code, String name, int probability, long count, BigDecimal amount) {
    }

    /** Cumulative customer contact thresholds, calculated from the latest active follow-up. */
    public record CustomerContactReminderVO(
            String status, List<CustomerContactReminderBucketVO> buckets) {
    }

    public record CustomerContactReminderBucketVO(String bucket, long count) {
    }

    /** One metric's owners, ranked under the same permission-aware dashboard selection. */
    public record RankingVO(String metric, String status, List<RankingItemVO> items) {
    }

    public record RankingItemVO(int rank, long ownerActorId, long count, BigDecimal amount) {
    }

    /** A target never makes unavailable business data appear as a zero. */
    public record TargetVO(
            String metric,
            String status,
            BigDecimal targetAmount,
            BigDecimal actualAmount,
            BigDecimal completionRate) {
    }
}
