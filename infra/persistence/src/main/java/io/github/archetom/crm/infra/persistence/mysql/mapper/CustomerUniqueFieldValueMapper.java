package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerUniqueFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerUniqueFieldValueMapper extends BaseMapper<CustomerUniqueFieldValuePO> {
}
