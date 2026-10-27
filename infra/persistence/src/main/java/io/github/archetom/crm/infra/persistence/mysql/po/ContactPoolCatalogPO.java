package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

@Data
public class ContactPoolCatalogPO {
    private Long id;
    private String name;
    private String status;
    private Boolean administrator;
}
