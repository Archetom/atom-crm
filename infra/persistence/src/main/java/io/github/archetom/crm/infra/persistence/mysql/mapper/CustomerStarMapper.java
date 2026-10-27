package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerStarPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerStarMapper extends BaseMapper<CustomerStarPO> {
}
