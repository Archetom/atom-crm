package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OpportunityPipelineDomainException;
import io.github.archetom.crm.domain.model.OpportunityPipelineStatus;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpportunityPipelineModelTest {

    private static final TenantId TENANT = new TenantId(42L);

    @Test
    void pipelineAndStageStartInactiveWithTrimmedExactCodes() {
        OpportunityPipeline pipeline = OpportunityPipeline.create(
                TENANT, "  Direct  ", " Direct sales ", 10);
        OpportunityPipelineStage stage = OpportunityPipelineStage.create(
                TENANT, pipeline.getCode(), " qualification ", " Qualification ", 10, 0);

        assertEquals("Direct", pipeline.getCode());
        assertEquals("qualification", stage.getCode());
        assertEquals(OpportunityPipelineStatus.INACTIVE, pipeline.getStatus());
        assertEquals(OpportunityPipelineStatus.INACTIVE, stage.getStatus());
    }

    @Test
    void codeIsPermanentAndDeletedRootsCannotChange() {
        OpportunityPipeline pipeline = OpportunityPipeline.create(TENANT,
                "direct", "Direct", 10);
        pipeline.update("Renamed", 20);
        pipeline.deactivate();
        pipeline.delete();

        assertEquals("direct", pipeline.getCode());
        assertThrows(OpportunityPipelineDomainException.class,
                () -> pipeline.update("Again", 30));
        assertThrows(OpportunityPipelineDomainException.class,
                () -> OpportunityPipeline.create(TENANT, "dîrect", "Bad", 0));
    }

    @Test
    void pipelineAndStageRequireInactiveBeforeDeletion() {
        OpportunityPipeline pipeline = OpportunityPipeline.create(TENANT,
                "direct", "Direct", 10);
        OpportunityPipelineStage stage = OpportunityPipelineStage.create(TENANT,
                "direct", "qualification", "Qualification", 10, 0);
        pipeline.activate();
        stage.activate();

        assertThrows(OpportunityPipelineDomainException.class, pipeline::delete);
        assertThrows(OpportunityPipelineDomainException.class, stage::delete);
    }

    @Test
    void namesUseUnicodeCodePointBoundaries() {
        String supplementary = "\uD83D\uDE80";
        String atLimit = supplementary.repeat(200);
        String aboveLimit = supplementary.repeat(201);

        assertEquals(atLimit, OpportunityPipeline.create(TENANT,
                "direct", atLimit, 10).getName());
        assertEquals(atLimit, OpportunityPipelineStage.create(TENANT,
                "direct", "qualification", atLimit, 10, 0).getName());
        assertThrows(OpportunityPipelineDomainException.class,
                () -> OpportunityPipeline.create(TENANT, "direct", aboveLimit, 10));
        assertThrows(OpportunityPipelineDomainException.class,
                () -> OpportunityPipelineStage.create(TENANT,
                        "direct", "qualification", aboveLimit, 10, 0));
    }
}
