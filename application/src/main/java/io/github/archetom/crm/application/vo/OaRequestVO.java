package io.github.archetom.crm.application.vo;

import java.util.List;
import java.util.Map;

public record OaRequestVO(
        Long id,
        Long formId,
        Long formVersion,
        Long approvalInstanceId,
        String approvalStatus,
        Map<String, List<String>> values,
        Long version) {
}
