package io.github.archetom.crm.application.vo;

import java.util.List;

/** Immutable application navigation tree node. */
public record NavigationItemVO(
        String code,
        String parentCode,
        String route,
        String titleKey,
        int order,
        String requiredAuthority,
        List<NavigationItemVO> children
) {
    public NavigationItemVO {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
