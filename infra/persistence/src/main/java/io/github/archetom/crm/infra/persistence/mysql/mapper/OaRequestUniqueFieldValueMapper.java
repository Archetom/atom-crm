package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestUniqueFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OaRequestUniqueFieldValueMapper
        extends BaseMapper<OaRequestUniqueFieldValuePO> {
}
