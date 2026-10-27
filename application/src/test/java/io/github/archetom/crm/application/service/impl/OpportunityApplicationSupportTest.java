package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.OpportunityEditableBaselineVO;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpportunityApplicationSupportTest {

    private final OpportunityApplicationSupport support = new OpportunityApplicationSupport(
            mock(FieldDefinitionRepository.class), mock(EffectiveFieldAccessResolver.class),
            mock(OrganizationMembershipRepository.class), mock(DepartmentRepository.class));

    @Test
    void returnsEveryReplacementValueWhenAllFieldsAreReadableUnmaskedAndWritable() {
        OpportunityEditableBaselineVO baseline = support.editableBaseline(
                opportunity(), writableFields());

        assertTrue(baseline.editable());
        assertEquals(1L, baseline.id());
        assertEquals(4L, baseline.version());
        assertEquals("Annual renewal", baseline.name());
        assertEquals(9L, baseline.customerId());
        assertEquals("direct", baseline.pipelineCode());
        assertEquals("qualification", baseline.stageCode());
        assertEquals(LocalDate.of(2026, 9, 30), baseline.expectedCloseDate());
        assertEquals(new BigDecimal("1250.50"), baseline.estimatedAmount());
        assertEquals(LocalDateTime.of(2026, 8, 8, 9, 30), baseline.nextContactTime());
        assertEquals("Renewal discussion", baseline.description());
    }

    @Test
    void withholdsEveryBusinessValueWhenOneReplacementFieldIsMasked() {
        Map<FieldCode, EffectiveFieldAccess> accesses = new HashMap<>(writableFields());
        accesses.put(new FieldCode(OpportunityFieldCatalog.ESTIMATED_AMOUNT),
                new EffectiveFieldAccess(FieldAccessLevel.WRITE, FieldMaskType.FULL));

        OpportunityEditableBaselineVO baseline = support.editableBaseline(opportunity(), accesses);

        assertFalse(baseline.editable());
        assertEquals(1L, baseline.id());
        assertEquals(4L, baseline.version());
        assertNull(baseline.name());
        assertNull(baseline.customerId());
        assertNull(baseline.pipelineCode());
        assertNull(baseline.stageCode());
        assertNull(baseline.expectedCloseDate());
        assertNull(baseline.estimatedAmount());
        assertNull(baseline.nextContactTime());
        assertNull(baseline.description());
    }

    private static Map<FieldCode, EffectiveFieldAccess> writableFields() {
        Map<FieldCode, EffectiveFieldAccess> accesses = new HashMap<>();
        List.of(OpportunityFieldCatalog.NAME, OpportunityFieldCatalog.CUSTOMER,
                        OpportunityFieldCatalog.PIPELINE_CODE, OpportunityFieldCatalog.STAGE_CODE,
                        OpportunityFieldCatalog.EXPECTED_CLOSE_DATE,
                        OpportunityFieldCatalog.ESTIMATED_AMOUNT,
                        OpportunityFieldCatalog.NEXT_CONTACT_TIME,
                        OpportunityFieldCatalog.DESCRIPTION)
                .forEach(code -> accesses.put(new FieldCode(code), EffectiveFieldAccess.owner()));
        return accesses;
    }

    private static Opportunity opportunity() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 8, 0);
        return Opportunity.reconstitute(new Opportunity.Snapshot(
                new OpportunityId(1L), new TenantId(1L), new MemberId(2L), new CustomerId(9L),
                null, "Annual renewal", "direct", "qualification", LocalDate.of(2026, 9, 30),
                new BigDecimal("1250.50"), LocalDateTime.of(2026, 8, 8, 9, 30),
                "Renewal discussion", OpportunityOutcome.OPEN, null, null,
                OpportunityStatus.ACTIVE, List.of(), List.of(), List.of(), List.of(), 4L, now, now));
    }
}
