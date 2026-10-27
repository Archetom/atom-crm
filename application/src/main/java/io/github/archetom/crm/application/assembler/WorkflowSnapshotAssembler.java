package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Filters and masks generic string snapshots using the caller's current B05 policy. */
final class WorkflowSnapshotAssembler {

    private static final String REDACTED = "[REDACTED]";

    private WorkflowSnapshotAssembler() {
    }

    static Map<String, List<String>> visible(
            Map<FieldCode, List<String>> values,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return visible(values, accesses, Set.of());
    }

    static Map<String, List<String>> visible(
            Map<FieldCode, List<String>> values,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            Set<FieldCode> hiddenRelationshipFacts) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((code, raw) -> {
            if (hiddenRelationshipFacts.contains(code)) {
                return;
            }
            EffectiveFieldAccess access = accesses.get(code);
            if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN) {
                return;
            }
            result.put(code.getValue(), raw.stream()
                    .map(value -> mask(value, access.maskType())).toList());
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String mask(String value, FieldMaskType type) {
        if (type == FieldMaskType.NONE) {
            return value;
        }
        int count = value.codePointCount(0, value.length());
        if (type == FieldMaskType.FULL || count < 3) {
            return REDACTED;
        }
        int firstEnd = value.offsetByCodePoints(0, 1);
        int lastStart = value.offsetByCodePoints(0, count - 1);
        return value.substring(0, firstEnd) + "*".repeat(Math.min(8, count - 2))
                + value.substring(lastStart);
    }
}
