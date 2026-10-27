package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorCredentialSecurityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Deliberately exposes only one exact credential-security query. */
@Mapper
public interface ConnectorCredentialLookupMapper {

    @Select("""
            SELECT r.connector_ref, c.key_id, c.secret_verifier,
                   r.status AS connector_status, c.status AS credential_status
              FROM crm_connector_registration r
              JOIN crm_connector_credential c
                ON c.tenant_id = r.tenant_id
               AND c.connector_id = r.id
               AND c.connector_ref = r.connector_ref
             WHERE r.connector_ref = #{connectorRef}
               AND c.key_id = #{keyId}
             LIMIT 2
            """)
    List<ConnectorCredentialSecurityPO> selectExact(
            @Param("connectorRef") String connectorRef, @Param("keyId") String keyId);
}
