package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorCredentialPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectorCredentialMapper extends BaseMapper<ConnectorCredentialPO> {
}
