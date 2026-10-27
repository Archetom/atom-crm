package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerAddressPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerAddressMapper extends BaseMapper<CustomerAddressPO> {
}
