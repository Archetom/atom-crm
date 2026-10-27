package io.github.archetom.crm.application.vo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record FieldLayoutVO(String objectType, List<String> orderedCodes,
                            Set<String> hiddenCodes, Set<String> pinnedCodes,
                            Map<String, Integer> columnWidths, Long version) {
}
