package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
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
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSalesFunnelQuerySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of());
    private static final ReportDateRange RANGE = new ReportDateRange(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

    @Test
    void returnsConfiguredStagesAndServerTotalsForTheNarrowedOpportunityScope() {
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        OpportunityAccessResolver opportunities = mock(OpportunityAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        EffectiveFieldAccessResolver fields = mock(EffectiveFieldAccessResolver.class);
        OpportunityPipelineRepository pipelines = mock(OpportunityPipelineRepository.class);
        OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
        OpportunityVisibility visibility = mock(OpportunityVisibility.class);
        OpportunityAccessResolver.OpportunityAccess source =
                new OpportunityAccessResolver.OpportunityAccess(TENANT, MEMBER, visibility);
        when(organization.requireActiveMembership(CALLER,
                AuthorityCatalog.OPPORTUNITY_PIPELINES_READ)).thenReturn(TENANT);
        when(opportunities.access(CALLER, AuthorityCatalog.OPPORTUNITIES_READ,
                OpportunityAccessMode.READ)).thenReturn(source);
        when(narrower.narrow(source, DashboardDataScope.DEPARTMENT_SUBTREE)).thenReturn(source);
        when(fields.resolve(TENANT, MEMBER, BusinessObjectType.OPPORTUNITY)).thenReturn(readableFields());
        OpportunityPipeline pipeline = OpportunityPipeline.create(TENANT, "direct", "直营流程", 0);
        pipeline.activate();
        OpportunityPipelineStage qualification = OpportunityPipelineStage.create(
                TENANT, "direct", "qualification", "需求确认", 0, 20);
        qualification.activate();
        OpportunityPipelineStage proposal = OpportunityPipelineStage.create(
                TENANT, "direct", "proposal", "方案报价", 1, 60);
        proposal.activate();
        when(pipelines.findPipelines(eq(TENANT), any(OpportunityPipelineQuery.class)))
                .thenReturn(new PageResult<>(1, 100, 1, List.of(pipeline)));
        // One batched stage lookup covers every pipeline in the view.
        when(pipelines.findActiveStages(eq(TENANT), anyCollection(), anyInt()))
                .thenReturn(Map.of("direct", List.of(qualification, proposal)));
        when(opportunityRepository.summarizeSalesFunnel(TENANT, visibility, RANGE)).thenReturn(List.of(
                new DashboardSalesFunnelValue("direct", "qualification", 2L,
                        new BigDecimal("30.30"))));

        var result = support(organization, opportunities, narrower, fields, pipelines,
                opportunityRepository).summarize(CALLER, TENANT,
                DashboardDataScope.DEPARTMENT_SUBTREE, RANGE);

        assertEquals("AVAILABLE", result.status());
        assertEquals(1, result.pipelines().size());
        var stages = result.pipelines().getFirst().stages();
        assertEquals("需求确认", stages.getFirst().name());
        assertEquals(2L, stages.getFirst().count());
        assertEquals(new BigDecimal("30.30"), stages.getFirst().amount());
        assertEquals("方案报价", stages.getLast().name());
        assertEquals(0L, stages.getLast().count());
        assertEquals(new BigDecimal("0.00"), stages.getLast().amount());
        verify(narrower).narrow(source, DashboardDataScope.DEPARTMENT_SUBTREE);
    }

    @Test
    void keepsTheFunnelOpaqueWhenThePipelineCatalogIsNotReadable() {
        OrganizationAccessGuard organization = mock(OrganizationAccessGuard.class);
        when(organization.requireActiveMembership(CALLER,
                AuthorityCatalog.OPPORTUNITY_PIPELINES_READ)).thenThrow(denied());

        var result = support(organization, mock(OpportunityAccessResolver.class),
                mock(DashboardVisibilityNarrower.class), mock(EffectiveFieldAccessResolver.class),
                mock(OpportunityPipelineRepository.class), mock(OpportunityRepository.class))
                .summarize(CALLER, TENANT, DashboardDataScope.SELF, RANGE);

        assertEquals("UNAVAILABLE", result.status());
        assertEquals(List.of(), result.pipelines());
    }

    private DashboardSalesFunnelQuerySupport support(
            OrganizationAccessGuard organization, OpportunityAccessResolver opportunities,
            DashboardVisibilityNarrower narrower, EffectiveFieldAccessResolver fields,
            OpportunityPipelineRepository pipelines, OpportunityRepository opportunityRepository) {
        return new DashboardSalesFunnelQuerySupport(organization, opportunities, narrower,
                fields, pipelines, opportunityRepository);
    }

    private Map<FieldCode, EffectiveFieldAccess> readableFields() {
        EffectiveFieldAccess readable = new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE);
        return Map.of(
                new FieldCode(OpportunityFieldCatalog.CREATED_TIME), readable,
                new FieldCode(OpportunityFieldCatalog.STATUS), readable,
                new FieldCode(OpportunityFieldCatalog.PIPELINE_CODE), readable,
                new FieldCode(OpportunityFieldCatalog.STAGE_CODE), readable,
                new FieldCode(OpportunityFieldCatalog.ESTIMATED_AMOUNT), readable);
    }

    private NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
    }
}
