package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.OpportunityPipeline;
import io.github.archetom.crm.domain.entity.OpportunityPipelineStage;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineId;
import io.github.archetom.crm.domain.valueobject.OpportunityPipelineStageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelinePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPipelineStagePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpportunityPipelinePOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 24, 10, 0);

    @Test
    void pipelineRoundTripsWithoutEvents() {
        OpportunityPipeline pipeline = OpportunityPipeline.create(
                TENANT, "Direct-Enterprise", "Direct enterprise", 10);
        pipeline.activate();
        pipeline.onPersisted(new OpportunityPipelineId(21L), 4L, CREATED, CREATED.plusMinutes(1));

        OpportunityPipelinePOConverter converter = new OpportunityPipelinePOConverter();
        OpportunityPipelinePO root = converter.toPO(pipeline);
        OpportunityPipeline restored = converter.toDomain(root);

        assertEquals(pipeline.getId(), restored.getId());
        assertEquals(pipeline.getTenantId(), restored.getTenantId());
        assertEquals("Direct-Enterprise", restored.getCode());
        assertEquals(pipeline.getStatus(), restored.getStatus());
        assertEquals(4L, restored.getVersion());
        assertFalse(restored.hasDomainEvents());
    }

    @Test
    void stageRoundTripsWithoutEvents() {
        OpportunityPipelineStage stage = OpportunityPipelineStage.create(
                TENANT, "Direct-Enterprise", "Qualified", "Qualified", 20, 60);
        stage.activate();
        stage.onPersisted(new OpportunityPipelineStageId(31L), 5L, CREATED, CREATED.plusMinutes(2));

        OpportunityPipelinePOConverter converter = new OpportunityPipelinePOConverter();
        OpportunityPipelineStagePO root = converter.toPO(stage);
        OpportunityPipelineStage restored = converter.toDomain(root);

        assertEquals(stage.getId(), restored.getId());
        assertEquals(stage.getPipelineCode(), restored.getPipelineCode());
        assertEquals(stage.getCode(), restored.getCode());
        assertEquals(60, restored.getProbability());
        assertEquals(stage.getStatus(), restored.getStatus());
        assertEquals(5L, restored.getVersion());
        assertFalse(restored.hasDomainEvents());
    }
}
