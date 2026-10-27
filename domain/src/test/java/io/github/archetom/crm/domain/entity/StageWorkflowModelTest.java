package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageTaskDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowRecordStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageWorkflowModelTest {

    private static final TenantId TENANT = new TenantId(1L);
    private static final FieldCode AMOUNT = new FieldCode("amount");
    private static final FieldCode CONTRACT_SCAN = new FieldCode("contract_scan");

    @Test
    void stageRequiresConfiguredFieldsAndRequiredTasks() {
        StageDefinition stage = stage("qualify", 0, true);
        StageWorkflowRecord record = record(stage);

        assertThrows(StageWorkflowDomainException.class,
                () -> record.capture(stage, Map.of(), Set.of("review")));
        assertThrows(StageWorkflowDomainException.class,
                () -> record.capture(stage, Map.of(AMOUNT, List.of()), Set.of("review")));
        assertThrows(StageWorkflowDomainException.class,
                () -> record.capture(stage, Map.of(AMOUNT, List.of("10")), Set.of()));

        record.capture(stage, Map.of(AMOUNT, List.of("10")), Set.of("review"));
        assertEquals(List.of("10"), record.getProgressByStage()
                .get("qualify").fieldValues().get(AMOUNT));
    }

    @Test
    void approvalGateCanResumeAfterRejectionAndCompleteAfterApproval() {
        StageDefinition current = stage("qualify", 0, true);
        StageDefinition next = stage("close", 1, false);
        StageWorkflowRecord record = record(current);
        record.capture(current, Map.of(AMOUNT, List.of("10")), Set.of("review"));
        record.awaitApproval(new ApprovalInstanceId(30L));

        assertEquals(StageWorkflowRecordStatus.AWAITING_APPROVAL, record.getStatus());
        assertFalse(record.resolveApproval(ApprovalInstanceStatus.REJECTED));
        assertEquals(StageWorkflowRecordStatus.ACTIVE, record.getStatus());
        assertEquals(1, record.getProgressByStage().get("qualify").approvalAttempt());

        record.awaitApproval(new ApprovalInstanceId(31L));
        assertTrue(record.resolveApproval(ApprovalInstanceStatus.APPROVED));
        record.completeCurrent(current, next);
        assertEquals("close", record.getCurrentStageCode());

        record.capture(next, Map.of(AMOUNT, List.of("20")), Set.of());
        record.completeCurrent(next, null);
        assertEquals(StageWorkflowRecordStatus.SUCCEEDED, record.getStatus());
        assertNull(record.getCurrentStageCode());
    }

    @Test
    void failedOutcomeIsTerminal() {
        StageDefinition stage = stage("qualify", 0, false);
        StageWorkflowRecord record = record(stage);
        record.fail("No fit");

        assertEquals(StageWorkflowRecordStatus.FAILED, record.getStatus());
        assertThrows(StageWorkflowDomainException.class, () -> record.fail("again"));
    }

    @Test
    void stageCanSatisfyRequiredFileWithoutLeakingItIntoScalarFacts() {
        StageDefinition stage = new StageDefinition("capture", "Capture", 0, 0,
                List.of(CONTRACT_SCAN), List.of(), null);
        StageWorkflowRecord record = record(stage);

        assertThrows(StageWorkflowDomainException.class,
                () -> record.capture(stage, Map.of(), Set.of(), Set.of()));

        record.capture(stage, Map.of(), Set.of(CONTRACT_SCAN), Set.of());
        assertTrue(record.getProgressByStage().get("capture").fieldValues().isEmpty());
    }

    private StageWorkflowRecord record(StageDefinition first) {
        return StageWorkflowRecord.start(TENANT, new StageWorkflowId(2L), 0L,
                BusinessObjectType.CUSTOMER, 20L, first);
    }

    private StageDefinition stage(String code, int order, boolean approval) {
        return new StageDefinition(code, code, order, order * 50,
                List.of(AMOUNT), order == 0
                ? List.of(new StageTaskDefinition("review", "Review", true)) : List.of(),
                approval ? new ApprovalDefinitionId(9L) : null);
    }
}
