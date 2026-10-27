package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashSet;

/** Cross-aggregate limit for the snapshot used by a stage approval gate. */
public final class StageApprovalFactPolicy {

    private StageApprovalFactPolicy() {
    }

    public static boolean isWithinLimit(
            StageDefinition stage, ApprovalDefinition definition) {
        if (stage == null || definition == null) {
            throw new IllegalArgumentException(
                    "stage and approval definition are required");
        }
        LinkedHashSet<FieldCode> fields =
                new LinkedHashSet<>(stage.requiredFieldCodes());
        definition.getSteps().forEach(step -> step.conditions().forEach(
                condition -> fields.add(condition.fieldCode())));
        return fields.size() <= ApprovalInstance.MAX_ROUTING_FACTS;
    }
}
