package io.github.archetom.crm.application.vo;

import java.util.List;

public record FilterConditionVO(String fieldCode, String operator, List<String> values) {
}
