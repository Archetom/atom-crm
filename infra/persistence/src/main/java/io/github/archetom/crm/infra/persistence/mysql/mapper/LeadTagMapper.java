package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.LeadTagPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadTagMapper extends BaseMapper<LeadTagPO> {
}
