package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.ConnectorCredentialLookup;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ConnectorCredentialLookupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConnectorCredentialLookupRepositoryImpl implements ConnectorCredentialLookup {
    private final ConnectorCredentialLookupMapper mapper;

    @Override
    public Optional<CredentialSecurityRecord> findExact(
            ConnectorRef connectorRef, String keyId) {
        if (connectorRef == null || keyId == null || !keyId.matches("[A-Za-z0-9_-]{24}")) {
            return Optional.empty();
        }
        var rows = mapper.selectExact(connectorRef.getValue(), keyId);
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException("Connector credential lookup was ambiguous");
        }
        var row = rows.getFirst();
        return Optional.of(new CredentialSecurityRecord(
                new ConnectorRef(row.getConnectorRef()), row.getKeyId(), row.getSecretVerifier(),
                ConnectorStatus.valueOf(row.getConnectorStatus()),
                ConnectorCredentialStatus.valueOf(row.getCredentialStatus())));
    }
}
