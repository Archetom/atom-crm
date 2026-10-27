package io.github.archetom.crm.application.vo;

import java.util.List;

public record ContactHierarchyVO(Long requestedContactId, List<ContactVO> contacts) {
}
