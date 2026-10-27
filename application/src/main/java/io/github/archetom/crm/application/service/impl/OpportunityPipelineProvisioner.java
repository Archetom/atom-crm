package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.OpportunityPipelineCatalogLock;
import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Idempotently installs the ordinary managed Opportunity sales catalog for a new tenant. */
@Component
@RequiredArgsConstructor
public class OpportunityPipelineProvisioner {

    private final OpportunityPipelineCatalogLock catalogLock;
    private final OpportunityPipelineRepository repository;

    public void provision(TenantId tenantId) {
        catalogLock.acquire(tenantId);
        if (repository.findPipelineByCode(tenantId, "direct").isPresent()) {
            return;
        }
        OpportunityPipeline pipeline = OpportunityPipeline.create(
                tenantId, "direct", "Direct", 10);
        repository.savePipeline(tenantId, pipeline);
        OpportunityPipelineStage qualification = OpportunityPipelineStage.create(
                tenantId, pipeline.getCode(), "qualification", "Qualification", 10, 0);
        qualification.activate();
        repository.saveStage(tenantId, qualification);
        OpportunityPipelineStage negotiation = OpportunityPipelineStage.create(
                tenantId, pipeline.getCode(), "negotiation", "Negotiation", 20, 0);
        negotiation.activate();
        repository.saveStage(tenantId, negotiation);
        pipeline.activate();
        repository.savePipeline(tenantId, pipeline);
    }
}
