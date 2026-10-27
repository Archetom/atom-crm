package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fixed, permission-aware sales dashboard overview. */
@Data
@Accessors(chain = true)
public class DashboardOverviewResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate asOfDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "TODAY", "CURRENT_WEEK", "CURRENT_MONTH", "CURRENT_QUARTER", "CURRENT_YEAR", "CUSTOM"})
    private String timeRange;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate from;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate to;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "SELF", "DEPARTMENT", "DEPARTMENT_SUBTREE", "TENANT"})
    private String preferredDataScope;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DAY", "WEEK", "MONTH"})
    private String granularity;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = CardResponse.class))
    private List<CardResponse> cards;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = SalesBriefResponse.class))
    private List<SalesBriefResponse> salesBrief;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = DataSummaryResponse.class))
    private List<DataSummaryResponse> dataSummary;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private SalesFunnelResponse salesFunnel;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private CustomerContactReminderResponse customerContactReminder;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = RankingResponse.class))
    private List<RankingResponse> rankings;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private TargetResponse target;

    @Data
    @Accessors(chain = true)
    public static class CardResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"})
        private String metric;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AVAILABLE", "UNAVAILABLE"})
        private String status;
        private Long count;
        private BigDecimal amount;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = PointResponse.class))
        private List<PointResponse> points;
    }

    @Data
    @Accessors(chain = true)
    public static class PointResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        private LocalDate periodStart;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;
    }

    /** A source-specific total for the selected period and its preceding comparable period. */
    @Data
    @Accessors(chain = true)
    public static class SalesBriefResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "NEW_CUSTOMER", "NEW_CONTACT", "NEW_OPPORTUNITY", "NEW_CONTRACT",
                "CONTRACT_AMOUNT", "OPPORTUNITY_AMOUNT", "RECEIVABLE_AMOUNT", "NEW_LEAD",
                "NEW_FOLLOW_UP"})
        private String metric;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AVAILABLE", "UNAVAILABLE"})
        private String status;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"COUNT", "AMOUNT"})
        private String unit;
        private BigDecimal value;
        private BigDecimal previousValue;
        private BigDecimal comparisonRate;
    }

    /** Fixed summary values; unavailable sources never use zero as a stand-in. */
    @Data
    @Accessors(chain = true)
    public static class DataSummaryResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "NEW_CUSTOMER", "CONVERTED_CUSTOMER", "CUSTOMER_RELEASED_TO_POOL",
                "CUSTOMER_CLAIMED_FROM_POOL", "NEW_OPPORTUNITY", "WON_OPPORTUNITY",
                "LOST_OPPORTUNITY", "OPPORTUNITY_AMOUNT", "SIGNED_CONTRACT",
                "EXPIRING_CONTRACT", "EXPIRED_CONTRACT", "CONTRACT_AMOUNT",
                "FOLLOWED_CUSTOMER", "NEW_UNFOLLOWED_CUSTOMER", "RECEIVABLE_AMOUNT",
                "EXPECTED_RECEIVABLE"})
        private String metric;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "AVAILABLE", "UNAVAILABLE"})
        private String status;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"COUNT", "AMOUNT"})
        private String unit;
        private BigDecimal value;
    }

    @Data
    @Accessors(chain = true)
    public static class RankingResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"})
        private String metric;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AVAILABLE", "UNAVAILABLE"})
        private String status;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = RankingItemResponse.class))
        private List<RankingItemResponse> items;
    }

    /** Fixed same-snapshot opportunity funnel scoped by the selected dashboard preference. */
    @Data
    @Accessors(chain = true)
    public static class SalesFunnelResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "AVAILABLE", "UNAVAILABLE", "EMPTY"})
        private String status;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = SalesFunnelPipelineResponse.class))
        private List<SalesFunnelPipelineResponse> pipelines;
    }

    @Data
    @Accessors(chain = true)
    public static class SalesFunnelPipelineResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String code;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = SalesFunnelStageResponse.class))
        private List<SalesFunnelStageResponse> stages;
    }

    @Data
    @Accessors(chain = true)
    public static class SalesFunnelStageResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String code;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "100")
        private Integer probability;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;
    }

    /** Cumulative customer contact thresholds for the permission-filtered dashboard scope. */
    @Data
    @Accessors(chain = true)
    public static class CustomerContactReminderResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "AVAILABLE", "UNAVAILABLE"})
        private String status;
        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(implementation = CustomerContactReminderBucketResponse.class))
        private List<CustomerContactReminderBucketResponse> buckets;
    }

    @Data
    @Accessors(chain = true)
    public static class CustomerContactReminderBucketResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "OVER_7_DAYS", "OVER_15_DAYS", "OVER_30_DAYS", "OVER_3_MONTHS",
                "OVER_6_MONTHS", "OVERDUE"})
        private String bucket;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
    }

    @Data
    @Accessors(chain = true)
    public static class RankingItemResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        private Integer rank;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        private Long ownerActorId;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;
    }

    /** Contract amount target completion for the same dashboard time selection. */
    @Data
    @Accessors(chain = true)
    public static class TargetResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE_CONTRACT"})
        private String metric;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "AVAILABLE", "UNAVAILABLE", "EMPTY"})
        private String status;
        private BigDecimal targetAmount;
        private BigDecimal actualAmount;
        @Schema(minimum = "0")
        private BigDecimal completionRate;
    }
}
