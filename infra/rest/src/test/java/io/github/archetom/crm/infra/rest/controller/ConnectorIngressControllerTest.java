package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.dto.response.ConnectorCredentialCreatedResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorIngressControllerTest {

    @Test
    void probeRejectsAnyNonEmptyBodyBeforeIdentityMappingOrApplicationAccess() {
        ConnectorIngressController controller = new ConnectorIngressController(null, null);

        assertThrows(IllegalArgumentException.class,
                () -> controller.probe(null, "{\"tenantId\":9}"));
    }

    @Test
    void credentialCreatedResponseStringNeverIncludesTheOneTimeCredential() {
        String credential = "sensitive-connector-credential";
        ConnectorCredentialCreatedResponse response = new ConnectorCredentialCreatedResponse()
                .setCredential(credential);

        assertFalse(response.toString().contains(credential));
    }
}
