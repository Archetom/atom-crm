package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.OpportunityPipelineCatalogLock;
import io.github.archetom.crm.domain.policy.OpportunityPipelineCatalogPolicy;
import io.github.archetom.crm.domain.repository.OpportunityPipelineRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Applies the catalog's active exact-pair invariant inside an already ordered command flow. */
@Component
@RequiredArgsConstructor
public class OpportunityPipelinePairValidator {

    private final OpportunityPipelineCatalogLock catalogLock;
    private final OpportunityPipelineRepository repository;

    public void acquire(TenantId tenantId) {
        catalogLock.acquire(tenantId);
    }

    public Pair normalize(String pipelineCode, String stageCode) {
        try {
            return new Pair(OpportunityPipelineCatalogPolicy.requireCode(pipelineCode),
                    OpportunityPipelineCatalogPolicy.requireCode(stageCode));
        } catch (RuntimeException exception) {
            throw unavailable(ApplicationErrorCode.PARAMETER_INVALID);
        }
    }

    public void requireActive(TenantId tenantId, Pair pair) {
        if (pair == null || !repository.existsActivePair(
                tenantId, pair.pipelineCode(), pair.stageCode())) {
            throw unavailable(ApplicationErrorCode.OPERATION_NOT_ALLOWED);
        }
    }

    private NonRetryableApplicationException unavailable(ApplicationErrorCode code) {
        return new NonRetryableApplicationException(
                code, "Opportunity pipeline stage is unavailable");
    }

    public record Pair(String pipelineCode, String stageCode) {
        public boolean sameAs(String pipelineCode, String stageCode) {
            return this.pipelineCode.equals(pipelineCode) && this.stageCode.equals(stageCode);
        }
    }
}
