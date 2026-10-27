package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Aggregated contact count for one tenant-scoped pool. */
@Data
public class ContactPoolContactCountPO {
    private Long poolId;
    private Long contactCount;
}
