package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Aggregated customer count for one tenant-scoped pool. */
@Data
public class CustomerPoolCustomerCountPO {
    private Long poolId;
    private Long customerCount;
}
