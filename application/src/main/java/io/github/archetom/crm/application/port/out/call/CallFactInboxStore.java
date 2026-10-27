package io.github.archetom.crm.application.port.out.call;

import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;

/** Durable core inbox for already-normalized private-adapter facts; it never receives raw input. */
public interface CallFactInboxStore {
    Outcome accept(TenantId tenantId, ConnectorId connectorId, byte[] deliveryRef, byte[] factDigest, Instant now);

    enum Outcome { APPLIED, DUPLICATE, CONFLICT }
}
