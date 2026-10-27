package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ContactAddressPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContactAddressMapper extends BaseMapper<ContactAddressPO> {
}
