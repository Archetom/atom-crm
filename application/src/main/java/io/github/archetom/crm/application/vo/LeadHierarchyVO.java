package io.github.archetom.crm.application.vo;

import java.util.List;

public record LeadHierarchyVO(Long requestedLeadId, List<LeadVO> leads) {
}
