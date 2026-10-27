package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Parameterized custom scalar B31a projection; it never crosses the persistence boundary. */
@Data
public class ContactExportValuePO {
    private Long contactId;
    private String fieldCode;
    private String scalarValue;
}
