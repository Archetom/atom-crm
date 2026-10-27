package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.LeadId;

/** Bounded structural metrics used to enforce queryable lead hierarchies on writes. */
public record LeadHierarchyStats(
        LeadId rootId,
        int depth,
        int subtreeNodeCount,
        int subtreeHeight,
        int treeNodeCount,
        int treeHeight) {

    public static final int MAX_TREE_NODES = 2_000;
    public static final int MAX_DEPTH = 64;
}
