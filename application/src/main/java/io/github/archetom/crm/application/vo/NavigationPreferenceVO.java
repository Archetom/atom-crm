package io.github.archetom.crm.application.vo;

import java.util.List;
import java.util.Set;

/** Current caller's bounded navigation preference. */
public record NavigationPreferenceVO(List<String> orderedCodes, Set<String> hiddenCodes, Long version,
                                     List<NavigationItemVO> availableItems) {
    public NavigationPreferenceVO {
        orderedCodes = orderedCodes == null ? List.of() : List.copyOf(orderedCodes);
        hiddenCodes = hiddenCodes == null ? Set.of() : Set.copyOf(hiddenCodes);
        availableItems = availableItems == null ? List.of() : List.copyOf(availableItems);
    }
}
