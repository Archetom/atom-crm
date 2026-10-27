package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerTagPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerTagMapper extends BaseMapper<CustomerTagPO> {
}
