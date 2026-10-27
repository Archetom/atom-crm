package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;
import lombok.ToString;

/** Minimal verifier-bearing projection for the transport authenticator only. */
@Data
public class ConnectorCredentialSecurityPO {
    private String connectorRef;
    private String keyId;
    @ToString.Exclude
    private String secretVerifier;
    private String connectorStatus;
    private String credentialStatus;
}
