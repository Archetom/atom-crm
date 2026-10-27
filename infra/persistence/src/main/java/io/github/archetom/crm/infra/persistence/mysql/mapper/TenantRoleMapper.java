package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.TenantRolePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantRoleMapper extends BaseMapper<TenantRolePO> {
}
