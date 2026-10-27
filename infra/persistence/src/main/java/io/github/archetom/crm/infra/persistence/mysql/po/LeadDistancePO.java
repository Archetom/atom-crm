package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read-only projection used by distance-sorted lead queries. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDistancePO extends LeadPO {
    private Long distanceMeters;
}
