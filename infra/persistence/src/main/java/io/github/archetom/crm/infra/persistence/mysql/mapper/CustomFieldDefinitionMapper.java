package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomFieldDefinitionMapper extends BaseMapper<CustomFieldDefinitionPO> {
}
