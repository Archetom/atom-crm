package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.LeadFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadFieldValueMapper extends BaseMapper<LeadFieldValuePO> {
}
