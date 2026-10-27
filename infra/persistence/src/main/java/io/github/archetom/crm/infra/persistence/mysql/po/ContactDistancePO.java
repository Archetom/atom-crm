package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read-only projection used by distance-sorted contact queries. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContactDistancePO extends ContactPO {
    private Long distanceMeters;
}
