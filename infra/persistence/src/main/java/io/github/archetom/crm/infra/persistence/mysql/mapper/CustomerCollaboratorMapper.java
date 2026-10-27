package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerCollaboratorPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerCollaboratorMapper extends BaseMapper<CustomerCollaboratorPO> {
}
