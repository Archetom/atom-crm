package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityContactPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpportunityContactMapper extends BaseMapper<OpportunityContactPO> {
}
