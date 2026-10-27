package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read-only projection used by distance-sorted customer queries. */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerDistancePO extends CustomerPO {
    private Long distanceMeters;
}
