package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Read-only projection for one bounded recursive lead subtree. */
@Data
public class LeadHierarchyStatsPO {
    private Integer nodeCount;
    private Integer height;
}
