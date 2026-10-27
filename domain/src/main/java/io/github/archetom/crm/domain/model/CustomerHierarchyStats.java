package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.CustomerId;

/** Bounded structural metrics used to enforce queryable customer hierarchies on writes. */
public record CustomerHierarchyStats(
        CustomerId rootId,
        int depth,
        int subtreeNodeCount,
        int subtreeHeight,
        int treeNodeCount,
        int treeHeight) {

    public static final int MAX_TREE_NODES = 2_000;
    public static final int MAX_DEPTH = 64;
}
