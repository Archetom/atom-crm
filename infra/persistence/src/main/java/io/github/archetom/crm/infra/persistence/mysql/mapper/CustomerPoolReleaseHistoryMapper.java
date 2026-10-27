package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolReleaseHistoryPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerPoolReleaseHistoryMapper extends BaseMapper<CustomerPoolReleaseHistoryPO> {
}
