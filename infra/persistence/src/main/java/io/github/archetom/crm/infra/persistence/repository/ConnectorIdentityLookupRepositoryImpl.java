package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.ConnectorIdentityLookup;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ConnectorIdentityLookupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConnectorIdentityLookupRepositoryImpl implements ConnectorIdentityLookup {
    private final ConnectorIdentityLookupMapper mapper;

    @Override
    public Optional<ConnectorIdentity> findExact(ConnectorRef connectorRef) {
        if (connectorRef == null) {
            return Optional.empty();
        }
        var rows = mapper.selectExact(connectorRef.getValue());
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException("Connector identity lookup was ambiguous");
        }
        var row = rows.getFirst();
        return Optional.of(new ConnectorIdentity(
                new ConnectorId(row.getConnectorId()), new TenantId(row.getTenantId()),
                new ConnectorRef(row.getConnectorRef()), ConnectorStatus.valueOf(row.getStatus()),
                row.getVersion()));
    }
}
