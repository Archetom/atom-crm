package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageApprovalFactPolicyTest {

    @Test
    void acceptsOneHundredUniqueFieldsAndRejectsOneHundredOne() {
        ApprovalDefinition definition = definitionWithConditionFields(50);

        assertTrue(StageApprovalFactPolicy.isWithinLimit(
                stageWithRequiredFields(50), definition));
        assertFalse(StageApprovalFactPolicy.isWithinLimit(
                stageWithRequiredFields(51), definition));
    }

    private ApprovalDefinition definitionWithConditionFields(int count) {
        List<ApprovalStepDefinition> steps = new ArrayList<>();
        int field = 0;
        int order = 0;
        while (field < count) {
            List<FilterCondition> conditions = new ArrayList<>();
            while (field < count
                    && conditions.size() < ApprovalStepDefinition.MAX_CONDITIONS) {
                conditions.add(new FilterCondition(new FieldCode("condition_" + field),
                        FilterOperator.EQ, List.of("value")));
                field++;
            }
            steps.add(new ApprovalStepDefinition("step_" + order, "Step " + order,
                    order, ApprovalStepType.MEMBER, ApprovalStepMode.ALL,
                    ApprovalFallback.AUTO_APPROVE, List.of(new MemberId(7L)), List.of(),
                    conditions));
            order++;
        }
        return ApprovalDefinition.create(new TenantId(99L), BusinessObjectType.CUSTOMER,
                "Definition", null, ApprovalRepeatMode.ONCE, List.of(), List.of(),
                List.of(), steps);
    }

    private StageDefinition stageWithRequiredFields(int count) {
        List<FieldCode> fields = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new FieldCode("required_" + index)).toList();
        return new StageDefinition("review", "Review", 0, 50,
                fields, List.of(), null);
    }
}
