package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpportunityMapper extends BaseMapper<OpportunityPO> {
}
