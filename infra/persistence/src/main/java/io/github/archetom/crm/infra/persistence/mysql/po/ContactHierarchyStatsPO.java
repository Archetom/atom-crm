package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Read-only projection for one bounded recursive contact subtree. */
@Data
public class ContactHierarchyStatsPO {
    private Integer nodeCount;
    private Integer height;
}
