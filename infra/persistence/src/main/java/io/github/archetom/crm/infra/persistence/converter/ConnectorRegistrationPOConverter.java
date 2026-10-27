package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ConnectorCredentialKey;
import io.github.archetom.crm.domain.entity.ConnectorRegistration;
import io.github.archetom.crm.domain.model.ConnectorCredentialStatus;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorCredentialPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorRegistrationPO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConnectorRegistrationPOConverter {

    public ConnectorRegistrationPO toPO(ConnectorRegistration value) {
        if (value == null) {
            return null;
        }
        ConnectorRegistrationPO po = new ConnectorRegistrationPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setConnectorRef(value.getConnectorRef().getValue());
        po.setStatus(value.getStatus().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        po.setRetiredTime(value.getRetiredTime());
        return po;
    }

    public ConnectorCredentialPO toPO(
            TenantId tenantId, ConnectorId connectorId, ConnectorRef connectorRef,
            ConnectorCredentialKey value) {
        ConnectorCredentialPO po = new ConnectorCredentialPO();
        po.setTenantId(tenantId.getValue());
        po.setConnectorId(connectorId.getValue());
        po.setConnectorRef(connectorRef.getValue());
        po.setKeyId(value.getKeyId());
        po.setSecretVerifier(value.getSecretVerifier());
        po.setStatus(value.getStatus().name());
        po.setCreatedTime(value.getCreatedTime());
        po.setRevokedTime(value.getRevokedTime());
        return po;
    }

    public ConnectorRegistration toDomain(
            ConnectorRegistrationPO root, List<ConnectorCredentialPO> credentials) {
        if (root == null) {
            return null;
        }
        List<ConnectorRegistration.CredentialSnapshot> snapshots = credentials == null
                ? List.of() : credentials.stream().map(value ->
                new ConnectorRegistration.CredentialSnapshot(
                        value.getKeyId(), value.getSecretVerifier(),
                        ConnectorCredentialStatus.valueOf(value.getStatus()),
                        value.getCreatedTime(), value.getRevokedTime())).toList();
        return ConnectorRegistration.reconstitute(new ConnectorRegistration.Snapshot(
                new ConnectorId(root.getId()), new TenantId(root.getTenantId()),
                new ConnectorRef(root.getConnectorRef()), ConnectorStatus.valueOf(root.getStatus()),
                snapshots, root.getVersion(), root.getCreatedTime(), root.getUpdatedTime(),
                root.getRetiredTime()));
    }
}
