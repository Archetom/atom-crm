package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageTaskDefinition;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApprovalWorkflowPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId APPLICANT = new MemberId(11L);
    private static final MemberId REVIEWER = new MemberId(12L);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 7, 17, 12, 0);

    @Test
    void approvalDefinitionAndRuntimeRoundTripWithRouteSnapshot() {
        ObjectMapper mapper = new ObjectMapper();
        ApprovalDefinitionPOConverter definitionConverter =
                new ApprovalDefinitionPOConverter(mapper);
        ApprovalTaskPOConverter taskConverter = new ApprovalTaskPOConverter();
        ApprovalInstancePOConverter instanceConverter =
                new ApprovalInstancePOConverter(mapper);
        ApprovalDefinition definition = ApprovalDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Discount", "High discount",
                ApprovalRepeatMode.ONCE, List.of(APPLICANT), List.of(),
                List.of(REVIEWER), List.of(new ApprovalStepDefinition(
                        "review", "Review", 0, ApprovalStepType.MEMBER,
                        ApprovalStepMode.ALL, ApprovalFallback.MANAGERS,
                        List.of(REVIEWER), List.of(), List.of())));
        definition.onPersisted(new ApprovalDefinitionId(21L), 3L, TIME, TIME);

        ApprovalDefinition restoredDefinition = definitionConverter.toDomain(
                definitionConverter.toPO(definition));
        assertEquals(definition.getSteps(), restoredDefinition.getSteps());
        assertEquals(3L, restoredDefinition.getVersion());

        FieldCode amount = new FieldCode("amount");
        ApprovalInstance instance = ApprovalInstance.submit(TENANT, definition.getId(),
                definition.getVersion(), BusinessObjectType.CUSTOMER, 31L,
                "converter-key", "Discount approval", APPLICANT,
                ApprovalRepeatMode.ONCE, Map.of(amount, List.of("100")),
                definition.getManagerMemberIds(), List.of(REVIEWER),
                List.of(new ResolvedApprovalStep(
                        "review", "Review", 0, ApprovalStepMode.ALL,
                        List.of(REVIEWER))));
        instance.onPersisted(new ApprovalInstanceId(41L), 2L, TIME, TIME);
        instance.getTasks().forEach(task -> task.onPersisted(1L, TIME, TIME));

        ApprovalInstance restoredInstance = instanceConverter.toDomain(
                instanceConverter.toPO(instance), instance.getTasks().stream()
                        .map(task -> taskConverter.toDomain(taskConverter.toPO(
                                TENANT, 41L, task))).toList(),
                instance.getManagerMemberIds(),
                instance.getWatcherMemberIds());
        assertEquals(instance.getFacts(), restoredInstance.getFacts());
        assertEquals(instance.getManagerMemberIds(), restoredInstance.getManagerMemberIds());
        assertEquals(instance.getTasks().getFirst().getTaskKey(),
                restoredInstance.getTasks().getFirst().getTaskKey());
        assertFalse(restoredInstance.hasDomainEvents());
    }

    @Test
    void stageDefinitionAndProgressRoundTrip() {
        ObjectMapper mapper = new ObjectMapper();
        StageWorkflowDefinitionPOConverter definitionConverter =
                new StageWorkflowDefinitionPOConverter(mapper);
        StageWorkflowRecordPOConverter recordConverter =
                new StageWorkflowRecordPOConverter(mapper);
        StageDefinition stage = new StageDefinition("qualify", "Qualify", 0, 25,
                List.of(new FieldCode("amount")),
                List.of(new StageTaskDefinition("review", "Review", true)),
                new ApprovalDefinitionId(21L));
        StageWorkflowDefinition workflow = StageWorkflowDefinition.create(TENANT,
                BusinessObjectType.CUSTOMER, "Sales", "Won", "Lost",
                List.of(APPLICANT), List.of(), List.of(stage));
        workflow.onPersisted(new StageWorkflowId(51L), 4L, TIME, TIME);

        StageWorkflowDefinition restoredWorkflow = definitionConverter.toDomain(
                definitionConverter.toPO(workflow));
        assertEquals(workflow.getStages(), restoredWorkflow.getStages());

        StageWorkflowRecord record = StageWorkflowRecord.start(TENANT, workflow.getId(),
                workflow.getVersion(), BusinessObjectType.CUSTOMER, 31L, stage);
        record.capture(stage, Map.of(new FieldCode("amount"), List.of("100")),
                java.util.Set.of("review"));
        record.onPersisted(new StageWorkflowRecordId(61L), 2L, TIME, TIME);

        StageWorkflowRecord restoredRecord = recordConverter.toDomain(
                recordConverter.toPO(record));
        assertEquals(record.getProgressByStage(), restoredRecord.getProgressByStage());
        assertEquals(2L, restoredRecord.getVersion());
        assertFalse(restoredRecord.hasDomainEvents());
    }
}
