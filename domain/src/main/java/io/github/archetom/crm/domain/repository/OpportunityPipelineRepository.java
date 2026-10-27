package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.model.OpportunityPipelineQuery;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tenant-scoped persistence for the two independent Opportunity sales-catalog roots. */
public interface OpportunityPipelineRepository {

    OpportunityPipeline savePipeline(TenantId tenantId, OpportunityPipeline pipeline);

    OpportunityPipelineStage saveStage(TenantId tenantId, OpportunityPipelineStage stage);

    Optional<OpportunityPipeline> findPipelineById(
            TenantId tenantId, OpportunityPipelineId pipelineId);

    Optional<OpportunityPipelineStage> findStageById(
            TenantId tenantId, String pipelineCode, OpportunityPipelineStageId stageId);

    Optional<OpportunityPipeline> findPipelineByCode(TenantId tenantId, String code);

    Optional<OpportunityPipelineStage> findStageByCode(
            TenantId tenantId, String pipelineCode, String stageCode);

    PageResult<OpportunityPipeline> findPipelines(
            TenantId tenantId, OpportunityPipelineQuery query);

    PageResult<OpportunityPipelineStage> findStages(
            TenantId tenantId, String pipelineCode, OpportunityPipelineQuery query);

    /**
     * Loads the active stages of many pipelines in one query, grouped by pipeline code.
     *
     * <p>A projection that shows every pipeline needs all of their stages at once; asking per
     * pipeline turns one view into one paged query per pipeline.</p>
     */
    Map<String, List<OpportunityPipelineStage>> findActiveStages(
            TenantId tenantId, Collection<String> pipelineCodes, int stageLimitPerPipeline);

    long countNonDeletedPipelines(TenantId tenantId);

    long countNonDeletedStages(TenantId tenantId, String pipelineCode);

    boolean existsActiveStage(TenantId tenantId, String pipelineCode);

    long countActiveStages(TenantId tenantId, String pipelineCode);

    boolean existsNonDeletedStage(TenantId tenantId, String pipelineCode);

    boolean existsActivePair(TenantId tenantId, String pipelineCode, String stageCode);
}
