package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OpportunityPipelineDomainException;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.policy.OpportunityPipelineCatalogPolicy;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Independently versioned tenant root for a stable Opportunity sales-pipeline code. */
@Getter
public class OpportunityPipeline extends AggregateRoot<OpportunityPipelineId> {

    private OpportunityPipelineId id;
    private TenantId tenantId;
    private String code;
    private String name;
    private int displayOrder;
    private OpportunityPipelineStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OpportunityPipeline() {
    }

    public record Snapshot(
            OpportunityPipelineId id, TenantId tenantId, String code, String name,
            Integer displayOrder, OpportunityPipelineStatus status, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static OpportunityPipeline create(
            TenantId tenantId, String code, String name, Integer displayOrder) {
        OpportunityPipeline pipeline = new OpportunityPipeline();
        pipeline.tenantId = requireTenant(tenantId);
        pipeline.code = OpportunityPipelineCatalogPolicy.requireCode(code);
        pipeline.status = OpportunityPipelineStatus.INACTIVE;
        pipeline.apply(name, displayOrder);
        pipeline.createdTime = pipeline.updatedTime;
        return pipeline;
    }

    public static OpportunityPipeline reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OpportunityPipelineDomainException("opportunity pipeline snapshot is required");
        }
        OpportunityPipeline pipeline = new OpportunityPipeline();
        pipeline.id = snapshot.id();
        pipeline.tenantId = requireTenant(snapshot.tenantId());
        pipeline.code = OpportunityPipelineCatalogPolicy.requireCode(snapshot.code());
        pipeline.name = OpportunityPipelineCatalogPolicy.requireName(snapshot.name());
        pipeline.displayOrder = OpportunityPipelineCatalogPolicy.requireDisplayOrder(
                snapshot.displayOrder());
        pipeline.status = snapshot.status() == null
                ? OpportunityPipelineStatus.DELETED : snapshot.status();
        pipeline.restoreVersion(snapshot.version());
        pipeline.createdTime = snapshot.createdTime();
        pipeline.updatedTime = snapshot.updatedTime();
        return pipeline;
    }

    public void update(String name, Integer displayOrder) {
        ensureMutable();
        apply(name, displayOrder);
    }

    public void activate() {
        ensureNotDeleted();
        status = OpportunityPipelineStatus.ACTIVE;
        touch();
    }

    public void deactivate() {
        ensureNotDeleted();
        status = OpportunityPipelineStatus.INACTIVE;
        touch();
    }

    public void delete() {
        if (status != OpportunityPipelineStatus.INACTIVE) {
            throw new OpportunityPipelineDomainException(
                    "only inactive opportunity pipelines can be deleted");
        }
        status = OpportunityPipelineStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == OpportunityPipelineStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == OpportunityPipelineStatus.DELETED;
    }

    public void onPersisted(
            OpportunityPipelineId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new OpportunityPipelineDomainException("persisted opportunity pipeline ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        this.createdTime = createdTime == null ? this.createdTime : createdTime;
        this.updatedTime = updatedTime == null ? this.updatedTime : updatedTime;
    }

    private void apply(String name, Integer displayOrder) {
        this.name = OpportunityPipelineCatalogPolicy.requireName(name);
        this.displayOrder = OpportunityPipelineCatalogPolicy.requireDisplayOrder(displayOrder);
        touch();
    }

    private void ensureMutable() {
        ensureNotDeleted();
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new OpportunityPipelineDomainException(
                    "deleted opportunity pipelines cannot change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new OpportunityPipelineDomainException("opportunity pipeline tenant is required");
        }
        return value;
    }
}
