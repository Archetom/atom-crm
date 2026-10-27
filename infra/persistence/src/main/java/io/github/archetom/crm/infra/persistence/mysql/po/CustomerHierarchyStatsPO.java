package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Read-only projection for one bounded recursive customer subtree. */
@Data
public class CustomerHierarchyStatsPO {
    private Integer nodeCount;
    private Integer height;
}
