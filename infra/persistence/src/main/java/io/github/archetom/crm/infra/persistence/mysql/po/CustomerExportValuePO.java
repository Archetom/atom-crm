package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Parameterized custom scalar B30a projection; it never crosses the persistence boundary. */
@Data
public class CustomerExportValuePO {
    private Long customerId;
    private String fieldCode;
    private String scalarValue;
}
