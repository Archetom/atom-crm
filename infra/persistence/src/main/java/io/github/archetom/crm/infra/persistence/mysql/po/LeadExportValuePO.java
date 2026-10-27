package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Parameterized custom scalar Lead projection; it never crosses the persistence boundary. */
@Data
public class LeadExportValuePO {
    private Long leadId;
    private String fieldCode;
    private String scalarValue;
}
