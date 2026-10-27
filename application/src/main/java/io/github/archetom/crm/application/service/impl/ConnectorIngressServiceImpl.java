package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ConnectorGuard;
import io.github.archetom.crm.application.service.ConnectorIngressService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Verifier-free application guard probe for independent-process adapter deployments. */
@Service
@RequiredArgsConstructor
public class ConnectorIngressServiceImpl implements ConnectorIngressService {
    private final ConnectorGuard connectorGuard;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Void> probe(AuthenticatedConnector connector) {
        return queryTemplate.execute(UseCaseOperation.CONNECTOR_INGRESS_PROBE,
                new ServiceOperation<>() {
                    @Override
                    public void validate() {
                        connectorGuard.requireActive(connector);
                    }

                    @Override
                    public Void execute() {
                        return null;
                    }
                });
    }
}
