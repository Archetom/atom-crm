package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReceivableMapper extends BaseMapper<ReceivablePO> {
}
