package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ContactTagPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContactTagMapper extends BaseMapper<ContactTagPO> {
}
