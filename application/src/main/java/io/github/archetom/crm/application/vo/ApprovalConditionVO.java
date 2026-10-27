package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalConditionVO(String fieldCode, String operator, List<String> values) {
}
