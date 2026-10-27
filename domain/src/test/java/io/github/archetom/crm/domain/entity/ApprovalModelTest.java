package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.ApprovalActionType;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalTaskStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.policy.ApprovalConditionEvaluator;
import io.github.archetom.crm.domain.policy.ApprovalFactPolicy;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalModelTest {

    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId APPLICANT = new MemberId(10L);
    private static final MemberId FIRST = new MemberId(11L);
    private static final MemberId SECOND = new MemberId(12L);

    @Test
    void allStepRequiresEveryAssigneeAndRejectsImmediately() {
        ApprovalInstance approved = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ALL, FIRST, SECOND));

        approved.act(FIRST, ApprovalActionType.APPROVE, null, "ok");
        assertEquals(ApprovalInstanceStatus.PENDING, approved.getStatus());
        approved.act(SECOND, ApprovalActionType.APPROVE, null, null);
        assertEquals(ApprovalInstanceStatus.APPROVED, approved.getStatus());
        assertNull(approved.getCurrentStepOrder());

        ApprovalInstance rejected = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ALL, FIRST, SECOND));
        rejected.act(FIRST, ApprovalActionType.REJECT, null, "no");
        assertEquals(ApprovalInstanceStatus.REJECTED, rejected.getStatus());
        assertTrue(rejected.getTasks().stream().anyMatch(task ->
                task.getAssignedMemberId().equals(SECOND)
                        && task.getStatus() == ApprovalTaskStatus.SKIPPED));
    }

    @Test
    void anyStepApprovesOnFirstApprovalButWaitsForAllRejections() {
        ApprovalInstance approved = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ANY, FIRST, SECOND));
        approved.act(FIRST, ApprovalActionType.APPROVE, null, null);
        assertEquals(ApprovalInstanceStatus.APPROVED, approved.getStatus());
        assertEquals(ApprovalTaskStatus.SKIPPED, approved.getTasks().get(1).getStatus());

        ApprovalInstance rejected = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ANY, FIRST, SECOND));
        rejected.act(FIRST, ApprovalActionType.REJECT, null, null);
        assertEquals(ApprovalInstanceStatus.PENDING, rejected.getStatus());
        rejected.act(SECOND, ApprovalActionType.REJECT, null, null);
        assertEquals(ApprovalInstanceStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void sequentialOnceSkipsAnApproverWhoAlreadyApprovedAnEarlierStep() {
        ApprovalInstance instance = instance(ApprovalRepeatMode.ONCE,
                step("first", 0, ApprovalStepMode.ALL, FIRST),
                step("second", 1, ApprovalStepMode.SEQUENTIAL, FIRST, SECOND));

        instance.act(FIRST, ApprovalActionType.APPROVE, null, null);

        assertEquals(ApprovalTaskStatus.SKIPPED, instance.getTasks().get(1).getStatus());
        assertTrue(instance.getTasks().get(2).isPendingFor(SECOND));
        instance.act(SECOND, ApprovalActionType.APPROVE, null, null);
        assertEquals(ApprovalInstanceStatus.APPROVED, instance.getStatus());
    }

    @Test
    void transferPreservesHistoryAndCreatesAReplacementTask() {
        MemberId replacement = new MemberId(13L);
        ApprovalInstance instance = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ALL, FIRST));

        instance.act(FIRST, ApprovalActionType.TRANSFER, replacement, "away");

        assertEquals(2, instance.getTasks().size());
        assertEquals(ApprovalTaskStatus.TRANSFERRED,
                instance.getTasks().getFirst().getStatus());
        assertEquals(instance.getTasks().getFirst().getTaskKey(),
                instance.getTasks().getLast().getSourceTaskKey());
        assertTrue(instance.getTasks().getLast().isPendingFor(replacement));
        instance.act(replacement, ApprovalActionType.APPROVE, null, null);
        assertEquals(ApprovalInstanceStatus.APPROVED, instance.getStatus());
    }

    @Test
    void transferCannotExceedTaskHistoryLimitAndDoesNotMutateSourceOnFailure() {
        ApprovalInstance full = instance(ApprovalRepeatMode.EVERY_STEP,
                routeWithTaskCount(ApprovalInstance.MAX_TASKS));
        ApprovalTask source = full.getTasks().getFirst();

        assertThrows(ApprovalDomainException.class, () -> full.act(
                source.getAssignedMemberId(), ApprovalActionType.TRANSFER,
                new MemberId(100_000L), null));

        assertEquals(ApprovalInstance.MAX_TASKS, full.getTasks().size());
        assertEquals(ApprovalTaskStatus.PENDING, source.getStatus());

        ApprovalInstance available = instance(ApprovalRepeatMode.EVERY_STEP,
                routeWithTaskCount(ApprovalInstance.MAX_TASKS - 1));
        ApprovalTask availableSource = available.getTasks().getFirst();
        available.act(availableSource.getAssignedMemberId(), ApprovalActionType.TRANSFER,
                new MemberId(100_000L), null);

        assertEquals(ApprovalInstance.MAX_TASKS, available.getTasks().size());
        assertEquals(ApprovalTaskStatus.TRANSFERRED, availableSource.getStatus());
        assertEquals(ApprovalInstance.MAX_TASKS, reconstitute(available).getTasks().size());
    }

    @Test
    void onceTransferRejectsEarlierApproverForEveryStepModeWithoutMutation() {
        for (ApprovalStepMode mode : List.of(
                ApprovalStepMode.SEQUENTIAL, ApprovalStepMode.ALL, ApprovalStepMode.ANY)) {
            ApprovalInstance instance = instance(ApprovalRepeatMode.ONCE,
                    step("first", 0, ApprovalStepMode.ALL, FIRST),
                    step("second", 1, mode, SECOND, new MemberId(13L)));
            instance.act(FIRST, ApprovalActionType.APPROVE, null, null);
            int taskCount = instance.getTasks().size();

            assertThrows(ApprovalDomainException.class, () -> instance.act(
                    SECOND, ApprovalActionType.TRANSFER, FIRST, null));

            assertEquals(taskCount, instance.getTasks().size());
            assertTrue(instance.getTasks().stream().anyMatch(
                    task -> task.isPendingFor(SECOND)));
        }
    }

    @Test
    void onlyApplicantCanWithdrawAndOnlyBeforeAnAction() {
        ApprovalInstance instance = instance(ApprovalRepeatMode.EVERY_STEP,
                step("review", 0, ApprovalStepMode.ALL, FIRST));
        assertThrows(ApprovalDomainException.class,
                () -> instance.withdraw(FIRST));
        instance.withdraw(APPLICANT);
        assertEquals(ApprovalInstanceStatus.WITHDRAWN, instance.getStatus());

        ApprovalInstance acted = instance(ApprovalRepeatMode.EVERY_STEP,
                step("first", 0, ApprovalStepMode.ALL, FIRST),
                step("second", 1, ApprovalStepMode.ALL, SECOND));
        acted.act(FIRST, ApprovalActionType.APPROVE, null, null);
        assertThrows(ApprovalDomainException.class,
                () -> acted.withdraw(APPLICANT));
    }

    @Test
    void typedConditionsUseAnImmutableFactSnapshot() {
        FieldCode amount = new FieldCode("amount");
        FilterCondition condition = new FilterCondition(amount, FilterOperator.BETWEEN,
                List.of("10", "20"));

        assertTrue(ApprovalConditionEvaluator.matches(List.of(condition),
                Map.of(amount, List.of("15")), Map.of(amount, CustomFieldType.DECIMAL)));
        assertThrows(ApprovalDomainException.class, () ->
                ApprovalConditionEvaluator.matches(List.of(condition),
                        Map.of(amount, List.of("invalid")),
                        Map.of(amount, CustomFieldType.DECIMAL)));
    }

    @Test
    void factNormalizationRejectsUnsafeDecimalExpansionAndMalformedEmail() {
        FieldDefinition decimal = FieldDefinition.createCustom(TENANT,
                BusinessObjectType.CUSTOMER, new FieldCode("amount"), "Amount", null,
                CustomFieldType.DECIMAL, false, false, true, 0, null, List.of());
        FieldDefinition email = FieldDefinition.createCustom(TENANT,
                BusinessObjectType.CUSTOMER, new FieldCode("email"), "Email", null,
                CustomFieldType.EMAIL, false, false, true, 1, null, List.of());

        assertEquals(List.of("10000000000000000000"),
                ApprovalFactPolicy.normalize(decimal, List.of("1e+19")));
        assertThrows(ApprovalDomainException.class,
                () -> ApprovalFactPolicy.normalize(decimal, List.of("1e+1000000")));
        assertEquals(List.of("user@example.com"),
                ApprovalFactPolicy.normalize(email, List.of(" User@Example.com ")));
        assertThrows(ApprovalDomainException.class,
                () -> ApprovalFactPolicy.normalize(email, List.of("not-an-email")));
        assertThrows(ApprovalDomainException.class,
                () -> ApprovalFactPolicy.normalize(email,
                        Collections.singletonList(null)));
    }

    @Test
    void instanceSnapshotsManagersAndDefinitionsBoundManagerFallback() {
        ApprovalInstance instance = instance(ApprovalRepeatMode.ONCE,
                step("review", 0, ApprovalStepMode.ALL, SECOND));
        assertTrue(instance.isManager(FIRST));

        List<MemberId> tooMany = java.util.stream.LongStream.rangeClosed(1, 101)
                .mapToObj(MemberId::new).toList();
        assertThrows(ApprovalDomainException.class, () -> ApprovalDefinition.create(
                TENANT, BusinessObjectType.CUSTOMER, "Too many managers", null,
                ApprovalRepeatMode.ONCE, List.of(), List.of(), tooMany,
                List.of(new ApprovalStepDefinition("review", "Review", 0,
                        ApprovalStepType.MEMBER, ApprovalStepMode.ALL,
                        ApprovalFallback.MANAGERS, List.of(SECOND), List.of(), List.of()))));
    }

    @Test
    void oaSnapshotAllowsFourAuthorityFactsWithoutWideningCrmBoundary() {
        Map<FieldCode, List<String>> oneHundredOne = facts(101);
        assertThrows(ApprovalDomainException.class, () -> instanceWithFacts(
                BusinessObjectType.CUSTOMER, oneHundredOne));
        assertDoesNotThrow(() -> instanceWithFacts(
                BusinessObjectType.OA_REQUEST, facts(104)));
        assertThrows(ApprovalDomainException.class, () -> instanceWithFacts(
                BusinessObjectType.OA_REQUEST, facts(105)));
    }

    private ApprovalInstance instance(ApprovalRepeatMode repeatMode,
                                      ResolvedApprovalStep... route) {
        return ApprovalInstance.submit(TENANT, new ApprovalDefinitionId(2L), 3L,
                BusinessObjectType.CUSTOMER, 20L, "test-key-" + System.nanoTime(),
                "Test approval", APPLICANT, repeatMode, Map.of(), List.of(FIRST), List.of(),
                List.of(route));
    }

    private ApprovalInstance instanceWithFacts(
            BusinessObjectType objectType, Map<FieldCode, List<String>> facts) {
        return ApprovalInstance.submit(TENANT, new ApprovalDefinitionId(2L), 3L,
                objectType, 20L, "fact-limit-" + objectType + '-' + facts.size(),
                "Fact boundary", APPLICANT, ApprovalRepeatMode.ONCE,
                facts, List.of(FIRST), List.of(), List.of());
    }

    private Map<FieldCode, List<String>> facts(int count) {
        LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            result.put(new FieldCode("field_" + index), List.of("value"));
        }
        return result;
    }

    private ResolvedApprovalStep step(String code, int order, ApprovalStepMode mode,
                                      MemberId... assignees) {
        return new ResolvedApprovalStep(code, code, order, mode, List.of(assignees));
    }

    private ResolvedApprovalStep[] routeWithTaskCount(int taskCount) {
        List<ResolvedApprovalStep> route = new ArrayList<>();
        int remaining = taskCount;
        int order = 0;
        while (remaining > 0) {
            int count = Math.min(ApprovalStepDefinition.MAX_ACTORS, remaining);
            List<MemberId> members = java.util.stream.LongStream.range(1_000L, 1_000L + count)
                    .mapToObj(MemberId::new).toList();
            route.add(new ResolvedApprovalStep("step-" + order, "Step " + order,
                    order, ApprovalStepMode.ALL, members));
            remaining -= count;
            order++;
        }
        return route.toArray(ResolvedApprovalStep[]::new);
    }

    private ApprovalInstance reconstitute(ApprovalInstance instance) {
        return ApprovalInstance.reconstitute(new ApprovalInstance.Snapshot(
                instance.getId(), instance.getTenantId(), instance.getDefinitionId(),
                instance.getDefinitionVersion(), instance.getObjectType(),
                instance.getSubjectId(), instance.getIdempotencyKey(), instance.getTitle(),
                instance.getApplicantMemberId(), instance.getRepeatMode(), instance.getStatus(),
                instance.getCurrentStepOrder(), instance.getFacts(),
                instance.getManagerMemberIds(), instance.getWatcherMemberIds(),
                instance.getTasks(), instance.getVersion(), instance.getSubmittedTime(),
                instance.getCompletedTime(), instance.getCreatedTime(), instance.getUpdatedTime()));
    }
}
