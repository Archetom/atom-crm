package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageWorkflowTitleTest {

    @Test
    void stageApprovalTitleNeverExceedsInstanceLimit() {
        String workflowName = "w".repeat(200);
        String stageName = "s".repeat(100);

        String title = StageWorkflowRecordOperations.stageApprovalTitle(
                workflowName, stageName);

        assertEquals(ApprovalInstance.MAX_TITLE_LENGTH, title.length());
        assertEquals("w".repeat(97) + " / " + stageName, title);
        assertEquals("Workflow / Review",
                StageWorkflowRecordOperations.stageApprovalTitle("Workflow", "Review"));
    }
}
