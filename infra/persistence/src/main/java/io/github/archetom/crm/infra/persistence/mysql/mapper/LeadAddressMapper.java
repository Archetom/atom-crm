package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.LeadAddressPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadAddressMapper extends BaseMapper<LeadAddressPO> {
}
