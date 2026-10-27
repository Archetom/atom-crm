package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.vo.DashboardOverviewVO;
import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardSalesFunnelValue;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityPipelineQuery;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a configured sales funnel without allowing dashboard preferences to broaden visibility. */
@Component
@RequiredArgsConstructor
class DashboardSalesFunnelQuerySupport {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String EMPTY = "EMPTY";
    private static final int MAXIMUM_PIPELINES = 100;
    private static final int MAXIMUM_STAGES = 100;

    private final OrganizationAccessGuard organizationAccessGuard;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final DashboardVisibilityNarrower visibilityNarrower;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OpportunityPipelineRepository pipelineRepository;
    private final OpportunityRepository opportunityRepository;

    DashboardOverviewVO.SalesFunnelVO summarize(
            AuthenticatedCaller caller, TenantId tenantId,
            DashboardDataScope preferredScope, ReportDateRange range) {
        try {
            TenantId catalogTenant = organizationAccessGuard.requireActiveMembership(
                    caller, AuthorityCatalog.OPPORTUNITY_PIPELINES_READ);
            requireSameTenant(tenantId, catalogTenant);
            OpportunityAccessResolver.OpportunityAccess source = opportunityAccessResolver.access(
                    caller, AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
            requireSameTenant(tenantId, source.tenantId());
            OpportunityAccessResolver.OpportunityAccess scoped = visibilityNarrower.narrow(
                    source, preferredScope);
            requireFields(tenantId, scoped);
            List<OpportunityPipeline> pipelines = pipelineRepository.findPipelines(tenantId,
                    new OpportunityPipelineQuery(null, OpportunityPipelineStatus.ACTIVE,
                            1, MAXIMUM_PIPELINES)).items();
            if (pipelines.isEmpty()) {
                return empty();
            }
            Map<StageKey, DashboardSalesFunnelValue> totals = totals(
                    opportunityRepository.summarizeSalesFunnel(
                            tenantId, scoped.visibility(), range));
            // One stage query for the whole view rather than one per pipeline.
            Map<String, List<OpportunityPipelineStage>> stagesByPipeline =
                    pipelineRepository.findActiveStages(tenantId,
                            pipelines.stream().map(OpportunityPipeline::getCode).toList(),
                            MAXIMUM_STAGES);
            List<DashboardOverviewVO.SalesFunnelPipelineVO> values = pipelines.stream()
                    .map(pipeline -> pipeline(pipeline,
                            stagesByPipeline.getOrDefault(pipeline.getCode(), List.of()), totals))
                    .filter(value -> !value.stages().isEmpty())
                    .toList();
            return values.isEmpty()
                    ? empty()
                    : new DashboardOverviewVO.SalesFunnelVO(AVAILABLE, values);
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return unavailable();
        }
    }

    private DashboardOverviewVO.SalesFunnelPipelineVO pipeline(
            OpportunityPipeline pipeline, List<OpportunityPipelineStage> pipelineStages,
            Map<StageKey, DashboardSalesFunnelValue> totals) {
        List<DashboardOverviewVO.SalesFunnelStageVO> stages = pipelineStages.stream()
                .map(stage -> stage(pipeline, stage, totals)).toList();
        return new DashboardOverviewVO.SalesFunnelPipelineVO(
                pipeline.getCode(), pipeline.getName(), stages);
    }

    private DashboardOverviewVO.SalesFunnelStageVO stage(
            OpportunityPipeline pipeline, OpportunityPipelineStage stage,
            Map<StageKey, DashboardSalesFunnelValue> totals) {
        DashboardSalesFunnelValue value = totals.get(new StageKey(
                pipeline.getCode(), stage.getCode()));
        return new DashboardOverviewVO.SalesFunnelStageVO(stage.getCode(), stage.getName(),
                stage.getProbability(), value == null ? 0L : value.count(),
                value == null ? BigDecimal.ZERO.setScale(2) : value.amount());
    }

    private Map<StageKey, DashboardSalesFunnelValue> totals(
            List<DashboardSalesFunnelValue> values) {
        Map<StageKey, DashboardSalesFunnelValue> result = new LinkedHashMap<>();
        for (DashboardSalesFunnelValue value : values) {
            if (result.put(new StageKey(value.pipelineCode(), value.stageCode()), value) != null) {
                throw new IllegalStateException("dashboard sales funnel projection is duplicated");
            }
        }
        return Map.copyOf(result);
    }

    private void requireFields(
            TenantId tenantId, OpportunityAccessResolver.OpportunityAccess source) {
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), BusinessObjectType.OPPORTUNITY);
        for (String code : List.of(OpportunityFieldCatalog.CREATED_TIME,
                OpportunityFieldCatalog.STATUS, OpportunityFieldCatalog.PIPELINE_CODE,
                OpportunityFieldCatalog.STAGE_CODE, OpportunityFieldCatalog.ESTIMATED_AMOUNT)) {
            EffectiveFieldAccess access = fields.get(new FieldCode(code));
            if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                    || access.maskType() != FieldMaskType.NONE) {
                throw denied("sales funnel field is unavailable");
            }
        }
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("dashboard sales funnel tenant is inconsistent");
        }
    }

    private DashboardOverviewVO.SalesFunnelVO unavailable() {
        return new DashboardOverviewVO.SalesFunnelVO(UNAVAILABLE, List.of());
    }

    private DashboardOverviewVO.SalesFunnelVO empty() {
        return new DashboardOverviewVO.SalesFunnelVO(EMPTY, List.of());
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    private record StageKey(String pipelineCode, String stageCode) {
    }
}
