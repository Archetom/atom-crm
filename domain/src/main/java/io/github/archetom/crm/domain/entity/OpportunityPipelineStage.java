package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OpportunityPipelineDomainException;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.policy.OpportunityPipelineCatalogPolicy;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;

/** Independently versioned tenant root for one immutable stage code under a pipeline code. */
@Getter
public class OpportunityPipelineStage extends AggregateRoot<OpportunityPipelineStageId> {

    private OpportunityPipelineStageId id;
    private TenantId tenantId;
    private String pipelineCode;
    private String code;
    private String name;
    private int displayOrder;
    private int probability;
    private OpportunityPipelineStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OpportunityPipelineStage() {
    }

    public record Snapshot(
            OpportunityPipelineStageId id, TenantId tenantId, String pipelineCode,
            String code, String name, Integer displayOrder, Integer probability,
            OpportunityPipelineStatus status, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public static OpportunityPipelineStage create(
            TenantId tenantId, String pipelineCode, String code, String name,
            Integer displayOrder, Integer probability) {
        OpportunityPipelineStage stage = new OpportunityPipelineStage();
        stage.tenantId = requireTenant(tenantId);
        stage.pipelineCode = OpportunityPipelineCatalogPolicy.requireCode(pipelineCode);
        stage.code = OpportunityPipelineCatalogPolicy.requireCode(code);
        stage.status = OpportunityPipelineStatus.INACTIVE;
        stage.apply(name, displayOrder, probability);
        stage.createdTime = stage.updatedTime;
        return stage;
    }

    public static OpportunityPipelineStage reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OpportunityPipelineDomainException("opportunity pipeline stage snapshot is required");
        }
        OpportunityPipelineStage stage = new OpportunityPipelineStage();
        stage.id = snapshot.id();
        stage.tenantId = requireTenant(snapshot.tenantId());
        stage.pipelineCode = OpportunityPipelineCatalogPolicy.requireCode(snapshot.pipelineCode());
        stage.code = OpportunityPipelineCatalogPolicy.requireCode(snapshot.code());
        stage.name = OpportunityPipelineCatalogPolicy.requireName(snapshot.name());
        stage.displayOrder = OpportunityPipelineCatalogPolicy.requireDisplayOrder(
                snapshot.displayOrder());
        stage.probability = OpportunityPipelineCatalogPolicy.requireProbability(
                snapshot.probability());
        stage.status = snapshot.status() == null
                ? OpportunityPipelineStatus.DELETED : snapshot.status();
        stage.restoreVersion(snapshot.version());
        stage.createdTime = snapshot.createdTime();
        stage.updatedTime = snapshot.updatedTime();
        return stage;
    }

    public void update(String name, Integer displayOrder, Integer probability) {
        ensureNotDeleted();
        apply(name, displayOrder, probability);
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
                    "only inactive opportunity pipeline stages can be deleted");
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
            OpportunityPipelineStageId id, Long version,
            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new OpportunityPipelineDomainException(
                    "persisted opportunity pipeline stage ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        this.createdTime = createdTime == null ? this.createdTime : createdTime;
        this.updatedTime = updatedTime == null ? this.updatedTime : updatedTime;
    }

    private void apply(String name, Integer displayOrder, Integer probability) {
        this.name = OpportunityPipelineCatalogPolicy.requireName(name);
        this.displayOrder = OpportunityPipelineCatalogPolicy.requireDisplayOrder(displayOrder);
        this.probability = OpportunityPipelineCatalogPolicy.requireProbability(probability);
        touch();
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new OpportunityPipelineDomainException(
                    "deleted opportunity pipeline stages cannot change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) {
            throw new OpportunityPipelineDomainException(
                    "opportunity pipeline stage tenant is required");
        }
        return value;
    }
}
