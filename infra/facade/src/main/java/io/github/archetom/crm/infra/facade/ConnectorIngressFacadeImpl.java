package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.api.facade.ConnectorIngressFacade;
import io.github.archetom.crm.application.service.ConnectorIngressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectorIngressFacadeImpl implements ConnectorIngressFacade {
    private final ConnectorIngressService service;

    @Override
    public Result<Void> probe(AuthenticatedConnector connector) {
        return service.probe(connector);
    }
}
