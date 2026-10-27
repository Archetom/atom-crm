package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.facade.ConnectorIngressFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedConnectorMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Core-owned normalized ingress identity probe; it accepts no fact body or tenant selector. */
@RestController
@RequestMapping("/api/v1/connector-ingress")
@RequiredArgsConstructor
public class ConnectorIngressController {
    private final ConnectorIngressFacade facade;
    private final AuthenticatedConnectorMapper connectorMapper;

    @PostMapping("/probe")
    public ResponseEntity<?> probe(
            Authentication authentication, @RequestBody(required = false) String body) {
        if (body != null && !body.isEmpty()) {
            throw new IllegalArgumentException("Connector ingress probe does not accept a body");
        }
        return ResponseEntityUtil.assembleResponse(
                facade.probe(connectorMapper.from(authentication)));
    }
}
