package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolMembershipPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerPoolMembershipMapper extends BaseMapper<CustomerPoolMembershipPO> {
}
