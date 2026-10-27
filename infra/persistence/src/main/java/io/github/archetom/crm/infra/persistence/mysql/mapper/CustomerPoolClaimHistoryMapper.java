package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolClaimHistoryPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerPoolClaimHistoryMapper extends BaseMapper<CustomerPoolClaimHistoryPO> {
}
