package io.github.archetom.crm.application.vo;

import java.util.List;

public record CustomerHierarchyVO(Long requestedCustomerId, List<CustomerVO> customers) {
}
