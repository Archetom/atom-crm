package io.github.archetom.crm.application.vo;

import java.util.List;

public record RoleFieldPolicyVO(Long roleId, String objectType,
                                List<RoleFieldAccessVO> accesses, Long version) {
}
