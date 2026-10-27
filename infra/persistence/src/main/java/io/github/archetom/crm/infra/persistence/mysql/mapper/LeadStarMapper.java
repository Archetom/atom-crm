package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.LeadStarPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadStarMapper extends BaseMapper<LeadStarPO> {
}
