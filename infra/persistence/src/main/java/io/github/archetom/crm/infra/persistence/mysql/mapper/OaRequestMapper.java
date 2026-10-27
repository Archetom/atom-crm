package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OaRequestMapper extends BaseMapper<OaRequestPO> {
}
