package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorIdentitySecurityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Deliberately exposes only one exact, verifier-free identity query. */
@Mapper
public interface ConnectorIdentityLookupMapper {

    @Select("""
            SELECT id AS connector_id, tenant_id, connector_ref, status, version
              FROM crm_connector_registration
             WHERE connector_ref = #{connectorRef}
             LIMIT 2
            """)
    List<ConnectorIdentitySecurityPO> selectExact(@Param("connectorRef") String connectorRef);
}
