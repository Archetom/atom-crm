package io.github.archetom.crm.api.dto.response;

import java.util.List;

public record ApprovalConditionResponse(String fieldCode, String operator, List<String> values) {
}
