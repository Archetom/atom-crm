package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Minimal verifier-free projection for application connector identity resolution. */
@Data
public class ConnectorIdentitySecurityPO {
    private Long connectorId;
    private Long tenantId;
    private String connectorRef;
    private String status;
    private Long version;
}
