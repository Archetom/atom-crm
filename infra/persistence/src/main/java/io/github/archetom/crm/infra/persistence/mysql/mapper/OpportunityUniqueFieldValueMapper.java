package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityUniqueFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpportunityUniqueFieldValueMapper
        extends BaseMapper<OpportunityUniqueFieldValuePO> {
}
