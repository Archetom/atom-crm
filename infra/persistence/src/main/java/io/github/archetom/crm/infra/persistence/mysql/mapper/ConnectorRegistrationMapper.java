package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ConnectorRegistrationPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectorRegistrationMapper extends BaseMapper<ConnectorRegistrationPO> {
}
