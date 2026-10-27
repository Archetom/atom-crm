package io.github.archetom.crm.application.vo;

import java.util.List;

public record SavedViewConditionVO(String fieldCode, String operator, List<String> values,
                                   boolean exposed) {
}
