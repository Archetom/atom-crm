package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;

public interface ConnectorIngressService {
    Result<Void> probe(AuthenticatedConnector connector);
}
