package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkflowSnapshotAssemblerTest {

    @Test
    void hiddenFieldsAreRemovedAndReadableFieldsUseTheirMasks() {
        FieldCode hidden = new FieldCode("hidden");
        FieldCode partial = new FieldCode("partial");
        FieldCode full = new FieldCode("full");
        FieldCode plain = new FieldCode("plain");

        Map<String, List<String>> result = WorkflowSnapshotAssembler.visible(
                Map.of(hidden, List.of("secret"), partial, List.of("secret"),
                        full, List.of("secret"), plain, List.of("secret")),
                Map.of(hidden, EffectiveFieldAccess.hidden(),
                        partial, new EffectiveFieldAccess(
                                FieldAccessLevel.READ, FieldMaskType.PARTIAL),
                        full, new EffectiveFieldAccess(
                                FieldAccessLevel.READ, FieldMaskType.FULL),
                        plain, new EffectiveFieldAccess(
                                FieldAccessLevel.READ, FieldMaskType.NONE)));

        assertFalse(result.containsKey("hidden"));
        assertEquals(List.of("s****t"), result.get("partial"));
        assertEquals(List.of("[REDACTED]"), result.get("full"));
        assertEquals(List.of("secret"), result.get("plain"));
    }
}
