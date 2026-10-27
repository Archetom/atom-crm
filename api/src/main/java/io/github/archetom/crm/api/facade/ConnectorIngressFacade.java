package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;

/** Non-human facade; human caller contexts are intentionally not accepted. */
public interface ConnectorIngressFacade {
    Result<Void> probe(AuthenticatedConnector connector);
}
