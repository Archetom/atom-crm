package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.StageWorkflowDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowStatus;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StageWorkflowDefinitionTest {

    @Test
    void exposesAndEnforcesOnlyAllowedStatusTargets() {
        LocalDateTime now = LocalDateTime.now();
        StageWorkflowDefinition workflow = StageWorkflowDefinition.reconstitute(
                new StageWorkflowDefinition.Snapshot(new StageWorkflowId(1L), new TenantId(1L),
                        BusinessObjectType.CUSTOMER, "Customer stages", "Succeeded", "Failed",
                        StageWorkflowStatus.ACTIVE, Set.of(), Set.of(), List.of(new StageDefinition(
                                "review", "Review", 0, 50, List.of(), List.of(), null)), 3L, now, now));

        assertEquals(Set.of(StageWorkflowStatus.INACTIVE, StageWorkflowStatus.DELETED),
                workflow.allowedStatusTransitions());
        assertThrows(StageWorkflowDomainException.class,
                () -> workflow.changeStatus(StageWorkflowStatus.ACTIVE));

        workflow.changeStatus(StageWorkflowStatus.INACTIVE);
        assertEquals(StageWorkflowStatus.INACTIVE, workflow.getStatus());
    }
}
