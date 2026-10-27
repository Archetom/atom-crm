package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpportunityFieldValueMapper extends BaseMapper<OpportunityFieldValuePO> {
}
