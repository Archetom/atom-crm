package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.RoleGrantPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleGrantMapper extends BaseMapper<RoleGrantPO> {
}
