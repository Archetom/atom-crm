package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolMembershipPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContactPoolMembershipMapper extends BaseMapper<ContactPoolMembershipPO> {
}
