package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ContactFieldValuePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContactFieldValueMapper extends BaseMapper<ContactFieldValuePO> {
}
